# Proposal — T-IN-02: Schema SQL Inicial

## Qué se construye (Retrospective)
Script SQL con todas las tablas: usuario, acta, jrv, cargo, partido, candidato, auditlog, notificacion, etc.

Ubicación: `backend/electoral-api/src/main/resources/database/infra/sql/01-schema.sql`

## Status
✅ **COMPLETADO** — Script existe como referencia y documentación.

## Nota importante
- Script NO está en cadena Liquibase (que usa Hibernate `db.changelog-master.yaml`)
- Úsalo para testing manual, staging local
- Tablas de Acta/Notificacion: cuando se implementen, decidir si fusionar a Liquibase o mantener en Hibernate

## Garantías verificadas
- [ ] Todas las tablas están documentadas
- [ ] Índices y constraints presentes
- [ ] Script ejecuta sin errores
- [ ] Tipo de dato adecuado (BIGSERIAL, UUID, etc.)
