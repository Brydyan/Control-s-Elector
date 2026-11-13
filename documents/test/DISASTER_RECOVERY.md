# T-COMPLIANCE-08: Disaster Recovery Playbook

**Version:** 1.0  
**Last Updated:** 2026-11-05  
**RTO Target:** 4 hours  
**RPO Target:** 24 hours (daily backup)  

---

## Overview

This playbook describes the step-by-step procedure to restore the electoral system from backups in case of data loss or system failure. Recovery is designed to restore service within 4 hours from a clean PostgreSQL instance.

### Key Metrics

| Metric | Value | Note |
|--------|-------|------|
| **RPO** | 24 hours | Data loss limited to max 1 day (daily backup schedule) |
| **RTO** | 4 hours | Expected time to restore from backup to operational system |
| **Backup Frequency** | Daily at 23:00 UTC | Full PostgreSQL dump + actas archive |
| **Retention Period** | 30 days | Automatic deletion of backups older than 30 days |
| **Storage** | S3 (primary + secondary region) | AES-256 encrypted, versioning enabled |

---

## Prerequisites

Before attempting recovery, ensure you have:

1. **Access to AWS account** with S3 bucket `electoral-backups` (or configured bucket)
2. **AWS CLI installed and configured** with appropriate IAM credentials
3. **PostgreSQL client tools** (`psql`, `pg_restore`) available
4. **Docker or VM capacity** to provision fresh PostgreSQL 15+ instance
5. **Network access** to S3 and fresh database server
6. **Backup credentials** stored securely (AWS keys, DB passwords)
7. **Contact list** for ops team notification

---

## Restore Procedure

### Phase 1: Preparation (30 minutes)

#### 1.1 Identify Latest Backup

```bash
# List available backups
aws s3 ls s3://electoral-backups/postgres/ --recursive | sort

# Identify latest backup (should be from yesterday or today at 23:00 UTC)
# Example: backup-2026-11-04.sql.gz
BACKUP_DATE="2026-11-04"
BACKUP_FILE="backup-${BACKUP_DATE}.sql.gz"
```

**Decision Point:** If primary region S3 unavailable, switch to secondary region:
```bash
aws s3 ls s3://electoral-backups-secondary/postgres/ --recursive
```

#### 1.2 Verify Backup Integrity

```bash
# Download checksum
aws s3 cp s3://electoral-backups/checksums/backup-${BACKUP_DATE}.md5 . --region us-east-1

# Download backup
aws s3 cp s3://electoral-backups/postgres/${BACKUP_FILE} . --region us-east-1

# Verify checksum
md5sum -c backup-${BACKUP_DATE}.md5
# Output should show: "backup-YYYY-MM-DD.sql.gz: OK"
```

**If checksum fails:** Do NOT proceed. Contact backup administrator. Try secondary region backup.

#### 1.3 Prepare Fresh PostgreSQL Instance

**Option A: Docker (Fastest)**
```bash
docker run --name electoral-db-restore \
  -e POSTGRES_DB=control_electoral \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=${POSTGRES_PASSWORD} \
  -p 5432:5432 \
  -d postgres:15-alpine

# Wait for database to be ready
sleep 10
docker logs electoral-db-restore | grep "database system is ready"
```

**Option B: VM / Cloud Instance**
```bash
# Provision Ubuntu 22.04+ VM with 8GB RAM, 50GB disk
# SSH into instance and run:
sudo apt-get update
sudo apt-get install -y postgresql-15 postgresql-client-15
sudo systemctl start postgresql
sudo systemctl enable postgresql
```

**Option C: AWS RDS**
```bash
# Create temporary RDS instance from snapshot or clean instance
# (Fastest if snapshot exists, ~5-10 min. Otherwise 15-20 min.)
# Note: Restore from snapshot is preferred; skip to Phase 2.1
```

---

### Phase 2: Database Restore (1-2 hours depending on backup size)

#### 2.1 Restore Database Schema and Data

```bash
# Decompress backup
gunzip backup-${BACKUP_DATE}.sql.gz

# Restore to PostgreSQL
# Note: This will create all tables, indices, and data
psql -h localhost -U postgres -d control_electoral \
  -f backup-${BACKUP_DATE}.sql \
  -v ON_ERROR_STOP=1 2>&1 | tee restore.log

# Expected output:
# - "CREATE TABLE" messages (tables being created)
# - "INSERT" messages (data being inserted)
# - No ERROR messages (if restoration is successful)
```

**Monitor restoration progress:**
```bash
# In another terminal, check row counts
psql -h localhost -U postgres -d control_electoral -c "SELECT count(*) FROM actas;"
psql -h localhost -U postgres -d control_electoral -c "SELECT count(*) FROM voters;"
```

**If restoration fails:**
1. Check restore.log for errors
2. Ensure fresh database is truly empty: `DROP DATABASE control_electoral; CREATE DATABASE control_electoral;`
3. Retry with same or older backup
4. If all backups fail: escalate to DBA / database team

#### 2.2 Verify Data Integrity

After successful restore, verify key data:

