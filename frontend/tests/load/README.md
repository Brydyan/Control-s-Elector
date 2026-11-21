# Load Testing Suite — Control Electoral

**Project:** T-INFRA-LOAD-01  
**Purpose:** Validate system capacity under electoral load  
**Stack:** k6 (load testing framework)  
**Status:** Ready to deploy

---

## 📋 Overview

Load testing suite para Control Electoral usando k6. Simula escenarios realistas de votación con picos de carga concentrados.

**Escenarios incluidos:**
1. **Baseline** (10 VUs, 5 min) — Verificar sistema sano
2. **Ramp-up** (0→100 VUs, 25 min) — Identificar degradación
3. **Listar Actas** (20 VUs, 20 min) — Coordinator dashboard
4. **Registrar Acta** (30 VUs, 20 min) — Observer workflow
5. **Pico Electoral** (0→150 VUs, 30 min) ⭐ **MÁS REALISTA**
6. **Stress Test** (200 VUs, 30 min) — Breaking point

---

## 🚀 Quick Start

### Paso 1: Instalar k6

**Option A: npm (recomendado si tienes Node.js)**
```bash
npm install -g k6
k6 version  # Verify
```

**Option B: Homebrew (macOS)**
```bash
brew install k6
```

**Option C: Docker**
```bash
docker pull grafana/k6:latest
```

**Option D: Descargar binario**
Ver https://grafana.com/products/cloud/k6/download/

---

### Paso 2: Seed Test Data en Base de Datos

Antes de correr tests, crear usuarios y datos de prueba:

```sql
-- Script: backend/src/main/resources/db/test-data.sql
-- Usuarios para tests
INSERT INTO usuario (email, password, rol)
VALUES
  ('obs1@electoral.ec', bcrypt('SecurePass123!'), 'OBSERVER'),
  ('obs2@electoral.ec', bcrypt('SecurePass123!'), 'OBSERVER'),
  ('obs3@electoral.ec', bcrypt('SecurePass123!'), 'OBSERVER'),
  ('obs4@electoral.ec', bcrypt('SecurePass123!'), 'OBSERVER'),
  ('obs5@electoral.ec', bcrypt('SecurePass123!'), 'OBSERVER'),
  ('coord1@electoral.ec', bcrypt('SecurePass123!'), 'COORDINATOR'),
  ('coord2@electoral.ec', bcrypt('SecurePass123!'), 'COORDINATOR'),
  ('admin@electoral.ec', bcrypt('SecurePass123!'), 'ADMIN');

-- JRVs para tests (~500)
INSERT INTO jrv (numero, canton_id, parroquia_id, ...)
SELECT ... FROM (SELECT row_number() OVER () as id FROM generate_series(1, 500));

-- Cargos (provincial, cantonal, parroquial)
INSERT INTO cargo (nombre, nivel) VALUES
  ('Presidente', 'PROVINCIAL'),
  ('Consejero Provincial', 'CANTONAL'),
  ('Consejero Cantonal', 'PARROQUIAL');

-- Candidatos por cargo
INSERT INTO candidato (nombre, partido, cargo_id)
SELECT 'Candidato ' || row_number() OVER (), 'Partido X', 1
FROM generate_series(1, 5);
-- (Repetir para cada cargo)
```

Luego ejecutar:
```bash
cd backend/electoral-api-quarkus
psql -h localhost -U electoral_user -d electoral_db -f ../src/main/resources/db/test-data.sql
```

---

### Paso 3: Correr Test Baseline

```bash
cd frontend/tests/load

# Opción A: Script local
./run.sh baseline

# Opción B: Comando k6 directo
k6 run scenarios/login.js --vus 10 --duration 5m

# Opción C: Con Docker
docker run --rm \
  -v $(pwd):/workspace \
  -e API_HOST=host.docker.internal:8080 \
  grafana/k6:latest \
  run /workspace/scenarios/login.js --vus 10 --duration 5m
```

