# Design — T-INFRASTRUCTURE-01: CORS Configuration & Security Headers

**Date:** 2026-11-06  
**Status:** Design Phase  

---

## Decisiones Arquitectónicas

### D1: CORS habilitado vía `application.properties` (NO code-based config)

**Decision:** Usar Quarkus built-in CORS configuration via `application.properties` en lugar de `SecurityConfig`.

**Why:** 
- `.cors(AbstractHttpConfigurer::disable)` en SecurityConfig bloquea CORS completamente
- Properties-based es más mantenible (dev/prod differ en values, not code)
- Quarkus maneja preflight OPTIONS automáticamente si está habilitado

**Alternatives:**
- ❌ Spring Security CorsConfigurationSource: reinventar en Quarkus, más verbose
- ✅ **Selected:** `quarkus.http.cors.*` properties

---

### D2: Allowed origins: hardcoded list (NO wildcard `*`)

**Decision:** Whitelist explícita per environment.

```properties
# prod
quarkus.http.cors.origins=https://control.twintailcs.xyz

# dev
quarkus.http.cors.origins=http://localhost:4200,http://localhost:4300

# staging
quarkus.http.cors.origins=https://staging.twintailcs.xyz
```

**Why:**
- `*` permite CSRF, cualquier origin puede robar tokens
- Electoral system = high value target → whitelist mandatory
- Cada entorno tiene origin específico

**Alternatives:**
- ❌ Dynamic origin list (read from DB): adds latency, complexity
- ✅ **Selected:** Static per environment

---

### D3: Refresh token: HttpOnly + Secure + SameSite=Strict

**Decision:** Cookie flags protegen contra XSS + CSRF.

**Flags:**
- `HttpOnly` → JS no puede acceder (document.cookie) → XSS no roba
- `Secure` → HTTPS only (man-in-the-middle protection)
- `SameSite=Strict` → no enviada en cross-site requests (CSRF prevention)
- `Path=/api/auth/refresh` → solo para refresh endpoint

**Why:**
- Access token: Bearer en Authorization header (stateless, no cookie)
- Refresh token: cookie (sensitive, needs extra protection)
- `SameSite=Strict` + `Secure` = OWASP A01 (Broken Access Control) satisfied

**Alternatives:**
- ❌ SameSite=Lax: permite top-level navigations, weaker
- ❌ No HttpOnly: JS可以盗取
- ✅ **Selected:** Strict + HttpOnly + Secure

---

### D4: Security headers via WebFilter (NOT global properties)

**Decision:** Implementar `SecurityHeadersFilter` que agrega headers en todas las responses.

**Headers:**
```
Content-Security-Policy: default-src 'self'
Strict-Transport-Security: max-age=31536000; includeSubDomains
X-Frame-Options: DENY
X-Content-Type-Options: nosniff
X-XSS-Protection: 1; mode=block
```

**Why:**
- CSP: previene inline scripts, external script injection (XSS)
- HSTS: force HTTPS para future requests (MITM prevention)
- X-Frame-Options: DENY previene clickjacking
- X-Content-Type-Options: previene MIME sniffing attacks
- X-XSS-Protection: legacy support (modern browsers ignore, pero Chrome necesita)

**Alternatives:**
- ❌ Properties-based (Quarkus no soporta todos estos headers)
- ✅ **Selected:** WebFilter + hardcoded values (security != config)

---

### D5: Preflight caching: 86400 segundos (1 día)

**Decision:** `Access-Control-Max-Age: 86400`

**Why:**
- Reduce round trips (OPTIONS requests costosos)
- 1 day = standard industry practice
- Dev: menor (3600) para cambios rápidos

**Alternatives:**
- ❌ 3600 (1h): excess preflight requests
- ❌ Infinite: si se agrega origin, no se ve hasta server restart
- ✅ **Selected:** 86400 prod, 3600 dev

---

### D6: JwtAuthFilter: skip CORS para OPTIONS requests

**Decision:** No validar JWT en preflight OPTIONS.

```java
if (request.getMethod().equals("OPTIONS")) {
  chain.doFilter(request, response);
  return;
}
// else: validate JWT
```

