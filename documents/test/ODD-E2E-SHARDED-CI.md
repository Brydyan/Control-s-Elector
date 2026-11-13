# ODD — E2E Sharded por Criticidad  
**Operational Design Document**

**Versión:** 1.0  
**Fecha:** 2026-11-13  
**Responsable:** QA / CI  
**Estado:** Diseño listo para implementar (después de Paso 1–2)

---

## 1. Visión

Partir la suite E2E en **4 shards paralelos** asignados por **criticidad electoral + UX**, evitando:
- 🔴 Flakes por DB compartida (409 false-positive)
- 🔴 Reintento enmascarando bugs de idempotencia
- 🔴 Demora de S4 (UI cosmética) bloqueando merge de S1 (defecto electoral)

---

## 2. Matriz de Criticidad

| Shard | Nombre | Tag | Ejemplos | Bloquea Merge | Budget | BD |
|---|---|---|---|---|---|---|
| **S1** | Guardián | `@guardian` | Registro real, checksum, 409 idempotencia, offline queue | ✅ **SÍ** | ≤4 min | `e2e_s1` |
| **S2** | Ciclo de Vida | `@ciclo-vida` | Disputa→corrección→aprobación→anulación, audit log, MASTER-only | ✅ **SÍ** | ≤4 min | `e2e_s2` |
| **S3** | Permisos | `@permisos` | Matriz RBAC en browser, RLS entre observers, 401/403 | ✅ **SÍ** | ≤3 min | `e2e_s3` |
| **S4** | Rebanada | `@rebanada` | Dashboard, invitaciones, legal, design, a11y | ❌ **NO** (nightly) | ≤4 min | `e2e_s4` |

**Lógica:**
- S1/S2/S3 = **defecto electoral** → merge-blocking
- S4 = **defecto UX** → nightly + `workflow_dispatch`

---

## 3. Diseño de CI Workflow

### 3.1 Job `e2e-s1-guardian` (required)

```yaml
e2e-s1-guardian:
  name: "E2E: S1 Guardián (registro, checksum, 409)"
  runs-on: ubuntu-latest
  timeout-minutes: 12  # setup (4 min) + tests (4 min) + buffer
  
  steps:
    - uses: actions/checkout@v4
    
    - name: "Setup: Postgres para S1"
      run: |
        docker run -d \
          --name e2e-s1-db \
          -e POSTGRES_PASSWORD=test \
          -e POSTGRES_DB=e2e_s1 \
          postgres:15
        sleep 3
        
    - name: "Setup: Backend + seed mínimo"
      run: |
        cd backend/electoral-api-quarkus
        docker build -t electoral-api:test .
        docker run -d \
          --name electoral-api-s1 \
          --link e2e-s1-db:postgres \
          -e DB_NAME=e2e_s1 \
          -p 8080:8080 \
          electoral-api:test
        sleep 6  # healthcheck
        
    - name: "Setup: Frontend (nginx + proxy_pass)"
      run: |
        cd frontend/app
        docker build -f Dockerfile -t electoral-frontend:test .
        docker run -d \
          --name electoral-frontend-s1 \
          --link electoral-api-s1:electoral-api \
          -p 4200:80 \
          electoral-frontend:test
        sleep 3
        
    - name: "Test: Playwright @guardian"
      run: |
        cd frontend/app
        npx playwright test \
          --grep '@guardian' \
          --project 'Desktop Chrome' \
          --reporter=html,junit \
          --output-dir=test-results-s1
      env:
        PLAYWRIGHT_JUNIT_OUTPUT_NAME: s1-results.xml
        
    - name: "Report: Upload results"
      if: always()
      uses: actions/upload-artifact@v3
      with:
        name: e2e-s1-results
        path: frontend/app/test-results-s1/
        
    - name: "Report: Publish JUnit"
      if: always()
      uses: dorny/test-reporter@v1
      with:
        name: "E2E S1 Results"
        path: frontend/app/test-results-s1/s1-results.xml
        reporter: java-junit
```

### 3.2 Job `e2e-s2-ciclo` (required)

Idéntico a S1 pero:
- Base: `e2e_s2` 
- Grep: `@ciclo-vida`
- Artifacts: `test-results-s2`

### 3.3 Job `e2e-s3-permisos` (required)

