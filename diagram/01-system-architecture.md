# Diagrama 01 — Arquitectura del Sistema (C4 Nivel 2)

Visión general de los componentes desacoplados del ecosistema electoral.

```mermaid
graph TB
    subgraph USUARIOS["👥 Usuarios"]
        OBS[📱 Observador\nDispositivo Móvil]
        COORD[💻 Coordinator / Master\nNavegador]
        ADMIN_U[📊 Admin\nNavegador]
    end

    subgraph APP["🟩 App — control.twintailcs.xyz\n(Angular + Ionic / Vercel)"]
        AUTH_GUARD[Route Guards\nRBAC por rol JWT]

        subgraph VIEW_OBS["Vista OBSERVER"]
            UI_SELECT[Selecciona JRV + Cantón\nLibremente]
            UI_CAPTURE[Formulario de Acta<br/>Cargo por JRV_CARGO]
            IMG_COMPRESS[Compresor WebP\nnativo en cliente]
            OFFLINE_IDB[IndexedDB\nCola Offline]
            OFFLINE_FS[Private Storage\nJRV-id_fecha_obs.webp]
            PENDING[Correcciones de\nCOORDINATOR]
        end

        subgraph VIEW_COORD["Vista COORDINATOR / MASTER"]
            UI_COORD[Gestión: Disputas\nCorrecciones · Aprobaciones]
            UI_MASTER[Gestión: Cargos · Partidos\nCandidatos · Usuarios]
        end

        subgraph VIEW_DASH["Vista ADMIN / MASTER / COORDINATOR"]
            UI_DASH[Dashboard: Resultados\npor Cargo y Cantón\nTiempo Real]
        end

        AUTH_GUARD --> VIEW_OBS
        AUTH_GUARD --> VIEW_COORD
        AUTH_GUARD --> VIEW_DASH
    end

    subgraph API["🟦 API Backend — api.twintailcs.xyz\n(Spring Boot / Railway)"]
        REST[REST Controllers]
        SECURITY[Spring Security\nRBAC + JWT]
        SERVICE[Business Services\nActaService · AuditLogService\nNotificacionService]
        JPA[Spring Data JPA\nLocking Optimista / Pesimista]
    end

    subgraph PERSIST["🗄️ Capa de Persistencia (Supabase)"]
        PG[(PostgreSQL\n+ RLS Policies\naudit_log · notificaciones)]
        STORAGE[(Object Storage\n.webp evidencias)]
        RT[Supabase Realtime\nWebSocket]
    end

    OBS --> APP
    COORD --> APP
    ADMIN_U --> APP

    UI_CAPTURE --> IMG_COMPRESS
    IMG_COMPRESS --> REST
    UI_CAPTURE --> REST
    OFFLINE_IDB -.->|reintento 30s| REST
    OFFLINE_FS -.->|exportación manual| REST
    PENDING --> REST
    UI_COORD --> REST
    UI_MASTER --> REST
    UI_DASH -->|Bearer JWT| REST

    REST --> SECURITY
    SECURITY --> SERVICE
    SERVICE --> JPA
    JPA --> PG
    SERVICE --> STORAGE
    SERVICE -->|notificaciones push| APP

    PG --> RT
    RT -->|WebSocket| UI_DASH
```

---

## Rutas por Rol

| Ruta | Rol requerido | Función |
|------|--------------|---------|
| `/login` | Público | Autenticación |
| `/observer/*` | OBSERVER | Registrar actas (selecciona JRV + Cantón), correcciones solicitadas |
| `/coordinator/*` | COORDINATOR · MASTER | Disputas, correcciones, aprobaciones |
| `/master/*` | MASTER | Cargos, partidos, candidatos, usuarios |
| `/dashboard/*` | ADMIN · MASTER · COORDINATOR | Resultados en tiempo real |
