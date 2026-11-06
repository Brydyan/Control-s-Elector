#!/bin/bash

#######################################################################
# Create Shortcut Tickets for 8 Compliance SDDs
#######################################################################

API_KEY="sct_rw_upse_NjhmOGRiOTUtYmFkYS00ZTA0LWFkMjgtYTQ0ZWJkZjU2ZjEzOjY4ZjhkYjk1LTg1MDQtNGM3Mi05ZjM1LTkyNmVkZGIxOTQ1YTphMzc1YTA1NDIzYjQ2ZWY3NGVjZGI2ZjEzMjYyNWI0Yw"
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
log "Creating 8 compliance tickets in epic 📊 CONTROL's..."
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
  fi
}

# 🔴 CRITICAL (5)
create_ticket \
  "T-COMPLIANCE-01: Privacy Policy Page" \
  "🔴 CRITICAL | 3 pts\n\nImplement /privacy with GDPR compliance\n\nSDD: openspec/changes/compliance/2026-11-05-t-compliance-01-privacy-policy/\n\n✅ Página /privacy con política completa\n✅ Endpoint /api/user/export-data\n✅ Auditoría de descargas\n✅ Multiidioma (ES+EN)\n✅ Google Play compliance"

create_ticket \
  "T-COMPLIANCE-02: Terms of Use Page" \
  "🔴 CRITICAL | 3 pts\n\nImplement /terms with legal compliance\n\nSDD: openspec/changes/compliance/2026-11-05-t-compliance-02-terms-of-use/\n\n✅ Página /terms con TOS completos\n✅ Modal de aceptación en login\n✅ Versionado de términos\n✅ Notificación de cambios (30 días)\n✅ Google Play compliance"

create_ticket \
  "T-COMPLIANCE-03: Licenses & IP Attribution" \
  "🔴 CRITICAL | 2 pts\n\nImplement /licenses with open source attribution\n\nSDD: openspec/changes/compliance/2026-11-05-t-compliance-03-licenses-ip/\n\n✅ Página /licenses con librerías\n✅ Auto-generated desde package.json\n✅ Assets attribution\n✅ GPL/AGPL compliance"

create_ticket \
  "T-COMPLIANCE-04: Delete Account (GDPR)" \
  "🔴 CRITICAL | 4 pts\n\nImplement account deletion with GDPR compliance\n\nSDD: openspec/changes/compliance/2026-11-05-t-compliance-04-delete-account/\n\n✅ Soft-delete con 30 días grace period\n✅ Email de confirmación\n✅ Hard-delete automático\n✅ Excepciones legales (actas 7 años)\n✅ GDPR artículo 17"

create_ticket \
  "T-COMPLIANCE-05: Monitoring & Crash Reporting (Sentry)" \
  "🔴 CRITICAL | 3 pts\n\nImplement error tracking with Sentry\n\nSDD: openspec/changes/compliance/2026-11-05-t-compliance-05-monitoring-sentry/\n\n✅ Sentry backend + frontend integration\n✅ PII scrubbing\n✅ Error sampling (10% prod, 100% staging)\n✅ Slack alerts\n✅ Google Play compliance"

# 🟠 HIGH (3)
create_ticket \
  "T-COMPLIANCE-06: Rate Limiting Infrastructure" \
  "🟠 HIGH | 3 pts\n\nImplement API rate limiting\n\nSDD: openspec/changes/compliance/2026-11-05-t-compliance-06-rate-limiting/\n\n✅ Global: 100 req/min per IP\n✅ Per-user: 1000 req/min\n✅ Per-endpoint: custom limits\n✅ Redis-backed\n✅ DDoS protection"

create_ticket \
  "T-COMPLIANCE-07: Data Transparency & Consent" \
  "🟠 HIGH | 3 pts\n\nImplement cookie banner + consent management\n\nSDD: openspec/changes/compliance/2026-11-05-t-compliance-07-data-transparency-consent/\n\n✅ Cookie banner (first-visit)\n✅ Granular preferences\n✅ Analytics guard\n✅ GDPR compliance"

create_ticket \
  "T-COMPLIANCE-08: Backup & Disaster Recovery" \
  "🟠 HIGH | 3 pts\n\nImplement backup infrastructure + recovery plan\n\nSDD: openspec/changes/compliance/2026-11-05-t-compliance-08-backup-recovery/\n\n✅ Daily backups (23 UTC)\n✅ Cross-region replication\n✅ 30-day retention\n✅ Recovery playbook (RTO 4h, RPO 24h)\n✅ Quarterly testing"

log ""
ok "$CREATED/8 tickets created in epic 📊 CONTROL's"
log ""
log "📊 View: https://app.shortcut.com/epics/$EPIC_ID"
log ""
