# Flujo 11 — Disputa de Acta y Corrección

n**Estado:** ⏳ PLANEADO — Código backend aún no implementado. Ver [diagram/README.md](../README.md).
---

## Parte A — COORDINATOR marca acta EN_DISPUTA

Escenario: COORDINATOR detecta inconsistencia entre foto y datos digitados; notifica al OBSERVER para corregir.

```mermaid
sequenceDiagram
    actor COORD as Coordinator
    actor OBS as Observador
    participant APP as App Angular/Ionic
    participant API as Spring Boot API
    participant SVC as ActaService
    participant AUDIT as AuditLogService
    participant NOTIF as NotificacionService
    participant JPA as JPA Repository
    participant PG as PostgreSQL

    COORD->>API: GET /api/actas/{acta_id}/evidencia
    API->>JPA: findById(acta_id)
    JPA->>PG: SELECT acta + storage_url
    PG-->>JPA: Acta + foto
    JPA-->>API: ActaDetalleDTO
    API-->>COORD: Datos + imagen WebP

    Note over COORD: Detecta que datos digitados no coinciden con foto

    COORD->>API: PATCH /api/actas/{acta_id}/estado {estado: EN_DISPUTA, motivo}
    API->>SVC: marcarDisputa(acta_id, motivo, coordinator_id)
    SVC->>JPA: updateEstado(acta_id, EN_DISPUTA)
    JPA->>PG: UPDATE acta SET estado = EN_DISPUTA
    PG-->>JPA: ok

    SVC->>AUDIT: registrar(ACTA_EN_DISPUTA, coordinator_id, acta_id, motivo)
    AUDIT->>JPA: save(AuditLog)
    JPA->>PG: INSERT audit_log {accion: ACTA_EN_DISPUTA, payload JSONB, timestamp}
    PG-->>JPA: audit_id

    SVC->>NOTIF: notificarObserver(observer_id, acta_id, motivo)
    NOTIF->>PG: INSERT notificacion {observer_id, acta_id, tipo: CORRECCION_REQUERIDA}
    NOTIF-->>APP: Push notification → Observador
    APP-->>OBS: 🔔 Acta {jrv_id} requiere corrección — {motivo}

    SVC-->>API: ok
    API-->>COORD: 200 OK — observador notificado
```

---

## Parte B — OBSERVER corrige acta EN_DISPUTA

Escenario: OBSERVER recibe notificación, abre el apartado de correcciones pendientes y edita los datos.

```mermaid
sequenceDiagram
    actor OBS as Observador
    participant APP as App Angular/Ionic
    participant API as Spring Boot API
    participant SVC as ActaService
    participant AUDIT as AuditLogService
    participant NOTIF as NotificacionService
    participant JPA as JPA Repository
    participant PG as PostgreSQL

    OBS->>APP: Abre apartado "Correcciones Pendientes"
    APP->>API: GET /api/observer/correcciones-pendientes
    API->>JPA: findActasByObserverAndEstado(observer_id, EN_DISPUTA)
    JPA->>PG: SELECT actas WHERE observer_id = ? AND estado = EN_DISPUTA
    PG-->>JPA: Lista de actas en disputa
    JPA-->>API: [ActaResumenDTO]
    API-->>APP: Lista con motivo de disputa por acta
    APP-->>OBS: Muestra actas pendientes con motivo

    OBS->>APP: Selecciona acta y edita datos incorrectos
    APP->>API: PATCH /api/actas/{acta_id} {campos_corregidos}
    API->>SVC: corregirActa(acta_id, dto, observer_id)
    SVC->>JPA: updateActa(acta_id, campos_corregidos, estado: CORREGIDA)
    JPA->>PG: UPDATE acta SET campos = ?, estado = CORREGIDA
    PG-->>JPA: ok

    SVC->>AUDIT: registrar(ACTA_CORREGIDA_POR_OBSERVER, observer_id, acta_id, diff)
    AUDIT->>JPA: save(AuditLog)
    JPA->>PG: INSERT audit_log {accion: ACTA_CORREGIDA_POR_OBSERVER, payload JSONB, timestamp}
    PG-->>JPA: audit_id

    SVC->>NOTIF: notificarCoordinator(acta_id)
    NOTIF->>PG: INSERT notificacion {tipo: ACTA_LISTA_PARA_REVISION, acta_id}
    NOTIF-->>API: ok

    SVC-->>API: ok
    API-->>APP: 200 OK
    APP-->>OBS: ✅ Corrección enviada
```

