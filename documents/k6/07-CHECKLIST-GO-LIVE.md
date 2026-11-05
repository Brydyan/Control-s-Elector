# 07: Checklist Final — Go Live

**Usar esto: 1 día antes de pasar a producción**

---

## ✅ Load Testing Results

```
[ ] Baseline test executed and PASSED
    p95 < 1.5s ✓
    failures 0% ✓

[ ] Pico test executed
    Status: ✅ PASS / ⚠️ CAUTION / ❌ FAIL
    
[ ] Results documented
    File: results/pico-20261105-*.json
    Summary: /path/to/summary.md
```

---

## ✅ Infrastructure

### Kubernetes

```
[ ] Replicas ≥ 2
    Current: kubectl get deployment backend -o jsonpath='{.spec.replicas}'
    
[ ] HPA configured (if load testing suggested)
    [ ] Min replicas: 2
    [ ] Max replicas: 8
    [ ] Target CPU: 70%
    [ ] Target Memory: 75%
    
[ ] Resource limits set
    [ ] CPU requests: 250m
    [ ] CPU limits: 1200m
    [ ] Memory requests: 512Mi
    [ ] Memory limits: 1.2Gi
    
[ ] Health checks configured
    [ ] Liveness probe: /actuator/health
    [ ] Readiness probe: /actuator/health
    [ ] Startup probe: /actuator/health/startup
```

Verification:
```bash
kubectl get hpa
kubectl describe deployment backend
kubectl get pods
```

### Database

```
[ ] Connection pool tuned
    [ ] max-size ≥ 25
    [ ] min-size ≥ 5
    [ ] idle-removal-interval: 10m
    
[ ] Indexes present
    [ ] idx_acta_jrv_cargo
    [ ] idx_acta_estado
    [ ] idx_acta_created_at
    [ ] idx_acta_detalle_acta_id
    
[ ] Backups working
    [ ] Daily backup: ✓ Configured
    [ ] Point-in-time recovery: ✓ Tested
    [ ] Backup retention: 30 days
    
[ ] Monitoring database
    [ ] Connection pool monitored
    [ ] Query performance logged
    [ ] Slow query threshold: 1s
```

Verification:
```bash
# Check indexes
psql -c "\d acta" | grep -i index

# Check backups
pg_dump --version && echo "Backup tool ready"
```

---

## ✅ Backend Application

```
[ ] Build successful
    ./mvnw clean build -DskipTests
    ✓ BUILD SUCCESS

[ ] No critical issues
    [ ] SpotBugs: No critical issues
    [ ] SonarQube: Code quality OK
    [ ] OWASP: No vulnerabilities
    
[ ] All tests pass
    ./mvnw test
    [ ] Unit tests: ✓ PASS
    [ ] Integration tests: ✓ PASS
    [ ] Contract tests: ✓ PASS
    
[ ] Latest dependencies
    [ ] No known CVEs in dependencies
    ./mvnw dependency-check:check
```

Verification:
```bash
./mvnw clean verify
```

---

## ✅ Frontend

```
[ ] Build successful
    npm run build
    ✓ No errors, warnings only

[ ] Tests pass
    npm run test:ci
    ✓ 100% pass rate

[ ] Linting passes
    npm run lint
    ✓ No errors

[ ] Load testing scripts present
    [ ] frontend/tests/load/
    [ ] All scenario files present
    [ ] config.js updated for prod URLs
```

Verification:
```bash
npm run build && npm run test:ci && npm run lint
```

---

## ✅ Monitoring & Observability

```
[ ] Logging configured
    [ ] Application logs: /var/log/app/
    [ ] Log rotation: daily, 30 days retention
    [ ] Log level: INFO (or DEBUG if dev)
    
[ ] Health endpoint working
    curl http://backend:8080/api/v1/health
    ✓ {"status":"UP"}
    
[ ] Metrics available
    [ ] Prometheus scrape endpoint: /actuator/prometheus
    [ ] Key metrics: http_requests_total, http_request_duration_seconds
    
[ ] Alerting configured
    [ ] Database unavailable: ✓
    [ ] High error rate (> 5%): ✓
    [ ] High latency (p95 > 5s): ✓
    [ ] High CPU (> 85%): ✓
    [ ] Pod restart loop: ✓
```

