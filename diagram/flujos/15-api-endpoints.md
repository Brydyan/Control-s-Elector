# Flujo 15 — Endpoints REST API

Referencia consolidada de todos los endpoints definidos en los flujos del sistema.

---

## Autenticación

| Método | Endpoint | Rol requerido | Descripción |
|--------|----------|---------------|-------------|
| `POST` | `/api/auth/login` | Público | Login con cédula + contraseña → JWT |
| `POST` | `/api/auth/refresh` | Público | Renueva access token con refresh token |
| `POST` | `/api/auth/revoke` | MASTER | Revoca refresh tokens de un usuario |

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
| `POST` | `/api/usuarios/{user_id}/jrvs` | MASTER | Asignar JRVs a un OBSERVER |
| `GET` | `/api/usuarios/{user_id}` | MASTER | Ver detalle de usuario |

---

## Actas

| Método | Endpoint | Rol requerido | Descripción |
|--------|----------|---------------|-------------|
| `POST` | `/api/actas` | OBSERVER | Registrar acta (datos + foto WebP) |
| `GET` | `/api/actas/{acta_id}` | MASTER · COORDINATOR | Ver acta por ID |
| `GET` | `/api/actas/{acta_id}/detalle` | MASTER · COORDINATOR | Ver acta + detalle votos + audit_log |
| `GET` | `/api/actas/{acta_id}/evidencia` | MASTER · COORDINATOR | Ver foto del acta |
| `GET` | `/api/actas/jrv/{jrv_id}` | OBSERVER (sus JRVs) | Ver actas de una JRV |
| `PATCH` | `/api/actas/{acta_id}` | OBSERVER · COORDINATOR · MASTER | Corregir datos del acta |
| `PATCH` | `/api/actas/{acta_id}/estado` | COORDINATOR · MASTER | Cambiar estado del acta |
| `POST` | `/api/actas/escalar` | OBSERVER | Escalar acta al COORDINATOR (MAX_RETRIES) |

---

## Observer — Vista Propia

| Método | Endpoint | Rol requerido | Descripción |
|--------|----------|---------------|-------------|
| `GET` | `/api/observer/correcciones-pendientes` | OBSERVER | Actas EN_DISPUTA asignadas al observer |

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
