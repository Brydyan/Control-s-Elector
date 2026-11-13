# K6 Load Test — Complete Electoral Flow (Local/Staging)

**NO en CI/PR.** Solo local o staging antes de merge.

## Estructura

```
frontend/app/tests/load/
├── scenarios/
│   ├── login.js                    ← baseline (5 min, CI)
│   └── complete-electoral-flow.js  ← full (10 min, local/staging)
├── scripts/
│   └── run-local.sh                ← ejecutor
└── documents/pictures/
    ├── acta-sample-1.jpg           ← 250 KB
    ├── acta-sample-2.jpg           ← 280 KB
    └── acta-sample-3.jpg           ← 270 KB
```

## Fotos: Compresión Recomendada

**Tienes que llevar fotos reales a `documents/pictures/`**

```bash
# Verificar tamaño
ls -lh documents/pictures/acta-sample-*.jpg

# Si son > 1MB, comprimir
mogrify -resize 50% -quality 75 acta-sample-1.jpg  # ~250 KB
```

**Targets:**
- JPEG Q=75: 200-300 KB
- 280 fotos × 250 KB = ~70 MB

## Ejecutar Local

**Prerequisitos:**
1. Backend: `mvn quarkus:dev`
2. Frontend: `pnpm start`
3. K6: https://grafana.com/docs/k6/latest/get-started/installation/

**Ejecución:**

```bash
cd frontend/app/tests/load

# Con valores por defecto (20 VUs, 10 min)
./scripts/run-local.sh

# Custom: 14 OBSERVERs + 5 COORDs, 10 min
./scripts/run-local.sh --vus 20 --duration 10m

# API custom (staging)
./scripts/run-local.sh --api staging.internal:8080
```

## Timing Real

| VUs | Duration | Uploads | Validations | Wall-Clock |
|-----|----------|---------|-------------|------------|
| 20  | 10m      | 280     | 56 c/u      | 10-12 min  |
| 14  | 5m       | 280     | 56 c/u      | 5-6 min    |
| 5   | 2m       | 140     | 28 c/u      | 2-3 min    |

## Resultados

```bash
# Ver JSON
jq '.metrics.http_req_duration.values' results/*.json

# HTML report (si K6 generó)
k6 run ... --out csv=results/data.csv
```

## Diferencia vs Baseline

| Scenario | Datos | VUs | Duración | Objetivo |
|----------|-------|-----|----------|----------|
| **login.js** | Solo auth | 20 | 5 min | CI/PR: detectar regresos |
| **complete-electoral-flow.js** | Flujo completo + fotos reales | 20 | 10 min | Local: validar perf real |

## Workflow Recomendado

```bash
# 1. Dev local: iterar scenario (pequeño)
./scripts/run-local.sh --vus 5 --duration 2m

# 2. Pre-merge: full run local
./scripts/run-local.sh --vus 20 --duration 10m

# 3. Staging: validar en env real
./scripts/run-local.sh --api staging.internal:8080 --vus 20 --duration 10m

# 4. PR: solo CI baseline (login.js, 5 min, en cada PR)
# ← automático en .github/workflows/load-test-baseline.yml

# 5. Antes de merge a main: rerun completo staging
./scripts/run-local.sh --api staging.internal:8080 --duration 10m
```

## Notas

- ✅ No comprime 280 fotos en 5 min (apretado pero posible)
- ✅ 10 min es cómodo para análisis
- ✅ K6 paralleliza automático con VUs
- ⚠️ DB limpia después (seed de nuevo si necesitas rerun)
