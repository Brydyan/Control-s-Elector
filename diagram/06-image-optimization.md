# Diagrama 06 — Pipeline de Optimización de Imágenes

Estrategia offline-first y compresión de evidencias fotográficas en el cliente.

```mermaid
flowchart TD
    CAM[📷 Cámara Nativa\n~5 MB PNG/JPEG]

    subgraph CLIENT["Procesamiento en Cliente (Angular/Ionic)"]
        CANVAS[Canvas API\nRedimensión máx 1920x1080]
        WEBP[Encoder WebP\nCalidad 0.82]
        CHECK_SIZE{¿Tamaño > 500KB?}
        RECOMPRESS[Recomprimir\nCalidad 0.65]
        RESULT[Imagen WebP\n~350 KB]
    end

    subgraph OFFLINE["Almacenamiento Local (sin red)"]
        IDB[(IndexedDB\nCola offline\ndatos + imagen)]
        FS[(Private Storage\n/files/evidencias/\nJRV-042_20261115_1423_OBS-187.webp)]
    end

    subgraph NETWORK["Decisión de Red"]
        CHECK_NET{¿Conexión disponible?}
    end

    subgraph UPLOAD["Transmisión"]
        PAYLOAD[Multipart HTTP\njrv_id + cargo_id + detalle_votos + WebP]
        API[API Backend\nSpring Boot]
        STORAGE[(Supabase Storage\nBucket evidencias)]
        ACTA[(PostgreSQL\nacta.storage_url)]
    end

    CAM --> CANVAS
    CANVAS --> WEBP
    WEBP --> CHECK_SIZE
    CHECK_SIZE -->|SÍ| RECOMPRESS
    CHECK_SIZE -->|NO| RESULT
    RECOMPRESS --> RESULT

    RESULT --> CHECK_NET
    CHECK_NET -->|SÍ, hay red| PAYLOAD
    CHECK_NET -->|NO, sin red| IDB
    CHECK_NET -->|NO, sin red| FS

    IDB -.->|Reintento automático 30s| PAYLOAD
    FS -.->|Exportación manual en oficina| PAYLOAD

    PAYLOAD --> API
    API --> STORAGE
    STORAGE -->|storage_url| API
    API -->|INSERT acta con storage_url| ACTA
    API -->|201 Created| CLIENT

    classDef offline fill:#fff3e0,stroke:#f57c00
    classDef storage fill:#e8f5e9,stroke:#388e3c
    classDef net fill:#e3f2fd,stroke:#1976d2
    classDef process fill:#f3e5f5,stroke:#7b1fa2

    class IDB,FS offline
    class STORAGE,ACTA storage
    class PAYLOAD,API net
    class CANVAS,WEBP,RECOMPRESS,CHECK_SIZE process
```

---

## Reducción de Payload

| Etapa | Formato | Tamaño Típico | Ahorro |
|-------|---------|---------------|--------|
| Captura nativa | PNG/JPEG | ~5 MB | — |
| Tras compresión cliente (0.82) | WebP | ~350 KB | **93%** |
| Recompresión si excede 500KB (0.65) | WebP | ~300 KB | **94%** |
| Transmisión 3G/4G | WebP (multipart) | ~350 KB | ✅ Viable |

---

## Capas de Resiliencia Offline

| Capa | Almacenamiento | Propósito | Cuándo se limpia |
|------|---------------|-----------|-----------------|
| **1 — IndexedDB** | Cola en navegador | Reintento automático cada 30s | Al sincronizar exitosamente |
| **2 — Private Storage** | `/files/evidencias/` app | Fallback manual en oficina | Al sincronizar exitosamente |

## Formato de Nombre — Private Storage

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

> Directorio privado de la app — no accesible por otras apps, no requiere permisos especiales. La `storage_url` resultante se persiste directamente en `acta.storage_url` (no existe tabla EVIDENCIA separada).
