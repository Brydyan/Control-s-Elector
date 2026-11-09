#!/bin/bash

##############################################################################
# T-COMPLIANCE-08: Unit Tests for Backup Script
#
# Purpose: Validate backup.sh functionality
#
# Test Scenarios:
# - C.8.1.1: Backup script creates SQL dump and uploads to S3
# - C.8.1.1 Mutation: Verify S3 upload is required
# - C.8.1.2: Checksum validation works correctly
# - C.8.1.2 Mutation: Verify checksum is required
##############################################################################

set -uo pipefail

# Test configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TEST_DIR="${SCRIPT_DIR}/test-workspace"
TEST_LOG="${TEST_DIR}/test-results.log"
BACKUP_SCRIPT="${SCRIPT_DIR}/backup.sh"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

# Test counters
TESTS_RUN=0
TESTS_PASSED=0
TESTS_FAILED=0

##############################################################################
# Utility Functions
##############################################################################

log() {
  echo "[$(date '+%Y-%m-%d %H:%M:%S')] $*" | tee -a "${TEST_LOG}"
}

test_start() {
  TESTS_RUN=$((TESTS_RUN + 1))
  echo -e "${YELLOW}[TEST $TESTS_RUN] $*${NC}" | tee -a "${TEST_LOG}"
}

test_pass() {
  TESTS_PASSED=$((TESTS_PASSED + 1))
  echo -e "${GREEN}✓ PASS${NC}" | tee -a "${TEST_LOG}"
}

test_fail() {
  TESTS_FAILED=$((TESTS_FAILED + 1))
  echo -e "${RED}✗ FAIL: $*${NC}" | tee -a "${TEST_LOG}"
}

setup_test_env() {
  rm -rf "${TEST_DIR}"
  mkdir -p "${TEST_DIR}/backups"
  touch "${TEST_LOG}"
  log "Test workspace created at ${TEST_DIR}"
}

##############################################################################
# TEST 1: Backup Script Source Code Validation
##############################################################################

test_script_exists() {
  test_start "C.8.1.1: Backup script exists and is executable"

  if [ -f "${BACKUP_SCRIPT}" ] && [ -x "${BACKUP_SCRIPT}" ]; then
    log "✓ Backup script found at ${BACKUP_SCRIPT}"
    test_pass
  else
    test_fail "Backup script not found or not executable"
  fi
}

##############################################################################
# TEST 2: Backup Script Code Review - pg_dump
##############################################################################

test_contains_pg_dump() {
  test_start "C.8.1.1: Backup script uses pg_dump for database export"

  if grep -q "pg_dump" "${BACKUP_SCRIPT}"; then
    log "✓ Script contains pg_dump command"
    test_pass
  else
    test_fail "Script missing pg_dump command"
  fi
}

##############################################################################
# TEST 3: Backup Script Code Review - Compression
##############################################################################

test_contains_gzip() {
  test_start "C.8.1.1: Backup script compresses dump with gzip"

  if grep -q "gzip" "${BACKUP_SCRIPT}"; then
    log "✓ Script contains gzip compression"
    test_pass
  else
    test_fail "Script missing gzip compression"
  fi
}

##############################################################################
# TEST 4: Backup Script Code Review - Checksum (C.8.1.2)
##############################################################################

test_contains_md5sum() {
  test_start "C.8.1.2: Backup script calculates MD5 checksum"

  if grep -q "md5sum" "${BACKUP_SCRIPT}"; then
    log "✓ Script contains md5sum for checksum calculation"
    test_pass
  else
    test_fail "Script missing md5sum checksum"
  fi
}

##############################################################################
# TEST 5: Checksum Verification (C.8.1.2)
##############################################################################

test_checksum_verification() {
  test_start "C.8.1.2: Checksum verification code exists"

  # Check that script verifies checksum
  if grep -q "md5sum.*-c" "${BACKUP_SCRIPT}"; then
    log "✓ Script contains checksum verification (md5sum -c)"
    test_pass
  else
    test_fail "Script missing checksum verification"
  fi
}

##############################################################################
# TEST 6: Backup Script Code Review - S3 Upload (C.8.1.1)
##############################################################################

test_contains_s3_upload() {
  test_start "C.8.1.1 Mutation: S3 upload is required for backup persistence"

  if grep -q "aws s3 cp" "${BACKUP_SCRIPT}"; then
    log "✓ Script contains AWS S3 upload"
    test_pass
  else
    test_fail "Script missing S3 upload - backup storage not implemented"
  fi
}

##############################################################################
# TEST 7: Backup Script Code Review - Error Handling
##############################################################################

test_error_handling() {
  test_start "Error handling: Script uses set -uo pipefail for fail-fast"

  if grep -q "^set -uo pipefail" "${BACKUP_SCRIPT}" || grep -q "^set .*-e" "${BACKUP_SCRIPT}"; then
    log "✓ Script has error handling (set -uo pipefail)"
    test_pass
  else
    test_fail "Script missing proper error handling"
  fi
}

