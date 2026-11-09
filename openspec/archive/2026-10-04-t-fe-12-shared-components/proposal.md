# Proposal — T-FE-12: Componentes Compartidos (Shared)

## Qué se construye
Componentes reutilizables usados en múltiples pantallas:
- EstadoBadgeComponent — chip de color por estado del acta
- RolBadgeComponent — chip por rol de usuario
- ConfirmDialogComponent — modal genérico de confirmación
- NotificacionBellComponent — campana con contador de no leídas

## Por qué
- Evitar código duplicado.
- Consistencia visual.
- Fácil de mantener (cambio en un lugar, refleja en todas partes).

## Alcance

| Elemento | Incluye | No incluye |
|----------|---------|-----------|
| Components | EstadoBadge, RolBadge, ConfirmDialog, NotificacionBell | 20+ componentes |
| Inputs/Outputs | @Input() para data, @Output() para eventos | lógica compleja |
| Styling | Ionic, colores por estado | CSS animations |
| Barrel | shared/components/index.ts | shared/pipes/, shared/models/ |

## Criterio de aceptación

- [ ] EstadoBadgeComponent: @Input estado, renderiza chip con color
- [ ] RolBadgeComponent: @Input rol, chip con icono + nombre
- [ ] ConfirmDialogComponent: @Input title/message, @Output confirm/cancel
- [ ] NotificacionBellComponent: @Input notificationCount, renderiza icono + badge
- [ ] Todos importables vía `import { X } from '@shared/components'`
- [ ] Usados en T-FE-05, T-FE-06, T-FE-10, T-FE-11

## Fuera de alcance

- Pipes personalizados
- Directivas
- Guards
- Servicios

## Dependencias

- T-FE-01 (estructura)

## Decisiones a tomar

**D1 — Input states:** Enum EstadoActa? O simple string? Elegir string (más flexible).

**D2 — ConfirmDialog: modal o native alert:** Modal personalizado (Ionic IonModal).
