# 02 — Builder SDD — Implementar Software Design Document

**Skill:** `/02-build-sdd <sdd-path>`

**Función:** Implementar una SDD completa siguiendo fases Authorize → Explore → Architect → Build.

**Subagente:** `sdd-apply` (sonnet, contexto limpio para cada fase).

---

## Flujo SDD (Architect → Builder → Verify → Archive)

```
┌─────────────────────────────────────────────────────────────┐
│ 1. ARCHITECT (tú + Claude)                                  │
│    - proposal.md: qué, por qué, out-of-scope               │
│    - design.md: decisiones D1, D2… con alternativas         │
│    - specs/*.md: requisitos GIVEN/WHEN/THEN                 │
│    - tasks.md: contratos ejecutables + mutaciones           │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│ 2. BUILDER (Agent sdd-apply, tú lanzas skills)             │
│    - Authorize: leer proposal, design, tasks               │
│    - Explore: analizar código existente, patrones          │
│    - Resolve: aclarar uncertainties                        │
│    - Implement: tasks A→B→C, tests, mutaciones             │
│    - Close: apply-progress.md con números medidos          │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│ 3. VERIFY (Agent verify-sdd, usuario lanza skill)          │
│    - Auditar tasks completadas vs. declaradas              │
│    - Ejecutar mutaciones                                   │
│    - Validar compuertas (lint, tests, build)               │
│    - Reportar hallazgos en fixes-required.md               │
└─────────────────────────────────────────────────────────────┘
                          ↓ (si PASS)
┌─────────────────────────────────────────────────────────────┐
│ 4. ARCHIVE (Arquitecto, vía git)                            │
│    - git mv openspec/changes/* openspec/archive/           │
│    - Fusionar specs en openspec/specs/<domain>/            │
│    - Actualizar ROADMAP.md                                 │
└─────────────────────────────────────────────────────────────┘
```

---

## Cuándo Lanzar `/02-build-sdd`

1. **Arquitecto cerró design.md** — decisiones D1-D5 documentadas con alternativas
2. **tasks.md está completo** — A-E tasks con mutaciones y compuertas
3. **specs/*.md existen** — requisitos en GIVEN/WHEN/THEN

**NO lanzar** si:
- design.md solo tiene ideas, no decisiones
- tasks.md tiene tareas vagas ("refactor", "clean up")
- specs/ está vacío

---

## Plantilla de Lanzamiento

```
Implementá la SDD openspec/changes/<área>/<fecha>-<slug>/.

ANTES de tocar código, leé:
  1. openspec/changes/<área>/<fecha>-<slug>/proposal.md
  2. openspec/changes/<área>/<fecha>-<slug>/design.md
  3. openspec/changes/<área>/<fecha>-<slug>/specs/*/spec.md
  4. openspec/changes/<área>/<fecha>-<slug>/tasks.md
     ↑ Este es tu contrato

## FASES

### Authorize — Comprende qué, por qué y cómo verificar
  ✓ ¿Cuál es el cambio arquitectónico? (D1, D2, …)
  ✓ ¿Cuál es la métrica de éxito? (tests, compuertas, mutaciones)
  ✓ ¿Qué herramientas cambian? (BD, API, storage)

### Explore — Estructura existente
  ✓ Buscar patrones similares en código
  ✓ Identificar archivos que se tocarán (A.1 → qué archivo)
  ✓ Tests existentes: baseline antes de cambios

### Resolve — Incertidumbres ANTES de escribir
  Si vos no sabés algo, NO agarres a escribir. Preguntame.
  ✓ ¿TDD o standard?
  ✓ ¿Refactor primero o build sobre lo viejo?
  ✓ ¿Qué test corre primero?

### Implement — Fase por fase (A, B, C, D, E)
  Marca `- [x]` en tasks.md mientras cierras cada una.
  - Tests: nombre exacto + resultado (PASS/FAIL)
  - Mutaciones: qué quitaste, qué test cayó
  - Commits: ~400 líneas por commit, message claro

### Close — apply-progress.md con NÚMEROS MEDIDOS
  - Suites/tests/errors antes vs. después (de XML, no de log)
  - Mutación: test name que cayó (no "some test failed")
  - Compuerta: comando exacto + resultado
  - Bloqueadores: qué no cerraste y por qué

## ESTÁNDARES DEL PROYECTO
[pegar de README.md § Estándares]

## TRAMPAS VERIFICADAS
[pegar de README.md § Trampas]

## REGLAS DE MUTACIÓN
[pegar de README.md § Reglas de mutación]

## SALIDA

1. **apply-progress.md** con:
   - Qué tareas cerraste (✅) y cuáles no (⏳)
   - Números medidos (tests, errors, failures)
   - Mutación: por cada una, el test exacto que cayó
   - Compuerta: comandos y outputs

2. **tasks.md actualizado**: `- [ ]` → `- [x]` mientras implementas

3. **Git history limpio**: ~400 líneas por commit, sin archivos extra

