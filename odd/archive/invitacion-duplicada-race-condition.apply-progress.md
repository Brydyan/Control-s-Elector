# Apply-Progress — Invitación Duplicada (Race Condition)

## Status: DONE

### A. Frontend
- [x] `puedeGuardar()` agregado: deshabilita Guardar si `submitting()` o `invitaando()`
- [x] Template usa `[disabled]="!puedeGuardar()"`
- [x] `guardar()` retorna temprano si `submitting()` ya está activo (defensa en TS)
- [x] Tests: `guardar_noDisparaSegundoRequestCuandoSubmitting`, `puedeGuardar_falseCuandoSubmitting`, `puedeGuardar_falseCuandoInvitaando`, `puedeGuardar_trueCuandoNoHayOperacionEnVuelo`

### B. Backend
- [x] Validación preventiva en `UserInvitationService.invitar()`: `Usuario.count("emailPersonal", emailPersonal) > 0` → `InvitacionException.conflicto(...)` (409)
- [x] Manejo de carrera: `persist()` captura `PersistenceException`; si es unicidad de `email_personal` (SQLState 23505 / constraint `uq_usuario_email_personal` o `idx_usuario_email_personal`) lanza 409 con el mismo mensaje
- [x] Mensaje claro: `Ya existe usuario con email personal: <email>`
- [x] Tests: `unEmailPersonalYaUsadoResponde409`, `dosInvitacionesParalelasMismoEmailPersonal` (afirma nunca 500, nunca >1 fila; si hay 409 verifica mensaje)

### C. DB (Opcional)
- [x] No aplicado, no requerido. El índice único parcial `idx_usuario_email_personal` (`046-usuario-email-personal-unique.sql`, `WHERE email_personal IS NOT NULL`) ya impide duplicados en BD; el constraint DEFERRABLE es redundante pero no se toca el schema. La capa app mapea la violación de unicidad a 409 con shape estándar.

### Verification (real)
- [x] Backend target: `./mvnw -B -Dmaven.repo.local=/tmp/m2/repository -Dquarkus.management.test-port=9002 -Dtest=UserInvitationIT test` → **16 tests, 0 failures, 0 errors**
- [x] Backend format: `./mvnw -B -Dmaven.repo.local=/tmp/m2/repository spotless:check` → **BUILD SUCCESS**
- [x] Frontend target: `pnpm vitest run src/app/features/admin/pages/usuarios/admin-usuarios.page.spec.ts` → **16 tests passed**
- [x] Frontend format/lint: `pnpm format:check && pnpm lint` → **PASS**
- [ ] Suite completa backend/frontend no rerun en esta sesión (tests locales del usuario / CI)

### Files Changed
- `backend/electoral-api-quarkus/src/main/java/ec/twintailcs/electoral/auth/application/UserInvitationService.java`
- `backend/electoral-api-quarkus/src/test/java/ec/twintailcs/electoral/auth/web/UserInvitationIT.java`
- `frontend/app/src/app/features/admin/pages/usuarios/admin-usuarios.page.ts`
- `frontend/app/src/app/features/admin/pages/usuarios/admin-usuarios.page.html`
- `frontend/app/src/app/features/admin/pages/usuarios/admin-usuarios.page.spec.ts`
- `odd/tasks/invitacion-duplicada-race-condition.apply-progress.md`

### Notes
- Caso paralelo cerrado a nivel contrato HTTP: la BD ya bloqueaba por índice único; el servicio ahora traduce esa violación de unicidad a 409, evitando 500 por race condition.
- No se tocó schema/Liquibase, Playwright/T-IN-13, k8s, ni archivos de Phase 2 Valkey.
