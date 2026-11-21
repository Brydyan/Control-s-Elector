# Enhancement — Recinto Wizard con Asignaciones de Usuarios

**Fecha:** 2026-11-20  
**Referencia:** T-COORD-RECINTOS-01 (enhancement a SDD existente)  
**Estado:** 📋 SPECIFICATION DRAFT

---

## Propuesta

Mejorar el flujo de **creación y edición de Recinto** para permitir asignar **Observadores** (mínimo 5) y **Coordinadores** (máximo 5) **en el mismo momento de creación**, con capacidad de editar estas asignaciones después.

---

## Requisitos Confirmados

### Creación de Recinto
- Modal wizard **2 pasos**:
  - **Paso 1:** Nombre, Ubicación (Provincia → Cantón → Parroquia cascada), Cantidad de JRVs
  - **Paso 2:** Selector de Observadores (mínimo 5) + Selector de Coordinadores (máximo 5)
- Validación: **frontend + backend**
- Resultado: 1 Recinto + N filas en tabla `usuario_recinto` (5-N observadores + 1-5 coordinadores)

### Edición de Recinto (DESPUÉS)
- Botones en tabla: `[Editar]` (datos + coordinadores + observadores)
- Permitir:
  - Cambiar Nombre/Ubicación
  - Cambiar Coordinadores (1-5 coordinadores asignados)
  - Agregar/Remover Observadores (mantener mínimo 5)
- Validación RLS: solo MASTER puede editar

### Vista para Coordinators
- `/coordinator/actas-pendientes` → **filtrado automático por recintos asignados**
- RLS en backend: solo muestra actas de JRVs donde el usuario está asignado como COORDINATOR en algún recinto

---

## Cambios Técnicos

### Base de Datos

**Nueva tabla:**
```sql
CREATE TABLE usuario_recinto (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  usuario_id UUID NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
  recinto_id UUID NOT NULL REFERENCES recinto(id) ON DELETE CASCADE,
  rol_en_recinto VARCHAR(20) NOT NULL 
    CHECK (rol_en_recinto IN ('COORDINATOR', 'OBSERVER')),
  fecha_asignacion TIMESTAMP DEFAULT NOW(),
  fecha_actualizacion TIMESTAMP DEFAULT NOW(),
  
  UNIQUE(usuario_id, recinto_id) -- No duplicados
);

CREATE INDEX idx_usuario_recinto_usuario ON usuario_recinto(usuario_id);
CREATE INDEX idx_usuario_recinto_recinto ON usuario_recinto(recinto_id);
CREATE INDEX idx_usuario_recinto_rol ON usuario_recinto(rol_en_recinto);
```

**Modificación a tabla `recinto`:**
```sql
-- Remover columna coordinator_id si existe (que lo maneje usuario_recinto)
-- O si ya existe: mantener para retrocompatibilidad, pero usar usuario_recinto como source of truth
```

---

### Backend

#### DTOs

```java
// CrearRecintoConAsignacionesRequest.java
public class CrearRecintoConAsignacionesRequest {
  public String nombre;
  public UUID parroquiaId;
  public Integer cantidadJrvs;
  public List<UUID> observadorIds;      // mínimo 5
  public List<UUID> coordinadorIds;     // máximo 5 (puede estar vacío)
  
  // validaciones
  public boolean isValid() {
    return nombre != null && !nombre.isEmpty()
        && parroquiaId != null
        && cantidadJrvs > 0
        && observadorIds != null && observadorIds.size() >= 5
        && observadorIds.size() == observadorIds.stream().distinct().count() // sin dups
        && coordinadorIds != null && coordinadorIds.size() <= 5
        && coordinadorIds.size() == coordinadorIds.stream().distinct().count(); // sin dups
  }
}

// EditarRecintoConAsignacionesRequest.java
public class EditarRecintoConAsignacionesRequest {
  public String nombre;              // opcional (null = no cambiar)
  public UUID parroquiaId;           // opcional
  public Integer cantidadJrvs;       // opcional
  public List<UUID> observadorIds;   // si present: reemplaza (mínimo 5)
  public List<UUID> coordinadorIds;  // si present: reemplaza (máximo 5)
}
```