**Resultado esperado (SUCCESS):**
```
✓ login status 200
✓ login has accessToken

█ baseline
  execution: local
  script: scenarios/login.js
  output: json

    data_received..................: 45 kB
    data_sent......................: 12 kB
    http_req_blocked...............: avg=1.23ms   min=0.5ms    med=1.1ms    max=8.9ms    p(90)=2.1ms p(95)=2.8ms p(99)=7.3ms
    http_req_connecting............: avg=0.2ms    min=0ms      med=0ms      max=1.2ms    p(90)=0.2ms p(95)=0.5ms p(99)=1.1ms
    http_req_duration..............: avg=125.21ms min=98.3ms   med=122.1ms  max=245.2ms  p(90)=134.2ms p(95)=142.1ms p(99)=198.3ms ✓
    http_req_failed................: 0%      ✓
    http_req_receiving.............: avg=12.2ms   min=5.3ms    med=11.2ms   max=28.9ms   p(90)=15.2ms p(95)=18.1ms p(99)=24.5ms
    http_req_sending...............: avg=2.1ms    min=0.8ms    med=2.1ms    max=4.2ms    p(90)=2.8ms  p(95)=3.2ms  p(99)=3.9ms
    http_req_tls_handshaking.......: avg=0ms      min=0ms      med=0ms      max=0ms      p(90)=0ms    p(95)=0ms    p(99)=0ms
    http_req_waiting...............: avg=110.9ms  min=91.2ms   med=108.3ms  max=218.5ms  p(90)=119.5ms p(95)=126.2ms p(99)=175.3ms
    http_reqs......................: 50 reqs/sec
    iteration_duration.............: avg=1.12s    min=1.10s    med=1.12s    max=1.25s    p(90)=1.14s  p(95)=1.15s  p(99)=1.21s
    iterations.....................: 50

✓ All checks passed!
```

---

## 🎯 Comandos Principales

### Baseline (verificar sistema sano)
```bash
./run.sh baseline
# 10 VUs, 5 min
# Esperado: p95 < 1.5s, failures 0%
```

### Pico Electoral (RECOMENDADO - más realista)
```bash
./run.sh pico
# 0→150 VUs over 30 min
# Simula: 30% observers, 40% coordinadores, 20% admin, 10% misc
# Esperado: p95 < 5s, p99 < 8s, failures < 10%
```

### Todos los Tests
```bash
./run.sh all
# Ejecuta TODOS los tests en secuencia (~2.5 horas)
```

### Con URL customizada
```bash
API_HOST=api.electoral.ec:8080 ./run.sh pico
```

### Con Docker
```bash
K6_DOCKER=true ./run.sh pico
```

### Cache Hit Ratio (T-CACHE-STRAT-01 F.5)
```bash
# 100 VUs, ramp up 10s, 10 lecturas por VU sobre los 4 endpoints cacheados
k6 run scenarios/cache-hit-ratio.js

# Con credenciales del MASTER (la geografía exige `configuracion:read`)
MASTER_EMAIL=... MASTER_PASSWORD=... k6 run scenarios/cache-hit-ratio.js
```
El script lee el hit ratio real de las métricas Micrometer del backend
(`cache_hits_total` / `cache_misses_total` en `/q/metrics`), no lo estima.
Escribe el reporte en `results/cache-hit-ratio-summary.{md,json}`.

Comparación con/sin cache (el número que justifica la cache):

```bash
# 1) Backend con la cache activa → guardar resumen
k6 run scenarios/cache-hit-ratio.js

# 2) Backend con QUARKUS_CACHE_ENABLED=false → guardar resumen
k6 run scenarios/cache-hit-ratio.js
```

---

## 📊 Interpretar Resultados

### Métricas Clave

| Métrica | Descripción | SLA |
|---------|-------------|-----|
| `http_req_duration` | Latencia de request HTTP | p95 < 5s, p99 < 8s |
| `http_req_failed` | % de requests fallidos (5xx) | < 10% |
| `http_reqs` | Throughput (requests/sec) | > 100 |
| `http_req_received` | Data recibida total | Monitor |
| `iterations` | Número de iteraciones completadas | > 1000 |

### Umbrales SLA por Endpoint

```javascript
// Definidos en config.js

Login:
  p95: 1500ms (1.5s)
  p99: 2000ms (2s)
  failureRate: 2%

Registrar Acta:
  p95: 5000ms (5s) ← Lock pesimistas
  p99: 8000ms (8s)
  failureRate: 5%

Listar Actas:
  p95: 3000ms (3s)
  p99: 5000ms (5s)
  failureRate: 5%

Dashboard:
  p95: 3000ms (3s)
  p99: 5000ms (5s)
  failureRate: 5%
```

### Ejemplos de Veredictos

#### ✅ PASS - Baseline (Esperado)
```
Duration: 5m, VUs: 10
- p95 latency: 1.2s (< 1.5s) ✓
- p99 latency: 1.8s (< 2s) ✓
- failures: 0% (< 2%) ✓
Verdict: Sistema sano ✅
```

#### ⚠️ CAUTION - Pico (Aceptable pero con caveats)
```
Duration: 30m, Peak VUs: 150
- p95 latency: 4.5s (< 5s) ✓
- p99 latency: 8.8s (> 8s) ⚠️
- failures: 8% (< 10%) ⚠️
CPU utilization: 85% (near limit)
DB pool: 24/25 conexiones (no headroom)
Verdict: Sistema tolera pico PERO sin margen de seguridad
Action: Aumentar replicas, pool tuning, implementar caché
```

