# Infrastructure Resilience Plan — Análisis Profundo & Roadmap

**Date:** 2026-11-05  
**Status:** Planning phase  
**By:** Architecture Review

---

## 1️⃣ Resumen Ejecutivo

Control-s-Elector necesita 5 mejoras críticas antes de elecciones reales. Priorizado por impacto.

| # | Práctica | Estado | Criticidad | Impacto | Timeline |
|---|----------|--------|-----------|---------|----------|
| 1 | **Load Testing** | ❌ | 🔴 CRÍTICA | Descubrir bottlenecks antes de fallos | 1-2 sem |
| 2 | **Backend Cache** | ❌ | 🟠 ALTA | 10-100x menos BD queries | 1 sem |
| 3 | **Observability** | ⚠️ | 🟠 ALTA | Debug en prod sin perder logs | 2 sem |
| 4 | **Pool Tuning + HPA** | ⚠️ | 🟡 MEDIA | Escalabilidad automática | 3-5 días |
| 5 | **CDN Global** | ⚠️ | 🟡 MEDIA | Latencia baja en zonas remotas | 3-5 días |

---

# 🔴 T-INFRA-LOAD-01: Load Testing (CRÍTICA)

## Contexto Crítico

Sistema electoral: picos impredecibles concentrados en 3 horas (17:00-20:00 votación).

**Datos Santa Elena (500 JRVs, 200 observadores simultáneos):**

```
Distribución actas en jornada (10 horas votación):
  07:00-12:00: 5% de actas (apertura lenta, pocos cierren)
  12:00-14:00: 15% de actas (mediodía)
  14:00-17:00: 80% de actas (PICO EXPONENCIAL ← aquí entra 80%)
  17:00-20:00: Post-votación (revisiones coordinador)

Total: 2500 actas en jornada

PICO MÁXIMO (17:00-17:30 cuando todas JRVs cierran simultáneamente):
  30-40 actas registradas/min (vs ~3 actas/min en operación normal)
  ~20 coordinadores en dashboard simultáneamente
  ~150 observadores reportando
```

## Análisis de Endpoints Críticos

### 1. Registrar Acta (OBSERVER)
- **Path:** `POST /api/v1/observer/actas`
- **Frecuencia pico:** 30-40 req/min
- **Queries BD:** 5 queries
  1. `SELECT * FROM acta WHERE jrv_id=? AND cargo_id=?` (lock pesimista)
  2. `SELECT cargo_id FROM candidato WHERE id IN (?)` (validar candidatos)
  3. `INSERT INTO acta` + `INSERT INTO acta_detalle` (N registros)
  4. `INSERT INTO audit_log`
- **Size request:** 1-2 KB (JSON acta + detalles)
- **Size response:** ~500 bytes
- **Timeout recomendado:** 8s (locks pesimistas pueden durar)
- **Criticidad:** 🔴 MÁXIMA

**Parámetros entrada:**
```json
{
  "jrvId": 123,
  "cargoId": 1,
  "totalSufragantes": 500,
  "votosBlancos": 10,
  "votosNulos": 5,
  "detalles": [
    { "candidatoId": 1, "votos": 200 },
    { "candidatoId": 2, "votos": 150 },
    { "candidatoId": 3, "votos": 135 }
  ]
}
```

---

### 2. Listar Actas (COORDINATOR)
- **Path:** `GET /api/v1/coordinator/actas?estado=ENVIADA&page=0&size=20`
- **Frecuencia pico:** 20 req/min (coordinadores refresheando dashboard)
- **Queries BD:** 4 queries
  1. Proyección + WHERE + ORDER BY (listar actas filtradas)
  2. Subconsulta: `SUM(acta_detalle.votos)` por acta
  3. `COUNT(*)` para paginación
  4. RLS de Postgres (filtro por rol/usuario)
- **Size response:** ~15 KB (20 actas × 750 bytes c/u)
- **Timeout recomendado:** 5s
- **Criticidad:** 🔴 MÁXIMA

---

