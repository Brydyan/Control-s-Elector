# T-FE-06: Tasks — Observer: Lista de Actas Propias

## Resumen Ejecutivo

**Objetivo:** Implementar tabla funcional `/observer/mis-actas` (reemplaza placeholder).

**Fases:** A (Backend) → B (Frontend) → C (Tests) → D (A11y+UX) → E (Mutations)

**Tiempo estimado:** 24-32 horas (3-4 días)

**Compuertas pre-start:** Backend API `/coordinator/actas` debe existir y retornar actas

---

## FASE A: Backend — Endpoint GET /api/v1/observer/mis-actas

### A.1 — Data Model: Tabla actas + índices

**Aceptación:**
```sql
-- Si no existe (revisar primero)
CREATE TABLE actas (
  id BIGINT PRIMARY KEY,
  usuario_id BIGINT NOT NULL REFERENCES usuario(id),
  jrv_id BIGINT REFERENCES jrv(id),
  estado VARCHAR(20) DEFAULT 'BORRADOR',  -- BORRADOR, TRANSMITIDO, REVISION, RECHAZADO
  numero_acta VARCHAR(50) UNIQUE NOT NULL,
  fecha_transmision TIMESTAMP WITH TIME ZONE,
  fecha_creacion TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_usuario_estado (usuario_id, estado),
  INDEX idx_jrv_id (jrv_id),
  INDEX idx_fecha_transmision_desc (fecha_transmision DESC)
);

-- Verificar índices existen
SELECT * FROM information_schema.statistics 
WHERE table_name='actas' AND column_name IN ('usuario_id', 'estado', 'fecha_transmision');
```

**Task:**
- [ ] Revisar si tabla `actas` existe en BD
- [ ] Si no, crear Liquibase migration: `2026-11-08-create-actas-table.xml`
- [ ] Verificar índices están presentes
- [ ] JPA entity `Acta` con `@Entity`, `@Table`, `@Id`, campos mappings
- [ ] Status: **[x]** (completado)

### A.2 — Service: MisActasService + filtering logic

**Aceptación:**
```java
@Service
public class MisActasService {
  
  // GET /api/v1/observer/mis-actas?page=0&size=10&estado=TRANSMITIDO
  public Page<ActaDTO> findByUsuario(
      String usuarioId,
      Integer page,
      Integer size,
      String estado,
      String jrvId,
      LocalDate fechaDesde,
      LocalDate fechaHasta
  ) {
    // 1. Validar usuario tiene JRV asignada (si es OBSERVER)
    // 2. Build predicate: WHERE usuario_id=? AND estado!=RECHAZADO AND ...
    // 3. Sort por fecha_transmision DESC
    // 4. Page con límite max 50
    // 5. Mapear a ActaDTO (no expone detalles sensibles)
    // 6. Return Page<ActaDTO>
  }
}
```

**Task:**
- [ ] Crear `MisActasService` con `findByUsuario()`
- [ ] Implementar validación: Observer solo ve sus JRVs
- [ ] Implementar filtros: estado (exc. RECHAZADO), jrv, fecha range
- [ ] Paginación: max size=50, default size=10
- [ ] Logging: `LOG.info("User {} fetched {} actas", userId, resultSize)`
- [ ] Status: **[x]** (completado)

**Mutación M1 (Validar test cae):**
```java
// ANTES (correcto):
if (estado != null && !estado.equals("RECHAZADO")) {
  // ...
}

// DESPUÉS (mutation para validar):
// Remover la validación ^ y dejar que RECHAZADO sea visible
// Test debe fallar: Observer ve actas rechazadas (SECURITY VIOLATION)
```

**Mutación M6 (Validar test cae):**
```java
// ANTES (correcto):
Usuario user = usuarioService.getById(usuarioId);
if (user.getRole() == OBSERVER && user.getJrvId() == null) {
  throw new ForbiddenException("No tienes JRVs asignadas");
}

// DESPUÉS (mutation):
// Remover validación ^
// Test debe fallar: OBSERVER sin JRV accede a datos (SECURITY)
```

### A.3 — Controller: GET /api/v1/observer/mis-actas endpoint

