# Software Requirements Specification (SRS)
## Control Electoral — Elecciones Seccionales 2027-2031

**Documento formal:** IEEE 830  
**Versión:** 1.0  
**Fecha:** 2026-07-18  
**Proyecto:** Control Electoral (Santa Elena, Ecuador)  
**Estado:** Activo — En desarrollo

---

## 1. INTRODUCCIÓN

### 1.1 Propósito

Este documento especifica los requisitos funcionales y no funcionales del sistema de **Control Electoral**, un aplicativo web y móvil para la observación y registro **en tiempo real** de actas electorales de las Elecciones Seccionales 2027-2031 en la Provincia de Santa Elena, Ecuador.

**Objetivo estratégico:** Permitir que veedores políticos registren, corrijan y verifiquen actas de forma segura, auditada y resiliente, garantizando transparencia electoral y detectando inconsistencias.

**Nota importante:** Este es un sistema de **observación y registro de resultados**, no un sistema de votación.

### 1.2 Alcance

El sistema cubre los siguientes procesos electorales:

1. **Autenticación y control de acceso** (JWT, RBAC, sesiones)
2. **Registro de actas** por observadores con selección libre de JRVs
3. **Validación de datos** (totales, candidatos, partidos)
4. **Captura y almacenamiento** de evidencia fotografica (WebP)
5. **Flujo de disputas y correcciones** (COORDINATOR interviene ante inconsistencias)
6. **Aprobación y consolidación** (MASTER sella actas)
7. **Auditoría completa** (logs inmutables por transacción)
8. **Dashboard en tiempo real** (estadísticas por canton, cargo, estado)
9. **Notificaciones** (cambios de estado, correcciones solicitadas)
10. **Resiliencia offline** (retry automático, fallback a storage privado)

**No incluye:**
- Votación electrónica ni máquinas de voto
- Transmisión de resultados a órganos electorales (OEE) — solo registro interno
- Cálculo de ganadores o escrutinio oficial
- Acceso público a resultados parciales

### 1.3 Definiciones, Acrónimos y Abreviaturas

| Término | Definición |
|---------|-----------|
| **Acta** | Registro de votos emitidos en una JRV para un cargo específico. Contiene totales, desglose por candidato, votos nulos y blancos. Foto anexada. |
| **ActaDetalle** | Desglose de votos por candidato dentro de un acta. |
| **AUDIT_LOG** | Registro inmutable de todas las acciones del sistema (creación, modificación, aprobación, anulación). |
| **COORDINATOR** | Rol operativo: revisa actas, marca disputas, aprueba correcciones, gestiona flujos globales. |
| **JRV** | Junta Receptora de Votos — recinto donde se realiza votación. Identifica única por `jrv_id + cargo_id`. |
| **JRV_CARGO** | Tabla de mapeo: qué cargos aplican a cada JRV según geografía (nivel parroquia, cantón o provincial). |
| **MASTER** | Rol administrativo: registra catálogos (cargos, partidos, candidatos), crea usuarios, revoca sesiones, anula actas. |
| **OBSERVER** | Rol de veedores políticos: registra actas con selección libre de JRVs en su recinto, corrige ante solicitud. |
| **ADMIN** | Rol de lectura global: accede a dashboard y estadísticas, no modifica datos. |
| **RLS** | Row Level Security — política de seguridad en PostgreSQL que filtra filas según usuario. |
| **RBAC** | Role-Based Access Control — matriz de permisos por rol. |
| **Refresh Token** | Token opaco (máx 8h) para renovar access tokens sin re-autenticar. Persistido en BD para revocación. |
| **WebP** | Formato de imagen comprimido, requerido. Objetivo: ~350 KB, rango 0.65-0.82 de calidad. |

### 1.4 Referencias

| Documento | Ubicación | Propósito |
|-----------|-----------|----------|
| CLAUDE.md | Raíz del proyecto | Contexto de negocio, roles, reglas invariables |
| Modelo de Dominio (05) | `documents/diagram/05-domain-model.md` | ER completo: entidades, relaciones, atributos |
| Seguridad (04) | `documents/diagram/04-security.md` | RBAC, RLS, JWT, AuditLog |
| Estado del Acta (07) | `documents/diagram/flujos/07-estado-acta.md` | Máquina de estados del ciclo de vida |
| API Endpoints (15) | `documents/diagram/flujos/15-api-endpoints.md` | Especificación técnica de endpoints REST |
| Secuencias (01-14) | `documents/diagram/secuencias/` | Flujos detallados por caso de uso |
| Mockups | `documents/mockups/` | Diseño de UI por rol (login, dashboard, actas, etc.) |
| CREDENCIALES.md | Raíz del proyecto | URLs de desarrollo, credenciales dev-only |
| DOCKER.md | Raíz del proyecto | Setup local, troubleshooting |

### 1.5 Visión General del Documento

```
Sección 1 — Introducción (propósito, alcance, definiciones)
Sección 2 — Descripción General (contexto, usuarios, entorno)
Sección 3 — Requisitos Específicos (RF numerados, RNF, interfaces)
Sección 4 — Estado de Implementación (qué ya está, qué falta)
Sección 5 — Apéndices (tablas, glosarios, diagramas)
```

---

## 2. DESCRIPCIÓN GENERAL

### 2.1 Perspectiva del Producto

**Visión de negocio:**  
Control Electoral es un observatorio digital para las Elecciones Seccionales 2027-2031. Los veedores políticos registran resultados con libre selección de JRVs (entrada de datos + foto). Coordinadores revisan y corrigen. MASTER sella y puede anular por fraude. Todo queda auditado en bases de datos seguras.

**Posicionamiento técnico:**  
- **Escalable:** PostgreSQL (Neon) + RLS para millones de registros, Cloudflare R2 para fotos.
- **Seguro:** JWT + sesiones revocables, audit log inmutable, locking pesimista en actas.
- **Resiliente:** Offline-first en móvil (IndexedDB + fallback a storage privado), reintentos automáticos.
- **Transparente:** Una sola app (sin subdominios por rol), RBAC visual en menús dinámicos.

**No es un sistema de votación** — no toca máquinas de voto ni ánforas. Solo registra lo que ya se votó.

### 2.2 Funciones del Producto

| Rol | Capacidades principales |
|-----|--------|
| **OBSERVER** | • Crear actas con libre selección de JRVs (BORRADOR → ENVIADA) • Descargar foto de acta previamente capturada • Corregir datos si COORDINATOR marca EN_DISPUTA • Ver propias actas y correcciones pendientes • Capturar foto (Canvas API, WebP) e intentar upload automático. Si falla → IndexedDB (reintento cada 30s) → fallback storage privado |
| **COORDINATOR** | • Revisar actas ENVIADAS • Marcar EN_DISPUTA si foto no coincide • Aprobar si datos OK • Corregir directamente si OBSERVER no está • Listar actas por estado, observer, cargo • Resolver disputas • Notificar cambios |
| **MASTER** | • Registrar cargos, partidos, candidatos • Crear usuarios (email + password, rol) • Asignar JRVs a OBSERVERs • Revocar sesiones (logout global) • Anular actas APROBADAS por fraude grave • Ver dashboard completo |
| **ADMIN** | • Dashboard y estadísticas (solo lectura) • Gráficos por canton, cargo, estado • Filtros por fecha, rol, region |

### 2.3 Clases de Usuario y Matriz de Permisos

