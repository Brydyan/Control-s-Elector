# Load Testing con k6 — Guía Pre-Producción

**Proyecto:** Control Electoral  
**Task:** T-INFRA-LOAD-01  
**Objetivo:** Validar que el sistema tolera carga electoral antes de pasar a producción  
**Tiempo estimado:** 1-2 semanas de pruebas + fixes

---

## 🎯 Por qué Load Testing es CRÍTICO

Sistema electoral con **picos impredecibles concentrados**:
- Normal: ~50 queries/min
- **Pico (17:00-17:30 votación):** 30-40 actas/min = **440-600 queries/min**
- **Riesgo actual:** 0 tests = descubrir problemas en producción = DESASTRE

Sin load testing **no estás listo para elecciones**.

---

## 📋 Documentación en Esta Carpeta

| Documento | Propósito | Tiempo |
|-----------|-----------|--------|
| **00-PRE-PRODUCCION.md** | Checklist antes de elecciones | 5 min lectura |
| **01-INSTALACION.md** | Cómo instalar k6 | 10 min |
| **02-SEED-DATOS.md** | Crear datos de test en BD | 15 min |
| **03-COMANDOS.md** | Todos los comandos k6 | 10 min referencia |
| **04-EJECUTAR-TESTS.md** | Cómo correr tests (paso a paso) | 30-60 min ejecución |
| **05-INTERPRETAR-RESULTADOS.md** | Qué significan las métricas | 20 min lectura |
| **06-FIXES-Y-TUNING.md** | Qué hacer si los tests fallan | Variable |
| **07-CHECKLIST-GO-LIVE.md** | Verification final antes de elecciones | 30 min |

---

## 🚀 Quick Reference — Camino Rápido

### Si tienes 1 hora (quick validation)
```bash
# 1. Instalar k6
npm install -g k6

# 2. Seed test data (5 min)
# Ver 02-SEED-DATOS.md

# 3. Baseline test (5 min ejecución)
cd frontend/tests/load
./run.sh baseline

# 4. Resultado esperado
# ✓ p95 < 1.5s, failures 0% = OK
```

### Si tienes 4 horas (proper validation)
```bash
# 1-2: Setup (ver 01-INSTALACION.md, 02-SEED-DATOS.md)

# 3. Baseline (5 min)
./run.sh baseline

# 4. Pico Electoral — TEST CRÍTICO (30 min)
./run.sh pico

# 5. Análisis (30 min)
# Ver 05-INTERPRETAR-RESULTADOS.md

# 6. Documentar findings
```

### Si tienes 2 semanas (full validation)
```bash
# Semana 1
# - Día 1-2: Setup completo
# - Día 3-4: Baseline + Pico
# - Día 5: Análisis

# Semana 2
# - Día 6-7: Fixes si es necesario
# - Día 8-9: Retest después de fixes
# - Día 10: Stress test (breaking point)
# - Día 11-14: Documentación + go/no-go decision
```

---

## 🔴 ANTES DE PASAR A PRODUCCIÓN — MUST DO

Lee esto **PRIMERO**: `00-PRE-PRODUCCION.md`

**Resumen: Tienes que verificar TODAS estas casillas:**

```
[ ] Sistema tolera 100 VUs sin degradación
[ ] p95 latency < 5s en pico
[ ] p99 latency < 8s en pico
[ ] Failure rate < 10% en pico
[ ] CPU no excede 85%
[ ] Pool DB no agotado (headroom disponible)
[ ] Observabilidad funciona (logs, metrics)
[ ] Backups realizados
[ ] Rollback plan documentado
```

Si **alguna casilla** está ❌, **NO estás listo para producción**.

---

## 🎓 Learning Path (Para Entender Todo)

### Día 1: Conceptos
1. Lee: `00-PRE-PRODUCCION.md` (contexto por qué esto importa)
2. Lee: `05-INTERPRETAR-RESULTADOS.md` (qué significan las métricas)

### Día 2: Setup
1. Lee: `01-INSTALACION.md`
2. Lee: `02-SEED-DATOS.md`
3. Ejecuta: setup completo

### Día 3-5: Pruebas
1. Ejecuta: `./run.sh baseline`
2. Ejecuta: `./run.sh pico`
3. Lee: `05-INTERPRETAR-RESULTADOS.md` (analiza resultados)

### Si necesitas fixes:
1. Lee: `06-FIXES-Y-TUNING.md`
2. Implementa: cambios necesarios
3. Retest: `./run.sh pico`

### Antes de Go Live:
1. Lee: `07-CHECKLIST-GO-LIVE.md`
2. Verifica: todas las casillas
3. Documenta: resultados finales

---

## 📊 Matriz de Decisión

### ✅ PASS — LISTO PARA PRODUCCIÓN

**Criterios:**
- Baseline PASS (p95 < 1.5s, failures 0%)
- Pico PASS (p95 < 5s, p99 < 8s, failures < 10%)
- Stress test entendido (breaking point identificado)
- Documentación completa

**Acción:** ✅ **GO TO PRODUCTION** con confianza

---

### ⚠️ CAUTION — PRODUCCIÓN CON RIESGOS

**Criterios:**
- Pico con latencias near limits (p99 7-8s)
- Failures 8-10%
- CPU 80-85%
- Pool near saturation

