# 04: Ejecutar Tests — Paso a Paso

**Tiempo:** 1-2 horas (incluyendo ejecución)  
**Dificultad:** Fácil

---

## ✅ Pre-requisitos

Antes de empezar, verifica:

```bash
# 1. k6 instalado
k6 version
# Output: k6 vX.XX.X

# 2. Backend corriendo
curl http://localhost:8080/api/v1/health
# Output: {"status":"UP",...}

# 3. Test data en BD
psql -h localhost -U electoral_user -d electoral_db -c \
  "SELECT COUNT(*) FROM usuario WHERE email LIKE '%@electoral.ec';"
# Output: 8 (o más)
```

Si algo falla, volver a:
- k6 no instalado → `01-INSTALACION.md`
- Backend no corre → arrancarlo
- Test data falta → `02-SEED-DATOS.md`

---

## FASE 1: Test Baseline (5-10 minutos)

### Paso 1.1: Preparar terminal

```bash
cd /home/andy/Documentos/PROYECTOS/CONTROL/Control-s-Elector/frontend/tests/load

# Verificar que estamos en la carpeta correcta
pwd
# Output: .../frontend/tests/load

# Verificar archivos existen
ls -1
# Output: config.js common.js run.sh scenarios/ ...
```

### Paso 1.2: Ejecutar baseline test

```bash
# Opción A: Bash script (recomendado)
./run.sh baseline

# Opción B: k6 directo
k6 run scenarios/login.js --vus 10 --duration 5m
```

**Esperado (output en terminal):**
```
█ baseline
  execution: local
  script: scenarios/login.js
  output: json

  ✓ login status 200
  ✓ login has accessToken

  data_received...................: 45 kB
  data_sent........................: 12 kB
  http_req_duration...............: avg=125ms p(95)=142ms p(99)=198ms ✓
  http_req_failed.................: 0% ✓
  http_reqs........................: 50 reqs/sec
  iterations.......................: 50

✓ All checks passed!
```

### Paso 1.3: Verificar resultado

```bash
# Archivo de resultado guardado
ls -lh results/
# Output: baseline-20261105-142300.json

# Ver el contenido
cat results/baseline-*.json | jq '.metrics.http_req_duration'
```

### ✅ Baseline PASS Criteria

```
✓ p95 < 1.5s
✓ p99 < 2.0s
✓ Failures = 0%
✓ "All checks passed!"
```

**Si pasa:** Continuar a FASE 2  
**Si falla:** Ver troubleshooting abajo

---

## FASE 2: Test Pico Electoral (30-35 minutos)

### Paso 2.1: Preparar

```bash
# Estar en misma carpeta
cd frontend/tests/load

# Opcional: limpiar resultados anteriores
rm -f results/pico-*.json
```

### Paso 2.2: Ejecutar pico test (CRÍTICO)

```bash
# Opción A: Bash script (recomendado)
./run.sh pico

# Opción B: k6 directo
k6 run scenarios/mixed-realistic.js
```

**Esto va a tomar ~30 minutos. Aquí está qué pasará:**

```
█ Pico Electoral - Mixed Traffic
  execution: local
  script: scenarios/mixed-realistic.js
  
  0-2 min:   Ramp-up (0 → 20 VUs)
  2-12 min:  Ramp to pico (20 → 100 VUs)
  12-22 min: PEAK (100 → 150 VUs) ← Aquí es cuando todo se calienta
  22-27 min: Ramp-down (150 → 50 VUs)
  27-30 min: Cool-off (50 → 0 VUs)
```

**Monitor en terminal:**
- VU count va aumentando
- Requests por segundo suben
- Latencias probablemente suben también
- Al final: resumen de métricas

### Paso 2.3: Monitorear durante test

Mientras test corre (30 min), opcionalmente monitorear:

**En otra terminal:**

```bash
# Ver recursos del sistema
watch -n 1 'free -h && echo "---" && top -b -n 1 | head -20'

# Ver DB connections (si acceso a PostgreSQL)
psql -h localhost -U electoral_user -d electoral_db -c \
  "SELECT count(*) FROM pg_stat_activity;" | watch -n 1

# Ver logs del backend (si disponible)
tail -f logs/application.log
```

### Paso 2.4: Esperar a que termine

Cuando k6 finaliza, verás:

```
█ Pico Electoral
  ...
  
  http_req_duration...............: avg=2.3s p(95)=4.5s p(99)=8.8s
  http_req_failed.................: 8.0%
  http_reqs........................: 450 req/sec

✓ All SLAs passed (with warnings)
```

### ✅ Pico PASS Criteria

```
✓ p95 latency < 5.0s
✓ p99 latency < 8.0s
✓ Failures < 10%
✓ CPU not crashed
✓ No OOM errors
```

