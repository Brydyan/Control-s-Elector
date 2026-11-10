# T-COMPLIANCE-08: Tasks — Backup & Disaster Recovery

**Effort:** 3-4 puntos → 1 sprint

---

## Sprint 1: Backup Infrastructure

### Task 8.1 — Backup Script

**Estimación:** 1.5 pts

- [ ] **C.8.1.1** — backup.sh script
  - Path: `scripts/backup/backup.sh`
  - Runs: daily at 23:00 UTC (cron)
  - Backup DB: `pg_dump control_electoral > backup-${DATE}.sql`
  - Archive files: tar configs, assets
  - Upload to S3: `aws s3 cp backup-${DATE}.tar.gz s3://backup-bucket/`

- [ ] **C.8.1.1 — Mutación**
  - Remove S3 upload line
  - Backup not stored (test fails)
  - Note: [test name]
  - Restore

- [ ] **C.8.1.2** — Checksum validation
  - Calculate MD5 of backup
  - Store in S3 metadata
  - Verify after upload

- [ ] **C.8.1.2 — Mutación**
  - Skip checksum verification
  - Corrupted backup not detected (test fails)
  - Note: [test name]
  - Restore

### Task 8.2 — Cross-Region Copy (24h)

**Estimación:** 1 pt

- [ ] **C.8.2.1** — Cross-region copy script
  - Runs 24h after backup
  - Copies S3 [primary] → S3 [secondary region]
  - Path: `scripts/backup/copy-backup-secondary.sh`

- [ ] **C.8.2.1 — Mutación**
  - Disable copy to secondary
  - Backup only in primary (test fails)
  - Note: [test name]
  - Restore

### Task 8.3 — Retention Policy

**Estimación:** 0.5 pts

- [ ] **C.8.3.1** — Retention script (30 días)
  - Runs daily
  - Delete backups > 30 días old
  - Log deleted files

- [ ] **C.8.3.1 — Mutación**
  - Skip deletion
  - Old backups accumulate (test fails)
  - Note: [test name]
  - Restore

### Task 8.4 — Recovery Playbook

**Estimación:** 1 pt

- [ ] **C.8.4.1** — DISASTER_RECOVERY.md
  - Path: `docs/DISASTER_RECOVERY.md`
  - Steps: download backup, restore DB, verify data, smoke tests
  - RTO target: 4 horas
  - RPO target: 24 horas
  - Quarterly test schedule

---

## Gates

```bash
# Backup exists
ls -la s3://backup-bucket/backup-*.tar.gz

# Cron scheduled
crontab -l | grep backup.sh

# Recovery script executable
ls -la scripts/backup/backup.sh

# Playbook exists
cat docs/DISASTER_RECOVERY.md
```

**Last Updated:** 2026-11-05