#### ❌ FAIL - Stress Test (Esperado)
```
Duration: 30m, Sustained VUs: 200
- p95 latency: 12.5s (> 5s) ❌
- p99 latency: 18.3s (> 8s) ❌
- failures: 35% (> 10%) ❌
Breaking point: ~120 VUs (antes de failures > 10%)
Verdict: Sistema colapsado en stress
Action: Este escenario es UNREALISTA pero útil para entender límites
```

---

## 📈 Análisis Detallado

### Guardar resultados para análisis posterior

k6 exporta JSON:
```bash
./run.sh pico
# Genera: results/pico-20261105-142300.json
```

### Convertir JSON a HTML report (opcional)

```bash
# Instalar convertidor
npm install -D @k6io/html-report

# Generar reporte HTML
node node_modules/@k6io/html-report/dist/index.js results/pico-*.json
```

### Integración con Grafana Cloud (opcional)

Si quieres cloud dashboards automáticos:
```bash
# 1. Crear cuenta en Grafana Cloud
# 2. Generar token
# 3. Ejecutar con token
k6 login cloud  # Te pide el token
k6 cloud scenarios/pico.js  # Sube resultados a cloud
```

---

## 🔧 Troubleshooting

### Error: "k6 command not found"
```bash
# Solución A: Instalar k6
npm install -g k6

# Solución B: Usar Docker
K6_DOCKER=true ./run.sh baseline
```

### Error: "Connection refused" (localhost:8080)
```bash
# Verificar que backend está corriendo
curl http://localhost:8080/api/v1/health

# O especificar URL correcta
API_HOST=your-api-host:port ./run.sh baseline
```

### Error: "Login failed" (401)
```bash
# Test data no existe en BD
# Ejecutar script de seed (ver Paso 2 arriba)

# O verificar credenciales en config.js
```

### Alto CPU/Memoria en cliente k6
```bash
# Reducir VUs o duración
./run.sh baseline --vus 5 --duration 2m

# k6 by default usa ~50MB RAM + CPU per VU
# 100 VUs = ~5GB RAM + high CPU normal
```

---

## 📝 Workflow Recomendado

### Antes de Elecciones (1-2 semanas previas)

1. **Día 1-2: Baseline**
   ```bash
   ./run.sh baseline
   # ✓ Verificar sistema sano
   ```

2. **Día 3-4: Ramp-up & Pico**
   ```bash
   ./run.sh rampup
   ./run.sh pico
   # ✓ Identificar bottlenecks
   # ✓ Verificar SLAs
   ```

3. **Día 5: Análisis & Fixes**
   - Aumentar replicas si CPU > 70%
   - Tuning pool si failures > 5%
   - Implementar caché si queries/sec muy alta

4. **Día 6-7: Retest después de fixes**
   ```bash
   ./run.sh pico
   # ✓ Verificar mejoras
   ```

5. **Semana 2: Stress & Contingency**
   ```bash
   ./run.sh stress
   # ✓ Entender límites
   # ✓ Plan B si picos > 150 VUs
   ```

### Semana de Elecciones

```bash
# Monitoreo en tiempo real (opcional con Grafana)
# Ejecutar load tests en paralelo con votación
# Capturar métricas de producción
# Documentar picos, errores, latencias
```

---

## 📚 Documentación Completa

Análisis detallado en: `/INFRASTRUCTURE_RESILIENCE_PLAN.md`
- Endpoints críticos analizados
- Escenarios de carga realistas
- Capacidad actual del sistema
- Recomendaciones por prioridad

---

## 📞 Support

Para issues o preguntas:
1. Revisar `INFRASTRUCTURE_RESILIENCE_PLAN.md` (análisis profundo)
2. Verificar `config.js` para ajustar SLAs/VUs
3. Consultar logs en `results/` (JSON)

---

## 📋 Checklist Pre-Launch

- [ ] k6 instalado y verificado (`k6 version`)
- [ ] Test data seedeado en BD
- [ ] Backend corriendo en localhost:8080 (o API_HOST configurado)
- [ ] Baseline test PASS (failures 0%, p95 < 1.5s)
- [ ] Pico test ejecutado y analizado
- [ ] Resultados documentados
- [ ] Fixes aplicados si SLAs fallaron
- [ ] Retest después de fixes
- [ ] Stress test para entender breaking point
- [ ] Equipo avisado de load test schedule

---

**Created:** 2026-11-05  
**Status:** Ready for deployment  
**Next:** Execute baseline test and validate setup