---

## Parte C — COORDINATOR corrige acta directamente

Escenario: OBSERVER no puede corregir en ese momento; COORDINATOR edita directamente sin esperar.

```mermaid
sequenceDiagram
    actor COORD as Coordinator
    participant API as Spring Boot API
    participant SVC as ActaService
    participant AUDIT as AuditLogService
    participant JPA as JPA Repository
    participant PG as PostgreSQL

    COORD->>API: PATCH /api/actas/{acta_id} {campos_corregidos}
    API->>SVC: corregirActa(acta_id, dto, coordinator_id)
    SVC->>JPA: updateActa(acta_id, campos_corregidos, estado: CORREGIDA)
    JPA->>PG: UPDATE acta SET campos = ?, estado = CORREGIDA
    PG-->>JPA: ok

    SVC->>AUDIT: registrar(ACTA_CORREGIDA_POR_COORDINATOR, coordinator_id, acta_id, diff)
    AUDIT->>JPA: save(AuditLog)
    JPA->>PG: INSERT audit_log {accion: ACTA_CORREGIDA_POR_COORDINATOR, payload JSONB, timestamp}
    PG-->>JPA: audit_id

    SVC-->>API: ok
    API-->>COORD: 200 OK
```

---

## Parte D — COORDINATOR aprueba acta CORREGIDA

Escenario: COORDINATOR revisa corrección (de OBSERVER o propia) y aprueba el acta.

```mermaid
sequenceDiagram
    actor COORD as Coordinator
    participant API as Spring Boot API
    participant SVC as ActaService
    participant AUDIT as AuditLogService
    participant JPA as JPA Repository
    participant PG as PostgreSQL

    COORD->>API: PATCH /api/actas/{acta_id}/estado {estado: APROBADA}
    API->>SVC: aprobarActa(acta_id, coordinator_id)
    SVC->>JPA: updateEstado(acta_id, APROBADA)
    JPA->>PG: UPDATE acta SET estado = APROBADA
    PG-->>JPA: ok

    SVC->>AUDIT: registrar(ACTA_APROBADA, coordinator_id, acta_id)
    AUDIT->>JPA: save(AuditLog)
    JPA->>PG: INSERT audit_log {accion: ACTA_APROBADA, payload JSONB, timestamp}
    PG-->>JPA: audit_id

    SVC-->>API: ok
    API-->>COORD: 200 OK — acta aprobada
```

---

## Estados del Acta

```
BORRADOR → ENVIADA → EN_DISPUTA → CORREGIDA → APROBADA
```

| Transición | Quién |
|------------|-------|
| `BORRADOR → ENVIADA` | OBSERVER |
| `ENVIADA → EN_DISPUTA` | COORDINATOR / MASTER |
| `EN_DISPUTA → CORREGIDA` | OBSERVER o COORDINATOR (sin tiempo de espera) |
| `CORREGIDA → APROBADA` | COORDINATOR / MASTER |

## Notas de Auditoría

| Acción | Quién | payload JSONB contiene |
|--------|-------|------------------------|
| `ACTA_EN_DISPUTA` | COORDINATOR | motivo, coordinator_id |
| `ACTA_CORREGIDA_POR_OBSERVER` | OBSERVER | campos cambiados, valor_anterior, valor_nuevo |
| `ACTA_CORREGIDA_POR_COORDINATOR` | COORDINATOR | campos cambiados, valor_anterior, valor_nuevo |
| `ACTA_APROBADA` | COORDINATOR | coordinator_id, timestamp |
