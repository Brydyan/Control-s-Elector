# Migration Example: convert deprecated scripts to JSON

This shows how to extract tickets from the old scripts and convert to the new JSON format.

## Example 1: Compliance Tickets (from create-shortcut-tickets.sh)

### Original Script (create-shortcut-tickets.sh, lines 57-89)

```bash
create_ticket \
  "T-COMPLIANCE-01: Privacy Policy Page" \
  "🔴 CRITICAL | 3 pts\n\nImplement /privacy with GDPR compliance\n\n..."

create_ticket \
  "T-COMPLIANCE-02: Terms of Use Page" \
  "🔴 CRITICAL | 3 pts\n\nImplement /terms with legal compliance\n\n..."
```

### Converted to JSON (data/tickets/sdds-compliance.json)

```json
{
  "meta": {
    "date": "2026-11-05",
    "batch": "8 Compliance SDDs",
    "epic": "CONTROL's Compliance"
  },
  "tickets": [
    {
      "name": "T-COMPLIANCE-01: Privacy Policy Page",
      "description": "🔴 CRITICAL | 3 pts\n\nImplement /privacy with GDPR compliance\n\nSDD: openspec/changes/compliance/2026-11-05-t-compliance-01-privacy-policy/\n\n✅ Página /privacy con política completa\n✅ Endpoint /api/user/export-data\n✅ Auditoría de descargas\n✅ Multiidioma (ES+EN)\n✅ Google Play compliance",
      "estimate": "3"
    },
    {
      "name": "T-COMPLIANCE-02: Terms of Use Page",
      "description": "🔴 CRITICAL | 3 pts\n\nImplement /terms with legal compliance\n\nSDD: openspec/changes/compliance/2026-11-05-t-compliance-02-terms-of-use/\n\n✅ Página /terms con TOS completos\n✅ Modal de aceptación en login\n✅ Versionado de términos\n✅ Notificación de cambios (30 días)\n✅ Google Play compliance",
      "estimate": "3"
    }
  ]
}
```

**Key mapping:**
```
Bash argument 1 → JSON "name"
Bash argument 2 → JSON "description"
(no estimate in old script) → JSON "estimate" (infer from description)
```

---

## Example 2: Infrastructure Tickets (from create-new-sdd-tickets.sh)

### Original Script (create-new-sdd-tickets.sh, lines 64-70)

```bash
create_ticket \
  "T-INFRASTRUCTURE-01: CORS & Security Headers" \
  "🔴 CRITICAL | 10 pts\n\nImplement CORS configuration and security headers\n\n..."

create_ticket \
  "T-LEGAL-01: Privacy Policy + Terms of Use" \
  "🔴 CRITICAL | 13 pts\n\nImplement legal compliance...\n\n..."
```

### Converted to JSON (data/tickets/sdds-infrastructure-legal.json)

```json
{
  "meta": {
    "date": "2026-11-06",
    "batch": "Infrastructure + Legal SDDs",
    "epic": "CONTROL's Foundation"
  },
  "tickets": [
    {
      "name": "T-INFRASTRUCTURE-01: CORS & Security Headers",
      "description": "🔴 CRITICAL | 10 pts\n\nImplement CORS configuration and security headers\n\nSDD: openspec/changes/compliance/2026-11-06-t-infrastructure-01-cors-security-headers/\n\n✅ CORS whitelist per environment (prod/dev/staging)\n✅ Preflight OPTIONS handling (no JWT validation)\n✅ Security headers: CSP, HSTS, X-Frame-Options, X-Content-Type-Options, X-XSS-Protection\n✅ Refresh token: HttpOnly + Secure + SameSite=Strict\n✅ Tests: unit + integration\n✅ Documentation",
      "estimate": "10"
    },
    {
      "name": "T-LEGAL-01: Privacy Policy + Terms of Use",
      "description": "🔴 CRITICAL | 13 pts\n\nImplement legal compliance: privacy policy, terms, GDPR Art. 15 export\n\nSDD: openspec/changes/compliance/2026-11-06-t-legal-01-privacy-terms/\n\n✅ Static /privacy + /terms pages (multi-language ES+EN)\n✅ Terms acceptance modal on login (mandatory)\n✅ GET /api/v1/user/export-data (GDPR Art. 15)\n✅ POST /api/v1/user/accept-terms + audit table\n✅ POST /api/v1/legal/contact-dpo (DPO form)\n✅ Rate limiting: export 1/24h, DPO 1/30s\n✅ Versionado de términos (v1.0, v1.1, etc)\n✅ Database migrations + tests",
      "estimate": "13"
    }
  ]
}
```

---

## How to Migrate

### Step 1: Extract tickets

Open deprecated script, copy all `create_ticket` calls.

### Step 2: Create JSON file

```bash
cat > data/tickets/sdds-compliance.json << 'EOF'
{
  "meta": {
    "date": "2026-11-05",
    "batch": "...",
    "epic": "..."
  },
  "tickets": [
    { "name": "...", "description": "...", "estimate": "..." }
  ]
}
EOF
```

### Step 3: Validate

```bash
./scripts/validate-shortcut-json.sh data/tickets/sdds-compliance.json
```

Expected output:
```
ℹ️  Validating: data/tickets/sdds-compliance.json
    ✓ Name: T-COMPLIANCE-01: Privacy Policy Page...
    ✓ Description: 350 chars
    ✓ Name: T-COMPLIANCE-02: Terms of Use Page...
    ✓ Description: 320 chars
✅ data/tickets/sdds-compliance.json — 2 ticket(s) valid
```

### Step 4: Create tickets

```bash
./scripts/create-shortcut-tickets-generic.sh data/tickets/sdds-compliance.json
```

Expected output:
```
ℹ️  Validating API key...
✅ API key valid (user: Andy Alejandro)

ℹ️  Creating tickets from data/tickets/sdds-compliance.json...
ℹ️  Epic: CONTROL's Compliance (ID: 108)

  [1] T-COMPLIANCE-01: Privacy Policy Page ... ✅ ID: 12345
  [2] T-COMPLIANCE-02: Terms of Use Page ... ✅ ID: 12346

✅ 2/2 tickets created successfully

📊 View in Shortcut: https://app.shortcut.com/epics/108
```

### Step 5: Clean up (optional)

Once migrated and tested, you can:
- Keep deprecated script as reference
- Or archive: `git mv scripts/create-shortcut-tickets.sh scripts/ARCHIVE/`
- Or delete: `git rm scripts/create-shortcut-tickets.sh`

---

## Tips

1. **Extract estimates from description** — Look for "X pts" pattern
2. **Preserve formatting** — Use `\n` for newlines in descriptions
3. **Keep meta section** — Helps document which SDD/batch this is for
4. **Test with validate-shortcut-json.sh first** — Catch errors before creating
5. **Use --epic=ID flag** if epic is different — `./create-shortcut-tickets-generic.sh file.json --epic=109`

---

## Templates

### Minimal JSON

```json
{
  "meta": {"date": "2026-11-19"},
  "tickets": [
    {"name": "T-NAME: Description", "description": "Details here"}
  ]
}
```

### Full JSON

```json
{
  "meta": {
    "date": "2026-11-19",
    "epic": "Name from Shortcut",
    "batch": "Description of batch",
    "feature": "Related feature/SDD"
  },
  "tickets": [
    {
      "name": "ODD/SDD — Title",
      "description": "Long description\n\nWith multiple lines",
      "estimate": "5"
    }
  ]
}
```
