# ⚡ Guía Rápida — Load Testing Pre-Producción

**Lee esto primero. 5 minutos.**

---

## 🎯 Misión

Validar que Control Electoral tolera carga de votación real **antes de pasar a producción**.

**Sin esto:** Riesgo de colapse durante pico electoral.  
**Con esto:** Confianza de que sistema aguanta 500 JRVs + 200 observadores simultáneos.

---

## 📋 Flujo Rápido (2 horas)

```
Paso 1: Setup (30 min)
  → 01-INSTALACION.md
  → 02-SEED-DATOS.md

Paso 2: Test Baseline (5 min)
  → ./run.sh baseline
  → ✓ p95 < 1.5s, failures 0% ?

Paso 3: Test Pico (30 min)
  → ./run.sh pico
  → ✓ p95 < 5s, p99 < 8s, failures < 10% ?

Paso 4: Análisis (20 min)
  → 05-INTERPRETAR-RESULTADOS.md
  → ¿PASS / CAUTION / FAIL?

Paso 5: Decide (5 min)
  → Si ✅ PASS → 07-CHECKLIST-GO-LIVE.md
  → Si ⚠️ CAUTION → 06-FIXES-Y-TUNING.md
  → Si ❌ FAIL → 06-FIXES-Y-TUNING.md
```

---

## 📚 Documentos en Orden

| # | Documento | Tiempo | Cuándo |
|---|-----------|--------|--------|
| **README** | `README.md` | 10 min | Primero: contexto + índice |
| **0️⃣ CRÍTICO** | `00-PRE-PRODUCCION.md` | 5 min | ANTES de hacer nada |
| **1️⃣** | `01-INSTALACION.md` | 10 min | Instalar k6 |
| **2️⃣** | `02-SEED-DATOS.md` | 15 min | Crear test data en BD |
| **3️⃣** | `03-COMANDOS.md` | 10 min | Referencia rápida |
| **4️⃣** | `04-EJECUTAR-TESTS.md` | 30-60 min | Correr tests (paso a paso) |
| **5️⃣** | `05-INTERPRETAR-RESULTADOS.md` | 20 min | Qué significan métricas |
| **6️⃣** | `06-FIXES-Y-TUNING.md` | Variable | Si tests fallan (fixes) |
| **7️⃣** | `07-CHECKLIST-GO-LIVE.md` | 30 min | Pre-producción final |

---

## 🚀 Comienza Aquí

### Si tienes 5 minutos:
→ Lee este documento (GUIA-RAPIDA.md)

### Si tienes 10 minutos:
→ Lee `00-PRE-PRODUCCION.md` (contexto crítico)

### Si tienes 30 minutos:
→ `01-INSTALACION.md` + `02-SEED-DATOS.md` (setup)

### Si tienes 1 hora:
→ Completa setup + baseline test

### Si tienes 2 horas:
→ Setup + baseline + pico test + análisis rápido

### Si tienes 1-2 días:
→ Todo: setup + tests + análisis + fixes + retest

---

## ✅ Pre-requisitos

Antes de empezar, verifica:

```bash
# 1. Backend corriendo?
curl http://localhost:8080/api/v1/health
# Esperado: {"status":"UP"}

# 2. Base de datos accesible?
psql -c "SELECT 1"
# Esperado: (1 row)

# 3. k6 instalado?
k6 version
# Esperado: k6 vX.XX.X

# Si algo falla: ver 01-INSTALACION.md
```

---

## 🎯 Criterios de Aceptación

### ✅ PASS — Go to Production

```
Baseline:
  ✓ p95 < 1.5s
  ✓ failures 0%

Pico:
  ✓ p95 < 5s
  ✓ p99 < 8s
  ✓ failures < 10%
  ✓ CPU < 80%
```

### ⚠️ CAUTION — Go with Monitoring

```
Pico:
  ⚠️ p99 7-8s (AT limit, not over)
  ⚠️ failures 8-10% (high but acceptable)
  ⚠️ CPU 75-85% (near saturation)
  ✓ system recovers after peak
```

### ❌ FAIL — Do NOT Go

```
Pico:
  ❌ p99 > 8s
  ❌ failures > 10%
  ❌ CPU > 85%
  ❌ no recovery after peak
```

---

## 🛠️ Comandos Principales