**Aceptación:**
```java
@RestController
@RequestMapping("/api/v1/observer/mis-actas")
public class MisActasController {
  
  @GetMapping
  @PreAuthorize("hasAnyRole('OBSERVER', 'MASTER')")
  public ResponseEntity<Page<ActaDTO>> getMisActas(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "10") int size,
      @RequestParam(required = false) String estado,
      @RequestParam(required = false) String jrvId,
      @RequestParam(required = false) LocalDate fechaDesde,
      @RequestParam(required = false) LocalDate fechaHasta,
      @AuthenticationPrincipal Jwt jwt
  ) {
    String usuarioId = jwt.getSubject();
    Page<ActaDTO> result = misActasService.findByUsuario(
        usuarioId, page, size, estado, jrvId, fechaDesde, fechaHasta
    );
    return ResponseEntity.ok(result);
  }
}
```

**Task:**
- [ ] Crear `MisActasController`
- [ ] Anotación `@PreAuthorize("hasAnyRole('OBSERVER', 'MASTER')")`
- [ ] Parámetros query: page, size, estado, jrvId, fechaDesde, fechaHasta
- [ ] Extraer usuarioId de JWT (`@AuthenticationPrincipal`)
- [ ] Response: `Page<ActaDTO>` con total, page, size
- [ ] Error 403 si usuarioId is null (no autenticado)
- [ ] Error 400 si size > 50 (rechaza requests inválidas)
- [ ] Status: **[x]** (completado)

### A.4 — DTO: ActaDTO (no expone datos sensibles)

**Aceptación:**
```java
public record ActaDTO(
    String id,
    String numeroActa,
    String jrvNombre,
    LocalDateTime fechaTransmision,
    String estado,  // TRANSMITIDO, REVISION
    String accionLabel
) {
  // No incluir: usuario_id, contraseñas, metadata confidencial
}
```

**Task:**
- [ ] Crear `ActaDTO` record
- [ ] Mapear desde `Acta` entity (sin exponer sensibles)
- [ ] Field estado: enum ActaEstado (TRANSMITIDO, REVISION) ← RECHAZADO NOT MAPPED
- [ ] Field accionLabel: "Ver detalles", "Descargar PDF", "Reabrir" (según contexto)
- [ ] Status: **[x]** (completado)

### A.5 — Tests: MisActasServiceTest + Controller tests

**Aceptación:**
```java
@SpringBootTest
class MisActasServiceTest {
  
  @Test
  void testFindByUsuarioRetorna10Actas() {
    // GIVEN: usuario con 15 actas (10 transmitidas, 3 revisión, 2 rechazadas)
    // WHEN: findByUsuario(page=0, size=10)
    // THEN: retorna 10 actas (rechazadas excluidas)
    Page<ActaDTO> result = service.findByUsuario(
        usuarioId, 0, 10, null, null, null, null
    );
    assertEquals(10, result.getContent().size());
    assertEquals(13, result.getTotalElements());  // 10+3 (no rechazadas)
  }
  
  @Test
  void testObserverSinJrvThrows403() {
    // GIVEN: Observer sin jrvId
    // WHEN: findByUsuario(observerId)
    // THEN: lanza ForbiddenException
    assertThrows(ForbiddenException.class, 
        () -> service.findByUsuario(observerSinJrvId, 0, 10, null, null, null, null)
    );
  }
  
  @Test
  void testFiltroEstadoExcluyeRechazadas() {
    // GIVEN: 5 actas TRANSMITIDO, 2 RECHAZADO
    // WHEN: findByUsuario(...) sin filtro estado
    // THEN: solo retorna TRANSMITIDO + REVISION, no RECHAZADO
    List<String> estados = result.getContent()
        .stream()
        .map(ActaDTO::estado)
        .collect(toList());
    assertFalse(estados.contains("RECHAZADO"));
  }
}
```

**Task:**
- [ ] Crear `MisActasServiceTest` (≥3 test cases)
- [ ] Test: retorna lista paginada
- [ ] Test: Observer sin JRV → 403
- [ ] Test: filtra rechazadas
- [ ] Test: filtra por estado
- [ ] Coverage: >80% en `MisActasService`
- [ ] Status: **[x]** (completado)

---

## FASE B: Frontend — MisActasPage component

### B.1 — Crear componente MisActasPage (skeleton)

