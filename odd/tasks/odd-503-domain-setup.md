# ODD #503: Domain Setup para MVP Control Electoral 2027

## 1. Contexto y Objetivos

El MVP corre en dev local con DNS implícito. Para staging y producción necesita:
- DNS registrado en Cloudflare (controls.brydyan.me con subdominios)
- Certificados TLS (wildcard)
- Env vars correctas en Railway (backend) y Vercel (frontend)
- Healthchecks para validar cada componente

**Objetivos:**
1. Registrar dominio `controls.brydyan.me` con subdominos en Cloudflare
2. Activar SSL wildcard automático
3. Inyectar env vars en Railway (backend) y Vercel (frontend)
4. Implementar 3 healthchecks (DB, storage, backend)
5. Documentar procedimiento en CREDENCIALES.md y DEPLOYMENT.md

## 2. Decisiones de Arquitectura

### D1 — DNS primario vs Cloudflare Workers
**Decisión**: DNS simple en Cloudflare. No Workers (realtime es post-MVP).
**Alternativa descartada**: Workers para redirecciones dinámicas.
- Añade complejidad innecesaria en MVP.
- Realtime viene en T-IN-06, no aquí.

### D2 — Certificados TLS
**Decisión**: SSL wildcard automático (Cloudflare universal SSL).
**Alternativa descartada**: Let's Encrypt via cert-manager en k8s.
- Railway/Vercel no soportan Docker in Docker.
- Cloudflare lo cubre en plan gratuito.

### D3 — Env vars: Railway vs secretos
**Decisión**: Railway UI para configurar; espejo en `openspec/env-vars-template.yaml`.
**Alternativa descartada**: Terraform/IaC para infra pre-MVP.
- Railway UI es manual la primera vez, después idempotente.
- IaC genera deuda técnica.

### D4 — Healthchecks
**Decisión**: Tres endpoints dedicados:
- `GET /api/v1/health/db` (conecta Neon, ejecuta `SELECT 1`)
- `GET /api/v1/health/storage` (conecta R2, lista buckets)
- `GET /api/v1/health/backend` (alive, sin deps)
- Frontend: `GET /health` (devuelve 200)

### D5 — Boot order y readiness
**Decisión**: Backend lanza, healthchecks POST-boot permiten a Railway saber si está ready.
**Regla**: Railway debe esperar a `/health/db` antes de rutear requests.

## 3. Especificación

### RF-DOM-01: A records en Cloudflare apuntan a Railway
GIVEN usuario accede a api.controls.brydyan.me
WHEN hace `nslookup api.controls.brydyan.me`
THEN resuelve a <Railway public IP>

### RF-DOM-02: CNAME para frontend en Vercel
GIVEN usuario accede a app.controls.brydyan.me o controls.brydyan.me
WHEN hace `nslookup`
THEN alias a vercel.app

### RF-DOM-03: SSL wildcard automatizado
GIVEN trafico HTTPS a *.controls.brydyan.me
WHEN browser abre conexión TLS
THEN sin error de certificado (Cloudflare universal SSL)

### RF-DOM-04: Env vars Railway funcionales
GIVEN Railway project de backend
WHEN desplegó y levantó
THEN todas las variables de entorno están presentes:
- SPRING_DATASOURCE_URL (Neon)
- AWS_S3_* (R2)
- APP_JWT_SECRET
- QUARKUS_DATASOURCE_JDBC_URL

### RF-DOM-05: Healthcheck DB funciona
GIVEN backend corriendo en Railway
WHEN cliente hace `GET /api/v1/health/db`
THEN responde 200 JSON: `{"status":"UP","database":"postgres"}`
O 503 si Neon no responde

### RF-DOM-06: Healthcheck Storage funciona
GIVEN backend corriendo en Railway
WHEN cliente hace `GET /api/v1/health/storage`
THEN responde 200 JSON: `{"status":"UP","s3":"bucket-list-ok"}`
O 503 si R2 no responde

### RF-DOM-07: Frontend env vars correctas
GIVEN Vercel deployment
WHEN build inyecta variables
THEN VITE_API_BASE_URL apunta a api.controls.brydyan.me