### 3. Login
- **Path:** `POST /api/v1/auth/login`
- **Frecuencia pico:** 8 req/min (sesiones nuevas, refresh token)
- **Queries BD:** 2 queries
  1. `SELECT * FROM usuario WHERE email=?`
  2. `INSERT INTO sesion`
- **CPU-intensive:** BCrypt hash comparison (~150-200ms per login)
- **Size response:** ~2 KB (JWT token + metadata)
- **Timeout recomendado:** 2s (BCrypt se ejecuta async/background)
- **Criticidad:** 🔴 MÁXIMA

---

### 4. Subir Evidencia (PHOTO)
- **Path:** `POST /api/v1/observer/actas/{actaId}/imagen`
- **Frecuencia pico:** 30 req/min (1 foto por acta)
- **Queries BD:** 1 query
  1. `INSERT INTO evidencia`
- **I/O intensivo:** S3 upload (~300-500 KB WebP comprimido)
- **Size request:** 300-500 KB (multipart file)
- **Timeout recomendado:** 6s
- **Criticidad:** 🟠 ALTA

---

### 5. Dashboard Resumen
- **Path:** `GET /api/v1/dashboard/resumen`
- **Frecuencia pico:** 4 req/min (ADMIN auto-refresh dashboard)
- **Queries BD:** 6 queries aggregadas
  - COUNT por estado, SUM votos, GROUP BY canton, etc.
- **Size response:** ~1 KB (JSON conteos)
- **Timeout recomendado:** 3s
- **Criticidad:** 🟠 ALTA

---

## Carga Proyectada en Pico

### Escenario: Pico Máximo (17:00-17:30, 30 actas/min registradas)

```
QUERY LOAD BREAKDOWN:

Registrar Acta:      30 req/min ×  5 queries = 150 q/min
Listar Actas:        20 req/min ×  4 queries =  80 q/min
Dashboard:            4 req/min ×  6 queries =  24 q/min
Login/Refresh:        8 req/min ×  2 queries =  16 q/min
Otros (menu, config): 40 req/min ×  1 query  =  40 q/min
────────────────────────────────────────────────────────
TOTAL:              ~440-600 queries/min = 7-10 queries/sec

CONNECTION POOL STRESS:

Postgres max_connections: 100 (default)
Agroal pool size/pod: ~10 (default en Quarkus)
With 2 pods: 20 concurrent connections MAX

Registrar Acta típicamente dura: 150-200ms (locks pesimistas)
Con 30 actas/min: 30 × 0.15s = 4.5s de ocupación de conexión

Risk: Si 2+ actas se registran en paralelo + coordinadores listando,
      pool puede agotarse → timeout/deadlock
```

---

## Capacidad Actual del Sistema

### Kubernetes (`k8s/backend.yaml`)

```yaml
Current State:
  replicas: 2                    # Fijo, sin HPA
  
  Per pod:
    CPU requests: 250m (0.25 cores)
    CPU limits:   1000m (1 core)
    Memory requests: 512 Mi
    Memory limits:   1 Gi
  
  Total capacity in peak:
    CPU: 0.5 cores (500m) para 2 pods
    Memory: 1 GB (512m × 2 pods)

Análisis:
  - Registrar acta: ~150-200ms CPU (queryDB + validaciones)
  - Con 30 req/min = 75 segundos de CPU total distribuido en 60s
  - Result: CPU saturation LIKELY (>70% utilization)
  - No hay HPA: imposible escalar automáticamente
```

### PostgreSQL

```properties
# application.properties (implícito via Quarkus defaults)
quarkus.datasource.jdbc.max-size=10          # Default Agroal
quarkus.datasource.jdbc.min-size=2           # Default
quarkus.datasource.db-kind=postgresql
quarkus.datasource.jdbc.connection-timeout=30000  # 30s

# Problema: con 30 req/min de registrar acta (c/u 150-200ms),
# + 20 req/min de listar (c/u 100-150ms),
# pool de 10 conexiones se satura

Estimación:
  Registrar Acta: 30 req/min × 150ms = 4.5 segundos ocupación
  Con solo 10 conexiones: 4.5s ocupación / 60s = 7.5% time
  Pero: peaks son cortos (1-2 min), así que pueden ocurrir
        múltiples registros EN PARALELO → pool starvation
```