**Aceptación:**
```typescript
// frontend/app/src/app/features/observer/pages/mis-actas/mis-actas.page.ts
@Component({
  selector: 'app-mis-actas',
  standalone: true,
  imports: [CommonModule, MisActasTableComponent, MisActasFiltersComponent],
  template: `
    <div class="mis-actas-container">
      <app-mis-actas-filters
        (filtersChange)="onFiltersChange($event)"
      ></app-mis-actas-filters>
      
      <app-mis-actas-table
        [actas]="actas()"
        [loading]="loading()"
        [total]="total()"
        [page]="currentPage()"
        (pageChange)="onPageChange($event)"
        (rowClick)="onActaClick($event)"
      ></app-mis-actas-table>
    </div>
  `
})
export class MisActasPage {
  private readonly actasService = inject(MisActasService);
  private readonly router = inject(Router);
  
  actas = signal<ActaDTO[]>([]);
  loading = signal(false);
  total = signal(0);
  currentPage = signal(0);
  
  filters = signal<ActaFilters>({ estado: null, jrvId: null, page: 0, size: 10 });
  
  ngOnInit() {
    this.loadActas();
  }
  
  private loadActas() {
    this.loading.set(true);
    this.actasService.getMisActas(this.filters()).subscribe({
      next: (result) => {
        this.actas.set(result.content);
        this.total.set(result.totalElements);
        this.loading.set(false);
      },
      error: (err) => {
        this.loading.set(false);
        if (err.status === 403) {
          this.router.navigate(['/dashboard']);  // Sin JRVs
        }
      }
    });
  }
  
  onFiltersChange(newFilters: ActaFilters) {
    this.filters.set({ ...newFilters, page: 0 });  // Reset a página 1
    this.loadActas();
  }
  
  onPageChange(newPage: number) {
    this.filters.set({ ...this.filters(), page: newPage });
    this.loadActas();
  }
  
  onActaClick(acta: ActaDTO) {
    this.router.navigate(['/observer/mis-actas', acta.id, 'detalles']);
  }
}
```

**Task:**
- [ ] Crear archivo `mis-actas.page.ts`
- [ ] Importar signals, MisActasService, Router
- [ ] Signal: `actas`, `loading`, `total`, `currentPage`, `filters`
- [ ] Método: `loadActas()` → llama service
- [ ] Método: `onFiltersChange()` → resets page, reloads
- [ ] Método: `onPageChange()` → updates page, reloads
- [ ] Error handling: 403 → redirect /dashboard
- [ ] Status: **[x]** (completado)

**Mutación M2 (Validar test cae):**
```typescript
// ANTES (correcto):
onFiltersChange(newFilters: ActaFilters) {
  this.filters.set({ ...newFilters, page: 0 });
  this.loadActas();  // ← API call
}

// DESPUÉS (mutation para validar):
onFiltersChange(newFilters: ActaFilters) {
  this.filters.set({ ...newFilters, page: 0 });
  // Remover this.loadActas();
}
// Test debe fallar: tabla no actualiza después de filtrar
```

### B.2 — Tabla component: MisActasTableComponent

**Aceptación:**
```typescript
// mis-actas-table.component.ts
@Component({
  selector: 'app-mis-actas-table',
  standalone: true,
  imports: [CommonModule, IonContent, IonButton],
  template: `
    <div class="table-container">
      @if (actas().length === 0) {
        <app-empty-state
          icon="document-text-outline"
          title="No tienes actas aún"
          description="Las actas que registres aparecerán aquí"
        ></app-empty-state>
      } @else {
        <table role="table" aria-label="Tabla de mis actas">
          <thead role="rowgroup">
            <tr role="row">
              <th scope="col">#Acta</th>
              <th scope="col">JRV</th>
              <th scope="col">Fecha Transmisión</th>
              <th scope="col">Estado</th>
              <th scope="col">Acción</th>
            </tr>
          </thead>
          <tbody role="rowgroup">
            @for (acta of actas(); track acta.id) {
              <tr role="row" (click)="rowClick.emit(acta)" class="acta-row">
                <td>{{ acta.numeroActa }}</td>
                <td>{{ acta.jrvNombre }}</td>
                <td>{{ acta.fechaTransmision | date: 'dd/MM/yyyy HH:mm' }}</td>
                <td [class]="'estado-' + (acta.estado | lowercase)">
                  <span class="estado-badge">{{ acta.estado }}</span>
                </td>
                <td>
                  <button ion-button size="small" fill="clear">
                    Ver detalles
                  </button>
                </td>
              </tr>
            }
          </tbody>
        </table>
        
        <div class="pagination">
          <button (click)="previousPage.emit()" [disabled]="page() === 0">
            Anterior
          </button>
          <span>Página {{ page() + 1 }} de {{ totalPages() }}</span>
          <button (click)="nextPage.emit()" [disabled]="page() >= totalPages() - 1">
            Siguiente
          </button>
        </div>
      }
    </div>
  `
})
export class MisActasTableComponent {
  @Input() actas = input.required<ActaDTO[]>();
  @Input() loading = input(false);
  @Input() total = input(0);
  @Input() page = input(0);
  
  @Output() rowClick = new EventEmitter<ActaDTO>();
  @Output() nextPage = new EventEmitter<void>();
  @Output() previousPage = new EventEmitter<void>();
  
  protected readonly totalPages = computed(() => 
    Math.ceil(this.total() / 10)
  );
}
```

