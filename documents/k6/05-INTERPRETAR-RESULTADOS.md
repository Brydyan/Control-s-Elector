# 05: Interpretar Resultados

**Tiempo:** 20-30 minutos  
**Dificultad:** Medio

---

## Anatomía de un Resultado k6

```json
{
  "metrics": {
    "http_req_duration": {
      "values": {
        "avg": 2300,           // promedio en ms
        "med": 2100,           // mediana
        "min": 850,
        "max": 15200,
        "p(90)": 3500,         // percentil 90
        "p(95)": 4500,         // percentil 95 ← CRÍTICO
        "p(99)": 8800          // percentil 99 ← CRÍTICO
      }
    },
    "http_req_failed": {
      "values": {
        "rate": 0.08          // 8% de requests fallieron
      }
    },
    "http_reqs": {
      "values": {
        "count": 18000,        // Total de requests
        "rate": 450.5          // por segundo promedio
      }
    }
  }
}
```

---

## Métricas Clave

### 1. Latency (Duración de Requests)

**p95: 4.5s**
> 95% de todos los requests completaron en 4.5 segundos o menos

**p99: 8.8s**
> 99% de todos los requests completaron en 8.8 segundos o menos

**Interpretation:**
```
p95 = "typical user experience"
p99 = "worst 1% experience"

Target SLA:
  p95 < 5s ← Si esto falla, problema visible para mayoría
  p99 < 8s ← Si esto falla, algunos usuarios esperan mucho
```

### 2. Failure Rate

**8%**
> 8 de cada 100 requests fallaron (5xx errors, timeouts, etc.)

**Target SLA:**
```
< 5%   = Acceptable
5-10%  = Caution (high, but tolerable in spike)
> 10%  = Failure (unacceptable)
```

### 3. Throughput

**450 req/sec**
> En pico, el sistema procesó 450 requests por segundo

**Interpretation:**
```
Con 150 VUs:
  450 req/sec ÷ 150 VUs = 3 req/VU promedio en test
  
Significa: cada usuario hizo ~3 requests en promedio
```

### 4. Error Count

**HTTP Status Codes:**
```
200 OK          = 14000 requests (success)
201 Created     = 2500 requests (success)
401 Unauthorized = 600 requests (auth failure)
503 Service Unavailable = 1200 requests (overload)
504 Gateway Timeout = 700 requests (timeout)
```

---

## Leer JSON en Terminal

### Simple: Latency

```bash
cat results/pico-*.json | jq '.metrics.http_req_duration'
```

Output:
```
{
  "avg": 2300,
  "med": 2100,
  "min": 850,
  "max": 15200,
  "p(95)": 4500,
  "p(99)": 8800
}
```

### Simple: Failures

```bash
cat results/pico-*.json | jq '.metrics.http_req_failed'
```

Output:
```
{
  "rate": 0.08
}
```

### By Endpoint

```bash
# Ver métricas separadas por endpoint
cat results/pico-*.json | jq '.metrics | with_entries(select(.key | contains("endpoint:")))'
```

Output:
```
{
  "key": "http_req_duration{endpoint:registrar-acta}",
  "value": {
    "avg": 2800,
    "p(95)": 5200,
    "p(99)": 9100
  }
},
{
  "key": "http_req_duration{endpoint:login}",
  "value": {
    "avg": 800,
    "p(95)": 1200,
    "p(99)": 1500
  }
}
```

---

## Comparar vs SLAs

| Endpoint | Target p95 | Test p95 | Status |
|----------|-----------|---------|--------|
| Login | < 1.5s | 1.2s | ✅ |
| Registrar Acta | < 5s | 5.2s | ⚠️ |
| Listar Actas | < 3s | 2.8s | ✅ |
| Dashboard | < 3s | 2.5s | ✅ |

| Endpoint | Target Failures | Test Failures | Status |
|----------|-----------------|---------------|--------|
| Login | < 2% | 1% | ✅ |
| Registrar Acta | < 5% | 7% | ⚠️ |
| Listar Actas | < 5% | 4% | ✅ |
| Dashboard | < 5% | 2% | ✅ |

