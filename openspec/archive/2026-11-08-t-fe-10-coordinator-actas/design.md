# T-FE-10: Design — Coordinator: Gestión Global de Actas

## Decisiones Arquitectónicas

### D1 — Backend: GET /api/v1/coordinator/actas — Tabla Global vs Restringida por JRV

**Decisión**: Tabla global (sin restricción JRV). Backend filtra por rol, Frontend NO impone restricciones.

**Implementación:**
```
GET /api/v1/coordinator/actas?page=0&size=10&estado=ENVIADA&jrvId=123&cargoId=456
→ {
  content: [...(TODAS las JRVs, no solo del user)...],
  total: 1250,
  page: 0,
  size: 10
}
```

**Por qué:**
- ✅ Escalable: Coordinator supervisa el TODO el proceso, no solo su JRV
- ✅ Seguridad: Backend valida rol (COORDINATOR, MASTER), no confía en params
- ✅ Performance: <200ms respuesta inicial con índices `(estado, fecha DESC)`
- ✅ Auditoria: cada GET loguea usuario, IP, filtros aplicados
- ✅ Conflictos: 409 cuando otro Coordinator aprueba la misma acta → reload automático

**Alternativa descartada:** Tabla restringida a JRV del Coordinator
- ❌ Silos de datos: Coordinator1 no ve actas JRV2 (imposible coordinar globalmente)
- ❌ Duplica Observers: repetir vista de cada JRV (ineficiente)

---

### D2 — Tabla vs Cards responsive

**Decisión**: Tabla en desktop (≥768px), cards apiladas en mobile (<768px).

**Implementación:**
```typescript
// Desktop (≥768px)
<table>
  <tr> #ACTA | JRV | CARGO | ESTADO | CHECKSUM | ACCIONES </tr>
  <tr> 001   | J-1 | Pres  | ✅ Env | ✓ OK     | [Aprobar] </tr>
</table>

// Mobile (<768px)
<div class="acta-card">
  <div class="acta-header">#001 · J-1 · Presidencia</div>
  <div class="acta-meta">ENVIADA · 15/11/2026 14:30</div>
  <div class="acta-checksum">✓ OK (250/250)</div>
  <button>Acciones</button>  ← dropdown menú
</div>
```

**Por qué:**
- ✅ Tabla: columnas denso en desktop, data-driven, scanneable rápido
- ✅ Cards: deslizable en mobile, readable, acciones en dropdown
- ✅ Mismo componente: @media dentro de template/styles

**Alternativa descartada:** Solo cards
- ❌ Desktop pierde densidad: Coordinator ve 2 actas por pantalla (ineficiente en 1250)
- ❌ Columnas no alineadas: hard scan rápido de estado

---

### D3 — Filtros: Sidebar Fijo vs Drawer Collapsible

**Decisión**: Sidebar fijo (desktop) + drawer collapsible (mobile).

**Implementación:**
```
Desktop:
┌─────────────────────────────┐
│ FILTROS (sidebar fijo)       │ TABLA
│ ─────────────────────        │ ──────
│ ☐ JRV (dropdown/multi)       │
│ ☐ Estado (checkboxes)        │
│ ☐ Cargo (dropdown)           │
│ ☐ Fecha (range picker)       │
│ [Limpiar] [Aplicar]          │
└─────────────────────────────┘

Mobile:
┌──────────────────────────┐
│ [☰ Filtros] TABLA        │  ← drawer collapse/expand
│ ──────────────────────── │
│ (tabla/cards)            │
└──────────────────────────┘
```

**Por qué:**
- ✅ Desktop: controles siempre visibles, Coordinator explora sin cerrar
- ✅ Mobile: drawer oculta filtros, maximiza espacio tabla
- ✅ Consistencia: mismo componente, solo visibilidad CSS

**Alternativa descartada:** Modal overlay
- ❌ Desktop: modal interrumpe contexto (Coordinator cierra/abre repetidamente)
- ❌ Scroll dentro modal: bad UX en mobile

---

### D4 — Estados Visuales: Colores + Iconos + Texto

**Decisión**: Icono + color + texto + tooltip (cuádruple redundancia para claridad).