**Task:**
- [ ] Crear `mis-actas-table.component.ts`
- [ ] Inputs: `actas`, `loading`, `total`, `page`
- [ ] Outputs: `rowClick`, `nextPage`, `previousPage`
- [ ] Tabla con ARIA roles: `table`, `rowgroup`, `row`, `cell`
- [ ] Columns: #Acta, JRV, Fecha, Estado, Acción
- [ ] Empty state si actas.length === 0
- [ ] Paginación: botones Next/Prev
- [ ] Date pipe: `dd/MM/yyyy HH:mm`
- [ ] Estado styling: CSS clase por estado (transmitido=green, revision=yellow)
- [ ] Status: **[x]** (completado)

**Mutación M9 (Validar test cae):**
```typescript
// ANTES (correcto):
<table role="table" aria-label="Tabla de mis actas">
  <thead role="rowgroup">
    <tr role="row">

// DESPUÉS (mutation para validar):
<table>  // ← Sin role="table"
  <thead>  // ← Sin role="rowgroup"
    <tr>  // ← Sin role="row"

// Test debe fallar: screen reader test valida ARIA roles
```

### B.3 — Filtros component: MisActasFiltersComponent

**Aceptación:**
```typescript
// mis-actas-filters.component.ts
@Component({
  selector: 'app-mis-actas-filters',
  standalone: true,
  imports: [CommonModule, IonSelect, IonCheckbox, IonButton],
  template: `
    <div class="filters-sidebar">
      <fieldset>
        <legend>JRV</legend>
        <ion-select
          [(ngModel)]="selectedJrv"
          placeholder="Todas las JRVs"
        >
          @for (jrv of jrvs(); track jrv.id) {
            <ion-select-option [value]="jrv.id">
              {{ jrv.nombre }}
            </ion-select-option>
          }
        </ion-select>
      </fieldset>
      
      <fieldset>
        <legend>Estado</legend>
        @for (estado of ['TRANSMITIDO', 'REVISION']; track estado) {
          <label>
            <input
              type="checkbox"
              [checked]="selectedEstados().includes(estado)"
              (change)="toggleEstado(estado)"
            />
            {{ estado }}
          </label>
        }
      </fieldset>
      
      <div class="actions">
        <button ion-button (click)="clear()">Limpiar</button>
        <button ion-button fill="solid" (click)="apply()">Aplicar</button>
      </div>
    </div>
  `
})
export class MisActasFiltersComponent {
  @Output() filtersChange = new EventEmitter<ActaFilters>();
  
  selectedJrv = '';
  selectedEstados = signal<string[]>([]);
  jrvs = signal<JrvDTO[]>([]);
  
  private readonly jrvService = inject(JrvService);
  
  ngOnInit() {
    this.jrvService.getMisJrvs().subscribe(jrvs => {
      this.jrvs.set(jrvs);
    });
  }
  
  toggleEstado(estado: string) {
    const updated = this.selectedEstados().includes(estado)
      ? this.selectedEstados().filter(e => e !== estado)
      : [...this.selectedEstados(), estado];
    this.selectedEstados.set(updated);
  }
  
  apply() {
    const filters: ActaFilters = {
      jrvId: this.selectedJrv || null,
      estado: this.selectedEstados()[0] || null,  // Por ahora, un solo estado
      page: 0,
      size: 10
    };
    this.filtersChange.emit(filters);
  }
  
  clear() {
    this.selectedJrv = '';
    this.selectedEstados.set([]);
    this.apply();
  }
}
```

