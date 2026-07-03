---
name: control-electoral-review
description: >
  Revisión de código arquitectónica y de seguridad para Control Electoral (backend Java 21/Spring Boot
  en backend/electoral-api, package ec.twintailcs.electoral; frontend Angular 17+/Ionic en frontend/app/src/app).
  Verifica RBAC (MASTER/COORDINATOR/ADMIN/OBSERVER), locking pesimista jrv_id+cargo_id, invariante de suma
  de votos, máquina de estados de acta, JWT en memoria, AuditLog, y pipeline WebP.
  Trigger: "revisa este código", "review this PR", "check RBAC", "audita el backend/frontend", revisión de
  diff/PR en este proyecto.
license: Apache-2.0
metadata:
  author: gentleman-programming
  version: "1.0"
---

## Cuándo usar

- Al revisar un PR o diff que toca `backend/electoral-api/**` o `frontend/app/src/app/**`.
- Al pedir auditoría de seguridad, RBAC, o cumplimiento de las reglas de `CLAUDE.md`.
- NO usar para nitpicks de docstrings o conteo de tests — eso no bloquea.

## Tono y formato de hallazgos

Arquitecto senior backend/full-stack, es-ES, directo y educativo. Estilo "request changes" — no diplomático de más.

Cada hallazgo, en este orden:

```
**Problema**: <qué está mal, con archivo:línea>
**Por qué importa**: <consecuencia concreta — no genérica>
**Fix**: <cómo corregir, con código si aplica>
**Patrón**: <nombre del patrón/herramienta que resuelve esta clase de problema>
```

Excluir del scope de revisión: `target/`, `node_modules/`, `.angular/`, `dist/`, `android/app/build/`,
`*.lock`, artefactos generados. No bloquear por falta de docstrings o "pocos tests" — solo por tests
que no prueban nada (`assertTrue(true)` equivalentes) o que faltan casos críticos listados abajo.

## Reglas de rol — no negociables (fuente: CLAUDE.md)

| Rol | Permitido |
|-----|-----------|
| MASTER | Todo. Único que revoca sesiones y anula actas APROBADA. |
| COORDINATOR | Disputas/correcciones/aprobaciones en todas las JRVs. |
| ADMIN | Solo lectura — dashboard. Nunca escribe. |
| OBSERVER | Registra/corrige actas solo en sus JRVs asignadas (`observer_jrv_asignacion`). |

Cualquier endpoint mutante sin guard de rol exacto a esta tabla es hallazgo crítico.
`ADMIN` en cualquier guard de escritura es hallazgo crítico.
Guards de Angular son solo UX — si la autorización real no está en el backend (`@PreAuthorize` o
equivalente), es hallazgo crítico, no da igual que el frontend "lo oculte".

## 1. Entidades JPA — `backend/electoral-api/.../domain/**`

- **Modelo anémico**: entidades con solo getters/setters y sin comportamiento cuando la lógica de
  negocio (ej. validar suma de votos) claramente pertenece a la entidad o value object.
  Patrón: Rich Domain Model.
- **Invariante de acta no validada en el lugar correcto**: `SUM(acta_detalle.votos) + votos_nulos +
  votos_blancos == total_sufragantes` debe validarse antes de persistir, no confiar solo en el cliente.
  Patrón: Always-valid domain model / invariant enforcement.
- **Entidad JPA expuesta directo como respuesta de API** en vez de DTO — filtra `passwordHash`,
  relaciones lazy, y acopla el contrato HTTP al esquema de BD.
  Fix: mapear a DTO explícito. Patrón: DTO pattern.
- **Roles/estados como `String` crudo** en vez de `enum` (`Rol`, `EstadoActa`) — typos silenciosos
  pasan compilación. Fix: usar enum como fuente única de verdad.

## 2. Servicios de negocio — `.../service/**`

- **`ActaService` sin `@Transactional`** en transición de estado multi-paso (ej. `corregir`, `aprobar`)
  — riesgo de escritura parcial si falla a mitad.
  Por qué importa: dato queda en estado inconsistente, no reproducible ni auditado.
  Patrón: Unit of Work / `@Transactional`.
- **Locking `SELECT FOR UPDATE` incorrecto o ausente** en cualquier ruta que toca Acta por
  `jrv_id + cargo_id` — flag lecturas-luego-escritura sin lock (race entre dos OBSERVER en la misma JRV+cargo).
  Patrón: Pessimistic locking.
- **Caso "no encontrado" o "ya registrado" sin excepción tipada** (`JrvCargoAlreadyRegisteredException`,
  etc.) — dejar que `null`/`Optional.get()` explote no es manejo de error.
- **Mutación sin llamar `AuditLogService`**: cualquier servicio que escribe/cambia estado debe invocar
  `AuditLogService.registrar(...)`. Flujo obligatorio es `API → AuditLogService → JPA → PG`, nunca
  escribir `AUDIT_LOG` directo desde otro repositorio.
- **Reintento de subida de imagen no idempotente** — reintentos de red no deben duplicar Acta ni
  imagen. Patrón: Idempotent consumer.

## 3. Seguridad / JWT — dondequiera que viva `security/**` o `JwtService`/`RefreshTokenService`

- **Access token persistido más allá de memoria** — en Angular, cualquier `localStorage`/`sessionStorage`
  para el access token es hallazgo crítico (regla explícita de CLAUDE.md: 15 min, solo memoria JS).
- **Refresh token sin rotación/revocación correcta** — reusar el mismo refresh token permite replay.
  Fix: invalidar el refresh token viejo al usarlo. Patrón: Refresh Token Rotation.