```bash
# Count rows in critical tables
psql -h localhost -U postgres -d control_electoral << EOF
SELECT 'actas' as table_name, COUNT(*) as row_count FROM actas
UNION ALL
SELECT 'voters', COUNT(*) FROM voters
UNION ALL
SELECT 'configuracion_electoral', COUNT(*) FROM configuracion_electoral
UNION ALL
SELECT 'acta_details', COUNT(*) FROM acta_details;
EOF

# Compare with production baseline (should be similar)
# Example baseline (adjust based on your data):
# - actas: ~10,000 rows
# - voters: ~50,000 rows
# - configuracion_electoral: ~500 rows
# - acta_details: ~150,000 rows
```

**If row counts are significantly lower:**
- May indicate corrupted backup or incomplete restore
- Try secondary region backup
- Escalate if pattern persists

---

### Phase 3: Restore Electoral Files (30 minutes)

If actas/assets were backed up as tar archive:

#### 3.1 Download and Extract Archive

```bash
# Download actas archive
aws s3 cp s3://electoral-backups/actas/${BACKUP_DATE}/ . --recursive --region us-east-1

# Extract to application directory
tar -xzf backup-${BACKUP_DATE}_actas.tar.gz -C /app/

# Verify files restored
ls -la /app/config/
ls -la /app/assets/
```

#### 3.2 Set Correct Permissions

```bash
# Ensure application user can read files
sudo chown -R electoral-api:electoral-api /app/config /app/assets
chmod -R 755 /app/config /app/assets
```

---

### Phase 4: System Health Check (30 minutes)

#### 4.1 Verify API Health Endpoint

```bash
# Start application (if not already running)
# Point to restored database

# Health check
curl -s http://localhost:8080/health | jq .

# Expected response:
# {
#   "status": "UP",
#   "components": {
#     "db": { "status": "UP" },
#     "diskSpace": { "status": "UP" }
#   }
# }
```

**If health check fails:**
- Check application logs for database connection errors
- Verify database user/password is correct
- Ensure firewall allows database access
- Retry database restore if needed

#### 4.2 Smoke Tests

Run critical business logic tests:

```bash
# Test 1: Login endpoint
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"test_operator","password":"password"}' | jq .

# Test 2: Fetch actas list
curl -X GET http://localhost:8080/api/actas \
  -H "Authorization: Bearer ${TOKEN}" | jq '.[] | select(.id) | .id' | head -5

# Test 3: Verify data consistency
curl -X GET http://localhost:8080/api/estadisticas \
  -H "Authorization: Bearer ${TOKEN}" | jq .
```

**If any endpoint returns errors:**
- Check database logs: `docker logs electoral-db-restore` (if Docker)
- Review application logs for SQL errors
- May need to re-run data integrity checks (Phase 2.2)

#### 4.3 Sample Data Verification

```bash
# Retrieve and verify sample acta
psql -h localhost -U postgres -d control_electoral -c \
  "SELECT id, acta_number, poll_location, status, created_at FROM actas LIMIT 1;"

# Verify audit trail exists
psql -h localhost -U postgres -d control_electoral -c \
  "SELECT COUNT(*) as audit_entries FROM audit_log WHERE event_date > NOW() - INTERVAL '1 day';"
```

---

### Phase 5: Cutover (30 minutes)

Once all health checks pass:

#### 5.1 Update Application Database Connection

Update application configuration to point to restored database:

```bash
# Update application.properties or environment
export DB_HOST=<restored-db-hostname>
export DB_PORT=5432
export DB_NAME=control_electoral
export DB_USER=postgres
export DB_PASSWORD=<secure-password>

# Restart application
systemctl restart electoral-api
# or
docker restart electoral-api
```

#### 5.2 Verify Production Traffic

```bash
# Monitor logs for errors
tail -f /var/log/electoral-api/application.log

# Check key metrics
curl -s http://localhost:8080/metrics | grep http_requests_total

# Run end-to-end test (login → fetch actas → export)
./scripts/e2e-test.sh
```

#### 5.3 Notify Operations Team

Send notification when recovery is complete:
```
Subject: Electoral System Recovery Complete
Status: OPERATIONAL
RTO Achieved: [X minutes] (target: 4 hours)
Data Loss: [X hours] (target: 24 hours)
Last Backup: ${BACKUP_DATE}
Next Actions: Monitor logs, document recovery test results
```

---

## Quarterly Disaster Recovery Drill

To ensure recovery procedure works, conduct quarterly test:

### Drill Schedule
- **Q1:** January 15
- **Q2:** April 15
- **Q3:** July 15
- **Q4:** October 15

### Test Procedure

1. **Provision staging environment** (separate from production)
2. **Follow recovery procedure** (Phases 1-4) using oldest available backup
3. **Record metrics:**
   - Time to provision database: __ minutes
   - Time to restore data: __ minutes
   - Time to pass health checks: __ minutes
   - **Total RTO:** __ minutes (target: < 240 min / 4 hours)
   - Backup age at time of test: __ days (RPO)

4. **Document findings:**
   - Any steps that failed or needed adjustment
   - Any data integrity issues discovered
   - Database size and compression ratio achieved
   - Any issues with S3 access or networking

