# SDD Roadmap — Compliance & Security (PWA Context)

**Date:** 2026-11-07  
**Audit Source:** Compliance audit findings + PWA adaptation  
**Method:** SDD (Software Design Document) cycle for formal features

---

## Priority Queue

### 🔴 CRITICAL - SDD Ready Now

#### **T-LEGAL-01: Privacy Policy + Terms of Use (PWA Pages)**

**Status:** ✅ **READY FOR BUILDER**

**Location:** `openspec/changes/compliance/2026-11-06-t-legal-01-privacy-terms/`

**Contents:**
- ✅ `proposal.md` — Complete (scope, why, risk, benefits)
- ✅ `design.md` — Decisions on versioning, auditing, export format
- ✅ `specs/privacy-terms.spec.md` — Gherkin scenarios
- ✅ `tasks.md` — 10 tasks, 13 story points, with mutations & traps

**Key Deliverables:**
- Pages: `GET /privacy?lang=es|en`, `GET /terms?lang=es|en`
- API: `POST /api/v1/user/accept-terms` (audited)
- API: `GET /api/v1/user/export-data` (GDPR Art. 15, rate-limited 1/24h)
- Frontend Modal: Terms acceptance (mandatory on version mismatch)
- Audit Trail: terms_acceptance_audit table + dpo_contact_requests

**PWA Considerations:**
- No native Crashlytics needed (web-only)
- Sentry for JS errors (separate SDD)
- Service Worker doesn't affect legal pages
- Offline: Static HTML cached, API calls sync on reconnect

**Effort:** 13 story points (~1 sprint)

**Decision:** Launch **immediately after ODD fixes**. No dependencies.

---

#### **T-COMPLIANCE-07: Web Consent Banner + Permission Flows (PWA-Specific)**

**Status:** 🔄 **NEEDS COMPLETION** (spec exists, tasks missing)

**Location:** `openspec/changes/compliance/2026-11-05-t-compliance-07-data-transparency-consent/`

**Contents:**
- ✅ `proposal.md` — Scope defined (cookies, geolocation, notifications)
- ⚠️ `design.md` — Partial (needs PWA-specific decisions)
- ✅ `specs/` — Gherkin scenarios
- ❌ `tasks.md` — MISSING, must create

**Key Deliverables (PWA-adapted):**
- Cookie consent banner (iubenda or custom with consent.tech)
- Permission request flows for:
  - **Geolocation** (if Electoral Districts require map)
  - **Notifications** (Web Push API for PWA)
  - **Camera** (if JRV photo capture needed)
- Consent record in DB: `usuario_consent` table
- GDPR/CCPA compliance

**PWA Considerations:**
- Web Push API (not mobile push — no FCM required)
- Service Worker integration: cache only after consent
- Geolocation: request at first use, store in localStorage
- Camera: if needed, use MediaDevices API (web standard)

**Next Step:** Create `tasks.md` (estimate 2-3 days)

**Conditional:** Only if app needs photo capture or notifications.

---

#### **T-COMPLIANCE-05: Sentry Error Tracking Integration (PWA + PWA Offline)**

**Status:** 🔄 **NEEDS COMPLETION** (proposal exists, tasks missing)

**Location:** `openspec/changes/compliance/2026-11-05-t-compliance-05-monitoring-sentry/`

**Contents:**
- ✅ `proposal.md` — Scope (error tracking, session replay optional)
- ⚠️ `design.md` — Partial (needs PWA-specific error scenarios)
- ❌ `tasks.md` — MISSING, must create

