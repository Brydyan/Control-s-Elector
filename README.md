# Control's Elector — Provincia de Santa Elena 🇪🇨

Sistema de misión crítica para el control, registro y validación de actas electorales en tiempo real, diseñado para las **Elecciones Seccionales 2027-2031** de la Provincia de Santa Elena (Cantones: Santa Elena, La Libertad, Salinas).

Permite a los observadores políticos digitalizar los resultados de las Juntas Receptoras del Voto (JRV) por cada dignidad electoral, adjuntar evidencias fotográficas optimizadas y consolidar los datos de manera inmediata para evitar inconsistencias en el escrutinio general.

---

## Dignidades Electorales

| Cargo | Nivel | Aplica en |
| ------- | ------- | ----------- |
| Prefecto/a | Provincial | Todas las JRVs |
| Consejero/a CPCCS | Nacional | Todas las JRVs |
| Alcalde/sa | Cantonal | JRVs del cantón |
| Concejal Urbano/a | Cantonal | JRVs en zonas urbanas |
| Concejal Rural | Cantonal | JRVs en zonas rurales |
| Vocal Junta Parroquial Rural | Parroquial | Solo parroquias rurales |

---

## Arquitectura del Sistema

Una sola aplicación con control de acceso basado en roles (RBAC). Las vistas se adaptan automáticamente según el rol del usuario autenticado.

```
              ┌──────────────────────────────────────────────────┐
              │     App Angular+PrimeNG — control.twintailcs.xyz │
              │        Route Guards RBAC por rol JWT             │
              │  /observer  /coordinator  /master  /dashboard    │
              └──────────────────────┬───────────────────────────┘
                                     │ HTTPS + Bearer JWT
                                     ▼
              ┌──────────────────────────────────────────────────┐
              │     API Backend — api.twintailcs.xyz             │
              │   Quarkus · SmallRye JWT · Panache               │
              └──────────┬──────────────────────────┬────────────┘
                         │                          │
          (Persistencia) ▼                          ▼ (Evidencias)
     ┌─────────────────────────────┐    ┌──────────────────────────┐
     │ PostgreSQL / Supabase       │    │ Supabase Object Storage  │
     │ + RLS · AuditLog · Realtime │    │ .webp images             │
     └─────────────────────────────┘    └──────────────────────────┘
```

---

## Roles del Sistema

| Rol | Responsabilidad |
| ----- | ---------------- |
| `MASTER` | Acceso total — registra cargos, partidos, candidatos, usuarios. Puede anular actas y revocar sesiones. |
| `COORDINATOR` | Global operativo — gestiona disputas, correcciones y aprobaciones en todas las JRVs. |
| `ADMIN` | Solo lectura — dashboard y estadísticas en tiempo real. No modifica datos. |
| `OBSERVER` | Registra actas en cualquier JRV (libre selección). Corrige actas EN_DISPUTA cuando se le solicita. |

---

## Rutas y Permisos RBAC

| Ruta | Descripción | OBSERVER | COORDINATOR | MASTER | ADMIN |
|------|-------------|----------|-------------|--------|-------|
| `/login` | Autenticación | ✅ | ✅ | ✅ | ✅ |
| **OBSERVER** | | | | | |
| `/observer/home` | Lista de actas propias | ✅ | ❌ | ❌ | ❌ |
| `/observer/registro` | Registrar acta nueva (selecciona JRV + Cantón libremente) | ✅ | ❌ | ❌ | ❌ |
| `/observer/correccion/:id` | Corregir acta EN_DISPUTA | ✅ | ❌ | ❌ | ❌ |
| `/observer/captura/:id` | Capturar/subir foto del acta | ✅ | ❌ | ❌ | ❌ |
| **COORDINATOR** | | | | | |
| `/coordinator/actas` | Listado global de actas con filtros (estado, JRV, OBSERVER) | ❌ | ✅ | ✅ | ❌ |
| `/coordinator/detalle/:id` | Detalle acta + foto + validaciones | ❌ | ✅ | ✅ | ❌ |
| `/coordinator/disputar/:id` | Marcar acta EN_DISPUTA (con motivo) | ❌ | ✅ | ✅ | ❌ |
| `/coordinator/aprobar/:id` | Aprobar acta CORREGIDA o ENVIADA | ❌ | ✅ | ✅ | ❌ |
| **MASTER** | | | | | |
| `/master/configuracion` | Cargos, partidos, candidatos, usuarios | ❌ | ❌ | ✅ | ❌ |
| `/master/cargos` | CRUD cargos electorales | ❌ | ❌ | ✅ | ❌ |
| `/master/partidos` | CRUD partidos políticos | ❌ | ❌ | ✅ | ❌ |
| `/master/candidatos` | CRUD candidatos | ❌ | ❌ | ✅ | ❌ |
| `/master/jrv-cargos` | Mapeo JRV_CARGO (qué cargos aplican por JRV) | ❌ | ❌ | ✅ | ❌ |
| `/master/usuarios` | Crear/editar usuarios (sin pre-asignación JRVs) | ❌ | ❌ | ✅ | ❌ |
| `/master/sesiones` | Ver sesiones activas y revocar tokens | ❌ | ❌ | ✅ | ❌ |
| `/master/anular/:id` | Anular acta APROBADA (irreversible) | ❌ | ❌ | ✅ | ❌ |
| **DASHBOARD** | | | | | |
| `/dashboard/resumen` | Totales por estado de acta | ❌ | ✅ | ✅ | ✅ |
| `/dashboard/resultados` | Votos consolidados por cargo + candidato + cantón | ❌ | ✅ | ✅ | ✅ |
| `/dashboard/progreso` | Actas registradas vs total por cantón | ❌ | ✅ | ✅ | ✅ |

