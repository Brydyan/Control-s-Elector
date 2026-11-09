# Archive — Backend Retrospective Changes

Cambios retrospectivos completados (T-BE-01 … T-BE-11).

Estos cambios documentan el estado actual del código implementado y sus garantías. No tienen phase de `apply` o `verify` — son **auditorías de especificación**, donde se escriben las garantías que el código ya cumple, se verifican por mutación, y se archivan como base para el próximo mantenimiento.

## Cambios Archivados

| Cambio | Título | Garantías verificadas |
|--------|--------|----------------------|
| T-BE-01 | Entidades base: geografía y usuarios | 6 (unicidad, RLS, habilitar/deshabilitar, RefreshToken dead code) |
| T-BE-02 | Entidades electorales | 6 (nivel geográfico, candidato/cargo, unicidad) |
| T-BE-03 | Entidades de acta | 7 (enums, terminalidad, no borrado, auditoría) |
| T-BE-04 | Repositorios JPA | 6 (locking, exclusión ANULADA, filtrado) |
| T-BE-05 | AuditLogService | 5 (auditoría, IP, convención) |
| T-BE-06 | Registro de acta con locking | 6 (unicidad, invariante, RLS, candidato/cargo) |
| T-BE-07 | Ciclo de vida del acta | 9 (máquina de estados, auditoría, notificaciones) |
| T-BE-08 | Spring Security + JWT | 11 (autenticación, autorización, CORS, TTL, RLS) |
| T-BE-09 | AuthController | 9 (endpoints, email colisión, revocación) |
| T-BE-10 | Configuración preelectoral | 8 (RBAC, lectura/escritura, coherencia, borrado) |
| T-BE-11 | Endpoints OBSERVER | 9 (rutas, aislamiento, filtrado, N+1) |

**Línea base 2026-09-07**: 78 tests en ERROR por Liquibase (bloqueante principal).
Todos estos cambios dependen de `back/2026-09-07-fix-liquibase-rbac-order` — el primer paso es arreglarlo.

## Decisiones Arquitectónicas Registradas

Cada cambio captura una decisión o un hallazgo:
- T-BE-01: `RefreshToken` es código muerto (usa `Sesion` en su lugar)
- T-BE-02: Coherencia nivel ↔ geografía no validada — decision pendiente
- T-BE-03: Campo `fechaRegistro` (no `registradaEn`)
- T-BE-04: Locking pesimista existe pero no tiene test de concurrencia real
- T-BE-05: Auditoría de evidencia falta (se repara en T-BE-14)
- T-BE-06: RLS no está demostrada — decision pendiente
- T-BE-07: Notificación de aprobación falta — decision pendiente
- T-BE-08: CORS deshabilitado para prod, Swagger público, TTL 7d (no 8h)
- T-BE-09: `/me` probablemente roto, revocación por MASTER no existe
- T-BE-10: Endpoint `JRV_CARGO` no existe — decision pendiente
- T-BE-11: `GET /jrvs` hace N+1 consultas (sin datos realistas, no se nota)

## Próximos Pasos

1. **Bloqueante**: Fijar `back/2026-09-07-fix-liquibase-rbac-order` (78 tests → 0 error)
2. Implementar y verificar T-BE-12…T-BE-16 (pendientes)
3. Ejecutar `sdd-apply` en los retrospectivos (verificar cada garantía por mutación)
4. Decidir las 7 preguntas abiertas de arquitectura
5. Cerrar con `sdd-archive`
