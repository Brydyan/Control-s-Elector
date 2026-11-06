# CRÍTICO: Checklist Pre-Producción

**Leer esto PRIMERO antes de cualquier otra cosa**

---

## 🚨 MUST READ

**Esto es un sistema electoral. NO es un CMS. NO es un carrito de compras.**

**Contexto:**
- 500 JRVs en Santa Elena, Ecuador
- ~200 observadores simultáneos en pico
- Votación: 10 horas (07:00-17:00)
- **PICO: 80% de actas llegan en últimas 3 horas (17:00-20:00)**
- **30-40 actas/min en pico (vs ~3 en operación normal)**

**Qué pasó en otros sistemas electorales sin load testing:**
- 2019 Bolivia: Sistema colapsó, votación se extendió 3 días
- 2020 USA: Algunos estados alcanzaron limites, largos delays
- 2022 Brasil: Traffic spike causó downtime de 2 horas

**Tú tienes un plan:** Load testing. Úsalo.

---

## 🔴 ANTES DE COMENZAR — VERIFICACIONES INICIALES

### 1️⃣ Backend Funciona?

```bash
curl http://localhost:8080/api/v1/health
```

**Respuesta esperada (200 OK):**
```json
{"status":"UP","checks":[...]}
```

**Si falla:**
```bash
cd backend/electoral-api-quarkus
./mvnw quarkus:dev
# Esperar: "Listening on: http://0.0.0.0:8080"
```

---

### 2️⃣ Base de Datos Funciona?

```bash
psql -h localhost -U electoral_user -d electoral_db -c "SELECT count(*) FROM usuario;"
```

**Respuesta esperada:**
```
 count 
-------
    10+
(1 fila)
```

**Si falla:** setup BD primero, ver `02-SEED-DATOS.md`

---

### 3️⃣ k6 Instalado?

```bash
k6 version
```

**Respuesta esperada:**
```
k6 v0.XX.X (linux/amd64)
```

**Si falla:** instalar, ver `01-INSTALACION.md`

---

## 📊 LO QUE VAS A MEDIR

### Escenario 1: Baseline (Test Sencillo)
- **VUs:** 10 usuarios virtuales
- **Duración:** 5 minutos
- **Propósito:** Verificar que el sistema funciona sin carga
- **SLA:** p95 < 1.5s, failures 0%
- **Tiempo:** ~5 minutos de ejecución

**Resultado esperado:**
```
✅ OK — Sistema sano, sin issues bajo carga mínima
```

### Escenario 2: Pico Electoral ⭐ (TEST CRÍTICO)
- **VUs:** 0 → 150 usuarios virtuales (gradual)
- **Duración:** 30 minutos
- **Propósito:** Simular votación real pico
- **SLA:** p95 < 5s, p99 < 8s, failures < 10%
- **Tiempo:** ~30 minutos de ejecución

**Fases:**
```
0-2 min:   0 → 20 VUs (ramp-up)
2-12 min:  20 → 100 VUs (ramp to pico)
12-22 min: 100 → 150 VUs (PEAK — simula 17:00-17:30)
22-27 min: 150 → 50 VUs (ramp-down)
27-30 min: 50 → 0 VUs (cool-off)
```

**Traffic Distribution en Pico:**
```
30% Observadores registrando actas        (30 req/min)
40% Coordinadores revisando actas         (20 req/min)
20% Admin checking dashboard              (4 req/min)
10% Misc login/refresh                    (8 req/min)
────────────────────────────────────────
Total: ~440-600 queries/min en pico
```

---

## ✅ CRITERIOS DE ACEPTACIÓN

### PASS — LISTO PARA PRODUCCIÓN

**Baseline:**
- [ ] p95 latency < 1.5s
- [ ] p99 latency < 2.0s
- [ ] Failures = 0%

**Pico Electoral:**
- [ ] p95 latency < 5.0s
- [ ] p99 latency < 8.0s (critical)
- [ ] Failures < 10%
- [ ] No error escalation durante test
- [ ] System recovers after peak

**Infrastructure:**
- [ ] CPU utilization < 80%
- [ ] Memory utilization < 75%
- [ ] DB connection pool no saturado
- [ ] No timeout/deadlock errors

---

### ⚠️ CAUTION — PRODUCCIÓN CON RIESGOS

**Síntomas:**
- Pico: p99 latency 7-8s (near limit)
- Pico: Failures 8-10% (high but tolerable)
- CPU 75-85% (near capacity)
- Pool connections 20-24/25 (little headroom)

