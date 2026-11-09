# Tasks: T-IN-07r

Rama: `infra/t-in-07r-railway-backend`, de `develop`.
Bloqueadores: T-IN-13 (Neon creado) + T-IN-14 (R2 creado) + ODD #503 (dominio).

---

## A · Audit current application.properties

- [ ] **A.1** — Revisa `backend/electoral-api-quarkus/src/main/resources/application.properties`:
  - Identifica todas las vars de env usadas (grep `${`)
  - Anotar lista en `apply-progress.md`:
    ```
    SPRING_DATASOURCE_URL
    SPRING_DATASOURCE_USERNAME
    SPRING_DATASOURCE_PASSWORD
    AWS_S3_ENDPOINT
    AWS_ACCESS_KEY_ID
    AWS_SECRET_ACCESS_KEY
    APP_JWT_SECRET
    ... (etc)
    ```

- [ ] **A.2** — Verifica que cada variable tenga valor default EN PROPERTIES o EN ENV:
  - Si no tiene default, Railway fallará en boot.
  - Anotar cuáles tienen default, cuáles no.

---

## B · Create application-prod.properties

- [ ] **B.1** — Crea `backend/electoral-api-quarkus/src/main/resources/application-prod.properties`:
  ```properties
  # Prod overrides (Railway) — inyecta via env vars
  spring.datasource.url=${SPRING_DATASOURCE_URL}
  spring.datasource.username=${SPRING_DATASOURCE_USERNAME}
  spring.datasource.password=${SPRING_DATASOURCE_PASSWORD}
  
  aws.s3.endpoint=${AWS_S3_ENDPOINT}
  aws.access.key.id=${AWS_ACCESS_KEY_ID}
  aws.secret.access.key=${AWS_SECRET_ACCESS_KEY}
  
  app.jwt.secret=${APP_JWT_SECRET}
  # ... etc, todo via ${VAR}
  ```
  Sin valores hardcodeados.

- [ ] **B.2** — Verifica que application.properties (default) TODAVÍA tenga valores
  para dev local (compose.yaml):
  ```properties
  # Dev defaults
  spring.datasource.url=jdbc:postgresql://localhost:5432/electoral_db
  spring.datasource.username=electoral_user
  ...
  ```

---

## C · Boot tests with Spring profiles

- [ ] **C.1** — Crea `ApplicationBootTest.java`:
  ```java
  @SpringBootTest(properties = {
      "spring.datasource.url=jdbc:postgresql://neon-host:5432/db",
      "spring.datasource.username=user",
      "spring.datasource.password=pass"
  })
  @ActiveProfiles("prod")
  class ApplicationBootTest {
    @Test void contextLoads() {
      // Si esto pasa, Spring boot cargó sin error
    }
  }
  ```

- [ ] **C.2** — Test con profile dev (default):
  ```java
  @SpringBootTest
  @ActiveProfiles("dev") // o sin profile, default
  class ApplicationBootDevTest {
    @Test void contextLoadsWithTestcontainers() {
      // Testcontainers arrancará automáticamente si el perfil lo necesita
    }
  }
  ```

---

## D · Railway configuration (via UI o CLI)