| Acción | Endpoint | MASTER | COORDINATOR | ADMIN | OBSERVER |
|--------|----------|:------:|:-----------:|:-----:|:--------:|
| Login | `POST /auth/login` | ✅ | ✅ | ✅ | ✅ |
| Refresh token | `POST /auth/refresh` | ✅ | ✅ | ✅ | ✅ |
| Ver usuario actual | `GET /auth/me` | ✅ | ✅ | ✅ | ✅ |
| Registrar usuario (crear otro) | `POST /auth/register` | ✅ | ❌ | ❌ | ❌ |
| Ver menús propios | `GET /menus/me` | ✅ | ✅ | ✅ | ✅ |
| Registrar acta | `POST /actas` | ❌ | ❌ | ❌ | ✅* |
| Listar actas | `GET /actas` | ✅ | ✅ | ❌ | ✅** |
| Ver detalle acta + foto | `GET /actas/{id}` | ✅ | ✅ | ❌ | ✅*** |
| Corregir datos acta | `PATCH /actas/{id}` | ✅ | ✅ | ❌ | ✅**** |
| Cambiar estado acta | `PATCH /actas/{id}/estado` | ✅ | ✅ | ❌ | ❌ |
| Anular acta | `PATCH /actas/{id}/estado=ANULADA` | ✅ | ❌ | ❌ | ❌ |
| Upload evidencia | `POST /evidencias` | ✅ | ❌ | ❌ | ✅ |
| Aprobar/rechazar evidencia | `PATCH /evidencias/{id}/estado` | ✅ | ❌ | ❌ | ❌ |
| Registrar cargo | `POST /cargos` | ✅ | ❌ | ❌ | ❌ |
| Registrar partido | `POST /partidos` | ✅ | ❌ | ❌ | ❌ |
| Registrar candidato | `POST /candidatos` | ✅ | ❌ | ❌ | ❌ |
| Asignar JRVs a OBSERVER | `POST /observer/{id}/jrvs` | ✅ | ❌ | ❌ | ❌ |
| Ver dashboard estadísticas | `GET /resultados/consolidado` | ✅ | ✅ | ✅ | ❌ |

*OBSERVER registra con libre selección de JRV  
**OBSERVER solo ve propias; COORDINATOR ve todas  
***OBSERVER solo propias; COORDINATOR todas  
****OBSERVER solo propias (EN_DISPUTA); COORDINATOR cualquiera

### 2.4 Entorno Operativo

#### Stack Tecnológico

| Capa | Tecnología | Versión |
|------|-----------|---------|
| **Frontend** | Angular + PrimeNG + Tailwind CSS (TypeScript) | 21.x / 21.x / 4.x |
| **Backend API** | Java Quarkus (REST) | 3.40 (Java 25) |
| **Base de datos** | PostgreSQL | 16+ (Neon) |
| **Storage** | Cloudflare R2 (prod) / MinIO o RustFS (dev) | — |
| **Realtime** | WebSocket o polling HTTP (mecanismo por definir) | — |
| **Auth** | JWT (Nimbus JOSE+JWT, HS256) | — |
| **ORM** | Hibernate ORM + Panache | gestionado por Quarkus 3.40 |
| **Migrations** | Liquibase | 4.x |
| **Dev local** | Docker Compose | v2.20+ |
| **Deploy backend** | Railway (plan Pro, 1 réplica) | Pro |
| **Deploy frontend** | Cloudflare Pages | — |
| **Dominios** | brydyan.me | — |
| **Subdominio API** | api.brydyan.me | HTTPS |
| **Subdominio App** | control.brydyan.me | HTTPS |

#### Entornos

| Entorno | Host | DB | Storage | Notes |
|---------|------|----|---------|----|
| **Local (dev)** | localhost:4200 (FE), localhost:8081 (BE) | Docker PostgreSQL | MinIO / RustFS | Hot-reload, debug |
| **Staging** | — | PostgreSQL Docker + Tailscale | MinIO | Cerrado, sin puertos públicos |
| **Producción** | Cloudflare Pages (FE), Railway (BE) | Neon PostgreSQL | Cloudflare R2 | HTTPS, logs centralizados |

### 2.5 Restricciones y Limitaciones

#### Restricciones de Negocio

- **Geografía:** Sistema exclusivo para Santa Elena (3 cantones: Santa Elena 2401, La Libertad 2402, Salinas 2403).
- **Período:** Válido durante proceso electoral 2027-2031; datos pueden persistir post-electoral para auditoría.
- **Libre selección de JRVs:** OBSERVER opera sobre cualquier JRV del cantón/recinto (decisión 2026-08-14 para contingencia y sustitución en campo; observer_jrv_asignacion deprecada). RLS filtra actas por registrada_por_id.
- **Una sola acta por JRV+Cargo:** Garantizado por locking pesimista en BD. Evita doble registro.
- **Acta anulada es irreversible:** MASTER no puede "desanular". Solo marca ANULADA.
- **ADMIN nunca escribe:** Lectura de dashboard únicamente.

#### Restricciones Técnicas

- **Imágenes obligatorias:** Cada acta requiere foto WebP (máx 1920×1080, ~350 KB objetivo).
- **Límite por foto:** 10 MB absoluto. Si resulta > 500 KB → recomprimir a calidad 0.65.
- **Offline-first:** Mobile (Ionic) debe funcionar sin red:
  - IndexedDB para reintentos automáticos (cada 30s, máx 3 intentos).
  - Fallback: storage privado del dispositivo si 3 reintentos agotan.
- **TTL tokens:** Access 15 min (memoria JS, nunca localStorage). Refresh 8h (httpOnly cookie + BD).
- **Sin puertos públicos en Staging:** Acceso via Tailscale únicamente.

### 2.6 Supuestos y Dependencias

#### Supuestos

- Todos los OBSERVER tienen equipos móviles (Android/iOS via Capacitor).
- Existen conectividades intermitentes (4G en Juntas); fallback offline es crítico.
- COORDINATOR/ADMIN/MASTER acceden desde navegador web (desktop o tablet).
- Datos electorales (cargos, partidos, candidatos) preexisten en BD antes de proceso electoral (MASTER registra).
- JRVs disponibles para selección libre por OBSERVERs.

#### Dependencias Externas

| Dependencia | Rol | Riesgo | Mitigación |
|-------------|-----|--------|----------|
| **Neon PostgreSQL** (prod) | BD principal | Downtime externo | Failover a backup; logs en Railway |
| **Cloudflare R2** (prod) | Almacenar fotos (WebP) | Downtime, pérdida de fotos | Versionado en el bucket; checksum SHA-256 |
| **Mecanismo realtime** (prod) | Actualización del dashboard | Latencia o caída | Fallback a polling HTTP cada 5s |
| **Cloudflare Pages** (prod) | Servir frontend | Downtime regional | CDN global; no crítico si BE está up |
| **Railway** (prod) | Ejecutar backend | Downtime crítico | Plan Pro; alertas automáticas |

---

## 3. REQUISITOS ESPECÍFICOS

### 3.1 Requisitos Funcionales

#### RF-AUTH: Autenticación y Sesiones

| RF-AUTH-001 | Login de usuario |
|---|---|
| **Descripción** | Usuario ingresa email + password; sistema valida en BD y retorna JWT (access + refresh). |
| **Actores** | Todos (público) |
| **Endpoint** | `POST /api/v1/auth/login` |
| **Entrada** | `{ email: string, password: string }` |
| **Precondición** | Usuario existe en BD y no está `habilitado = false`. |
| **Flujo principal** | 1. Validar email existe. 2. Hash password + comparar. 3. Si OK: generar JWT + refresh token opaco. 4. Guardar refresh token en BD con `expiresAt = now + 8h`. 5. Retornar `{ accessToken, refreshToken (httpOnly cookie) }` + datos usuario (`publicId, email, rol.nombre`). |
| **Flujo alterno** | Email no existe o password incorrecto → `401 Unauthorized`. Usuario deshabilitado → `403 Forbidden`. |
| **Postcondición** | Sesión creada; token valido por 15 min. |
| **Reglas negocio** | • Contraseña hasheada con bcrypt (nunca plain text). • Access token: memoria JS (no localStorage). • Refresh: cookie httpOnly, path `/api/v1/auth`, SameSite=Strict (HTTPS prod). |

| RF-AUTH-002 | Refresh token (renovar access) |
|---|---|
| **Descripción** | Cliente envía refresh token (via cookie o body) para obtener nuevo access token sin re-login. |
| **Actores** | Todos |
| **Endpoint** | `POST /api/v1/auth/refresh` |
| **Entrada** | Cookie: `refresh_token`. O body: `{ refreshToken: string }`. |
| **Precondición** | Refresh token válido, no expirado, no revocado. |
| **Flujo principal** | 1. Validar refresh token en BD. 2. Si expirado o `revocado = true` → rechazar. 3. Generar nuevo access token (TTL 15 min). 4. Retornar `{ accessToken }`. |
| **Flujo alterno** | Token no valido/expirado → `401 Unauthorized`. |
| **Postcondición** | Nuevo access token active por 15 min. |
| **Reglas negocio** | • Refresh token máx 8h desde creation. • Renovación no extiende refresh (dura original 8h). • Si refresh próximo a expirar (< 1h), retornar nueva cookie refresh también. |