---

## Veredictos

### ✅ PASS — Pico Test Completado Exitosamente

**Criterios:**
```
✓ p95 latency < 5s
✓ p99 latency < 8s
✓ Failure rate < 10%
✓ CPU < 80%
✓ No cascading failures
```

**Ejemplo de PASS:**
```
Baseline:
  p95: 1.2s ✓
  p99: 1.8s ✓
  failures: 0% ✓

Pico:
  p95: 4.5s ✓
  p99: 8.2s ✓
  failures: 8% ✓
  CPU: 75% ✓

Decision: ✅ GO TO PRODUCTION with confidence
```

---

### ⚠️ CAUTION — Aceptable pero Riesgoso

**Síntomas:**
```
⚠️ p95 4.8s (near limit)
⚠️ p99 8.0s (AT limit, not over)
⚠️ failures 9% (high but acceptable)
⚠️ CPU 82% (near capacity)
⚠️ But: system recovers, no cascading failures
```

**Ejemplo de CAUTION:**
```
Baseline: ✓ PASS

Pico:
  p95: 4.8s ⚠️ (near limit, not over)
  p99: 8.0s ⚠️ (exactly at SLA, no margin)
  failures: 9% ⚠️ (high)
  CPU: 82% ⚠️ (near saturation)

Pero después de pico, sistema se recupera:
  - Latencies vuelven a normal
  - Error rate baja
  - No cascading failures

Decision: ⚠️ GO TO PRODUCTION CON RIESGOS
  - Requirement: 24/7 monitoreo
  - Requirement: on-call team ready
  - Requirement: rollback plan documentado
```

---

### ❌ FAIL — No Listo para Producción

**Síntomas:**
```
❌ p95 > 5s (over limit)
❌ p99 > 8s (significantly over)
❌ failures > 10% (unacceptable)
❌ CPU > 85% (saturated)
❌ Cascading failures (no recovery)
```

**Ejemplo de FAIL:**
```
Pico:
  p95: 6.2s ❌ (significantly over 5s)
  p99: 10.5s ❌ (significantly over 8s)
  failures: 15% ❌ (unacceptable)
  CPU: 88% ❌ (saturated)
  After pico: system still degraded, no recovery

Decision: ❌ NO GO TO PRODUCTION
  Action: Fix infrastructure, retest
```

---

## Análisis Detallado

### Registrar Acta Lenta

**Síntoma:** p99: 9.5s (over 8s SLA)

**Posibles Causas:**
```
1. Lock Contention
   → Múltiples observadores registrando simultáneamente
   → Locks pesimistas en tabla acta
   → Solution: Caché + batch updates

2. Query Lenta
   → SELECT * FROM acta WHERE ... tarda mucho
   → Solution: Add index, optimize query

3. Validación Lenta
   → Validar candidatos/JRV/etc. tarda
   → Solution: Caché candidatos/JRV

4. Pool Exhaustion
   → Pool de conexiones agotado
   → Solution: Increase max-size
```

**Cómo debuggear:**
```bash
# Ver query más lenta en logs
grep "duration=" logs/application.log | sort -t= -k3 -rn | head -5

# Ver locks en DB
SELECT * FROM pg_locks;

# Ver conexiones activas
SELECT count(*) FROM pg_stat_activity;
```

---

### Failure Rate Alto

**Síntoma:** 12% failures en Registrar Acta

**Posibles errores:**
```
503 Service Unavailable (40% de failures)
  → Backend pod restarted
  → CPU maxed, rejecting requests
  → Solution: Increase replicas, add HPA

504 Gateway Timeout (35% of failures)
  → DB connection timeout
  → Query took > timeout (default 30s)
  → Solution: Tune timeout, fix slow query

401 Unauthorized (15% of failures)
  → Token expired mid-test
  → Session issue
  → Solution: Refresh token logic

500 Internal Server Error (10% of failures)
  → Exception en código
  → DB error
  → Solution: Check logs, fix bug
```