- [ ] **D.1** — Accede a Railway project (el que existe de ODD #503):
  - Ve al servicio "backend"
  - Ve a "Variables" y añade todas de A.1:
    ```
    SPRING_DATASOURCE_URL = jdbc:postgresql://[neon-host]:5432/[db]?sslmode=require
    SPRING_DATASOURCE_USERNAME = [neon_user]
    SPRING_DATASOURCE_PASSWORD = [neon_pass]
    AWS_S3_ENDPOINT = https://[account].r2.cloudflarestorage.com
    AWS_ACCESS_KEY_ID = [r2_key]
    AWS_SECRET_ACCESS_KEY = [r2_secret]
    APP_JWT_SECRET = [gen-32-chars-random]
    SPRING_PROFILES_ACTIVE = prod
    ```
  - Anotar en `apply-progress.md`: "RAILWAY_VARS_SET = N" (count).

- [ ] **D.2** — Verifica que Railway detecte cambios:
  - Si cambias una var, Railway rebuilda + redeploy automáticamente.
  - Espera a que el build complete (5–10 min típico).
  - Anotar tiempo de primera compilación.

---

## E · Health checks en Railway

- [ ] **E.1** — En Railway console:
  - Ve a servicio backend → "Deploy" → "Health Check"
  - Configura:
    ```
    Path: /api/v1/health/backend
    Initial Delay: 30s (Liquibase + boot)
    Interval: 30s
    Timeout: 10s
    Threshold: 3 failures = unhealthy
    ```
  - Salva.

- [ ] **E.2** — Observa logs de Railway:
  ```bash
  railway logs -s backend --follow
  ```
  Busca:
  - "Liquibase: Migration successful" o "Liquibase: No migrations to run"
  - "/api/v1/health/backend: 200"
  - Sin errores de conexión a Neon/R2

---

## F · Verify boot sequence

- [ ] **F.1** — Arranca backend localmente contra Neon (sin Railway):
  ```bash
  cd backend/electoral-api-quarkus
  export SPRING_PROFILES_ACTIVE=prod
  export SPRING_DATASOURCE_URL="jdbc:postgresql://[neon-host]:5432/[db]?sslmode=require"
  export SPRING_DATASOURCE_USERNAME=[user]
  export SPRING_DATASOURCE_PASSWORD=[pass]
  export AWS_S3_ENDPOINT=https://[account].r2.cloudflarestorage.com
  export AWS_ACCESS_KEY_ID=[key]
  export AWS_SECRET_ACCESS_KEY=[secret]
  export APP_JWT_SECRET=[secret]
  
  ./mvnw spring-boot:run
  ```
  Esperado: boot sin error, Liquibase ejecuta una vez, healthchecks responden 200.
  Anotar en `apply-progress.md`: "Local boot against Neon: OK".

- [ ] **F.2** — Healthchecks:
  ```bash
  curl http://localhost:8081/api/v1/health/backend
  curl http://localhost:8081/api/v1/health/db
  curl http://localhost:8081/api/v1/health/storage
  ```
  Todas deben ser 200. Anotar.

---

## G · Railway deployment

- [ ] **G.1** — Pushea rama a GitHub:
  ```bash
  git push origin infra/t-in-07r-railway-backend
  ```

- [ ] **G.2** — Railway detecta push:
  - Ve a Railway console → servicio backend → "Deployments"
  - Espera a que build complete (si está en beta, Railway redeploya auto)
  - Anotar time-to-deploy en `apply-progress.md`.

- [ ] **G.3** — Verifica deployment en Railway:
  ```bash
  curl https://api.controls.brydyan.me/api/v1/health/backend
  curl https://api.controls.brydyan.me/api/v1/health/db
  curl https://api.controls.brydyan.me/api/v1/health/storage
  ```
  Las tres deben ser 200. Si alguna es 503, revisar Railway logs.

---

## H · Mutaciones

- [ ] **H.1** — RF-RW-02: Env var missing.
  Quita SPRING_DATASOURCE_URL de Railway vars.
  Mutación: backend falla al boot con error de conexión.
  Anotar el error exacto. Restaura var.

- [ ] **H.2** — RF-RW-04: Profiles override.
  En application-prod.properties, cambia un valor (ej: `spring.datasource.url = jdbc:postgresql://invalid:5432/invalid`).
  Mutación: boot con prod profile falla en conexión.
  Restaura.

- [ ] **H.3** — RF-RW-06: Liquibase lock.
  En Liquibase, añade una migración de prueba (ej: ALTER TABLE test ADD COLUMN test_col INT).
  Arranca dos instancias del backend (localmente o en Railway preview deploys):
  ```bash
  # Terminal 1
  ./mvnw spring-boot:run -Dspring.profiles.active=prod &
  
  # Terminal 2 (rápido, antes de que T1 termine Liquibase)
  ./mvnw spring-boot:run -Dspring.profiles.active=prod
  ```
  Mutación: espera que T2 espere en lock (logs muestran "Waiting for lock").
  Una vez T1 termina, T2 procede. Anotar tiempos. Quita la migración de prueba.

---

## Qué NO hacer

- No hardcodear secrets en application.properties (fue hecho en D1).
- No cambiar Liquibase config (ddl-auto, changeLogFile) — ya está optimizado.
- No añadir circuit breaker sin medir (post-MVP).
- No mover el backend a Kubernetes aquí (decide usuario post-MVP).

---

## Compuertas

```bash
cd backend/electoral-api-quarkus

# ¿Tests pasan?
./mvnw -B test
# Esperado: exit 0, N tests

# ¿Build aislado (sin Testcontainers)?
./mvnw -B clean package -DskipTests
# Esperado: BUILD SUCCESS

# ¿Boot local contra Neon?
SPRING_PROFILES_ACTIVE=prod ./mvnw spring-boot:run
# Esperado: inicia sin error en <30s

# ¿Health checks responden?
curl http://localhost:8081/api/v1/health/db
curl http://localhost:8081/api/v1/health/storage
curl http://localhost:8081/api/v1/health/backend
# Todas: 200

# ¿Railway boot OK?
curl https://api.controls.brydyan.me/api/v1/health/backend
# Esperado: 200
```

Si todas pasan ✓, T-IN-07r cierra.
