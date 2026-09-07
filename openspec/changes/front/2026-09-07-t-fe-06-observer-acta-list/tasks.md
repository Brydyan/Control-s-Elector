# Tasks — T-FE-06: Observer: Lista de Actas Propias

## Fase A: Componente HomeComponent

- [ ] **A.1** — Crear `features/observer/home/home.component.ts`.
  - Standalone: true
  - Inyectar: HttpClient, Router, LoadingController
  - Propiedades: actas$, estadoFiltro = '', loading = false

- [ ] **A.2** — Crear template con:
  - IonList (o tabla si prefieres)
  - Rows: jrv nombre, cargo nombre, estado, fecha
  - Dropdown IonSelect para filtro por estado
  - Loading spinner

- [ ] **A.3** — En ngOnInit(), llamar GET /api/observer/mis-actas.
  ```typescript
  ngOnInit() {
    this.loading = true;
    this.actas$ = this.http.get<Acta[]>('/api/observer/mis-actas').pipe(
      tap(() => this.loading = false),
      catchError(err => { ... })
    );
  }
  ```

**Compuerta A:** `ng build`. GET se ejecuta al navegar a /observer/home.

## Fase B: Filtrado Frontend

- [ ] **B.1** — Agregar método `filteredActas$`:
  ```typescript
  get filteredActas$() {
    return this.actas$.pipe(
      map(actas => 
        this.estadoFiltro 
          ? actas.filter(a => a.estado === this.estadoFiltro) 
          : actas
      )
    );
  }
  ```

- [ ] **B.2** — En template, usar `filteredActas$ | async` para mostrar.

- [ ] **B.3** — Dropdown `(ionChange)` actualiza `this.estadoFiltro`.

**Compuerta B:** Cambiar filtro, lista se filtra.

## Fase C: Navegación

- [ ] **C.1** — Agregar método `goToDetail(acta)`:
  ```typescript
  goToDetail(acta: Acta) {
    this.router.navigate(['/observer/acta-detail', acta.id]);
  }
  ```

- [ ] **C.2** — En template, `(click)="goToDetail(acta)"` en cada fila.

- [ ] **C.3** — Crear stub `acta-detail.component.ts` (solo template vacío).

**Compuerta C:** Click navega a /observer/acta-detail/{id}.

## Qué NO hacer

- **No agregar paginación** — todas las actas a la vez
- **No agregar edición inline** — solo visualización
- **No filtro backend** — filtro cliente es suficiente
- **No búsqueda por texto** — solo dropdown estado

## Compuertas

| Fase | Comando | Expected |
|------|---------|----------|
| A | Navegar a /observer/home, DevTools Network | GET /api/observer/mis-actas visible |
| B | Cambiar dropdown | lista se filtra |
| C | Click acta, inspeccionar URL | ruta /observer/acta-detail/{id} |

## Verificación por Mutación

- **V.1** — Comentar GET en ngOnInit() → actas$ nunca emite → restaurar
- **V.2** — Comentar filtro en filteredActas$ → cambiar dropdown, lista no cambia → restaurar
