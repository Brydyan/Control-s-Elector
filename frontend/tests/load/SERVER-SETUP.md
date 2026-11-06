# K6 Load Testing — Setup en Tu Servidor

**Aplicable a:** Servidores privados sin limitaciones de GitHub Actions  
**Status:** Listo para implementar  
**Last Updated:** 2026-11-05

---

## 📋 Overview

Configuración de:
1. **Nightly Pico Test** — 0→150 VUs, 30 min (2 AM UTC diariamente)
2. **Weekly Stress Test** — 200 VUs, 30 min (3 AM Sundays)
3. **Alertas** — Slack + Email
4. **Reportes** — HTML dashboard + CSV trends

---

## 🚀 Setup en tu Servidor

### Paso 1: Instalar k6

```bash
# Ubuntu/Debian
sudo apt-get update
sudo apt-get install -y k6

# Verificar
k6 version
```

### Paso 2: Crear directorio de scripts

```bash
mkdir -p /home/electoral/k6/{scripts,results,reports,logs}
chmod +x /home/electoral/k6/scripts/*.sh

# Copy scripts
cp frontend/tests/load/scripts/*.sh /home/electoral/k6/scripts/
```

### Paso 3: Crear archivo .env

```bash
# /home/electoral/k6/.env
API_HOST=localhost:8080
DB_HOST=localhost
DB_PORT=5432
DB_NAME=electoral_db
DB_USER=electoral_user
DB_PASSWORD=electoral_pass

# Notificaciones
SLACK_WEBHOOK=https://hooks.slack.com/services/YOUR/WEBHOOK/HERE
EMAIL_TO=ops@control.ttcs
EMAIL_FROM=k6-loadtest@control.ttcs
```

### Paso 4: Configurar Cron Jobs

```bash
# Abrir crontab
sudo crontab -e

# Agregar estas líneas:

# Nightly Pico Test — 2 AM UTC daily
0 2 * * * /home/electoral/k6/scripts/nightly-pico-test.sh >> /home/electoral/k6/logs/cron.log 2>&1

# Weekly Stress Test — 3 AM UTC, Sundays
0 3 * * 0 /home/electoral/k6/scripts/weekly-stress-test.sh >> /home/electoral/k6/logs/cron.log 2>&1

# Cleanup old logs (keep last 90 days)
0 4 * * * find /home/electoral/k6/logs -name "*.log" -mtime +90 -delete
```

**Ajustar times según tu zona horaria:**
- 2 AM UTC = 21:00 Ecuador (UTC-5)
- 3 AM UTC = 22:00 Ecuador

---

## 📧 Slack Integration

### Crear Incoming Webhook en Slack

