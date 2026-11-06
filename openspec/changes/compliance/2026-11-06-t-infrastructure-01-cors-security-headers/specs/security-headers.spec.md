# Spec: Security Headers (CSP, HSTS, X-Frame-Options, etc)

## Feature: HTTP Security Headers Protection

### Scenario 1: Content-Security-Policy prevents inline scripts

```gherkin
Given a response from https://api.twintailcs.xyz/api/data
When the response includes header:
  - Content-Security-Policy: default-src 'self'

Then:
  - Any JavaScript injected via XSS must come from same origin
  - Inline <script> tags are blocked
  - External scripts from evil.com are blocked
```

### Scenario 2: HSTS enforces HTTPS for all future requests

```gherkin
Given a browser receives response from https://api.twintailcs.xyz
And the response includes header:
  - Strict-Transport-Security: max-age=31536000; includeSubDomains

When the browser later navigates to http://api.twintailcs.xyz (non-HTTPS)
Then the browser upgrades the request to https:// automatically
And man-in-the-middle cannot intercept with HTTP
```

### Scenario 3: X-Frame-Options DENY prevents clickjacking

```gherkin
Given a response from https://api.twintailcs.xyz
And the response includes header:
  - X-Frame-Options: DENY

When an attacker embeds the API response in an <iframe>
  <iframe src="https://api.twintailcs.xyz/api/sensitive"></iframe>

Then the browser blocks the frame from loading
And the page shows blank instead of sensitive data
```

### Scenario 4: X-Content-Type-Options nosniff prevents MIME sniffing

```gherkin
Given a response with Content-Type: application/json
And the response includes header:
  - X-Content-Type-Options: nosniff

When the response body contains:
  {"data": "<script>alert('xss')</script>"}

Then the browser interprets it as JSON (not HTML/script)
And the script does not execute even if misnamed .html
```

### Scenario 5: X-XSS-Protection (legacy protection)

```gherkin
Given a response from https://api.twintailcs.xyz
And the response includes header:
  - X-XSS-Protection: 1; mode=block

When the browser detects XSS patterns in the response
Then the browser blocks rendering and displays blank page
(This is legacy; modern CSP is primary defense)
```

### Scenario 6: All responses include security headers

```gherkin
Given any endpoint: GET /api/auth/me, POST /api/data, DELETE /api/resource
When the response is sent

Then ALL responses include:
  - Content-Security-Policy: default-src 'self'
  - Strict-Transport-Security: max-age=31536000; includeSubDomains
  - X-Frame-Options: DENY
  - X-Content-Type-Options: nosniff
  - X-XSS-Protection: 1; mode=block
```

---

## Acceptance Criteria

- [x] All responses include CSP header with `default-src 'self'`
- [x] HSTS header present with max-age=31536000 (1 year)
- [x] X-Frame-Options set to DENY
- [x] X-Content-Type-Options set to nosniff
- [x] X-XSS-Protection set to `1; mode=block`
- [x] Headers are consistent across all endpoints
- [x] No exceptions or "unsafe" directives in CSP (for MVP)