**Acción:** 
- ⚠️ Go to production CON MONITORES activos 24/7
- Mantener on-call ready
- Plan de rollback activo
- Monitoreo agresivo primeras horas

---

### ❌ FAIL — NO LISTO PARA PRODUCCIÓN

**Criterios:**
- Pico p99 > 8s
- Failures > 10%
- CPU > 85%
- Pool agotado
- Breaking point < 80 VUs

**Acción:**
- ❌ **NO GO TO PRODUCTION**
- Fix issues: ver `06-FIXES-Y-TUNING.md`
- Retest completo
- Recurrir escalación si no se puede fix

---

## 🛠️ Tech Stack

| Component | Versión | Propósito |
|-----------|---------|----------|
| **k6** | Latest | Load testing framework |
| **JavaScript** | ES6+ | Test scripting |
| **PostgreSQL** | (prod) | Test data |
| **Quarkus/Spring** | (backend) | API under test |

---

## 📈 Métricas Clave a Monitorear

```javascript
// SLAs por endpoint

Login:
  p95 < 1.5s      (95% de logins < 1.5 segundos)
  p99 < 2.0s      (99% de logins < 2 segundos)
  failures < 2%   (menos del 2% falla)

Registrar Acta (CRÍTICO — locks pesimistas):
  p95 < 5.0s      (95% finalizan en < 5s)
  p99 < 8.0s      (99% finalizan en < 8s)
  failures < 5%   (menos del 5% falla)

Listar Actas (Coordinator):
  p95 < 3.0s
  p99 < 5.0s
  failures < 5%

Dashboard:
  p95 < 3.0s
  p99 < 5.0s
  failures < 5%
```

---

## 🚨 Common Failures & Solutions

| Falla | Causa | Solución |
|-------|-------|----------|
| Login 401 Unauthorized | Test users no existen | Ver `02-SEED-DATOS.md` |
| Connection refused | Backend no corre | Ver `01-INSTALACION.md` step 2 |
| p99 > 8s | Registrar Acta bajo locks | Ver `06-FIXES-Y-TUNING.md` — caché |
| Failures > 10% | Pool BD agotado | Ver `06-FIXES-Y-TUNING.md` — pool tuning |
| CPU > 85% | Insuficientes replicas | Ver `06-FIXES-Y-TUNING.md` — HPA |

---

## 📞 Workflow Recomendado

### Fase 1: Setup (Día 1-2)
```
❌ → ✅ Instalar k6
❌ → ✅ Seed test data
❌ → ✅ Baseline test PASS
```

### Fase 2: Validation (Día 3-5)
```
❌ → ✅ Pico test PASS
❌ → ✅ Stress test ejecución
❌ → ✅ Documentar resultados
```

### Fase 3: Fixes (Día 6-10, si es necesario)
```
⚠️ → ✅ Identificar bottleneck
⚠️ → ✅ Implementar fix
⚠️ → ✅ Retest after fix
⚠️ → ✅ Verificar mejora
```

### Fase 4: Go/No-Go Decision (Día 11)
```
✅ → GO TO PRODUCTION
⚠️ → GO con riesgos documentados
❌ → NO GO — escalation
```

---

## 🎯 Success Criteria — Pre-Producción

**MUST HAVE:**
- [ ] Baseline PASS
- [ ] Pico PASS (failures < 10%)
- [ ] Breaking point > 100 VUs
- [ ] Documentation completa

**NICE TO HAVE:**
- [ ] Stress test ejecutado
- [ ] Observabilidad setup
- [ ] Runbooks documentados
- [ ] Equipo entrenado

---

## 📚 Estructura de Carpetas

```
documents/k6/
├── README.md (este archivo — índice)
├── 00-PRE-PRODUCCION.md (LEER PRIMERO)
├── 01-INSTALACION.md
├── 02-SEED-DATOS.md
├── 03-COMANDOS.md
├── 04-EJECUTAR-TESTS.md
├── 05-INTERPRETAR-RESULTADOS.md
├── 06-FIXES-Y-TUNING.md
├── 07-CHECKLIST-GO-LIVE.md
└── assets/
    ├── example-output.txt (output de test baseline)
    ├── sla-thresholds.json
    └── breaking-point-graph.png
```

---

## 🔗 Links Relacionados

- **Scripts k6:** `frontend/tests/load/`
- **Análisis completo:** `INFRASTRUCTURE_RESILIENCE_PLAN.md`
- **Backend health:** `http://localhost:8080/api/v1/health`

---

## 📝 Version Control

**Last Updated:** 2026-11-05  
**Status:** Ready for pre-production testing  
**Next Review:** After test execution

---

## ✅ Comienza Aquí

**Paso 1:** Lee `00-PRE-PRODUCCION.md` (5 min)  
**Paso 2:** Sigue `01-INSTALACION.md` (10 min)  
**Paso 3:** Ejecuta `02-SEED-DATOS.md` (15 min)  
**Paso 4:** Corre tests en `04-EJECUTAR-TESTS.md` (30-60 min)  
**Paso 5:** Analiza resultados en `05-INTERPRETAR-RESULTADOS.md` (20 min)

**Total:** ~2-3 horas para validación completa

---

**¿Listo?** → Abre `00-PRE-PRODUCCION.md` ahora