| RF-AUTH-003 | Logout (revocar sesión) |
|---|---|
| **Descripción** | Usuario cierra sesión; refresh token revocado en BD, cookie borrada en cliente. |
| **Actores** | Autenticado (cualquier rol) |
| **Endpoint** | `POST /api/v1/auth/logout` |
| **Entrada** | Header: `Authorization: Bearer <accessToken>`. |
| **Precondición** | Access token válido. |
| **Flujo principal** | 1. Extraer `user_id` del JWT. 2. Marcar `revocado = true` en refresh_token de ese usuario. 3. Borrar cookie `refresh_token`. 4. Retornar `200 OK { message: "Logged out" }`. |
| **Flujo alterno** | Token inválido → `401 Unauthorized`. |
| **Postcondición** | Usuario no puede usar refresh token. Siguiente request sin access token fallará. |
| **Reglas negocio** | • MASTER puede revocar sesiones de otros usuarios (feature futura). • Cookie `secure` en prod (HTTPS). |

| RF-AUTH-004 | Ver usuario actual |
|---|---|
| **Descripción** | Endpoint para que cliente verifique identidad y permisos tras login. |
| **Actores** | Autenticado (cualquier rol) |
| **Endpoint** | `GET /api/v1/auth/me` |
| **Entrada** | Header: `Authorization: Bearer <accessToken>`. |
| **Precondición** | Access token válido. |
| **Flujo principal** | 1. Extraer `user_id` del JWT. 2. Buscar Usuario en BD. 3. Retornar `{ publicId (uuid), email, rol: "MASTER" | "COORDINATOR" | "ADMIN" | "OBSERVER" }`. |
| **Flujo alterno** | Token inválido/expirado → `401 Unauthorized`. |
| **Postcondición** | Cliente tiene identidad confirmada. |
| **Reglas negocio** | • No retornar password ni datos sensibles. • Mapeado via MapStruct (publicId → id). |

| RF-AUTH-005 | Registrar nuevo usuario |
|---|---|
| **Descripción** | MASTER crea nuevo usuario (nombre, apellido, password, rol). Email se genera automáticamente. |
| **Actores** | MASTER |
| **Endpoint** | `POST /api/v1/auth/register` |
| **Entrada** | `{ nombre: string, apellido: string, password: string, rol: "MASTER" | "COORDINATOR" | "ADMIN" | "OBSERVER" }` |
| **Precondición** | Autenticado como MASTER. Password ≥ 8 caracteres. Nombre/apellido ≥ 2 caracteres. |
| **Flujo principal** | 1. Validar permiso (MASTER). 2. Generar email con formato `{3 letras nombre}{3 letras apellido}{3 letras rol}@control.ttcs` (normaliza tildes, resuelve colisiones con sufijo numérico). 3. Hash password. 4. Crear Usuario con `habilitado = true`. 5. Asignar rol. 6. Retornar `201 Created { publicId, email, nombre, apellido, rol }`. 7. AUDIT_LOG: `USUARIO_CREADO`. |
| **Flujo alterno** | Email generado ya existe tras sufijos incrementales → `500 Internal Server Error` (MAX_RETRIES agotados). Rol inválido → `422`. Rol insuficiente (MASTER) → `403 Forbidden`. Validación fallida → `422`. |
| **Postcondición** | Usuario creado, listo para login con email generado. |
| **Reglas negocio** | • Email generado automáticamente, no manual. • Normalización: tildes removidas (José → jose), solo letras alfab. en email. • Colisión resuelta: si `juaperobs@` existe, intenta `juaperobs2@`, `juaperobs3@`, etc. (máx 100 reintentos). • No retornar password en respuesta. • RNF: log alertado si MASTER crea usuario sin asignación de JRVs (orphaned OBSERVER). |

#### RF-MENU: Menús Dinámicos por RBAC

| RF-MENU-001 | Obtener menús del usuario |
|---|---|
| **Descripción** | Retorna árbol de menús/rutas autorizadas basado en rol + permisos del usuario. |
| **Actores** | Autenticado (cualquier rol) |
| **Endpoint** | `GET /api/v1/menus/me` |
| **Entrada** | Header: `Authorization: Bearer <accessToken>`. |
| **Precondición** | Access token válido. |
| **Flujo principal** | 1. Extraer `user_id` del JWT. 2. Buscar Usuario → rol → RolPermiso. 3. Construir árbol de menús visibles. 4. Retornar `[ { label, route, icon, children[] } ]`. |
| **Flujo alterno** | Token inválido → `401 Unauthorized`. |
| **Postcondición** | Cliente renderiza sidebar solo con opciones permitidas. |
| **Reglas negocio** | • Menús = permissions traducidas a rutas Angular (ej: `acta:crear` → `/observer/nueva-acta`). • OBSERVER ve "Mis JRVs", "Actas". • COORDINATOR ve "Actas", "Disputas", "Reportes". • ADMIN ve "Dashboard", "Resultados". • MASTER ve todo. |

---

#### RF-GEO: Geografía Electoral (Provincia/Canton/Parroquia/JRV)

| RF-GEO-001 | Registrar provincia |
|---|---|
| **Descripción** | MASTER crea región geográfica de nivel provincia (Santa Elena). |
| **Actores** | MASTER |
| **Endpoint** | `POST /api/geo/provincias` (futuro) |
| **Entrada** | `{ nombre: string, codigo: string }` |
| **Precondición** | MASTER autenticado. Código único. |
| **Flujo principal** | 1. Validar permiso. 2. Crear Provincia. 3. AUDIT_LOG: `PROVINCIA_CREADA`. 4. Retornar `201`. |
| **Flujo alterno** | Código duplicado → `409 Conflict`. |
| **Postcondición** | Provincia disponible para crear cantones. |
| **Reglas negocio** | • Código = formato nacional Ecuador (ej: 24 para Santa Elena). |

| RF-GEO-002 | Registrar cantón |
|---|---|
| **Descripción** | MASTER crea cantón dentro de provincia. |
| **Actores** | MASTER |
| **Endpoint** | `POST /api/geo/cantones` (futuro) |
| **Entrada** | `{ provincia_id: uuid, nombre: string, codigo: string }` |
| **Precondición** | MASTER autenticado. Provincia existe. Código único. |
| **Flujo principal** | 1. Validar permiso + provincia. 2. Crear Canton. 3. Retornar `201`. |
| **Flujo alterno** | Código duplicado → `409 Conflict`. |
| **Postcondición** | Cantón disponible para JRVs. |
| **Reglas negocio** | • Código formato local (ej: 2401, 2402, 2403 para Santa Elena). • Máximo 3 cantones por provincia. |

| RF-GEO-003 | Listar cantones por provincia |
|---|---|
| **Descripción** | OBSERVER/COORDINATOR selecciona cantón antes de registrar acta. |
| **Actores** | OBSERVER, COORDINATOR, MASTER |
| **Endpoint** | `GET /api/geo/provincias/{id}/cantones` (futuro) |
| **Entrada** | URL param: `provincia_id`. |
| **Precondición** | Autenticado. Provincia existe. |
| **Flujo principal** | 1. Validar permisos (OBSERVER solo via RLS: jrv de su canton). 2. Query cantones de provincia. 3. Retornar lista. |
| **Flujo alterno** | Provincia no existe → `404`. |
| **Postcondición** | UI llena dropdown de cantones. |