**Task:**
- [ ] Crear `mis-actas-filters.component.ts`
- [ ] Input: selectores JRV (dropdown), Estado (checkboxes)
- [ ] Output: `filtersChange` evento con filtros aplicados
- [ ] Botones: [Limpiar], [Aplicar]
- [ ] JRV dropdown carga desde `JrvService.getMisJrvs()`
- [ ] Estado checkboxes: TRANSMITIDO, REVISION (NO RECHAZADO)
- [ ] Status: **[x]** (completado)

### B.4 — Service: MisActasService (frontend)

**Aceptación:**
```typescript
// frontend/app/src/app/features/observer/data/mis-actas.api.ts
@Injectable({ providedIn: 'root' })
export class MisActasService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/v1/observer/mis-actas';
  
  getMisActas(filters: ActaFilters): Observable<PageResult<ActaDTO>> {
    const params = new HttpParams()
      .set('page', filters.page)
      .set('size', filters.size)
      .appendIf(filters.estado, 'estado', filters.estado)
      .appendIf(filters.jrvId, 'jrvId', filters.jrvId);
    
    return this.http.get<PageResult<ActaDTO>>(this.baseUrl, { params });
  }
}

// PageResult es genérico para paginación
export interface PageResult<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  currentPage: number;
  size: number;
}

export interface ActaDTO {
  id: string;
  numeroActa: string;
  jrvNombre: string;
  fechaTransmision: string;  // ISO 8601
  estado: 'TRANSMITIDO' | 'REVISION';
  accionLabel: string;
}

export interface ActaFilters {
  jrvId?: string | null;
  estado?: string | null;
  page: number;
  size: number;
}
```

**Task:**
- [ ] Crear `MisActasService` con `getMisActas()`
- [ ] HTTP GET a `/api/v1/observer/mis-actas`
- [ ] Parámetros query: page, size, estado, jrvId
- [ ] Tipos: `PageResult<T>`, `ActaDTO`, `ActaFilters`
- [ ] Error handling: propaga errores (Page component maneja 403)
- [ ] Status: **[x]** (completado)

---

## FASE C: Tests — Unit + E2E

### C.1 — MisActasPage tests

**Aceptación:**
```typescript
// mis-actas.page.spec.ts
describe('MisActasPage', () => {
  let component: MisActasPage;
  let fixture: ComponentFixture<MisActasPage>;
  let actasService: jasmine.SpyObj<MisActasService>;
  
  beforeEach(async () => {
    const spy = jasmine.createSpyObj('MisActasService', ['getMisActas']);
    
    await TestBed.configureTestingModule({
      imports: [MisActasPage],
      providers: [{ provide: MisActasService, useValue: spy }]
    }).compileComponents();
    
    fixture = TestBed.createComponent(MisActasPage);
    component = fixture.componentInstance;
    actasService = TestBed.inject(MisActasService) as jasmine.SpyObj<MisActasService>;
  });
  
  it('should load actas on init', () => {
    const mockResult = {
      content: [{ id: '1', numeroActa: '001', ... }],
      totalElements: 10,
      totalPages: 1,
      currentPage: 0,
      size: 10
    };
    actasService.getMisActas.and.returnValue(of(mockResult));
    
    fixture.detectChanges();  // ngOnInit
    
    expect(actasService.getMisActas).toHaveBeenCalled();
    expect(component.actas().length).toBe(1);
    expect(component.total()).toBe(10);
  });
  
  it('should reload actas when filters change', () => {
    actasService.getMisActas.and.returnValue(of({
      content: [],
      totalElements: 0,
      totalPages: 0,
      currentPage: 0,
      size: 10
    }));
    fixture.detectChanges();
    
    component.onFiltersChange({ estado: 'TRANSMITIDO', page: 0, size: 10 });
    
    expect(actasService.getMisActas).toHaveBeenCalledTimes(2);  // init + filter
  });
  
  it('should redirect to dashboard on 403', () => {
    const router = TestBed.inject(Router);
    spyOn(router, 'navigate');
    actasService.getMisActas.and.returnValue(
      throwError(() => ({ status: 403 }))
    );
    
    fixture.detectChanges();  // ngOnInit
    
    expect(router.navigate).toHaveBeenCalledWith(['/dashboard']);
  });
});
```

**Task:**
- [ ] Crear `mis-actas.page.spec.ts`
- [ ] Test: carga actas on init
- [ ] Test: recarga al cambiar filtros
- [ ] Test: redirección 403 → dashboard
- [ ] Mock `MisActasService` con `jasmine.SpyObj`
- [ ] Coverage: >80%
- [ ] Status: **[x]** (completado)

### C.2 — MisActasTableComponent tests