**Key Deliverables (PWA-adapted):**
- Sentry backend (Java SDK): `quarkus-sentry` dependency
- Sentry frontend (JavaScript): `@sentry/angular`
- Error capturing: uncaught exceptions, API failures, validation errors
- Offline handling: queue errors, flush on sync
- **NO Crashlytics** (mobile-only, PWA doesn't use it)
- Session context: user ID, environment, build version

**PWA Considerations:**
- Service Worker errors captured separately
- IndexedDB failures logged
- Network errors in offline mode queued
- Source maps uploaded for minified code

**Next Step:** Create `tasks.md` (estimate 2-3 days)

**Launch After:** T-LEGAL-01 (no dependency, but secondary priority)

---

### 🟡 HIGH - SDD In-Progress

#### **T-INFRASTRUCTURE-01: CORS + Security Headers (PWA Browser Security)**

**Status:** 🔄 **NEEDS COMPLETION** (spec only, no proposal/design/tasks)

**Location:** `openspec/changes/compliance/2026-11-06-t-infrastructure-01-cors-security-headers/`

**Recommendation:** ⚠️ **Convert to ODD (quick fix)** instead of formal SDD

**Why:** CORS is 1 line of config (`quarkus.http.cors=true` + origins). Formal SDD overhead not justified.

**Action:** Already documented in `openspec/odd-priority-fixes.md` (Phase 1).

**If insisting on SDD:** See `openspec/odd-priority-fixes.md` for spec details to embed in formal doc.

---

## Execution Timeline

### **Now (Today)**

```
┌─ ODD Fixes (Phase 1: CORS, LICENSE, THIRDPARTY, Jacoco)
│  └─ Task: 5 builders running fixes in parallel
│     Effort: 55 min total
│     Blocker: None
│
└─ (Once ODD closes) → Assign T-LEGAL-01 to Builder
```

### **Sprint 1 (Week 1-2)**

```
┌─ T-LEGAL-01 Builder Phase
│  └─ Task: Execute tasks.md (13 points, 1 sprint)
│     DB migration → Backend endpoints → Frontend pages/modals
│     Tests: E2E (Playwright) + unit (backend + frontend)
│
└─ (Parallel) Arquitecto reviews T-COMPLIANCE-07 (complete tasks.md)
```

### **Sprint 2 (Week 3-4)**

```
┌─ T-COMPLIANCE-07 Builder Phase (if approved)
│  └─ Consent banner + permission flows (PWA-specific)
│
├─ (Parallel) Arquitecto reviews T-COMPLIANCE-05 (complete tasks.md)
│
└─ (Parallel) Verify phases of T-LEGAL-01 close
```

### **Sprint 3 (Week 5-6)**

```
└─ T-COMPLIANCE-05 Builder Phase (if approved)
   └─ Sentry integration (backend + frontend)
   └─ Error tracking, session replay, offline queue
```

---

## Dependencies & Risks

| SDD | Blocker | Risk | Mitigation |
|-----|---------|------|-----------|
| T-LEGAL-01 | ODD CORS | Low | CORS fixed before launch |
| T-COMPLIANCE-07 | None (conditional) | Med | Requires product decision: do we need geolocation/camera? |
| T-COMPLIANCE-05 | None | Low | Sentry SaaS account required (create before Builder) |

---

## PWA-Specific Notes

### Service Worker Impact
- Privacy/Terms pages: static, no SW impact
- Consent banner: store in localStorage (cached)
- Sentry errors: queue in IndexedDB if offline

### Offline Considerations
- Legal pages: pre-cache with Service Worker
- Terms acceptance: queue until online
- Exports: must be online (sensitive data)

### Build & Deploy
- No native-specific code (no Gradle, no Xcode)
- Web manifest already exists
- Angular build: same as today

---

## Success Criteria (All SDDs)

- [ ] All SDDs have proposal.md + design.md + specs + tasks.md
- [ ] All tasks have mutations + traps documented
- [ ] Builder phase executes without blocking issues
- [ ] Verify phase audits with 100% mutation pass rate
- [ ] Archive-report.md summary written post-closure
- [ ] Roadmap updated with new traps (if found)

---

## Next Steps (for Arquitecto)

1. **Complete T-COMPLIANCE-07 tasks.md**
   - Add PWA-specific permission flows
   - Include offline consent queueing
   - Estimate effort (likely 2-3 days)

2. **Complete T-COMPLIANCE-05 tasks.md**
   - Add Sentry backend + frontend integration
   - Include offline error queuing
   - Estimate effort (likely 2-3 days)

3. **Create Sentry SaaS account**
   - Project key for backend (Java)
   - Project key for frontend (Angular)
   - Provide API credentials before Builder phase

4. **Confirm product decisions**
   - Does app need geolocation? (affects T-COMPLIANCE-07 scope)
   - Does app need camera? (affects T-COMPLIANCE-07 scope)
   - Does app need Web Push notifications? (affects T-COMPLIANCE-07 scope)

---

**Status:** T-LEGAL-01 ready. T-COMPLIANCE-07/05 need task details.  
**Updated:** 2026-11-07  
**Owner:** Arquitecto (pending task completion)
