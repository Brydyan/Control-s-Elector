# ODD — Priority Compliance Fixes (PWA)

**Date:** 2026-11-07  
**Context:** Audit findings require immediate fixes before Privacy/Terms/Sentry SDD rollout.  
**Method:** ODD (Internal Builder routing for quick fixes)

---

## Phase 1: CORS Configuration (🔴 CRITICAL)

**Why:** Frontend (:4200) ↔ Backend (:8080) cross-origin blocked. No explicit CORS config.

### Task 1: Add CORS to application.properties

**File:** `backend/electoral-api-quarkus/src/main/resources/application.properties`

**Add:**
```properties
# CORS Configuration (PWA + development)
quarkus.http.cors=true
quarkus.http.cors.origins=https://control.twintailcs.xyz,http://localhost:4200,http://localhost:4300
quarkus.http.cors.methods=GET,POST,PUT,DELETE,OPTIONS,PATCH
quarkus.http.cors.headers=Content-Type,Authorization,X-Requested-With,Accept,X-Forwarded-For
quarkus.http.cors.access-control-allow-credentials=true
quarkus.http.cors.access-control-max-age=3600
quarkus.http.cors.access-control-expose-headers=Content-Length,Content-Type,X-Total-Count
```

**Verification:**
```bash
# Local test
curl -i -X OPTIONS http://localhost:8080/api/auth/login \
  -H "Origin: http://localhost:4200" \
  -H "Access-Control-Request-Method: POST"

# Should return 200 with:
# Access-Control-Allow-Origin: http://localhost:4200
# Access-Control-Allow-Methods: GET, POST, PUT, DELETE, OPTIONS, PATCH
# Access-Control-Allow-Credentials: true
```

**Effort:** 5 min | **Risk:** Very Low

---

## Phase 2: License Files (🟡 HIGH)

### Task 2: Create LICENSE file (Apache 2.0)

**File:** `LICENSE`

**Content:**
```
Apache License
Version 2.0, January 2004

TERMS AND CONDITIONS FOR USE, REPRODUCTION, AND DISTRIBUTION

1. Definitions.
   - "License" shall mean the terms and conditions for use, reproduction, and distribution
   - "Licensor" shall mean Control Electoral / Consejo Nacional Electoral (CNE)
   - "Project" shall mean Control Electoral System
   - "Work" shall mean the Project and any modifications thereof

2. Grant of Copyright License.
   Licensor grants to You a perpetual, worldwide, non-exclusive license to reproduce,
   prepare derivative works of, publicly display, publicly perform, sublicense, and
   distribute the Work.

3. Grant of Patent License.
   Subject to the terms and conditions of this License, each Contributor hereby grants
   to You a perpetual, worldwide, non-exclusive, no-charge, royalty-free, irrevocable
   patent license...

[Full Apache 2.0 text - use standard version]
```

**Effort:** 10 min | **Risk:** Very Low

---

### Task 3: Create THIRD_PARTY_LICENSES.md

**File:** `THIRD_PARTY_LICENSES.md`

**Content:**
```markdown
# Third-Party Licenses

## Backend Dependencies

### Apache License 2.0
- Quarkus Framework (3.40)
- AWS SDK for Java v2
- Apache Commons
- Hibernate ORM
- Liquibase

### MIT License
- Nimbus JOSE JWT (Java JWT library)
- JUnit 5
- AssertJ

### LGPL
- PostgreSQL JDBC Driver

### ISC License
- Various utility libraries

## Frontend Dependencies

### MIT License
- Angular 21
- TypeScript 5
- Angular Material
- RxJS
- Prettier
- ESLint

### Apache License 2.0
- Tailwind CSS
- PrimeNG Components

### Dual License (MIT / Apache)
- Capacitor (PWA/mobile bridge)

## Assets

### Icons
- [Source/License info]

### Fonts
- [Source/License info]

---

## How to Update

When adding new dependencies:
1. Check license type: `npm info <package> license` or `mvn dependency:tree`
2. Add to corresponding section
3. Update this file before merging

---

**Last Updated:** 2026-11-07
```

**Effort:** 15 min | **Risk:** Very Low

---

## Phase 3: Jacoco Code Coverage (🟡 HIGH)

### Task 4: Add Jacoco plugin to backend

**File:** `backend/electoral-api-quarkus/pom.xml`

**Add to `<plugins>` section:**
```xml
<plugin>
  <groupId>org.jacoco</groupId>
  <artifactId>jacoco-maven-plugin</artifactId>
  <version>0.8.10</version>
  <executions>
    <execution>
      <goals>
        <goal>prepare-agent</goal>
      </goals>
    </execution>
    <execution>
      <id>report</id>
      <phase>test</phase>
      <goals>
        <goal>report</goal>
      </goals>
    </execution>
    <execution>
      <id>jacoco-check</id>
      <phase>test</phase>
      <goals>
        <goal>check</goal>
      </goals>
      <configuration>
        <rules>
          <rule>
            <element>PACKAGE</element>
            <excludes>
              <exclude>*Test</exclude>
            </excludes>
            <limits>
              <limit>
                <counter>LINE</counter>
                <value>COVEREDRATIO</value>
                <minimum>0.60</minimum>
              </limit>
            </limits>
          </rule>
        </rules>
      </configuration>
    </execution>
  </executions>
</plugin>
```

**Update CI:** `.github/workflows/ci.yml`

```yaml
- name: Generate coverage report
  run: cd backend/electoral-api-quarkus && mvn jacoco:report
  
- name: Upload coverage to Codecov (optional)
  uses: codecov/codecov-action@v3
  with:
    files: ./backend/electoral-api-quarkus/target/site/jacoco/jacoco.xml
```

**Verification:**
```bash
cd backend/electoral-api-quarkus
mvn clean test
# Report generated: target/site/jacoco/index.html
open target/site/jacoco/index.html
```

**Effort:** 20 min | **Risk:** Very Low

---

## Phase 4: PWA Manifest & Service Worker Check

### Task 5: Verify PWA web manifest (already should exist)

**File:** `frontend/app/src/manifest.webmanifest`

**Verify contains:**
```json
{
  "name": "Control Electoral",
  "short_name": "Control",
  "theme_color": "#1976d2",
  "background_color": "#fafafa",
  "start_url": "/",
  "display": "standalone",
  "scope": "/",
  "icons": [
    {
      "src": "assets/icons/icon-72x72.png",
      "sizes": "72x72",
      "type": "image/png",
      "purpose": "any"
    }
  ]
}
```

**angular.json check:** verify `serviceWorker` enabled
```json
"serviceWorker": true,
"ngswConfigPath": "ngsw-config.json"
```

**Effort:** 5 min (validation only)

---

## Execution Order

1. **CORS config** (5 min) - unblocks frontend/backend communication
2. **LICENSE file** (10 min) - legal compliance
3. **THIRD_PARTY file** (15 min) - transparency
4. **Jacoco coverage** (20 min) - CI/CD integration
5. **PWA manifest check** (5 min) - validation

**Total ODD effort:** ~55 minutes

---

## After ODD Completes

✅ Frontend can communicate with backend (CORS)
✅ Legal transparency (LICENSE, THIRDPARTY)
✅ Code coverage visible in CI
✅ PWA ready for distribution

Then: **Launch SDD builders** (Privacy+Terms, Cookie Consent, Sentry)

---

**Status:** Ready for execution  
**Assigned to:** Builder (ODD phase)  
**Next:** `design.md` for each SDD after ODD closes
