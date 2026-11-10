# T-FE-10: Tasks — Coordinator: Gestión Global de Actas

## Resumen Ejecutivo

**Objetivo:** Implementar tabla funcional `/coordinator/actas` con supervisión, aprobación y gestión de actas globales.

**Fases:** A (Backend API check) → B (Frontend componentes) → C (Tests) → D (A11y+UX) → E (Mutations)

**Tiempo estimado:** 24-32 horas (3-4 días)

**Compuertas pre-start:** 
- Backend endpoints `/coordinator/actas*` deben existir y ser funcionales
- CoordinatorApi must expose getActas(), aprobarActa(), disputarActa(), etc.

---

## FASE A: Backend API — Verificación de Endpoints

### A.1 — GET /api/v1/coordinator/actas — Tabla global

**Aceptación:**
```java
GET /api/v1/coordinator/actas?page=0&size=10&estado=ENVIADA&jrvId=1&cargoId=2
→ 200 OK
{
  "content": [
    {
      "id": 1,
      "jrvId": 1,
      "numeroJrv": "J-1",
      "cargoId": 2,
      "nombreCargo": "Presidencia",
      "estado": "ENVIADA",
      "totalSufragantes": 250,
      "votosBlancos": 5,
      "votosNulos": 10,
      "totalVotosCandidatos": 235,
      "fechaRegistro": "2026-11-15T14:30:00Z",
      "urlEvidencia": "https://...",
      "motivoDisputa": null,
      "motivoAnulacion": null
    }
  ],
  "meta": {
    "totalElements": 156,
    "totalPages": 16,
    "page": 0,
    "size": 10
  }
}
```

**Checklist:**
- [ ] Endpoint existe en backend (CoordinatorActasController)
- [ ] Retorna ≥1 acta con estructura correcta
- [ ] Soporta filtros: estado, jrvId, cargoId, search, fechaDesde, fechaHasta
- [ ] Retorna paginación con meta (totalElements, totalPages, page, size)
- [ ] Máximo 50 registros por request
- [ ] Requiere rol COORDINATOR o MASTER
- [ ] 200 OK si usuario autorizado, 403 si no
- [ ] Status: **[ ]** (verificar)

---

### A.2 — PUT /api/v1/coordinator/actas/{actaId}/aprobar

**Aceptación:**
```java
PUT /api/v1/coordinator/actas/1/aprobar
→ 200 OK
{
  "id": 1,
  "estado": "APROBADA",
  "fechaAprobacion": "2026-11-15T16:00:00Z",
  "aprobadoPor": "coordinator@ejemplo.com",
  ...
}
```

**Checklist:**
- [ ] Endpoint existe
- [ ] Valida estado=ENVIADA o CORREGIDA antes de aprobar
- [ ] Cambia estado → APROBADA
- [ ] Retorna 200 OK con acta actualizada
- [ ] Retorna 409 CONFLICT si ya fue aprobado por otro
- [ ] Retorna 400 BAD_REQUEST si estado no permite aprobación
- [ ] Registra auditoría: quién aprobó, cuándo
- [ ] Status: **[ ]** (verificar)

---

### A.3 — PUT /api/v1/coordinator/actas/{actaId}/disputar

**Aceptación:**
```java
PUT /api/v1/coordinator/actas/1/disputar
{
  "motivo": "Diferencia de 1 voto en blancos"
}
→ 200 OK
{
  "id": 1,
  "estado": "EN_DISPUTA",
  "motivoDisputa": "Diferencia de 1 voto en blancos",
  ...
}
```

**Checklist:**
- [ ] Endpoint existe
- [ ] Valida motivo es no-empty
- [ ] Valida estado=ENVIADA antes de disputar
- [ ] Cambia estado → EN_DISPUTA
- [ ] Almacena motivoDisputa
- [ ] Retorna 200 OK
- [ ] Registra auditoría
- [ ] Status: **[ ]** (verificar)

---