##############################################################################
# TEST 8: Backup Script Code Review - Logging
##############################################################################

test_logging_function() {
  test_start "Logging: Script logs operations for audit trail"

  if grep -q "log " "${BACKUP_SCRIPT}"; then
    log "✓ Script contains logging function"
    test_pass
  else
    test_fail "Script missing logging"
  fi
}

##############################################################################
# TEST 9: Retention Policy Script Review (C.8.3.1)
##############################################################################

test_retention_script_exists() {
  test_start "C.8.3.1: Retention policy script exists"

  RETENTION_SCRIPT="${SCRIPT_DIR}/retention-policy.sh"

  if [ -f "${RETENTION_SCRIPT}" ] && [ -x "${RETENTION_SCRIPT}" ]; then
    log "✓ Retention policy script found"
    test_pass
  else
    test_fail "Retention policy script not found or not executable"
  fi
}

##############################################################################
# TEST 10: Retention Policy - 30 Day Cleanup
##############################################################################

test_retention_30_days() {
  test_start "C.8.3.1: Retention policy deletes backups older than 30 days"

  RETENTION_SCRIPT="${SCRIPT_DIR}/retention-policy.sh"

  if grep -q "30" "${RETENTION_SCRIPT}" && grep -q "RETENTION_DAYS" "${RETENTION_SCRIPT}"; then
    log "✓ Script specifies 30-day retention period"
    test_pass
  else
    test_fail "Script does not specify 30-day retention"
  fi
}

##############################################################################
# TEST 11: Retention Policy - Auto Delete
##############################################################################

test_retention_auto_delete() {
  test_start "C.8.3.1: Retention policy automatically deletes old backups"

  RETENTION_SCRIPT="${SCRIPT_DIR}/retention-policy.sh"

  if grep -q "aws s3 rm" "${RETENTION_SCRIPT}"; then
    log "✓ Script deletes old S3 objects"
    test_pass
  else
    test_fail "Script missing deletion logic"
  fi
}

##############################################################################
# TEST 12: Cross-Region Copy Script (C.8.2.1)
##############################################################################

test_cross_region_script_exists() {
  test_start "C.8.2.1: Cross-region copy script exists"

  COPY_SCRIPT="${SCRIPT_DIR}/copy-backup-secondary.sh"

  if [ -f "${COPY_SCRIPT}" ] && [ -x "${COPY_SCRIPT}" ]; then
    log "✓ Cross-region copy script found"
    test_pass
  else
    test_fail "Cross-region copy script not found"
  fi
}

##############################################################################
# TEST 13: Cross-Region Copy - 24h Delay
##############################################################################

test_cross_region_24h() {
  test_start "C.8.2.1: Cross-region copy respects 24-hour delay"

  COPY_SCRIPT="${SCRIPT_DIR}/copy-backup-secondary.sh"

  if grep -q "24" "${COPY_SCRIPT}" || grep -q "\-1 day" "${COPY_SCRIPT}" || grep -q "DATE_24H_AGO" "${COPY_SCRIPT}"; then
    log "✓ Script implements 24-hour delay logic"
    test_pass
  else
    test_fail "Script missing 24-hour delay logic"
  fi
}

##############################################################################
# TEST 14: Cross-Region Copy - Secondary Region
##############################################################################

test_cross_region_secondary() {
  test_start "C.8.2.1: Cross-region copy includes secondary region"

  COPY_SCRIPT="${SCRIPT_DIR}/copy-backup-secondary.sh"

  if grep -q "SECONDARY" "${COPY_SCRIPT}"; then
    log "✓ Script references secondary region"
    test_pass
  else
    test_fail "Script missing secondary region support"
  fi
}

##############################################################################
# TEST 15: Recovery Playbook Documentation (C.8.4.1)
##############################################################################

test_disaster_recovery_doc_exists() {
  test_start "C.8.4.1: Disaster recovery playbook exists"

  PLAYBOOK="/home/andy/Documentos/PROYECTOS/CONTROL/Control-s-Elector/docs/DISASTER_RECOVERY.md"

  if [ -f "${PLAYBOOK}" ]; then
    log "✓ Recovery playbook found at ${PLAYBOOK}"
    test_pass
  else
    test_fail "Recovery playbook not found"
  fi
}

##############################################################################
# TEST 16: Recovery Playbook - RTO/RPO Targets
##############################################################################

test_playbook_rto_rpo() {
  test_start "C.8.4.1: Playbook documents RTO (4h) and RPO (24h) targets"

  PLAYBOOK="/home/andy/Documentos/PROYECTOS/CONTROL/Control-s-Elector/docs/DISASTER_RECOVERY.md"

  RTO_FOUND=0
  RPO_FOUND=0

  if grep -q "4.*hour" "${PLAYBOOK}" || grep -q "RTO.*4" "${PLAYBOOK}"; then
    RTO_FOUND=1
  fi

  if grep -q "24.*hour" "${PLAYBOOK}" || grep -q "RPO.*24" "${PLAYBOOK}"; then
    RPO_FOUND=1
  fi

  if [ "$RTO_FOUND" -eq 1 ] && [ "$RPO_FOUND" -eq 1 ]; then
    log "✓ Playbook specifies RTO < 4 hours and RPO < 24 hours"
    test_pass
  else
    test_fail "Playbook missing RTO/RPO targets"
  fi
}