NO hagas:
  ❌ archives de código (apply-progress.md o verify-report.md)
  ❌ Decidir si tu trabajo está bien
  ❌ Commitear ni pushear
  ❌ Escribir verify-report.md (lo hace Verify)
  ❌ Auto-archivar (git mv a archive/) — lo hace Arquitecto
```

---

## Métodos Obligatorios — SDD vs ODD

| Fase | SDD | ODD |
|------|-----|-----|
| **Authorize** | Leer proposal, design, specs | (no aplica) |
| **Explore** | Código existente + patrones | (no aplica) |
| **Resolve** | ¿TDD? ¿Refactor? | (no aplica) |
| **Classify** | Tasks en A-F + mutaciones | Tasks en A-E sin mutaciones |
| **Implement** | Task by task con tests + mutaciones | Task by task |
| **Close** | apply-progress.md + números medidos | apply-progress.md |
| **Verify** | Formal: mutation testing + compuertas | Validación de tasks |

---

## Ejemplo: Tasks SDD

```markdown
### A · Crear tabla `partido`

- [ ] **A.1** — Migración Liquibase: `CREATE TABLE partido (id, nombre, sigla)`
  Mutación: quitar `ALTER TABLE rol ADD CONSTRAINT fk_partido_id`. Test debe fallar.

- [ ] **A.2** — Entidad JPA: `@Entity class Partido { id, nombre, sigla }`
  Test: `PartidoRepositoryTest.findById()` pasa.

- [ ] **A.3** — Endpoint GET: `GET /api/v1/configuracion/partidos`
  Test: request sin auth → 401. Con auth MASTER → 200, 3 partidos.

### B · Relación JRV → Partido

- [ ] **B.1** — Migración: `ALTER TABLE jrv ADD COLUMN partido_id UUID FOREIGN KEY`
  Mutación: quitar el FOREIGN KEY. Test de integridad referencial debe fallar.
```

---

## Ejecutar Mutaciones en SDD

```bash
# Mutar: quitar tabla
git stash
edit 001-schema.sql # quitar CREATE TABLE partido
mvn -B test -Dtest=PartidoTest
# Esperado: test cae (ej. "ERROR: relation \"partido\" does not exist")
git checkout 001-schema.sql
mvn -B test -Dtest=PartidoTest
# Esperado: test pasa

# Resultado en apply-progress.md:
# Mutación M1: CRITICAL — Quitar CREATE TABLE partido
#   Test: PartidoRepositoryTest.findById
#   Resultado: ✅ Test cayó (relation does not exist)
```

---

## Entrega: apply-progress.md

```markdown
# apply-progress.md

## Baseline (Authorize)
Backend tests: 213 suites / 4,521 tests / 0 error / 2 failure / 0 skip

## Tasks Completadas

### A — Crear tabla `partido`

- [x] **A.1** — Migración Liquibase
  - Comando: `mvn -B test -Dtest=MigracionesSchemaTest`
  - Resultado: ✅ PASS
  
- [x] **A.2** — Entidad JPA
  - Tests: `PartidoRepositoryTest.findById`, `PartidoRepositoryTest.save`
  - Resultado: ✅ PASS (2 tests new)

- [x] **A.3** — Endpoint GET
  - Test: `PartidoControllerTest.getPartidos_WithMasterRole`
  - Resultado: ✅ PASS

### B — Relación JRV → Partido

- [x] **B.1** — Migración ALTER TABLE
  - Mutación M1: quitar FOREIGN KEY
  - Test: `JrvPartidoConstraintTest.insertWithoutPartido_ShouldFail`
  - Resultado: ✅ Test cayó (FK violation)
  - Restaurado: git checkout

## Números Medidos (Final)

Backend tests: 213 suites / 4,523 tests / 0 error / 2 failure / 0 skip
  Δ: +2 tests (A.2, A.3)

Frontend: lint ✅, format ✅, test ✅ (0 failing)

## Hallazgos

(Solo si encontraste bugs fuera de scope)
```

---

## Qué NO Hacer en SDD Builder

| ❌ | Razón |
|----|-------|
| Escribir verify-report.md | Lo hace Verify, no tú |
| Auto-archivar (git mv) | Lo decide Arquitecto después de Verify PASS |
| Commitear/pushear | Decisión de Arquitecto |
| Tapar tests que no caen | Si mutación no causa test fail, la garantía no existe — rehace |
| Refactor fuera de scope | Si bloquea, declará. Sino, anótalo bajo "Hallazgos" |

---

## Diferencia Clave: SDD vs ODD

**SDD:** "¿Está todo lo propuesto implementado y probado con exactitud?"
- Mutaciones SIEMPRE
- Compuertas formales
- apply-progress.md detallado
- Verify es FORMAL

**ODD:** "¿Se completaron las tasks y funcionan?"
- Mutaciones OPCIONALES (si task introduce garantía)
- Compuertas básicas (lint, tests)
- apply-progress.md simplificado
- Verify es VALIDACIÓN