### A.4 — PUT /api/v1/coordinator/actas/{actaId}/corregir

**Aceptación:**
```java
PUT /api/v1/coordinator/actas/1/corregir
{
  "jrvId": 1,
  "cargoId": 2,
  "totalSufragantes": 250,
  "votosBlancos": 5,
  "votosNulos": 10,
  "detalles": [
    { "candidatoId": 1, "votos": 120 },
    { "candidatoId": 2, "votos": 80 },
    { "candidatoId": 3, "votos": 35 }
  ]
}
→ 200 OK
{
  "id": 1,
  "estado": "CORREGIDA",
  "totalSufragantes": 250,
  ...
}
```

**Checklist:**
- [ ] Endpoint existe
- [ ] Valida estado=EN_DISPUTA antes de corregir
- [ ] Valida checksum: suma(votos candidatos) + blancos + nulos = sufragantes
- [ ] Cambia estado → CORREGIDA
- [ ] Actualiza votos en BD
- [ ] Retorna 200 OK
- [ ] Registra auditoría
- [ ] Status: **[ ]** (verificar)

---

### A.5 — DELETE /api/v1/coordinator/actas/{actaId} (ANULAR)

**Aceptación:**
```java
DELETE /api/v1/coordinator/actas/1
{
  "motivo": "Datos verificados como fraudulentos"
}
→ 204 NO CONTENT
```

**Checklist:**
- [ ] Endpoint existe
- [ ] Requiere rol MASTER (no COORDINATOR)
- [ ] Valida estado=APROBADA antes de anular
- [ ] Almacena motivoAnulacion
- [ ] Cambia estado → ANULADA (soft-delete, no borra fila)
- [ ] Retorna 204 NO CONTENT
- [ ] Registra auditoría: quién anulóó, cuándo, motivo
- [ ] Status: **[ ]** (verificar)

---

### A.6 — GET /api/v1/coordinator/actas/{actaId}/historial

**Aceptación:**
```java
GET /api/v1/coordinator/actas/1/historial
→ 200 OK
[
  {
    "timestamp": "2026-11-15T14:00:00Z",
    "usuario": "observer@ejemplo.com",
    "accion": "REGISTRADA",
    "deEstado": null,
    "aEstado": "ENVIADA",
    "detalles": "Acta registrada por observer"
  },
  {
    "timestamp": "2026-11-15T15:30:00Z",
    "usuario": "coordinator@ejemplo.com",
    "accion": "DISPUTADA",
    "deEstado": "ENVIADA",
    "aEstado": "EN_DISPUTA",
    "detalles": "Motivo: Diferencia de 1 voto"
  },
  {
    "timestamp": "2026-11-15T16:00:00Z",
    "usuario": "observer@ejemplo.com",
    "accion": "CORREGIDA",
    "deEstado": "EN_DISPUTA",
    "aEstado": "CORREGIDA",
    "detalles": "Votos ajustados: +1 a candidato 1"
  }
]
```

**Checklist:**
- [ ] Endpoint existe
- [ ] Retorna historial completo de transiciones
- [ ] Cada entrada: timestamp, usuario, acción, de/a estado, detalles
- [ ] Ordenado por timestamp ascendente (antiguo → reciente)
- [ ] Retorna 200 OK
- [ ] Status: **[ ]** (verificar)

---

## FASE B: Frontend — Componentes e Interfaces

### B.1 — ActasListaPage (contenedor principal)