#### Services

```java
// RecintoService.java
public UUID crearRecintoConAsignaciones(
    CrearRecintoConAsignacionesRequest req) {
  
  // 1. Validar
  if (!req.isValid()) throw new ValidationException(...);
  
  // 2. Verificar usuarios existen y tienen rol correcto
  validarObservadores(req.observadorIds);        // rol=OBSERVER
  validarCoordinadores(req.coordinadorIds);      // rol=COORDINATOR, máximo 5
  
  // 3. Crear Recinto
  Recinto recinto = new Recinto()
    .setNombre(req.nombre)
    .setParroquiaId(req.parroquiaId)
    .setCantidadJrvs(req.cantidadJrvs);
  recintoRepository.persist(recinto);
  
  // 4. Asignar Observadores
  for (UUID observadorId : req.observadorIds) {
    usuarioRecintoRepository.persist(new UsuarioRecinto()
      .setUsuarioId(observadorId)
      .setRecintoId(recinto.getId())
      .setRolEnRecinto("OBSERVER"));
  }
  
  // 5. Asignar Coordinadores (si existen)
  for (UUID coordinadorId : req.coordinadorIds) {
    usuarioRecintoRepository.persist(new UsuarioRecinto()
      .setUsuarioId(coordinadorId)
      .setRecintoId(recinto.getId())
      .setRolEnRecinto("COORDINATOR"));
  }
  
  return recinto.getId();
}

public void editarRecintoConAsignaciones(
    UUID recintoId,
    EditarRecintoConAsignacionesRequest req) {
  
  Recinto recinto = recintoRepository.findById(recintoId);
  
  // 1. Actualizar datos principales (si presente)
  if (req.nombre != null) recinto.setNombre(req.nombre);
  if (req.parroquiaId != null) recinto.setParroquiaId(req.parroquiaId);
  if (req.cantidadJrvs != null) recinto.setCantidadJrvs(req.cantidadJrvs);
  recintoRepository.update(recinto);
  
  // 2. Actualizar Observadores (si presente)
  if (req.observadorIds != null) {
    if (req.observadorIds.size() < 5) 
      throw new ValidationException("Mínimo 5 observadores");
    
    // Remover los viejos
    usuarioRecintoRepository.delete(
      "recinto_id = ? AND rol_en_recinto = 'OBSERVER'", 
      recintoId);
    
    // Agregar los nuevos
    for (UUID obsId : req.observadorIds) {
      usuarioRecintoRepository.persist(new UsuarioRecinto()
        .setUsuarioId(obsId)
        .setRecintoId(recintoId)
        .setRolEnRecinto("OBSERVER"));
    }
  }
  
  // 3. Actualizar Coordinadores (si presente)
  if (req.coordinadorIds != null) {
    // Remover los viejos
    usuarioRecintoRepository.delete(
      "recinto_id = ? AND rol_en_recinto = 'COORDINATOR'", 
      recintoId);
    
    // Agregar los nuevos (si existen)
    for (UUID coordId : req.coordinadorIds) {
      usuarioRecintoRepository.persist(new UsuarioRecinto()
        .setUsuarioId(coordId)
        .setRecintoId(recintoId)
        .setRolEnRecinto("COORDINATOR"));
    }
  }
}
```

#### Endpoints

```java
// MasterRecintoResource.java

@POST
@Path("/crear-con-asignaciones")
@RolesAllowed("MASTER")
public Response crearRecintoConAsignaciones(
    CrearRecintoConAsignacionesRequest req) {
  UUID recintoId = recintoService.crearRecintoConAsignaciones(req);
  return Response.ok(Map.of("recintoId", recintoId)).build();
}

@PUT
@Path("/{id}/editar-con-asignaciones")
@RolesAllowed("MASTER")
public Response editarRecintoConAsignaciones(
    @PathParam("id") UUID recintoId,
    EditarRecintoConAsignacionesRequest req) {
  recintoService.editarRecintoConAsignaciones(recintoId, req);
  return Response.ok().build();
}
```

