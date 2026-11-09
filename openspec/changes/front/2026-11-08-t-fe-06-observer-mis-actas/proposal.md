# T-FE-06: Observer — Lista de Actas Propias (Mis Actas)

## 1. Problema

El Observer necesita ver el historial, trazabilidad y estado de sus actas transmitidas. Actualmente `/observer/mis-actas` muestra un `PlaceholderPage` sin datos ni funcionalidad.

**Síntomas:**
- Usuario logueado entra a `/observer/mis-actas`
- Ve solo: "Mis Actas · Historial, trazabilidad y estado de las actas electorales transmitidas." + botón "Volver al Dashboard"
- Sin tabla de datos, filtros, búsqueda

**Impacto:**
- 🔴 BLOCKER: Observer no puede ver su propio trabajo
- 🔴 Reduce confianza: sin visibilidad, no hay trazabilidad de cambios

---

## 2. Propuesta

**Implementar tabla interactiva de actas del usuario:**

1. **Backend:** Endpoint `GET /api/v1/observer/mis-actas` que retorna actas del usuario autenticado
   - Filtra por JRV asignada (si es OBSERVER)
   - MASTER ve todas las actas de su área

2. **Frontend:** Componente `MisActasPage` con tabla filtrable
   - Tabla: #Acta, JRV, Fecha Transmisión, Estado, Acciones
   - Filtros: por JRV, por estado, por rango de fechas
   - Búsqueda: por número de acta
   - Acciones: ver detalles, descargar PDF, reabrir (si aplica)
   - Paginación: 10-50 registros por página
   - Responsive: mobile-first (stack vertical)

3. **Integraciones:**
   - Backend: DB actas (Liquibase migrations si es necesario)
   - API: Coordina con `/api/v1/observer/jrvs` (JRVs del usuario)
   - Frontend: Reutiliza tabla de `ODD #505` (Design System)

---

## 3. Criterios de Aceptación

**Funcional:**
- [ ] GET /api/v1/observer/mis-actas retorna ≥1 acta con estado
- [ ] Tabla ordena por fecha descendente (más reciente primero)
- [ ] Filtro por estado excluye actas rechazadas (solo transmitidas/revisión)
- [ ] Búsqueda por acta-id es case-insensitive y parcial
- [ ] Paginación respeta límite backend (max 50 registros/request)
- [ ] Cada acta muestra: número, JRV, fecha, estado, 1 acción

**UX/A11y:**
- [ ] 44px targets (botones, checkboxes filtro)
- [ ] ARIA roles: `table`, `rowgroup`, `row`, `cell`
- [ ] Teclado: Tab navega filtros → tabla → acciones
- [ ] Screen reader: describe "1 acta de 15" en resumen
- [ ] Contrast 4.5:1 mínimo (estado: Borrador=amarillo, Transmitido=verde, Rechazado=rojo)

**Técnico:**
- [ ] Componente tests: >70% coverage (tabla, filtros, búsqueda, acciones)
- [ ] E2E: flujo Observer → mis-actas → filtra por estado → ve 5 actas
- [ ] Backend tests: endpoint retorna 403 si usuario sin JRV asignada
- [ ] Performance: tabla <200ms carga inicial + <100ms filtro

**Legal/Compliance:**
- [ ] Actas rechazadas NO se muestran (privacidad: evita ver feedback negativo público)
- [ ] Auditoría: GET log cada consulta (quién vio qué actas cuándo)

---

## 4. Scope

**INCLUIR:**
- Vista tabla con filtros
- Endpoint backend + DB schema (si es necesario)
- Tests (unit + E2E)
- Accesibilidad WCAG AA

**EXCLUIR:**
- Edición de actas (es T-FE-14)
- Importación/exportación masiva (post-MVP)
- Gráficos de estado (es T-FE-15: Dashboard Analítico)
- Real-time (es T-FE-11: Supabase Realtime)

---

## 5. Dependencias

**Requiere:**
- ✅ T-FE-02: Routing con RBAC Guards (ya archived)
- ✅ T-FE-03: AuthService (ya archived)
- ✅ T-FE-04: Interceptors HTTP (ya archived)
- ✅ T-FE-05: Pantalla de Login (ya archived)
- ✅ SDD #492: User Invitations + SMTP (ya archived)
- 🟡 ODD #505: Design System Completion (Toast, table styles, A11y tests) — paralelo
- 🔵 Backend: Endpoint `/coordinator/actas` must exist (check if available)

**Bloqueada por:**
- Nada. Puede proceder inmediatamente.

---

## 6. Estimación

**Esfuerzo:** 3-4 días (24-32 horas)

- Authorize + Explore: 2-3h
- Backend (API + schema): 4-5h
- Frontend (componente + tabla): 5-6h
- Tests (unit + E2E): 4-5h
- Mutations + compuertas: 3-4h
- UX/A11y audit (/ux-designer, /taste-skill): 2-3h

**Criticidad:** 🟡 **ALTA**
- Sin esta, Observer tiene cero visibilidad de su trabajo
- Es tabla de datos core para MVP (feature-blocker para Observer UX)

---

## 7. Seguimiento

Referencia roadmap: `/openspec/changes/front/README.md` (T-FE-06)

Una vez completada:
1. `/02-build-sdd openspec/changes/front/2026-11-08-t-fe-06-observer-mis-actas/` → implementar
2. `/03-verify-sdd openspec/changes/front/2026-11-08-t-fe-06-observer-mis-actas/` → auditar
3. Si PASS: archiva a `openspec/archive/front/2026-11-08-t-fe-06-observer-mis-actas-VERIFIED/`
4. Actualiza `openspec/changes/front/README.md` status → "✓ Archived"
