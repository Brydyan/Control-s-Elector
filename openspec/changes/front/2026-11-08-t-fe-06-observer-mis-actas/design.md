# T-FE-06: Design — Observer: Lista de Actas Propias

## Decisiones Arquitectónicas

### D1 — Backend: GET /api/v1/observer/mis-actas vs trayendo todas y filtrando frontend

**Decisión**: Backend filtra y pagina (servidor-driven).

**Implementación:**
```
GET /api/v1/observer/mis-actas?page=0&size=10&estado=TRANSMITIDO&jrvId=123
→ {
  content: [...],
  total: 45,
  page: 0,
  size: 10
}
```

**Por qué:**
- ✅ Escalable: no carga 1000 actas si user solo ve 10
- ✅ Seguridad: servidor filtra, no confía en cliente
- ✅ Performance: <200ms respuesta inicial
- ✅ Índices DB: `(usuario_id, estado, jrv_id, fecha DESC)`

**Alternativa descartada:** Traer todas las actas y filtrar en frontend
- ❌ Inseguro: expone actas que no debería ver
- ❌ Lento: >5s carga si 500 actas
- ❌ Memoria: frontend colapsa con dataset grande

---

### D2 — Tabla vs Cards responsive

**Decisión**: Tabla en desktop (≥768px), cards apiladas en mobile (<768px).

**Implementación:**
```typescript
// Desktop (≥768px)
<table>
  <tr> #ACTA | JRV | FECHA | ESTADO | ACCIÓN </tr>
  <tr> 001   | J-1 | ...   | ✅     | Ver detalles </tr>
</table>

// Mobile (<768px)
<div class="acta-card">
  <div class="acta-header">#001 · J-1 · 15/11/2026</div>
  <div class="acta-estado">✅ Transmitido</div>
  <button>Ver detalles</button>
</div>
```

**Por qué:**
- ✅ Tabla: columnas denso, data-driven desktop
- ✅ Cards: deslizable, readable mobile
- ✅ Mismo componente: @media dentro de template/styles

**Alternativa descartada:** Solo cards
- ❌ Desktop pierde densidad: user ve 2 actas por pantalla (ineficiente)
- ❌ Columnas no alineadas: hard scan rápido

---

### D3 — Filtros: Sidebar fijo vs dropdown modal

**Decisión**: Sidebar fijo (desktop) + collapsible drawer (mobile).

**Implementación:**
```
Desktop:
┌─────────────────────────────┐
│ FILTROS (sidebar fijo)       │ TABLA
│ ─────────────────────        │ ──────
│ ☐ JRV (dropdown)             │
│ ☐ Estado (checkboxes)        │
│ ☐ Fecha (range picker)       │
│ [Limpiar] [Aplicar]          │
└─────────────────────────────┘

Mobile:
┌──────────────────────────┐
│ [☰ Filtros] TABLA        │  ← drawer collapse/expand
│ ──────────────────────── │
│ (tabla)                  │
└──────────────────────────┘
```

**Por qué:**
- ✅ Desktop: controles siempre visibles, user explora sin cerrar
- ✅ Mobile: drawer oculta filtros, maximiza espacio tabla
- ✅ Consistencia: mismo componente, solo visibilidad CSS

**Alternativa descartada:** Modal overlay
- ❌ Desktop: modal interrumpe contexto (user cierra/abre repetidamente)
- ❌ Scroll dentro modal: bad UX en mobile

---

### D4 — Estado visual: colores vs iconos vs texto

**Decisión**: Icono + color + texto (triple redundancia).

**Implementación:**
```typescript
// Design system tokens
estado: 'TRANSMITIDO' → {
  icon: 'checkmark-circle',
  color: 'var(--color-success)',  // verde
  label: '✅ Transmitido',
  ariaLabel: 'Estado: transmitido'
}
```

**Por qué:**
- ✅ Accessible: no solo color (WCAG AA)
- ✅ International: icono universal, no depende idioma
- ✅ Quick scan: color rápido + texto confirma

**Alternativa descartada:** Solo color
- ❌ WCAG AA fail: usuario daltónico no distingue
- ❌ Impresión B/W: se pierde

---

### D5 — Búsqueda: input text vs autocomplete vs faceted search

**Decisión**: Input text (busca por acta-id) + filtros faceted.

**Implementación:**
```typescript
// Search input (búsqueda por número acta)
<input type="text" placeholder="Buscar acta (#001, #042, ...)"
  (input)="onSearch($event)" />

// Filtros faceted (checkboxes + dropdown)
<fieldset>
  <legend>Estado</legend>
  <label><input type="checkbox" value="TRANSMITIDO" /> Transmitido (45)</label>
  <label><input type="checkbox" value="REVISION" /> En revisión (12)</label>
</fieldset>
```

