# Tasks — T-INFRASTRUCTURE-01: CORS & Security Headers Implementation

**Epic:** T-INFRASTRUCTURE-01  
**Sprint:** 1 sprint (3-4 points)  
**Owner:** [To be assigned]  

---

## Phase 1: Backend CORS Configuration

### T-INFRA-01.1: Update application.properties with CORS config

**Description:** Add Quarkus CORS properties for prod/dev/staging origins.

**Acceptance Criteria:**
- [ ] `application.properties` (base) has:
  - `quarkus.http.cors.origins=http://localhost:4200` (override per env)
  - `quarkus.http.cors.methods=GET,POST,PUT,DELETE,OPTIONS`
  - `quarkus.http.cors.headers=Content-Type,Authorization,X-Requested-With,Accept`
  - `quarkus.http.cors.access-control-allow-credentials=true`
  - `quarkus.http.cors.access-control-max-age=86400`
- [ ] `application-dev.properties` has: `quarkus.http.cors.origins=http://localhost:4200,http://localhost:4300`
- [ ] `application-prod.properties` has: `quarkus.http.cors.origins=https://control.twintailcs.xyz`
- [ ] `application-staging.properties` has: `quarkus.http.cors.origins=https://staging.twintailcs.xyz`

**Mutation:** Changed CORS from disabled to whitelist-based.

---

### T-INFRA-01.2: Modify JwtAuthFilter to skip JWT validation for OPTIONS

**Description:** Add early return for preflight OPTIONS requests (no JWT needed).

**File:** `backend/electoral-api-quarkus/src/main/java/ec/gob/cne/electoralsystem/api/auth/JwtAuthFilter.java`

**Acceptance Criteria:**
- [ ] Filter checks: `if (request.getMethod().equals("OPTIONS")) { chain.doFilter(request, response); return; }`
- [ ] OPTIONS requests bypass JWT validation
- [ ] Unit test verifies: OPTIONS → no JWT check
- [ ] Unit test verifies: POST/PUT/DELETE → JWT required

**Mutation:** Added OPTIONS exemption to JWT filter.

---

### T-INFRA-01.3: Create SecurityHeadersFilter for CSP, HSTS, X-Frame-Options

**Description:** Implement WebFilter that adds security headers to all responses.

**File:** `backend/electoral-api-quarkus/src/main/java/ec/gob/cne/electoralsystem/api/security/SecurityHeadersFilter.java`

**Code Structure:**
```java
@WebFilter(urlPatterns = "/*")
public class SecurityHeadersFilter implements Filter {
  @Override
  public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) {
    HttpServletResponse response = (HttpServletResponse) res;
    
    // Add security headers
    response.setHeader("Content-Security-Policy", "default-src 'self'");
    response.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
    response.setHeader("X-Frame-Options", "DENY");
    response.setHeader("X-Content-Type-Options", "nosniff");
    response.setHeader("X-XSS-Protection", "1; mode=block");
    
    chain.doFilter(req, res);
  }
}
```

**Acceptance Criteria:**
- [ ] Filter adds 5 security headers to all responses
- [ ] Headers are set before chain.doFilter() (applied to all endpoints)
- [ ] Unit test verifies all headers present
- [ ] Integration test: request /api/auth/me → all headers in response

**Mutation:** Created SecurityHeadersFilter WebFilter.

---

### T-INFRA-01.4: Update RefreshTokenCookie to set HttpOnly, Secure, SameSite

**Description:** Modify cookie settings for refresh_token to include security flags.

**File:** `backend/electoral-api-quarkus/src/main/java/ec/gob/cne/electoralsystem/api/auth/AuthController.java`

**Current Code:** (find refresh token cookie creation)
```java
// OLD:
response.addCookie(new Cookie("refresh_token", token));

// NEW:
Cookie refreshToken = new Cookie("refresh_token", tokenValue);
refreshToken.setHttpOnly(true);
refreshToken.setSecure(true);  // HTTPS only
refreshToken.setPath("/api/auth/refresh");
refreshToken.setAttribute("SameSite", "Strict");
response.addCookie(refreshToken);
```

**Acceptance Criteria:**
- [ ] Cookie includes `HttpOnly=true`
- [ ] Cookie includes `Secure=true` (HTTPS only)
- [ ] Cookie includes `SameSite=Strict`
- [ ] Cookie `Path=/api/auth/refresh`
- [ ] Integration test: login response includes Set-Cookie with all flags
- [ ] Dev env (non-HTTPS): Secure flag skipped gracefully

**Mutation:** Added HttpOnly, Secure, SameSite flags to refresh token cookie.

---

## Phase 2: Testing & Documentation

### T-INFRA-01.5: Unit tests for CORS and security filters

**Description:** Test filter logic in isolation.

**File:** `backend/electoral-api-quarkus/src/test/java/ec/gob/cne/electoralsystem/api/security/CorsSecurityTest.java`

