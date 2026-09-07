# Design — T-FE-06: Observer: Lista de Actas Propias

## D1 — UI Library

**Decisión:** Ionic (IonCard, IonList, IonButton). Ya está en el stack.

**Alternativa descartada — Angular Material.** Ionic es más ligero, móvil-first.

## D2 — Filtrado: Frontend vs Backend

**Decisión:** Frontend. Dropdown filtra array en memoria.

**Alternativa descartada — Backend retorna ya filtrado (endpoint con ?estado=ENVIADA).** Frontend es más rápido, menos requests. Para MVP está bien.

## D3 — Estructura de datos (Acta)

**Decisión:** DTO Acta con campos: id, jrvId, jrvNombre, cargoId, cargoNombre, estado, fechaRegistro, imageUrl (si disponible).

**Alternativa descartada — retornar solo IDs, cliente hace 2das queries.** MVP: denormalizado en DTO.

## D4 — Click en acta: qué pasa

**Decisión:** Navega a `/observer/acta-detail/{id}` (stub por ahora). T-FE-07 es registrar nueva, no editar.

**Alternativa descartada — abrir modal en línea.** Routing es más limpio.