**Aceptación:**
```typescript
@Component({
  selector: 'app-actas-lista',
  templateUrl: './actas-lista.page.html',
  styleUrls: ['./actas-lista.page.scss'],
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    ActasFiltersComponent,
    ActasTableComponent,
    ActasCardsComponent,
    PaginationComponent,
    ModalAprobarComponent,
    ModalDisputarComponent,
    ModalCorregirComponent,
    ModalAnularComponent,
  ]
})
export class ActasListaPage implements OnInit, OnDestroy {
  // Signals
  readonly actas = signal<ActaItem[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  
  // Filtros
  estadoSeleccionado: EstadoActa | 'TODAS' = 'TODAS';
  jrvSeleccionada: number | null = null;
  cargoSeleccionado: number | null = null;
  terminoBusqueda = '';
  
  // Paginación
  page = 0;
  size = 10;
  readonly totalElements = signal(0);
  readonly totalPages = signal(0);
  
  // Modales de acción
  modalAprobarAbierto = false;
  modalDisputarAbierto = false;
  modalCorregirAbierto = false;
  modalAnularAbierto = false;
  
  // Datos de acción en progreso
  actaEnAccion: ActaItem | null = null;
  
  // Constructor e inyecciones
  coordinatorService = inject(CoordinatorApi);
  router = inject(Router);
  authStore = inject(AuthStore);
  
  // Métodos
  ngOnInit(): void { ... cargarActas() ... }
  ngOnDestroy(): void { ... cleanup ... }
  cargarActas(): void { ... GET /coordinator/actas ... }
  onFiltrosChange(filtros: ActaFiltros): void { ... }
  onSearch(query: string): void { ... }
  onPageChange(page: number): void { ... }
  
  // Modales
  abrirModalAprobar(acta: ActaItem): void { ... }
  confirmarAprobar(): void { ... PUT /aprobar ... }
  abrirModalDisputar(acta: ActaItem): void { ... }
  confirmarDisputa(): void { ... PUT /disputar ... }
  abrirModalCorregir(acta: ActaItem): void { ... }
  confirmarCorrecion(): void { ... PUT /corregir ... }
  abrirModalAnular(acta: ActaItem): void { ... }
  confirmarAnulacion(): void { ... DELETE / ... }
}
```

**Checklist:**
- [ ] Componente carga actas en ngOnInit
- [ ] Signals: actas[], loading, error
- [ ] Filtros: estado, jrv, cargo, búsqueda
- [ ] Paginación: page, size, totalElements, totalPages
- [ ] Modales: aprobar, disputar, corregir, anular
- [ ] Manejo de errores: 403 redirige, 409 recarga tabla
- [ ] Status: **[ ]** (implementar)

---

### B.2 — ActasFiltersComponent (sidebar / drawer)

**Aceptación:**
```typescript
@Component({
  selector: 'app-actas-filters',
  templateUrl: './actas-filters.component.html',
  standalone: true,
})
export class ActasFiltersComponent {
  @Output() filtrosChange = new EventEmitter<ActaFiltros>();
  
  readonly cargos = signal<CargoResumen[]>([]);
  readonly cantones = signal<CantonResumen[]>([]);
  readonly jrvs = signal<JrvResumen[]>([]);
  
  estadoSeleccionado: EstadoActa | 'TODAS' = 'TODAS';
  jrvSeleccionada: number | null = null;
  cargoSeleccionado: number | null = null;
  
  tabsEstado = [
    { estado: 'TODAS', label: 'Todas' },
    { estado: 'ENVIADA', label: 'Enviadas' },
    { estado: 'EN_DISPUTA', label: 'En Disputa' },
    { estado: 'CORREGIDA', label: 'Corregidas' },
    { estado: 'APROBADA', label: 'Aprobadas' },
  ];
  
  onEstadoChange(estado: EstadoActa | 'TODAS'): void { ... emit ... }
  onJrvChange(jrvId: number | null): void { ... emit ... }
  onCargoChange(cargoId: number | null): void { ... emit ... }
  limpiarFiltros(): void { ... reset + emit ... }
}
```

**Checklist:**
- [ ] Carga cargos, cantones, JRVs
- [ ] Tabs para estados (TODAS, ENVIADA, EN_DISPUTA, CORREGIDA, APROBADA)
- [ ] Dropdown para JRV, cargo
- [ ] Botones [Limpiar] y [Aplicar]
- [ ] Emite evento al cambiar filtro
- [ ] Status: **[ ]** (implementar)

