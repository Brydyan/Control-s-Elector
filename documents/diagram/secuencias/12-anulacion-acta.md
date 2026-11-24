# Flujo 12 — Anulación de Acta por MASTER

n**Estado:** ⏳ PLANEADO — Código backend aún no implementado. Ver [diagram/README.md](../README.md).
---

## Escenario

MASTER detecta fraude o irregularidad grave en acta `APROBADA`. Cambia estado a `ANULADA`. Los datos no se borran — quedan en BD para trazabilidad forense.

```mermaid
sequenceDiagram
    actor MASTER as Master
    participant API as Quarkus API
    participant SVC as ActaService
    participant AUDIT as AuditLogService
    participant NOTIF as NotificacionService
    participant JPA as JPA Repository
    participant PG as PostgreSQL

    MASTER->>API: GET /api/actas/{acta_id}/detalle
    API->>JPA: findByIdWithAuditHistory(acta_id)
    JPA->>PG: SELECT acta + audit_log WHERE acta_id = ?
    PG-->>JPA: Acta + historial completo
    JPA-->>API: ActaDetalleDTO
    API-->>MASTER: Datos + foto + historial de cambios

    Note over MASTER: Confirma irregularidad — procede a anular

    MASTER->>API: PATCH /api/actas/{acta_id}/estado {estado: ANULADA, motivo}
    API->>API: Valida rol MASTER
    API->>SVC: anularActa(acta_id, motivo, master_id)

    SVC->>JPA: updateEstado(acta_id, ANULADA)
    JPA->>PG: UPDATE acta SET estado = ANULADA
    Note over PG: Datos intactos — solo cambia estado
    PG-->>JPA: ok

    SVC->>AUDIT: registrar(ACTA_ANULADA, master_id, acta_id, motivo)
    AUDIT->>JPA: save(AuditLog)
    JPA->>PG: INSERT audit_log {accion: ACTA_ANULADA, payload JSONB, timestamp}
    PG-->>JPA: audit_id
    JPA-->>AUDIT: ok

    SVC->>NOTIF: notificarCoordinator(acta_id, motivo)
    NOTIF->>PG: INSERT notificacion {tipo: ACTA_ANULADA, acta_id, motivo}
    NOTIF-->>API: ok

    SVC-->>API: ok
    API-->>MASTER: 200 OK — acta anulada
```

---

## Estados del Acta (completo)

```
BORRADOR → ENVIADA → EN_DISPUTA → CORREGIDA → APROBADA → ANULADA
```

| Estado | Quién puede llegar aquí | Reversible |
|--------|------------------------|------------|
| `BORRADOR` | OBSERVER | ✅ |
| `ENVIADA` | OBSERVER | ✅ (si EN_DISPUTA) |
| `EN_DISPUTA` | COORDINATOR / MASTER | ✅ (→ CORREGIDA) |
| `CORREGIDA` | OBSERVER / COORDINATOR | ✅ (→ APROBADA) |
| `APROBADA` | COORDINATOR / MASTER | ⚠️ solo MASTER puede anular |
| `ANULADA` | MASTER | ❌ estado final |

---

## Reglas de Anulación

- Solo MASTER puede ejecutar esta acción.
- `ANULADA` es estado final — no hay transición de salida.
- Datos e historial se conservan íntegros en PostgreSQL.
- Actas `ANULADA` se excluyen de estadísticas comparativas en dashboard.
- COORDINATOR recibe notificación automática al producirse la anulación.

## Notas de Auditoría

| Acción | Quién | payload JSONB contiene |
|--------|-------|------------------------|
| `ACTA_ANULADA` | MASTER | motivo, master_id, estado_anterior: APROBADA |