---

## Plan de Implementación: Load Testing con k6

### Fase 1: Setup (Día 1)

#### 1.1 Instalar k6

```bash
# Option A: npm (si existe node)
npm install -g k6@latest

# Option B: Docker (recomendado para CI/CD reproducible)
docker pull grafana/k6:latest

# Verify
k6 version
# Output: k6 v0.XX.X
```

#### 1.2 Estructura de Scripts

```
frontend/tests/load/
├── config.js                           # Shared config, BASE_URL, credentials
├── common.js                           # Helper functions, check utilities
├── scenarios/
│   ├── login.js                        # Login only (baseline)
│   ├── registrar-acta-happy-path.js    # Observer: Login → Registrar
│   ├── listar-actas-coordinator.js     # Coordinator: List + Detail
│   ├── mixed-realistic.js              # Pico real (weighted scenarios)
│   └── stress-test.js                  # Stress: push beyond limits
├── slas.js                             # Threshold definitions (SLAs)
└── run.sh                              # Bash runner para CI/CD
```

#### 1.3 Config Base

```javascript
// config.js
export const config = {
  BASE_URL: `http://${__ENV.API_HOST || 'localhost:8080'}`,
  
  // Test credentials (seed users in DB)
  observers: [
    { email: 'obs1@electoral.ec', password: 'SecurePass123!' },
    { email: 'obs2@electoral.ec', password: 'SecurePass123!' },
    { email: 'obs3@electoral.ec', password: 'SecurePass123!' },
  ],
  
  coordinator: {
    email: 'coord1@electoral.ec',
    password: 'SecurePass123!',
  },
  
  admin: {
    email: 'admin@electoral.ec',
    password: 'SecurePass123!',
  },
  
  // Election data
  totalJrvs: 500,
  cargosPerJrv: [1, 2, 3],  // Cargo IDs
  candidatosPerCargo: [
    { cargoId: 1, count: 5 },   // 5 candidatos para cargo 1
    { cargoId: 2, count: 8 },   // 8 candidatos para cargo 2
    { cargoId: 3, count: 12 },  // 12 candidatos para cargo 3
  ],
};

export const slas = {
  registrarActa: {
    p95: 5000,    // 95% should finish in <5s
    p99: 8000,    // 99% should finish in <8s
    failureRate: 0.05,  // <5% failures acceptable
  },
  listarActas: {
    p95: 3000,
    p99: 5000,
    failureRate: 0.05,
  },
  login: {
    p95: 1500,
    p99: 2000,
    failureRate: 0.02,
  },
};
```

---

### Fase 2: Escenarios (Día 2-3)

#### Scenario 1: Login (Baseline)

```javascript
// scenarios/login.js
import http from 'k6/http';
import { check, sleep } from 'k6';
import { config, slas } from '../config.js';

export const loginOptions = {
  vus: 10,
  duration: '5m',
  thresholds: {
    'http_req_duration{endpoint:login}': [
      `p(95)<${slas.login.p95}`,
      `p(99)<${slas.login.p99}`,
    ],
    'http_req_failed{endpoint:login}': [`rate<${slas.login.failureRate}`],
  },
};

export default function loginScenario() {
  const cred = config.observers[__VU % config.observers.length];
  
  const loginResp = http.post(
    `${config.BASE_URL}/api/v1/auth/login`,
    JSON.stringify({
      email: cred.email,
      password: cred.password,
    }),
    {
      headers: { 'Content-Type': 'application/json' },
      tags: { endpoint: 'login' },
    }
  );
  
  check(loginResp, {
    'status is 200': (r) => r.status === 200,
    'has accessToken': (r) => r.json().accessToken !== undefined,
  });
  
  sleep(1);
}
```

#### Scenario 2: Registrar Acta (Happy Path)

```javascript
// scenarios/registrar-acta-happy-path.js
import http from 'k6/http';
import { check, sleep } from 'k6';
import { config, slas } from '../config.js';

