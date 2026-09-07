# Tasks — T-FE-12: Componentes Compartidos

## Fase A: EstadoBadgeComponent + RolBadgeComponent

- [ ] **A.1** — Crear `shared/components/estado-badge/estado-badge.component.ts`.
  - Standalone: true
  - @Input() estado: string
  - Template: `<ion-chip color="{{estadoColor}}">{{ estado }}</ion-chip>`
  - Método: `get estadoColor()` retorna color (azul=ENVIADA, rojo=EN_DISPUTA, verde=APROBADA)

- [ ] **A.2** — Crear `shared/components/rol-badge/rol-badge.component.ts`.
  - @Input() rol: string
  - Template: `<ion-chip><ion-icon name="{{rolIcon}}"></ion-icon> {{ rol }}</ion-chip>`
  - Mapeo: rol → icono (person, cog, shield, etc.)

**Compuerta A:** `ng build`. Importables vía @shared/components.

## Fase B: ConfirmDialogComponent

- [ ] **B.1** — Crear `shared/components/confirm-dialog/confirm-dialog.component.ts`.
  - Standalone: true, imports: [IonModal, IonButton, CommonModule]
  - @Input() title, message, confirmLabel, cancelLabel
  - @Output() confirm = new EventEmitter(), cancel = new EventEmitter()
  - Template: IonModal con dos botones
  - public method: `open()` abre modal
  - Botones llaman emit() + modal.dismiss()

- [ ] **B.2** — Usar ModalController de Ionic para abrir/cerrar.

- [ ] **B.3** — Test: mock emit(), verificar que confirm/cancel se emiten.

**Compuerta B:** `ng test`. Modal abre/cierra, eventos emiten.

## Fase C: NotificacionBellComponent

- [ ] **C.1** — Crear `shared/components/notificacion-bell/notificacion-bell.component.ts`.
  - @Input() count: number
  - Template: `<ion-icon name="notifications"><ion-badge>{{ count }}</ion-badge></ion-icon>`
  - CSS: badge color rojo si count > 0

**Compuerta C:** `ng build`. Badge renderiza con contador.

## Fase D: Barrel + Integración

- [ ] **D.1** — Crear `shared/components/index.ts`:
  ```typescript
  export { EstadoBadgeComponent } from './estado-badge/estado-badge.component';
  export { RolBadgeComponent } from './rol-badge/rol-badge.component';
  export { ConfirmDialogComponent } from './confirm-dialog/confirm-dialog.component';
  export { NotificacionBellComponent } from './notificacion-bell/notificacion-bell.component';
  ```

- [ ] **D.2** — En `shared/index.ts`, re-export:
  ```typescript
  export * from './components';
  ```

- [ ] **D.3** — Test: en un feature component, importar desde @shared/components y usar cada uno.

**Compuerta D:** `ng build`. Componentes importables via barrel.

## Qué NO hacer

- **No lógica compleja** — solo presentación
- **No servicios** — eso es core/
- **No pipes** — solo componentes
- **No testing exhaustivo** — cobertura básica

## Compuertas

| Fase | Comando | Expected |
|------|---------|----------|
| A | `ng build` | EstadoBadge, RolBadge renderean |
| B | `ng test` (ConfirmDialog) | open(), emit() funcionan |
| C | `ng build` | NotificacionBell con badge |
| D | Import desde @shared/components | TypeScript resuelve, IDE autocomplete |

## Verificación por Mutación

- **V.1** — Comentar @Input() estado → [estado] binding no tiene efecto → restaurar
- **V.2** — Comentar emit() en confirm button → evento no emite → restaurar
- **V.3** — Remover barrel → import directo debe fallar (detectar falla, fuerza barrel) → restaurar
