# Diagrama 02 — Topología de Despliegue (Staging vs Producción)

Dos entornos de red completamente diferenciados para QA y producción.

```mermaid
graph LR
    subgraph STAGING["🟢 Entorno Staging — Servidor Local"]
        direction TB
        DEV_DB[(Docker\nPostgreSQL)]
        DEV_STORE[(Emulación\nStorage Local)]
        DEV_API[Spring Boot\nLocal Dev Server]
        TAILSCALE[🔒 Túnel Tailscale\nSin puertos públicos]

        DEV_API --> DEV_DB
        DEV_API --> DEV_STORE
        TAILSCALE --> DEV_API
    end

    subgraph PROD["🔵 Entorno Producción — Nube"]
        direction TB
        subgraph RAILWAY["Railway Pro"]
            PROD_API[Spring Boot API\nCI/CD desde GitHub main]
        end
        subgraph SUPABASE["Supabase Cloud"]
            PROD_DB[(PostgreSQL\nInstancia Dedicada\n+ RLS)]
            PROD_STORE[(Object Storage\nBuckets .webp)]
            PROD_RT[Supabase Realtime\nWebSocket]
        end
        subgraph VERCEL["Vercel (CDN Global)"]
            PROD_APP[App Angular+Ionic\ncontrol.twintailcs.xyz\nRBAC por rol JWT]
        end

        PROD_API --> PROD_DB
        PROD_API --> PROD_STORE
        PROD_DB --> PROD_RT
        PROD_APP -->|HTTPS + JWT| PROD_API
        PROD_RT -->|WebSocket| PROD_APP
    end

    MOBILE_TEST[📱 Dispositivo Móvil\nPruebas de Stress] -->|VPN Tailscale| TAILSCALE
    MOBILE_PROD[📱 Observador\nDía Electoral] -->|HTTPS| PROD_APP
    BROWSER[💻 Coordinator / Master / Admin\nNavegador] -->|HTTPS| PROD_APP

    GITHUB[GitHub\nrama main] -->|CI/CD auto-deploy| PROD_API
```

## Subdominios Producción

| Subdominio | Servicio | Roles | Proveedor |
|------------|---------|-------|-----------|
| `api.twintailcs.xyz` | API REST Spring Boot | Todos (con JWT) | Railway Pro |
| `control.twintailcs.xyz` | App Angular+Ionic — vistas por rol RBAC | MASTER · COORDINATOR · ADMIN · OBSERVER | Vercel |