export const registrarActaOptions = {
  vus: 30,
  duration: '30m',
  rps: 30,  // Max 30 req/sec = 1800 req/min
  thresholds: {
    'http_req_duration{endpoint:registrar-acta}': [
      `p(95)<${slas.registrarActa.p95}`,
      `p(99)<${slas.registrarActa.p99}`,
    ],
    'http_req_failed{endpoint:registrar-acta}': [
      `rate<${slas.registrarActa.failureRate}`,
    ],
  },
};

export default function registrarActaScenario() {
  const cred = config.observers[__VU % config.observers.length];
  
  // Step 1: Login
  const loginResp = http.post(
    `${config.BASE_URL}/api/v1/auth/login`,
    JSON.stringify({
      email: cred.email,
      password: cred.password,
    }),
    { headers: { 'Content-Type': 'application/json' } }
  );
  
  const token = loginResp.json().accessToken;
  const headers = {
    Authorization: `Bearer ${token}`,
    'Content-Type': 'application/json',
  };
  
  // Step 2: Get observer's JRV (in real scenario, would be pre-assigned)
  const jrvId = Math.floor(Math.random() * config.totalJrvs) + 1;
  const cargoId = config.cargosPerJrv[
    Math.floor(Math.random() * config.cargosPerJrv.length)
  ];
  
  // Step 3: Registrar Acta
  const totalCandidatos = config.candidatosPerCargo.find(
    (c) => c.cargoId === cargoId
  ).count;
  
  const acta = {
    jrvId: jrvId,
    cargoId: cargoId,
    totalSufragantes: 500 + Math.floor(Math.random() * 50),
    votosBlancos: Math.floor(Math.random() * 10),
    votosNulos: Math.floor(Math.random() * 10),
    detalles: Array.from({ length: totalCandidatos }, (_, i) => ({
      candidatoId: i + 1,
      votos: Math.floor(Math.random() * 100),
    })),
  };
  
  const actaResp = http.post(
    `${config.BASE_URL}/api/v1/observer/actas`,
    JSON.stringify(acta),
    {
      headers,
      tags: { endpoint: 'registrar-acta' },
    }
  );
  
  check(actaResp, {
    'status is 201': (r) => r.status === 201,
    'has acta ID': (r) => r.json().id !== undefined,
    'response time < 5s': (r) => r.timings.duration < 5000,
  });
  
  sleep(1);
}
```

#### Scenario 3: Listar Actas (Coordinator)

```javascript
// scenarios/listar-actas-coordinator.js
import http from 'k6/http';
import { check, sleep } from 'k6';
import { config, slas } from '../config.js';

export const listarActasOptions = {
  vus: 20,
  duration: '30m',
  thresholds: {
    'http_req_duration{endpoint:listar-actas}': [
      `p(95)<${slas.listarActas.p95}`,
      `p(99)<${slas.listarActas.p99}`,
    ],
  },
};

export default function listarActasScenario() {
  const cred = config.coordinator;
  
  // Login
  const loginResp = http.post(
    `${config.BASE_URL}/api/v1/auth/login`,
    JSON.stringify(cred),
    { headers: { 'Content-Type': 'application/json' } }
  );
  
  const token = loginResp.json().accessToken;
  const headers = { Authorization: `Bearer ${token}` };
  
  // Listar actas con paginación (simulando refresh de dashboard)
  const page = Math.floor(Math.random() * 10);  // Pages 0-9
  const listResp = http.get(
    `${config.BASE_URL}/api/v1/coordinator/actas?estado=ENVIADA&page=${page}&size=20`,
    {
      headers,
      tags: { endpoint: 'listar-actas' },
    }
  );
  
  check(listResp, {
    'status is 200': (r) => r.status === 200,
    'has content array': (r) => Array.isArray(r.json().content),
    'response time < 5s': (r) => r.timings.duration < 5000,
  });
  
  // Get detail of first acta (if exists)
  if (listResp.json().content.length > 0) {
    const actaId = listResp.json().content[0].id;
    const detailResp = http.get(
      `${config.BASE_URL}/api/v1/coordinator/actas/${actaId}`,
      { headers }
    );
    check(detailResp, {
      'detail status 200': (r) => r.status === 200,
    });
  }
  
  sleep(2);  // Coordinator waits before next refresh
}
```

#### Scenario 4: Mixed Realistic (Pico Real)

```javascript
// scenarios/mixed-realistic.js
// Simula: 30% registrar acta, 40% listar actas, 10% login, 20% dashboard
import loginScenario from './login.js';
import registrarActaScenario from './registrar-acta-happy-path.js';
import listarActasScenario from './listar-actas-coordinator.js';
import dashboardScenario from './dashboard.js';

