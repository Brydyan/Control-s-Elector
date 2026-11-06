# Diagrama 06 — Pipeline de Optimización de Imágenes y Almacenamiento

Estrategia offline-first, compresión adaptativa para celulares modernos (48-108 MP) y replicación asíncrona hacia servidor local.

```mermaid
flowchart TD
    CAM[📷 Cámara Nativa / Galería\n12 a 25 MB - 48 a 108 MP]

    subgraph CLIENT["Procesamiento en Cliente (Angular + OffscreenCanvas)"]
        PREVIEW[Generación Preview Instantáneo\nURL.createObjectURL - 0ms latencia]
        CANVAS[createImageBitmap GPU\nRedimensión máx 2048px width]
        WEBP[Encoder WebP\nPasada 1: Calidad 0.80]
        CHECK_SIZE{¿Tamaño > 650 KB?}
        RECOMPRESS[Pasada 2: Recomprimir\nCalidad 0.65]
        RESULT[Imagen WebP Optimizada\n~450 a 600 KB\nNitidez 100% de firmas y actas]
    end

    subgraph OFFLINE["Almacenamiento Local (IndexedDB)"]
        IDB[(IndexedDB\nCola offline segura\nActa binaria + WebP)]
    end

    subgraph NETWORK["Decisión de Red"]
        CHECK_NET{¿Conexión disponible?}
    end

    subgraph CLOUD["Almacenamiento Nube (Jornada Electoral)"]
        API[Quarkus API Backend\nValidación transaccional]
        R2[(Cloudflare R2\nStorage S3 Compatible\nTier 10 GB Free)]
        ACTA[(PostgreSQL\nacta.foto_url)]
    end

    subgraph LOCAL_VM["Backup Histórico Local"]
        RCLONE[Cron Job / rclone sync\nCada 15 min en background]
        HDD[(HDD Proxmox 500 GB\nCopia histórica offline\nCosto $0)]
    end

    CAM --> PREVIEW
    CAM --> CANVAS
    CANVAS --> WEBP
    WEBP --> CHECK_SIZE
    CHECK_SIZE -->|SÍ| RECOMPRESS
    CHECK_SIZE -->|NO| RESULT
    RECOMPRESS --> RESULT

    RESULT --> CHECK_NET
    CHECK_NET -->|SÍ, hay red| API
    CHECK_NET -->|NO, sin red| IDB
    IDB -.->|Reintento automático background| API

    API --> R2
    API --> ACTA
    R2 ==>|Replicación Asíncrona sin impacto| RCLONE
    RCLONE --> HDD

    classDef offline fill:#fff3e0,stroke:#f57c00
    classDef storage fill:#e8f5e9,stroke:#388e3c
    classDef net fill:#e3f2fd,stroke:#1976d2
    classDef process fill:#f3e5f5,stroke:#7b1fa2

    class IDB offline
    class R2,ACTA,HDD storage
    class API net
    class CANVAS,WEBP,RECOMPRESS,CHECK_SIZE,PREVIEW process
```

---

## Métricas de Reducción de Payload

| Etapa | Formato | Dimensión | Peso Típico | Ahorro |
|---|---|---|---|---|
| Captura móvil moderna (48-108 MP) | JPEG / HEIC original | 8000x6000 | ~15 MB a 25 MB | — |
| Pre-scale GPU + WebP (0.80) | WebP | 2048px max | ~500 KB | **96% - 98%** |
| Recompresión si > 650KB (0.65) | WebP | 2048px max | ~450 KB | **97% - 98%** |
| Transmisión móvil 3G/4G | WebP binario | 2048px | ~500 KB | ✅ Subida en 1-2s |

---

## Capacidad de Almacenamiento Electoral (Provincia Santa Elena)

- **Total actas estimadas**: ~4,000 actas (800 JRVs × 4 dignidades + margen de disputas).
- **Consumo total estimado en Cloudflare R2**: $4{,}000 \times 0.5\text{ MB} = \mathbf{2.0\text{ GB}}$.
- **Tier Gratuito Cloudflare R2**: 10.0 GB/mes (Cubre el 100% de la elección a costo $0.00).
- **Copia Local HDD Proxmox**: 2.0 GB respaldados silenciosamente en tu disco de 500 GB.
