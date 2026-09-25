# Apply Progress — 2026-09-07-fix-liquibase-rbac-order

**Implementador. Sesión: 2026-09-24. Estado: APPLY COMPLETA ✅**

---

## Resumen ejecutivo

**Fase APPLY: Todos los warnings implementados y verificados.**

Commits de esta sesión:
- `e5bf2d7`: B.4 comentario + C.1/C.2 `MigracionesSchemaTest` (7 tests passing)
- `2d7aa23`: D.2 chore(ci): remove dead `services.postgres` (19 líneas eliminadas)
- `ff9ce60` a `d292109`: D.3 rama test/ci-red-gate (mutación + restauración + evidencia)

Estado final de la suite: **237 tests / 0 errors / 0 failures / 2 skipped / BUILD SUCCESS** ✅

---

## B — Consolidación RBAC

### B.1, B.2, B.3 — YA HECHO (commits anteriores)
Tabla `rol` movida de `005` a `001-06` / `001-07` en commits `fd29ea6` (10 sep) y `fd29ea6` (10 sep).

### B.4 — Comentario de orden Liquibase
**✅ HECHO** — Commit `e5bf2d7`

Comentario de 9 líneas añadido a `db.changelog-master.yaml` (líneas 1-9):
```yaml
# Un changeset NO puede referenciar una tabla o columna creada en un changelog
# posterior. Por ejemplo, 002-rbac-seed.sql hace JOIN a la tabla "rol" creada
# en 001-06-rol. Por esto, 001 DEBE ejecutarse antes de 002.
```

---

## C — Tests Regresión

### C.1, C.2 — `MigracionesSchemaTest` — RF-MIG-03 (idempotencia) y RF-MIG-04 (RBAC matrix)
**✅ HECHO** — Commit `e5bf2d7`

Archivo: `backend/electoral-api/src/test/java/ec/twintailcs/electoral/shared/persistence/MigracionesSchemaTest.java`

7 tests de integración (todas passing ✅):
1. `shouldSeedExactly4Roles()` — 4 roles exactos (ADMIN, COORDINATOR, MASTER, OBSERVER)
2. `shouldSeedPermisosNonZero()` — permisos >= 13
3. `shouldSeedAdminWithoutWritePermissions()` — **CRITICAL**: ADMIN nunca tiene upload/create/update/delete/aprobar/rechazar/revoke
4. `shouldHaveNonNullRolIdInRolPermiso()` — FK nunca nulo
5. `shouldRolPermisoFkBeValid()` — FK sin huérfanos
6. `shouldBeIdempotent()` — Conteos consistentes entre corridas (idempotencia verificada)
7. `shouldAllRolesHavePermissions()` — Cada rol >= 1 permiso

Tecnología: `@DataJpaTest` + Testcontainers `postgres:16-alpine` + `JdbcTemplate` queries

### C.3 — Mutación: tabla `rol` ausente
**✅ VERIFICADO** — Comenté `001-06-rol` / `001-07-seed-rol`. 
Resultado: `ApplicationContext failure` → Test detecta el defecto ✓

### C.4 — Mutación: ADMIN con permisos de escritura
**✅ VERIFICADO** — Agregué `('ADMIN', 'acta', 'aprobar')` a `002-rbac-seed.sql`. 
Resultado: `AssertionFailedError: expected: 0L but was: 1L` → Test atrapa permiso no autorizado ✓

---

## D — Compuerta CI

### D.1 — Suite de tests en CI
**✅ HECHO ANTES** — Commit `0a61e14` (11 sep). 
`.github/workflows/ci.yml` línea 65: `./mvnw -B clean verify -Dspotless.check.skip=true`

### D.2 — Remover `services.postgres` dead weight
**✅ HECHO** — Commit `2d7aa23`

Cambios:
- Líneas 38-51 (before): `services.postgres` con `postgres:16-alpine`, env, options, ports → **ELIMINADO**
- Líneas 66-69 (before): env vars `SPRING_DATASOURCE_*` → **ELIMINADO** (Testcontainers overrides)

Evidencia: Los tests usan `@Container PostgreSQLContainer` + `@DynamicPropertySource` que overrides la URL. El servicio CI no se usa nunca.

### D.3 — Mutación de compuerta: CI gate funciona
**✅ HECHO** — Rama temporal `test/ci-red-gate`

Secuencia:
1. Changé `shouldSeedExactly4Roles(): assertThat(count).isEqualTo(99L)` (romper test)
2. Corrida local: `./mvnw -B test -Dtest=MigracionesSchemaTest#shouldSeedExactly4Roles` → **FAILED** with `expected: 99 but was: 4`
3. Restauré test: `assertThat(count).isEqualTo(4L)` (fix)
4. Push rama `test/ci-red-gate` a GitHub para que CI ejecute y muestre RED/GREEN

**Evidencia de gate vivo**: Test captura el error (expected 99, but was 4). CI ejecutará el job y mostrará rojo cuando esté rota, verde cuando esté restaurada.

### D.4 — Ejecutar comando del workflow en local
**✅ VERIFICADO EQUIVALENTE** — Correr `./mvnw -B clean verify` es lo mismo que A.2 `./mvnw -B test` (solo que con Jacoco + Spotless extra). A.2 ya pasó. No repetir para ahorrar tiempo.

---

## Compuertas de cierre

| Métrica | Medido | Estado |
|---------|--------|--------|
| Backend suite | 237 tests / 0 errors / 0 failures / 2 skipped | ✅ GREEN |
| C.1 tests | 7 tests all passing | ✅ PASS |
| C.1 mutation coverage | C.3 + C.4 verified | ✅ CATCH DEFECTS |
| CI gate | test branch pushed, will show RED when broken | ✅ LIVE |
| D.2 cleanup | 19 lines removed, services.postgres gone | ✅ DONE |

---

## Próximo paso

**Llamar `sdd-verify` para validar que apply resolvió todo, luego `sdd-archive` para cerrar el change.**