---

### B.3 — ActasTableComponent (tabla responsive)

**Aceptación:**
```html
<!-- Desktop -->
<table>
  <thead>
    <tr>
      <th>#ACTA</th>
      <th>JRV</th>
      <th>CARGO</th>
      <th>ESTADO</th>
      <th>CHECKSUM</th>
      <th>ACCIONES</th>
    </tr>
  </thead>
  <tbody>
    <tr *ngFor="let acta of actas()">
      <td>{{ acta.id }}</td>
      <td>{{ acta.numeroJrv }}</td>
      <td>{{ acta.nombreCargo }}</td>
      <td>
        <app-badge [variant]="acta.estado">
          {{ obtenerEstadoLabel(acta.estado) }}
        </app-badge>
      </td>
      <td>
        <span [ngClass]="checksumValido(acta) ? 'checksum-ok' : 'checksum-error'">
          {{ checksumTexto(acta) }}
        </span>
      </td>
      <td>
        <button (click)="abrirMenu(acta)">⋮</button>
        <menu *ngIf="menuAbierto === acta.id">
          <li (click)="onAprobar.emit(acta)">✓ Aprobar</li>
          <li (click)="onDisputar.emit(acta)">⚠️ Disputar</li>
          <li (click)="onCorregir.emit(acta)">📝 Corregir</li>
          <li *ngIf="esMaster() && acta.estado === 'APROBADA'"
              (click)="onAnular.emit(acta)">✗ Anular</li>
        </menu>
      </td>
    </tr>
  </tbody>
</table>

<!-- Mobile -->
<div class="acta-card" *ngFor="let acta of actas()">
  <div class="acta-header">
    #{{ acta.id }} · {{ acta.numeroJrv }} · {{ acta.nombreCargo }}
  </div>
  <div class="acta-meta">
    {{ acta.estado }} · {{ acta.fechaRegistro | date }}
  </div>
  <div class="acta-checksum">
    {{ checksumTexto(acta) }}
  </div>
  <button (click)="abrirMenu(acta)">Acciones</button>
</div>
```

**Checklist:**
- [ ] Tabla desktop: #ACTA | JRV | CARGO | ESTADO | CHECKSUM | ACCIONES
- [ ] Cards mobile: apiladas, info resumida
- [ ] Estado visual: icono + color + texto
- [ ] Checksum: ✓ (verde) o ✗ (rojo) + tooltip con diferencia
- [ ] Dropdown menú ⋮ con acciones contextuales
- [ ] Outputs: onAprobar, onDisputar, onCorregir, onAnular
- [ ] Status: **[ ]** (implementar)

---

### B.4 — Modales de Acción (4 componentes)

#### ModalAprobarComponent

**Checklist:**
- [ ] Muestra acta, JRV, cargo, checksum
- [ ] Buttons: [Cancelar] [Aprobar]
- [ ] Input Output: acta, approve event
- [ ] Validación: estado=ENVIADA o CORREGIDA
- [ ] Error handling: 409 conflict, 400 bad state
- [ ] Status: **[ ]** (implementar)

#### ModalDisputarComponent

**Checklist:**
- [ ] Textarea: motivo (obligatorio)
- [ ] Validación: motivo no-empty
- [ ] Buttons: [Cancelar] [Disputar]
- [ ] Error si motivo vacío
- [ ] Emite evento con motivo
- [ ] Status: **[ ]** (implementar)

#### ModalCorregirComponent

**Checklist:**
- [ ] Tabla de candidatos con votos originales + corregidos
- [ ] Inputs: sufragantes, blancos, nulos
- [ ] Validación: suma debe = sufragantes
- [ ] Mostrar checksum actualizado en tiempo real
- [ ] Buttons: [Cancelar] [Corregir]
- [ ] Status: **[ ]** (implementar)

#### ModalAnularComponent