#### RLS (ActasCoordinatorResource)

```java
// ActasCoordinatorResource.java
@GET
@Path("/actas-pendientes")
@RolesAllowed("COORDINATOR")
public Response obtenerActasPendientes() {
  UUID coordinatorId = securityContext.getUserPrincipal().getName(); // UUID del user
  
  // RLS: solo actas de recintos donde este coordinador está asignado
  List<Acta> actas = actaService.obtenerActasPendientesDelCoordinador(coordinatorId);
  return Response.ok(actas).build();
}

// ActaService
public List<Acta> obtenerActasPendientesDelCoordinador(UUID coordinatorId) {
  return Acta.<Acta>find("""
    SELECT a FROM Acta a
    JOIN a.jrv jrv
    JOIN jrv.recinto recinto
    WHERE recinto.id IN (
      SELECT ur.recinto.id 
      FROM UsuarioRecinto ur 
      WHERE ur.usuario.id = ?1 
        AND ur.rolEnRecinto = 'COORDINATOR'
    )
    AND a.estado = 'ENVIADA'
    ORDER BY a.fechaCreacion DESC
  """, coordinatorId).list();
}
```

---

### Frontend

#### Component: recinto-create-wizard.modal.ts

```typescript
export class RecintoCreateWizardModal {
  // State
  currentStep = signal<1 | 2>(1);
  
  // Paso 1: datos
  nombre = signal('');
  provincias = signal<Provincia[]>([]);
  cantones = signal<Canton[]>([]);
  parroquias = signal<Parroquia[]>([]);
  
  provinciaSeleccionada = signal('');
  cantonSeleccionado = signal('');
  parroquiaSeleccionada = signal('');
  cantidadJrvs = signal(0);
  
  // Paso 2: usuarios
  observadoresDisponibles = signal<Usuario[]>([]);
  observadoresSeleccionados = signal<Set<string>>(new Set());
  coordinadoresDisponibles = signal<Usuario[]>([]);
  coordinadoresSeleccionados = signal<Set<string>>(new Set());
  
  // Validación
  paso1Valido = computed(() => 
    this.nombre().trim() !== ''
    && this.parroquiaSeleccionada() !== ''
    && this.cantidadJrvs() > 0
  );
  
  paso2Valido = computed(() => 
    this.observadoresSeleccionados().size >= 5
    && this.coordinadoresSeleccionados().size <= 5
  );
  
  constructor(
    private recintoApi: RecintoApi,
    private geografiaApi: GeografiaApi,
    private usuarioApi: UsuarioApi,
    private ref: DynamicDialogRef
  ) {
    effect(() => {
      if (this.provinciaSeleccionada()) {
        this.geografiaApi.obtenerCantones(this.provinciaSeleccionada())
          .subscribe(c => this.cantones.set(c));
      }
    });
    
    effect(() => {
      if (this.cantonSeleccionado()) {
        this.geografiaApi.obtenerParroquias(this.cantonSeleccionado())
          .subscribe(p => this.parroquias.set(p));
      }
    });
  }
  
  ngOnInit() {
    this.geografiaApi.obtenerProvincias()
      .subscribe(p => this.provincias.set(p));
    
    // Cargar usuarios disponibles
    this.usuarioApi.obtenerPorRol('OBSERVER')
      .subscribe(obs => this.observadoresDisponibles.set(obs));
    
    this.usuarioApi.obtenerPorRol('COORDINATOR')
      .subscribe(coords => this.coordinadoresDisponibles.set(coords));
  }
  
  ir_a_paso_2() {
    if (!this.paso1Valido()) return;
    this.currentStep.set(2);
  }
  
  volver_a_paso_1() {
    this.currentStep.set(1);
  }
  
  crear() {
    if (!this.paso2Valido()) return;
    
    const req: CrearRecintoConAsignacionesRequest = {
      nombre: this.nombre(),
      parroquiaId: this.parroquiaSeleccionada(),
      cantidadJrvs: this.cantidadJrvs(),
      observadorIds: Array.from(this.observadoresSeleccionados()),
      coordinadorIds: Array.from(this.coordinadoresSeleccionados())
    };
    
    this.recintoApi.crearConAsignaciones(req).subscribe({
      next: () => {
        this.ref.close({ success: true });
      },
      error: (err) => {
        // mostrar error
      }
    });
  }
  
  cancelar() {
    this.ref.close();
  }
}
```