5. **Update playbook:**
   - Add notes for any steps that were unclear
   - Record actual timing for capacity planning
   - Update prerequisites if tools or versions changed

6. **File report:**
   - Save results in: `docs/disaster-recovery-drills/drill-YYYY-Q[1-4].md`
   - Template: [See below]

---

## Drill Report Template

```markdown
# Disaster Recovery Drill — Q[N] 20XX

**Date:** YYYY-MM-DD  
**Duration:** [X hours]  
**Backup Used:** backup-YYYY-MM-DD.sql.gz  
**Backup Age:** [X days] days  
**Status:** PASS / FAIL

## Metrics

| Phase | Task | Duration | Status |
|-------|------|----------|--------|
| 1 | Preparation | [X] min | PASS/FAIL |
| 2 | DB Restore | [X] min | PASS/FAIL |
| 3 | File Restore | [X] min | PASS/FAIL |
| 4 | Health Check | [X] min | PASS/FAIL |
| **Total** | **RTO** | **[X] min** | **[X]/240 min** |

**RPO Confirmed:** Backup from [X] days ago, latest data recoverable  

## Issues Found

- [ ] Issue 1: [description]
- [ ] Issue 2: [description]

## Resolutions

1. Resolution for issue 1
2. Resolution for issue 2

## Lessons Learned

[Notes on what worked well, what could be improved]

## Playbook Updates Required

- [ ] Step 2.1: Clarified [section]
- [ ] Step 3.2: Added [missing information]

**Approved by:** [Name], [Title]  
**Date:** YYYY-MM-DD
```

---

## Troubleshooting

### Issue: Backup File Corrupted (Checksum Fails)

**Solution:**
1. Try secondary region backup: `aws s3 ls s3://electoral-backups-secondary/postgres/`
2. Try older backup: Use backup from 2+ days ago
3. If all backups fail: Check S3 bucket versioning, may be able to restore file version

### Issue: Database Restore Hangs or Stalls

**Solution:**
1. Cancel restore: `Ctrl+C` in psql, or kill psql process
2. Drop incomplete database: `DROP DATABASE control_electoral;`
3. Check PostgreSQL logs: `psql -c "SHOW log_statement;"`
4. Retry with smaller backup or try restore on different hardware

### Issue: Data Integrity Check Shows Missing Rows

**Solution:**
1. Verify backup is not truncated: `ls -lh backup-*.sql`
2. Check restore logs for errors: `grep ERROR restore.log`
3. Attempt restore to different PostgreSQL instance
4. Try previous day's backup

### Issue: Application Cannot Connect to Restored Database

**Solution:**
1. Verify database is running: `psql -h localhost -U postgres -d control_electoral -c "SELECT 1;"`
2. Check firewall rules allow access
3. Verify database user exists: `psql -U postgres -c "\du"`
4. Verify database exists: `psql -U postgres -c "\l"`
5. Check application configuration for correct host/port

### Issue: Restored Data Looks Incomplete

**Solution:**
1. Verify data in production before proceeding (check baseline row counts)
2. Check restore logs for warnings/errors
3. Query specific tables: `SELECT COUNT(*) FROM actas;`
4. Compare against known good state or use older backup
5. If production data also looks suspicious, may indicate problem predates backup

---

## Contacts & Escalation

For recovery support, contact:

| Role | Name | Email | Phone |
|------|------|-------|-------|
| **DBA** | [Name] | dba@example.com | +1-XXX-XXX-XXXX |
| **DevOps Lead** | [Name] | devops@example.com | +1-XXX-XXX-XXXX |
| **Security Officer** | [Name] | security@example.com | +1-XXX-XXX-XXXX |
| **Electoral Commission** | [Contact] | contact@example.com | +1-XXX-XXX-XXXX |

---

## Appendix: Recovery Script Reference

All backup/recovery scripts are located in:
- **Backup Script:** `backend/electoral-api-quarkus/scripts/backup/backup.sh`
- **Cross-Region Copy:** `backend/electoral-api-quarkus/scripts/backup/copy-backup-secondary.sh`
- **Retention Policy:** `backend/electoral-api-quarkus/scripts/backup/retention-policy.sh`

### Manual Restore Command

If using scripts for unattended restore:

```bash
#!/bin/bash
set -e

BACKUP_DATE="2026-11-04"
S3_BUCKET="electoral-backups"
DB_HOST="localhost"
DB_NAME="control_electoral"

# Download and verify
aws s3 cp s3://${S3_BUCKET}/postgres/backup-${BACKUP_DATE}.sql.gz . --region us-east-1
aws s3 cp s3://${S3_BUCKET}/checksums/backup-${BACKUP_DATE}.md5 . --region us-east-1
md5sum -c backup-${BACKUP_DATE}.md5 || exit 1

# Restore
gunzip backup-${BACKUP_DATE}.sql.gz
psql -h ${DB_HOST} -U postgres -d ${DB_NAME} < backup-${BACKUP_DATE}.sql

echo "Restore completed at $(date)"
```

---

**Last Updated:** 2026-11-05  
**Next Review:** 2026-11-15  
**Version:** 1.0