**Por qué:**
- ✅ Buscar simple: user escribe "001" → filtra
- ✅ Faceted: checkboxes muestran conteos (transparency)
- ✅ Independientes: busca + filtros trabajan juntos

**Alternativa descartada:** Autocomplete con API
- ❌ Overkill: dataset pequeño (<100 actas en MVP)
- ❌ Network: llamadas extra cada keystroke
- ❌ Latency: delay visible en 3G

---

## Diagramas

### Flujo Usuario

```
Observer logueado
    ↓
clickea "Mis Actas" en sidebar
    ↓
GET /api/v1/observer/mis-actas?page=0&size=10
    ↓
Tabla aparece con primeras 10 actas (Transmitido + Revisión)
    ↓
Usuario filtra por estado = "En revisión"
    ↓
GET /api/v1/observer/mis-actas?page=0&size=10&estado=REVISION
    ↓
Tabla recarga: 2 actas en revisión
    ↓
Usuario busca "#042"
    ↓
Tabla filtra local: 1 acta (#042)
    ↓
Usuario clickea "Ver detalles" (T-FE-14)
```

### Arquitectura Componentes

```
MisActasPage
├── MisActasFiltersComponent (sidebar)
│   ├── FilterJrvSelect
│   ├── FilterEstadoCheckbox
│   └── FilterFechaRange
├── MisActasTableComponent (tabla)
│   ├── ActaTableRow (desktop)
│   └── ActaCard (mobile)
├── MisActasSearchComponent (search input)
└── PaginationComponent
```

---

## Trampas Documentadas

1. **Estado filtrable vs estado visible**
   - Backend retorna TODOS los estados (Transmitido, Revisión, Borrador, Rechazado)
   - Frontend UI OCULTA Rechazado por privacidad (no mostrar feedback negativo)
   - Filtro backend excluye Rechazado con `estado != 'RECHAZADO'`

2. **Paginación vs infinito scroll**
   - MVP usa paginación (botones Next/Prev) para control explícito
   - Infinito scroll → ODD post-MVP

3. **Búsqueda local vs servidor**
   - Búsqueda por acta-id es LOCAL (re-filtra tabla en memory)
   - Filtros (JRV, estado, fecha) van al servidor (rebuild API request)
   - Reasoning: acta-id pequeño dataset, rápido local

4. **Timezone fechas**
   - Backend: retorna ISO 8601 UTC (`2026-11-08T15:30:00Z`)
   - Frontend: muestra zona horaria del user (`15:30 -05:00 ECT`)
   - Token: Quarkus lanza automaticamente `ZonedDateTime` en response

5. **Actas sin JRV**
   - User MASTER sin JRV asignada: vería sus propias actas (metadata)
   - User OBSERVER sin JRV: 403 Forbidden (no tiene acceso a datos)

---

## Testing Strategy

### Unit Tests (Frontend)
- MisActasTableComponent.renderRows: data → HTML rows
- MisActasFiltersComponent.onEstadoChange: emite evento con filtro correcto
- MisActasSearchComponent.onSearch: filtra tabla local por acta-id

### Unit Tests (Backend)
- MisActasController.findByUser: @Authorized + @Transactional
- MisActasService.applyFilter: SQL query generation + pagination

### E2E Tests (Playwright)
- Observer → Mis Actas → tabla carga → filtra → ve 5 actas
- Search por #001 → tabla muestra 1 acta
- Pagination → Next button → página 2 carga

### Mutations (validar tests caen)
- M1: Remover roleGuard de ruta → test falla (no autorizado)
- M2: Omitir estado filtro backend → test falla (ver actas rechazadas)
- M3: Remover paginación → test falla (carga >50 items)

---

## Performance Targets

| Métrica | Target | Medida |
|---------|--------|--------|
| Carga inicial | <200ms | GET /mis-actas |
| Filtro | <100ms | Re-render tabla |
| Búsqueda local | <50ms | Filter por acta-id |
| Tabla rows | <500 (max) | Limit server side |
| Network | 1 request | Paginar, no reload |

---

## Dark Mode

Token reutiliza paleta SDD #493:
```css
.table-row {
  background: var(--color-surface-primary);
  color: var(--color-text-primary);
}

.estado-transmitido {
  background: var(--color-success-bg);
  color: var(--color-success);
}
```

Validar: `/taste-skill` audita contraste en dark mode (4.5:1 mínimo).