| RF-GEO-004 | Registrar JRV |
|---|---|
| **Descripción** | MASTER crea Junta Receptora de Votos dentro de parroquia. |
| **Actores** | MASTER |
| **Endpoint** | `POST /api/geo/jrvs` (futuro) |
| **Entrada** | `{ parroquia_id: uuid, numero: string, recinto: string, total_electores: int }` |
| **Precondición** | MASTER autenticado. Parroquia existe. |
| **Flujo principal** | 1. Validar permiso + parroquia. 2. Crear Jrv. 3. Retornar `201 { jrv_id, ... }`. |
| **Flujo alterno** | Parroquia no existe → `404`. |
| **Postcondición** | JRV lista para registrar actas. |
| **Reglas negocio** | • `numero`: identifica JRV dentro del recinto (ej: "JRV 001", "JRV 002"). • `recinto`: nombre completo del local (ej: "Escuela Fiscal República Argentina"). • `total_electores`: empadronados en esa junta. |

---

#### RF-EVID: Evidencia Fotografica (Upload/Descarga/Validación)

| RF-EVID-001 | Upload de foto de acta |
|---|---|
| **Descripción** | OBSERVER captura acta (Canvas → WebP), intenta upload a S3. Si falla → IndexedDB. |
| **Actores** | OBSERVER |
| **Endpoint** | `POST /api/v1/evidencias` (multipart) |
| **Entrada** | FormData: `file: Blob (WebP)`, `jrv_id: uuid`, `cargo_id: uuid` |
| **Precondición** | Autenticado como OBSERVER. JRV en asignación. Archivo < 10 MB. MIME type `image/*`. |
| **Flujo principal** | 1. Validar archivo (tipo, tamaño, dimensiones ≤ 1920×1080). 2. Calcular SHA-256. 3. Si tamaño > 500 KB → notificar cliente "recomprimir a 0.65". 4. Upload a S3 (key: `actas/{jrv_id}/{cargo_id}/{uuid}.webp`). 5. Si OK → crear Evidencia en BD. 6. Retornar `201 { publicId (uuid), s3_key, size_bytes, sha256 }`. 7. AUDIT_LOG: `EVIDENCIA_SUBIDA`. |
| **Flujo alterno** | • Archivo inválido → `422 Unprocessable Entity`. • S3 no responde → `503 Service Unavailable` (trigger reintentos en cliente). • Archivo > 10 MB → `413 Payload Too Large`. |
| **Postcondición** | Evidencia registrada, accesible via GET. Si upload falla 3 veces en cliente → almacenada en IndexedDB + notificación "retry manual en oficina". |
| **Reglas negocio** | • WebP obligatorio. • SHA-256 para detectar duplicados/corrupción. • S3 con presigned URLs (15 min). • Compensating delete: si BD falla después S3 OK → marcar S3 como huérfano (log + limpieza posterior). • RLS: OBSERVER solo ve propias; COORDINATOR ve todas. |

| RF-EVID-002 | Descargar URL presignada de foto |
|---|---|
| **Descripción** | Cliente descarga foto via presigned URL (válida 15 min). No requiere re-auth. |
| **Actores** | COORDINATOR, ADMIN, MASTER (o OBSERVER de la acta propia) |
| **Endpoint** | `GET /api/v1/evidencias/{id}/download-url` |
| **Entrada** | URL param: `evidencia_id`. |
| **Precondición** | Autenticado. Autorizado (RLS). Evidencia existe. |
| **Flujo principal** | 1. Validar permisos (RLS). 2. Generar presigned GET URL (válida 15 min). 3. Retornar `{ url: "https://storage.../actas/..." }`. |
| **Flujo alterno** | Evidencia no existe → `404`. Permisos insuficientes → `403`. |
| **Postcondición** | Cliente puede descargar foto sin requiere auth en S3. |
| **Reglas negocio** | • URL expira en 15 minutos. • No usar URLs de larga vida (security). |

| RF-EVID-003 | Aprobar/rechazar evidencia |
|---|---|
| **Descripción** | Requiere coordinador-like rol (para actas futuro). Hoy: endpoint presente pero acoplado a foto de acta. |
| **Actores** | COORDINATOR, MASTER |
| **Endpoint** | `PATCH /api/v1/evidencias/{id}/estado` |
| **Entrada** | `{ estado: "APROBADA" | "RECHAZADA" }` |
| **Precondición** | Autenticado como COORDINATOR/MASTER. Evidencia en estado `EN_REVISION`. |
| **Flujo principal** | 1. Validar permiso. 2. Cambiar estado. 3. Si RECHAZADA → marcar para reupload. 4. Retornar `200`. 5. AUDIT_LOG: `EVIDENCIA_{APROBADA|RECHAZADA}`. |
| **Flujo alterno** | Ya aprobada/rechazada → `409 Conflict`. |
| **Postcondición** | Acta bloqueada si evidencia rechazada. |

---

#### RF-ACTA: Registro y Ciclo de Vida del Acta (PLANEADO)

| RF-ACTA-001 | Registrar nueva acta |
|---|---|
| **Descripción** | OBSERVER inicia registro en BORRADOR, luego envía (ENVIADA). Acta = jrv_id + cargo_id (única). |
| **Actores** | OBSERVER |
| **Endpoint** | `POST /api/v1/actas` |
| **Entrada** | `{ jrv_id: uuid, cargo_id: uuid, votos_nulos: int, votos_blancos: int, total_sufragantes: int, detalle: [ { candidato_id: uuid, votos: int } ], evidencia_id: uuid (optional) }` |
| **Precondición** | OBSERVER autenticado. JRV en asignación. Cargo válido para JRV (via JRV_CARGO). No existe acta APROBADA|EN_DISPUTA|CORREGIDA para (jrv_id, cargo_id). |
| **Flujo principal** | **Fase BORRADOR (no en API, en cliente):** 1. Cliente abre form, rellena datos. 2. Valida localmente. **Fase ENVIADA (API):** 3. Cliente POST a `/actas` con datos. 4. API valida: `SUM(detalle.votos) + votos_nulos + votos_blancos = total_sufragantes`. 5. Locking pesimista: `SELECT * FROM acta WHERE jrv_id = ? AND cargo_id = ? FOR UPDATE`. 6. Si existe (no ANULADA) → `409 Conflict`. 7. Crear Acta (estado = ENVIADA). 8. Crear ActaDetalle (uno por candidato). 9. AUDIT_LOG: `ACTA_REGISTRADA`. 10. Retornar `201 { acta_id, estado: ENVIADA }`. |
| **Flujo alterno** | • Validación SUM fallida → `422 Unprocessable Entity { error: "Totales no coinciden" }`. • JRV + cargo ya registrado → `409 Conflict { error: "Acta existente" }`. • Candidato no válido para cargo → `422`. • Datos incompletos → `422`. |
| **Postcondición** | Acta en estado ENVIADA, pendiente revisión COORDINATOR. ActaDetalle contiene votos por candidato. |
| **Reglas negocio** | • Una sola acta activa por (jrv_id, cargo_id). • Locking pesimista evita race condition si dos OBSERVERs intentan registrar simultáneamente. • OBSERVER solo en JRVs propias (RLS). • SUM validación: `SUM(votos) + nulos + blancos = total_sufragantes`. Si NO coincide → rechazada con error descriptivo. • Evidencia (foto) es opcional al registrar (puede subirse luego). |

| RF-ACTA-002 | Ver detalles de acta |
|---|---|
| **Descripción** | Visualizar acta completa (header + detalle de votos + foto si existe). |
| **Actores** | COORDINATOR, MASTER, OBSERVER (propia) |
| **Endpoint** | `GET /api/v1/actas/{id}` |
| **Entrada** | URL param: `acta_id`. |
| **Precondición** | Autenticado. Autorizado (RLS). |
| **Flujo principal** | 1. Validar permisos. 2. Query acta + actadetalle (JOIN candidato/partido). 3. Si foto → generar presigned URL. 4. Retornar `{ acta: {...}, detalle: [...], foto_url: "...", estado: "..." }`. |
| **Flujo alterno** | Acta no existe → `404`. Permisos insuficientes → `403`. |
| **Postcondición** | UI muestra desglose de votos, foto, metadata (quién registró, cuándo). |