### RNF-DOM-01: Documentación en CREDENCIALES.md
GIVEN este change
WHEN se archiva
THEN CREDENCIALES.md contiene:
- Cómo registrar dominio en Cloudflare
- Cómo inyectar vars en Railway
- Cómo inyectar vars en Vercel
- Cómo testear healthchecks

## 4. Tasks

Rama: `infra/odd-503-domain-setup`, de `develop`.
Bloqueador: ninguno (es el primero).

### A · Averiguar el estado actual

- [ ] **A.1** — ¿Existe proyecto Railway actual? ¿Nombre?
  Anotar en `apply-progress.md`: `RAILWAY_PROJECT_NAME = "xxx"`.

- [ ] **A.2** — ¿Quién es dueño del dominio brydyan.me? ¿Está registrado en Cloudflare?
  Anotar: `DOMAIN_REGISTRAR = "cloudflare"`, `CLOUDFLARE_ACCOUNT = "xxx"`.

- [ ] **A.3** — Acceso a Railway: ¿existe API token? ¿Scopes?
  (Necesario para D3: plantilla env-vars.)

### B · DNS en Cloudflare

- [ ] **B.1** — Crea subdominios:
  - `api.controls.brydyan.me` → A record → Railway public IP
  - `app.controls.brydyan.me` → CNAME → cname.vercel-dns.com (o lo que Vercel pida)
  - `controls.brydyan.me` → CNAME → mismo Vercel
  Anotar en `apply-progress.md`: IPs y CNAMEs reales.

- [ ] **B.2** — Verifica SSL wildcard activo:
  ```bash
  openssl s_client -connect api.controls.brydyan.me:443 -servername api.controls.brydyan.me
  ```
  Busca "Issuer: ... Cloudflare". Anotar fingerprint.

- [ ] **B.3** — Test nslookup desde máquina del dev:
  ```bash
  nslookup api.controls.brydyan.me
  nslookup app.controls.brydyan.me
  ```
  Los IPs deben coincidir con B.1.

### C · Env vars Railway

- [ ] **C.1** — Crea plantilla en `openspec/env-vars-template.yaml`:
  ```yaml
  environment:
    SPRING_DATASOURCE_URL: jdbc:postgresql://[neon-host]:5432/[db-name]
    DB_USER: postgres_user_neon
    AWS_S3_ENDPOINT: https://xxx.r2.cloudflarestorage.com
    AWS_ACCESS_KEY_ID: "[R2 API token]"
    APP_JWT_SECRET: "[generado, ≥32 chars]"
    # ... etc
  ```
  Sin valores reales, solo placeholders y docs de fuentes.

- [ ] **C.2** — Crea variables en Railway UI:
  - Scoped a backend service
  - Referencia cada una contra C.1
  - Anotar en `apply-progress.md`: `RAILWAY_VARS_CREATED = <count>`

- [ ] **C.3** — Test de env vars inyectadas:
  ```bash
  curl https://api.controls.brydyan.me/actuator/env | jq '.propertySources[].properties | select(.name | contains("spring.datasource"))'
  ```
  (Requiere que Actuator esté en prod; alternativa: logs del container)

### D · Healthchecks (backend)

- [ ] **D.1** — Añade en backend:
  `src/main/java/ec/twintailcs/electoral/health/HealthController.java`
  ```java
  @RestController @RequestMapping("/api/v1/health")
  public class HealthController {
    @GetMapping("/db") public ResponseEntity<Map> db(DataSource ds) { ... }
    @GetMapping("/storage") public ResponseEntity<Map> storage(S3Client s3) { ... }
    @GetMapping("/backend") public ResponseEntity<Map> backend() { return 200; }
  }
  ```

- [ ] **D.2** — Tests unitarios:
  - HealthControllerTest.dbUp() → mock DataSource, mock OK
  - HealthControllerTest.dbDown() → mock exception, espera 503
  - HealthControllerTest.storageUp() → mock S3Client
  - HealthControllerTest.storageDown() → espera 503
  - HealthControllerTest.backendAlive() → espera 200 siempre

- [ ] **D.3** — Test manual contra Railway:
  ```bash
  curl https://api.controls.brydyan.me/api/v1/health/db
  curl https://api.controls.brydyan.me/api/v1/health/storage
  curl https://api.controls.brydyan.me/api/v1/health/backend
  ```
  Anotar respuestas en `apply-progress.md`.

