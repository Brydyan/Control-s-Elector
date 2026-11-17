# Diagrama 02 — Topología de Despliegue (Staging, Producción y Backup)

Topología de despliegue con alta disponibilidad para el día electoral y resguardo histórico a costo $0.

```mermaid
graph LR
    subgraph STAGING["🟢 Entorno Staging / Backup — VM Proxmox (Local)"]
        direction TB
        DEV_DB[(Podman\nPostgreSQL Local)]
        DEV_API[Quarkus REST API\nContainer Podman]
        HDD_BACKUP[(HDD 500 GB\nMirror Asíncrono R2\nrclone sync cada 15 min)]
        TUNNEL_CF[🔒 Cloudflare Tunnel\nRespaldo Contingencia]

        DEV_API --> DEV_DB
        TUNNEL_CF --> DEV_API
    end

    subgraph PROD["🔵 Entorno Producción — Cloud Alta Disponibilidad"]
        direction TB
        subgraph CF_EDGE["Cloudflare Edge Global"]
            CF_PAGES[Cloudflare Pages\nFrontend Angular\nCDN 300+ Ciudades\n99.99% Uptime]
            CF_R2[(Cloudflare R2\nStorage S3 Compatible\nTier 10 GB Gratis\n0$ Egress)]
        end

        subgraph CLOUD_VPS["VPS Dedicado (Hetzner / DigitalOcean)"]
            PROD_API[Quarkus API REST\nPodman Container\nPool Hikari / PgBouncer]
            PROD_DB[(PostgreSQL 16\nInstancia dedicada\n+ RLS Transaccional)]
            PROD_API --> PROD_DB
        end

        CF_PAGES -->|HTTPS REST + JWT| PROD_API
        PROD_API -->|Presigned URLs / Direct Upload| CF_R2
    end

    MOBILE_OBS[📱 Observador Electoral\nMóvil 3G/4G] -->|HTTPS WebP ~500KB| CF_PAGES
    BROWSER_DIR[💻 Coordinator / Admin / Master\nDashboard Analítico] -->|HTTPS Lectura Inmediata| CF_PAGES
    CF_PAGES -->|Lectura de fotos de actas| CF_R2

    CF_R2 ==>|Sync Asíncrono rclone\n(Sin impacto en subida)| HDD_BACKUP
```

---

## Estrategia de Infraestructura y Costos

| Componente | Plataforma | Rol en Elección | Costo Estimado |
|---|---|---|---|
| **Frontend** | Cloudflare Pages | Distribución global de estáticos Angular. Inmune a picos de tráfico. | **$0.00** |
| **Storage Actas** | Cloudflare R2 | Almacenamiento rápido de actas optimizadas (~500 KB). 10 GB gratis al mes. | **$0.00** (< 3 GB para ~4,000 actas) |
| **API Backend** | VPS Cloud (Podman) | Procesamiento transaccional de actas y autenticación RBAC con Quarkus. | ~$15 - $25 (solo mes electoral) |
| **Base de Datos** | PostgreSQL en VPS | Única fuente de verdad electoral, RLS y exclusión de anuladas. | Incluido en VPS |
| **Copia Fría / Backup** | VM Proxmox (HDD 500GB) | Replicación asíncrona periódica de actas mediante `rclone` y hot-standby. | **$0.00** (Infraestructura propia) |