**Implementación:**
```typescript
// Design system tokens
estado: 'ENVIADA' → {
  icon: 'paperplane-outline',
  color: 'var(--color-info)',      // azul
  label: '📮 Enviada',
  ariaLabel: 'Estado: enviada',
  tooltip: 'Esperando supervisión del Coordinator'
}

estado: 'EN_DISPUTA' → {
  icon: 'warning-outline',
  color: 'var(--color-warning)',    // naranja
  label: '⚠️ En Disputa',
  tooltip: 'Acta con inconsistencias detectadas'
}

estado: 'CORREGIDA' → {
  icon: 'checkmark-outline',
  color: 'var(--color-success)',    // verde
  label: '✓ Corregida',
  tooltip: 'Votos re-cargados, lista para aprobación'
}

estado: 'APROBADA' → {
  icon: 'checkmark-circle-outline',
  color: 'var(--color-success)',    // verde oscuro
  label: '✓✓ Aprobada',
  tooltip: 'Acta verificada y aprobada'
}

estado: 'ANULADA' → {
  icon: 'close-circle-outline',
  color: 'var(--color-danger)',     // rojo
  label: '✗ Anulada',
  tooltip: `Anulada por: ${motivo}`
}
```

**Por qué:**
- ✅ Accessible: no solo color (WCAG AA, daltónicos)
- ✅ Internacional: icono universal
- ✅ Quick scan: color rápido + tooltip aclara
- ✅ Mobile: tooltip useful en hover de cards

**Alternativa descartada:** Solo color
- ❌ WCAG AA fail: usuario daltónico no distingue ENVIADA (azul) de EN_DISPUTA (rojo)

---

### D5 — Búsqueda: Input Text vs Autocomplete vs Faceted

**Decisión**: Input text (búsqueda por acta-id / JRV) + filtros faceted (estado, cargo, fechas).

**Implementación:**
```typescript
// Search input (búsqueda por número acta o JRV)
<input type="text" placeholder="Buscar acta (#001) o JRV (J-1)"
  (input)="onSearch($event)" />

// Filtros faceted (checkboxes + dropdown)
<fieldset>
  <legend>Estado</legend>
  <label><input type="checkbox" value="ENVIADA" /> Enviadas (345)</label>
  <label><input type="checkbox" value="EN_DISPUTA" /> En Disputa (23)</label>
  <label><input type="checkbox" value="APROBADA" /> Aprobadas (892)</label>
</fieldset>

<fieldset>
  <legend>Cargo</legend>
  <select>
    <option value="">Todos los Cargos</option>
    <option value="1">Presidencia (120)</option>
    <option value="2">Vicepresidencia (120)</option>
  </select>
</fieldset>
```

**Por qué:**
- ✅ Búsqueda rápida: user escribe "J-1" → filtra tabla global por JRV
- ✅ Faceted: checkboxes muestran conteos (transparency, explora sin API)
- ✅ Independientes: búsqueda + filtros trabajan juntos sin bloquear

**Alternativa descartada:** Autocomplete con API
- ❌ Dataset grande (1250 actas): autocomplete lento en 3G
- ❌ Network churn: llamadas cada keystroke

---

### D6 — Acciones: Modal Confirmar vs Inline vs Dropdown

**Decisión**: Dropdown menú en tabla + modal de confirmación con detalles.

**Implementación:**
```typescript
// Desktop: dropdown menú (right-click o icono ⋮)
<menu>
  <li [click]="abrirModalAprobar()">✓ Aprobar</li>
  <li [click]="abrirModalDisputar()">⚠️ Disputar + Motivo</li>
  <li [click]="abrirModalCorregir()">📝 Corregir Votos</li>
  <li [click]="abrirModalAnular()" [disabled]="!esMaster">
    ✗ Anular (MASTER)
  </li>
</menu>

// Modal APROBAR
Confirmation Modal:
┌─────────────────────────────┐
│ ✓ Confirmar Aprobación      │
├─────────────────────────────┤
│ Acta #001 (Presidencia)     │
│ JRV J-1 · 15/11/2026 14:30  │
│ Checksum: ✓ OK (250/250)    │
│                             │
│ [Cancelar]  [Aprobar]       │
└─────────────────────────────┘
```

**Por qué:**
- ✅ Dropdown: acceso rápido sin abrir modal
- ✅ Modal: permite ver detalles ANTES de confirmar (no impulso)
- ✅ Consistency: mismo patrón en Observer (T-FE-06)

**Alternativa descartada:** Inline buttons en cada fila
- ❌ Tabla cluttered: 4+ botones por fila (caos visual)
- ❌ Móvil: botones tiny, hard target

---

### D7 — Conflictos (409 Conflict): Reload vs Merge vs Reject

**Decisión**: Reload automático + notificación visual.

**Implementación:**
```typescript
coordinatorService.aprobarActa(actaId).subscribe({
  next: () => {
    // ✓ 200 OK: acta APROBADA
    handleActionSuccess('Acta APROBADA');
    cargarActas();  // reload tabla
  },
  error: (err) => {
    if (err.status === 409) {
      // Conflict: otro Coordinator ya aprobó
      handleConflict('Otro Coordinator ya aprobó esta acta');
      cargarActas();  // reload automático para reflejar estado real
    }
  }
});
```

