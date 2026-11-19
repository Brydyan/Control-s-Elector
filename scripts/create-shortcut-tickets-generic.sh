#!/bin/bash

#######################################################################
# Generic Shortcut Ticket Creator
#
# Usage: ./create-shortcut-tickets-generic.sh [tickets-file.json] [--epic=ID] [--state=ID]
#
# Examples:
#   ./create-shortcut-tickets-generic.sh data/tickets/odd-consolidate-modal.json
#   ./create-shortcut-tickets-generic.sh data/tickets/odds.json --epic=108
#   SHORTCUT_API_KEY=... ./create-shortcut-tickets-generic.sh ...
#
#######################################################################

set -e

# ─── Colors ────────────────────────────────────────────────────────
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

log() { echo -e "${BLUE}ℹ️  $1${NC}"; }
ok() { echo -e "${GREEN}✅ $1${NC}"; }
warn() { echo -e "${YELLOW}⚠️  $1${NC}"; }
err() { echo -e "${RED}❌ $1${NC}"; }

# ─── Configuration ──────────────────────────────────────────────────

# Load from .env if exists
if [ -f .env ]; then
  export $(grep -v '^#' .env | xargs)
fi

# CLI arguments override .env
TICKETS_FILE="${1}"
EPIC_ID="${EPIC_ID:-108}"
WORKFLOW_STATE_ID="${WORKFLOW_STATE_ID:-500000006}"

# Parse --epic and --state flags
while [[ $# -gt 1 ]]; do
  case "$2" in
    --epic=*) EPIC_ID="${2#*=}"; shift ;;
    --state=*) WORKFLOW_STATE_ID="${2#*=}"; shift ;;
    *) shift ;;
  esac
done

API_KEY="${SHORTCUT_API_KEY}"
BASE_URL="https://api.app.shortcut.com/api/v3"

# ─── Validation ────────────────────────────────────────────────────

if [ -z "$TICKETS_FILE" ]; then
  err "Usage: $0 <tickets-file.json> [--epic=ID] [--state=ID]"
  echo ""
  echo "  tickets-file.json — JSON file with ticket definitions"
  echo "  --epic=ID         — Epic ID (default: 108)"
  echo "  --state=ID        — Workflow state ID (default: 500000006 = Backlog)"
  echo ""
  echo "Environment variables:"
  echo "  SHORTCUT_API_KEY  — Shortcut API token (or load from .env)"
  echo ""
  exit 1
fi

if [ ! -f "$TICKETS_FILE" ]; then
  err "Tickets file not found: $TICKETS_FILE"
  exit 1
fi

if [ -z "$API_KEY" ]; then
  err "Missing SHORTCUT_API_KEY. Set it in .env or as environment variable."
  exit 1
fi

# ─── Validate API Key ──────────────────────────────────────────────

log "Validating API key..."
me_response=$(curl -s -X GET \
  -H "Shortcut-Token: $API_KEY" \
  "$BASE_URL/me")

me_id=$(echo "$me_response" | jq -r '.id // empty' 2>/dev/null)

if [ -z "$me_id" ]; then
  err "Invalid API key or connection failed."
  err "Response: $(echo "$me_response" | jq -r '.message // .')"
  exit 1
fi

ok "API key valid (user: $(echo "$me_response" | jq -r '.name'))"

# ─── Validate Epic & State ─────────────────────────────────────────

log "Validating epic ID $EPIC_ID..."
epic_response=$(curl -s -X GET \
  -H "Shortcut-Token: $API_KEY" \
  "$BASE_URL/epics/$EPIC_ID")

epic_name=$(echo "$epic_response" | jq -r '.name // empty' 2>/dev/null)

if [ -z "$epic_name" ]; then
  err "Epic not found: $EPIC_ID"
  exit 1
fi

ok "Epic found: $epic_name"

# ─── Create Tickets ────────────────────────────────────────────────

log ""
log "Creating tickets from $TICKETS_FILE..."
log "Epic: $epic_name (ID: $EPIC_ID)"
log ""

TOTAL=0
CREATED=0
FAILED=0

create_ticket() {
  local name="$1"
  local desc="$2"
  local estimate="${3:-0}"

  TOTAL=$((TOTAL + 1))

  # Escape description for JSON
  local desc_escaped=$(echo "$desc" | jq -Rs '.')

  echo -n "  [$TOTAL] $name ... "

  local payload=$(jq -n \
    --arg name "$name" \
    --argjson desc "$desc_escaped" \
    --argjson epic_id "$EPIC_ID" \
    --argjson workflow_state_id "$WORKFLOW_STATE_ID" \
    --arg estimate "$estimate" \
    '{
      name: $name,
      description: $desc,
      epic_id: $epic_id,
      workflow_state_id: $workflow_state_id,
      estimate: (if $estimate != "0" then ($estimate | tonumber) else null end)
    }')

  local response=$(curl -s -w "\n%{http_code}" -X POST \
    -H "Shortcut-Token: $API_KEY" \
    -H "Content-Type: application/json" \
    -d "$payload" \
    "$BASE_URL/stories")

  # Split response and status code
  local body=$(echo "$response" | head -n -1)
  local http_code=$(echo "$response" | tail -n 1)

  local id=$(echo "$body" | jq -r '.id // empty' 2>/dev/null)

  if [ -n "$id" ] && [ "$http_code" = "201" ]; then
    ok "ID: $id"
    CREATED=$((CREATED + 1))
  else
    local err_msg=$(echo "$body" | jq -r '.message // .errors[0].message // "Unknown error"' 2>/dev/null)
    err "Failed (HTTP $http_code: $err_msg)"
    FAILED=$((FAILED + 1))
  fi
}

# Parse JSON and create tickets
jq -c '.tickets[]' "$TICKETS_FILE" 2>/dev/null | while read -r ticket; do
  name=$(echo "$ticket" | jq -r '.name // empty')
  desc=$(echo "$ticket" | jq -r '.description // ""')
  estimate=$(echo "$ticket" | jq -r '.estimate // "0"')

  if [ -z "$name" ]; then
    err "Skipping: missing 'name' field"
    continue
  fi

  create_ticket "$name" "$desc" "$estimate"
done

# ─── Summary ───────────────────────────────────────────────────────

log ""
ok "$CREATED/$TOTAL tickets created successfully"
[ $FAILED -gt 0 ] && warn "$FAILED tickets failed"
log ""
log "📊 View in Shortcut: https://app.shortcut.com/epics/$EPIC_ID"
log ""

# Exit with error if any failed
[ $FAILED -eq 0 ]