| RF-ACTA-003 | Marcar acta EN_DISPUTA |
|---|---|
| **Descripción** | COORDINATOR detecta inconsistencia (foto no coincide con datos); marca EN_DISPUTA. OBSERVER recibe notificación. |
| **Actores** | COORDINATOR, MASTER |
| **Endpoint** | `PATCH /api/v1/actas/{id}/estado` |
| **Entrada** | `{ estado: "EN_DISPUTA", motivo: "Foto no coincide con totales digitados" }` |
| **Precondición** | Autenticado como COORDINATOR/MASTER. Acta en ENVIADA. |
| **Flujo principal** | 1. Validar permiso + estado. 2. Cambiar `estado = EN_DISPUTA`. 3. Guardar `motivo_disputa`. 4. Crear NOTIFICACION: `{ observer_id, acta_id, tipo: CORRECCION_REQUERIDA }`. 5. AUDIT_LOG: `ACTA_EN_DISPUTA`. 6. Retornar `200`. |
| **Flujo alterno** | Acta no ENVIADA → `409 Conflict`. |
| **Postcondición** | OBSERVER ve notificación + puede corregir. |

| RF-ACTA-004 | Corregir acta (por OBSERVER o COORDINATOR) |
|---|---|
| **Descripción** | Cambiar datos tras disputa. OBSERVER corrige propia. COORDINATOR puede corregir cualquiera. |
| **Actores** | OBSERVER (propia EN_DISPUTA), COORDINATOR (cualquiera) |
| **Endpoint** | `PATCH /api/v1/actas/{id}` |
| **Entrada** | `{ votos_nulos: int (optional), votos_blancos: int (optional), detalle: [ { candidato_id, votos } ] (optional) }` |
| **Precondición** | Autenticado. Autorizado (OBSERVER: propia EN_DISPUTA; COORDINATOR: cualquier EN_DISPUTA). Acta EN_DISPUTA. |
| **Flujo principal** | 1. Validar permisos + estado. 2. Si OBSERVER: validar es registrador. 3. Actualizar datos. 4. Validar SUM nuevamente. 5. Cambiar `estado = CORREGIDA`. 6. AUDIT_LOG: `ACTA_CORREGIDA_{OBSERVER|COORDINATOR}`. 7. Crear NOTIFICACION: `{ coordinator_id, tipo: ACTA_LISTA_PARA_REVISION }` (si fue OBSERVER quien corrigió). 8. Retornar `200`. |
| **Flujo alterno** | Validación SUM fallida → `422`. Permisos insuficientes → `403`. |
| **Postcondición** | Acta en CORREGIDA. COORDINATOR puede revisar. |

| RF-ACTA-005 | Aprobar acta |
|---|---|
| **Descripción** | COORDINATOR/MASTER confirma datos correctos. Acta pasa a APROBADA (final antes de anulación). |
| **Actores** | COORDINATOR, MASTER |
| **Endpoint** | `PATCH /api/v1/actas/{id}/estado` |
| **Entrada** | `{ estado: "APROBADA" }` |
| **Precondición** | Autenticado. Acta ENVIADA o CORREGIDA. |
| **Flujo principal** | 1. Validar permiso + estado. 2. Cambiar `estado = APROBADA`. 3. Grabar `aprobada_por = user_id`, `aprobada_en = NOW()`. 4. AUDIT_LOG: `ACTA_APROBADA`. 5. Excluir de recálculos si dashboard ya corre. 6. Retornar `200`. |
| **Flujo alterno** | Acta no ENVIADA/CORREGIDA → `409 Conflict`. |
| **Postcondición** | Acta sellada. Solo MASTER puede anular. Incluida en dashboard/consolidación. |

| RF-ACTA-006 | Anular acta |
|---|---|
| **Descripción** | MASTER marca acta como ANULADA por fraude/irregularidad. Irreversible. Datos intactos en BD. Excluida de estadísticas. |
| **Actores** | MASTER |
| **Endpoint** | `PATCH /api/v1/actas/{id}/estado` |
| **Entrada** | `{ estado: "ANULADA", razon: "Fraude detectado" }` |
| **Precondición** | Autenticado como MASTER. Acta APROBADA. |
| **Flujo principal** | 1. Validar MASTER. 2. Validar acta APROBADA. 3. Cambiar `estado = ANULADA`. 4. Grabar `razon_anulacion`. 5. AUDIT_LOG: `ACTA_ANULADA`. 6. Crear NOTIFICACION: `{ coordinator_id, tipo: ACTA_ANULADA }`. 7. Excluir de dashboard consolidado. 8. Retornar `200`. |
| **Flujo alterno** | Acta no APROBADA → `409 Conflict { error: "Solo actas aprobadas pueden anularse" }`. |
| **Postcondición** | Acta permanentemente marcada ANULADA. No aparece en resultados públicos. Auditoría conserva todo. |
| **Reglas negocio** | • ANULADA es estado final. • Ningún rol puede revertir. • Datos e historial conservados (no delete). • Excluida de SUM de votos en consolidación. |

---

#### RF-DASH: Dashboard y Resultados (PLANEADO)

| RF-DASH-001 | Consolidado de resultados por cargo/cantón |
|---|---|
| **Descripción** | ADMIN/COORDINATOR/MASTER ven gráficos de votos consolidados, filtrados por estado (APROBADA), excluyendo ANULADA. |
| **Actores** | ADMIN, COORDINATOR, MASTER |
| **Endpoint** | `GET /api/v1/resultados/consolidado?cargo_id=...&canton_id=...` |
| **Entrada** | Query params: `cargo_id` (uuid, opcional), `canton_id` (uuid, opcional), `estado` (enum, default APROBADA). |
| **Precondición** | Autenticado como ADMIN/COORDINATOR/MASTER. |
| **Flujo principal** | 1. Query actas con estado = APROBADA. 2. Si cargo_id: filtrar. 3. Si canton_id: filtrar (JOIN jrv → parroquia → canton). 4. Groupby candidato + SUM votos. 5. Retornar `{ cargo_id, cargo_nombre, resultados: [ { candidato_id, nombre, partido, votos, porcentaje } ], total_votos }`. |
| **Flujo alterno** | Sin filtros → retorna consolidado global (todos los cargos, todos los cantones). |
| **Postcondición** | Dashboard renderiza gráficos (bar, pie, mapa). |
| **Reglas negocio** | • Excluye ANULADA. • Excluye EN_DISPUTA, CORREGIDA, BORRADOR. • Calcula porcentaje: `(votos / total) * 100`. • Realtime vía WebSocket. Si no disponible → polling cada 5s. |

---

#### RF-OFFLINE: Resiliencia Offline en Móvil (PLANEADO)

| RF-OFFLINE-001 | Guardar acta en IndexedDB si upload falla |
|---|---|
| **Descripción** | Cliente guarda foto + datos en IndexedDB. Reintenta upload cada 30s, máx 3 intentos. |
| **Actores** | OBSERVER (móvil) |
| **Endpoint** | N/A (cliente-side, IndexedDB) |
| **Entrada** | Evento: upload a S3 falla con error de conectividad (timeout, 503, 504, etc.). |
| **Precondición** | Aplicación móvil Ionic. Archivo en IndexedDB. |
| **Flujo principal** | 1. Cliente intercepta error de upload. 2. Guarda `{ jrv_id, cargo_id, blob, timestamp }` en IndexedDB. 3. Configura retry automático: `setInterval(retryUpload, 30000)`. 4. En cada retry: intenta POST a `/api/v1/evidencias`. 5. Si OK en reintentos → borra de IndexedDB. 6. Si 3 fallos → notifica usuario "Guardar manual: storage privado". |
| **Flujo alterno** | Usuario connectable → UI informa conexión perdida, retry automático en bkg. |
| **Postcondición** | Foto guardada localmente; sincroniza cuando red disponible. |
| **Reglas negocio** | • Timeout (conexión perdida) ≠ 422 (validación rechazada). Solo retry en timeout/5xx. • Max 3 reintentos antes de fallback manual. • IndexedDB puede contener ~10 MB de fotos. • Usuario puede descargar archivo manualmente en oficina luego. |

