# ODD — Import JRVs from CNE Data

**Fecha:** 2026-11-16  
**Scope:** Importación de JRVs (Juntas de Recepción de Votos) desde datos del CNE  
**Status:** 📋 Structurado (pendiente implementación)

---

## Propósito

Habilitar carga de geografía completa (JRVs) pre-elección desde datos reales del CNE. MVP usa seed Liquibase; esta ODD formaliza el procedimiento.

---

## Scope de Tareas

### A) Crear migration Liquibase 026-data-jrvs-produccion.sql

**Qué:**
- Crear changeset: `026-data-jrvs-produccion context:seed`
- Estructura: Provincia → Canton → Parroquia → JRV
- Idempotente: `NOT EXISTS` guards en cada INSERT

**Criterio de aceptación:**
- [ ] Migration corre sin errores
- [ ] 0 duplicados (FK unique constraints respetadas)
- [ ] JRVs referenciables por actas (FK jrv_id válidas)
- [ ] Rollback funciona: `liquibase rollback-count 1`

### B) Documentar validaciones & data integrity

**Qué:**
- Escribir guide: `JRVS_IMPORT_GUIDE.md`
- Listar validaciones obligatorias:
  - Codes únicos (provincia.codigo, canton.codigo, jrv.numero)
  - FK integrity (canton → provincia, parroquia → canton, jrv → parroquia)
  - totalElectores > 0
  - recinto no vacío

**Criterio de aceptación:**
- [ ] Guide documentado en `/documents/`
- [ ] Incluye ejemplos de datos válidos/inválidos
- [ ] Menciona rollback procedure

### C) Crear test data fixture (5 JRVs mínimo)

**Qué:**
- Seed data refleja geografía real del CNE (o muestra ficticia realista)
- Cobertura: 2+ provincias, 3+ cantones, 4+ parroquias, 5+ JRVs
- Electores: rango realista (150-500 por JRV)

**Criterio de aceptación:**
- [ ] Query `SELECT COUNT(*) FROM jrv;` retorna ≥ 5
- [ ] Cada JRV tiene parroquia_id válida
- [ ] Cada parroquia tiene canton_id válida

### D) Verificar actas pueden referenciar JRVs

**Qué:**
- Test: Observer registra acta → jrv_id referencia a JRV seed
- Validar: Dashboard muestra resultados por JRV

**Criterio de aceptación:**
- [ ] `INSERT INTO acta (..., jrv_id, cargo_id, ...) VALUES (...);` funciona
- [ ] No FK constraint violations
- [ ] Dashboard/resultados ordena por JRV correctamente

### E) Documentar procedimiento manual (pre-elección)

**Qué:**
- Guide: "Cómo cargar JRVs reales del CNE antes de la elección"
- Pasos:
  1. Admin descarga CSV del CNE (provincias, cantones, parroquias, jrvs)
  2. Crea nueva migration 027-data-jrvs-<elección>-YYYY.sql
  3. Genera INSERTs con script o manualmente
  4. Ejecuta: `mvn liquibase:update`
  5. Verifica: `SELECT * FROM jrv;`

**Criterio de aceptación:**
- [ ] Procedimiento documentado en `/documents/OPERATIONS.md`
- [ ] Incluye template SQL para auto-generar INSERTs
- [ ] Menciona rollback

### F) Crear apply-progress.md template

**Qué:**
- Template vacío para Builder documentar progreso

**Criterio de aceptación:**
- [ ] `apply-progress.md` existe con estructura base
- [ ] Listo para Builder marcar [x]

---

## Parent SDD

None — standalone operational task.

---

## Notas de Implementación

**En esta fase NO hacer:**
- API endpoint `/admin/import-jrvs` (post-MVP, → SDD)
- CSV parser en backend (post-MVP)
- UI form upload (post-MVP)
- Cambiar tablas existentes (schema is locked)

**Qué SÍ hacer:**
- Liquibase migration (zero-risk, versioned)
- Documentation (operational knowledge)
- Test fixtures (verify FK integrity)

---

## Checklist Pre-Implementación

- [ ] Esta ODD revisada
- [ ] Documentos de referencia accesibles (01-arquitecto-qa.md, Liquibase conventions)
- [ ] Branches creadas: `brydyan/odd-import-jrvs`
- [ ] Listo para `/02-builder-odd odd/tasks/import-jrvs.md`

---

## apply-progress.md (Template para Builder)

```markdown
# Apply Progress — Import JRVs ODD

**Fecha Inicio:** [date]  
**Fecha Cierre:** [date]  
**Builder:** [name]

## Tareas Completadas

- [ ] A) Migration 026-data-jrvs-produccion.sql
- [ ] B) JRVS_IMPORT_GUIDE.md documentado
- [ ] C) Test data fixture (5+ JRVs)
- [ ] D) Actas referenciando JRVs ✅
- [ ] E) Procedimiento manual documentado
- [ ] F) apply-progress.md estructura

## Tests Ejecutados

```bash
# Backend
rtk mvn test -Dtest=JrvDataIT

# Verify
SELECT COUNT(*) FROM jrv;  -- must return >= 5
SELECT * FROM acta WHERE jrv_id = <test_jrv_id>;  -- must work
```

## Commits

- [x] commit 1: migration 026
- [x] commit 2: documentation
- [x] commit 3: test fixtures

## Blockers

- None (operational only)

## Próximos Pasos

→ Ready for /03-verify-odd
```

---

**Estado:** 📋 Structurado. Esperando: listo para invocar `/02-builder-odd` cuando des la orden.
