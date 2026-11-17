# K6 Load Testing en CI — GitHub Actions Integration

**Date:** 2026-11-05  
**Status:** Integrated en `.github/workflows/ci.yml`  
**VUs en CI:** 20 (Baseline)  
**Duración:** 5 minutos

---

## 📋 Qué se ejecuta en cada PR

Cuando haces push a una rama con prefijo `feat/`, `fix/`, etc., GitHub Actions corre automáticamente:

```yaml
Job: load-test-baseline
├─ Services: PostgreSQL 16, RustFS (S3 mock)
├─ Backend: Docker image (electoral-api:load-test)
├─ K6: 20 VUs × 5 minutos
└─ SLAs: p95 < 1.5s, p99 < 2s, failures < 2%
```

**Tiempo total:** ~12-15 minutos (después que backend + BD levanten)

---

## 🎯 Flujo de Ejecución

```
1. Build Backend Image (2-3 min)
   ↓
2. Start Services (PostgreSQL, RustFS, Backend)
   ↓
3. Wait for Backend Health (up to 3 min)
   ↓
4. Seed Test Data (30 sec)
   ├─ 5 observers
   ├─ 2 coordinators
   └─ 1 admin
   ↓
5. Run K6 Baseline (5 min)
   ├─ Login calls: 20 VUs
   └─ Output: JSON results
   ↓
6. Validate SLAs
   ├─ p95 must be < 1500ms ✓
   ├─ p99 must be < 2000ms ✓
   └─ Failures must be < 2% ✓
   ↓
7. Upload Results as Artifact (30 day retention)
```

---

## 📊 Métricas Monitoreadas

| Métrica | Threshold | Failure Severity |
|---------|-----------|------------------|
| **p95 latency** | < 1500ms | ❌ Blocks PR |
| **p99 latency** | < 2000ms | ❌ Blocks PR |
| **Failure Rate** | < 2% | ❌ Blocks PR |

Si **cualquiera falla**: la PR no puede mergearse.

---

## 🔴 Por qué 20 VUs en lugar de 10

| Aspecto | 10 VUs | 20 VUs | Razón |
|--------|--------|--------|-------|
| **Detecta regresión** | Débil | ✓ Fuerte | 2x carga = 2x probabilidad de encontrar bugs |
| **Tiempo CI** | ~10 min | ~12 min | +2 min tolerable, detecta issues |
| **Realismo** | Muy bajo | Bajo-moderado | Más cercano a realidad (100+ en pico) |
| **Cost** | $0.005 | $0.008 | Negligible en GitHub Actions free tier |

**Decisión:** 20 VUs = sweet spot entre cobertura y tiempo de CI.

---

## 🚀 Cómo Funciona en Práctica

### Ejemplo 1: PR que PASA

```
PR #123 opened: feat/user-invitations
├─ Backend builds ✅
├─ Backend starts ✅
├─ K6 runs (20 VUs) ✅
│  └─ p95: 1200ms, p99: 1850ms, failures: 0%
├─ SLAs validated ✅
└─ PR reviewable ✅
```

**Tiempo total:** ~13 minutos  
**Result:** ✅ Load test passed — PR can be merged

---

### Ejemplo 2: PR que FALLA (regresión de performance)

```
PR #124 opened: feat/slow-db-query
├─ Backend builds ✅
├─ Backend starts ✅
├─ K6 runs (20 VUs) ✅
│  └─ p95: 2100ms ❌ (exceeds 1500ms threshold)
├─ SLAs validated ❌
└─ PR blocked ❌

GitHub shows:
  ❌ load-test-baseline — Load test failed
     p95 exceeds 1500ms threshold (2100ms > 1500ms)

Developer action:
  → Optimize the slow query
  → Push fix
  → CI re-runs automatically
  → K6 baseline passes ✅
  → PR ready for merge
```

---

## 📈 Datos de Baseline

Valores históricos esperados (login endpoint):

```
Normal system (p95):     1000-1200ms
Under load (p95):        1200-1400ms
Degraded (p95):          1500-2000ms
Critical (p95):          > 2000ms ❌
```

Nuestro threshold (p95 < 1500ms) captura casi toda regresión significativa.

---

## 🔧 Configuración en CI

Ubicación: `.github/workflows/ci.yml`

### Job: load-test-baseline

```yaml
load-test-baseline:
  name: Load Test Baseline (20 VUs)
  runs-on: ubuntu-latest
  services:
    postgres: { ... }
    rustfs: { ... }
  steps:
    - Build backend
    - Seed data
    - Run k6 (20 VUs, 5 min)
    - Validate SLAs
    - Upload results
```

