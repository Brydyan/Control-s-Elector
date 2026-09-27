# Design — T-BE-21: Acta Detalle Completo DTO (Votos, Evidencia, Motivo) & GET Observer

---

## D1 — DTO de Detalle Específico vs Sobrecargar el DTO de Listado

**Decisión**:
Mantener `ActaResponseDto` plano exclusivamente para la consulta masiva y paginada `GET /api/v1/coordinator/actas`. Crear `ActaDetalleCompletoDto` para las consultas unitarias (`GET /api/v1/coordinator/actas/{id}` y `GET /api/v1/observer/actas/{id}`).
El service ejecutará `actaDetalleRepository.findByActaId(actaId)` únicamente en llamadas unitarias.

**Alternativa descartada — Incrustar la lista de candidatos en `ActaResponseDto`**:
Descartada porque causaría un problema N+1 severo o un `JOIN FETCH` de 20 a 50 candidatos por cada una de las 20 actas del listado paginado. Multiplicaría por 30 el tamaño de cada página JSON en la red sin que el usuario haya hecho clic en ver el detalle.

---

## D2 — Enlace de Evidencia Fotográfica y Presigned URL en Memoria

**Decisión**:
El caso de uso `ConsultarActaDetalleUseCase` inyecta `EvidenciaRepository` y `EvidenciaService`.
1. Busca la evidencia más reciente asociada al acta mediante `evidenciaRepository.findByJrvIdAndCargoId(acta.getJrv().getId(), acta.getCargo().getId())`.
2. Si existe y se encuentra activa, invoca `evidenciaService.presignDownload(evidencia.getPublicId())` para generar una URL prefirmada temporal (expiración de 15 minutos en S3/RustFS).
3. Asigna `urlEvidencia` y `evidenciaPublicId` en el DTO de respuesta. Si no hay evidencia, ambos quedan en `null`.

**Alternativa descartada — Forzar al frontend a llamar `GET /api/v1/evidencias/...` por separado**:
Descartada porque el frontend no conoce el `evidenciaId` del acta. Obligaría a crear un endpoint intermedio de búsqueda de evidencias y aumentaría la latencia con dos peticiones HTTP secuenciales en redes móviles.

---

## D3 — Extracción Forense del Motivo de Disputa

**Decisión**:
Si `acta.getEstado() == EstadoActa.EN_DISPUTA`:
Se consulta el último registro en `audit_log` para `entidad = 'acta'` y `entidad_id = actaId` con acción `'ACTA_EN_DISPUTA'`.
El prefijo `"Motivo: "` se retira para exponer el texto limpio en `motivoDisputa`. Si no hay registro o no está en disputa, el campo viaja `null`.

**Alternativa descartada — Crear una migración Liquibase para añadir la columna `motivo_disputa` en la tabla `acta`**:
Descartada porque el motivo de disputa es un dato transicional que ya está registrado de forma inmutable y con auditoría forense (usuario, rol, IP y timestamp) en la tabla `audit_log` gracias a `MarcarEnDisputaActaUseCase`. Evita migraciones de esquema innecesarias.

---

## D4 — Autorización y Restricción de Autoría en ObserverController

**Decisión**:
En `ObserverController`:
```java
@PreAuthorize("hasAuthority('acta:upload')")
@GetMapping("/actas/{actaId}")
public ResponseEntity<ActaDetalleCompletoDto> obtenerActa(@PathVariable Long actaId)
```
El servicio verifica que el usuario autenticado (`CurrentUser.id()`) coincida con el autor del acta (`acta.getRegistradaPor()`). Si no coincide y el usuario no tiene rol `MASTER`, lanza `AccessDeniedException` (HTTP 403 Forbidden).

**Alternativa descartada — Permitir a cualquier veedor consultar el acta con solo conocer el ID numérico**:
Descartada porque violaría la privacidad operativa de las mesas electorales, permitiendo a un veedor de un recinto explorar datos de escrutinio de recintos ajenos sin autorización.
