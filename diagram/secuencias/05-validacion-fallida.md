# Flujo 05 — Validación de Acta Fallida y Corrección

---

## Parte A — Envío y Rechazo por Validación

Escenario: backend rechaza acta por totales incorrectos y habilita modo edición.

```mermaid
sequenceDiagram
    actor OBS as Observador
    participant APP as App Angular/Ionic
    participant API as Spring Boot API
    participant SVC as ActaService
    participant AUDIT as AuditLogService
    participant JPA as JPA Repository
    participant PG as PostgreSQL

    OBS->>APP: Envía acta con datos erróneos
    APP->>API: POST /api/actas {jrv_id, cargo_id, detalle:[{candidato_id, votos}], nulos: 20, blancos: 0, total: 300}

    API->>API: Valida JWT y rol OBSERVER
    API->>SVC: procesarActa(dto)

    SVC->>SVC: Regla: SUM(detalle.votos) + nulos + blancos == total_sufragantes
    Note over SVC: 150 + 80 + 20 + 0 = 250 ≠ 300 ❌

    SVC-->>API: ValidationException {campo: total_sufragantes}
    API->>AUDIT: registrar(VALIDACION_FALLIDA, user_id, jrv_id, cargo_id, payload, errores)
    AUDIT->>JPA: save(AuditLog)
    JPA->>PG: INSERT audit_log {user_id, jrv_id, acta_id: null, accion: VALIDACION_FALLIDA, payload JSONB, timestamp}
    PG-->>JPA: audit_id
    JPA-->>AUDIT: ok

    API-->>APP: 422 Unprocessable Entity {errores: [{campo, mensaje}]}

    APP->>APP: Resalta campo(s) en error (borde rojo)
    APP->>APP: Muestra mensaje inline por campo
    APP->>APP: Habilita modo edición — formulario desbloqueado
    APP->>APP: Incrementa contador de intentos fallidos
    APP-->>OBS: ❌ Totales no coinciden. Revisa los valores.
```

---

## Parte B — Corrección Exitosa (intentos < 3)

Escenario: observador consulta acta física, corrige el valor, pasa validación cliente y reenvía.

```mermaid
sequenceDiagram
    actor OBS as Observador
    participant APP as App Angular/Ionic
    participant VAL as Validador Cliente
    participant API as Spring Boot API
    participant AUDIT as AuditLogService
    participant SVC as ActaService
    participant STG as Supabase Storage
    participant JPA as JPA Repository
    participant PG as PostgreSQL

    Note over OBS,APP: Observador consulta acta física en papel

    OBS->>APP: Corrige votos de candidato(s) (ej: total = 250)
    APP->>VAL: Valida en cliente antes de reenviar
    VAL->>VAL: SUM(detalle.votos) + nulos + blancos = 250 == 250 ✅
    VAL-->>APP: Sin errores locales

    APP->>APP: Bloquea formulario (evita doble envío)
    APP->>API: POST /api/actas {datos corregidos}

    API->>AUDIT: registrar(ACTA_CORREGIDA, user_id, jrv_id, cargo_id, detalle_anterior, detalle_nuevo)
    AUDIT->>JPA: save(AuditLog)
    JPA->>PG: INSERT audit_log {user_id, jrv_id, accion: ACTA_CORREGIDA, payload JSONB, timestamp}
    PG-->>JPA: audit_id
    JPA-->>AUDIT: ok

    API->>SVC: procesarActa(dto)
    SVC->>SVC: Valida totales ✅
    SVC->>STG: upload imagen.webp
    STG-->>SVC: storage_url
    SVC->>JPA: save(Acta {estado: ENVIADA})
    JPA->>PG: INSERT acta
    PG-->>JPA: acta_id
    SVC->>JPA: saveAll(ActaDetalle)
    JPA->>PG: INSERT acta_detalle (bulk)
    PG-->>JPA: ok

    JPA-->>SVC: Acta persistida
    SVC-->>API: ActaResponseDTO
    API-->>APP: 201 Created {acta_id}
    APP->>APP: Limpia estado de error
    APP-->>OBS: ✅ Acta registrada exitosamente
```