**Why:**
- Preflight OPTIONS is browser automatic, sin JWT
- Si validamos JWT en OPTIONS → 401 → preflight falla → fetch bloqueado
- OPTIONS es info-only, no accede recursos

**Alternatives:**
- ❌ Validar JWT en OPTIONS: breaks preflight
- ✅ **Selected:** Skip JWT for OPTIONS

---

### D7: Refresh token endpoint: `/api/auth/refresh` (NOT `/api/v1/...`)

**Decision:** Keep versioning out of auth paths.

**Why:**
- Auth paths no cambian versión de API (cookies bind a path, no a version)
- Si cambias a `/api/v2/auth/refresh`, cookies viejas no funcionan
- `/api/auth/refresh` es semántico, no versionado

**Alternatives:**
- ❌ `/api/v1/auth/refresh`: couples auth to API versioning
- ✅ **Selected:** `/api/auth/refresh` (stable path)

---

## Arquitectura de Componentes

```
┌─────────────────────────────────────────────┐
│ Frontend (control.twintailcs.xyz)           │
│  fetch(url, { credentials: 'include' })    │
└────────────────────┬────────────────────────┘
                     │
         (1) OPTIONS Preflight
                     ↓
┌─────────────────────────────────────────────┐
│ Quarkus Backend (api.twintailcs.xyz)        │
│                                              │
│  ┌──────────────────────────────────────┐  │
│  │ CorsFilter                            │  │
│  │  - Check origin in whitelist         │  │
│  │  - Add CORS headers (Allow-Origin)   │  │
│  └──────────────────────────────────────┘  │
│                 ↓                           │
│  ┌──────────────────────────────────────┐  │
│  │ JwtAuthFilter                         │  │
│  │  - Skip if OPTIONS                    │  │
│  │  - Validate JWT if POST/PUT/DELETE   │  │
│  └──────────────────────────────────────┘  │
│                 ↓                           │
│  ┌──────────────────────────────────────┐  │
│  │ SecurityHeadersFilter                 │  │
│  │  - Add CSP, HSTS, X-Frame-Options    │  │
│  └──────────────────────────────────────┘  │
│                 ↓                           │
│  ┌──────────────────────────────────────┐  │
│  │ Controller (POST /api/auth/login)    │  │
│  │  - Set Refresh-Token cookie          │  │
│  │  - Return Access-Token (Bearer)      │  │
│  └──────────────────────────────────────┘  │
└─────────────────────────────────────────────┘
                     ↑
         (2) Actual Request + Response
                     │
         Headers include:
         - Access-Control-Allow-Origin: https://control...
         - Set-Cookie: refresh_token=...; HttpOnly; Secure; SameSite=Strict
         - Content-Security-Policy: default-src 'self'
         - Strict-Transport-Security: max-age=31536000
```

---

## Configuración por Entorno

### Development (localhost)
```properties
quarkus.http.cors.origins=http://localhost:4200,http://localhost:4300
quarkus.http.cors.access-control-max-age=3600
quarkus.http.ssl.certificate.key-store-file=target/quarkus/dev-keystore.jks
```

### Staging
```properties
quarkus.http.cors.origins=https://staging.twintailcs.xyz
quarkus.http.cors.access-control-max-age=86400
quarkus.http.ssl.certificate.key-store-file=/etc/certs/staging.jks
```

### Production
```properties
quarkus.http.cors.origins=https://control.twintailcs.xyz
quarkus.http.cors.access-control-max-age=86400
quarkus.http.ssl.certificate.key-store-file=/etc/certs/prod.jks
```

---

## Descartadas (Why NOT)

### ❌ Nginx reverse proxy CORS
- Moves CORS responsibility to infrastructure (ops, not dev)
- CORS logic should live near app, not in proxy
- Harder to debug

### ❌ OAuth2 / Keycloak integration
- Out of scope (future)
- Current auth (JWT) sufficient for compliance phase

### ❌ CSP report-uri endpoint
- Adds complexity (need separate reporting service)
- CSP blocking behavior > reporting for MVP

### ❌ Subresource Integrity (SRI)
- For HTML scripts only, not JSON APIs
- Not applicable to REST backend

---

**Architecture Status:** Approved  
**Next:** Implement specs & test plan