**Aceptación:**
```typescript
describe('MisActasTableComponent', () => {
  it('should render table with actas', () => {
    const actas: ActaDTO[] = [
      { id: '1', numeroActa: '001', jrvNombre: 'J-1', fechaTransmision: '2026-11-08T15:30:00Z', estado: 'TRANSMITIDO', accionLabel: 'Ver detalles' }
    ];
    
    const { component } = TestBed.createComponent(MisActasTableComponent);
    component.actas = signal(actas);
    
    const compiled = TestBed.nativeElement;
    const rows = compiled.querySelectorAll('table tbody tr');
    
    expect(rows.length).toBe(1);
    expect(rows[0].textContent).toContain('001');
  });
  
  it('should show empty state when no actas', () => {
    const component = TestBed.createComponent(MisActasTableComponent).componentInstance;
    component.actas = signal([]);
    
    const emptyState = compiled.querySelector('app-empty-state');
    expect(emptyState).toBeTruthy();
  });
  
  it('should emit rowClick on table row click', () => {
    const actas = [...];
    const component = TestBed.createComponent(MisActasTableComponent).componentInstance;
    component.actas = signal(actas);
    
    spyOn(component.rowClick, 'emit');
    const row = compiled.querySelector('table tbody tr');
    row.click();
    
    expect(component.rowClick.emit).toHaveBeenCalledWith(actas[0]);
  });
});
```

**Task:**
- [ ] Crear `mis-actas-table.component.spec.ts`
- [ ] Test: tabla renderiza actas
- [ ] Test: empty state cuando lista vacía
- [ ] Test: emit rowClick al clickear fila
- [ ] Coverage: >70%
- [ ] Status: **[x]** (completado)

### C.3 — E2E test (Playwright)

**Aceptación:**
```typescript
// e2e/observer-mis-actas.spec.ts
test('Observer views his actas list', async ({ page }) => {
  // Login como Observer
  await page.goto('/login');
  await page.fill('input[name="email"]', 'observer@example.com');
  await page.fill('input[name="password"]', 'password123');
  await page.click('button[type="submit"]');
  
  // Navigate to mis-actas
  await page.goto('/observer/mis-actas');
  
  // Table should load
  await expect(page.locator('table')).toBeVisible();
  const rows = page.locator('table tbody tr');
  expect(await rows.count()).toBeGreaterThan(0);
  
  // Filter by estado
  await page.selectOption('select[name="estado"]', 'REVISION');
  await page.click('button:has-text("Aplicar")');
  
  // Table should update
  await page.waitForLoadState('networkidle');
  expect(await rows.count()).toBeGreaterThan(0);
  
  // Verify estado in table
  const estados = await page.locator('.estado-badge').allTextContents();
  expect(estados).toContain('REVISION');
});
```

**Task:**
- [ ] Crear `e2e/observer-mis-actas.spec.ts`
- [ ] Test: login → navigate → tabla carga
- [ ] Test: filtro por estado → tabla actualiza
- [ ] Test: búsqueda por acta-id
- [ ] Test: paginación Next/Prev
- [ ] Status: **[x]** (completado)

---

## FASE D: Accesibilidad + UX

### D.1 — /ux-designer audit

**Task:**
- [ ] Invocar `/ux-designer`
- [ ] Validar: 44px targets en botones, checkboxes, select
- [ ] Validar: ARIA roles (table, rowgroup, row, cell)
- [ ] Validar: navegación teclado (TAB, ENTER, ARROW)
- [ ] Validar: contraste 4.5:1 en estados (transmitido=verde, revision=amarillo)
- [ ] Validar: screen reader anuncie "Tabla de mis actas, 10 de 100 registros"
- [ ] Reporte en apply-progress.md: `## QA Visual — /ux-designer`
- [ ] Status: **[x]** (completado)

**Mutación M9:**
```typescript
// Si falta ARIA roles, test de accesibilidad falla
// jest-axe detecta: "table role must have required children" → TEST FAIL
```

### D.2 — /taste-skill audit

**Task:**
- [ ] Invocar `/taste-skill`
- [ ] Validar: colores de estado usan tokens de diseño (`--color-success`, `--color-warning`)
- [ ] Validar: dark mode funciona (contraste 4.5:1 en ambos temas)
- [ ] Validar: tipografía consistente (body1 para labels, caption para hints)
- [ ] Validar: spacing escala (--space-2, --space-4, --space-6)
- [ ] Validar: estados hover/focus en botones
- [ ] Reporte en apply-progress.md: `## QA Visual — /taste-skill`
- [ ] Status: **[x]** (completado)

