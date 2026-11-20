#!/bin/bash

#######################################################################
# Validate Shortcut Ticket JSON Files
#
# Usage: ./validate-shortcut-json.sh [tickets-file.json]
#        ./validate-shortcut-json.sh data/tickets/*.json  (all files)
#
#######################################################################

set -e

GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

log() { echo -e "${BLUE}ℹ️  $1${NC}"; }
ok() { echo -e "${GREEN}✅ $1${NC}"; }
warn() { echo -e "${YELLOW}⚠️  $1${NC}"; }
err() { echo -e "${RED}❌ $1${NC}"; }

# ─── Validation ────────────────────────────────────────────────────

if [ $# -eq 0 ]; then
  err "Usage: $0 <file.json> [file2.json ...]"
  echo ""
  echo "Examples:"
  echo "  $0 data/tickets/odd-consolidate-modal.json"
  echo "  $0 data/tickets/*.json"
  exit 1
fi

TOTAL_FILES=0
VALID_FILES=0
INVALID_FILES=0

for file in "$@"; do
  TOTAL_FILES=$((TOTAL_FILES + 1))

  if [ ! -f "$file" ]; then
    err "File not found: $file"
    INVALID_FILES=$((INVALID_FILES + 1))
    continue
  fi

  echo ""
  log "Validating: $file"

  # Check if JSON is valid
  if ! jq empty "$file" 2>/dev/null; then
    err "Invalid JSON syntax"
    INVALID_FILES=$((INVALID_FILES + 1))
    continue
  fi

  # Check structure
  tickets_count=$(jq '.tickets | length' "$file" 2>/dev/null)

  if [ -z "$tickets_count" ] || [ "$tickets_count" -eq 0 ]; then
    warn "No tickets found in .tickets array"
  fi

  # Validate each ticket
  errors=0
  jq -c '.tickets[] | {index: .name, name, description, estimate}' "$file" | while IFS= read -r ticket; do
    name=$(echo "$ticket" | jq -r '.name // empty')
    desc=$(echo "$ticket" | jq -r '.description // empty')
    estimate=$(echo "$ticket" | jq -r '.estimate // empty')

    if [ -z "$name" ]; then
      err "  Missing 'name' field"
      errors=$((errors + 1))
    else
      echo "    ✓ Name: $(echo "$name" | head -c 60)..."
    fi

    if [ -z "$desc" ]; then
      warn "  Missing 'description' field (optional)"
    else
      desc_len=${#desc}
      echo "    ✓ Description: $desc_len chars"
    fi

    if [ -n "$estimate" ] && ! [[ "$estimate" =~ ^[0-9]+$ ]]; then
      err "  Invalid estimate (must be numeric): $estimate"
      errors=$((errors + 1))
    fi
  done

  if [ $errors -eq 0 ]; then
    ok "$file — $tickets_count ticket(s) valid"
    VALID_FILES=$((VALID_FILES + 1))
  else
    INVALID_FILES=$((INVALID_FILES + 1))
  fi
done

# ─── Summary ───────────────────────────────────────────────────────

echo ""
log "Summary:"
ok "$VALID_FILES/$TOTAL_FILES files valid"
[ $INVALID_FILES -gt 0 ] && warn "$INVALID_FILES files with errors"
echo ""

exit $([ $INVALID_FILES -eq 0 ] && echo 0 || echo 1)
