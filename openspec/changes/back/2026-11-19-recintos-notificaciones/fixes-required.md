# Fixes Required — Recintos & Notificaciones (DIVERGE → CONFORM)

**Verificación Status:** ❌ DIVERGE  
**Bloqueadores:** 3 CRITICAL  
**Rework Estimate:** 8-12 horas  

---

## 🔴 CRITICAL BLOCKERS (Must fix before merge)

### 1. C.1 NOT IMPLEMENTED — ActaService Notification Interceptors

**Location:** `backend/electoral-api-quarkus/src/main/java/ec/twintailcs/electoral/acta/application/ActaService.java`

**Problem:** 
- ActaService methods (enviarActa, aprobar, rechazarActa) don't create notifications
- Spec C.1 requires notifications on state transitions
- Coordinator never receives "acta pendiente" notifications
- Observer never receives "acta aprobada/rechazada" notifications

**Required Changes:**

**A) Modify `enviarActa()` method:**
```java
// After estado = ENVIADA, before persisting:
@Transactional
public ActaResponse enviarActa(Long actaId, String xForwardedFor) {
  // ... existing code ...
  acta.setEstado("ENVIADA");
  
  // NEW: Create notification for Coordinator
  Jrv jrv = acta.getJrv(); // existing
  Recinto recinto = entityManager.find(Recinto.class, jrv.getRecintoId());
  if (recinto != null && recinto.getCoordinatorId() != null) {
    String mensaje = String.format(
        "Acta pendiente: JRV %d - %s enviada por %s",
        jrv.getNumero(), 
        acta.getCargo().getNombre(),
        acta.getRegistradoPor().getNombre()
    );
    notificacionService.crearNotificacion(
        recinto.getCoordinatorId(),
        "ACTA_ENVIADA",
        "Acta pendiente de revisión",
        mensaje,
        acta.getId()
    );
  }
  
  acta.persist();
  return mapToResponse(acta);
}
```

**B) Modify `aprobar()` method:**
```java
@Transactional
public ActaResponse aprobar(Long actaId, String xForwardedFor) {
  // ... existing code ...
  Acta acta = obtenerActa(actaId);
  
  // Check RLS: coordinator only approves his recintos
  validarCoordinatorOwnsRecinto(acta.getJrv().getRecintoId());
  
  acta.setEstado("APROBADA");
  acta.setAprobadaPor(currentUser.getId()); // NEW field
  acta.setFechaAprobacion(Instant.now());   // NEW field
  
  // NEW: Notify Observer
  String mensaje = String.format(
      "Tu acta (%s - %s) ha sido APROBADA",
      acta.getJrv().getNumero(),
      acta.getCargo().getNombre()
  );
  notificacionService.crearNotificacion(
      acta.getRegistradoPor().getId(),
      "ACTA_APROBADA",
      "Acta aprobada",
      mensaje,
      acta.getId()
  );
  
  acta.persist();
  return mapToResponse(acta);
}
```

**C) Modify `rechazarActa()` method (or add new `disputar()`):**
```java
@Transactional
public ActaResponse rechazarActa(Long actaId, String motivo, String xForwardedFor) {
  // ... existing code ...
  Acta acta = obtenerActa(actaId);
  
  // Check RLS
  validarCoordinatorOwnsRecinto(acta.getJrv().getRecintoId());
  
  acta.setEstado("EN_DISPUTA");
  acta.setMotivoRechazo(motivo); // NEW field (500 chars max)
  
  // NEW: Notify Observer
  String mensaje = String.format(
      "Tu acta (%s - %s) ha sido RECHAZADA.\nMotivo: %s\nPor favor corrige y reenvía.",
      acta.getJrv().getNumero(),
      acta.getCargo().getNombre(),
      motivo
  );
  notificacionService.crearNotificacion(
      acta.getRegistradoPor().getId(),
      "ACTA_RECHAZADA",
      "Acta rechazada - requiere corrección",
      mensaje,
      acta.getId()
  );
  
  acta.persist();
  return mapToResponse(acta);
}
```

**Testing:** Add to `ActaServiceTest.java`
```java
@Test
@Transactional
void testEnviarActaCreatesNotificationForCoordinator() {
  // Setup: acta enviada, jrv con recinto asignado a coordinator
  Acta acta = createTestActa("BORRADOR");
  
  // Act
  actaService.enviarActa(acta.getId(), null);
  
  // Assert
  long notifCount = entityManager
      .createNativeQuery("SELECT count(*) FROM notificacion WHERE tipo = 'ACTA_ENVIADA'")
      .getSingleResult();
  assertEquals(1, notifCount);
}
```

**Commits:**
- `feat(recintos): C.1 - ActaService notification interceptors`

---

### 2. Missing Unit Test — `eliminarRecinto()` with JRVs

**Location:** `backend/electoral-api-quarkus/src/test/java/ec/twintailcs/electoral/geografia/application/RecintoServiceTest.java`