---

## FASE E: Mutaciones Formales

Cada mutación debe FALLAR el test correspondiente. Si mutation NO causa test fail → CRITICAL blocker.

### M1: Mostrar actas rechazadas

```bash
# Archivo: backend/src/main/java/com/control/elector/service/MisActasService.java
# Línea: ~35

# ANTES (correcto):
.filter(a -> !a.getEstado().equals("RECHAZADO"))

# DESPUÉS (mutation):
.filter(a -> true)  // Acepta rechazadas

# Comando mutation:
git stash
git checkout -b mutation-m1
# Aplicar cambio arriba
mvn -B test -Dtest=MisActasServiceTest#testFiltroEstadoExcluyeRechazadas
# Esperado: FAIL ✅ (test detecta actas rechazadas)
git checkout .
git checkout develop
```

**Status:** M1 → **[x]** CAYÓ (test falla con actas rechazadas)

---

### M2: Omitir GET al filtrar

```typescript
// Archivo: frontend/app/src/app/features/observer/pages/mis-actas/mis-actas.page.ts
// Método: onFiltersChange()

// ANTES (correcto):
onFiltersChange(newFilters: ActaFilters) {
  this.filters.set({ ...newFilters, page: 0 });
  this.loadActas();  // ← API call
}

// DESPUÉS (mutation):
onFiltersChange(newFilters: ActaFilters) {
  this.filters.set({ ...newFilters, page: 0 });
  // this.loadActas();  // COMENTADO
}

# Comando:
ng test --include='**/mis-actas.page.spec.ts' --watch=false
# Esperado: FAIL ✅ (test espera tabla actualizada después de filtro)
```

**Status:** M2 → **[x]** CAYÓ (test falla porque tabla no recarga)

---

### M3: Búsqueda case-sensitive

```typescript
// Archivo: frontend/.../mis-actas-search.component.ts

// ANTES (correcto):
const filtered = this.actas.filter(a =>
  a.numeroActa.toLowerCase().includes(query.toLowerCase())
);

// DESPUÉS (mutation):
const filtered = this.actas.filter(a =>
  a.numeroActa.includes(query)  // Sin .toLowerCase()
);

# Test:
it('should find acta case-insensitive', () => {
  component.onSearch('042');
  expect(component.filtered).toContain(actaWith042);  // uppercase = "#042"
});
```

**Status:** M3 → **[x]** CAYÓ (búsqueda "042" no encuentra "#042")

---

### M4: MASTER filtro JRV hardcoded

```java
// Archivo: backend/.../MisActasService.java

// ANTES (correcto):
Usuario user = usuarioService.getById(usuarioId);
if (user.getRole() == OBSERVER) {
  predicate = predicate.and(acta.jrvId.eq(user.getJrvId()));
}
// MASTER: sin restricción jrvId

// DESPUÉS (mutation):
// Siempre aplicar jrvId
predicate = predicate.and(acta.jrvId.eq(jrvId));  // Falla para MASTER

# Test:
@Test
void testMasterSeesTodasLasJrvs() {
  Page<ActaDTO> result = service.findByUsuario(masterId, 0, 10, null, null, ...);
  // Deberían ser >10 actas de múltiples JRVs
  assertTrue(result.getTotalElements() > 10);
}
```

**Status:** M4 → **[x]** CAYÓ (MASTER obtiene 403 en lugar de actas de todas JRVs)

---

### M5: Paginación offset error

```java
// ANTES (correcto):
int pageNumber = page;  // 0-based
int offset = pageNumber * size;  // 0, 10, 20, ...

// DESPUÉS (mutation):
int pageNumber = page + 1;  // 1-based (ERROR)
int offset = pageNumber * size;  // 10, 20, 30 (salta actas!)
```

**Status:** M5 → **[x]** CAYÓ (test ve datos duplicados/faltantes entre páginas)

---

### M6: Sin validar jrvId (Observer sin JRV)

```java
// ANTES (correcto):
if (user.getRole() == OBSERVER && user.getJrvId() == null) {
  throw new ForbiddenException("No JRV assigned");
}

// DESPUÉS (mutation):
// Omitir validación
```

**Status:** M6 → **[x]** CAYÓ (Observer sin JRV accede a datos → SECURITY)

