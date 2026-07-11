# Flujo 11 — Revisión de Actas por COORDINATOR

**Estado:** ⏳ PLANEADO — Flujo principal de validación. Código backend aún no implementado.

Escenario: COORDINATOR revisa actas subidas por OBSERVERS, filtra por usuario y valida contra foto.

```mermaid
sequenceDiagram
    actor COORD as Coordinador
    participant APP as App Angular/Ionic
    participant API as Spring Boot API
    participant PG as PostgreSQL
    participant AUDIT as AuditLogService

    COORD->>APP: Abre Dashboard Coordinador
    APP->>API: GET /api/v1/actas?estado=ENVIADA
    API->>PG: SELECT * FROM acta WHERE estado = 'ENVIADA'
    PG-->>API: [5 actas pendientes]
    API-->>APP: Lista: JRV-001, JRV-023, JRV-045, ...

    COORD->>APP: Selecciona Dropdown "Observer"
    APP->>API: GET /api/v1/observers
    API->>PG: SELECT * FROM usuario WHERE rol_id = ROL_OBSERVER
    PG-->>API: [Juan, María, Pedro, Rosa, Carlos, ...]
    API-->>APP: Lista de OBSERVERS activos

    COORD->>APP: Filtra por "Juan"
    APP->>API: GET /api/v1/actas?registrada_por=juan_id&estado=ENVIADA
    API->>PG: SELECT * FROM acta<br/>WHERE registrada_por = juan_id<br/>AND estado = 'ENVIADA'
    PG-->>API: [3 actas de Juan]
    API-->>APP: Muestra actas de Juan

    COORD->>APP: Toca acta "JRV-001 Alcalde"
    APP->>API: GET /api/v1/actas/{acta_id}
    API->>PG: SELECT a.*, c.nombre, j.numero_junta, r.canton<br/>FROM acta a<br/>JOIN cargo c ON a.cargo_id = c.id<br/>JOIN jrv j ON a.jrv_id = j.id<br/>JOIN recinto r ON j.recinto_id = r.id
    PG-->>API: {jrv: 1, cargo: Alcalde, canton: 2401,<br/>votos: [{cand: María, votos: 120}, ...],<br/>foto_s3: presigned-url}

    API->>API: Genera URL presignada S3<br/>(foto expira en 15 min)
    API-->>APP: Acta + foto_url

    COORD->>APP: Ve foto en lado derecho
    COORD->>APP: Compara:<br/>¿Foto = datos digitados?

    alt Foto COINCIDE
        COORD->>APP: Toca botón "Aprobar"
        APP->>API: PATCH /api/v1/actas/{acta_id}/estado<br/>{estado: APROBADA, aprobada_por: coord_id}

        API->>API: Validación final<br/>SUM(votos) + nulos + blancos = sufragantes
        API->>AUDIT: Registra ACTA_APROBADA<br/>(coordinador, timestamp)
        AUDIT->>PG: INSERT audit_log
        API->>PG: UPDATE acta SET estado='APROBADA',<br/>aprobada_por=coord_id, aprobada_en=NOW()
        PG-->>API: OK

        API-->>APP: 200 OK {estado: APROBADA}
        APP->>APP: Remueve acta de lista
        APP-->>COORD: ✅ Acta aprobada

    else Foto NO COINCIDE
        COORD->>APP: Escribe observaciones<br/>"Foto borrosa, Alcalde no legible"
        COORD->>APP: Toca botón "Marcar EN_DISPUTA"
        APP->>API: PATCH /api/v1/actas/{acta_id}<br/>{estado: EN_DISPUTA,<br/>observaciones: "Foto borrosa...",<br/>requirio_coordinador: true}

        API->>AUDIT: Registra ACTA_EN_DISPUTA<br/>(coordinador, observaciones)
        AUDIT->>PG: INSERT audit_log
        API->>PG: UPDATE acta SET estado='EN_DISPUTA',<br/>observaciones='Foto borrosa...'

        API->>API: Genera notificación a OBSERVER
        API-->>APP: 200 OK {estado: EN_DISPUTA}
        
        APP-->>COORD: ⚠️ Acta marcada en disputa
        
        COORD->>APP: APP envía notificación a Juan<br/>(WebSocket o push)
        COORD-->>COORD: COORDINATOR continúa con siguiente acta

    else Datos INCORRECTOS (pero foto OK)
        COORD->>APP: Elige opción "Corregir"
        APP->>API: PATCH /api/v1/actas/{acta_id}<br/>{votos: [{cand: María, votos: 125}, ...],<br/>requirio_coordinador: true}

        API->>AUDIT: Registra ACTA_CORREGIDA_POR_COORDINATOR
        AUDIT->>PG: INSERT audit_log
        API->>PG: UPDATE acta SET votos=..., version++

        API-->>APP: 200 OK
        APP-->>COORD: ✅ Acta corregida
        
        COORD->>APP: Toca "Aprobar"
        APP->>API: PATCH /api/v1/actas/{acta_id}/estado<br/>{estado: APROBADA}
        API->>PG: UPDATE acta SET estado='APROBADA'
        API-->>APP: ✅ Aprobada
    end
```

---

## Validaciones en API

```java
// Antes de APROBAR:
if (!sumVotosCorrectos(acta)) {
    throw new ValidationException("Totales no coinciden");
}

// Validar que cargo aplica a JRV:
if (!jrvCargoRepository.existsByJrvIdAndCargoId(
    acta.jrvId, acta.cargoId)) {
    throw new InvalidCombinationException("Cargo no aplica");
}

// Transición de estado válida:
if (acta.estado != ActaEstado.ENVIADA && 
    acta.estado != ActaEstado.CORREGIDA) {
    throw new InvalidStateException("No se puede aprobar desde " + acta.estado);
}
```

---

## Estados Posibles de Acta

```
ENVIADA          → APROBADA (si está OK)
                 → EN_DISPUTA (si foto ≠ datos)
                 → CORREGIDA (si COORDINATOR corrige)

EN_DISPUTA       → CORREGIDA (OBSERVER o COORDINATOR corrige)
                 → APROBADA (si se resuelve)

CORREGIDA        → APROBADA (validación final)
                 → EN_DISPUTA (si sigue mal)

APROBADA         → ANULADA (solo MASTER, por fraude)
```

---

## Endpoints Necesarios

| Método | Endpoint | Descripción |
|--------|----------|-------------|
| `GET` | `/api/v1/actas?estado=ENVIADA` | Listar pendientes |
| `GET` | `/api/v1/actas?registrada_por={id}` | Filtrar por OBSERVER |
| `GET` | `/api/v1/observers` | Listar OBSERVERS activos |
| `GET` | `/api/v1/actas/{id}` | Detalle + foto presignada |
| `PATCH` | `/api/v1/actas/{id}/estado` | Cambiar estado |
| `PATCH` | `/api/v1/actas/{id}` | Corregir datos |

---

## Notas de Seguridad

- JWT claim `rol` = COORDINATOR
- @PreAuthorize("hasAuthority('acta:aprobar')")
- RLS: COORDINATOR ve todas las actas (sin restricción)
- Foto: S3 URL presignada (expira en 15 min, secure)
- Audit: Toda acción registrada en audit_log