**Acción recomendada:**
```
🚨 Aumentar alertas para 24/7 monitoreo
🚨 Mantener equipo técnico on-call
🚨 Documentar rollback plan
🚨 Reducir timeouts de usuario si es necesario
🚨 Considerar load shedding (rechazar requests si system overload)
```

**Decisión:** ⚠️ Puedes ir a producción PERO con riesgos documentados

---

### ❌ FAIL — NO LISTO PARA PRODUCCIÓN

**Síntomas:**
- Pico: p95 > 5s
- Pico: p99 > 8s
- Pico: Failures > 10%
- Pico: CPU > 85%
- Breaking point < 80 VUs
- Pool completamente agotado

**Acción recomendada:**
```
❌ NO PASAR A PRODUCCIÓN
📋 Identificar bottleneck (ver 06-FIXES-Y-TUNING.md)
🔧 Implementar fixes:
   - Aumentar replicas
   - Tuning DB pool
   - Implementar caché
   - Optimizar queries
🔄 Retest completo
⬆️ Escalación si no se puede fix
```

---

## 🎯 PLAN DE ACCIÓN — PRÓXIMAS 2 SEMANAS

### Semana 1: Setup & Validation

**Día 1-2: Setup Inicial**
```bash
# Día 1: 2 horas
□ Instalar k6 (15 min)                    → 01-INSTALACION.md
□ Seed test data en BD (15 min)           → 02-SEED-DATOS.md
□ Verificar scripts k6 (30 min)           → frontend/tests/load/

# Día 2: 1 hora
□ Ejecutar baseline test (5 min)          → 04-EJECUTAR-TESTS.md
□ Interpretar resultados (30 min)         → 05-INTERPRETAR-RESULTADOS.md
□ Documentar findings (25 min)
```

**Día 3-4: Pico Test (CRÍTICO)**
```bash
# Día 3: 1.5 horas
□ Ejecutar pico test (30 min)             → 04-EJECUTAR-TESTS.md
□ Monitorear durante test (30 min)
□ Análisis inicial (30 min)

# Día 4: 2 horas
□ Análisis detallado (1 hora)             → 05-INTERPRETAR-RESULTADOS.md
□ Comparar vs SLAs (30 min)
□ Identificar issues si los hay (30 min)
```

**Día 5: Análisis & Decisión**
```bash
# Día 5: 1-2 horas
□ Compilar reporte (1 hora)
□ Decisión: PASS / CAUTION / FAIL
□ Comunicar al equipo
```

---

### Semana 2: Fixes (Si es Necesario)

**Si resultado fue ⚠️ CAUTION o ❌ FAIL:**

```bash
# Día 6-7: Identificar & Fijar Bottleneck (8-12 horas)
□ Root cause analysis                     → 06-FIXES-Y-TUNING.md
□ Implementar fix(es):
   - Aumentar replicas en k8s?
   - Tuning DB connection pool?
   - Implementar Redis cache?
   - Optimizar query lenta?
□ Deploy fix a dev

# Día 8-9: Retest (2 horas)
□ Ejecutar pico test nuevamente
□ Comparar vs baseline
□ Verificar mejora

# Día 10-14: Documentación & Go/No-Go
□ Stress test execution (1 hora)
□ Final documentation (2 horas)
□ Executive summary (1 hora)
□ GO / NO-GO decision meeting
```

---

## 📈 MATRIZ DE DECISIÓN

### ¿PASS?

```
Baseline ✅ (p95 < 1.5s, failures 0%)
Pico ✅ (p95 < 5s, p99 < 8s, failures < 10%)
CPU ✅ (< 80%)
Pool ✅ (headroom available)
Breaking point ✅ (> 100 VUs)

                    ↓
                    
        ✅ GO TO PRODUCTION
        
Estás listo. Confianza completa.
Monitoreo normal durante votación.
```

---

### ¿CAUTION?

```
Baseline ✅
Pico ⚠️ (p99 7-8s, failures 8-10%, CPU 75-85%)
Recoverable ✅ (sistema se recupera después de pico)

                    ↓
                    
    ⚠️ GO TO PRODUCTION CON RIESGOS
    
Requisitos:
  - 24/7 monitoreo en vivo
  - Equipo técnico on-call
  - Rollback plan documentado
  - Alertas agresivas configuradas
  - Load shedding plan (opcional)
  
Aceptable solo si:
  - Equipo capacitado
  - Alertas configuradas
  - Runbooks listos
```

---

### ¿FAIL?

```
Pico ❌ (p99 > 8s, failures > 10%)
CPU ❌ (> 85%)
Pool ❌ (exhausted)
Breaking point ❌ (< 80 VUs)

                    ↓
                    
            ❌ NO GO TO PRODUCTION
            
Acciones:
  1. Identificar bottleneck (CPU? DB? pool?)
  2. Implementar fix (replicas? cache? query opt?)
  3. Retest completo
  4. Si sigue fallando: Escalación
```

