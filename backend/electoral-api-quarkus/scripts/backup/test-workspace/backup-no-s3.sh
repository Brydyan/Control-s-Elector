#!/bin/bash

##############################################################################
# T-COMPLIANCE-08: Backup & Disaster Recovery - Main Backup Script
#
# Purpose: Daily automated backup of PostgreSQL database and electoral files
# Schedule: 23:00 UTC daily via cron
#
# Performs:
# 1. Full PostgreSQL dump
# 2. Archive configuration and asset files
# 3. Upload to S3 with encryption
# 4. Calculate and verify checksum (MD5)
# 5. Log completion with metadata
##############################################################################

set -euo pipefail

# Configuration from environment or defaults
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${DB_NAME:-electoral_db}"
DB_USER="${DB_USER:-postgres}"
S3_BUCKET="${S3_BUCKET:-electoral-backups}"
BACKUP_BASE_PATH="${BACKUP_BASE_PATH:-/tmp}"
LOG_FILE="${BACKUP_BASE_PATH}/backup-$(date +%Y%m%d-%H%M%S).log"

# Date formatting for backup naming
DATE=$(date +%Y-%m-%d)
TIMESTAMP=$(date +%s)

# Backup file names
BACKUP_SQL="backup-${DATE}.sql"
BACKUP_SQL_GZ="${BACKUP_SQL}.gz"
BACKUP_TAR="backup-${DATE}_actas.tar.gz"
BACKUP_CHECKSUM="backup-${DATE}.md5"

# Output directory
BACKUP_DIR="${BACKUP_BASE_PATH}/backups"
mkdir -p "${BACKUP_DIR}"

# Logging function
log() {
  echo "[$(date '+%Y-%m-%d %H:%M:%S')] $*" | tee -a "${LOG_FILE}"
}

log "=== Starting backup job ==="
log "DB_HOST: ${DB_HOST}, DB_NAME: ${DB_NAME}"
log "S3_BUCKET: ${S3_BUCKET}"
log "BACKUP_DIR: ${BACKUP_DIR}"

##############################################################################
# STEP 1: PostgreSQL Full Dump
##############################################################################
log "Step 1: Dumping PostgreSQL database..."

if ! pg_dump -U "${DB_USER}" -h "${DB_HOST}" -p "${DB_PORT}" -d "${DB_NAME}" \
  > "${BACKUP_DIR}/${BACKUP_SQL}" 2>> "${LOG_FILE}"; then
  log "ERROR: PostgreSQL dump failed"
  exit 1
fi

DUMP_SIZE=$(du -h "${BACKUP_DIR}/${BACKUP_SQL}" | cut -f1)
log "Database dump complete: ${DUMP_SIZE}"

##############################################################################
# STEP 2: Compress SQL dump
##############################################################################
log "Step 2: Compressing SQL dump..."

if ! gzip -f "${BACKUP_DIR}/${BACKUP_SQL}" 2>> "${LOG_FILE}"; then
  log "ERROR: Compression failed"
  exit 1
fi

COMPRESSED_SIZE=$(du -h "${BACKUP_DIR}/${BACKUP_SQL_GZ}" | cut -f1)
log "Compression complete: ${COMPRESSED_SIZE}"

##############################################################################
# STEP 3: Archive configuration and asset files (if they exist)
##############################################################################
log "Step 3: Archiving configuration and asset files..."

ARCHIVE_PATHS=()
if [ -d "/app/config" ]; then
  ARCHIVE_PATHS+=("/app/config")
fi
if [ -d "/app/assets" ]; then
  ARCHIVE_PATHS+=("/app/assets")
fi