| RF-OFFLINE-002 | Fallback: almacenar foto en storage privado de dispositivo |
|---|---|
| **Descripción** | Si IndexedDB reintentos agotan, guardar foto en filesystem privado del app. Nombre: `JRV-{jrv_id}_{YYYYMMDD}_{HHmm}_{observer_id}.webp`. |
| **Actores** | OBSERVER (móvil, después de fallos) |
| **Endpoint** | N/A (Capacitor Filesystem) |
| **Entrada** | IndexedDB retry máx 3 agotado. |
| **Precondición** | App tiene permisos de filesystem (Capacitor Android/iOS). |
| **Flujo principal** | 1. Después 3 reintentos fallidos → notificar usuario "Upload imposible. Guardar localmente". 2. Si usuario acepta → escribir foto a `{APP_DATA_DIR}/JRV-{jrv_id}_{timestamp}_{observer_id}.webp`. 3. Guardar metadata: `{ jrv_id, cargo_id, timestamp, observer_id, local_path }` en IndexedDB (marcado como "local_only"). 4. Mostrar en UI "Fotos pendientes: 1". 5. En oficina: admin puede recuperar via `GET /api/v1/observer/fotos-offline?observer_id=...` (futuro). |
| **Flujo alterno** | Usuario rechaza guardar → pierde foto. |
| **Postcondición** | Foto segura en storage privado. No sincronizada hasta upload manual o próxima conectividad. |

---

### 3.2 Requisitos No Funcionales

#### Seguridad (RNF-SEC)

| RNF-SEC-001 | Autenticación JWT stateless |
|---|---|
| **Descripción** | Access token (15 min, memoria JS) + Refresh token opaco (8h, cookie httpOnly, BD). |
| **Medida** | • Access: nunca localStorage. • Refresh: cookie, `HttpOnly`, `Secure` (HTTPS prod), `SameSite=Strict`. • Algoritmo: HS256 (Nimbus JOSE+JWT). |
| **Validación** | Token inválido/expirado → `401 Unauthorized`. |

| RNF-SEC-002 | Control de sesión (revocación) |
|---|---|
| **Descripción** | Refresh tokens persistidos en PG; marcar `revocado = true` para logout global. |
| **Medida** | Cada `POST /refresh` valida `revocado` en BD. MASTER puede logout otros usuarios (futuro). |
| **Validación** | Token revocado → `401 Unauthorized`. |