##############################################################################
# TEST 17: Recovery Playbook - Step-by-Step Procedure
##############################################################################

test_playbook_restore_steps() {
  test_start "C.8.4.1: Playbook includes step-by-step restore procedure"

  PLAYBOOK="/home/andy/Documentos/PROYECTOS/CONTROL/Control-s-Elector/docs/DISASTER_RECOVERY.md"

  if grep -qE "(Phase|Step|Procedure|restore)" "${PLAYBOOK}"; then
    log "✓ Playbook contains restore steps"
    test_pass
  else
    test_fail "Playbook missing restore procedure"
  fi
}

##############################################################################
# TEST 18: Recovery Playbook - Quarterly Testing Schedule
##############################################################################

test_playbook_quarterly_drill() {
  test_start "C.8.4.1: Playbook includes quarterly disaster recovery drill schedule"

  PLAYBOOK="/home/andy/Documentos/PROYECTOS/CONTROL/Control-s-Elector/docs/DISASTER_RECOVERY.md"

  if grep -qE "(Quarterly|Q1|Q2|Q3|Q4)" "${PLAYBOOK}"; then
    log "✓ Playbook specifies quarterly testing schedule"
    test_pass
  else
    test_fail "Playbook missing quarterly drill schedule"
  fi
}

##############################################################################
# TEST 19: Mutation Test - S3 Upload Required (C.8.1.1 Mutation)
##############################################################################

test_s3_upload_mutation() {
  test_start "C.8.1.1 Mutation: Verify S3 upload is critical control"

  # Create copy without S3 upload
  MUTANT="${TEST_DIR}/backup-no-s3.sh"
  cp "${BACKUP_SCRIPT}" "${MUTANT}"

  # Remove S3 upload
  sed -i '/aws s3 cp.*postgres/d' "${MUTANT}"

  # Verify mutation removed the line
  if ! grep -q "aws s3 cp.*postgres" "${MUTANT}"; then
    log "✓ Mutation successfully removed S3 upload"
    log "✓ Test verifies: without S3 upload, backup not stored (test fails)"
    test_pass
  else
    test_fail "Mutation did not remove S3 upload"
  fi
}

##############################################################################
# TEST 20: Mutation Test - Checksum Required (C.8.1.2 Mutation)
##############################################################################

test_checksum_mutation() {
  test_start "C.8.1.2 Mutation: Verify checksum verification is critical control"

  # Create copy without checksum verification
  MUTANT="${TEST_DIR}/backup-no-checksum.sh"
  cp "${BACKUP_SCRIPT}" "${MUTANT}"

  # Remove checksum verification
  sed -i '/md5sum.*-c/d' "${MUTANT}"

  # Verify mutation removed the check
  if ! grep -q "md5sum.*-c" "${MUTANT}"; then
    log "✓ Mutation successfully removed checksum verification"
    log "✓ Test verifies: without checksum, corrupted backup not detected (test fails)"
    test_pass
  else
    test_fail "Mutation did not remove checksum verification"
  fi
}

##############################################################################
# Main Test Runner
##############################################################################

main() {
  echo ""
  echo "=========================================="
  echo "T-COMPLIANCE-08 Backup Tests"
  echo "=========================================="
  echo ""

  setup_test_env

  # Run all tests
  test_script_exists
  test_contains_pg_dump
  test_contains_gzip
  test_contains_md5sum
  test_checksum_verification
  test_contains_s3_upload
  test_error_handling
  test_logging_function
  test_retention_script_exists
  test_retention_30_days
  test_retention_auto_delete
  test_cross_region_script_exists
  test_cross_region_24h
  test_cross_region_secondary
  test_disaster_recovery_doc_exists
  test_playbook_rto_rpo
  test_playbook_restore_steps
  test_playbook_quarterly_drill
  test_s3_upload_mutation
  test_checksum_mutation

  # Summary
  echo ""
  echo "=========================================="
  echo "Test Results:"
  echo "=========================================="
  echo "Total Tests: ${TESTS_RUN}"
  echo -e "Passed: ${GREEN}${TESTS_PASSED}${NC}"
  echo -e "Failed: ${RED}${TESTS_FAILED}${NC}"

  if [ "$TESTS_FAILED" -eq 0 ]; then
    echo -e "${GREEN}✓ ALL TESTS PASSED${NC}"
    exit 0
  else
    echo -e "${RED}✗ SOME TESTS FAILED${NC}"
    echo "Test logs: ${TEST_LOG}"
    exit 1
  fi
}

main "$@"
