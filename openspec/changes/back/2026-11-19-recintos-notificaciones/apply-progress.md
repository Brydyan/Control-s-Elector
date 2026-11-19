# Apply Progress — Recintos & Notificaciones de Actas Pendientes

```markdown
# Apply Progress — Recintos & Notificaciones

## Estado: PENDIENTE

Esperando asignación a Builder (post-arquitecto review de D1-D5).

## Commits aplicados
(ninguno aún)

## Notas de Implementación
(a llenar durante build)

---

## Phase A: Database Migrations

- [ ] A.1: Tabla recinto creada
- [ ] A.2: JRV.recinto_id agregado (nullable)
- [ ] A.3: Datos mirados: todas las JRVs asignadas a Recintos
- [ ] A.4: Acta fields agregados (aprobada_por, fecha_aprobacion, motivo_rechazo)
- [ ] A.5: Tabla notificacion creada

**Commits:** 
- TBD

---

## Phase B: Backend Recintos CRUD

- [ ] B.1: Recinto.java entidad + validaciones
- [ ] B.2: RecintoService.java con lógica
- [ ] B.3: RecintoResource.java con endpoints
- [ ] B.4: Tests (unit + integration)

**Tests Passing:** 0/X

**Commits:**
- TBD

---

## Phase C: Backend Actas Pendientes & Notificaciones

- [ ] C.1: ActaService modificado (interceptar ENVIADA/APROBADA/EN_DISPUTA)
- [ ] C.2: NotificacionService.java con operaciones CRUD
- [ ] C.3: ActasCoordinatorResource con endpoints
- [ ] C.4: NotificacionResource con endpoints
- [ ] Tests (integration + RLS validation)

**Tests Passing:** 0/X

**RLS Validated:** [ ] Coordinator solo ve sus recintos

**Commits:**
- TBD

---

## Phase D: Frontend Panel Recintos (Admin)

- [ ] D.1: Componentes (list, form, jrvs)
- [ ] D.2: API integration
- [ ] D.3: Menu integration
- [ ] D.4: Tests (form validation, CRUD ops)

**UI Screenshots:** [ ] Created

**Tests Passing:** 0/X

**Commits:**
- TBD

---

## Phase E: Frontend Panel Actas Pendientes (Coordinator)

- [ ] E.1: Componentes (actas-pendientes, acta-card, acta-detalle, rechazo-form)
- [ ] E.2: API integration
- [ ] E.3: Notificaciones (bell component + polling)
- [ ] E.4: Menu integration
- [ ] E.5: Tests (tabs, polling, approve/reject flows)

**Polling Working:** [ ] 5s interval, actas appear

**Notifications Bell:** [ ] Shows badge + dropdown

**Tests Passing:** 0/X

**Commits:**
- TBD

---

## Phase F: Documentation

- [ ] Update AUTHENTICATION.md (COORDINATOR role)
- [ ] Update API_REFERENCE.md (new endpoints)
- [ ] Create RECINTOS_GUIDE.md
- [ ] Create ACTAS_VALIDACION_GUIDE.md

**Commits:**
- TBD

---

## Regressions & Verification

| Feature | Before | After | Status |
|---------|--------|-------|--------|
| Observer registro acta | Funciona | No cambio | ✓ |
| Admin usuarios | Funciona | No cambio | ✓ |
| Configuración geográfica | Funciona | +Recintos | ✓ |
| Login/Auth | Funciona | No cambio | ✓ |
| RLS: Coordinator | N/A | Solo sus recintos | ✓ |
| RLS: Observer | Funciona | No cambio | ✓ |

**Result:** ✓ / ✗

---

## Performance & Load

| Metric | Target | Actual |
|--------|--------|--------|
| GET /coordinator/actas-pendientes | <1s | TBD |
| GET /notifications/pendientes | <500ms | TBD |
| Polling latency | <5s | TBD |
| Notify creation latency | <100ms | TBD |

---

## Sign-Off

**Builder:** _______________  
**Date:** _______________  

**QA:** _______________  
**Date:** _______________  

**Ready for Merge:** [ ] Yes [ ] No

**Merge Commit:** TBD

---

## Post-Merge Next Steps

### Immediate (Phase 1)
- Assign Coordinators to Recintos (via API or manual)
- Monitor polls + notifications in production
- Gather feedback from Coordinators

### Phase 2 (Optional)
- WebSocket instead of polling
- Email notifications for actas pendientes
- Bulk approve/reject for Coordinators
- Acta history/audit log

### Future
- Recinto settings (horarios, contactos)
- Generador de reportes por Recinto
- SLA tracking: tiempo entre ENVIADA → APROBADA

```
