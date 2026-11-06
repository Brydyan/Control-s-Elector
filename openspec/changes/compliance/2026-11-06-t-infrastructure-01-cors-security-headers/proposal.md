# Proposal — T-INFRASTRUCTURE-01: CORS Configuration & Security Headers

**Date:** 2026-11-06  
**Priority:** CRITICAL  
**Estimación:** 3-4 puntos → 1 sprint

---

## Qué se construye

Configuración completa de CORS (Cross-Origin Resource Sharing) y security headers en la API Quarkus para soportar el frontend cross-origin y proteger contra ataques comunes:
- **CORS Configuration:** allowed origins explícitas (`control.twintailcs.xyz` en prod, `localhost:4200` en dev), métodos (GET, POST, PUT, DELETE, OPTIONS), headers autorizados y credenciales (cookies httpOnly)
- **Security Headers:** CSP (Content-Security-Policy), HSTS (Strict-Transport-Security), X-Frame-Options (clickjacking), X-Content-Type-Options (MIME sniffing), X-XSS-Protection (legacy XSS)
- **OPTIONS Handler:** Quarkus devuelve preflight responses con correctos headers CORS (sin redirecciones 302)
- **Cookie Security:** httpOnly, Secure, SameSite=Strict en refresh tokens
- **Backend:** implementado en `SecurityConfig`, `CorsFilter`, y `SecurityHeadersFilter` (Quarkus HttpSecurityPolicy)

## Por qué es necesario

**Hoy vacío y vulnerable:**
- ❌ `application.properties` NO tiene CORS configurado → frontend falla con `Access-Control-Allow-Origin` missing
- ❌ `SecurityConfig:43` hace `.cors(AbstractHttpConfigurer::disable)` → CORS deshabilitado, pero frontend es cross-origin en producción
- ❌ `JwtAuthFilter` no maneja `OPTIONS` preflight → 401 Unauthorized en preflight, breaks fetch
- ❌ Sin security headers → vulnerable a clickjacking (X-Frame-Options), MIME sniffing (X-Content-Type-Options), XSS (CSP)
- ❌ Cookies de refresh SIN httpOnly → vulnerable a XSS (JS puede robar token)
- ❌ Local funciona con proxy, pero el día de despliegue falla (fatalidad clásica)

**Con esto:**
- ✅ Frontend en `control.twintailcs.xyz` conecta a API en `api.twintailcs.xyz` sin bloques CORS
- ✅ Preflight OPTIONS resuelve sin 401
- ✅ Cookies de refresh protegidas: httpOnly + Secure + SameSite
- ✅ Headers de seguridad previenen clickjacking, MIME sniffing, XSS básicos
- ✅ Compliance: OWASP A01:2021 (Broken Access Control), A04:2021 (Insecure Design)

## Problema específico

1. **CORS disabled:** `.cors(AbstractHttpConfigurer::disable)` en `SecurityConfig` rompe frontend cross-origin. Local oculta el problema con proxy.
2. **No preflight handler:** `JwtAuthFilter` retorna 401 en `OPTIONS` requests → breaks fetch con credenciales y custom headers.
3. **Sin security headers:** Aplicación vulnerable a clickjacking, MIME sniffing, XSS básicos. No hay CSP, HSTS, X-Frame-Options.
4. **Cookies inseguras:** Refresh token sin httpOnly → atacante XSS puede robar vía `document.cookie`.

## Solución alto nivel

```
[Cross-origin Fetch] (control.twintailcs.xyz → api.twintailcs.xyz)
    ↓
[Preflight OPTIONS] → [Quarkus OPTIONS Handler]
    ↓
[CORS Filter: check Allow-Origin list]
    ↓
[Preflight Allowed → 200 OK + CORS headers]
    ↓
[Actual request (GET, POST, etc)]
    ↓
[JwtAuthFilter: skip CORS check for OPTIONS, verify JWT]
    ↓
[SecurityHeadersFilter: add CSP, HSTS, X-Frame-Options, etc]
    ↓
[Response 200 + security headers + CORS headers]
```

**Backend stack:**
- **Quarkus:**
  - `SecurityConfig` → enable CORS, configure origins, methods, credentials
  - `CorsFilter` → `@WebFilter` para interceptar OPTIONS, devolver preflight responses
  - `SecurityHeadersFilter` → `@WebFilter` para agregar CSP, HSTS, X-Frame-Options en todas las responses
- **application.properties:**
  - `quarkus.http.cors.origins` = `https://control.twintailcs.xyz` (prod), `http://localhost:4200` (dev)
  - `quarkus.http.cors.methods` = `GET,POST,PUT,DELETE,OPTIONS`
  - `quarkus.http.cors.headers` = `Content-Type,Authorization,X-Requested-With`
  - `quarkus.http.cors.access-control-allow-credentials` = `true`
  - `quarkus.http.cors.access-control-max-age` = `86400` (1 día)
- **Cookies:**
  - Refresh token → httpOnly, Secure, SameSite=Strict, Path=/api/auth/refresh
  - Access token → Bearer (no cookie, header Authorization), Secure si HTTPS

