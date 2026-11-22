# Setup Guide — k6 Load Testing

**Time Required:** 10-15 minutes  
**Difficulty:** Easy

---

## 1️⃣ Install k6

### Option A: Using npm (RECOMMENDED if you have Node.js)

```bash
# Install globally
npm install -g k6

# Verify installation
k6 version
# Output: k6 vX.XX.X (linux/amd64)
```

### Option B: Using Homebrew (macOS)

```bash
brew install k6

# Verify
k6 version
```

### Option C: Using Chocolatey (Windows)

```powershell
choco install k6

# Verify
k6 version
```

### Option D: Using Docker (No Installation Needed)

```bash
docker pull grafana/k6:latest

# Test
docker run --rm grafana/k6:latest version
```

### Option E: Download Binary

Visit https://grafana.com/products/cloud/k6/download/ and download the appropriate binary for your OS.

---

## 2️⃣ Verify Backend is Running

```bash
# Test health endpoint
curl http://localhost:8080/q/health

# Expected response (200 OK):
# {"status": "UP", ...}
```

If this fails, start the backend:
```bash
cd backend/electoral-api-quarkus
./mvnw quarkus:dev
# Wait for: "Listening on: http://0.0.0.0:8080"
```

---

## 3️⃣ Seed Test Data

k6 needs test users in the database. Execute this SQL:

### Quick Option: Use a SQL Script

```bash
# Create script
cat > /tmp/test-data.sql << 'EOF'
-- Test users for load testing
INSERT INTO usuario (email, password_hash, rol, estado)
VALUES
  ('obs1@electoral.ec', '$2a$12$...', 'OBSERVER', 'ACTIVO'),
  ('obs2@electoral.ec', '$2a$12$...', 'OBSERVER', 'ACTIVO'),
  ('obs3@electoral.ec', '$2a$12$...', 'OBSERVER', 'ACTIVO'),
  ('obs4@electoral.ec', '$2a$12$...', 'OBSERVER', 'ACTIVO'),
  ('obs5@electoral.ec', '$2a$12$...', 'OBSERVER', 'ACTIVO'),
  ('coord1@electoral.ec', '$2a$12$...', 'COORDINATOR', 'ACTIVO'),
  ('coord2@electoral.ec', '$2a$12$...', 'COORDINATOR', 'ACTIVO'),
  ('admin@electoral.ec', '$2a$12$...', 'ADMIN', 'ACTIVO')
ON CONFLICT (email) DO NOTHING;
EOF

# Execute on database
psql -h localhost -U electoral_user -d electoral_db -f /tmp/test-data.sql
```

### Via Backend API (Recommended)

If your backend has a test setup endpoint:

```bash
# Create test user via API
curl -X POST http://localhost:8080/api/v1/admin/test-data \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_ADMIN_TOKEN" \
  -d '{
    "action": "seed-test-users",
    "count": 5
  }'
```

### Via Admin UI

If your backend has an admin panel, use it to create test users manually.

---

## 4️⃣ Configure Environment (Optional)

Create a `.env` file in the load testing directory:

```bash
# .env
API_HOST=localhost:8080
SLEEP_DURATION=2
K6_DOCKER=false
```

Then load it before running tests:
```bash
source .env
./run.sh baseline
```

Or inline:
```bash
API_HOST=localhost:8080 ./run.sh baseline
```

---

## 5️⃣ Run Baseline Test

```bash
cd frontend/tests/load

# Option A: Using bash script
./run.sh baseline

# Option B: Using k6 directly
k6 run scenarios/login.js --vus 10 --duration 5m

# Option C: Using npm script
npm run test:baseline

# Option D: Using Docker
K6_DOCKER=true ./run.sh baseline
```

**Expected Output:**
```
█ baseline
  execution: local
  script: scenarios/login.js
  output: json

    ✓ login status 200
    ✓ login has accessToken

  data_received....................: 45 kB
  data_sent.........................: 12 kB
  http_req_duration.................: avg=125ms p(95)=142ms p(99)=198ms ✓
  http_req_failed....................: 0% ✓
  http_reqs..........................: 50 reqs/sec
  iterations.........................: 50

✓ All thresholds passed!
```

---

## 🆘 Troubleshooting

### k6 command not found
```bash
# Check if installed
which k6

# If not found, install via npm
npm install -g k6

# Verify
k6 version
```

### Connection refused (localhost:8080)
```bash
# Check if backend is running
curl http://localhost:8080/q/health

# If not, start backend
cd backend/electoral-api-quarkus
./mvnw quarkus:dev

# Or check if port is correct
netstat -tuln | grep 8080
```

### Login failed (401 Unauthorized)
```bash
# Test users don't exist in DB
# Seed test data (see Step 3 above)

# Or verify credentials in config.js match DB
```

### Too many connections error
```bash
# Database connection limit reached
# Reduce VUs in config.js or reduce concurrent tests
./run.sh baseline --vus 5 --duration 2m
```

### Out of memory
```bash
# k6 uses ~50MB per VU
# 100 VUs = ~5GB RAM (normal)
# If system OOM, reduce VUs or run on more powerful machine

# Monitor during test
watch -n 1 'ps aux | grep k6'
```

### Docker permission denied
```bash
# Add user to docker group
sudo usermod -aG docker $USER
newgrp docker

# Or use sudo
sudo docker pull grafana/k6:latest
```

---

## ✅ Verification Checklist

- [ ] k6 installed and verified (`k6 version`)
- [ ] Backend running on localhost:8080 (or configured API_HOST)
- [ ] Backend health check passes (`curl http://localhost:8080/q/health`)
- [ ] Test data seeded in database
- [ ] Test credentials exist (`obs1@electoral.ec`, etc.)
- [ ] Baseline test runs successfully
- [ ] Results save to `results/` directory

---

## 🚀 Next Steps

Once setup is complete:

1. **Run Baseline Test**
   ```bash
   ./run.sh baseline
   ```

2. **Run Pico Test (Most Realistic)**
   ```bash
   ./run.sh pico
   ```

3. **Analyze Results**
   - Check `results/*.json` files
   - Look for SLA violations
   - Document findings

4. **Apply Fixes** (if needed)
   - Increase replicas if CPU > 70%
   - Tune connection pool if failures > 5%
   - Implement cache if queries/sec too high

5. **Retest After Fixes**
   ```bash
   ./run.sh pico
   ```

---

## 📚 Additional Resources

- **k6 Documentation:** https://k6.io/docs/
- **k6 HTTP API:** https://k6.io/docs/javascript-api/k6-http/
- **Load Testing Best Practices:** https://k6.io/docs/test-authoring/
- **Load Testing Scenarios:** https://k6.io/docs/using-k6/scenarios/

---

## 🎓 Learning Path

1. **Beginner:** Read README.md (this directory)
2. **Intermediate:** Examine scenarios/ (study real test cases)
3. **Advanced:** Read `documents/CACHING_STRATEGY.md` and `documents/diagram/02-deployment.md` (context & strategy)

---

**Last Updated:** 2026-11-05  
**Status:** Ready for first test run
