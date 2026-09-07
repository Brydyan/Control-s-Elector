# Archive — Infra Retrospective Changes

Cambios retrospectivos completados (T-IN-01 … T-IN-04).

Estos cambios documentan infraestructura ya implementada. No tienen fase de `apply` o `verify` — son **auditorías de especificación**, donde se escriben las garantías que el código ya cumple, se verifican por mutación, y se archivan como base para el próximo mantenimiento.

## Cambios Archivados

| Cambio | Título | Status |
|--------|--------|--------|
| T-IN-01 | Docker Compose staging completo | ✅ COMPLETADO |
| T-IN-02 | Schema SQL inicial | ✅ COMPLETADO |
| T-IN-03 | RLS PostgreSQL | ✅ COMPLETADO |
| T-IN-04 | Datos semilla (seed) | ✅ COMPLETADO |

**Línea base 2026-09-07:** Todo implementado. Solo falta cloud deployment (T-IN-05 en adelante).

## Próximos Pasos

1. Implementar T-IN-05 a T-IN-12 (cloud deployment)
2. Integrar estas retrospectives con nuevos cambios (Realtime, deploy, CI/CD)
3. Validar que staging local + cloud prod funcionan juntos

## Notas

- Staging local es reproducible (Docker Compose)
- Schema y RLS son documentados (referencia viva)
- Seed data permite probar sin configuración manual
- Pendiente: integración entre local (Docker) + cloud (Supabase)
