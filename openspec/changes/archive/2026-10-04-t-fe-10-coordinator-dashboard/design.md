# Design — T-FE-10: Coordinator: Gestión Global de Actas

## D1 — Filtros frontend vs backend

**Decisión:** Frontend (Ionic IonSelect). Más responsive para MVP.

**Alternativa descartada — backend ?estado=ENVIADA.** Más tarde si hay miles de actas y filter es lento.

## D2 — Acciones: botones o links

**Decisión:** Botones con handlers. Cada uno dispara acción específica (disputar, corregir, aprobar).

**Alternativa descartada — links a detalle.** Acciones rápidas evitan navegación.

## D3 — Qué retorna GET /api/coordinator/actas

**Decisión:** Array de actas con campos: id, jrvNombre, cargoNombre, estado, fechaRegistro, observerNombre (quién la registró).

**Alternativa descartada — solo IDs.** Denormalizado es más eficiente.

## D4 — Confirmación antes de disputar/corregir

**Decisión:** Modal/dialog de confirmación (T-FE-12 ConfirmDialogComponent).

**Alternativa descartada — sin confirmación.** Evita cambios accidentales.
