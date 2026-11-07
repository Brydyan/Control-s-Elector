#!/bin/bash

#######################################################################
# Create Shortcut Tickets for 2 New SDDs (INFRASTRUCTURE + LEGAL)
#######################################################################

# Load API key from .env
if [ -f .env ]; then
  export $(grep API_SHORTCUT_KEY .env | xargs)
fi

API_KEY="${API_SHORTCUT_KEY}"
BASE_URL="https://api.app.shortcut.com/api/v3"
EPIC_ID=108
WORKFLOW_STATE_ID=500000006  # Backlog

GREEN='\033[0;32m'
BLUE='\033[0;34m'
RED='\033[0;31m'
NC='\033[0m'

log() { echo -e "${BLUE}ℹ️  $1${NC}"; }
ok() { echo -e "${GREEN}✅ $1${NC}"; }
err() { echo -e "${RED}❌ $1${NC}"; }

log ""
log "Creating 2 new SDD tickets in epic 📊 CONTROL's..."
log ""

CREATED=0

# Create each ticket
create_ticket() {
  local name="$1"
  local desc="$2"

  echo -n "  $name ... "

  local payload=$(jq -n \
    --arg name "$name" \
    --arg desc "$desc" \
    --argjson epic_id $EPIC_ID \
    --argjson workflow_state_id $WORKFLOW_STATE_ID \
    '{name: $name, description: $desc, epic_id: $epic_id, workflow_state_id: $workflow_state_id}')

  local response=$(curl -s -X POST \
    -H "Shortcut-Token: $API_KEY" \
    -H "Content-Type: application/json" \
    -d "$payload" \
    "$BASE_URL/stories")

  local id=$(echo "$response" | jq -r '.id // empty')

  if [ -n "$id" ]; then
    ok "ID: $id"
    ((CREATED++))
  else
    err "Failed"
    echo "Response: $response"
  fi
}

# 🔴 CRITICAL
create_ticket \
  "T-INFRASTRUCTURE-01: CORS & Security Headers" \
  "🔴 CRITICAL | 10 pts\n\nImplement CORS configuration and security headers\n\nSDD: openspec/changes/compliance/2026-11-06-t-infrastructure-01-cors-security-headers/\n\n✅ CORS whitelist per environment (prod/dev/staging)\n✅ Preflight OPTIONS handling (no JWT validation)\n✅ Security headers: CSP, HSTS, X-Frame-Options, X-Content-Type-Options, X-XSS-Protection\n✅ Refresh token: HttpOnly + Secure + SameSite=Strict\n✅ Tests: unit + integration\n✅ Documentation"

create_ticket \
  "T-LEGAL-01: Privacy Policy + Terms of Use" \
  "🔴 CRITICAL | 13 pts\n\nImplement legal compliance: privacy policy, terms, GDPR Art. 15 export\n\nSDD: openspec/changes/compliance/2026-11-06-t-legal-01-privacy-terms/\n\n✅ Static /privacy + /terms pages (multi-language ES+EN)\n✅ Terms acceptance modal on login (mandatory)\n✅ GET /api/v1/user/export-data (GDPR Art. 15)\n✅ POST /api/v1/user/accept-terms + audit table\n✅ POST /api/v1/legal/contact-dpo (DPO form)\n✅ Rate limiting: export 1/24h, DPO 1/30s\n✅ Versionado de términos (v1.0, v1.1, etc)\n✅ Database migrations + tests"

log ""
ok "$CREATED/2 tickets created in epic 📊 CONTROL's"
log ""
log "📊 View: https://app.shortcut.com/epics/$EPIC_ID"
log ""