1. Abre [Slack API Apps](https://api.slack.com/apps)
2. "Create New App" → "From scratch"
3. Name: "K6 Load Tests"
4. Select workspace
5. Left menu: "Incoming Webhooks" → Enable
6. "Add New Webhook to Workspace"
7. Select channel: #alerts (o crea una)
8. Copiar URL (comenzará con `https://hooks.slack.com/...`)

### En tu servidor

```bash
# Actualizar .env
SLACK_WEBHOOK=https://hooks.slack.com/services/YOUR/WEBHOOK/HERE
```

---

## 📧 Email Integration

### Opción A: Using SendGrid (recomendado)

```bash
# Install ssmtp + sendgrid
sudo apt-get install ssmtp

# Edit /etc/ssmtp/ssmtp.conf
root=k6-loadtest@control.ttcs
mailhub=smtp.sendgrid.net:587
AuthUser=apikey
AuthPass=SG.xxxxxxxxxxxxx
UseSTARTTLS=YES
```

### Opción B: Using local postfix

```bash
sudo apt-get install postfix
# Configure as: Internet Site
# System mail name: control.ttcs
```

### Test

```bash
echo "Test message" | mail -s "K6 Test" ops@control.ttcs
```

---

## 📊 Monitorear Resultados

### Ver últimos resultados

```bash
# Últimas 10 ejecutiones
ls -lt /home/electoral/k6/reports/pico-*.txt | head -10

# Ver reporte más reciente
cat /home/electoral/k6/reports/pico-*.txt | head -50
```

### Logs de ejecución

```bash
# Ver cron logs
tail -100 /home/electoral/k6/logs/cron.log

# Ver logs específicos
tail -50 /home/electoral/k6/logs/nightly-20261105-020000.log
```

### Resultados JSON brutos

```bash
# Ver todas las ejecuciones
ls /home/electoral/k6/results/

# Analizar específica ejecución
jq '.metrics.http_req_duration.values' \
  /home/electoral/k6/results/pico-nightly-20261105-020000.json
```

---

## 🎯 Interpretar Reportes

### Reporte Nightly (Pico)

```
p95 Latency: 4200ms (threshold: 5000ms) ✅
p99 Latency: 6800ms (threshold: 8000ms) ✅
Failure Rate: 3% (threshold: <10%) ✅

OVERALL: ✅ PASS
```

✅ = Sistema saludable durante cierre de votación

---

```
p95 Latency: 5800ms (threshold: 5000ms) ❌
p99 Latency: 9200ms (threshold: 8000ms) ❌
Failure Rate: 15% (threshold: <10%) ❌

OVERALL: ❌ FAIL
```

❌ = Necesita optimización antes de producción

---

### Reporte Weekly (Stress)

```
Status: ✅ SUSTAINABLE
System handling 200 VUs reasonably
Failures < 10%
Good headroom for elections
```

✅ = Capacidad suficiente

---

```
Status: ❌ BREAKING POINT EXCEEDED
Failure rate > 50%
System unable to handle 200 VUs

Recommendations:
1. Scale backend (add replicas)
2. Optimize database queries
3. Implement caching
```

❌ = Escalar sistema antes de producción

---

## 🔧 Troubleshooting

### K6 command not found

```bash
# Instalar manualmente
sudo apt-get install -y gnupg2 apt-transport-https

curl -s https://dl.k6.io/key.gpg | sudo apt-key add -
echo "deb https://dl.k6.io/deb stable main" | sudo tee /etc/apt/sources.list.d/k6-stable.list

sudo apt-get update
sudo apt-get install k6
```

### Backend no responde

```bash
# Verificar health endpoint
curl http://localhost:8080/actuator/health

# Si falla, revisar:
docker ps  # ¿Container corriendo?
docker logs <container-id>  # ¿Errores?
```

### Database no conecta

```bash
# Test connection
PGPASSWORD=electoral_pass psql -h localhost -U electoral_user -d electoral_db -c "SELECT 1"

# Si falla:
sudo systemctl status postgresql
sudo systemctl restart postgresql
```

### Slack notification no llega

```bash
# Verificar webhook
curl -X POST "$SLACK_WEBHOOK" \
  -H 'Content-Type: application/json' \
  -d '{"text":"Test message"}'

# Si error: revisar URL, permisos del canal
```

---

## 📈 Trending & Analysis

### Generar reporte de tendencias mensuales

```bash
cat /home/electoral/k6/reports/stress-trends-$(date +%Y-%m).txt
```

Output ejemplo:
```
STRESS TEST TRENDS — November 2026
═════════════════════════════════════════════════════════

2026-11-01: p95=4200ms, failures=2%
2026-11-08: p95=4300ms, failures=2%
2026-11-15: p95=4500ms, failures=3%
2026-11-22: p95=4600ms, failures=4%

TREND: Slight degradation (p95 +10% over 3 weeks)
Action: Investigate database slow queries
```

---

## 🔄 Rotación de Logs

Automático vía cron (ver arriba), pero puedes hacer manualmente:

```bash
# Limpiar logs > 90 días
find /home/electoral/k6/logs -name "*.log" -mtime +90 -delete

# Archivar resultados antiguos
tar -czf /home/electoral/k6/archive/results-$(date +%Y-%m-%d).tar.gz \
  /home/electoral/k6/results/*.json
```

---

## 📋 Checklist: Antes de Producción

- [ ] k6 instalado: `k6 version`
- [ ] Backend corriendo: `curl http://localhost:8080/actuator/health`
- [ ] Database conecta: `psql ... -c "SELECT 1"`
- [ ] Scripts tienen permisos: `ls -l /home/electoral/k6/scripts/`
- [ ] .env configurado: `cat /home/electoral/k6/.env`
- [ ] Cron jobs configurados: `sudo crontab -l`
- [ ] Slack webhook funciona: Test via curl
- [ ] Email configurado: Test con `mail` comando
- [ ] Directorio existe: `ls -ld /home/electoral/k6/`
- [ ] Cron logs monitoreados: `tail /home/electoral/k6/logs/cron.log`

---

## 🚀 Ejecutar Tests Manualmente (Testing Setup)

### Nightly Pico Test

```bash
# Run now
/home/electoral/k6/scripts/nightly-pico-test.sh

# With custom API host
API_HOST=staging.control.ttcs /home/electoral/k6/scripts/nightly-pico-test.sh
```

### Weekly Stress Test

```bash
# Run now
/home/electoral/k6/scripts/weekly-stress-test.sh

# With logging
/home/electoral/k6/scripts/weekly-stress-test.sh 2>&1 | tee /tmp/stress-test.log
```

---

## 📧 Alertas Personalizadas

### Agreguen custom alertas si falla

Editar scripts y agregar:

```bash
# En nightly-pico-test.sh, después de notify_slack():

if [ "$TEST_STATUS" = "FAIL" ]; then
  # Custom action si falla
  curl -X POST "https://your-incident-system/api/incidents" \
    -d "service=k6-pico&severity=high&message=Pico+test+failed"
fi
```

---

## 🎯 Próximos Pasos

1. ✅ Copiar scripts al servidor
2. ✅ Crear .env con credenciales
3. ✅ Configurar Slack + Email
4. ✅ Setup cron jobs
5. ✅ Test manual (ejecutar scripts)
6. ✅ Monitorear primeras ejecuciones
7. ✅ Ajustar thresholds si es necesario

---

## 📞 Support

Si algo falla:
1. Revisar logs: `/home/electoral/k6/logs/`
2. Run manually con debug: `bash -x /home/electoral/k6/scripts/nightly-pico-test.sh`
3. Check docs: `frontend/tests/load/README.md`

---

**Status:** Ready for server deployment  
**Last Updated:** 2026-11-05