**Checklist:**
- [ ] Requiere rol MASTER (check en acta-lista.page)
- [ ] ⚠️ ACCIÓN IRREVERSIBLE header
- [ ] Textarea: motivo (obligatorio)
- [ ] Buttons: [Cancelar] [Anular]
- [ ] Validación: estado=APROBADA
- [ ] Status: **[ ]** (implementar)

---

### B.5 — Modelos de Datos (domain/models/coordinator.model.ts)

**Checklist:**
- [ ] ActaItem: id, jrvId, numeroJrv, cargoId, nombreCargo, estado, totalSufragantes, votosBlancos, votosNulos, totalVotosCandidatos, fechaRegistro, urlEvidencia, motivoDisputa, motivoAnulacion
- [ ] ActaFiltros: estado, jrvId, cargoId, search, fechaDesde, fechaHasta, page, size
- [ ] ActaDetalleCompleto: id, acta (ActaItem), candidatos[], historial[]
- [ ] EstadoActa type: 'ENVIADA' | 'EN_DISPUTA' | 'CORREGIDA' | 'APROBADA' | 'ANULADA'
- [ ] DisputarActaPayload: motivo
- [ ] CorregirActaPayload: jrvId, cargoId, totalSufragantes, votosBlancos, votosNulos, detalles[]
- [ ] Status: **[x]** (ya existen)

---

## FASE C: Tests

### C.1 — Unit Tests (Frontend)

**Componentes a testear:**
```bash
frontend/app/src/app/features/coordinator/pages/actas-lista/actas-lista.page.spec.ts
  - ✓ Carga actas en ngOnInit
  - ✓ Filtra por estado
  - ✓ Busca por acta-id (local)
  - ✓ Pagina entre páginas
  - ✓ 409 Conflict recarga tabla
  
frontend/app/src/app/features/coordinator/components/actas-filters/...spec.ts
  - ✓ Emite filtros al cambiar estado
  - ✓ Emite filtros al cambiar JRV
  - ✓ Limpia filtros correctamente
  
frontend/app/src/app/features/coordinator/components/modal-aprobar/...spec.ts
  - ✓ Muestra modal con acta
  - ✓ Valida estado ENVIADA
  - ✓ Envía PUT /aprobar
  - ✓ Maneja error 409

frontend/app/src/app/features/coordinator/components/modal-disputar/...spec.ts
  - ✓ Valida motivo no-empty
  - ✓ Envía PUT /disputar con motivo
  - ✓ Muestra error si motivo vacío

frontend/app/src/app/features/coordinator/components/modal-corregir/...spec.ts
  - ✓ Valida checksum suma=sufragantes
  - ✓ Actualiza suma en tiempo real
  - ✓ Envía PUT /corregir

frontend/app/src/app/features/coordinator/components/modal-anular/...spec.ts
  - ✓ Requiere MASTER (visible/disabled)
  - ✓ Valida motivo no-empty
  - ✓ Envía DELETE con motivo
```

**Checklist:**
- [ ] 70%+ coverage en página principal
- [ ] Todos los escenarios GIVEN/WHEN/THEN testados
- [ ] Mutaciones implementadas y verificadas
- [ ] Status: **[ ]** (implementar)

---

### C.2 — E2E Tests (Playwright)

**Journeys críticos:**

1. **Tabla carga y filtra**
```bash
test('Tabla carga con actas ENVIADAS', async ({ page }) => {
  await page.goto('/coordinator/actas');
  const tabla = page.locator('table tbody tr');
  await expect(tabla).toHaveCount(10);
  const primeraFila = tabla.first();
  await expect(primeraFila).toContainText('ENVIADA');
});
```

2. **Approbar acta ENVIADA**
```bash
test('Coordinator aprueba acta ENVIADA', async ({ page }) => {
  await page.goto('/coordinator/actas');
  await page.locator('[data-testid="menu-001"]').click();
  await page.locator('text=Aprobar').click();
  await page.locator('button:has-text("Aprobar")').click();
  const toast = page.locator('text=Acta APROBADA');
  await expect(toast).toBeVisible();
  const estadoBadge = page.locator('[data-testid="estado-001"]');
  await expect(estadoBadge).toContainText('Aprobada');
});
```