#### Template: recinto-create-wizard.modal.html

```html
<p-dialog [(visible)]="visible" [modal]="true" [header]="'Crear Recinto'" [style]="{ width: '800px' }">
  
  @if (currentStep() === 1) {
    <div class="paso-1">
      <h3>Paso 1: Datos Principales</h3>
      
      <div class="form-group">
        <label>Nombre del Recinto *</label>
        <input [(ngModel)]="nombre" type="text" placeholder="Ej: Escuela República del Ecuador">
      </div>
      
      <div class="form-row">
        <div class="form-group">
          <label>Provincia *</label>
          <select [(ngModel)]="provinciaSeleccionada">
            <option value="">-- Seleccionar --</option>
            @for (prov of provincias(); track prov.id) {
              <option [value]="prov.id">{{ prov.nombre }}</option>
            }
          </select>
        </div>
        
        <div class="form-group">
          <label>Cantón *</label>
          <select [(ngModel)]="cantonSeleccionado" [disabled]="!provinciaSeleccionada()">
            <option value="">-- Seleccionar --</option>
            @for (cant of cantones(); track cant.id) {
              <option [value]="cant.id">{{ cant.nombre }}</option>
            }
          </select>
        </div>
        
        <div class="form-group">
          <label>Parroquia *</label>
          <select [(ngModel)]="parroquiaSeleccionada" [disabled]="!cantonSeleccionado()">
            <option value="">-- Seleccionar --</option>
            @for (parr of parroquias(); track parr.id) {
              <option [value]="parr.id">{{ parr.nombre }}</option>
            }
          </select>
        </div>
      </div>
      
      <div class="form-group">
        <label>Cantidad de JRVs/Mesas *</label>
        <input [(ngModel)]="cantidadJrvs" type="number" min="1">
      </div>
      
      <p-footer>
        <button pButton type="button" label="Siguiente" 
          (click)="ir_a_paso_2()" 
          [disabled]="!paso1Valido()"></button>
        <button pButton type="button" label="Cancelar" 
          (click)="cancelar()" severity="secondary"></button>
      </p-footer>
    </div>
  }
  
  @if (currentStep() === 2) {
    <div class="paso-2">
      <h3>Paso 2: Asignar Usuarios</h3>
      
      <div class="usuarios-grid">
        <div class="observadores-col">
          <h4>Observadores (mínimo 5)</h4>
          <div class="checkbox-list">
            @for (obs of observadoresDisponibles(); track obs.id) {
              <label class="checkbox-item">
                <input type="checkbox" 
                  [checked]="observadoresSeleccionados().has(obs.id)"
                  (change)="$event.target.checked 
                    ? observadoresSeleccionados().add(obs.id)
                    : observadoresSeleccionados().delete(obs.id)">
                {{ obs.nombre }}
              </label>
            }
          </div>
          <p class="count">Seleccionados: {{ observadoresSeleccionados().size }}/{{ observadoresDisponibles().length }}</p>
        </div>
        
        <div class="coordinadores-col">
          <h4>Coordinadores (máximo 5)</h4>
          <div class="checkbox-list">
            @for (coord of coordinadoresDisponibles(); track coord.id) {
              <label class="checkbox-item">
                <input type="checkbox" 
                  [checked]="coordinadoresSeleccionados().has(coord.id)"
                  [disabled]="!coordinadoresSeleccionados().has(coord.id) && coordinadoresSeleccionados().size >= 5"
                  (change)="$event.target.checked 
                    ? coordinadoresSeleccionados().add(coord.id)
                    : coordinadoresSeleccionados().delete(coord.id)">
                {{ coord.nombre }}
              </label>
            }
          </div>
          <p class="count">Seleccionados: {{ coordinadoresSeleccionados().size }}/5</p>
        </div>
      </div>
      
      <p-footer>
        <button pButton type="button" label="< Atrás" 
          (click)="volver_a_paso_1()"></button>
        <button pButton type="button" label="Crear Recinto" 
          (click)="crear()" 
          [disabled]="!paso2Valido()"></button>
        <button pButton type="button" label="Cancelar" 
          (click)="cancelar()" severity="secondary"></button>
      </p-footer>
    </div>
  }
  
</p-dialog>
```