### E · Frontend healthcheck

- [ ] **E.1** — Añade en frontend (Angular) ruta pública GET `/health`:
  `src/app/shared/health.service.ts`
  ```typescript
  @Injectable()
  export class HealthService {
    check(): Observable<{status: string}> {
      return of({status: 'UP'}).pipe(delay(0));
    }
  }
  ```

- [ ] **E.2** — Test:
  ```bash
  curl https://app.controls.brydyan.me/health
  ```

### F · Documentación

- [ ] **F.1** — Actualiza `documents/CREDENCIALES.md`:
  - Sección "## 🌐 Dominio (Cloudflare)"
  - Tabla: dominio, registrar, nameservers
  - Pasos: cómo registrar, cómo añadir A/CNAME, cómo verificar SSL

- [ ] **F.2** — Añade `documents/DEPLOYMENT.md`:
  - MVP despliega en Railway (backend) + Vercel (frontend)
  - Procedure: Railway UI → vars → deploy
  - Procedure: Vercel → GitHub integration → auto-deploy on push
  - Healthchecks: cómo testear los tres endpoints
  - Troubleshooting: DNS no resuelve (espera TTL), SSL error (Cloudflare espera ~24h)

### G · Mutaciones

**Regla**: cp, mutar, correr, anotar test fallido, restaurar, rm, `git status --short` limpio.

- [ ] **G.1** — RF-DOM-01: Test A record. Quita el A record de Cloudflare.
  Mutación: espera que `nslookup api.controls.brydyan.me` no resuelva.
  Restaura.

- [ ] **G.2** — RF-DOM-05: HealthCheck DB. Cambia SPRING_DATASOURCE_URL a host inválido.
  Mutación: espera `GET /health/db` → 503.
  Restaura.

- [ ] **G.3** — RF-DOM-06: HealthCheck Storage. Cambia AWS_ACCESS_KEY_ID a valor inválido.
  Mutación: espera `GET /health/storage` → 503.
  Restaura.

## 5. Compuertas antes de cerrar

```bash
# Resuelven
nslookup api.controls.brydyan.me          # A record OK
nslookup app.controls.brydyan.me          # CNAME OK

# SSL
openssl s_client -connect api.controls.brydyan.me:443 2>/dev/null | grep "Issuer"

# Healthchecks
curl https://api.controls.brydyan.me/api/v1/health/db
curl https://api.controls.brydyan.me/api/v1/health/storage
curl https://api.controls.brydyan.me/api/v1/health/backend
curl https://app.controls.brydyan.me/health

# Env vars en Railway
echo $SPRING_DATASOURCE_URL    # debe tener Neon host
echo $AWS_S3_ENDPOINT          # debe tener R2 host
```

Si todos los curls dan 200 ✓, la tarea cierra.

## 6. Qué NO hacer

- No resolver subdomios custom para staging (Proxmox) en esta fase.
- No añadir rate limiting en Cloudflare. (Eso es T-IN-15.)
- No activar DNSSEC (no es MVP).
- No crear secrets manager externo (Railway UI basta).

---

## ✅ Verificación (Verify Round N)

**Fecha**: YYYY-MM-DD  
**Verificador**: [agent name]  
**Status**: ⏳ Pending

### Checklist Verify
- [ ] ODD Phase Authorize: tasks.md coherente con requirements
- [ ] ODD Phase Explore: Builder exploró arquitectura + config patterns
- [ ] ODD Phase Implement: todas las tasks ejecutadas correctamente
- [ ] ODD Phase Close: apply-progress.md completo (valores reales + mutaciones)
- [ ] Mutaciones: cada una causó fallo esperado
- [ ] No hay hallazgos no documentados en tasks.md
- [ ] Compuertas pasaron (nslookup, SSL, curl healthchecks)

### Hallazgos
(a rellenar por Verify)

### Próximo step
**Si PASS**: Mover archivo a `odd/archive/VERIFIED-<YYYY-MM-DD>-domain-setup.md`
**Si REWORK**: Vuelve a Builder con esta sección actualizada