---

## Ciclo de Vida del Acta

```
BORRADOR → ENVIADA → EN_DISPUTA → CORREGIDA → APROBADA → ANULADA
```

| Transición | Quién |
| ------------ | ------- |
| `BORRADOR → ENVIADA` | OBSERVER |
| `ENVIADA → EN_DISPUTA` | COORDINATOR · MASTER |
| `EN_DISPUTA → CORREGIDA` | OBSERVER o COORDINATOR (sin tiempo de espera) |
| `CORREGIDA → APROBADA` | COORDINATOR · MASTER |
| `APROBADA → ANULADA` | Solo MASTER — estado final irreversible |

---

## Stack Tecnológico

### Frontend

- **Angular 21** + **PrimeNG 21** + **Tailwind CSS 4** (TypeScript)
- RBAC por Route Guards según rol del JWT
- Compresión nativa WebP en cliente (Canvas API)
- Estrategia Offline-First: IndexedDB + Private Storage

### Backend

- **Java 25** / **Quarkus 3.40** (RESTEasy Reactive, Hibernate ORM, SmallRye)
- **GraalVM Native Image** — el build nativo cierra y es el artefacto que publica
  el CD (`Dockerfile.native`). El pipeline lo construye y lo verifica en cada PR
  que toca el módulo Quarkus (`.github/workflows/ci-native.yml`).
- RBAC por permisos atómicos sobre JWT stateless: el claim `perms` se proyecta a
  authorities y cada método exige su permiso con una llamada explícita
- Locking pesimista `SELECT FOR UPDATE` por `jrv_id + cargo_id`
- AuditLogService con registro completo de operaciones

### Persistencia

- **PostgreSQL** (Supabase) con Row Level Security (RLS)
- **Supabase Object Storage** — evidencias en formato `.webp`
- **Supabase Realtime** — WebSocket para dashboard en tiempo real

---

## Gestión de Entornos

### Staging — Servidor Local

- PostgreSQL dockerizado + emulación de storage local
- Acceso remoto mediante túnel **Tailscale** sin puertos públicos
- Pruebas de stress desde dispositivos móviles reales

### Producción — Nube

| Subdominio | Servicio | Proveedor |
| ------------ | --------- | ----------- |
| `api.twintailcs.xyz` | API REST Quarkus | Railway Pro |
| `control.twintailcs.xyz` | App Angular+PrimeNG (todos los roles) | Vercel |

CI/CD automático desde la rama `develop` de GitHub hacia Railway.

---

## Resiliencia Offline

Dos capas de protección cuando falla el upload de evidencias:

```
Upload falla 3 veces
    ├── Capa 1: IndexedDB      → reintento automático cada 30s
    └── Capa 2: Private Storage → fallback manual en oficina
                 JRV-{id}_{YYYYMMDD}_{HHmm}_{observer_id}.webp
```

---

## Seguridad

- **JWT** — access token en memoria (15 min), refresh token en localStorage (8h)
- **RLS PostgreSQL** — OBSERVER solo ve actas que registró (`registrada_por = current_user_id`)
- **AuditLog** — cada operación relevante queda registrada con payload JSONB
- **Locking pesimista** — previene condiciones de carrera por `jrv_id + cargo_id`
- **Compresión WebP** — reduce imágenes de ~5MB a ~350KB antes de transmitir

---

## Documentación

```
diagram/
  01-system-architecture.md   → Arquitectura C4 nivel 2
  02-deployment.md             → Topología staging vs producción
  03-data-flow.md              → Índice de todos los flujos
  04-security.md               → RBAC, RLS, Route Guards, AuditLog
  05-domain-model.md           → Modelo de dominio ER completo
  06-image-optimization.md     → Pipeline WebP + resiliencia offline
  secuencias/                  → 14 diagramas de secuencia
  flujos/                      → Estados, casos de uso, endpoints REST
```