**Problem:**
- Test for D5 decision (cannot delete Recinto with JRVs) is missing
- RecintoService.eliminarRecinto() line 173 calls `countJrvsInRecinto()` but no test validates this

**Required Test:**
```java
@Test
@Transactional
void eliminarRecintoConJrvsAsignadas() {
  // Setup: recinto + 2 jrvs asignadas
  Parroquia parroquia = Parroquia.find("order by id").firstResult();
  Recinto recinto = service.crearRecinto("Test", parroquia.getId(), null, "ACTIVO");
  
  // Create 2 JRVs and assign to recinto
  Jrv jrv1 = new Jrv(/* params */, recinto.getId());
  Jrv jrv2 = new Jrv(/* params */, recinto.getId());
  jrv1.persist();
  jrv2.persist();
  
  // Act & Assert: cannot delete
  assertThrows(
      GeografiaConflictException.class,
      () -> service.eliminarRecinto(recinto.getId()),
      "Cannot delete Recinto with assigned JRVs"
  );
}
```

**Commits:**
- `test(recintos): add test for eliminarRecinto with JRVs`

---

### 3. Missing Integration Test — RLS Validation for CoordinatorResource

**Location:** `backend/electoral-api-quarkus/src/test/java/ec/twintailcs/electoral/acta/web/CoordinatorResourceIT.java` (create new file)