| RNF-SEC-003 | RBAC en API (autorización) |
|---|---|
| **Descripción** | Spring Security + @PreAuthorize. Permisos granulares: `acta:crear`, `acta:aprobar`, `evidencia:subir`, etc. |
| **Medida** | Cada endpoint valida rol + permiso. Matriz en [Sección 2.3](#23-clases-de-usuario-y-matriz-de-permisos). |
| **Validación** | Rol insuficiente → `403 Forbidden`. |

| RNF-SEC-004 | RLS (Row Level Security) en PostgreSQL |
|---|---|
| **Descripción** | Segunda línea de defensa. OBSERVER solo ve/modifica filas donde `jrv_id IN (su asignación)`. |
| **Medida** | Políticas RLS en tablas: `acta`, `acta_detalle`, `evidencia`. COORDINATOR/ADMIN/MASTER sin restricción (control en capa API). |
| **Validación** | Query RLS -> 0 filas si usuario no autorizado. |

| RNF-SEC-005 | Locking pesimista para actas |
|---|---|
| **Descripción** | `SELECT ... FOR UPDATE` en (jrv_id, cargo_id) evita race condition de dos OBSERVERs registrando simultáneamente. |
| **Medida** | Al crear acta: `SELECT * FROM acta WHERE jrv_id = ? AND cargo_id = ? FOR UPDATE NOWAIT`. Si existe no-ANULADA → `409 Conflict`. |
| **Validación** | Segunda tentativa de registrar la misma acta → error o bloqueo. |

| RNF-SEC-006 | HTTPS en producción |
|---|---|
| **Descripción** | Todos los endpoints en prod (`api.brydyan.me`, `control.brydyan.me`) sobre HTTPS. Certificado LetsEncrypt renovación automática. |
| **Medida** | Railway + Cloudflare Pages gestionan TLS. Cliente en prod: rechazar `http://`. |
| **Validación** | Intento HTTP → redirect 301 a HTTPS. |

| RNF-SEC-007 | Hash de contraseña |
|---|---|
| **Descripción** | Contraseñas nunca plain text. Usar bcrypt con salt. |
| **Medida** | Spring Security BCryptPasswordEncoder (strength 10+). |
| **Validación** | SELECT password FROM usuario → texto hash, nunca plaintext. |

| RNF-SEC-008 | Auditoría completa |
|---|---|
| **Descripción** | Cada acción genera entrada en AUDIT_LOG: usuario, acción, acta/usuario/etc, timestamp, payload JSON. |
| **Medida** | AuditLogService intercepta: crear/cambiar estado/anular acta, crear usuario, etc. Logs persistidos en PG (no borrados jamás). |
| **Validación** | SELECT * FROM audit_log WHERE acta_id = ? → ver historial completo. |

#### Rendimiento (RNF-PERF)

| RNF-PERF-001 | Tamaño de imagen |
|---|---|
| **Descripción** | Fotos WebP comprimidas: objetivo ~350 KB, máx 10 MB. Si > 500 KB → notificar recomprimir. |
| **Medida** | Cliente: Canvas API → WebP calidad 0.82. Si resultado > 500 KB → UI sugiere 0.65. |
| **Validación** | Upload > 10 MB → `413 Payload Too Large`. |

| RNF-PERF-002 | Tiempo de respuesta API |
|---|---|
| **Descripción** | 95% de requests < 500 ms (p95). |
| **Medida** | Railway (1 réplica), HikariCP pool (12 conexiones def), índices en PG (jrv_id, cargo_id, usuario_id, estado). |
| **Validación** | Monitoring: New Relic / Datadog / CloudWatch. |

| RNF-PERF-003 | TTL tokens |
|---|---|
| **Descripción** | Access: 15 min (carga baja en sesiones). Refresh: 8h (duración electoral). |
| **Medida** | JWT claim `exp`. Refresh token BD con `expires_at`. |
| **Validación** | Token expirado → `401`. Cliente renueva automático via refresh (transparente a usuario). |

| RNF-PERF-004 | Presigned URL válida 15 min |
|---|---|
| **Descripción** | Descargas de fotos via URL presignada temporal (no long-lived token). |
| **Medida** | S3 (Cloudflare R2) genera URL con expiración. |
| **Validación** | URL > 15 min → `403 Forbidden` (expirada). |

#### Disponibilidad y Resiliencia (RNF-AVAIL)

| RNF-AVAIL-001 | Offline-first móvil |
|---|---|
| **Descripción** | Aplicación Ionic funciona sin red. Sync en background cuando disponible. |
| **Medida** | IndexedDB + Capacitor Storage. Retry automático. Notificaciones push (futuro). |
| **Validación** | Sin conexión → app sigue funcional (lectura local, edición). |

| RNF-AVAIL-002 | Reintentos automáticos en upload |
|---|---|
| **Descripción** | Si upload falla (timeout, 503, 504) → reintento cada 30s, máx 3. Luego fallback manual. |
| **Medida** | Cliente-side exponential backoff. AUDIT_LOG de intentos fallidos. |
| **Validación** | Simular fallo de S3 → app reintenta 3 veces, notifica usuario. |

| RNF-AVAIL-003 | Fallback a storage privado |
|---|---|
| **Descripción** | Después 3 reintentos en IndexedDB → guardar en filesystem privado del dispositivo. |
| **Medida** | Capacitor Filesystem (Android/iOS). Archivo nombrado con timestamp, JRV_id, observer_id. |
| **Validación** | Desconectar red, intentar upload → foto guardada localmente en `{APP_DATA}/JRV-*`. |

| RNF-AVAIL-004 | SLA backend |
|---|---|
| **Descripción** | Railway plan Pro, 1 réplica. |
| **Medida** | Despliegue con `railway.json` (builder dockerfile + `Dockerfile.native`); failover y redundancia geográfica (si contratado). |
| **Validación** | Monitoreo de disponibilidad. Alertas si < 99.5%. |

| RNF-AVAIL-005 | Backup/Recuperación de BD |
|---|---|
| **Descripción** | Neon: snapshots automáticos diarios y PITR (retención según plan). |
| **Medida** | Configurar backup policies. Test restore mensual. |
| **Validación** | Simulacro: restaurar BD de backup. |

#### Usabilidad (RNF-USAB)

| RNF-USAB-001 | Mobile-first (Ionic) |
|---|---|
| **Descripción** | UI diseñada para pantalla pequeña (dispositivos móviles). Desktop view es secondary. |
| **Medida** | Responsive design (Ionic components). Breakpoints: mobile (< 768px), tablet, desktop. |
| **Validación** | Usable en Samsung/Apple, 4-6 pulgadas. |

| RNF-USAB-002 | Menús dinámicos por RBAC |
|---|---|
| **Descripción** | Sidebar muestra solo opciones autorizadas. Sin rutas "ocultas" accesibles directamente. |
| **Medida** | Angular Route Guards + menús vía `/menus/me` API. |
| **Validación** | OBSERVER ve "Mis actas"; no ve "Gestión usuarios" (MASTER only). |

| RNF-USAB-003 | Notificaciones en tiempo real |
|---|---|
| **Descripción** | Cambios de estado (EN_DISPUTA, CORREGIDA, APROBADA) notificados al usuario. Toast o badge. |
| **Medida** | WebSocket o polling HTTP. |
| **Validación** | COORDINATOR marca acta EN_DISPUTA → OBSERVER ve notificación en < 2s. |

| RNF-USAB-004 | Mensajes de error descriptivos |
|---|---|
| **Descripción** | Validación fallida (422) retorna error legible: "Totales no coinciden: esperado 100, recibido 95". |
| **Medida** | API retorna `{ error, details, field }`. Frontend renderiza user-friendly. |
| **Validación** | Usuario ve "Error: Votos por candidatos + nulos + blancos no suman total". |

#### Mantenibilidad (RNF-MAINT)

| RNF-MAINT-001 | Versionado de DB con Liquibase |
|---|---|
| **Descripción** | Cada cambio de schema en archivo `.sql` con `id` y `author`. No cambios directos en BD. |
| **Medida** | `src/main/resources/database/changelog/db.changelog-master.yaml` incluye todos los `.sql`. CI/CD aplica automático. |
| **Validación** | `SELECT * FROM DATABASECHANGELOG` → ver historial de migraciones. |

| RNF-MAINT-002 | Arquitectura por capas |
|---|---|
| **Descripción** | Separación clara: controller (web), service (business logic), repository (data access), domain (entities). |
| **Medida** | Estructura Maven: `ec.twintailcs.electoral.auth.web` vs `.service` vs `.domain`. |
| **Validación** | Fácil localizar dónde cambiar lógica (service) vs endpoint (controller). |

| RNF-MAINT-003 | Código documentado |
|---|---|
| **Descripción** | Clases y métodos con Javadoc. Decisiones arquitectónicas en ADRs. |
| **Medida** | Javadoc en interfaces públicas. ADRs en `doc/adr/`. Comentarios solo para "por qué", no "qué". |
| **Validación** | `mvn javadoc:javadoc` genera sin errores. |

| RNF-MAINT-004 | Tests automatizados |
|---|---|
| **Descripción** | Unit tests (JUnit 5), integration tests (TestRestTemplate). Cobertura ≥ 70% en servicios. |
| **Medida** | `src/test/java/...` + `pom.xml` JUnit 5 + Mockito. CI corre tests antes de merge. |
| **Validación** | `mvn test` pasa al 100%. |

---

### 3.3 Requisitos de Interfaz

#### 3.3.1 Interfaces de Usuario (UI)

**Mockups disponibles en:** `documents/mockups/`

| Pantalla | Rol(es) | Descripción |
|----------|---------|-------------|
| Login | Público | Email + password. Redirect a dashboard según rol. |
| Dashboard Home | ADMIN, COORDINATOR, MASTER, OBSERVER | Estadísticas y menú rápido según permisos. |
| Mi JRVs (selector) | OBSERVER | Desplegable: seleccionar JRV de asignación. |
| Registrar Acta | OBSERVER | Formulario: cargo → candidatos → votos → totales. Captura foto (Canvas). |
| Revisión antes de enviar | OBSERVER | Confirmación: foto + datos. Botón "Enviar acta". |
| Mis Actas | OBSERVER | Lista filtrada por estado (ENVIADA, EN_DISPUTA, CORREGIDA, APROBADA). |
| Acta en Disputa | OBSERVER | Notificación + formulario de corrección (pre-relleno). |
| Lista de Actas | COORDINATOR, MASTER | Tabla: JRV, Cargo, Estado, Registrado por, Fecha. Filtros: estado, rango fecha, observer. |
| Detalle de Acta | COORDINATOR, MASTER | Desglose de votos, foto, metadata (quién, cuándo, eventos). |
| Gestión de Usuarios | MASTER | CRUD usuarios (crear, editar rol, deshabilitar). |
| Configuración Electoral | MASTER | Registrar cargos, partidos, candidatos, JRVs, asignaciones. |
| Reportes | COORDINATOR, MASTER | Exportar CSV/PDF de actas, estadísticas por canton. |

#### 3.3.2 Interfaces de Software (API)

**Especificación técnica completa en:** `documents/diagram/flujos/15-api-endpoints.md`

Endpoints principales:
- **Auth:** `/api/v1/auth/login`, `refresh`, `logout`, `me`, `register`
- **Actas:** `/api/v1/actas` (POST, GET, PATCH)
- **Evidencias:** `/api/v1/evidencias` (POST, GET, PATCH estado, DELETE)
- **Menus:** `/api/v1/menus/me`
- **Resultados:** `/api/v1/resultados/consolidado`
- **Geografía (futuro):** `/api/geo/provincias`, `/cantones`, `/jrvs`
- **Usuarios (futuro):** `/api/usuarios` (CRUD)
- **Notificaciones (futuro):** `/api/notificaciones`

**Convenciones HTTP:**
- `201 Created` — recurso creado
- `200 OK` — operación exitosa
- `422 Unprocessable Entity` — validación negocio falla (ej: totales incorrectos)
- `409 Conflict` — concurrencia (ej: acta ya existe)
- `401 Unauthorized` — token inválido/expirado
- `403 Forbidden` — rol insuficiente
- `503 Service Unavailable` — storage no disponible (reintentar)

**Autenticación:** Bearer JWT en header `Authorization: Bearer <token>`.

#### 3.3.3 Interfaces de Comunicación

- **HTTPS:** Todos los endpoints en prod. Certificado TLS.
- **WebSocket:** Dashboard subscriptions a tablas (`acta`, `audit_log`). Fallback: polling HTTP cada 5s.
- **S3 API:** Upload/descarga de fotos (presigned URLs, SigV4).

---

## 4. ESTADO DE IMPLEMENTACIÓN

| Módulo | Estado | Detalles | Referencia |
|--------|--------|---------|-----------|
| **Auth (Login, Refresh, Logout, Me, Register)** | ✅ Implementado | AuthController completamente funcional. JWT + refresh token en BD. | `src/main/java/...auth/web/AuthController.java` |
| **Menús dinámicos (RBAC)** | ✅ Implementado | MenuController retorna árbol según permisos del usuario. | `src/main/java/...auth/web/MenuController.java` |
| **Geografía (Provincia, Canton, Parroquia, JRV)** | ✅ Entidades, sin API | JPA entities `Provincia`, `Canton`, `Parroquia`, `Jrv` creadas. Repositories existentes. Endpoints REST (POST, GET) planeados. | `src/main/java/...geografia/domain/` |
| **Evidencia (Upload, Descarga, Aprobar)** | ✅ Implementado | EvidenciaController: POST upload (S3 + BD), GET presigned URL, PATCH estado. Reglas: 10MB, SHA-256, recomprimir si > 500KB. | `src/main/java/...evidencia/web/EvidenciaController.java` |
| **Acta (Registrar, Estados, Disputas, Correcciones, Anulación)** | ⏳ Planeado | Diseño completado. Máquina de estados en diagrama 07. Endpoints en diagrama 15. Entidades `Acta`, `ActaDetalle` no creadas aún. Business logic + locking pesimista pendiente. | `documents/diagram/flujos/07-estado-acta.md`, `15-api-endpoints.md` |
| **Dashboard (Resultados consolidados)** | ⏳ Planeado | Endpoint `/resultados/consolidado` diseñado. Realtime vía WebSocket (por definir) o polling HTTP. Mockups disponibles. | `documents/mockups/admin/dashboard-home.png` |
| **Offline-first (IndexedDB + fallback storage privado)** | ⏳ Planeado | Lógica cliente-side en Ionic/Capacitor. Retry automático 30s, máx 3. Fallback filesystem. | `documents/diagram/flujos/02-offline-sync.md` |
| **Configuración pre-electoral (Cargos, Partidos, Candidatos)** | ⏳ Planeado | Endpoints de registro + listado. MASTER only. Entidades planeadas. | `documents/diagram/flujos/13-configuracion-preelectoral.md` |
| **Notificaciones en tiempo real** | ⏳ Planeado | Tipos: `CORRECCION_REQUERIDA`, `ACTA_LISTA_PARA_REVISION`, `ACTA_ANULADA`. WebSocket (por definir) o polling HTTP. | `documents/diagram/05-domain-model.md` (tabla `NOTIFICACION`) |
| **AUDIT_LOG** | ⏳ Planeado | Registrar toda acción. Tabla en PG. Service layer preparado. | `documents/diagram/flujos/` (varias referencias) |

---

## 5. APÉNDICES

### A. Matriz de Permisos Completa por Endpoint

Ver [Sección 2.3](#23-clases-de-usuario-y-matriz-de-permisos) para tabla detallada.

### B. Máquina de Estados del Acta

```
BORRADOR (cliente)
    ↓
ENVIADA (API POST /actas)
    ├→ APROBADA (COORDINATOR POST /estado=APROBADA)
    ├→ EN_DISPUTA (COORDINATOR POST /estado=EN_DISPUTA)
    │   ├→ CORREGIDA (OBSERVER/COORDINATOR PATCH /actas/{id})
    │   │   ├→ APROBADA (COORDINATOR POST /estado=APROBADA)
    │   │   └→ EN_DISPUTA (COORDINATOR POST /estado=EN_DISPUTA, nueva disputa)
    │   └→ [feedback loop si COORDINATOR rechaza corrección]
    └→ [estado final si APROBADA]

APROBADA
    └→ ANULADA (MASTER POST /estado=ANULADA)

ANULADA [final, irreversible]
```

### C. Cargos Electorales y Niveles Geográficos

| Cargo | Nivel | Aplica en | Candidatos típicos |
|-------|-------|-----------|-------------------|
| **Prefecto/a** | PROVINCIA | Todas las JRVs | Candidato provincial |
| **Alcalde/sa** | CANTON | Todas las JRVs del cantón | Candidato cantonal |
| **Concejal Urbano** | CANTON | JRVs en zonas urbanas | Candidatos urbanos (lista) |
| **Concejal Rural** | CANTON | JRVs en zonas rurales | Candidatos rurales (lista) |
| **Vocal Junta Parroquial Rural** | PARROQUIA | JRVs en parroquias rurales | Candidatos locales |
| **Consejero/a CPCCS** | NACIONAL | Todas las JRVs | Candidatos nacionales |

**Cantones de Santa Elena:**
- Santa Elena (código 2401): ~200 JRVs
- La Libertad (código 2402): ~150 JRVs
- Salinas (código 2403): ~100 JRVs

### D. Glosario de Términos Técnicos

| Término | Definición |
|---------|-----------|
| **JRV** | Junta Receptora de Votos — recinto de votación. Identificada por `jrv_id`. Una JRV tiene múltiples cargos (via `JRV_CARGO`). |
| **Acta** | Registro de votos por cargo en una JRV. Identificada por `(jrv_id, cargo_id)` — única. Estados: BORRADOR, ENVIADA, EN_DISPUTA, CORREGIDA, APROBADA, ANULADA. |
| **ActaDetalle** | Desglose de votos por candidato. Tabla: `(acta_id, candidato_id, votos)`. |
| **Evidencia** | Foto WebP de acta. Almacenada en S3. Referenciada en `acta.storage_url`. |
| **RBAC** | Role-Based Access Control — permisos por rol (MASTER, COORDINATOR, ADMIN, OBSERVER). |
| **RLS** | Row Level Security — política en PostgreSQL que filtra filas según usuario (segunda línea de defensa). |
| **Locking pesimista** | `SELECT ... FOR UPDATE` — bloquea fila mientras se registra, evita race conditions. |
| **Presigned URL** | URL temporal (15 min) para descargar fotos sin reauthenticación. Generada por S3. |
| **Offline-first** | App móvil funciona sin red. Sincroniza en background cuando conecta. IndexedDB + Capacitor Storage. |
| **WebP** | Formato de imagen moderno, comprimido. Obligatorio. ~350 KB objetivo. |

### E. URLs de Desarrollo y Producción

**Desarrollo (local):**
- Frontend: `http://localhost:4200`
- Backend API: `http://localhost:8081`
- Swagger UI: `http://localhost:8081/swagger-ui.html`
- Postgres: `localhost:5432` (Docker)
- S3 (MinIO): `http://localhost:9000`

**Staging:**
- Sin puertos públicos. Acceso via Tailscale.

**Producción:**
- Frontend: `https://control.brydyan.me` (Cloudflare Pages)
- Backend API: `https://api.brydyan.me` (Railway)
- OpenAPI: `https://api.brydyan.me/v3/api-docs`

### F. Referencias de Seguridad

| Aspecto | Implementación |
|--------|-----------------|
| **JWT Algorithm** | HS256 (Nimbus JOSE+JWT) |
| **Secret Min Length** | 32 caracteres |
| **Access Token TTL** | 15 minutos |
| **Refresh Token TTL** | 8 horas |
| **Refresh Token Storage** | Cookie httpOnly, HTTPS only (prod), SameSite=Strict |
| **Password Hashing** | bcrypt (Spring Security, strength 10+) |
| **HTTPS** | Obligatorio en prod. Certificado LetsEncrypt. |
| **RLS Policies** | OBSERVER: registrada_por_id = current_user_id (aislamiento de actas; selección libre de JRV). COORDINATOR/ADMIN/MASTER: sin restricción. |
| **Audit Logging** | Toda acción en AUDIT_LOG (creación, modificación, aprobación, anulación). |

### G. Stack Técnico Resumido

```
Frontend:    Angular 21 + PrimeNG 21       (TypeScript 5.9, RxJS, Tailwind CSS 4)
Backend:     Quarkus 3.40 (Java 25)        (REST, Security, Hibernate ORM + Panache)
Database:    PostgreSQL 16+                (Neon en prod, Docker en dev)
Storage:     Cloudflare R2                  (S3-compatible, .webp)
Realtime:    WebSocket o polling HTTP       (mecanismo por definir)
Auth:        JWT HS256 (Nimbus JOSE+JWT)
ORM:         Hibernate ORM + Panache
DB Migration: Liquibase 4.x
Compression: WebP (Canvas API, quality 0.65-0.82)
Deployment:  Cloudflare Pages (FE), Railway (BE), Neon (DB), Cloudflare R2 (Storage)
Monitoring:  Datadog / CloudWatch / New Relic (futuro)
```

---

## Historial de Cambios

| Versión | Fecha | Autor | Cambios |
|---------|-------|-------|---------|
| 1.0 | 2026-07-18 | Control Electoral Team | SRS inicial. IEEE 830. Cubre auth, menus, geo, evidencias. Actas/Dashboard planeado. |

---

**Documento clasificado:** INTERNAL (no confidencial, uso interno equipo dev + stakeholders)

**Próximas revisiones:**
- Tras implementación de Actas: actualizar RF-ACTA con detalles reales.
- Tras go-live de producción: agregar sección de "Operaciones en Vivo".

---

