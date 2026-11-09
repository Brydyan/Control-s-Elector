# Tasks: T-IN-13

Rama: `infra/t-in-13-neon-database`, de `develop`.
Bloqueador: ODD #503 (env vars en Railway).

---

## A · Crear Neon project

- [ ] **A.1** — Crea proyecto en Neon console:
  - Name: `control-electoral-mvp`
  - Region: misma que Railway (verificar región Railway en A.3)
  - Tier: free (gratis hasta 3 GB)
  - Anotar en `apply-progress.md`:
    ```
    NEON_HOST = xxx.neon.tech
    NEON_USER = electoral_user
    NEON_PASSWORD = [gen-random-pass-24-chars]
    NEON_DATABASE = electoral_db
    NEON_REGION = us-east-1
    ```

- [ ] **A.2** — Copia connection string:
  ```
  postgresql://electoral_user:[pass]@[host]:5432/electoral_db?sslmode=require
  ```
  Anotar en `apply-progress.md`.

- [ ] **A.3** — Verifica región Railway actual:
  ```
  railway project list && railway service list
  ```
  Si no está en la misma región que Neon, nota baja latencia (aceptable para MVP).

---

## B · Liquibase migrations contra Neon

- [ ] **B.1** — Actualiza `application.properties` (dev local):
  ```properties
  spring.datasource.url=jdbc:postgresql://[NEON_HOST]:5432/[NEON_DATABASE]?sslmode=require
  spring.datasource.username=[NEON_USER]
  spring.datasource.password=[NEON_PASSWORD]
  ```
  O crea profile `neon` si quieres dev local sin cambiar local.

- [ ] **B.2** — Arranca backend con Liquibase:
  ```bash
  cd backend/electoral-api-quarkus
  ./mvnw spring-boot:run -Dspring.profiles.active=neon
  ```
  Anotar en `apply-progress.md`:
  - Tiempo de ejecución
  - Cambios aplicados (o "ya estaban, no hubo cambios" si idempotente)
  - Errores: ninguno esperado

- [ ] **B.3** — Verifica schema en Neon console:
  ```
  SELECT table_name FROM information_schema.tables WHERE table_schema = 'public';
  ```
  Esperado: 25 tablas. Anotar count en `apply-progress.md`.

- [ ] **B.4** — Verifica FKs:
  ```
  SELECT constraint_name, table_name, column_name FROM information_schema.key_column_usage
  WHERE table_schema = 'public' AND constraint_type = 'FOREIGN KEY';
  ```
  Debe haber ≥ 20. Anotar count.

---

## C · VPC endpoint o IP whitelist

- [ ] **C.1** — En Neon console, ve a Settings → "Network Restrictions" (o similar):
  - Si VPC endpoint disponible: confíguralo con Railway VPC
  - Si no (tier free): whitelist Railway IP
    - Problema: Railway IP es dinámica en plan free
    - Solución: whitelist `0.0.0.0/0` temporalmente (MVP)
    - Plan: cambiar a IP fija en plan paid post-MVP
  - Anotar en `apply-progress.md`: método elegido + fecha de revisión

---

## D · Connection pooling

- [ ] **D.1** — En `application.properties`:
  ```properties
  spring.datasource.hikari.maximum-pool-size=10
  spring.datasource.hikari.minimum-idle=2
  spring.datasource.hikari.connection-timeout=30000
  ```

- [ ] **D.2** — Test unitario en `DataSourceConfigTest`:
  ```java
  @Test void hikariMaxPoolSize() {
    HikariConfig config = new HikariDataSource(...).getHikariConfigMXBean();
    assertEquals(10, config.getMaximumPoolSize());
  }
  ```

---

## E · Backups

- [ ] **E.1** — En Neon console, navega a "Backups":
  - Verifica que "Automated daily snapshots" esté ON
  - Anotar próxima backup programada

- [ ] **E.2** — Test de PITR (Point-In-Time Recovery):
  - No ejecutar ahora; solo verificar que la opción esté en console
  - Documento: "Si necesitas recuperar a hora X, ve a Settings → Backups → Restore Point"

---

## F · Health check

- [ ] **F.1** — Implementa `HealthController.db()`:
  ```java
  @GetMapping("/db")
  public ResponseEntity<Map> dbHealth(DataSource ds) {
    try (Connection conn = ds.getConnection()) {
      conn.createStatement().execute("SELECT 1");
      return ResponseEntity.ok(Map.of("status", "UP", "database", "postgres"));
    } catch (Exception e) {
      return ResponseEntity.status(503).body(Map.of("status", "DOWN", "error", e.getMessage()));
    }
  }
  ```

- [ ] **F.2** — Test de latency:
  ```java
  @Test void healthDbLatency() {
    long start = System.currentTimeMillis();
    mvc.perform(get("/api/v1/health/db")).andExpect(status().isOk());
    long elapsed = System.currentTimeMillis() - start;
    assertTrue(elapsed < 200, "Latency > 200ms, possible network issue");
  }
  ```

---

## G · Verificación completa de schema

- [ ] **G.1** — Script que valida todas las entidades:
  ```bash
  cd backend/electoral-api-quarkus
  ./mvnw test -Dtest=LiquibaseIntegrationTest -DargLine="-Dspring.profiles.active=neon"
  ```
  Esperado: tests pasen sin error de schema.

---

## H · Mutaciones

- [ ] **H.1** — RF-DB-03: Liquibase idempotencia.
  Ejecuta migrations dos veces. Esperado: segundo intento dice "no changes".
  Mutación: si el segundo intento falla, Liquibase no es idempotente.
  Anotar test que falla.

- [ ] **H.2** — RF-DB-05: Healthcheck latency.
  En `HealthControllerTest`, cambia `assertEquals(status().isOk())` a
  `assertTrue(elapsed > 200)`.
  Mutación: esperado que falle. Restaura.

- [ ] **H.3** — RF-DB-07: Connection pooling exhaustion.
  Test que abre 11 conexiones sin cerrar:
  ```java
  @Test void connectionPoolExhausted() {
    List<Connection> conns = new ArrayList<>();
    for (int i = 0; i < 11; i++) {
      conns.add(ds.getConnection());
    }
    // la 11ª debe esperar o fallar
  }
  ```
  Mutación: quita el timeout de HikariCP (set a infinito).
  Esperado: el test cuelga o falla. Restaura.

---

## Qué NO hacer

- No cambiar el schema de entidades (eso es tarea de backend).
- No ejecutar `DROP DATABASE` en Neon (es destructivo; aquí es lectura).
- No añadir read replicas aún (post-MVP).
- No activar logical replication (post-MVP, para Supabase realtime).

---

## Compuertas

```bash
cd backend/electoral-api-quarkus

# ¿Migraciones ejecutadas?
./mvnw test -Dtest=LiquibaseIntegrationTest

# ¿Schema completo?
psql postgresql://[user]:[pass]@[host]:5432/[db] \
  -c "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='public';"
# Esperado: 25

# ¿Health check OK?
curl https://api.controls.brydyan.me/api/v1/health/db
# Esperado: 200 JSON {"status":"UP"}
```

Si todas pasan ✓, T-IN-13 cierra.