### Variables de Entorno

```bash
K6_VUS=20              # Virtual users
K6_DURATION=5m         # Test duration
API_HOST=localhost:8080  # Backend URL
```

---

## 📁 Archivos Involucrados

| Archivo | Propósito |
|---------|-----------|
| `.github/workflows/ci.yml` | Workflow definition (includes load-test job) |
| `frontend/tests/load/config.js` | K6 configuration (VUs, SLAs, scenarios) |
| `frontend/tests/load/scenarios/login.js` | Baseline scenario (login endpoint) |
| `frontend/tests/load/run.sh` | Manual k6 runner (for local development) |

---

## 🛑 Cuando el Load Test Falla

### Step 1: Review the Error

```
❌ Load test FAILED: p95 exceeds 1500ms (found 2100ms)
```

### Step 2: Run Locally (if needed)

```bash
cd frontend/tests/load

# Start backend locally
docker-compose up -d

# Seed data
psql -h localhost -U electoral_user -d electoral_db -f seed.sql

# Run same test as CI
./run.sh baseline
# or manually:
k6 run scenarios/login.js --vus 20 --duration 5m
```

### Step 3: Identify Root Cause

Common causes:

- **Slow query**: Check `SELECT COUNT(*) FROM usuario` query plan
- **Connection pool exhausted**: Tune `max.pool.size` in application.properties
- **CPU at 100%**: Too many VUs for runner, scale back (unlikely at 20 VUs)
- **Network latency**: Check docker networking

### Step 4: Fix

Fix the issue (optimize query, tune pool, etc.) and push — CI re-runs automatically.

---

## 📊 Accessing Results

### Via GitHub Actions UI

1. Open PR
2. Click "Checks" tab
3. Find "Load Test Baseline" job
4. Click "Artifacts" section
5. Download `k6-baseline-results-*.zip`

### Via CLI

```bash
# List artifacts for a run
gh run list --limit 10

# Download results
gh run download <run-id> --name k6-baseline-results-*
```

### Analyze Results

```bash
# Parse JSON results
jq '.metrics.http_req_duration.values' results/baseline-*.json

# Pretty print
cat results/baseline-*.json | jq '.'
```

---

## ⚡ Performance Tips

### If K6 Test Takes Too Long

- K6 itself is fast (~1-2 min)
- Slowness is usually: backend startup + DB + service health checks
- Expected total time: 12-15 minutes in GitHub Actions

### If You Want Faster CI

Option: Run load test only on `main` and nightly (not on every PR)

```yaml
# Current: runs on every PR
on:
  pull_request:
    branches: [main, develop, 'feat/**', ...]

# Alternative: only on specific branches
on:
  pull_request:
    branches: [main, develop]  # Skip feature branches
  schedule:
    - cron: '0 2 * * *'  # Nightly test
```

---

## 📋 Checklist: Before Deploying

Before going to production, run **extended tests locally**:

```bash
# 1. Baseline (5 min, 20 VUs)
./run.sh baseline
# Expect: p95 < 1.5s, failures 0%

# 2. Pico (30 min, 0→150 VUs) — THE REAL TEST
./run.sh pico
# Expect: p95 < 5s, p99 < 8s, failures < 10%

# 3. Stress (30 min, 200 VUs sustained) — Find breaking point
./run.sh stress
# Goal: Identify where system degrades
```

**CI tests only login endpoint with 20 VUs** — sufficient for PR validation, but **not sufficient for production readiness**.

---

## 🎯 Next Steps

1. ✅ **Done:** K6 integrated in CI (baseline 20 VUs)
2. **Next:** Run baseline on your local machine to verify it works
3. **Then:** Create a nightly job for pico test (0→150 VUs) if desired
4. **Before Prod:** Run full pico + stress tests manually

---

**Status:** Ready  
**Last Updated:** 2026-11-05

---

## 📞 Troubleshooting

**Q: K6 command not found in CI**  
A: CI installs k6 via `sudo apt-get install -y k6`. If fails, use Docker: `K6_DOCKER=true ./run.sh baseline`

**Q: Backend doesn't start in CI**  
A: Check logs: `docker logs electoral-api`. Usually: wrong env vars or DB not ready.

**Q: Test data not seeded**  
A: Verify PostgreSQL is healthy and SQL file is correct. Run manually: `psql -h localhost -U electoral_user -d electoral_db -f /tmp/seed/seed.sql`

**Q: Can I skip load test on my PR?**  
A: No — it's a required check. But you can run locally first to debug before pushing.

---

**Questions?** Check `documents/k6/README.md` for full load testing guide.
