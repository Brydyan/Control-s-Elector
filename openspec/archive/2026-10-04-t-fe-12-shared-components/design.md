# Design — T-FE-12: Componentes Compartidos

## D1 — Input states: Enum vs String

**Decisión:** String. `estado: 'ENVIADA' | 'EN_DISPUTA' | ...` (type union).

**Alternativa descartada — Enum EstadoActa.** Más type-safe pero riguroso. String es flexible si cambian estados.

## D2 — ConfirmDialog: Modal o Alert

**Decisión:** Ionic IonModal personalizado. Más control de UI.

**Alternativa descartada — AlertController nativo.** Menos customizable.

## D3 — Styling: CSS vs Tailwind vs Ionic

**Decisión:** Ionic utilities (color, padding). IonBadge, IonChip, IonIcon.

**Alternativa descartada — CSS modules, Tailwind.** Ionic ya está en el stack.

## D4 — NotificacionBellComponent: contador real o stub

**Decisión:** Stub inicialmente. Contador es property, no calcula desde API.

**Alternativa descartada — GET /api/me/notificaciones.** Más tarde si se necesita.