Idéntico a S1 pero:
- Base: `e2e_s3`
- Grep: `@permisos`
- Artifacts: `test-results-s3`

### 3.4 Job `e2e-s4-rebanada` (nightly only)

```yaml
e2e-s4-rebanada:
  name: "E2E: S4 Rebanada (UX, a11y) — Nightly"
  runs-on: ubuntu-latest
  if: |
    github.event_name == 'schedule' ||
    github.event_name == 'workflow_dispatch' ||
    github.ref == 'refs/heads/main'
  
  # ... mismo setup que S1/S2/S3, pero:
  # - Grep: @rebanada
  # - Timeout: 15 min (menos crítico, permite reintento)
```

### 3.5 Branch Protection Rule

```yaml
branch_protection:
  required_status_checks:
    - build
    - unit-tests
    - backend-contract
    - e2e-s1-guardian      # ← bloquea
    - e2e-s2-ciclo         # ← bloquea
    - e2e-s3-permisos      # ← bloquea
  required_checks:
    - e2e-s4-rebanada is NOT required
```

---

## 4. Estructura de Tags en Specs

### 4.1 Convención

```typescript
// S1: Guardián
@tag('guardian')
@tag('smoke')
@tag('no-intercept')  // cruza Angular → backend real
describe('j1-observer-happy-path', () => {
  test('Registro completo: BORRADOR → ENVIADA, checksum correcto', async ({ page }) => {
    // Sin mocks. Backend en vivo.
    await page.goto('/observer/dashboard');
    // ...
  });
});

// S2: Ciclo de vida
@tag('ciclo-vida')
@tag('disputa')
describe('j4-disputa-correccion', () => {
  // ...
});

// S3: Permisos
@tag('permisos')
@tag('rbac')
describe('j5-monitoreo-en-vivo', () => {
  // Solo MASTER ve dashboard
  // ...
});

// S4: Rebanada (nightly)
@tag('rebanada')
@tag('a11y')
describe('j8-design-system', () => {
  // ...
});
```

### 4.2 Grep en CI

```bash
# S1: todos @guardian
npx playwright test --grep '@guardian'

# S2: todos @ciclo-vida
npx playwright test --grep '@ciclo-vida'

# S3: todos @permisos
npx playwright test --grep '@permisos'

# S4: todos @rebanada
npx playwright test --grep '@rebanada'

# Local: solo smoke (rápido)
npx playwright test --grep '@smoke'
```

---

## 5. Wall-Clock Esperado

| Fase | Tiempo |
|---|---|
| Checkout + setup (Docker, DB, backend build) | 4–5 min (paralelo) |
| Frontend build + start | 2–3 min |
| **Cada shard tests** | 3–4 min |
| **Total paralelo (3 shards)** | ~6–8 min |
| **Total con S4 nightly** | nightly: ~15 min |

**Comparativa:**
- Hoy (sin E2E en CI): 3 min
- Con 3 shards: 8 min (aceptable, bloquea defectos electorales)
- Alternativa (1 big shard): 15 min (inaceptable)

---

## 6. Matriz de Transiciones

### 6.1 Tests por shard (propuesta inicial)

```
S1 @guardian (4 min)
├─ j1-observer-happy-path           (8 tests, no mock)
├─ j3-cuadre-estricto               (4 tests: checksum, 409)
└─ j2-offline-resilience            (2 tests: IndexedDB)

S2 @ciclo-vida (4 min)
├─ j4-disputa-correccion           (4 tests)
└─ journeys/j2-dispute              (6 tests)

S3 @permisos (3 min)
├─ j5-monitoreo-en-vivo             (4 tests: MASTER only)
├─ journeys/j3-coordinator-lifecycle (3 tests)
└─ observer-mis-actas.spec.ts       (24 tests: RLS + paginación)

S4 @rebanada (4 min, nightly)
├─ j6-legal-terms                   (3 tests)
├─ j7-auth-inv-02-user-invitations (3 tests)
├─ j8-design-system                 (2 tests)
└─ journeys/j4-admin, j5-master     (6 tests)
```

**Total: 66 tests distribuidos**

### 6.2 Reintento (retry policy)

```yaml
# S1/S2/S3: sin reintento (flaky = bug real)
retries: 0

# S4: 2 reintentos (menos crítico)
retries: 2
```