**Frontend:**
- Verificar que cookies se envían: `fetch(..., { credentials: 'include' })`
- Verificar preflight: network tab debe mostrar OPTIONS 200 antes del POST

## Alcance

✅ DENTRO:
- Quarkus CORS configuration vía `application.properties`
- CORS allowed origins: producción (`control.twintailcs.xyz`), dev (`localhost:4200`), staging (`staging.twintailcs.xyz`)
- CORS allowed methods: GET, POST, PUT, DELETE, OPTIONS
- CORS allowed headers: Content-Type, Authorization, X-Requested-With, Accept
- CORS credentials: true (para cookies httpOnly)
- CORS preflight cache: 86400 segundos (1 día)
- Security headers: CSP (default-src 'self'), HSTS (max-age=31536000), X-Frame-Options (DENY), X-Content-Type-Options (nosniff), X-XSS-Protection (1; mode=block)
- JwtAuthFilter: no bloquea OPTIONS, devuelve 200 OK
- Refresh token: httpOnly, Secure (HTTPS), SameSite=Strict, Path=/api/auth/refresh
- Access token: Bearer en Authorization header, no cookie
- Tests: unit tests para filter logic, integration tests para preflight y security headers
- Documentation: cuáles origins están permitidos, headers, valores de seguridad por entorno
- Docker-compose: entorno de dev con mock HTTPS (self-signed cert) para verificar Secure flag

❌ FUERA:
- CSP report-uri / report-to (futuro, sin integración con backend de reports)
- CORS + authentication layer (Keycloak/OAuth2) → futuro
- Subresource Integrity (SRI) → futuro, no aplica para API JSON
- HPKP (HTTP Public Key Pinning) → deprecated, omitir
- Proxy inverso CORS (nginx/Cloudflare) → futuro / infraestructura

---

## Beneficios

| Aspecto | Valor |
|---|---|
| **Funcionalidad** | Frontend cross-origin funciona. Desaparece `Access-Control-Allow-Origin` missing error. |
| **Seguridad** | Clickjacking bloqueado (X-Frame-Options), MIME sniffing bloqueado (X-Content-Type-Options), XSS básico prevenido (CSP). |
| **Cookies** | Refresh token protegido: httpOnly + Secure + SameSite. XSS no puede robar vía JS. |
| **Compliance** | OWASP A01 (Broken Access Control), A04 (Insecure Design) satisfechos. |
| **Operacional** | 0 latencia agregada (headers son inline). Dev/prod config en `application.properties`. |
| **UX** | Preflight cacheado 1 día → menos round trips. |

---

## Criterios de aceptación

- [ ] `application.properties` define `quarkus.http.cors.origins` con prod/dev/staging origins
- [ ] Preflight OPTIONS para `POST /api/auth/login` desde `control.twintailcs.xyz` devuelve 200 OK + CORS headers
- [ ] Access-Control-Allow-Credentials: true en preflight y responses
- [ ] Access-Control-Allow-Methods: GET, POST, PUT, DELETE, OPTIONS
- [ ] Access-Control-Allow-Headers: Content-Type, Authorization, X-Requested-With, Accept
- [ ] Access-Control-Max-Age: 86400 (preflight cacheado)
- [ ] Refresh token cookie: HttpOnly, Secure (HTTPS), SameSite=Strict, Path=/api/auth/refresh
- [ ] Security headers presentes en todas las responses:
  - [ ] Content-Security-Policy: default-src 'self'
  - [ ] Strict-Transport-Security: max-age=31536000; includeSubDomains
  - [ ] X-Frame-Options: DENY
  - [ ] X-Content-Type-Options: nosniff
  - [ ] X-XSS-Protection: 1; mode=block
- [ ] JwtAuthFilter NO retorna 401 en OPTIONS requests
- [ ] Fetch desde `control.twintailcs.xyz` con credenciales funciona end-to-end (preflight + actualrequest)
- [ ] Unit tests: verifican que filters agregan headers correctamente
- [ ] Integration tests: con backend en puerto 8081 y frontend simulado en otro origen, verifican CORS flow
- [ ] Documentation: README.md explica qué origins, headers, cookies, por qué SameSite=Strict

---

## Riesgos

| Riesgo | Probabilidad | Impacto | Mitigación |
|---|---|---|---|
| **CORS too permissive** | Baja (si se define origins explícitamente) | Alto (CSRF, token leakage) | Whitelist explícita; NO usar `*`; usar SameSite=Strict. |
| **Preflight cacheado demasiado** | Baja (86400 es standard) | Medio (si se agrega origin nuevo, no se ve hasta 1 día) | aceptable; en dev, valor bajo (3600). |
| **Cookies sin Secure en prod** | Muy baja (usar HTTPS) | Alto (man-in-the-middle roba token) | Verificar que Secure flag se setea si HTTPS. SSL/TLS obligatorio. |
| **CSP too strict bloquea features** | Media (si no bien testeado) | Medio (frontend break) | Staging testing; CSP report-only mode first, luego enforce. |
| **Options handler falla por excepción** | Baja | Medio (preflight falla, frontend falla) | Try-catch en filter; log warnings pero no fallar. |

---

**Status:** Ready for design  
**Last Updated:** 2026-11-06