**Problem:**
- D4 decision (Coordinator only sees his Recintos' actas) has NO validation
- CoordinatorResource.actas-pendientes uses RLS query but no test verifies it works
- Risk: Coordinator could see actas from other coordinators' recintos

**Required Integration Test:**
```java
@QuarkusTest
class CoordinatorResourceIT {
  
  @Inject ActaService actaService;
  @Inject RecintoService recintoService;
  @Inject EntityManager entityManager;
  
  @Test
  @WithMockJwt(claims = {
    @Claim(key = "sub", value = "coordinator1"),
    @Claim(key = "roles", value = "COORDINATOR")
  })
  void coordinatorOnlySeesPendingActasFromHisRecintos() {
    // Setup:
    // 1. Create Recinto A, assigned to Coordinator 1
    // 2. Create Recinto B, assigned to Coordinator 2
    // 3. Create Acta in Recinto A (ENVIADA)
    // 4. Create Acta in Recinto B (ENVIADA)
    
    Parroquia parroquia = Parroquia.find("order by id").firstResult();
    Recinto recintoA = recintoService.crearRecinto("Recinto A", parroquia.getId(), 100L, "ACTIVO");
    Recinto recintoB = recintoService.crearRecinto("Recinto B", parroquia.getId(), 101L, "ACTIVO");
    
    Acta actaA = createActaInRecinto(recintoA, "ENVIADA");
    Acta actaB = createActaInRecinto(recintoB, "ENVIADA");
    
    // Act: Coordinator 1 fetches pending actas
    given()
      .header("Authorization", "Bearer <jwt_coordinator_1>")
      .when()
      .get("/api/v1/coordinator/actas-pendientes")
      .then()
      .statusCode(200)
      .body("data", hasSize(1))  // Only Acta A
      .body("data[0].id", equalTo(actaA.getId()));
    
    // Assert: Acta B should NOT be in response
  }
}
```

**Commits:**
- `test(recintos): add integration test for CoordinatorResource RLS`

---

## 🟡 HIGH PRIORITY (Strongly recommended)

### 4. Add Integration Tests for RecintoResource

**Location:** `backend/electoral-api-quarkus/src/test/java/ec/twintailcs/electoral/geografia/web/MasterRecintoResourceIT.java` (create)

**Problem:**
- Endpoints tested only via unit tests (Service layer)
- No validation of HTTP status codes (400, 404, 409)
- No test for MASTER-only authorization

**Required Tests:**
```java
@QuarkusTest
class MasterRecintoResourceIT {
  
  @Test
  @WithMockJwt(claims = { @Claim(key = "roles", value = "MASTER") })
  void crearRecintoExitosa() {
    given()
      .body(new RecintoRequest("New Recinto", 1L, 10L))
      .contentType(ContentType.JSON)
      .when()
      .post("/api/v1/admin/recintos")
      .then()
      .statusCode(201)
      .body("nombre", equalTo("New Recinto"));
  }
  
  @Test
  @WithMockJwt(claims = { @Claim(key = "roles", value = "MASTER") })
  void crearRecintoDuplicado() {
    // Create first
    // Create second with same nombre + parroquiaId
    // Expect 400 or 409
  }
  
  @Test
  @WithMockJwt(claims = { @Claim(key = "roles", value = "OBSERVER") })
  void crearRecintoForbiddenNonMaster() {
    // OBSERVER tries to create
    // Expect 403
  }
}
```

**Commits:**
- `test(recintos): add integration tests for RecintoResource`

---

## 🟠 MEDIUM PRIORITY

### 5. Frontend Phase D — Admin Recintos Panel (NOT STARTED)

**Location:** `frontend/app/src/app/features/admin/pages/recintos/`

**Scope:**
- [ ] `recintos-list.page.ts` (~150 LOC) — Table + CRUD buttons
- [ ] `recinto-form.modal.ts` (~200 LOC) — Cascada geográfica + form
- [ ] `recinto-jrvs.modal.ts` (~150 LOC) — Assign JRVs modal
- [ ] `recinto.api.ts` (~50 LOC) — API integration
- [ ] Integration in Configuración menu

**Tests:** 15+ unit tests (form validation, API calls, error handling)

**Commits:**
- `feat(recintos): D.1-D.4 - Admin Recintos panel`

---

### 6. Frontend Phase E — Coordinator Actas Panel (NOT STARTED)

**Location:** `frontend/app/src/app/features/coordinator/pages/actas-pendientes/`

**Scope:**
- [ ] `actas-pendientes.page.ts` (~200 LOC) — Tabs + polling (5s)
- [ ] `acta-card.component.ts` (~100 LOC) — Card display
- [ ] `acta-detalle.modal.ts` (~180 LOC) — Photo + details
- [ ] `rechazo-form.modal.ts` (~100 LOC) — Reject with reason
- [ ] `notificaciones.component.ts` (~120 LOC) — Bell + dropdown
- [ ] `coordinator.api.ts` (~50 LOC) — API integration
- [ ] `notificacion.api.ts` (~30 LOC) — Notification polling

**Tests:** 20+ unit + E2E tests (polling, approve/reject flows, notifications)

**Commits:**
- `feat(recintos): E.1-E.5 - Coordinator Actas panel + Notifications`

---

### 7. Documentation Phase F (NOT STARTED)

**Location:** `backend/docs/`

**Files:**
- [ ] Update `AUTHENTICATION.md` — Add COORDINATOR role
- [ ] Update `API_REFERENCE.md` — Document new endpoints
- [ ] Create `RECINTOS_GUIDE.md` — Admin user guide
- [ ] Create `ACTAS_VALIDACION_GUIDE.md` — Coordinator user guide

**Commits:**
- `docs(recintos): F.1-F.4 - Documentation + API reference`

---

## 🔧 MINOR FIXES

### 8. Fix typo in RecintoServiceTest

**Location:** Line 34

**Current:**
```java
void crearRecintoVaaldido() {
```

**Fix:**
```java
void crearRecintoValido() {
```

---

### 9. Remove TODO in RecintoService

**Location:** Line 65

**Current:**
```java
// TODO: Validate coordinator has COORDINATOR role (when role enum is available)
```

**Current behavior:** Accepts any user (permissive)

**Fix:** When role enum available, add validation:
```java
if (coordinatorRole != RoleEnum.COORDINATOR) {
  throw new GeografiaConflictException("Coordinator must have COORDINATOR role");
}
```

---

### 10. Add missing RecintoRepository methods

**Location:** `backend/electoral-api-quarkus/src/main/java/ec/twintailcs/electoral/geografia/domain/RecintoRepository.java`

**Missing methods:**
- `countJrvsInRecinto(Long recintoId): long` — Used in eliminarRecinto()
- `findByCoordinatorId(Long coordinatorId): List<Recinto>` — For RLS queries

**Implementation:**
```java
default long countJrvsInRecinto(Long recintoId) {
  return find("recintoId", recintoId).count();
}

default List<Recinto> findByCoordinatorId(Long coordinatorId) {
  return find("coordinatorId", coordinatorId).list();
}
```

---

## ✅ Implementation Order

**Phase 1 (CRITICAL — 2 hours):**
1. Implement C.1 (ActaService interceptors) — BLOCKER #1
2. Add eliminarRecinto test — BLOCKER #2
3. Add RLS integration test — BLOCKER #3

**Phase 2 (HIGH — 2 hours):**
4. Add RecintoResource integration tests
5. Fix typos + TODOs

**Phase 3 (MEDIUM — 6 hours):**
6. Frontend D (Admin panel)
7. Frontend E (Coordinator panel)

**Phase 4 (1 hour):**
8. Documentation

**Total: 11 hours**

---

## Verification Checklist (Post-fixes)

Before merging:
- [ ] All 3 CRITICAL blockers fixed
- [ ] `mvn clean test` → all tests pass
- [ ] No compiler warnings
- [ ] RLS integration test validates D4
- [ ] Frontend tests pass locally
- [ ] E2E tests: Observer upload → Coordinator notification → approve → Observer notified ✅
- [ ] Load test: 50 coordinators polling /actas-pendientes, no timeout
- [ ] No regressions: existing observer/admin flows still work

---

## Sign-Off

**Builder:** Implement CRITICAL blockers + HIGH priority, then re-run `/03-verify-sdd`

**QA:** Revert to ✅ CONFORM when all fixes applied and tests pass

**Merge:** Only after ✅ CONFORM verdict