**Por qué:**
- ✅ Transparencia: user ve que acción falló (no silenciosamente ignora)
- ✅ Fuente de verdad: backend siempre correcto, frontend recarga
- ✅ Previene duplicados: 409 impide 2 aprobaciones de la misma acta
- ✅ Consistencia: mismo patrón en T-FE-06 (Observer)

**Alternativa descartada:** Merge optimista (assume success)
- ❌ Inseguro: state mismatch si 409 ocurre
- ❌ Auditoría: perderías qué Coordinator realmente aprobó

---

## Diagramas

### Flujo Usuario — Aprobar Acta

```
Coordinator logueado
    ↓
clickea "Actas para Aprobación" en sidebar
    ↓
GET /api/v1/coordinator/actas?page=0&size=10
    ↓
Tabla aparece con primeras 10 actas (estado ENVIADA)
    ↓
Coordinator filtra por JRV = "J-1"
    ↓
GET /api/v1/coordinator/actas?page=0&size=10&jrvId=1
    ↓
Tabla recarga: 3 actas de JRV-1
    ↓
Coordinator clickea menú ⋮ en acta #001
    ↓
Dropdown: [✓ Aprobar | ⚠️ Disputar | 📝 Corregir | ✗ Anular (disabled)]
    ↓
Coordinator clickea "✓ Aprobar"
    ↓
Modal CONFIRMATION:
  Acta #001 · Presidencia
  Checksum ✓ OK (250/250)
  [Cancelar] [Aprobar]
    ↓
Coordinator clickea [Aprobar]
    ↓
PUT /api/v1/coordinator/actas/001/aprobar
    ↓
200 OK: "Acta APROBADA exitosamente"
    ↓
GET /api/v1/coordinator/actas?page=0&size=10  (reload tabla)
    ↓
Tabla recarga: acta #001 ahora estado APROBADA (verde ✓✓)
```

### Flujo Usuario — Disputar Acta (Checksum Incorrecto)

```
Coordinator ve acta #005 con checksum ROJO ✗ INCORRECTO
    ↓
Clickea menú ⋮ → "⚠️ Disputar"
    ↓
Modal DISPUTA:
  ┌─────────────────────────────────┐
  │ ⚠️ Poner en Disputa             │
  ├─────────────────────────────────┤
  │ Acta #005 (Cargo: Alcalde)      │
  │ JRV J-3 · 15/11/2026 16:45      │
  │ Checksum: ✗ INCORRECTO (250/251)│
  │                                 │
  │ Motivo (obligatorio):           │
  │ [textarea] "Diferencia de 1 voto│
  │            en cómputo de blancos]
  │                                 │
  │ [Cancelar] [Disputar]           │
  └─────────────────────────────────┘
    ↓
Coordinator tipea motivo + clickea [Disputar]
    ↓
PUT /api/v1/coordinator/actas/005/disputar
  { "motivo": "Diferencia de 1 voto en cómputo de blancos" }
    ↓
200 OK: "Acta puesta EN DISPUTA"
    ↓
GET /api/v1/coordinator/actas (reload tabla)
    ↓
Acta #005 ahora estado EN_DISPUTA (naranja ⚠️)
    ↓
Observer recibe notificación: "Acta #005 en disputa, motivo: ..."
    ↓
Observer navega a acta → Modal CORREGIR → carga votos → submite
    ↓
200 OK: "Acta CORREGIDA"
    ↓
Tabla Coordinator: acta #005 ahora estado CORREGIDA (amarillo ✓)
    ↓
Coordinator revisa corrección → clickea [✓ Aprobar]
    ↓
PUT /api/v1/coordinator/actas/005/aprobar
    ↓
200 OK: "Acta APROBADA"
```

### Arquitectura Componentes

```
ActasListaPage
├── ActasFiltersComponent (sidebar)
│   ├── FilterJrvSelect
│   ├── FilterEstadoCheckbox
│   ├── FilterCargoSelect
│   └── FilterFechaRange
├── ActasTableComponent (tabla desktop)
│   ├── ActaTableRow (con dropdown menú)
│   └── AccionesDropdown
├── ActasCardsComponent (cards mobile)
│   └── ActaCard
├── PaginationComponent
└── Modales de Acción
    ├── ModalAprobarComponent
    ├── ModalDisputarComponent
    ├── ModalCorregirComponent
    ├── ModalAnularComponent (MASTER-only)
    └── ModalDetalleComponent (detalle + historial)
```

---

## Trampas Documentadas