3. **Disputar + Corregir + Aprobar**
```bash
test('Disputar acta → Observer corrige → Aprobar', async ({ page }) => {
  // Disputa
  // Change para Observer → corrigir
  // Approve nuevamente
});
```

4. **409 Conflict handling**
```bash
test('2 Coordinators aprueban concurrentemente → segundo obtiene 409 + reload', async () => {
  // Simular 2 requests simultáneos
  // Verificar 409 en segundo
  // Verificar reload automático
});
```

5. **MASTER anula acta APROBADA**
```bash
test('MASTER anula acta', async ({ page }) => {
  // Ver opción anular (enabled para MASTER, disabled para COORDINATOR)
  // Submit modal anulación
  // Verificar estado ANULADA
});
```

**Checklist:**
- [ ] 5+ E2E journeys implementados
- [ ] Cobertura: happy path + error path
- [ ] 409 conflict testing
- [ ] RBAC testing (MASTER vs COORDINATOR)
- [ ] Status: **[ ]** (implementar)

---

### C.3 — Contract Tests (Backend)

**Ya existe:** `backend/contract-tests/src/test/java/ec/twintailcs/electoral/contract/CoordinatorActasContractIT.java`

**Verificar:**
- [ ] GET /coordinator/actas ✓
- [ ] PUT /aprobar ✓
- [ ] PUT /disputar ✓
- [ ] PUT /corregir ✓
- [ ] DELETE /anular (MASTER-only) ✓
- [ ] GET /historial ✓
- [ ] 409 Conflict scenarios ✓

---

## FASE D: A11y + UX

### D.1 — Accesibilidad (WCAG AA)

**Checklist:**
- [ ] ARIA roles: table, rowgroup, row, cell
- [ ] ARIA labels: "Estado: enviada", "Checksum: incorrecto"
- [ ] Keyboard navigation: Tab entre filtros → tabla → acciones
- [ ] Color + text + icon (no solo color para estado)
- [ ] Contrast 4.5:1 mínimo (estados en dark mode)
- [ ] 44px min targets (botones menú ⋮)
- [ ] Focus visible en botones (outline)
- [ ] Status: **[ ]** (auditar con /ux-designer)

---

### D.2 — UX Review

**Checklist:**
- [ ] Mobile: cards apiladas, dropdown menú acciones
- [ ] Tooltip en checksum incorrecto (diff en rojo)
- [ ] Tooltip en ANULADA (mostrar motivo)
- [ ] Toast para estado (✓ success, ✗ error, ⚠️ conflict)
- [ ] Confirmación antes de APROBAR / DISPUTAR / ANULAR
- [ ] Loading state (spinner) durante petición
- [ ] Empty state si no hay actas
- [ ] Error state si GET falla
- [ ] Status: **[ ]** (auditar con /taste-skill)

---

## FASE E: Mutations + Compuertas

### E.1 — Mutaciones (validar test cae)

Todas las 10 mutaciones de spec.md deben implementarse + verificarse:

```bash
M1:  Mostrar ANULADA por defecto → test falla ✓
M2:  Omitir GET al filtrar → test falla ✓
M3:  Búsqueda case-sensitive → test falla ✓
M4:  No validar estado=ENVIADA → test falla ✓
M5:  Motivo DISPUTA opcional → test falla ✓
M6:  No validar checksum post-corregir → test falla ✓
M7:  COORDINATOR puede anular → test falla ✓
M8:  Offset 0-based vs 1-based → test falla ✓
M9:  No reload en 409 → test falla ✓
M10: Omitir roleGuard → test falla ✓
```

**Checklist:**
- [ ] Cada mutación implementada y test verifica caída
- [ ] Documento mutation-results.md con evidencia
- [ ] Status: **[ ]** (implementar)