---

## Parte C — Escalación al Coordinator (intentos >= 3)

Escenario: observador agota intentos sin corregir correctamente; caso escala al coordinator.

```mermaid
sequenceDiagram
    actor OBS as Observador
    actor COORD as Coordinator
    participant APP as App Angular/Ionic
    participant API as Spring Boot API
    participant AUDIT as AuditLogService
    participant NOTIF as NotificacionService
    participant JPA as JPA Repository
    participant PG as PostgreSQL

    APP->>APP: Detecta intentos fallidos >= 3
    APP-->>OBS: ⛔ Límite de correcciones alcanzado

    APP->>API: POST /api/actas/escalar {jrv_id, cargo_id, motivo: MAX_RETRIES, acta_borrador}
    API->>JPA: save(Acta {estado: EN_DISPUTA, requirio_coordinador: true})
    JPA->>PG: INSERT acta {estado: EN_DISPUTA, requirio_coordinador: true}
    PG-->>JPA: acta_id

    API->>AUDIT: registrar(VALIDACION_FALLIDA, user_id, jrv_id, cargo_id, intentos: 3)
    AUDIT->>JPA: save(AuditLog)
    JPA->>PG: INSERT audit_log
    PG-->>JPA: audit_id

    API->>NOTIF: notificarCoordinator(acta_id, motivo: MAX_RETRIES)
    NOTIF->>PG: INSERT notificacion {tipo: ACTA_LISTA_PARA_REVISION, acta_id}
    PG-->>NOTIF: ok

    API-->>APP: 201 Created — coordinator notificado
    APP-->>OBS: 📋 Caso escalado al coordinator

    Note over COORD,PG: Coordinator recibe alerta en su panel

    COORD->>API: GET /api/actas/{acta_id}/detalle
    API->>JPA: findByIdWithAuditAndDetalle(acta_id)
    JPA->>PG: SELECT acta + acta_detalle + audit_log + candidatos WHERE acta_id = ?
    PG-->>JPA: Detalle completo
    JPA-->>API: ActaDetalleDTO
    API-->>COORD: Datos {jrv, cargo, candidatos, votos_borrador, intentos, audit_log}

    COORD->>API: PATCH /api/actas/{acta_id} {detalle_corregido, estado: CORREGIDA}
    API->>JPA: updateActa + updateDetalle
    JPA->>PG: UPDATE acta SET estado = CORREGIDA
    JPA->>PG: UPDATE acta_detalle SET votos = ?
    PG-->>JPA: ok
    API-->>COORD: ✅ Acta corregida por coordinator
```

---

## Reglas de Validación del Acta

| Regla | Fórmula | Error |
|---|---|---|
| Suma de votos | `SUM(acta_detalle.votos) + nulos + blancos = total_sufragantes` | 422 campo: total_sufragantes |
| Total no excede padrón | `total_sufragantes <= jrv.total_electores` | 422 campo: total_sufragantes |
| Candidatos obligatorios | Todos los candidatos del cargo con valor >= 0 | 422 campo: candidato_{id} |
| Evidencia obligatoria | Imagen WebP requerida | 422 campo: evidencia |

## Notas de Auditoría

`AuditLog` es tabla en PostgreSQL. Flujo: `AuditLogService → JPA Repository → PostgreSQL`.

| Acción | Cuándo | payload JSONB contiene |
|--------|--------|------------------------|
| `VALIDACION_FALLIDA` | Rechazo 422 | cargo_id, detalle enviado, errores[] |
| `ACTA_CORREGIDA` | Reenvío exitoso | cargo_id, detalle_anterior, detalle_nuevo |

> Registrar fallos permite detectar patrones sospechosos (múltiples intentos con valores distintos en misma JRV/cargo) y provee trazabilidad forense completa ante disputas.
