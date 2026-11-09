#!/bin/bash

##############################################################################
# T-COMPLIANCE-08: Backup & Disaster Recovery - Retention Policy
#
# Purpose: Automatically delete backups older than 30 days
# Schedule: Run daily (recommend 23:30 UTC, after main backup)
#
# Performs:
# 1. List all backups in S3
# 2. Filter for backups > 30 days old
# 3. Delete old backups
# 4. Log deleted files
# 5. Verify deletion
##############################################################################

set -euo pipefail

# Configuration from environment or defaults
S3_BUCKET="${S3_BUCKET:-electoral-backups}"
S3_REGION="${S3_REGION:-us-east-1}"
RETENTION_DAYS="${RETENTION_DAYS:-30}"
BACKUP_BASE_PATH="${BACKUP_BASE_PATH:-/tmp}"
LOG_FILE="${BACKUP_BASE_PATH}/retention-policy-$(date +%Y%m%d-%H%M%S).log"

# Logging function
log() {
  echo "[$(date '+%Y-%m-%d %H:%M:%S')] $*" | tee -a "${LOG_FILE}"
}

log "=== Starting retention policy job ==="
log "Retention period: ${RETENTION_DAYS} days"
log "S3 bucket: ${S3_BUCKET}"
log "Region: ${S3_REGION}"

##############################################################################
# STEP 1: Check AWS CLI availability
##############################################################################
if ! command -v aws &> /dev/null; then
  log "ERROR: aws CLI not found"
  exit 1
fi

##############################################################################
# STEP 2: Calculate cutoff date (30 days ago)
##############################################################################
log "Step 1: Calculating cutoff date..."

CUTOFF_DATE=$(date -d "-${RETENTION_DAYS} days" +%Y-%m-%d)
CUTOFF_TIMESTAMP=$(date -d "-${RETENTION_DAYS} days" +%s)

log "Cutoff date: ${CUTOFF_DATE}"
log "Backups created before ${CUTOFF_DATE} will be deleted"

##############################################################################
# STEP 3: List and delete old SQL backups
##############################################################################
log "Step 2: Deleting old SQL backups..."

DELETE_COUNT=0
ERROR_COUNT=0

# List all backups in postgres directory
if ! aws s3 ls "s3://${S3_BUCKET}/postgres/" --region "${S3_REGION}" 2>> "${LOG_FILE}" | \
   while read -r date_str time_str size_str filename; do
     if [ -z "$filename" ]; then
       continue
     fi

     # Extract date from filename (format: backup-YYYY-MM-DD.sql.gz)
     BACKUP_DATE=$(echo "$filename" | grep -oP '\d{4}-\d{2}-\d{2}' || true)
     if [ -z "$BACKUP_DATE" ]; then
       continue
     fi

     # Compare dates - if backup_date < cutoff_date, delete
     if [[ "$BACKUP_DATE" < "$CUTOFF_DATE" ]]; then
       log "Deleting old backup: ${filename}"

       if aws s3 rm "s3://${S3_BUCKET}/postgres/${filename}" \
         --region "${S3_REGION}" 2>> "${LOG_FILE}"; then
         ((DELETE_COUNT++))
         log "  ✓ Deleted: ${filename}"
       else
         ((ERROR_COUNT++))
         log "  ✗ ERROR: Failed to delete ${filename}"
       fi
     fi
   done; then
  log "SQL backup deletion completed"
else
  log "WARNING: Could not list S3 postgres directory"
fi

##############################################################################
# STEP 4: Delete old actas archives
##############################################################################
log "Step 3: Deleting old actas archives..."

if ! aws s3 ls "s3://${S3_BUCKET}/actas/" --region "${S3_REGION}" --recursive 2>> "${LOG_FILE}" | \
   while read -r date_str time_str size_str full_path; do
     if [ -z "$full_path" ]; then
       continue
     fi

     # Extract date from path (format: actas/YYYY-MM-DD/...)
     BACKUP_DATE=$(echo "$full_path" | grep -oP 'actas/\K\d{4}-\d{2}-\d{2}' || true)
     if [ -z "$BACKUP_DATE" ]; then
       continue
     fi

     # Compare dates
     if [[ "$BACKUP_DATE" < "$CUTOFF_DATE" ]]; then
       log "Deleting old actas: ${full_path}"

       if aws s3 rm "s3://${S3_BUCKET}/${full_path}" \
         --region "${S3_REGION}" 2>> "${LOG_FILE}"; then
         ((DELETE_COUNT++))
         log "  ✓ Deleted: ${full_path}"
       else
         ((ERROR_COUNT++))
         log "  ✗ ERROR: Failed to delete ${full_path}"
       fi
     fi
   done; then
  log "Actas deletion completed"
else
  log "WARNING: Could not list S3 actas directory"
fi

##############################################################################
# STEP 5: Delete old checksum files
##############################################################################
log "Step 4: Deleting old checksum files..."

if ! aws s3 ls "s3://${S3_BUCKET}/checksums/" --region "${S3_REGION}" 2>> "${LOG_FILE}" | \
   while read -r date_str time_str size_str filename; do
     if [ -z "$filename" ]; then
       continue
     fi

     # Extract date from filename
     BACKUP_DATE=$(echo "$filename" | grep -oP '\d{4}-\d{2}-\d{2}' || true)
     if [ -z "$BACKUP_DATE" ]; then
       continue
     fi

     if [[ "$BACKUP_DATE" < "$CUTOFF_DATE" ]]; then
       log "Deleting old checksum: ${filename}"

       if aws s3 rm "s3://${S3_BUCKET}/checksums/${filename}" \
         --region "${S3_REGION}" 2>> "${LOG_FILE}"; then
         ((DELETE_COUNT++))
         log "  ✓ Deleted: ${filename}"
       else
         ((ERROR_COUNT++))
         log "  ✗ ERROR: Failed to delete ${filename}"
       fi
     fi
   done; then
  log "Checksum deletion completed"
else
  log "WARNING: Could not list S3 checksums directory"
fi

##############################################################################
# STEP 6: Verify remaining backups
##############################################################################
log "Step 5: Verifying remaining backups..."

REMAINING_POSTGRES=$(aws s3 ls "s3://${S3_BUCKET}/postgres/" \
  --region "${S3_REGION}" 2>> "${LOG_FILE}" | wc -l || echo 0)
REMAINING_ACTAS=$(aws s3 ls "s3://${S3_BUCKET}/actas/" \
  --region "${S3_REGION}" --recursive 2>> "${LOG_FILE}" | wc -l || echo 0)

log "Remaining postgres backups: ${REMAINING_POSTGRES}"
log "Remaining actas entries: ${REMAINING_ACTAS}"

##############################################################################
# Final Status
##############################################################################
if [ "${ERROR_COUNT}" -eq 0 ]; then
  log "=== Retention policy completed successfully ==="
  log "Total deleted: ${DELETE_COUNT}"
  log "S3 bucket: ${S3_BUCKET}"
  exit 0
else
  log "=== Retention policy completed with errors ==="
  log "Total deleted: ${DELETE_COUNT}"
  log "Errors: ${ERROR_COUNT}"
  exit 1
fi