**Test Cases:**
```
1. testOptionsRequestBypassesJwt() 
   - OPTIONS /api/auth/login → no JWT check
   
2. testPostRequestRequiresJwt()
   - POST /api/auth/login (no JWT) → 401
   
3. testSecurityHeadersAdded()
   - Any request → response has CSP, HSTS, etc
   
4. testRefreshTokenCookieFlags()
   - Login response → Set-Cookie header has HttpOnly, Secure, SameSite
   
5. testCorsOriginWhitelisting()
   - Origin: https://control.twintailcs.xyz → Allow
   - Origin: https://evil.com → Deny (no header)
```

**Acceptance Criteria:**
- [ ] All 5 tests pass
- [ ] Code coverage >90% for filters
- [ ] Tests run in `mvn test`

**Mutation:** Added unit test suite for CORS and security.

---

### T-INFRA-01.6: Integration test: end-to-end preflight + login flow

**Description:** Test CORS and security in an integrated environment.

**File:** `backend/electoral-api-quarkus/src/test/java/ec/gob/cne/electoralsystem/api/integration/CorsIntegrationTest.java`

**Test Flow:**
```
1. Start backend on :8081
2. Simulate browser OPTIONS request from http://localhost:4200
   - Verify: 200 OK + CORS headers + Cache-Control
3. Send OPTIONS preflight for POST /api/auth/login
   - Verify: Access-Control-Allow-Methods includes POST
4. Send actual POST /api/auth/login with credentials
   - Verify: response has Set-Cookie (refresh_token)
   - Verify: Cookie has HttpOnly, Secure, SameSite flags
5. Attempt preflight from wrong origin (https://evil.com)
   - Verify: no Access-Control-Allow-Origin header
```

**Acceptance Criteria:**
- [ ] Integration test passes with testcontainers or embedded server
- [ ] Test verifies preflight → actual request flow
- [ ] Test verifies security headers present
- [ ] Test verifies cookie flags

**Mutation:** Added integration test for CORS flow.

---

### T-INFRA-01.7: Documentation: CORS & Security Headers README

**Description:** Document CORS configuration, security headers, environment-specific settings.

**File:** `backend/electoral-api-quarkus/docs/CORS_SECURITY.md`

**Content:**
```markdown
# CORS & Security Headers Configuration

## CORS Setup

### Allowed Origins per Environment
- **Development:** http://localhost:4200, http://localhost:4300
- **Staging:** https://staging.twintailcs.xyz
- **Production:** https://control.twintailcs.xyz

### How Preflight Works
1. Browser sends OPTIONS request
2. Server responds with 200 + CORS headers
3. Browser caches response (86400 seconds)
4. Browser sends actual request (POST, etc)

### Troubleshooting
- "Access-Control-Allow-Origin missing": Origin not in whitelist
- "Cookies not sent": credentials: 'include' not used in fetch()

## Security Headers

### CSP (Content-Security-Policy): default-src 'self'
- Prevents inline scripts, external script injection
- Reduces XSS attack surface

### HSTS (Strict-Transport-Security): max-age=31536000
- Forces HTTPS for all future requests
- Prevents MITM attacks

### X-Frame-Options: DENY
- Prevents clickjacking attacks
- Blocks <iframe> embedding

### X-Content-Type-Options: nosniff
- Prevents MIME sniffing
- Browser respects Content-Type header

## Refresh Token Security

- **HttpOnly:** JS cannot access (prevents XSS token theft)
- **Secure:** HTTPS only (prevents network sniffing)
- **SameSite=Strict:** Not sent in cross-site requests (CSRF prevention)
```

**Acceptance Criteria:**
- [ ] README explains each security header
- [ ] Origin whitelist documented per env
- [ ] Troubleshooting section covers common issues
- [ ] Examples show how to test (curl, Postman)

**Mutation:** Created CORS_SECURITY.md documentation.

---

## Phase 3: Frontend Verification (Optional)

### T-INFRA-01.8: Frontend test: verify credentials include

**Description:** Verify frontend sends `credentials: 'include'` for login requests.

**File:** `frontend/app/src/app/auth/auth.service.ts`

**Check:**
```typescript
// Ensure fetch includes credentials
fetch('/api/auth/login', {
  method: 'POST',
  credentials: 'include',  // <-- CRITICAL
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify(...)
})
```

**Acceptance Criteria:**
- [ ] AuthService.login() includes `credentials: 'include'`
- [ ] All authenticated requests include credentials flag
- [ ] Browser DevTools → Network tab shows:
  - OPTIONS request before POST
  - Set-Cookie in response
  - Cookie sent in next requests

**Mutation:** Verified frontend credentials handling.

---

## Summary

| Task | Effort | Risk | Owner |
|------|--------|------|-------|
| T-INFRA-01.1 | 1pt | Low | TBD |
| T-INFRA-01.2 | 1pt | Low | TBD |
| T-INFRA-01.3 | 2pt | Low | TBD |
| T-INFRA-01.4 | 1pt | Medium | TBD |
| T-INFRA-01.5 | 1pt | Low | TBD |
| T-INFRA-01.6 | 2pt | Medium | TBD |
| T-INFRA-01.7 | 1pt | Low | TBD |
| T-INFRA-01.8 | 1pt | Low | TBD |
| **Total** | **10pt** | | |

---

**Status:** Tasks ready for assignment  
**Last Updated:** 2026-11-06
