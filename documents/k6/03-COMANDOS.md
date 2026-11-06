# 03: Todos los Comandos k6

**Referencia rápida de comandos**

---

## Ejecutar via Bash Script (Recomendado)

```bash
cd frontend/tests/load

# Baseline test (10 VUs, 5 min)
./run.sh baseline

# Ramp-up test (0→100 VUs)
./run.sh rampup

# Listar actas (coordinator, 20 VUs)
./run.sh listar-actas

# Registrar acta (observer, 30 VUs)
./run.sh registrar-acta

# Pico electoral (0→150 VUs) — RECOMENDADO
./run.sh pico
# o alias:
./run.sh mixed

# Stress test (200 VUs, breaking point)
./run.sh stress

# Todos los tests en secuencia (~2.5h)
./run.sh all

# Help
./run.sh help
```

---

## Ejecutar Directamente con k6

```bash
cd frontend/tests/load

# Baseline
k6 run scenarios/login.js --vus 10 --duration 5m

# Ramp-up
k6 run scenarios/registrar-acta.js \
  --stage 2m:20vus \
  --stage 5m:50vus \
  --stage 8m:100vus \
  --stage 5m:0vus

# Pico (con stages)
k6 run scenarios/mixed-realistic.js

# Con output a JSON
k6 run scenarios/mixed-realistic.js \
  --out json=results/pico-$(date +%Y%m%d-%H%M%S).json

# Con custom API host
k6 run scenarios/login.js \
  -e API_HOST=api.example.com:8080

# Con sleep duration
k6 run scenarios/login.js \
  -e SLEEP_DURATION=3
```

---

## Ejecutar via npm Scripts

```bash
npm run test:baseline
npm run test:rampup
npm run test:listar-actas
npm run test:registrar-acta
npm run test:pico
npm run test:stress
npm run test:all

# Generate HTML report
npm run report
```

---

## Ejecutar via Docker

```bash
# Sin instalación local de k6
docker run --rm \
  -v $(pwd):/workspace \
  -e API_HOST=host.docker.internal:8080 \
  grafana/k6:latest \
  run /workspace/scenarios/login.js --vus 10 --duration 5m

# O usar script con Docker
export K6_DOCKER=true
./run.sh pico
```

---

## Opciones Comunes de k6

```bash
# Usuarios virtuales
k6 run scenarios/login.js --vus 10

# Duración del test
k6 run scenarios/login.js --duration 5m

# Iterations (si en lugar de duration)
k6 run scenarios/login.js --vus 10 --iterations 100

# Ramp-up (stages)
k6 run scenarios/login.js \
  --stage 1m:10vus \
  --stage 2m:20vus \
  --stage 1m:0vus

# Output format
k6 run scenarios/login.js --out json=results/test.json
k6 run scenarios/login.js --out csv=results/test.csv
k6 run scenarios/login.js --out influxdb=http://localhost:8086/k6

# Environment variables
k6 run scenarios/login.js \
  -e API_HOST=localhost:8080 \
  -e SLEEP_DURATION=2

# Tags (for filtering metrics)
k6 run scenarios/login.js --tag page:login --tag team:backend

# Help
k6 run --help
k6 version
```

---

## Ver Resultados

```bash
# Listar archivos de resultados
ls -lh frontend/tests/load/results/

# Leer JSON
cat results/pico-*.json | jq '.metrics.http_req_duration'

# Filtrar por endpoint
cat results/pico-*.json | jq '.metrics | select(.http_req_duration.endpoint=="login")'

# Contar requests
cat results/pico-*.json | jq '.metrics.http_reqs'

# Ver failure rate
cat results/pico-*.json | jq '.metrics.http_req_failed'
```

---

## Generar Reports

```bash
# HTML report (requiere @k6io/html-report)
npm run report

# O manual
node node_modules/@k6io/html-report/dist/index.js results/pico-*.json
```

---

## Monitoreo en Vivo (Cloud Opcional)

```bash
# Login a Grafana Cloud
k6 login cloud

# Ejecutar test y subir resultados a cloud
k6 cloud scenarios/pico.js
```

---

## Debugging

```bash
# Verbose output
k6 run scenarios/login.js --vus 1 --duration 10s -v

# Solo un VU para debugging
k6 run scenarios/login.js --vus 1 --iterations 1

# Con logs
k6 run scenarios/login.js -v 2>&1 | tee debug.log

# Check script syntax sin ejecutar
k6 run scenarios/login.js --no-setup --no-teardown --iterations 0
```

---

## Usar Variables de Entorno

```bash
# En terminal
export API_HOST=api.example.com:8080
export SLEEP_DURATION=2
./run.sh pico

# O inline
API_HOST=api.example.com:8080 SLEEP_DURATION=2 ./run.sh pico

# En script k6
k6 run scenarios/login.js -e API_HOST=api.example.com:8080
```

---

## Ejecutar Múltiples Veces

```bash
# Loop 10 veces
for i in {1..10}; do
  echo "Run $i of 10"
  ./run.sh baseline
  sleep 60  # esperar 1 min entre runs
done

# Con timestamps
for i in {1..10}; do
  echo "Run $i: $(date)"
  ./run.sh pico -e RUN_ID=$i
  sleep 300
done
```

---

## Performance Tweaks

```bash
# Ejecutar en background
./run.sh pico > results/pico.log 2>&1 &

# Limitar CPU/Memory (si corre muchos VUs)
k6 run scenarios/pico.js \
  --system-tags=proto,subproto,status,error,error_code,vu,iteration

# Reducir verbosidad
k6 run scenarios/pico.js --logformat json 2>/dev/null
```

---

## Cleanup

```bash
# Borrar todos los resultados
rm -f frontend/tests/load/results/*.json

# Borrar un resultado específico
rm frontend/tests/load/results/pico-20261105-142300.json
```

---

## Comparar Runs

```bash
# Después de runs múltiples
cd results

# Extraer p95 latency de cada run
for f in pico-*.json; do
  echo "File: $f"
  cat $f | jq '.metrics.http_req_duration.values.p95'
done

# Generar CSV para análisis
jq -r '[.metrics.http_req_duration.values.p95, .metrics.http_req_failed.values.rate] | @csv' \
  pico-*.json > comparison.csv
```

---

## Troubleshooting Commands

```bash
# Verificar k6 instalado
k6 version

# Verificar backend disponible
curl http://localhost:8080/api/v1/health

# Verificar test data
psql -c "SELECT COUNT(*) FROM usuario WHERE email LIKE '%@electoral.ec';"

# Ver logs en tiempo real
tail -f results/pico-*.json | jq '.metrics.http_req_duration'

# Kill hung k6 process
pkill -f "k6 run"

# Kill Docker k6
docker kill $(docker ps | grep k6 | awk '{print $1}')
```

---

## Cheatsheet Rápido

```
Baseline:     ./run.sh baseline
Pico:         ./run.sh pico
Todos:        ./run.sh all
Help:         ./run.sh help

API custom:   API_HOST=api.com:8080 ./run.sh pico
Docker:       K6_DOCKER=true ./run.sh pico
Report:       npm run report

Ver results:  cat results/pico-*.json | jq
```

---

**Last Updated:** 2026-11-05
