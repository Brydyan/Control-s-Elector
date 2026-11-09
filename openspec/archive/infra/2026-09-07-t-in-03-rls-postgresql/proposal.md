# Proposal — T-IN-03: RLS PostgreSQL

## Qué se construye (Retrospective)
Row Level Security policies: OBSERVER solo ve sus actas, COORDINATOR ve todas, etc.

Ubicación: `backend/electoral-api/src/main/resources/database/infra/sql/02-rls.sql`

## Status
✅ **COMPLETADO** — RLS policies implemented.

## Cómo funciona
- Spring Boot: `SET LOCAL app.current_user_id = '...'` antes de query
- PostgreSQL: RLS policy valida `app.current_user_id` contra `observer_jrv_asignacion`
- Segunda línea de defensa detrás de Spring Security

## Garantías verificadas
- [ ] Policies creadas para todas las tablas
- [ ] OBSERVER no ve actas de otros
- [ ] COORDINATOR ve todas las actas
- [ ] SET LOCAL funciona en transacción