**Razón:** Si un test de S1 falla, es un bug de idempotencia. Reintentarlo = ocultarlo.

---

## 7. Casos de Uso

### UC-1: Dev local antes de push

```bash
# Smoke rápido (Σ 2 min)
pnpm run e2e:smoke

# Full (Σ 15 min) — antes de push a develop
pnpm run e2e:all
```

### UC-2: PR a develop (S1/S2/S3 requerido)

CI ejecuta 3 jobs en paralelo (8 min):
- ✅ merge permitido si todos verdes
- ❌ merge bloqueado si alguno falla

### UC-3: Merge a main (S4 también corre)

Nightly job ejecuta S4. Si falla:
- 📧 Notificación Slack (no bloquea)
- 📊 Dashboard con histórico

### UC-4: Hotfix electoral (out-of-band)

```bash
# Salta S4, corre solo S1/S2/S3
gh workflow run e2e-sharded.yml --inputs shards=s1,s2,s3
```

---

## 8. Dependency: Requisitos Previos

**ANTES de implementar CI, local DEBE estar verde:**

- [ ] **Paso 1:** `globalSetup.ts` + fixture cookies ✅ → 7 tests pasan
- [ ] **Paso 2:** Config único, sin conflictos ✅ → `playwright.config.ts` solo (no `e2e/...`)
- [ ] **Paso 3:** Mocks alineados con backend (`data: [], meta: {}`) ✅ → j1 sin NPE
- [ ] **Paso 4:** j1 corriendo sin mocks ✅ → 409 real validado

**Sin eso, CI = descubrir lo rojo, no evitarlo.**

---

## 9. Rollout (Fases)

### Fase 1: Local (Semana 1)
- Pasos 1–4
- E2E verde local contra backend vivo
- Tag structure definida

### Fase 2: CI (Semana 2)
- Push a `develop` branch protectada
- S1/S2/S3 required
- S4 nightly

### Fase 3: Main (Semana 3)
- Main branch también requiere S1/S2/S3
- S4 → on-demand + nightly

---

## 10. Métricas de Éxito

| KPI | Meta | Cómo medir |
|---|---|---|
| **Wall-clock CI** | <10 min | `github.run.duration` |
| **Flakiness (S1/S2/S3)** | 0% (100 runs) | Reintento rate |
| **Coverage E2E routes** | ≥60 endpoints | Trace de `--headed` |
| **Time-to-result (dev)** | <2 min (smoke) | Local `pnpm run e2e:smoke` |

---

## 11. Risks & Mitigations

| Risk | Impacto | Mitigación |
|---|---|---|
| DB compartida → 409 false-positive | 🔴 Shard flaky | Base por shard (`e2e_s1`, `e2e_s2`, etc.) |
| Reintento en S1 enmascara 409 | 🔴 Defecto no visto | `retries: 0` en required shards |
| Backend timeout en CI | 🟡 Test falla esporádico | Healthcheck + `wait-for-it.sh` |
| S4 nightly olvidado | 🟡 UX bugs no vistos | Dashboard + Slack notification |

---

## 12. Preguntas Respondidas

**P: ¿Por qué 4 shards y no 3?**
R: Criticidad. S4 es UX, no defecto electoral. Separarlo = no bloquea merge de seguridad.

**P: ¿Por qué sin mocks en S1?**
R: El invariante exige: *"nada de mocks en la ruta de registro de acta"*.  
Mocks validan el mock, no el sistema. S1 debe validar idempotencia HTTP real.

**P: ¿Qué pasa si un test de S1 falla en CI pero pasa local?**
R: Indica **falta de hermética**. Investigar:
- ¿Seed diferente en CI?
- ¿Race condition en paralelo?
- ¿Mock interceptado en CI pero no local?

**P: ¿Cómo evito que todo S1 falle si un test tiene bug?**
R: Cuarentena explícita: mover el test a `@quarantine`. No silenciar con retry.

---

## 13. Siguiente Paso

**Implementar Pasos 1–4 del plan de acción (en orden).**

1. ✅ H1 (contract) ya verde
2. 🟡 Paso 1: `globalSetup.ts` (bloqueante de cookies)
3. 🟡 Paso 2: Config único
4. 🟡 Paso 3: Mocks reales
5. 🟡 Paso 4: j1 sin mocks

**Una vez local esté verde, CI implementation es 30 min.**
