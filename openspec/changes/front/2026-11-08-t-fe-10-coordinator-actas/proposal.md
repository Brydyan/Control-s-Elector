# T-FE-10: Coordinator — Gestión Global de Actas

## 1. Problema

El Coordinator necesita supervisar, revisar y aprobar TODAS las actas del proceso electoral. Actualmente `/coordinator/actas` es un placeholder sin tabla de datos, filtros ni funcionalidad de supervisión.

**Síntomas:**
- Usuario (Coordinator o Master) logueado entra a `/coordinator/actas`
- Ve solo: "Aprobación de Actas · Supervisión, resolución de inconsistencias y aprobación" + botón "Volver al Dashboard"
- Sin tabla de datos global, sin acciones de aprobación/rechazo

**Impacto:**
- 🔴 BLOCKER: Coordinator no puede supervisar ni aprobar actas
- 🔴 Impide cierre del proceso electoral: sin aprobación, los resultados no son válidos
- 🔴 Sin visibilidad de inconsistencias: actas con cuadre incorrecto no se detectan

---

## 2. Propuesta

**Implementar tabla interactiva global de actas supervisadas:**

1. **Backend:** Endpoint `GET /api/v1/coordinator/actas` que retorna TODAS las actas
   - Sin restricción JRV (tabla global)
   - Filtra por estado, JRV, cargo, rango de fechas
   - Roles autorizados: COORDINATOR, MASTER

2. **Frontend:** Componente `ActasListaPage` con tabla filtrable
   - Tabla: #Acta, JRV, Cargo, Estado, Checksum (✓/✗), Acciones
   - Filtros: por JRV, por estado, por cargo, por rango de fechas
   - Búsqueda: por número de JRV o acta
   - Acciones: 
     - APROBAR (estado ENVIADA → APROBADA)
     - DISPUTAR (estado ENVIADA → EN_DISPUTA + motivo)
     - CORREGIR (re-cargar votos tras disputa)
     - ANULAR (MASTER-only, estado APROBADA → ANULADA + motivo)
   - Modal de detalle con evidencia fotográfica y historial
   - Paginación: 10-50 registros por página
   - Responsive: mobile-first (cards apiladas)

3. **Integraciones:**
   - Backend: Endpoints existen (`GET /coordinator/actas`, `PUT /aprobar`, `PUT /disputar`, `PUT /corregir`, `DELETE /anular`)
   - Frontend: Reutiliza tabla de `ODD #505` (Design System)
   - Auth: RBAC Guard (COORDINATOR, MASTER solo)

---

## 3. Criterios de Aceptación

**Funcional:**
- [ ] GET /api/v1/coordinator/actas retorna ≥1 acta con estado
- [ ] Tabla ordena por fecha registrada descendente (más recientes primero)
- [ ] Filtro por estado funciona (ENVIADA, EN_DISPUTA, CORREGIDA, APROBADA, ANULADA)
- [ ] Búsqueda por acta-id y JRV es case-insensitive y parcial
- [ ] Paginación respeta límite backend (max 50 registros/request)
- [ ] Checksum visual: ✓ (verde) si suma = total, ✗ (rojo) si no coincide
- [ ] Botón APROBAR cambia estado a APROBADA (200 OK)
- [ ] Botón DISPUTAR abre modal con campo motivo obligatorio, cambia a EN_DISPUTA
- [ ] Botón CORREGIR abre modal para re-cargar votos, cambia a CORREGIDA
- [ ] MASTER ve botón ANULAR (rojo, peligroso), otros roles lo ven deshabilitado
- [ ] Acta ANULADA muestra motivo en tooltip

**UX/A11y:**
- [ ] 44px targets (botones, checkboxes filtro)
- [ ] ARIA roles: `table`, `rowgroup`, `row`, `cell`
- [ ] Teclado: Tab navega filtros → tabla → acciones
- [ ] Screen reader: describe "1 acta de 156" en resumen
- [ ] Contrast 4.5:1 mínimo (estado: ENVIADA=azul, EN_DISPUTA=naranja, CORREGIDA=amarillo, APROBADA=verde, ANULADA=rojo)

**Técnico:**
- [ ] Componente tests: >70% coverage (tabla, filtros, búsqueda, acciones de aprobación/rechazo)
- [ ] E2E: flujo Coordinator → actas → filtra por estado → aprueba → acta cambia estado
- [ ] Backend tests: endpoint retorna 403 si user no es COORDINATOR/MASTER
- [ ] Performance: tabla <200ms carga inicial + <100ms filtro
- [ ] Conflictos: cuando múltiples coordinators actúan, 409 Conflict dispara recarga automática

**Legal/Compliance:**
- [ ] Auditoría: cada GET, PUT (aprobar, disputar, corregir), DELETE (anular) loguea usuario, IP, timestamp
- [ ] Validación checksum: muestra diferencia en rojo si suma ≠ total
- [ ] Motivo anulación: obligatorio, auditable

---

## 4. Scope

**INCLUIR:**
- Vista tabla global con filtros
- Modales de acciones (APROBAR, DISPUTAR, CORREGIR)
- Detalle expandible con evidencia fotográfica
- Historial de transiciones por acta
- Tests (unit + E2E)
- Accesibilidad WCAG AA
- Manejo de conflictos (409) con reload automático

**EXCLUIR:**
- Reportes analíticos (es T-FE-17: Reportes y Expedientes CNE)
- Gráficos de estado (es T-FE-15: Dashboard Analítico)
- Real-time (es T-FE-11: Supabase Realtime)
- Exportación masiva (post-MVP)

---

## 5. Dependencias

**Requiere:**
- ✅ T-FE-02: Routing con RBAC Guards (ya archived)
- ✅ T-FE-03: AuthService (ya archived)
- ✅ T-FE-04: Interceptors HTTP (ya archived)
- ✅ T-FE-05: Pantalla de Login (ya archived)
- ✅ T-FE-12: Componentes Compartidos (ya archived)
- ✅ Backend: Endpoints `/coordinator/actas*` ya existen

**Bloqueada por:**
- Nada. Puede proceder inmediatamente.

---

## 6. Estimación

**Esfuerzo:** 24-32 horas (3-4 días)

- Authorize + Explore: 2-3h
- Frontend (componente + tabla + modales): 6-8h
- Acciones (APROBAR, DISPUTAR, CORREGIR, ANULAR): 4-5h
- Tests (unit + E2E): 4-5h
- Mutations + compuertas: 3-4h
- UX/A11y audit (/ux-designer, /taste-skill): 2-3h

**Criticidad:** 🔴 **CRÍTICA**
- Sin esta, Coordinator tiene cero visibilidad de actas
- Impide completar el proceso electoral
- Es blocker para MVP

---

## 7. Seguimiento

Referencia roadmap: `/openspec/changes/front/README.md` (T-FE-10)

Una vez completada:
1. `/02-build-sdd openspec/changes/front/2026-11-08-t-fe-10-coordinator-actas/` → implementar
2. `/03-verify-sdd openspec/changes/front/2026-11-08-t-fe-10-coordinator-actas/` → auditar
3. Si PASS: archiva a `openspec/archive/front/2026-11-08-t-fe-10-coordinator-actas-VERIFIED/`
4. Actualiza `openspec/changes/front/README.md` status → "✓ Archived"
