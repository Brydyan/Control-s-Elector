# 📊 Diagramas de Arquitectura — Control Electoral

Documentación visual de la arquitectura, flujos, y modelo de datos del sistema electoral.

---

## ✅ Estado de Implementación

| Módulo | Estado | Código | Diagramas |
|--------|--------|--------|-----------|
| **Autenticación** | ✅ IMPLEMENTADO | `backend/electoral-api/auth/` | 03, 03-1, 03-2, 04 |
| **Menus RBAC** | ✅ IMPLEMENTADO | `backend/electoral-api/auth/application/MenuService.java` | 03-1 |
| **Evidencias (Storage S3)** | ✅ IMPLEMENTADO | `backend/electoral-api/evidencia/` | 08, 10 |
| **Actas (CRUD)** | ⏳ PLANEADO | — | 01, 02, 05, 06, 11, 12 |
| **Candidatos/Cargos/Partidos** | ⏳ PLANEADO | — | 13 |
| **Notificaciones** | ⏳ PLANEADO | — | 09, 14 |
| **Dashboard/Resultados** | ⏳ PLANEADO | — | 09 |
| **Modelo de Datos** | 📋 REFERENCIA | — | 05 |

---

## 📁 Estructura de Diagramas

### Diagramas Principales
- **01-system-architecture.md** — Visión C4 nivel 2 (Usuarios, App, API, BD) ✅
- **02-deployment.md** — Staging vs Producción ✅
- **03-data-flow.md** — Índice de todos los flujos
- **04-security.md** — RBAC, RLS, AuditLog ✅
- **05-domain-model.md** — Modelo ER completo (Referencia) 📋

### Flujos de Secuencia (`secuencias/`)

| Flujo | Estado | Descripción |
|-------|--------|-------------|
| **03-autenticacion-login.md** | ✅ | Login con email + password → JWT + menus RBAC |
| **03-1-menus-rbac.md** | ✅ | GET /api/v1/menus/me — construcción de sidebar |
| **03-2-user-me.md** | ✅ | GET /api/v1/auth/me — datos usuario actual |
| **04-token-expirado.md** | ⏳ | Refresh token automático en cliente |
| **01-registro-exitoso.md** | ⏳ | OBSERVER registra acta exitosamente |
| **02-offline-sync.md** | ⏳ | Sincronización post-conexión desde IndexedDB |
| **05-validacion-fallida.md** | ⏳ | Rechazo de acta por totales incorrectos |
| **06-concurrencia-misma-jrv.md** | ⏳ | Dos OBSERVER mismo JRV → locking pesimista |
| **07-rls-acceso-denegado.md** | ⏳ | OBSERVER intenta acta fuera su JRV → RLS rechaza |
| **08-compresion-imagen.md** | ⏳ | WebP compression en cliente (Canvas API) |
| **09-dashboard-realtime.md** | ⏳ | Supabase Realtime → actualización WebSocket |
| **10-error-storage.md** | ⏳ | Manejo de reintentos S3 + fallback offline |
| **11-disputa-acta.md** | ⏳ | COORDINATOR marca acta EN_DISPUTA |
| **12-anulacion-acta.md** | ⏳ | MASTER anula acta APROBADA |
| **13-configuracion-preelectoral.md** | ⏳ | MASTER configura cargos/partidos/candidatos |
| **14-notificaciones.md** | ⏳ | Push notifications a OBSERVER |

### Otros Diagramas (`flujos/`)

| Diagrama | Estado | Descripción |
|----------|--------|-------------|
| **07-estado-acta.md** | 📋 | Máquina de estados ACTA (BORRADOR → ANULADA) |
| **08-caso-de-uso.md** | 📋 | Casos de uso por actor/rol |
| **15-api-endpoints.md** | ✅ | Referencia de endpoints REST (actualizado) |

---

## 🔄 Cambios Recientes (2026-07-06)

**Diagrama 03 corregido:**
- ❌ `{cedula, password}` → ✅ `{email, password}`
- ❌ `{accessToken, refreshToken, jrv_asignada}` → ✅ `{accessToken, tokenType, expiresInSeconds, sesionId, usuario}`
- ✅ Agregados: GET /api/v1/menus/me, GET /api/v1/auth/me
- ✅ Rutas: `/api/auth/*` → `/api/v1/auth/*`

**Nuevos diagramas:**
- `03-1-menus-rbac.md` — MenuService RBAC tree algorithm
- `03-2-user-me.md` — User info endpoint

**Endpoint reference actualizada:**
- `15-api-endpoints.md` — 6 endpoints de auth documentados

---

## 🚀 Próximos Pasos

### Fase 1: Actas (Próxima)
- [ ] ActaService + ActaController (CRUD)
- [ ] Validación de totales (SUM votos + nulos + blancos = sufragantes)
- [ ] Locking pesimista (SELECT FOR UPDATE por jrv_id + cargo_id)
- [ ] AuditLog automático (CREATE, UPDATE, estado changes)
- [ ] Actualizar diagramas: 01, 05, 06, 11, 12, 15

### Fase 2: Notificaciones
- [ ] NotificacionService + WebSocket push
- [ ] CORRECCION_REQUERIDA cuando OBSERVER → EN_DISPUTA
- [ ] ACTA_LISTA_PARA_REVISION cuando OBSERVER corrige
- [ ] Actualizar diagramas: 09, 14, 15

### Fase 3: Dashboard
- [ ] ResultadosService (consolidado por cargo/cantón)
- [ ] Supabase Realtime subscribe
- [ ] Actualizar diagramas: 09, 15

---

## 📝 Convenciones de Diagramas

- **Participante auditoría:** `AUDIT as AuditLogService`
- **Flujo audit:** API → AUDIT → JPA → PostgreSQL
- **Roles:** `MASTER`, `COORDINATOR`, `ADMIN`, `OBSERVER` (inglés)
- **Estados acta:** `BORRADOR`, `ENVIADA`, `EN_DISPUTA`, `CORREGIDA`, `APROBADA`, `ANULADA` (mayúsculas)
- **Diagramas seq:** `sequenceDiagram` (Mermaid)
- **Estado machine:** `stateDiagram` (Mermaid)
- **Casos uso:** `graph` (Mermaid)

