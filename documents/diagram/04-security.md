# Diagrama 04 — Modelo de Seguridad y Control de Acceso

Capas de seguridad desde la app hasta la base de datos.

```mermaid
graph TD
    subgraph CLIENT["Capa Cliente — control.brydyan.me"]
        APP[App Angular+Ionic]
        GUARD[Route Guards<br/>RBAC por rol JWT]
        V_OBS[Vista /observer/*]
        V_COORD[Vista /coordinator/* · /master/*]
        V_DASH[Vista /dashboard/*]

        APP --> GUARD
        GUARD -->|OBSERVER| V_OBS
        GUARD -->|COORDINATOR · MASTER| V_COORD
        GUARD -->|ADMIN · MASTER · COORDINATOR| V_DASH
    end

    subgraph API_SEC["Capa API — Quarkus Security RBAC"]
        JWT[JWT Filter<br/>Validación de Token]
        ROLES{Roles}
        R_MASTER[ROLE_MASTER<br/>Acceso total + anulación]
        R_COORD[ROLE_COORDINATOR<br/>Global operativo — disputas y correcciones]
        R_ADMIN[ROLE_ADMIN<br/>Global solo lectura — dashboard y stats]
        R_OBS[ROLE_OBSERVER<br/>Registra actas libres — ve solo sus registros]

        JWT --> ROLES
        ROLES --> R_MASTER
        ROLES --> R_COORD
        ROLES --> R_ADMIN
        ROLES --> R_OBS
    end

    subgraph DB_SEC["Capa Base de Datos — PostgreSQL RLS"]
        RLS[Row Level Security]
        POL_MASTER[Política MASTER<br/>Sin restricción]
        POL_COORD[Política COORDINATOR<br/>Sin restricción — todas las actas]
        POL_ADMIN[Política ADMIN<br/>Sin restricción — solo lectura vía API]
        POL_OBS[Política OBSERVER<br/>WHERE registrada_por = current_user_id<br/>Solo sus actas]

        RLS --> POL_MASTER
        RLS --> POL_COORD
        RLS --> POL_ADMIN
        RLS --> POL_OBS
    end

    subgraph AUDIT["Trazabilidad — AuditLog"]
        AUDIT_SVC[AuditLogService]
        AUDIT_TBL[(audit_log<br/>PostgreSQL)]
        AUDIT_SVC -->|JPA| AUDIT_TBL
    end

    subgraph CONCURRENCY["Control de Concurrencia — JPA"]
        OPT[Locking Optimista<br/>@Version en entidades]
        PES[Locking Pesimista<br/>SELECT FOR UPDATE<br/>acta por JRV]
    end

    V_OBS -->|Bearer JWT| JWT
    V_COORD -->|Bearer JWT| JWT
    V_DASH -->|Bearer JWT| JWT

    R_MASTER --> RLS
    R_COORD --> RLS
    R_ADMIN --> RLS
    R_OBS --> RLS

    R_OBS --> OPT
    R_COORD --> PES
    R_MASTER --> PES

    R_MASTER --> AUDIT_SVC
    R_COORD --> AUDIT_SVC
    R_OBS --> AUDIT_SVC
```

---

## Matriz de Permisos

| Acción | MASTER | COORDINATOR | ADMIN | OBSERVER |
|--------|--------|-------------|-------|----------|
| Gestionar usuarios y configuración | ✅ | ❌ | ❌ | ❌ |
| Registrar cargos, partidos y candidatos | ✅ | ❌ | ❌ | ❌ |
| Ver todas las actas | ✅ | ✅ | ✅ APROBADAS | ✅ sus registros |
| Registrar acta | ❌ | ❌ | ❌ | ✅ cualquier JRV |
| Ver evidencias (fotos) | ✅ | ✅ | ✅ | ✅ sus registros |
| Marcar acta EN_DISPUTA | ✅ | ✅ | ❌ | ❌ |
| Corregir acta | ✅ | ✅ | ❌ | ✅ si se le solicita |
| Aprobar acta | ✅ | ✅ | ❌ | ❌ |
| Anular acta APROBADA | ✅ | ❌ | ❌ | ❌ |
| Revocar tokens de sesión | ✅ | ❌ | ❌ | ❌ |
| Ver dashboard y estadísticas | ✅ | ✅ | ✅ | ❌ |
| Recibir notificación de corrección | ❌ | ❌ | ❌ | ✅ |

> La fuente de verdad de los permisos atómicos es el seed de Liquibase
> (`002-rbac-seed.sql`, `008-configuracion.sql`, `009-acta-observer-permisos.sql`).

### Endpoint → permiso y rate limit

Matriz por endpoint (guardrail: todo endpoint declara sus roles). Se completa a
medida que se tocan endpoints; hoy cubre los del rate limiting (sc-521).

| Endpoint | Permiso exigido | Roles con ese permiso (seed) | Rate limit |
|---|---|---|---|
| `POST /api/v1/auth/login` | público | — | `login-ip` + `login-account(-ip)` |
| `PUT /api/v1/user/update-personal-email` | autenticado | todos | `reauth` |
| `POST /api/v1/legal/contact-dpo` | público | — | `contact-dpo` |
| `GET /api/v1/observer/jrvs` | `acta:read` | OBSERVER, COORDINATOR, ADMIN, MASTER | `observer-jrvs` |
| `GET /api/v1/observer/jrv/{id}/cargos` | `acta:read` | OBSERVER, COORDINATOR, ADMIN, MASTER | `observer-jrvs` |
| `POST /api/v1/observer/actas` | `acta:upload` | OBSERVER, MASTER | `observer-actas` |
| `GET /api/v1/configuracion/{cargos,partidos,candidatos}` | autenticado | todos | `config-read` |
| `GET /api/v1/admin/usuarios` | `usuario:read` o MASTER | MASTER, COORDINATOR, ADMIN | `admin-usuarios` |
| cualquier endpoint autenticado | — | — | `user-global` |

Pendiente de decisión: el seed da `acta:upload` a MASTER, pero la matriz de
arriba dice "Registrar acta: MASTER ❌".

---

## Políticas RLS

| Rol | Política | Alcance |
|-----|----------|---------|
| `MASTER` | Sin restricción | Todas las filas |
| `COORDINATOR` | Sin restricción | Todas las filas |
| `ADMIN` | Sin restricción | Todas las filas (lectura forzada en capa API) |
| `OBSERVER` | `WHERE registrada_por = current_user_id` | Solo sus actas registradas |

---

## Acciones Auditadas

| Acción | Quién la genera |
|--------|----------------|
| `VALIDACION_FALLIDA` | OBSERVER — totales incorrectos rechazados |
| `STORAGE_REINTENTOS_AGOTADOS` | OBSERVER — upload imagen falló 3 veces |
| `ACTA_EN_DISPUTA` | COORDINATOR / MASTER |
| `ACTA_CORREGIDA_POR_OBSERVER` | OBSERVER |
| `ACTA_CORREGIDA_POR_COORDINATOR` | COORDINATOR |
| `ACTA_APROBADA` | COORDINATOR / MASTER |
| `ACTA_ANULADA` | MASTER |
