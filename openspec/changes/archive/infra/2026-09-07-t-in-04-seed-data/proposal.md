# Proposal — T-IN-04: Datos Semilla

## Qué se construye (Retrospective)
Script SQL con datos iniciales: provincia Santa Elena, 3 cantones, parroquias, JRVs de ejemplo, usuario MASTER, cargos electorales.

Ubicación: `backend/electoral-api/src/main/resources/database/infra/sql/03-seed.sql`

## Status
✅ **COMPLETADO** — Seed data para staging local.

## Contenido
- Provincia Santa Elena (código 24)
- Cantones: Santa Elena (2401), La Libertad (2402), Salinas (2403)
- ~450 parroquias y JRVs de ejemplo
- Usuario MASTER inicial
- Cargos electorales (Prefecto, Alcalde, etc.)

## Garantías verificadas
- [ ] Script ejecuta sin errores
- [ ] Datos coherentes (FKs válidas)
- [ ] Usuario MASTER puede loguear
- [ ] Datos realistas para testing