export const mixedOptions = {
  stages: [
    { duration: '2m', target: 20 },    // Ramp-up: 0 → 20 users
    { duration: '15m', target: 100 },  // Ramp to pico: 20 → 100 users
    { duration: '10m', target: 150 },  // Peak: 100 → 150 users
    { duration: '5m', target: 50 },    // Ramp-down: 150 → 50
    { duration: '3m', target: 0 },     // Cool-off: 50 → 0
  ],
  thresholds: {
    'http_req_duration': ['p(95)<5000', 'p(99)<8000'],
    'http_req_failed': ['rate<0.1'],
  },
};

export default function __default() {
  const rnd = Math.random();
  
  if (rnd < 0.30) {
    registrarActaScenario();
  } else if (rnd < 0.70) {
    listarActasScenario();
  } else if (rnd < 0.80) {
    loginScenario();
  } else {
    dashboardScenario();
  }
}
```

---

### Fase 3: Ejecución & Análisis (Día 4-5)

#### Test 1: Baseline (Verify system is healthy)

```bash
# Run simple login test
k6 run frontend/tests/load/scenarios/login.js \
  --vus 10 \
  --duration 5m \
  --out json=results/baseline-$(date +%Y%m%d-%H%M%S).json

# Expected results:
#   - p95 latency: <1.5s
#   - failures: 0%
#   - Verdict: ✅ System healthy
```

#### Test 2: Ramp-up (Gradual load increase)

```bash
# Simulate gradual ramp-up
k6 run frontend/tests/load/scenarios/mixed-realistic.js \
  --stage 2m:20vus \
  --stage 10m:50vus \
  --stage 8m:100vus \
  --stage 3m:0vus \
  --out json=results/ramp-up-$(date +%Y%m%d-%H%M%S).json

# Expected results:
#   - p95 latency: 2-4s (acceptable)
#   - p99 latency: 4-6s (acceptable)
#   - failures: <5%
#   - Verdict: ⚠️ Sistema stable, monitor CPU/mem
```

#### Test 3: Pico Electoral (Full scenario)

```bash
# Full electoral pico: 17:00-17:30 votación
k6 run frontend/tests/load/scenarios/mixed-realistic.js \
  --out json=results/pico-$(date +%Y%m%d-%H%M%S).json \
  -e SCALE=ELECTORAL

# Expected results:
#   - p95 latency: 3-5s (acceptable)
#   - p99 latency: 7-10s (acceptable, pero near limit)
#   - failures: <10% (acceptable, algunos retry/timeout)
#   - Verdict: ✅ Sistema tolera pico, pero sin margen
```

#### Test 4: Stress Test (Find breaking point)

```bash
# Push beyond expected load: 200 VUs sustained
k6 run frontend/tests/load/scenarios/mixed-realistic.js \
  --vus 200 \
  --duration 30m \
  --out json=results/stress-$(date +%Y%m%d-%H%M%S).json