---

### E.2 — Compuertas (Gates)

**Pre-implementación:**
- [ ] Backend endpoints funcionan (Fase A verificada)
- [ ] CoordinatorApi methods implementados
- [ ] Routes configuradas en app.routes.ts
- [ ] roleGuard aplicado (/coordinator/actas)

**Post-implementación:**
- [ ] Componentes unitarios >70% coverage
- [ ] E2E journeys 5/5 passing
- [ ] Mutations 10/10 failing (cuando mutation activada)
- [ ] A11y audit WCAG AA pass
- [ ] UX audit /taste-skill pass
- [ ] Performance <200ms GET, <100ms filtro

**Pre-merge:**
- [ ] ZERO console warnings
- [ ] ZERO console errors
- [ ] ZERO failed tests
- [ ] 80%+ coverage (target)

---

## Checklist de Cierre

### Código
- [ ] ActasListaPage: completo + tested
- [ ] Todas modales: completas + tested
- [ ] Filtros: funcionales + responsive
- [ ] Paginación: funcional + tested
- [ ] Manejo de errores: 403, 409, 400
- [ ] 409 conflict: reload automático
- [ ] Dark mode: tokens aplicados + contrast OK

### Tests
- [ ] Unit: 70%+ coverage
- [ ] E2E: 5 journeys passing
- [ ] Contract: backend endpoints OK
- [ ] Mutations: 10/10 mutaciones causan test fail

### A11y + UX
- [ ] WCAG AA completo (/ux-designer)
- [ ] Mobile responsive (/taste-skill)
- [ ] Performance <200ms (/lighthouse)
- [ ] Toast + confirmaciones
- [ ] Loading + error states

### Documentación
- [ ] README componentes (docstrings)
- [ ] DECISIONS.md (decisiones de diseño)
- [ ] MUTATIONS.md (resultados mutaciones)
- [ ] TESTING.md (estrategia tests)

### Merge + Cleanup
- [ ] SDD completada → moved to archive/
- [ ] Roadmap actualizado (T-FE-10 → Archived)
- [ ] PR merged a develop
- [ ] ZERO code comments dejan pendiente

---

## Estimación de Tiempo

| Fase | Tarea | Horas | Hito |
|------|-------|-------|------|
| A | Backend API verification | 2-3 | Endpoints OK |
| B | Frontend componentes | 6-8 | Tabla + modales funcionales |
| B | Acciones (aprobar/disputar/corregir) | 4-5 | Modales integrables |
| C | Tests (unit + E2E) | 4-5 | Coverage 70%+ |
| D | A11y + UX | 2-3 | WCAG AA + responsive |
| E | Mutations | 3-4 | 10 mutaciones validadas |
| **Total** | | **24-32h (3-4 días)** | **DONE** |

---

## Notas Importantes

1. **Ya implementado:** ActasListaPage + ActaDetallePage existen con código completo
   - SDD es "retroactiva" (documenta decisiones del código existente)
   - Usar como verificación / audit de lo que ya fue hecho
   - Puede servir para refactoring si hay gaps

2. **Backend endpoints:** Ya existen en CoordinatorActasController
   - Verificar con contract tests que funcionan
   - Audit: GET, PUT approve/dispute/correct, DELETE annul, GET historial

3. **Roles:** COORDINATOR + MASTER
   - COORDINATOR: puede aprobar, disputar, corregir
   - MASTER: puede además anular actas (acción destructiva)

4. **Conflictos (409):** Manejar gracefully
   - No es error, es "someone else won the race"
   - Reload tabla automático
   - Toast informativo

5. **Auditoria:** Cada acción debe loguear
   - GET /coordinator/actas: usuario, IP, filtros
   - PUT /aprobar: usuario, timestamp, acta
   - PUT /disputar: usuario, motivo
   - DELETE /anular: usuario, motivo (MASTER-only)