Verification:
```bash
kubectl logs -f deployment/backend
curl http://backend:8080/api/v1/health | jq
```

---

## ✅ Security

```
[ ] HTTPS enforced
    [ ] TLS certificate: valid until {{ cert_expiry }}
    [ ] Certificate auto-renewal: enabled
    [ ] HTTP → HTTPS redirect: enabled
    
[ ] Authentication working
    [ ] Login endpoint: /api/v1/auth/login
    [ ] Token generation: ✓ Working
    [ ] Token expiry: 1 hour
    [ ] Refresh token: ✓ Working
    
[ ] Authorization checks
    [ ] RBAC roles: OBSERVER, COORDINATOR, ADMIN
    [ ] Endpoint protection: @PreAuthorize on all endpoints
    [ ] Row-level security: enabled in PostgreSQL
    
[ ] Secrets management
    [ ] DB credentials: in K8s secret, NOT in code
    [ ] JWT secret: in K8s secret, NOT in code
    [ ] S3 credentials: in K8s secret, NOT in code
    
[ ] CORS configured
    [ ] Frontend origin: whitelisted
    [ ] Credentials: allowed
    [ ] Methods: GET, POST, PUT, DELETE
```

Verification:
```bash
# Check HTTPS
curl -k https://backend:8443/api/v1/health

# Check secrets
kubectl get secrets
kubectl describe secret api-secrets

# Check CORS
curl -H "Origin: http://frontend" http://backend/api
```

---

## ✅ Data & Backups

```
[ ] Test data cleaned (production)
    [ ] Test users removed (@electoral.ec users gone)
    [ ] Seed data cleared
    
[ ] Production data imported
    [ ] JRVs: 500 ✓
    [ ] Candidatos: ≥ 50 ✓
    [ ] Cargos: ≥ 3 ✓
    
[ ] Backup before deployment
    [ ] Full backup created: 2026-11-05_backup.sql
    [ ] Size verified: ≥ 50MB
    [ ] Restore tested: ✓ Works
    
[ ] Disaster recovery plan
    [ ] RTO (Recovery Time Objective): 4 hours
    [ ] RPO (Recovery Point Objective): 1 hour
    [ ] Runbook created: docs/disaster-recovery.md
```

Verification:
```bash
# Count data
psql -c "SELECT COUNT(*) FROM jrv; SELECT COUNT(*) FROM candidato;"

# Test backup
pg_dump > backup.sql
# Verify can restore
psql < backup.sql
```

---

## ✅ Documentation

```
[ ] Runbooks written
    [ ] Deploy procedure: docs/runbooks/deploy.md
    [ ] Rollback procedure: docs/runbooks/rollback.md
    [ ] Incident response: docs/runbooks/incident-response.md
    [ ] On-call playbook: docs/runbooks/oncall.md
    
[ ] Architecture documented
    [ ] System diagram: docs/architecture.md
    [ ] Data flow: docs/data-flow.md
    [ ] Scaling strategy: docs/scaling.md
    
[ ] Load testing results documented
    [ ] Test methodology: docs/k6/results/methodology.md
    [ ] Results summary: docs/k6/results/summary.md
    [ ] Recommendations applied: docs/k6/results/actions-taken.md
    
[ ] Troubleshooting guide
    [ ] Common issues: docs/troubleshooting.md
    [ ] Debug commands: docs/debug.md
```

---

## ✅ Team Readiness

```
[ ] Team trained
    [ ] DevOps: K8s operations, troubleshooting
    [ ] Backend: Code deployment, debugging
    [ ] Frontend: Deployment, error handling
    [ ] On-call: Load testing, incident response
    
[ ] On-call schedule established
    [ ] Primary on-call: __________ until ________
    [ ] Backup on-call: __________ until ________
    [ ] Weekly rotation: Yes / No
    [ ] Alert escalation: ✓ Defined
    
[ ] Communication channels active
    [ ] Slack #incidents: ready
    [ ] PagerDuty: configured
    [ ] War room: ready (Zoom/Teams link)
    
[ ] Stakeholders informed
    [ ] PM: ✓ Notified
    [ ] Director: ✓ Notified
    [ ] Election commission: ✓ Ready
    [ ] Observers: ✓ Trained
```

