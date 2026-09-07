# back — changes de backend

Fuente: `documents/task-control/backend.md` y `documents/task-control/backendtask/T-BE-*.md`.
Código: `backend/electoral-api/`. Paquete: `ec.twintailcs.electoral`.

---

## Compuertas de esta área

```bash
cd backend/electoral-api
./mvnw -B test > /tmp/test.log 2>&1; echo "exit=$?"        # NUNCA en un pipe
./mvnw -B clean package -DskipTests                        # exit 0
```

Línea base 2026-09-07 (`develop`, `108819d`): **19 suites / 93 tests / 78 ERROR / 1 skip**.
Está en rojo: hasta que sea 0, ninguna verificación de backend significa nada.

---

## Cola

| Tarea | Estado real | Change | Notas |
|---|---|---|---|
| T-BE-01 … T-BE-11 | implementadas | — | `COMPLETADO-*.md` |
| T-BE-12 Coordinación | implementada | — | `CoordinatorController` existe con los 6 endpoints, pero `T-BE-12.md` **no está renombrado** a `COMPLETADO-`. Cerrar |
| **suite de tests** | **78 errores** | `2026-09-07-fix-liquibase-rbac-order` | **abierto**. Bloquea todo lo demás |
| T-BE-13 Dashboard | TODO | por abrir | Bloqueado: sus tests no se distinguirían de los 78 rojos |
| T-BE-14 Supabase Storage | TODO | por abrir | `ObserverController:191-196` devuelve `501 NOT_IMPLEMENTED` en `/actas/{id}/imagen` con `TODO(T-BE-14)` |
| T-BE-15 Tests de integración | TODO | por abrir | Absorbe las mutaciones de T-BE-06/07/11/12. Ver ROADMAP §7 |
| T-BE-16 Observaciones en evidencia | TODO | bloqueado | El enum `TipoObservacion` está marcado «TBD — valores a definir por usuario». Falta decisión, no código |

---

## Lo que hay que verificar en esta área, no asumir

- **Dos mecanismos de autorización conviviendo** (§4.2 — una regla en un sitio y no en su
  vecino). `ObserverController` usa `@PreAuthorize("@authz.has('acta:upload')")` (permiso);
  `CoordinatorController` usa `hasAnyRole('COORDINATOR','MASTER')` (rol). Hay que comprobar
  endpoint por endpoint cuál corresponde, y que ninguno quedó sin ninguno.

- **El comentario sobre RLS** (§4.3 — el comentario que promete la garantía contraria).
  `ActaService.registrar` líneas 77-82 afirman que «RLS PostgreSQL + permisos JWT son el
  control de acceso vigente» tras quitar la comprobación de pre-asignación.
  `RlsInterceptor.java` existe en `shared/security/`. Verificar que está **cableado y
  ejecutándose** —que emite el `SET LOCAL app.current_user_id`— no que el archivo existe.

- **Controles que comparan contra un nombre** (§4.4). La tabla `rol` tiene `nombre` editable
  y `uk_rol_nombre` como única restricción. Buscar `getNombre().equals("MASTER")` y
  equivalentes: si alguien puede renombrar el rol, el control no existe.

- **Dos fuentes de verdad del esquema.** Liquibase
  (`src/main/resources/database/changelog/`) y los scripts manuales
  (`src/main/resources/database/infra/sql/01-schema.sql`, `02-rls.sql`, `03-seed.sql`).
  Nadie comprueba que coincidan. Es exactamente §4.1: cada lado probado por separado.

---

## Mutaciones canónicas del dominio

Cada change que toque una de estas garantías lleva su mutación en `tasks.md`.
Un test que pasa no prueba nada hasta que lo viste fallar.

| Garantía | Mutación | Debe caer |
|---|---|---|
| Unicidad `jrv_id + cargo_id` | quitar `findActaActivaConLock` de `ActaService.registrar` | test de doble registro |
| `SUM(votos)+nulos+blancos = totalSufragantes` | invertir el `!=` de la validación | test de suma inválida |
| Sólo MASTER anula | quitar `@PreAuthorize("hasRole('MASTER')")` de `CoordinatorController.anular` | test de COORDINATOR → 403 |
| Sólo se anulan actas APROBADAS | quitar el guard de estado en `ActaService.anular` | test de anular ENVIADA |
| Sólo se corrigen actas EN_DISPUTA | quitar el guard de estado en `ActaService.corregir` | test de corregir APROBADA |
| Un OBSERVER sólo corrige lo suyo | quitar `!esCoordinator && !acta.getRegistradaPor()...` | test de observer ajeno → 403 |
| `ADMIN` nunca escribe | quitar el guard de rol de un endpoint de escritura | test de ADMIN → 403 |
| Actas ANULADA fuera de estadísticas | quitar el filtro `!= ANULADA` | test de dashboard |

Anotar siempre **el nombre del test que cayó** en `apply-progress.md`. Si no cae ninguno,
el test es decorativo y hay que rehacerlo antes de seguir.