if [ ${#ARCHIVE_PATHS[@]} -gt 0 ]; then
  if ! tar -czf "${BACKUP_DIR}/${BACKUP_TAR}" "${ARCHIVE_PATHS[@]}" 2>> "${LOG_FILE}"; then
    log "WARNING: Could not archive some files, continuing..."
  else
    ARCHIVE_SIZE=$(du -h "${BACKUP_DIR}/${BACKUP_TAR}" | cut -f1)
    log "Archive complete: ${ARCHIVE_SIZE}"
  fi
else
  log "No config/assets to archive, skipping tar creation"
  touch "${BACKUP_DIR}/${BACKUP_TAR}"
fi

##############################################################################
# STEP 4: Calculate and Verify Checksum
##############################################################################
log "Step 4: Calculating checksum..."

if ! md5sum "${BACKUP_DIR}/${BACKUP_SQL_GZ}" > "${BACKUP_DIR}/${BACKUP_CHECKSUM}" 2>> "${LOG_FILE}"; then
  log "ERROR: Checksum calculation failed"
  exit 1
fi

CHECKSUM=$(cat "${BACKUP_DIR}/${BACKUP_CHECKSUM}" | awk '{print $1}')
log "Checksum calculated: ${CHECKSUM}"

# Verify checksum by reading file back
if ! md5sum -c "${BACKUP_DIR}/${BACKUP_CHECKSUM}" >> "${LOG_FILE}" 2>&1; then
  log "ERROR: Checksum verification failed - backup may be corrupted"
  exit 1
fi

log "Checksum verified successfully"

##############################################################################
# STEP 5: Upload to S3
##############################################################################
log "Step 5: Uploading to S3..."

# Check if AWS CLI is available
if ! command -v aws &> /dev/null; then
  log "ERROR: aws CLI not found"
  exit 1
fi

# Upload compressed SQL dump
if ! aws s3 cp "${BACKUP_DIR}/${BACKUP_SQL_GZ}" \
  "s3://${S3_BUCKET}/postgres/${BACKUP_SQL_GZ}" \
  --metadata "timestamp=${TIMESTAMP},checksum=${CHECKSUM}" \
  --sse AES256 \
  2>> "${LOG_FILE}"; then
  log "ERROR: S3 upload of SQL dump failed"
  exit 1
fi

log "SQL dump uploaded to S3"

# Upload tar archive if it exists and has content
if [ -f "${BACKUP_DIR}/${BACKUP_TAR}" ] && [ -s "${BACKUP_DIR}/${BACKUP_TAR}" ]; then
  if ! aws s3 cp "${BACKUP_DIR}/${BACKUP_TAR}" \
    "s3://${S3_BUCKET}/actas/${DATE}/${BACKUP_TAR}" \
    --sse AES256 \
    2>> "${LOG_FILE}"; then
    log "WARNING: S3 upload of actas archive failed (non-fatal)"
  else
    log "Actas archive uploaded to S3"
  fi
fi

# Upload checksum file
if ! aws s3 cp "${BACKUP_DIR}/${BACKUP_CHECKSUM}" \
  "s3://${S3_BUCKET}/checksums/${BACKUP_CHECKSUM}" \
  --sse AES256 \
  2>> "${LOG_FILE}"; then
  log "WARNING: S3 upload of checksum failed"
fi

##############################################################################
# STEP 6: Cleanup local files (optional - keep for 3 days for recovery)
##############################################################################
log "Step 6: Cleanup old local backups (>3 days)..."

find "${BACKUP_DIR}" -name "backup-*.sql.gz" -mtime +3 -delete 2>> "${LOG_FILE}" || true
find "${BACKUP_DIR}" -name "backup-*.tar.gz" -mtime +3 -delete 2>> "${LOG_FILE}" || true
find "${BACKUP_DIR}" -name "backup-*.md5" -mtime +3 -delete 2>> "${LOG_FILE}" || true

##############################################################################
# Final Status
##############################################################################
log "=== Backup job completed successfully ==="
log "Backup files:"
log "  - SQL dump: ${BACKUP_SQL_GZ} (${COMPRESSED_SIZE})"
log "  - Actas archive: ${BACKUP_TAR}"
log "  - Checksum: ${CHECKSUM}"
log "  - S3 location: s3://${S3_BUCKET}/postgres/ and s3://${S3_BUCKET}/actas/"

exit 0