---

## ✅ Pre-Launch Tests (24 hours before)

```
[ ] Smoke test passed
    ./run.sh baseline
    ✓ 10 VUs, 5 min, 0% failures

[ ] Load test re-run (optional, but recommended)
    ./run.sh pico
    ✓ Results same as before

[ ] Database health check
    psql -c "SELECT COUNT(*) FROM acta;"
    ✓ Connected, no errors

[ ] Backend health check
    curl http://backend:8080/api/v1/health
    ✓ {"status":"UP"}

[ ] Frontend loads
    Open in browser: http://frontend
    ✓ Loads, no console errors

[ ] End-to-end sanity test
    [ ] Login as observer ✓
    [ ] Login as coordinator ✓
    [ ] Login as admin ✓
    [ ] Navigate pages ✓
    [ ] Submit test acta ✓
```

---

## ✅ Election Day (Morning Of)

```
[ ] Verify all systems running
    kubectl get pods
    # All showing RUNNING / READY

[ ] Check monitoring dashboards
    kubectl port-forward svc/prometheus 9090:9090
    # Metrics available

[ ] Verify on-call team present
    [ ] Primary: Online and ready
    [ ] Backup: Online and ready
    [ ] War room link: Shared

[ ] Final status check
    curl http://backend:8080/api/v1/health
    ✓ OK

[ ] Open war room
    [ ] Zoom/Teams meeting active
    [ ] Recording: ON
    [ ] Chat: #incidents active
    
[ ] Begin voting
    Vote opens at 07:00 AM
```

---

## 🔴 STOP Signs — Do NOT Launch If

```
❌ Any test is FAILING
   → Resolve before launch

❌ Load test p99 > 8.5s
   → Risk of timeout issues

❌ Failure rate > 12%
   → Too many users will experience errors

❌ CPU approaching 90% in test
   → No headroom for spike

❌ Backup cannot be restored
   → Risk of data loss

❌ Team not trained
   → Risk of wrong decisions in incident

❌ On-call not ready
   → Who will respond to issues?

❌ Any critical security issue
   → Could be exploited

IF ANY ❌ ABOVE: DELAY LAUNCH
```

---

## 📋 Sign-Off

**Before launching, all stakeholders must sign off:**

```
Project Manager: _________________________ Date: _______
  ✓ Confirmed: System ready, no blockers, team trained

Technical Lead: _________________________ Date: _______
  ✓ Confirmed: Infrastructure validated, load tests pass,
               documentation complete

DevOps Lead:    _________________________ Date: _______
  ✓ Confirmed: Monitoring ready, alerts configured,
               runbooks tested, disaster recovery plan ready

Election Commission: __________________ Date: _______
  ✓ Confirmed: Ready for voting
```

---

## 🚀 Launch

```
When all ✓ above complete and signed:

1. Take deep breath
2. Announce: "VOTING SYSTEM GO LIVE"
3. Open polling stations
4. Monitor dashboards 24/7
5. Be ready to escalate if needed

Good luck! 🎉
```

---

## 📊 Post-Launch Monitoring (First 24h)

Every hour for first 24 hours:

```
[ ] Check error rate < 5%
[ ] Check p95 latency < 5s
[ ] Check CPU < 80%
[ ] Check DB connections < 25/35
[ ] Check no stuck pods
[ ] Check logs for errors
[ ] Touch base with election observers
```

---

## ✅ Rollback Criteria (If Needed)

Rollback immediately if:

```
❌ Error rate > 20%
❌ Database inaccessible
❌ p99 latency > 15s sustained
❌ Pod crash loop
❌ Loss of data integrity
```

Rollback procedure:
```bash
# 1. Stop voting
# 2. Scale backend to 0
kubectl scale deployment backend --replicas=0

# 3. Restore from backup
pg_restore < backup.sql

# 4. Redeploy previous version
kubectl apply -f k8s/backend-v1.yaml

# 5. Verify health
curl http://backend:8080/api/v1/health
```

---

**Last Updated:** 2026-11-05  
**Status:** Pre-production launch checklist  
**Criticality:** 🔴 MUST COMPLETE BEFORE LAUNCH
