# Tasks — T-FE-10: Coordinator: Gestión Global de Actas

## Fase A: CoordinatorHomeComponent

- [ ] **A.1** — Crear `features/coordinator/home/home.component.ts`.
  - Standalone: true
  - Inyectar: HttpClient, LoadingController, AlertController
  - Propiedades: actas$, estadoFiltro = ''
  - ngOnInit: GET /api/coordinator/actas

- [ ] **A.2** — Crear template con:
  - IonList o tabla
  - Columnas: JRV, Cargo, Estado, Fecha, Acciones
  - Dropdown IonSelect para filtro
  - Botones: Disputar, Corregir, Aprobar

- [ ] **A.3** — Filtro frontend (igual a T-FE-06).

**Compuerta A:** `ng build`. GET ejecuta al navegar.

## Fase B: Acciones (Disputar, Corregir, Aprobar)

- [ ] **B.1** — Agregar métodos:
  ```typescript
  async disputar(acta: Acta) {
    const confirm = await this.alertController.create({
      header: 'Disputar acta?',
      message: `JRV ${acta.jrvNombre} - ${acta.cargoNombre}`,
      buttons: [
        { text: 'Cancelar', role: 'cancel' },
        { text: 'Disputar', handler: () => this.executeDispute(acta) }
      ]
    });
    await confirm.present();
  }
  
  private executeDispute(acta: Acta) {
    this.http.put(`/api/coordinator/actas/${acta.id}/disputar`, {})
      .subscribe({
        next: () => {
          acta.estado = 'EN_DISPUTA';
          this.toastr.success('Acta disputada');
        }
      });
  }
  ```

- [ ] **B.2** — Repetir para `corregir()` y `aprobar()`.

- [ ] **B.3** — En template: botones llaman estos métodos.

**Compuerta B:** Click botón, AlertController abre, confirmar → PUT ejecuta.

## Fase C: Integración

- [ ] **C.1** — Registrar rutas de coordinator (lazy-load):
  ```typescript
  // features/coordinator/coordinator.routes.ts
  export const COORDINATOR_ROUTES = [
    {
      path: 'actas',
      component: CoordinatorHomeComponent
    }
  ];
  ```

- [ ] **C.2** — Test: navegar /coordinator/actas, tabla cargada, filtro funciona, acciones disparan PUT.

**Compuerta C:** `/coordinator/actas` carga, tabla funciona.

## Qué NO hacer

- **No edición inline** — solo acciones discretas
- **No comentarios** — out of scope
- **No historial** — out of scope
- **No búsqueda avanzada** — solo estado + JRV

## Compuertas

| Fase | Comando | Expected |
|------|---------|----------|
| A | Navegar /coordinator/actas | GET visible en Network |
| B | Click "Disputar", AlertController | Alert abre, PUT /api/coordinator/actas/{id}/disputar ejecuta |
| C | Cambiar filtro, click acción | tabla filtra, PUT ejecuta |

## Verificación por Mutación

- **V.1** — Comentar GET → tabla vacía → restaurar
- **V.2** — Comentar AlertController.create() → acción ocurre sin confirmar → restaurar