### ⚠️ Pico CAUTION Criteria (Aceptable pero riesgoso)

```
⚠️ p99 latency 7-8s (near limit)
⚠️ Failures 8-10% (high)
⚠️ CPU 75-85% (near saturation)
⚠️ But: system recovers after peak
```

### ❌ Pico FAIL Criteria (NO LISTO)

```
❌ p95 > 5s
❌ p99 > 8s
❌ Failures > 10%
❌ CPU 85%+
❌ Pool exhausted
```

---

## FASE 3: Guardar & Analizar Resultados (15-20 minutos)

### Paso 3.1: Guardar resultados

```bash
# El script ya guardó en results/pico-*.json
# Hacer backup (opcional)
cp results/pico-*.json ~/backup-pico-test-$(date +%Y%m%d-%H%M%S).json
```

### Paso 3.2: Leer JSON

```bash
# Ver p95/p99 latency
cat results/pico-*.json | jq '.metrics.http_req_duration'

# Ver failure rate
cat results/pico-*.json | jq '.metrics.http_req_failed'

# Ver endpoint específico (login)
cat results/pico-*.json | jq '.metrics | select(.http_req_duration.tags.endpoint=="login")'

# Full metrics
cat results/pico-*.json | jq '.metrics | keys'
```

### Paso 3.3: Interpretación (ver `05-INTERPRETAR-RESULTADOS.md`)

---

## FASE 4: Documentar Findings (10 minutos)

Crear documento con hallazgos:

```markdown
# Load Testing Results — 2026-11-05

## Baseline Test
- Status: ✅ PASS
- Duration: 5 min, 10 VUs
- p95 latency: 1.2s (< 1.5s target) ✓
- Failures: 0% ✓

## Pico Electoral Test  
- Status: ✅ PASS / ⚠️ CAUTION / ❌ FAIL
- Duration: 30 min, 0→150 VUs
- p95 latency: 4.5s (< 5s target) ✓
- p99 latency: 8.8s (> 8s target) ⚠️
- Failures: 8% (< 10% target) ✓
- CPU peak: 82% (near limit)
- DB pool: 24/25 (little headroom)

## Analysis
- Registrar Acta endpoint: p99 9.2s (slightly over SLA)
- Cause: Lock contention during peak
- Recommendation: Increase replicas, implement cache

## Decision
- ⚠️ CAUTION: Can go to production with 24/7 monitoring
- Action: Set up alertas, on-call team, rollback plan
```

---

## 🆘 Troubleshooting

### Test no inicia

```bash
# Error: "Connection refused"
# → Backend no corre
curl http://localhost:8080/api/v1/health
# Si falla, arrancar backend

# Error: "Login failed 401"
# → Test users no existen
psql -c "SELECT count(*) FROM usuario WHERE email LIKE '%@electoral.ec';"
# Si < 8, seed data (ver 02-SEED-DATOS.md)
```

### Test corre pero failure rate muy alta

```bash
# Posibles causas:
# 1. Backend overloaded (primera vez, normal)
# 2. Pool DB saturado
# 3. Query lenta

# Check backend logs
tail -f logs/application.log

# Check DB connections
psql -c "SELECT count(*) FROM pg_stat_activity;"

# Retry después de 5 min (dar tiempo a recuperarse)
./run.sh pico
```

### Latencias muy altas (p99 > 15s)

```bash
# Posibles causas:
# 1. Máquina insuficiente (< 4GB RAM)
# 2. Backend CPU maxed
# 3. DB lock contention
# 4. Network latency

# Check sistema
free -h           # RAM
top -bn1 | head   # CPU
ping localhost    # latency

# Si máquina es el problema, considerar:
# - Correr test en máquina más potente
# - Reducir VUs (./run.sh pico --vus 100)
# - Ver 06-FIXES-Y-TUNING.md
```

### Test terminó prematuramente

```bash
# Error: "Killed" o "Out of memory"
# → Sistema sin memoria

# k6 usa ~50MB per VU
# 150 VUs = ~7.5 GB RAM
# Check disponible:
free -h

# Si insuficiente:
# - Correr en máquina con más RAM
# - Reducir VUs (--vus 75)
# - Usar Docker limit memory
```

---

## 📋 Quick Recap

| Paso | Tiempo | Comando | Check |
|------|--------|---------|-------|
| **Baseline** | 5 min | `./run.sh baseline` | p95 < 1.5s ✓ |
| **Pico** | 30 min | `./run.sh pico` | p95 < 5s, p99 < 8s ✓ |
| **Analyze** | 15 min | Ver JSON | Documentar ✓ |

---

## ✅ Próximo Paso

Cuando tests terminen:

→ Ir a `05-INTERPRETAR-RESULTADOS.md` (entender qué significan las métricas)

---

**Last Updated:** 2026-11-05
