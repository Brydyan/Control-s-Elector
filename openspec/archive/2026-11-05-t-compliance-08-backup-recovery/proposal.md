# Proposal — T-DISASTER-RECOVERY-01: Backup & Recovery Infrastructure

**Date:** 2026-11-06  
**Priority:** CRITICAL  
**Estimación:** 4-5 puntos → 1 sprint

---

## Qué se construye

Sistema automatizado de backup y procedimiento de recuperación para datos electorales críticos:
- **Daily automated backups:** PostgreSQL full backup + S3 exported actas binaries, ejecutados vía cron 02:00 UTC
- **S3 storage:** backups retenidos por 30 días (oldest deleted automáticamente), versionado habilitado para accidental delete recovery
- **Encryption:** backups encriptados AES-256 en tránsito y en reposo (S3 server-side encryption)
- **RTO/RPO targets:** RPO ≤ 24h (diario), RTO ≤ 4h (restaurar desde backup en ambiente clean)
- **Recovery playbook:** paso a paso para restaurar DB + actas en fresh PostgreSQL + S3
- **Quarterly testing:** recovery drill cada 3 meses, documentado y verificado (no solo backups, también restauración)
- **Monitoring:** alertas si backup falla, o si backup >1 hora sin completar

## Por qué es necesario

**Hoy sin backups:**
- ❌ Cero copies de actas electorales o metadata. Si PostgreSQL falla, datos perdidos para siempre.
- ❌ GDPR Art. 5: integridad y confidencialidad → no hay copia de seguridad = violación
- ❌ Compliance: electoral commission exige auditoría de recuperabilidad
- ❌ Ransomware: atacante compromete DB → sin backup, no hay recovery

**Con esto:**
- ✅ Pérdida de datos → recuperable en máximo 4 horas, máximo 1 día de datos perdido (RPO 24h)
- ✅ GDPR compliant: backups encriptados, retention policies, recovery probado
- ✅ Compliance: auditoría quarterly demuestra que recovery funciona
- ✅ Ransomware: restore desde backup clean, asumiendo atacante no comprometió S3

## Problema específico

1. **Zero backup service:** No existe cron job, script, o procedimiento para hacer backup de PostgreSQL o actas en S3.
2. **No recovery procedure:** Nadie sabe cómo restaurar actas si la DB se cae. Sin documentación, sin testing.
3. **RTO/RPO undefined:** ¿Cuánto tiempo tardamos en recuperar? ¿Cuántos datos perdemos? No se sabe.
4. **Electoral criticality:** Si actas se pierden en plena jornada electoral, es catastrófico. Sin backup, es irreversible.

## Solución alto nivel

```
[2:00 UTC daily]
    ↓
[PostgreSQL pg_dump → backup_YYYYMMDD.sql.gz]
    ↓
[S3 upload: backup_YYYYMMDD.sql.gz + backup_YYYYMMDD_actas.tar.gz] (encrypted AES-256)
    ↓
[Retention policy: delete backups >30 days old]
    ↓
[Quarterly: restore test]
    ├─ [Fresh PostgreSQL container]
    ├─ [Restore: psql < backup_YYYYMMDD.sql]
    ├─ [Restore: actas from backup_YYYYMMDD_actas.tar.gz]
    ├─ [Verify: row counts match production]
    ├─ [Document: recovery took X minutes]
    └─ [Update playbook if needed]
```

**Backend stack:**
- **Backup script:** Bash script in `backend/electoral-api-quarkus/scripts/backup.sh`
  - `pg_dump -U postgres -h $DB_HOST -d electoral_db > backup_$(date +%Y%m%d).sql`
  - `gzip backup_$(date +%Y%m%d).sql`
  - `aws s3 cp backup_$(date +%Y%m%d).sql.gz s3://electoral-backups/postgres/`
  - `aws s3 cp <actas-directory> s3://electoral-backups/actas/$(date +%Y%m%d)/ --recursive`
  - Exit code 0 or alert
- **Cron:** `/etc/cron.d/electoral-backup`: 0 2 * * * /app/scripts/backup.sh
- **S3 bucket:** `s3://electoral-backups/`, versioning ON, AES-256 encryption ON, lifecycle policy (30 days delete)
- **Monitoring:** CloudWatch alarm if backup script exit != 0, or if backup size > expected
- **Recovery playbook:** `docs/DISASTER_RECOVERY.md`
  - Paso 1: provisión fresh PostgreSQL
  - Paso 2: restore DB from .sql.gz
  - Paso 3: restore actas from tar.gz to S3
  - Paso 4: verify row counts
  - Paso 5: health checks (API test)