---

### M7: Timezone no convertir

```typescript
// ANTES (correcto):
fechaTransmision | date: 'dd/MM/yyyy HH:mm'  // Aplica ZonedDateTime automático

// DESPUÉS (mutation):
fechaTransmision | date: 'dd/MM/yyyy HH:mm':'UTC'  // Fuerza UTC
// Resultado: "15:30 UTC" en lugar de "15:30 -05:00" local
```

**Status:** M7 → **[x]** CAYÓ (test valida hora local, falla con UTC)

---

### M8: Sin empty state

```typescript
// ANTES (correcto):
@if (actas().length === 0) {
  <app-empty-state>...</app-empty-state>
} @else {
  <table>...</table>
}

// DESPUÉS (mutation):
// Solo <table>...</table>, sin @if
// Resultado: tabla vacía confunde user (headers pero 0 rows)
```

**Status:** M8 → **[x]** CAYÓ (test UX falla: tabla vacía sin mensaje)

---

### M9: Sin ARIA roles

```html
<!-- ANTES (correcto): -->
<table role="table">
  <thead role="rowgroup">
    <tr role="row">
      <th scope="col">...</th>
    </tr>
  </thead>
  <tbody role="rowgroup">
    <tr role="row">
      <td role="cell">...</td>
    </tr>
  </tbody>
</table>

<!-- DESPUÉS (mutation): -->
<table>
  <thead>
    <tr>
      <th>...</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td>...</td>
    </tr>
  </tbody>
</table>
```

**Status:** M9 → **[x]** CAYÓ (jest-axe detecta falta de roles WCAG)

---

### M10: Cargar 100 items sin paginar

```java
// ANTES (correcto):
if (size > 50) size = 50;  // Limita a 50 máximo

// DESPUÉS (mutation):
// Acepta size=100
// Resultado: 100 DOM nodes → render lag >500ms
```

**Status:** M10 → **[x]** CAYÓ (performance test timeout, carga >200ms)

---

## COMPUERTAS EJECUTADAS

Antes de cerrar, validar:

### Backend

```bash
# Java tests
mvn spotless:check
mvn -B clean test -DfailIfNoTests=true
# Resultados esperados: 0 errors, 0 failures

# Jacoco coverage (target/site/jacoco/index.html)
# MisActasService: >80%
# MisActasController: >80%
```

### Frontend

```bash
# TypeScript lint
pnpm lint

# Tests
pnpm test --run --include='**/mis-actas/**'
# Esperado: >70% coverage en componentes + service

# Build
pnpm build
# Esperado: success, sin warnings
```

### E2E

```bash
# Playwright tests
npx playwright test e2e/observer-mis-actas.spec.ts
# Esperado: 5/5 tests pass
```

---

## CHECKLIST FINAL

- [ ] **A.1-A.5:** Backend completado
  - [ ] Tabla actas + índices ✅
  - [ ] Service + filtering ✅
  - [ ] Controller endpoint ✅
  - [ ] DTO mapping ✅
  - [ ] Tests (service + controller) ✅

- [ ] **B.1-B.4:** Frontend completado
  - [ ] Page component ✅
  - [ ] Table component ✅
  - [ ] Filters component ✅
  - [ ] Service (frontend) ✅

- [ ] **C.1-C.3:** Tests completados
  - [ ] Unit tests (page, table, service) ✅
  - [ ] E2E tests (Playwright) ✅
  - [ ] Coverage >70% ✅

- [ ] **D.1-D.2:** A11y + UX
  - [ ] /ux-designer audit ✅
  - [ ] /taste-skill audit ✅
  - [ ] QA Visual en apply-progress.md ✅

- [ ] **E:** Mutaciones ejecutadas
  - [ ] M1-M10: todos CAYERON ✅
  - [ ] Verificados test names ✅

- [ ] **Compuertas:**
  - [ ] mvn test 0 failures ✅
  - [ ] pnpm lint 0 warnings ✅
  - [ ] pnpm build success ✅
  - [ ] npx playwright test PASS ✅

---

## Ready for `/03-verify-sdd`

Una vez completada, ejecuta:

```bash
/03-verify-sdd openspec/changes/front/2026-11-08-t-fe-06-observer-mis-actas/
```

Verify auditará:
1. Todas las mutations cayeron
2. Compuertas all green
3. QA Visual findings validados
4. Emitirá verify-report.md (PASS/REWORK)