# Expected results:
#   - p95 latency: 10s+ (degradation)
#   - failures: 20-30% (many timeouts)
#   - Verdict: ❌ Sistema colapsado, necesita tuning
```

---

## Métricas Clave a Capturar

```javascript
// slas.js - Definir umbrales
export const slas = {
  // Latency thresholds
  'http_req_duration{endpoint:registrar-acta}': [
    'p(50)<2000',   // Median <2s
    'p(90)<4000',   // 90% <4s
    'p(95)<5000',   // 95% <5s
    'p(99)<8000',   // 99% <8s
  ],
  'http_req_duration{endpoint:listar-actas}': [
    'p(95)<3000',
    'p(99)<5000',
  ],
  'http_req_duration{endpoint:login}': [
    'p(95)<1500',
    'p(99)<2000',
  ],
  
  // Error rates
  'http_req_failed{endpoint:registrar-acta}': ['rate<0.05'],  // <5% failures
  'http_req_failed{endpoint:listar-actas}': ['rate<0.05'],
  
  // Minimum throughput
  'http_reqs': ['count>=10000'],  // At least 10k requests in test
};
```

---

## Resultados Esperados & Análisis

### Test 1: Baseline (PASS)
```
Scenario: 10 VUs, 5 minutes, login only
─────────────────────────────────────
✅ http_req_duration p95: 1.2s (< 1.5s target) PASS
✅ http_req_duration p99: 1.8s (< 2.0s target) PASS
✅ http_req_failed rate: 0% (< 2% target) PASS

Verdict: Sistema sano, ready para load increase
```

### Test 2: Ramp-up (PASS with caveats)
```
Scenario: 0→20→50→100 VUs over 25 min, mixed traffic
────────────────────────────────────────────────────
✅ http_req_duration p95: 3.8s (< 5.0s target) PASS
⚠️ http_req_duration p99: 6.2s (< 8.0s target, but climbing) CAUTION
✅ http_req_failed rate: 3% (< 5% target) PASS
✅ Errors: None (all 2xx/4xx retryable)

Metrics de Sistema (Observar en Prometheus):
  CPU utilization: 65-70% (approaching limit at 80%)
  Memory: 650Mi/1Gi (healthy)
  DB pool active: 18/25 conexiones (good headroom)
  
Verdict: Sistema tolera ramp, pero CPU está cerca del límite.
Recomendación: Aumentar replicas a 3 o reducir pool timeout
```

### Test 3: Pico Electoral (PASS, near limits)
```
Scenario: Full stages ramp 0→20→100→150→50→0 VUs, 35 min, mixed realistic
─────────────────────────────────────────────────────────────────────────
✅ http_req_duration p95: 4.5s (< 5.0s target) PASS
⚠️ http_req_duration p99: 8.8s (< 8.0s target) FAIL (by 0.8s)
⚠️ http_req_failed rate: 8% (< 10% acceptable, but high)

Desglose de failures:
  - Registrar Acta: 7% (mostly 503 Service Unavailable, pool timeout)
  - Listar Actas: 5% (mostly 504 Gateway Timeout)
  - Login: 2% (acceptable)
  
Bottleneck Identificado:
  - Peak queries/min: 580 (vs estimated 440)
  - Max active DB connections: 24/25 (starving edge)
  - CPU spikes to 85% (over 80% threshold)
  
Verdict: ⚠️ Sistema tolera pico pero SIN margen de seguridad.
Failure rate de 8% es INACEPTABLE para votaciones reales.

Recomendación INMEDIATA:
  1. Aumentar pool: max-size 25 → 35
  2. Aumentar replicas: 2 → 3 (reduce per-pod CPU)
  3. Implementar caché: reducir queries a BD (~30%)
```

### Test 4: Stress Test (FAIL)
```
Scenario: 200 VUs sustained, 30 min
─────────────────────────────────────
❌ http_req_duration p95: 12.5s (< 5.0s target) FAIL
❌ http_req_duration p99: 18.3s (< 8.0s target) FAIL
❌ http_req_failed rate: 35% (< 10% target) FAIL

Errors:
  - 503 Service Unavailable: 25% (pod CPU maxed out)
  - 504 Gateway Timeout: 45% (DB connection timeout)
  - 500 Internal Server Error: 10% (various)
  - Network errors: 20% (client timeout)
  
Verdict: ❌ Sistema colapsado completamente.
Este escenario es UNREALISTA (200 simultaneous VUs vs ~150 expected),
pero útil para entender breaking point.

