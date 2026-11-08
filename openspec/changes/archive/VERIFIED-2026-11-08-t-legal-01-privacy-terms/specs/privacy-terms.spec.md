# Spec: Privacy Policy & Terms of Use Pages

## Feature: Legal Documents Access

### Scenario 1: User accesses /privacy page in Spanish

```gherkin
Given a user navigates to GET /privacy
When the browser detects Spanish locale (or URL param ?lang=es)

Then the server responds with:
  - HTTP 200 OK
  - Content-Type: text/html; charset=utf-8
  - Body: privacy-es.html static file
  - Readable, responsive design
  - Links to Terms page (GET /terms)
```

### Scenario 2: User accesses /terms page in English

```gherkin
Given a user navigates to GET /terms?lang=en
When the backend serves static terms content

Then the response includes:
  - HTML with complete Terms of Use
  - Sections: data usage, liability, cancellation (Art. 17)
  - Link to Privacy page (GET /privacy)
  - Version number (v1.0) visible in page footer
```

### Scenario 3: Language parameter overrides locale

```gherkin
Given a browser with Spanish locale
When the user visits /privacy?lang=en

Then the page is served in English (URL param > browser locale)
```

### Scenario 4: Page is public (no authentication required)

```gherkin
Given an unauthenticated user
When GET /privacy or GET /terms

Then:
  - No 401 response
  - Page loads without login
  - GDPR Art. 13/14 info accessible to everyone (not just users)
```

### Scenario 5: Pages are mobile-responsive

```gherkin
Given privacy page loaded on Mobile Pixel 7 (375px width)
When the user views the page

Then:
  - Text is readable without horizontal scroll
  - Links are tap-able (>44px touch targets)
  - Responsive images scale
```

---

## Feature: Terms Acceptance in Login Flow

### Scenario 1: User accepts new terms version on login

```gherkin
Given a logged-in user with accepted_terms_version = null
When the user logs in

Then:
  - Terms Acceptance Modal appears
  - Modal shows Privacy + Terms summaries (or links)
  - User clicks "Acepto" button
  
And the frontend:
  - POSTs to /api/v1/user/accept-terms
  - Backend records acceptance in terms_acceptance_audit
  - Backend updates usuario.accepted_terms_version = "1.0"
  - Modal closes
  - User redirected to /dashboard
```

### Scenario 2: User has already accepted current version

```gherkin
Given a user with accepted_terms_version = "1.0"
And current legal.terms-version = "1.0"
When the user logs in

Then:
  - No modal appears
  - User goes directly to /dashboard
```

### Scenario 3: New terms version forces re-acceptance

```gherkin
Given a user with accepted_terms_version = "1.0"
And the admin updates terms to v1.1 (changed: data retention policy)
And legal.terms-version = "1.1" deployed

When the user logs in

Then:
  - Modal appears (version mismatch detected)
  - User must accept v1.1 to proceed
  - Audit table records: version="1.1", accepted_at=now
```

### Scenario 4: User closes modal without accepting

```gherkin
Given Terms Acceptance Modal open
When the user closes the modal (X button, no Accept click)

Then:
  - Modal closes
  - User is redirected to /login page
  - Session cleared (logged out)
  - User must log in again
```

### Scenario 5: Modal appears only once per browser session

```gherkin
Given user accepted terms in this session
When the user navigates to /dashboard → /observer/jrv/:id → back to /dashboard

Then:
  - Modal does NOT appear again
  - Frontend caches acceptance in session storage
```

---

## Acceptance Criteria

- [x] Pages `/privacy` and `/terms` accessible without authentication
- [x] Pages support ES and EN languages
- [x] Language selection via URL param `?lang=es|en` or browser locale
- [x] Terms Acceptance Modal blocks login until accepted
- [x] Modal shows when terms version changed
- [x] Acceptance recorded in audit table with user_id, version, timestamp, IP, user-agent
- [x] User cannot bypass modal (no "Decline" option, only "Acepto")
- [x] Pages are mobile-responsive
- [x] Version number visible in page footer