#### Component: recinto-edit-modal.ts (similar al wizard, pero combo edit)

```typescript
export class RecintoEditModal {
  recinto = signal<Recinto | null>(null);
  observadoresAsignados = signal<Set<string>>(new Set());
  coordinadoresAsignados = signal<Set<string>>(new Set());
  
  // ...resto similar al wizard
  
  ngOnInit() {
    this.recintoApi.obtenerConAsignaciones(this.recintoId)
      .subscribe(rec => {
        this.recinto.set(rec);
        this.observadoresAsignados.set(new Set(rec.observadorIds));
        this.coordinadoresAsignados.set(new Set(rec.coordinadorIds));
      });
  }
  
  guardar() {
    const req: EditarRecintoConAsignacionesRequest = {
      nombre: this.recinto().nombre,
      // ... resto del form
      observadorIds: Array.from(this.observadoresAsignados()),
      coordinadorIds: Array.from(this.coordinadoresAsignados())
    };
    
    this.recintoApi.editarConAsignaciones(this.recintoId, req).subscribe(...);
  }
}
```

---

## Tareas

### Backend (5 tareas)
- [ ] A) Crear tabla `usuario_recinto` + índices (Liquibase)
- [ ] B) DTOs: `CrearRecintoConAsignacionesRequest`, `EditarRecintoConAsignacionesRequest`
- [ ] C) `RecintoService.crearRecintoConAsignaciones()` + `editarRecintoConAsignaciones()`
- [ ] D) Endpoints POST/PUT en `MasterRecintoResource`
- [ ] E) RLS en `ActasCoordinatorResource.obtenerActasPendientes()`

### Frontend (4 tareas)
- [ ] F) `recinto-create-wizard.modal.ts` (component + template)
- [ ] G) `recinto-edit-modal.ts` (component + template)
- [ ] H) Integrar modales en `recintos-list.page.ts` ([Crear], [Editar])
- [ ] I) Tests + E2E

### Tests (2 tareas)
- [ ] J) Backend: 15+ tests (validaciones, RLS, edge cases)
- [ ] K) Frontend: 10+ tests (wizard flow, validaciones, llamadas API)

---

## Commits Esperados

```
feat(recintos): Phase G.1 - Create usuario_recinto table + migrations
feat(recintos): Phase G.2 - Backend asignaciones CRUD (service + endpoints)
feat(recintos): Phase G.3 - RLS filtering for Coordinator actas
feat(recintos): Phase G.4 - Frontend wizard: 2-step Recinto creation
feat(recintos): Phase G.5 - Frontend edit modal: actualizar coordinador/observadores
test(recintos): Phase G - Backend + Frontend tests + RLS validation
docs(recintos): Phase G - Updated guides (RECINTOS_GUIDE.md)
```

---

## Criterios de Aceptación

- [x] Crear Recinto con 5-N observadores + 1-5 coordinadores en un paso
- [x] Validación mínimo 5 observadores (frontend + backend)
- [x] Validación máximo 5 coordinadores (frontend + backend)
- [x] Editar Recinto: cambiar coordinadores, agregar/remover observadores
- [x] Mantener mínimo 5 observadores en edición
- [x] Todos los coordinadores asignados ven actas del recinto en `/coordinator/actas-pendientes`
- [x] RLS enforce: query SQL filtra por `usuario_recinto` join (múltiples coordinadores)
- [x] 0 regressions en tests existentes
- [x] E2E: crear recinto + observer sube acta + 5 coordinadores pueden ver + 1 aprueba

---

¿Arrancamos con esto? ¿O le cambio algo?