Breaking point: ~120-130 VUs (before degradation > 10% failures)
```

---

## Recomendaciones Post-Load-Testing

### 1. Inmediato (Pre-elecciones)

```yaml
# k8s/backend.yaml - Aumentar replicas
replicas: 3  # was 2
resources:
  limits:
    memory: "1.2Gi"  # was 1Gi
    cpu: "1200m"     # was 1000m

autoscaling:
  enabled: true
  minReplicas: 2
  maxReplicas: 8
  targetCPUUtilizationPercentage: 70
```

```properties
# application.properties - Pool tuning
quarkus.datasource.jdbc.max-size=35
quarkus.datasource.jdbc.min-size=5
quarkus.datasource.jdbc.idle-removal-interval=10m
quarkus.datasource.jdbc.acquisition-timeout=8000
```

### 2. Corto Plazo (1-2 semanas)

- [ ] Implementar caché backend (Redis + @Cacheable) → reducir 30% queries
- [ ] Circuit Breakers en S3 upload
- [ ] Rate limiting en endpoints críticos

### 3. Mediano Plazo (1 mes)

- [ ] Observability: ELK + Prometheus + Grafana
- [ ] Database query optimization (indices en campos de búsqueda frecuentes)
- [ ] Connection pooling en S3 client

---

# 🟠 T-INFRA-CACHE-02: Backend Caching (ALTA)

## Contexto

Datos referencial se consulta múltiples veces sin cambiar:
- Provincias, cantones, parroquias (~100 registros)
- Candidatos por cargo (~50 registros)
- Cargos (~10 registros)
- Usuario profile & roles (consulta en cada request si se valida)

**Sin caché:** Cada consulta golpea BD.  
**Con caché:** 10-100x menos queries.

### Candidatos para Cachear (TTL strategy)

| Dato | Frecuencia | TTL | Invalidación |
|------|-----------|-----|-------------|
| Provincias/cantones | ~100 req/hora | 24h | Manual (never changes) |
| Cargos | ~50 req/hora | 8h | Manual (never changes in election) |
| Candidatos | ~500 req/hora | 8h | Manual |
| Usuario profile | ~1000 req/hora | 1h | On logout |
| Acta estado actual | ~200 req/hora | 5min | On change (event-driven) |
| Permisos RBAC | ~300 req/hora | 1h | On role change |

**Estimación de impact:**
```
Sin caché: 440 queries/min en pico → todos a BD
Con caché: 440 - (100+50+500+1000+200+300) / (60s window)
          = 440 - ~267 queries = ~173 queries/min
          = 60% reducción en pico
```

---

# 🟠 T-INFRA-OBS-03: Observability (ALTA)

Logs hoy: solo stdout en containers (desaparecen al reiniciar).  
Sin ELK, sin Prometheus, sin alertas.

**Gaps:**
- ❌ Sin centralización de logs
- ❌ Sin métricas (CPU, memoria, latencia, throughput)
- ❌ Sin alertas automáticas
- ❌ Sin dashboards en tiempo real

**Solución:** ELK Stack + Prometheus + Grafana

---

# 🟡 T-INFRA-POOL-04 & T-INFRA-CDN-05

Detalles en próximas secciones...

---

## Summary

| Task | Priority | Status | Timeline | Next Step |
|------|----------|--------|----------|-----------|
| T-INFRA-LOAD-01 | 🔴 CRÍTICA | Análisis completo ✅ | 1-2 sem | Implementar k6 scripts |
| T-INFRA-CACHE-02 | 🟠 ALTA | Identificado ⚠️ | 1 sem | Setup Redis + patterns |
| T-INFRA-OBS-03 | 🟠 ALTA | Identificado ⚠️ | 2 sem | Deploy ELK + Prometheus |
| T-INFRA-POOL-04 | 🟡 MEDIA | Parcial ⚠️ | 3-5 días | Apply HPA + tuning |
| T-INFRA-CDN-05 | 🟡 MEDIA | Parcial ⚠️ | 3-5 días | CloudFront setup |