---

## 🛠️ QUICK DECISION TREE

```
┌─ Ejecutar ./run.sh baseline ──→ ❌ FAIL?
│                                    └─→ Debugging (01-INSTALACION.md)
│                                    
├─ Ejecutar ./run.sh pico ─────→ ❌ FAIL? (p99 > 8s, failures > 10%)
│  (30 min)                         └─→ See 06-FIXES-Y-TUNING.md (fixes necesarios)
│                                    
├─ ✅ PASS? (p95 < 5s, failures < 10%)
│  └─→ 07-CHECKLIST-GO-LIVE.md (verificaciones finales)
│
├─ ⚠️ CAUTION? (p99 7-8s, CPU 75-85%)
│  └─→ 07-CHECKLIST-GO-LIVE.md (pero con monitoreo 24/7)
│
└─→ GO / NO-GO DECISION
```

---

## ⚡ COMANDOS RÁPIDOS

```bash
# Setup (30 min total)
npm install -g k6                           # Install k6
# ... seed data (ver 02-SEED-DATOS.md)

# Test 1: Baseline (5 min)
cd frontend/tests/load
./run.sh baseline

# Test 2: Pico Election (30 min) — CRÍTICO
./run.sh pico

# Ver resultados
ls -lh results/
cat results/pico-*.json | jq '.metrics.http_req_duration'

# Help
./run.sh help
```

---

## 📞 ESCENARIOS COMUNES

### Escenario A: "No tengo tiempo, debo ir a producción YA"

**Mínimo aceptable:**
```bash
□ Ejecutar baseline test → debe PASS
□ Ejecutar pico test (30 min) → debe PASS
□ Revisar breaking point > 100 VUs
□ Decisión basada en resultados
```

**Tiempo mínimo:** 1 hora

---

### Escenario B: "Pico test fallió, ¿qué hago?"

**Pasos:**
1. No entres en pánico, esto es esperado
2. Ir a `06-FIXES-Y-TUNING.md`
3. Identificar bottleneck (CPU? DB pool? query lenta?)
4. Implementar fix
5. Retest: `./run.sh pico`
6. Si sigue fallando, escalación

**Tiempo:** 2-5 días típicamente

---

### Escenario C: "Tengo solo 4 horas antes de votación"

**Plan de emergencia:**
```bash
# 1h: Setup
npm install -g k6
# Seed data...

# 2.5h: Baseline + Pico test
./run.sh baseline (5 min)
./run.sh pico (30 min)

# 0.5h: Análisis rápido
# Si PASS → GO con confianza
# Si FAIL → decidir si arriesgar o delay votación
```

**Nota:** No ideal, pero posible

---

## 🚀 DECISIÓN FINAL

**ANTES de pasar a producción, completa esto:**

```
[ ] PASO 1: Lee este documento (5 min)
[ ] PASO 2: Ejecuta baseline test (5 min)
[ ] PASO 3: Ejecuta pico test (30 min)
[ ] PASO 4: Interpreta resultados (20 min)
[ ] PASO 5: Completa 07-CHECKLIST-GO-LIVE.md (30 min)
[ ] PASO 6: Decisión GO / NO-GO
```

**Total:** ~1.5-2 horas

**Si NO completas estos pasos, NO estás listo para producción.**

---

## 📋 Firma de Aceptación

**Antes de votación, DEBE estar firmado:**

```
Project Manager: _________________ Date: _______
Reason: Confirmado que sistema tolera carga pico
        load testing PASS, SLAs met, ready for production

Technical Lead: _________________ Date: _______
Reason: Infraestructura validada, no blockers identificados

DevOps Lead:    _________________ Date: _______
Reason: Monitoreo setup, alertas configured, rollback plan ready
```

---

## ✅ Estás Listo Cuando

1. ✅ Baseline test PASS
2. ✅ Pico test PASS (o CAUTION con mitigaciones)
3. ✅ Documentación completa
4. ✅ Equipo entrenado
5. ✅ Monitoreo funciona
6. ✅ Rollback plan listo
7. ✅ Todas las casillas arriba firmadas

---

## 🔗 Próximos Pasos

**Si estás listo:** Ve a `01-INSTALACION.md`  
**Si necesitas contexto:** Lee `README.md`  
**Si ya pasaste tests:** Ve a `07-CHECKLIST-GO-LIVE.md`

---

**Última actualización:** 2026-11-05  
**Status:** Pre-production validation checklist  
**Criticidad:** 🔴 MUST READ BEFORE GOING LIVE