1. **Estado filtrable vs visible vs accionable**
   - Backend retorna TODOS los estados (ENVIADA, EN_DISPUTA, CORREGIDA, APROBADA, ANULADA)
   - Frontend OCULTA ANULADA por defecto (filtro checkbox-able para auditoría)
   - Solo Coordinator puede DISPUTAR si estado=ENVIADA
   - Solo Coordinator puede CORREGIR si estado=EN_DISPUTA
   - Solo Coordinator puede APROBAR si estado=CORREGIDA o ENVIADA (sin disputa)
   - Solo MASTER puede ANULAR si estado=APROBADA

2. **Paginación vs infinito scroll**
   - MVP usa paginación (botones Next/Prev) para control explícito
   - Infinito scroll → ODD post-MVP

3. **Búsqueda local vs servidor**
   - Búsqueda por acta-id / JRV es LOCAL (re-filtra tabla en memory de página actual)
   - Filtros (estado, cargo, fecha) van al servidor (rebuild API request)
   - Reasoning: dataset ~100 items por página, rápido local

4. **Timezone fechas**
   - Backend: retorna ISO 8601 UTC (`2026-11-08T15:30:00Z`)
   - Frontend: muestra zona horaria del user (`15:30 -05:00 ECT`)
   - Token: Quarkus lanza automaticamente `ZonedDateTime` en response

5. **Acta sin JRV**
   - Theoretically acta sin JRV no debería existir (constraint DB)
   - Si existe: mostrar como `JRV #???` en rojo, alert admin

6. **Checksum válido vs incorrecto**
   - Válido: suma(votos candidatos) + votos_blancos + votos_nulos = total_sufragantes
   - Incorrecto: diferencia > 0
   - Visual: ✓ verde si OK, ✗ rojo si incorrecto + tooltip con diferencia
   - Tooltip: "Diferencia de 5 votos (249/250)"

7. **409 Conflict Race Condition**
   - Escenario: Coordinator1 y Coordinator2 aprueban la misma acta concurrentemente
   - Coordinator2 obtiene 409: "Acta ya aprobada por otro usuario"
   - Solución: reload automático tabla para reflejar estado real
   - Logging: ambas transacciones auditadas (quién ganó timestamp)

---

## Testing Strategy

### Unit Tests (Frontend)
- ActasTableComponent.renderRows: data → HTML rows con checksum correcto
- ActasFiltersComponent.onEstadoChange: emite evento con filtro correcto
- ActasSearchComponent.onSearch: filtra tabla local por acta-id
- ModalAprobarComponent.confirmar: llama service.aprobar() con actaId correcto
- ModalDisputarComponent.confirmar: valida motivo obligatorio antes de submit

### Unit Tests (Backend)
- ActasController.findAll: @Authorized (COORDINATOR, MASTER) + @Transactional
- ActasService.applyFilter: SQL query generation + pagination
- ActasService.aprobar: valida estado=ENVIADA antes de cambiar, registra audit
- ActasService.disputar: valida motivo, registra audit
- ActasService.anular: solo MASTER, valida estado=APROBADA, registra audit

### E2E Tests (Playwright)
- Coordinator → Actas → tabla carga → filtra por JRV → ve 5 actas
- Approva acta → modal confirma → estado cambia a APROBADA en tabla
- Disputa acta → modal pide motivo → estado cambia a EN_DISPUTA
- 409 Conflict: 2 coordinators aprueban concurrentemente → segundo obtiene error + tabla recarga

### Mutations (validar tests caen)
- M1: Remover COORDINATOR role check → test falla (no autorizado)
- M2: Omitir estado filtro backend → test falla (ver actas ANULADA por defecto)
- M3: Remover paginación → test falla (carga >50 items, memoria)
- M4: Omitir 409 conflict handling → test falla (state mismatch)
- M5: Remover validación motivo en DISPUTAR → test falla (submit sin motivo)

---

## Performance Targets

| Métrica | Target | Medida |
|---------|--------|--------|
| Carga inicial | <200ms | GET /coordinator/actas |
| Filtro | <100ms | Re-render tabla |
| Búsqueda local | <50ms | Filter por acta-id |
| Tabla rows | <500 (max) | Limit server side |
| Network | 1 request | Paginar, no reload |
| Aprobar/Disputar | <300ms | PUT endpoint |

---

## Dark Mode

Token reutiliza paleta SDD #493:
```css
.table-row {
  background: var(--color-surface-primary);
  color: var(--color-text-primary);
}

.estado-enviada {
  background: var(--color-info-bg);
  color: var(--color-info);
}

.checksum-valido {
  background: var(--color-success-bg);
  color: var(--color-success);
}

.checksum-invalido {
  background: var(--color-danger-bg);
  color: var(--color-danger);
}
```

Validar: `/taste-skill` audita contraste en dark mode (4.5:1 mínimo).