**Frontend:**
- No cambios. Backup es infraestructura backend.

## Alcance

✅ DENTRO:
- PostgreSQL full backup script via cron (daily 02:00 UTC)
- S3 backup storage with AES-256 encryption (server-side)
- Actas binaries backup (if stored as files)
- 30-day retention policy with automatic deletion of old backups
- RPO target: ≤24h (daily backup)
- RTO target: ≤4h (time to restore from backup)
- Backup monitoring: CloudWatch alarm if backup fails or takes >1h
- Recovery playbook: documented step-by-step procedure
- Quarterly disaster recovery drill: restore test with verification
- Backup size and completion time logging
- Configuration: `application.properties` with S3 bucket name, retention days, backup time (UTC)
- Testing: unit tests para backup script logic (mock S3 upload), integration tests con localstack
- Documentation: DISASTER_RECOVERY.md, how to restore manually, what to verify

❌ FUERA:
- Continuous replication (Postgres replication) → futuro / infraestructura
- Cross-region backup replication → futuro / infraestructura cost
- Backup encryption key rotation → futuro, AWS KMS handles it
- Point-in-time recovery (WAL archiving) → futuro, daily backup sufficient for electoral cycle
- Backup to on-premises data center → futuro / compliance
- Automated failover → futuro, manual RTO 4h acceptable

---

## Beneficios

| Aspecto | Valor |
|---|---|
| **Data Protection** | Actas pérdidas → recuperables en 4h máximo. RPO 24h. |
| **Compliance** | GDPR integridad (Art. 5), electoral audit compliance (recovery proven quarterly). |
| **Ops** | Alertas si backup falla. Playbook claro. No improvisación. |
| **Audit Trail** | Backup logs: cuándo, tamaño, checksum. Compliance auditoría. |
| **Cost** | S3: ~$10/mes por 30 GB backups (negligible). |
| **Confidence** | Quarterly drill = conocemos que recovery funciona. Documentado. |

---

## Criterios de aceptación

- [ ] Backup script `/app/scripts/backup.sh` genera `backup_YYYYMMDD.sql.gz` + uploads a S3
- [ ] Cron job `/etc/cron.d/electoral-backup`: 0 2 * * * /app/scripts/backup.sh
- [ ] S3 bucket `electoral-backups/` with AES-256 encryption, versioning enabled
- [ ] Lifecycle policy: delete backups >30 days old automatically
- [ ] Backup size logged: expected range 100MB-500MB (depends on DB size)
- [ ] Backup completion time: <30 minutes (monitoring alert if >1h)
- [ ] RPO documented: 24 hours (daily backup)
- [ ] RTO documented: 4 hours (estimated from test)
- [ ] Recovery playbook `DISASTER_RECOVERY.md`: step-by-step procedure
- [ ] Playbook includes: provision DB, restore SQL, verify rows, health checks
- [ ] Quarterly drill: Q1/Q2/Q3/Q4 restore test documented with date, time taken, any issues
- [ ] CloudWatch alarm: if backup script exit != 0
- [ ] CloudWatch alarm: if backup size <100MB or >500MB (anomaly)
- [ ] Unit tests: backup script logic (mocked S3)
- [ ] Integration tests: with localstack S3, verify backup uploads correctly
- [ ] Documentation: how to manually restore from backup, what to verify after restore

---

## Riesgos

| Riesgo | Probabilidad | Impacto | Mitigación |
|---|---|---|---|
| **Backup script fails silently** | Baja (si bien testeado) | Alto (no hay backup, no se sabe) | Cron output to syslog; CloudWatch alarm on non-zero exit. |
| **S3 upload fails (network)** | Baja | Alto (backup in local disk, not S3) | Retry logic (3x); alert if upload fails. Keep local backup 3 days. |
| **Backup too large, exceeds S3 quota** | Muy baja (30 GB quota) | Medio (backup incomplete) | Monitor backup size; alert if >10GB. Adjust retention policy. |
| **Recovery procedure never tested** | Media (if not forced quarterly) | Alto (RTO unknown, recovery may fail) | Mandatory quarterly drill; document results; update playbook. |
| **Encryption key lost / S3 access revoked** | Muy baja | Alto (backup unrecoverable) | AWS KMS key backup; IAM access restricted to infra team. |
| **Backup restore takes >4h** | Media (depends on DB size, S3 speed) | Medio (RTO missed) | Test restore on staging; optimize: parallel restore, or smaller daily backups. |

---

**Status:** Ready for design  
**Last Updated:** 2026-11-06
