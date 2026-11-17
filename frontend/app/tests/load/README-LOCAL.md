# K6 Load Test — Complete Electoral Flow (Local/Staging)

**NO en CI/PR.** Solo local o staging antes de merge.

## Estructura

```
frontend/app/tests/load/
├── scenarios/
│   ├── login.js                    ← baseline (5 min, CI)
│   └── complete-electoral-flow.js  ← full (10 min, local/staging)
├── scripts/
│   └── run-local.sh                ← ejecutor (descubre fotos automático)
└── documents/pictures/
    ├── acta-sample-1.jpg           ← 250 KB (JPEG)
    ├── acta-sample-2.png           ← 500 KB (PNG)
    └── acta-sample-3.webp          ← 150 KB (WEBP high-compression)
```

## Fotos: Formatos y Compresión

**Formatos soportados:**
- `*.jpg`, `*.jpeg` — JPEG (cámara digital, más común)
- `*.png` — PNG (lossless, sin compresión)
- `*.webp` — WEBP (alta compresión, moderno)

**Llevar fotos reales a `documents/pictures/`**

```bash
# Script busca automáticamente:
acta-sample-*.jpg
acta-sample-*.jpeg
acta-sample-*.png
acta-sample-*.webp

# Verificar tamaño
ls -lh documents/pictures/acta-sample-*

# Si son > 1MB, comprimir según formato
# JPEG Q=75
convert acta-sample-1.jpg -quality 75 acta-sample-1.jpg  # ~250 KB

# WEBP (mejor compresión)
cwebp -q 75 acta-sample-1.jpg -o acta-sample-1.webp  # ~150 KB

# PNG (si prefieres lossless)
# PNG típicamente > 500 KB, más lento que JPEG/WEBP
```

**Targets recomendados:**
- JPEG Q=75: 200-300 KB
- WEBP Q=75: 100-200 KB
- PNG: 400-800 KB
- **280 fotos × 250 KB promedio = ~70 MB**

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

Con fotos reales (promedio 250 KB):

| VUs | Duration | Uploads | Validations | Wall-Clock |
|-----|----------|---------|-------------|------------|
| 20  | 10m      | 280     | 56 c/u      | 10-12 min  |
| 14  | 5m       | 280     | 56 c/u      | 5-6 min    |
| 5   | 2m       | 140     | 28 c/u      | 2-3 min    |

**Por formato (280 fotos):**
- JPEG (250 KB): 10-12 min
- WEBP (150 KB): 7-9 min ⭐ recomendado
- PNG (500 KB): 15-18 min (más lento)

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
