# Flujo 14 — Ciclo de Notificaciones Push

n**Estado:** ⏳ PLANEADO — Código backend aún no implementado. Ver [diagram/README.md](../README.md).
Escenario: ciclo completo desde que se genera un evento hasta que la notificación llega al dispositivo del OBSERVER.

---

## Parte A — Notificación de Corrección al OBSERVER

Escenario: COORDINATOR marca acta EN_DISPUTA → OBSERVER recibe push en su dispositivo.

```mermaid
sequenceDiagram
    actor COORD as Coordinator
    actor OBS as Observador
    participant API as Quarkus API
    participant NOTIF as NotificacionService
    participant JPA as JPA Repository
    participant PG as PostgreSQL
    participant WS as WebSocket (mecanismo por definir)
    participant APP as App Angular

    COORD->>API: PATCH /api/actas/{acta_id}/estado {estado: EN_DISPUTA, motivo}
    API->>NOTIF: notificarObserver(observer_id, acta_id, motivo)

    NOTIF->>JPA: save(Notificacion {observer_id, acta_id, tipo: CORRECCION_REQUERIDA})
    JPA->>PG: INSERT notificacion
    PG-->>JPA: notificacion_id
    PG->>WS: NOTIFY notificaciones (nuevo INSERT)

    WS->>APP: Evento {tipo: CORRECCION_REQUERIDA, acta_id, motivo}
    APP->>APP: Muestra badge en "Correcciones Pendientes"
    APP-->>OBS: 🔔 Acta JRV-042 / Alcalde requiere corrección

    OBS->>APP: Abre apartado "Correcciones Pendientes"
    APP->>API: GET /api/observer/correcciones-pendientes + Bearer JWT
    API->>JPA: findNotificacionesByObserver(observer_id, leida: false)
    JPA->>PG: SELECT notificaciones + actas WHERE observer_id = ? AND leida = false
    PG-->>JPA: [NotificacionDTO]
    API-->>APP: Lista con acta, cargo, motivo de disputa
    APP-->>OBS: Muestra actas pendientes con detalle
```

---

## Parte B — Notificación al COORDINATOR tras Corrección del OBSERVER

Escenario: OBSERVER corrige acta → COORDINATOR es notificado para revisar y aprobar.

```mermaid
sequenceDiagram
    actor OBS as Observador
    actor COORD as Coordinator
    participant API as Quarkus API
    participant NOTIF as NotificacionService
    participant JPA as JPA Repository
    participant PG as PostgreSQL
    participant WS as WebSocket (mecanismo por definir)
    participant DASH as Dashboard Angular

    OBS->>API: PATCH /api/actas/{acta_id} {campos_corregidos}
    API->>NOTIF: notificarCoordinator(acta_id)

    NOTIF->>JPA: save(Notificacion {tipo: ACTA_LISTA_PARA_REVISION, acta_id})
    JPA->>PG: INSERT notificacion
    PG-->>JPA: notificacion_id
    PG->>WS: NOTIFY notificaciones (nuevo INSERT)

    WS->>DASH: Evento {tipo: ACTA_LISTA_PARA_REVISION, acta_id, jrv_id, cargo_id}
    DASH->>DASH: Muestra badge en panel de revisión
    DASH-->>COORD: 🔔 Acta JRV-042 / Alcalde lista para revisión
```

---

## Parte C — Notificación de Anulación al COORDINATOR

Escenario: MASTER anula acta APROBADA → COORDINATOR es notificado.

```mermaid
sequenceDiagram
    actor MASTER as Master
    actor COORD as Coordinator
    participant API as Quarkus API
    participant NOTIF as NotificacionService
    participant JPA as JPA Repository
    participant PG as PostgreSQL
    participant WS as WebSocket (mecanismo por definir)
    participant DASH as Dashboard Angular

    MASTER->>API: PATCH /api/actas/{acta_id}/estado {estado: ANULADA, motivo}
    API->>NOTIF: notificarCoordinator(acta_id, motivo)

    NOTIF->>JPA: save(Notificacion {tipo: ACTA_ANULADA, acta_id, motivo})
    JPA->>PG: INSERT notificacion
    PG-->>JPA: notificacion_id
    PG->>WS: NOTIFY notificaciones (nuevo INSERT)

    WS->>DASH: Evento {tipo: ACTA_ANULADA, acta_id, motivo}
    DASH-->>COORD: ⚠️ Acta JRV-042 / Alcalde anulada por MASTER — {motivo}
```

---

## Tipos de Notificación

| Tipo | Receptor | Origen | Canal |
|------|----------|--------|-------|
| `CORRECCION_REQUERIDA` | OBSERVER | COORDINATOR marca EN_DISPUTA | WebSocket (mecanismo por definir) → App móvil |
| `ACTA_LISTA_PARA_REVISION` | COORDINATOR | OBSERVER corrige acta | WebSocket (mecanismo por definir) → Dashboard |
| `ACTA_ANULADA` | COORDINATOR | MASTER anula acta | WebSocket (mecanismo por definir) → Dashboard |

---

## Marcar Notificación como Leída

```mermaid
sequenceDiagram
    participant APP as App Angular
    participant API as Quarkus API
    participant JPA as JPA Repository
    participant PG as PostgreSQL

    APP->>API: PATCH /api/notificaciones/{notificacion_id}/leida
    API->>JPA: updateLeida(notificacion_id, true)
    JPA->>PG: UPDATE notificacion SET leida = true
    PG-->>JPA: ok
    API-->>APP: 200 OK
    APP->>APP: Quita badge del apartado de correcciones
```
