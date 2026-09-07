# Spec — T-FE-12: Componentes Compartidos

## Escenarios

### S1 — EstadoBadgeComponent

```gherkin
GIVEN    <app-estado-badge [estado]="'ENVIADA"></app-estado-badge>
WHEN     componente renderiza
THEN     chip muestra "ENVIADA" con color azul
```

### S2 — RolBadgeComponent

```gherkin
GIVEN    <app-rol-badge [rol]="'COORDINATOR"></app-rol-badge>
WHEN     renderiza
THEN     chip muestra icono coordinator + "COORDINATOR" en color naranja
```

### S3 — ConfirmDialogComponent (open/close)

```gherkin
GIVEN    componente en template
WHEN     parent llama ref.open({title: 'Disputar?', message: 'Está seguro?'})
THEN     modal abre
WHEN     usuario hace click "Cancelar"
THEN     (cancel) emite, modal cierra
WHEN     usuario hace click "Confirmar"
THEN     (confirm) emite, modal cierra
```

### S4 — NotificacionBellComponent

```gherkin
GIVEN    <app-notificacion-bell [count]="5"></app-notificacion-bell>
WHEN     renderiza
THEN     campana + badge con número "5" rojo
```

### S5 — Importar desde @shared/components

```gherkin
GIVEN    parent component (ej: CoordinatorHome)
WHEN     imports { EstadoBadgeComponent } from '@shared/components'
THEN     TypeScript resuelve, IDE autocomplete funciona
```

## Mutaciones

### M1 — Remover @Input() de EstadoBadgeComponent

Si se comenta `@Input() estado`, componente no recibe data. Verificación: [estado] binding no tiene efecto, siempre muestra estado por defecto.

### M2 — ConfirmDialog no emite evento

Si se comenta `this.confirm.emit()`, click "Confirmar" no hace nada. Verificación: click, (confirm) handler no se ejecuta.

## Garantías

- **G1:** Componentes renderean con @Inputs
- **G2:** @Outputs emiten eventos
- **G3:** Importables vía barrel (shared/components/index.ts)
- **G4:** Reutilizables en múltiples features