**Cómo debuggear:**
```bash
# Ver errors por tipo
cat results/pico-*.json | jq '.metrics | keys | map(select(contains("failed")))'

# Ver error messages
grep ERROR logs/application.log | tail -100
```

---

### CPU Near Saturation

**Síntoma:** CPU 85% during peak

**Significa:**
```
Each additional request will:
  - Queue instead of execute immediately
  - Experience higher latency
  - May timeout if queue too long
```

**Solution:**
```
1. Horizontal Pod Autoscaling (HPA)
   → Add more replicas automatically when CPU > 70%

2. Optimize Code
   → Profile to find CPU hot spots
   → Reduce unnecessary operations

3. Cache
   → Cache expensive computations
   → Reduce query load
```

---

## Red Flags

| Signal | Severity | Action |
|--------|----------|--------|
| p95 > 5s | 🔴 Critical | DO NOT GO TO PRODUCTION |
| p99 > 8s | 🔴 Critical | DO NOT GO TO PRODUCTION |
| failures > 10% | 🔴 Critical | DO NOT GO TO PRODUCTION |
| CPU > 85% | 🟠 High | Add HPA / replicas |
| p99 7-8s | 🟡 Medium | Go with caution + monitoring |
| failures 8-10% | 🟡 Medium | Go with caution + monitoring |
| Cascading failures | 🔴 Critical | DO NOT GO TO PRODUCTION |
| No recovery after peak | 🔴 Critical | DO NOT GO TO PRODUCTION |

---

## Ejemplo: Análisis Completo

```
Test Results Summary:

BASELINE (10 VUs, 5 min):
  p95 latency:  1.2s ✓ (target < 1.5s)
  p99 latency:  1.8s ✓ (target < 2.0s)
  failures:     0% ✓ (target < 2%)
  Status:       ✅ PASS

PICO (0→150 VUs, 30 min):
  p95 latency:  4.8s ⚠️ (target < 5.0s, but very close)
  p99 latency:  8.1s ❌ (target < 8.0s, slightly over)
  failures:     9% ⚠️ (target < 10%, but high)
  CPU peak:     82% ⚠️ (near saturation)
  DB pool:      24/25 ⚠️ (little headroom)
  Recovery:     ✓ System recovers after peak

BREAKDOWN BY ENDPOINT:
  Login:          p95 1.1s ✓, failures 1% ✓
  Registrar Acta: p95 5.2s ⚠️, failures 12% ❌
  Listar Actas:   p95 2.8s ✓, failures 4% ✓
  Dashboard:      p95 2.5s ✓, failures 2% ✓

ROOT CAUSE ANALYSIS:
  - Registrar Acta (5 BD queries with locks) is bottleneck
  - Lock contention when 40+ observadores register actas simultaneously
  - DB pool nearly exhausted (24/25 conexiones)
  
RECOMMENDATIONS:
  1. Increase DB pool: max-size 25 → 35
  2. Add replicas: 2 → 3 pods (reduce per-pod CPU)
  3. Implement Redis cache for JRV/candidatos lookup
  4. Add HPA: scale to 4-5 pods when CPU > 70%

DECISION:
  ⚠️ CAUTION: Can go to production with mitigations
  - If fixes implemented first → Change to ✅ PASS
  - If going without fixes → Requires 24/7 monitoring
```

---

## ✅ Próximo Paso

Basado en resultado:

**Si ✅ PASS:**
→ `07-CHECKLIST-GO-LIVE.md` (final pre-producción checks)

**Si ⚠️ CAUTION:**
→ `06-FIXES-Y-TUNING.md` (implementar mejoras, retest)

**Si ❌ FAIL:**
→ `06-FIXES-Y-TUNING.md` (fixes urgentes requeridos)

---

**Last Updated:** 2026-11-05
