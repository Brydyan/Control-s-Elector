# Flujo 15 — Endpoints REST API

Referencia consolidada de todos los endpoints definidos en los flujos del sistema.

---

## Autenticación

| Método | Endpoint | Rol requerido | Descripción |
|--------|----------|---------------|-------------|
| `POST` | `/api/v1/auth/login` | Público | Login con email + contraseña → accessToken + refreshToken (httpOnly cookie) |
| `POST` | `/api/v1/auth/refresh` | Público | Renueva access token usando refresh token (cookie o body) |
| `POST` | `/api/v1/auth/logout` | Público | Revoca refresh token en DB + borra cookie |
| `POST` | `/api/v1/auth/register` | MASTER | Crear usuario (solo MASTER puede registrar) |
| `GET` | `/api/v1/auth/me` | Autenticado | Obtener datos del usuario actual (desde JWT) |
| `GET` | `/api/v1/menus/me` | Autenticado | Obtener arbol de menus visibles según permisos RBAC |

---

## Actas (OBSERVER · COORDINATOR · MASTER)

| Método | Endpoint | Rol requerido | Descripción |
|--------|----------|---------------|-------------|
| `POST` | `/api/v1/actas` | OBSERVER | Registrar acta (jrv_id, cargo_id, votos[], foto) |
| `GET` | `/api/v1/actas` | OBSERVER · COORDINATOR · MASTER | Listar actas (con filtros) |
| `GET` | `/api/v1/actas?estado=ENVIADA` | COORDINATOR | Pendientes de revisión |
| `GET` | `/api/v1/actas?registrada_por={observer_id}` | COORDINATOR | Filtrar por OBSERVER |
| `GET` | `/api/v1/actas/{id}` | COORDINATOR · MASTER | Detalle acta + foto |
| `PATCH` | `/api/v1/actas/{id}` | COORDINATOR · OBSERVER | Corregir datos |
| `PATCH` | `/api/v1/actas/{id}/estado` | COORDINATOR · MASTER | Cambiar estado (APROBADA, EN_DISPUTA, ANULADA) |
| `GET` | `/api/v1/observers` | COORDINATOR | Listar OBSERVERS activos (para filtrar) |

---

## Configuración Pre-Electoral (MASTER)

| Método | Endpoint | Rol requerido | Descripción |
|--------|----------|---------------|-------------|
| `POST` | `/api/cargos` | MASTER | Registrar cargo electoral |
| `GET` | `/api/cargos` | MASTER · COORDINATOR | Listar cargos |
| `POST` | `/api/partidos` | MASTER | Registrar partido político |
| `GET` | `/api/partidos` | MASTER · COORDINATOR | Listar partidos |
| `POST` | `/api/candidatos` | MASTER | Registrar candidato |
| `GET` | `/api/cargos/{cargo_id}/candidatos` | Todos | Listar candidatos por cargo |
| `POST` | `/api/jrv/{jrv_id}/cargos` | MASTER | Asignar cargos a una JRV |
| `GET` | `/api/jrv/{jrv_id}/cargos` | Todos | Ver cargos aplicables a una JRV |

---

## Usuarios

| Método | Endpoint | Rol requerido | Descripción |
|--------|----------|---------------|-------------|
| `POST` | `/api/usuarios` | MASTER | Crear usuario |
| `GET` | `/api/usuarios/{user_id}` | MASTER | Ver detalle de usuario |

---

## Observer — Vista Propia

| Método | Endpoint | Rol requerido | Descripción |
|--------|----------|---------------|-------------|
| `GET` | `/api/observer/correcciones-pendientes` | OBSERVER | Actas EN_DISPUTA registradas por el observer (requieren corrección) |
| `GET` | `/api/observer/mis-actas` | OBSERVER | Actas propias por estado (ENVIADA, EN_DISPUTA, CORREGIDA, APROBADA) |

---

## Notificaciones

| Método | Endpoint | Rol requerido | Descripción |
|--------|----------|---------------|-------------|
| `GET` | `/api/notificaciones` | Todos | Ver notificaciones propias no leídas |
| `PATCH` | `/api/notificaciones/{id}/leida` | Todos | Marcar notificación como leída |

---

## Resultados (Dashboard)

| Método | Endpoint | Rol requerido | Descripción |
|--------|----------|---------------|-------------|
| `GET` | `/api/resultados/consolidado` | ADMIN · MASTER · COORDINATOR | Resultados consolidados por cargo y cantón |

---

## Convenciones

| Código | Significado |
|--------|-------------|
| `201 Created` | Recurso creado exitosamente |
| `200 OK` | Operación exitosa sin creación |
| `422 Unprocessable Entity` | Validación de negocio fallida (totales incorrectos) |
| `409 Conflict` | Conflicto de concurrencia (JRV + cargo ya registrado) |
| `404 Not Found` | Recurso no encontrado o invisible por RLS |
| `401 Unauthorized` | Token inválido, expirado o revocado |
| `403 Forbidden` | Rol insuficiente para la operación |
| `503 Service Unavailable` | Storage no disponible — reintentar |
