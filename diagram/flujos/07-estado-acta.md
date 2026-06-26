# Flujo 07b — Diagrama de Estados del Acta

Ciclo de vida completo de un acta desde su creación hasta su estado final.

```mermaid
stateDiagram-v2
    [*] --> BORRADOR : OBSERVER abre formulario

    BORRADOR --> ENVIADA : OBSERVER sube acta + foto ✅
    BORRADOR --> BORRADOR : Validación fallida (422)\nvuelve a edición

    ENVIADA --> EN_DISPUTA : COORDINATOR / MASTER\ndetecta inconsistencia foto vs datos
    ENVIADA --> APROBADA : COORDINATOR / MASTER\nconfirma datos correctos

    EN_DISPUTA --> CORREGIDA : OBSERVER corrige\n(notificación recibida)
    EN_DISPUTA --> CORREGIDA : COORDINATOR corrige\n(sin esperar al OBSERVER)

    CORREGIDA --> APROBADA : COORDINATOR / MASTER\nverifica y aprueba
    CORREGIDA --> EN_DISPUTA : COORDINATOR / MASTER\ndetecta nuevo error

    APROBADA --> ANULADA : MASTER detecta fraude\no irregularidad grave

    ANULADA --> [*] : Estado final\ndatos intactos en BD
```

---

## Transiciones Permitidas

| Desde | Hacia | Quién | Condición |
|-------|-------|-------|-----------|
| `BORRADOR` | `ENVIADA` | OBSERVER | Validación OK + foto WebP adjunta |
| `BORRADOR` | `BORRADOR` | OBSERVER | Validación fallida — vuelve a editar |
| `ENVIADA` | `EN_DISPUTA` | COORDINATOR · MASTER | Foto no coincide con datos digitados |
| `ENVIADA` | `APROBADA` | COORDINATOR · MASTER | Datos correctos — aprobación directa |
| `EN_DISPUTA` | `CORREGIDA` | OBSERVER · COORDINATOR | Corrección de datos realizada |
| `CORREGIDA` | `APROBADA` | COORDINATOR · MASTER | Corrección verificada |
| `CORREGIDA` | `EN_DISPUTA` | COORDINATOR · MASTER | Corrección insuficiente — nueva disputa |
| `APROBADA` | `ANULADA` | MASTER | Fraude o irregularidad grave detectada |

---

## Reglas Invariables

- `ANULADA` es estado final — ningún rol puede revertirlo.
- Datos e historial se conservan íntegros en PostgreSQL al anular.
- Actas `ANULADA` se excluyen de estadísticas del dashboard.
- Cada transición genera entrada en `audit_log`.
- Solo existe una acta por `jrv_id + cargo_id` — unicidad garantizada por locking pesimista.