- **Revocación de sesión no restringida a MASTER en el backend** — verificar `@PreAuthorize` o guard
  explícito en `DELETE /api/auth/sessions/{usuarioId}`, no solo ocultar el botón en UI.
- **Hash de password no vía `PasswordEncoder`/bcrypt** — cualquier MD5/SHA1/comparación `==` de
  password es hallazgo crítico (timing attack + hash roto).
  Patrón: Timing-safe comparison, `BCryptPasswordEncoder`.
- **Login sin rate limiting** — falta de protección brute-force en `/api/auth/login`.

## 4. RBAC / autorización — controllers + Angular guards

- Todo endpoint mutante debe tener `@PreAuthorize` (o guard method-level equivalente) que matchee
  EXACTO la tabla de roles de arriba: `POST /api/actas` → solo OBSERVER; `DELETE /api/actas/{id}`
  (anular) → solo MASTER; endpoints de coordinación → COORDINATOR + MASTER.
- Cualquier discrepancia entre lo que el guard de Angular asume permitido y lo que el backend
  realmente autoriza es hallazgo crítico — el guard de frontend nunca es el límite de seguridad real.
- `ADMIN` apareciendo en cualquier guard de escritura (backend o frontend) es hallazgo crítico.

## 5. REST Controllers — `.../controller/**` (`@RestController`)

- **Lógica de negocio en el controller** — debe delegar a un service. Controller sin lógica testeable
  fuera del contexto HTTP es antipatrón aquí.
- **DTO de entrada sin validación** — todo campo de usuario necesita anotaciones `jakarta.validation`
  (`@NotNull`, `@Min`, etc.). Sin esto, dato inválido llega a la capa de negocio o BD.
- **Endpoint público sin Swagger/OpenAPI** (`@Operation`, `@ApiResponse`) — falta documentación de
  contrato.
- **Upload de imagen de acta sin validar MIME type / tamaño / formato WebP** — usar `Multer`-equivalente
  Spring (`MultipartFile` + validación manual) con `fileFilter`+`limits` equivalente.

## 6. DTOs

- Campo de request sin validador → superficie de inyección/dato corrupto.
- DTO de respuesta que expone `passwordHash` u otro campo interno sensible.
- Mismo DTO usado para request y response — separar (`CreateUsuarioDto` vs `UsuarioResponseDto`).

## 7. Storage (Supabase Object Storage / `StorageService`)

- Llamadas de upload sin timeout explícito.
- Sin retry/backoff para fallos transitorios.
- Nombre de archivo no forzado en servidor: debe ser
  `JRV-{jrv_id}_{YYYYMMDD}_{HHmm}_{observer_id}.webp` — validar ahí también, no confiar solo en cliente.
- Fallo de upload no registrado en `AuditLog` (regla explícita T-BE-14).
- Si se usan URLs prefirmadas: verificar expiración configurada y razonable.

## 8. Concurrencia / locking

- Cualquier ruta que toca Acta por `jrv_id + cargo_id` sin locking pesimista o equivalente — patrón
  read-then-write ingenuo es carrera real con observadores concurrentes en campo.

## 9. Observabilidad / logging

- PII en logs (email, nombre, cédula) en texto plano — solo loguear IDs (UUID).
- Logs por interpolación de string en vez de structured logging (key-value).

## 10. Angular — guards / interceptors (`core/guards/**`, `core/interceptors/**`)

- `auth.guard` y `role.guard` deben chequear autenticación Y rol exacto según la tabla de rutas de
  CLAUDE.md (`/observer`→OBSERVER, `/coordinator`→COORDINATOR|MASTER, `/master`→MASTER,
  `/dashboard`→ADMIN|MASTER|COORDINATOR).
- `jwt.interceptor` adjuntando `Authorization: Bearer` a requests que NO son de la API (ej. CDN, storage
  público) — fuga de token.
- `refresh.interceptor` sin protección contra loop infinito de refresh en 401 repetido.

## 11. Angular — sync offline (`features/observer/**`, IndexedDB/Capacitor)

- Fallo parcial de sync que pierde datos — debe reintentar sin descartar acta.
- IndexedDB/Private Storage limpiado ANTES de confirmar ack del servidor — riesgo de pérdida si el ack
  nunca llega.
- Falla repetida que descarta silenciosamente una acta sin indicador visual al observer.

## 12. Angular — captura/compresión de imagen

- Calidad WebP inicial debe ser `0.82`, recomprimir a `0.65` solo si resultado > 500KB.
- Dimensión máxima 1920×1080 manteniendo aspect ratio — flag si se deforma o no se limita.

## 13. Tests (`*Test.java`, `*.spec.ts`)

- Test sin aserciones reales (equivalente a `assertTrue(true)`) — falsa confianza.
- Falta cobertura de casos críticos explícitos (T-BE-15):
  - Locking pesimista con dos requests simultáneos a la misma JRV+cargo.
  - Validación de suma de votos incorrecta → debe fallar 400.
  - RLS/scoping — OBSERVER no debe ver actas de JRV no asignada.
  - Flujo completo de máquina de estados: BORRADOR→ENVIADA→EN_DISPUTA→CORREGIDA→APROBADA.
- Tests de repositorio/integración que mockean la BD en vez de usar Testcontainers con Postgres real
  para código sensible a locking/concurrencia — mock pasa, prod con carrera real falla.

## Resumen de salida esperado

1. Resumen ejecutivo corto (1-3 líneas) del estado del PR/diff.
2. Lista de hallazgos ordenados por severidad (crítico primero), cada uno con el formato
   Problema/Por qué importa/Fix/Patrón.
3. Si no hay hallazgos bloqueantes, decirlo explícito — no inventar nitpicks para llenar espacio.
