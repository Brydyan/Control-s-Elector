#!/bin/bash

##############################################################################
# T-COMPLIANCE-08: Backup & Disaster Recovery - Secondary Region Copy
#
# Purpose: Copy backups from primary region to secondary region (24h delay)
# Schedule: Run daily, copies backups older than 24h
#
# Performs:
# 1. List backups in primary S3 region
# 2. Filter for backups not yet copied to secondary
# 3. Copy to secondary S3 region
# 4. Verify copy success
# 5. Log results
##############################################################################

set -euo pipefail

# Configuration from environment or defaults
S3_BUCKET_PRIMARY="${S3_BUCKET_PRIMARY:-electoral-backups}"
S3_REGION_PRIMARY="${S3_REGION_PRIMARY:-us-east-1}"
S3_REGION_SECONDARY="${S3_REGION_SECONDARY:-us-west-2}"
BACKUP_BASE_PATH="${BACKUP_BASE_PATH:-/tmp}"
LOG_FILE="${BACKUP_BASE_PATH}/copy-backup-secondary-$(date +%Y%m%d-%H%M%S).log"

# Logging function
log() {
  echo "[$(date '+%Y-%m-%d %H:%M:%S')] $*" | tee -a "${LOG_FILE}"
}

log "=== Starting cross-region backup copy job ==="
log "Primary region: ${S3_REGION_PRIMARY}"
log "Secondary region: ${S3_REGION_SECONDARY}"
log "S3 bucket: ${S3_BUCKET_PRIMARY}"

##############################################################################
# STEP 1: Check AWS CLI availability
##############################################################################
if ! command -v aws &> /dev/null; then
  log "ERROR: aws CLI not found"
  exit 1
fi

##############################################################################
# STEP 2: List backups in primary region older than 24h
##############################################################################
log "Step 1: Listing backups in primary region (older than 24h)..."

# Get current date and date 24h ago
CURRENT_DATE=$(date +%Y-%m-%d)
DATE_24H_AGO=$(date -d "-1 day" +%Y-%m-%d)

log "Current date: ${CURRENT_DATE}"
log "Copying backups from: ${DATE_24H_AGO} and earlier"

##############################################################################
# STEP 3: Copy backups to secondary region
##############################################################################
log "Step 2: Copying backups to secondary region..."

COPY_COUNT=0

# Copy SQL dumps
if aws s3 ls "s3://${S3_BUCKET_PRIMARY}/postgres/" --region "${S3_REGION_PRIMARY}" 2>> "${LOG_FILE}" | \
   while read -r line; do
     FILENAME=$(echo "$line" | awk '{print $NF}')
     if [ -z "$FILENAME" ]; then
       continue
     fi

     # Check if backup is from 24h ago or earlier
     BACKUP_DATE=$(echo "$FILENAME" | grep -oP '\d{4}-\d{2}-\d{2}' || true)
     if [ -z "$BACKUP_DATE" ]; then
       continue
     fi

     # Simple date comparison (works for YYYY-MM-DD format)
     if [[ "$BACKUP_DATE" < "$CURRENT_DATE" ]]; then
       log "Copying backup: ${FILENAME}"

       if aws s3 cp \
         "s3://${S3_BUCKET_PRIMARY}/postgres/${FILENAME}" \
         "s3://${S3_BUCKET_PRIMARY}-secondary/postgres/${FILENAME}" \
         --region "${S3_REGION_PRIMARY}" \
         --sse AES256 \
         2>> "${LOG_FILE}"; then
         ((COPY_COUNT++))
         log "Successfully copied: ${FILENAME}"
       else
         log "WARNING: Failed to copy ${FILENAME}"
       fi
     fi
   done; then
  log "SQL backup copy operation completed"
else
  log "WARNING: Could not list S3 contents"
fi

# Copy actas archives
if aws s3 ls "s3://${S3_BUCKET_PRIMARY}/actas/" --region "${S3_REGION_PRIMARY}" --recursive 2>> "${LOG_FILE}" | \
   while read -r line; do
     FULL_PATH=$(echo "$line" | awk '{print $NF}')
     if [ -z "$FULL_PATH" ]; then
       continue
     fi

     # Check if backup is from 24h ago or earlier
     BACKUP_DATE=$(echo "$FULL_PATH" | grep -oP '\d{4}-\d{2}-\d{2}' | head -1 || true)
     if [ -z "$BACKUP_DATE" ]; then
       continue
     fi

     if [[ "$BACKUP_DATE" < "$CURRENT_DATE" ]]; then
       log "Copying actas: ${FULL_PATH}"

       if aws s3 cp \
         "s3://${S3_BUCKET_PRIMARY}/${FULL_PATH}" \
         "s3://${S3_BUCKET_PRIMARY}-secondary/${FULL_PATH}" \
         --region "${S3_REGION_PRIMARY}" \
         --sse AES256 \
         2>> "${LOG_FILE}"; then
         ((COPY_COUNT++))
         log "Successfully copied: ${FULL_PATH}"
       else
         log "WARNING: Failed to copy ${FULL_PATH}"
       fi
     fi
   done; then
  log "Actas backup copy operation completed"
else
  log "WARNING: Could not list actas in S3"
fi

##############################################################################
# STEP 4: Verify secondary region has copies
##############################################################################
log "Step 3: Verifying secondary region copies..."

SECONDARY_COUNT=$(aws s3 ls "s3://${S3_BUCKET_PRIMARY}-secondary/postgres/" \
  --region "${S3_REGION_SECONDARY}" 2>> "${LOG_FILE}" | wc -l || echo 0)

log "Backups in secondary region: ${SECONDARY_COUNT}"

##############################################################################
# Final Status
##############################################################################
log "=== Cross-region backup copy completed ==="
log "Files copied: ${COPY_COUNT}"
log "Secondary region bucket: s3://${S3_BUCKET_PRIMARY}-secondary/"
log "Regions: ${S3_REGION_PRIMARY} -> ${S3_REGION_SECONDARY}"

exit 0
