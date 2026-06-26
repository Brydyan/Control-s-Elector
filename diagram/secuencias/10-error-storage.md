# Flujo 10 — Error al Subir Imagen a Storage (Compensación)

Escenario: el upload de la evidencia falla; el sistema compensa para no dejar datos huérfanos.

---

## Parte A — Fallo de Upload y Guardado Local

```mermaid
sequenceDiagram
    participant APP as App Angular/Ionic
    participant API as Spring Boot API
    participant SVC as ActaService
    participant STG as Supabase Storage
    participant AUDIT as AuditLogService
    participant IDB as IndexedDB (Cola Local)
    participant FS as App Private Storage<br/>/data/data/com.app/files/evidencias/
    participant JPA as JPA Repository
    participant PG as PostgreSQL

    APP->>API: POST /api/actas (datos + webp)
    API->>SVC: procesarActa(dto, imagen)

    SVC->>STG: upload imagen.webp (bucket: evidencias)
    STG-->>SVC: ❌ 503 Service Unavailable

    Note over SVC: Primer reintento (1 seg)
    SVC->>STG: upload imagen.webp (retry 1/3)
    STG-->>SVC: ❌ Timeout

    Note over SVC: Segundo reintento (2 seg)
    SVC->>STG: upload imagen.webp (retry 2/3)
    STG-->>SVC: ❌ Timeout

    SVC->>SVC: Agotados los reintentos
    SVC-->>API: StorageException {retriesExhausted}

    API->>AUDIT: registrar(STORAGE_REINTENTOS_AGOTADOS, user_id, jrv_id, intentos: 3)
    AUDIT->>JPA: save(AuditLog)
    JPA->>PG: INSERT audit_log {user_id, jrv_id, accion: STORAGE_REINTENTOS_AGOTADOS, payload JSONB, timestamp}
    PG-->>JPA: audit_id
    JPA-->>AUDIT: ok

    Note over API,JPA: No se persiste acta en PostgreSQL.<br/>Transacción nunca inició — no hay rollback necesario.

    API-->>APP: 503 Service Unavailable<br/>{code: STORAGE_UNAVAILABLE, retryAfter: 30}

    APP->>IDB: Guarda acta completa (datos + imagen) en cola offline
    IDB-->>APP: Encolada (id_local)

    APP->>FS: Guarda imagen con nombre estructurado
    Note over FS: JRV-{jrv_id}_{YYYYMMDD}_{HHmm}_{observer_id}.webp<br/>Ej: JRV-042_20261115_1423_OBS-187.webp
    FS-->>APP: Archivo persistido en directorio privado

    APP-->>APP: Programa reintento automático en 30 seg
    APP-->>APP: ⚠️ Sin red — acta guardada localmente
```

---

## Parte B — Reintento Automático Exitoso (corte breve)

```mermaid
sequenceDiagram
    participant APP as App Angular/Ionic
    participant IDB as IndexedDB (Cola Local)
    participant FS as App Private Storage
    participant API as Spring Boot API
    participant SVC as ActaService
    participant STG as Supabase Storage
    participant JPA as JPA Repository
    participant PG as PostgreSQL

    Note over APP: ...30 segundos después...

    APP->>IDB: Obtiene actas pendientes
    IDB-->>APP: [acta_1, acta_2, ...]

    APP->>API: POST /api/actas (reintento desde cola local)
    API->>SVC: procesarActa(dto, imagen)
    SVC->>STG: upload imagen.webp
    STG-->>SVC: ✅ storage_url
    SVC->>JPA: save(Acta{datos, storage_url, estado: ENVIADA})
    JPA->>PG: INSERT acta
    PG-->>JPA: acta_id
    JPA-->>SVC: ok
    SVC-->>API: ActaResponseDTO
    API-->>APP: 201 Created

    APP->>IDB: Elimina acta de cola local
    APP->>FS: Elimina archivo local (ya sincronizado)
    APP-->>APP: ✅ Acta sincronizada
```

---

## Parte C — Exportación Manual en Oficina (corte prolongado)

Escenario: reintento automático nunca logró conectar (corte prolongado). OBSERVER llega a oficina y exporta evidencia manualmente.

```mermaid
sequenceDiagram
    actor OBS as Observador
    participant APP as App Angular/Ionic
    participant IDB as IndexedDB (Cola Local)
    participant FS as App Private Storage
    participant NET as Monitor de Red
    participant API as Spring Boot API
    participant STG as Supabase Storage
    participant JPA as JPA Repository
    participant PG as PostgreSQL

    OBS->>APP: Abre "Pendientes sin sincronizar"
    APP->>IDB: findActasPendientes()
    IDB-->>APP: [JRV-042, JRV-051, ...]
    APP-->>OBS: Lista actas pendientes con estado y timestamp

    OBS->>APP: Selecciona acta JRV-042
    APP->>NET: ¿Hay conexión?

    alt Con red disponible en oficina
        NET-->>APP: ✅ Conexión disponible
        APP->>API: POST /api/actas (reintento directo)
        API->>STG: upload imagen.webp
        STG-->>API: ✅ storage_url
        API->>JPA: save(Acta{datos, storage_url, estado: ENVIADA})
        JPA->>PG: INSERT acta
        PG-->>JPA: acta_id
        API-->>APP: 201 Created
        APP->>IDB: Elimina acta de cola local
        APP->>FS: Elimina archivo local (ya sincronizado)
        APP-->>OBS: ✅ Acta sincronizada automáticamente

    else Sin red — exportación manual
        NET-->>APP: ❌ Sin conexión
        APP->>FS: Lee JRV-042_20261115_1423_OBS-187.webp
        FS-->>APP: Archivo WebP
        APP-->>OBS: Opciones: compartir por correo / WhatsApp / cable USB
        OBS->>APP: Selecciona método de exportación
        APP->>APP: Invoca Android Share Sheet con archivo
        APP-->>OBS: 📤 Archivo enviado — entregar a coordinador en oficina
    end
```

---

## Capas de Resiliencia

```
Upload falla 3 veces
    ├── Capa 1: IndexedDB      → reintento automático cada 30s (cortes breves)
    └── Capa 2: Storage privado → fallback manual en oficina (cortes prolongados)
                 └── nombre: JRV-{jrv_id}_{YYYYMMDD}_{HHmm}_{observer_id}.webp
```

## Formato de Nombre de Archivo

```
JRV-{jrv_id}_{YYYYMMDD}_{HHmm}_{observer_id}.webp

Ejemplo: JRV-042_20261115_1423_OBS-187.webp
```

| Parte | Dato | Propósito |
|-------|------|-----------|
| `JRV-042` | jrv_id | Identifica mesa exacta |
| `20261115` | Fecha | Día de votación |
| `1423` | Hora local | Orden cronológico |
| `OBS-187` | observer_id | Quién capturó la evidencia |

## Notas

- Archivos guardados en directorio privado de la app — no accesibles por otras apps, no requieren permisos especiales.
- Al sincronizar exitosamente (Parte B o C con red), el archivo local se elimina automáticamente.
- La exportación manual solo se ofrece cuando no hay red disponible en el momento del intento.