```bash
cd frontend/tests/load

# Test sencillo (5 min)
./run.sh baseline

# Test realista (30 min) ← RECOMENDADO
./run.sh pico

# Test de estrés (30 min)
./run.sh stress

# Todos los tests (2.5 horas)
./run.sh all

# Ver help
./run.sh help
```

---

## 📊 Qué Medir

| Métrica | Target | Crítico |
|---------|--------|---------|
| **p95 latency** | < 5s | ✓ SÍ |
| **p99 latency** | < 8s | ✓ SÍ |
| **Failures** | < 10% | ✓ SÍ |
| **CPU** | < 80% | ✓ SÍ |
| **DB Pool** | < 80% utilizado | ⚠️ |

---

## 🔴 Red Flags — STOP

Si ves cualquiera de estas, NO pases a producción:

```
❌ p95 > 5s
❌ p99 > 8s
❌ failures > 10%
❌ CPU > 85%
❌ Sistema no se recupera
❌ Team no entrenado
❌ Monitoring no funciona
```

---

## 📞 Workflow Típico

### Escenario 1: Todo PASS (50% de casos)

```
Setup (30 min) → Baseline (5 min) → Pico (30 min)
                                           ↓
                           → ✅ PASS → Go Live
                                  ↓
                          (total: 1.5 horas)
```

### Escenario 2: CAUTION (30% de casos)

```
Setup → Baseline → Pico
                    ↓
            ⚠️ CAUTION
                    ↓
        Implementar fixes (4h) → Retest (30 min)
                                        ↓
                            → ✅ PASS o ⚠️ GO with monitoring
                                  ↓
                          (total: 6-8 horas)
```

### Escenario 3: FAIL (20% de casos)

```
Setup → Baseline → Pico
                    ↓
            ❌ FAIL
                    ↓
        Fixes (8h) → Retest (30 min)
                            ↓
                    → ✅ PASS
                         ↓
                  (total: 10+ horas)
```

---

## 💡 Pro Tips

```
1. Correr pico test DESPUÉS de baseline
   → Baseline verifica salud inicial
   → Pico es más complejo y tarda más

2. Monitorear logs durante pico
   tail -f logs/application.log | grep duration

3. No correr todos los tests juntos
   → Mejor: baseline → esperar → pico → esperar → análisis

4. Guardar resultados
   Los archivos JSON en results/ tienen todo

5. Si confuso, releer 05-INTERPRETAR-RESULTADOS.md
   → Dice exactamente qué significa cada número
```

---

## 📈 Escala de Tiempo Recomendada

```
Semana 1:
  Lun: Setup + baseline (2 horas)
  Mar: Pico test + análisis (2 horas)
  Mié: Fixes si es necesario (4-8 horas)
  Jue: Retest después de fixes (2 horas)
  Vie: Documentación final (2 horas)

Semana 2:
  Próximo lunes: Votación real ✅
```

---

## 🎓 Aprende los Conceptos

**ENTENDER antes de ejecutar:**

1. **Latency (Latencia):**
   - p95 = 95% de requests terminan en X segundos
   - p99 = 99% de requests terminan en X segundos
   - Si p99 > 8s, algunos usuarios esperan mucho

2. **Throughput (Rendimiento):**
   - Requests por segundo que sistema puede procesar
   - Si cae durante pico, hay bottleneck

3. **Failure Rate:**
   - % de requests que fallan (5xx errors)
   - > 10% = problema

4. **Load Profile:**
   - VU count vs time
   - Pico test simula: 0→20→100→150 usuarios en 30 minutos

---

## 🔗 Links Útiles

- **k6 Scripts:** `frontend/tests/load/`
- **Config:** `frontend/tests/load/config.js`
- **Infrastructure Plan:** `/INFRASTRUCTURE_RESILIENCE_PLAN.md`
- **Resultados:** `frontend/tests/load/results/*.json`

---

## ✅ Próximo Paso

1. Lee `00-PRE-PRODUCCION.md` (5 min)
2. Sigue `01-INSTALACION.md` (10 min)
3. Ejecuta `./run.sh baseline` (5 min)
4. Ejecuta `./run.sh pico` (30 min)
5. Analiza en `05-INTERPRETAR-RESULTADOS.md` (20 min)

**Total:** ~1.5 horas para validación básica

---

**Última actualización:** 2026-11-05  
**Status:** Ready to use  
**Versión:** 1.0

**¿Listo?** → Abre `00-PRE-PRODUCCION.md` ahora
