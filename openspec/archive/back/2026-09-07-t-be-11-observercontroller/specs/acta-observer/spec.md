# Spec — API del OBSERVER (retrospectivo)

Dominio: `acta-observer`. Change: `2026-09-07-t-be-11-observercontroller`.
Autoridad: `ObserverController`, no el task doc.

---

## RF-OBS-API-01 — Las rutas reales son las que están en el código

| Operación | Ruta real | Lo que dicen los documentos |
|---|---|---|
| Registrar acta | `POST /api/v1/observer/actas` | `POST /api/actas` |
| Corregir acta | `PUT /api/v1/observer/actas/{id}/corregir` | `PATCH /api/actas/{actaId}` |
| Mis actas | `GET /api/v1/observer/mis-actas` | `GET /api/observer/mis-actas` |
| Correcciones pendientes | `GET /api/v1/observer/correcciones-pendientes` | igual, sin `/v1` |
| JRVs | `GET /api/v1/observer/jrvs` | — |
| Cargos de una JRV | `GET /api/v1/observer/jrv/{jrvId}/cargos` | `GET /api/jrv/{jrvId}/cargos` |
| Subir imagen | `POST /api/v1/observer/actas/{id}/imagen` | — |

`backend.md:177-181` y `T-BE-11.md` usan las de la derecha. **El frontend se va a escribir
contra estos documentos** (T-FE-04 … T-FE-07): corregirlos es parte del cierre, no un extra.

---

## RF-OBS-API-02 — Sólo quien tiene `acta:upload` registra actas

GIVEN un usuario sin el permiso `acta:upload`
WHEN llama `POST /api/v1/observer/actas`
THEN recibe `403`

`CLAUDE.md`: «Solo `OBSERVER` registra actas». El control es por permiso, no por nombre de
rol — el seed de `009-acta-observer-permisos.sql` concede `acta:upload` a OBSERVER.

**Mutación**: quitar `@PreAuthorize("@authz.has('acta:upload')")` de `registrarActa`.
Debe caer el test de COORDINATOR o ADMIN → 403.

---

## RF-OBS-API-03 — `mis-actas` devuelve sólo las del usuario autenticado

GIVEN dos OBSERVERs con actas
WHEN uno consulta `GET /api/v1/observer/mis-actas`
THEN recibe únicamente las suyas

El filtro es `findWithFilters(estado, null, null, observerId)`, con `observerId` resuelto del
JWT vía `usuarioRepository.findByPublicId(currentUser.id())`.

**Mutación**: pasar `null` como `observerId`.
Debe caer el test de aislamiento entre OBSERVERs. Sin él, cada veedor vería las actas de
toda la provincia.

---

## RF-OBS-API-04 — `correcciones-pendientes` son las EN_DISPUTA propias

GIVEN un OBSERVER con actas en varios estados
THEN `correcciones-pendientes` devuelve sólo las suyas en EN_DISPUTA

**Mutación**: cambiar `EstadoActa.EN_DISPUTA` por `null` en la llamada.
Debe caer el test correspondiente.

---

## RF-OBS-API-05 — Un OBSERVER sólo corrige sus propias actas

GIVEN un acta EN_DISPUTA registrada por otro OBSERVER
WHEN se intenta corregir
THEN se rechaza

`ObserverController.corregirActa` pasa `esCoordinator = false` a `ActaService.corregir`, que
compara `registradaPor` con el usuario.

**Mutación**: cambiar ese `false` por `true`.
Debe caer el test de corrección ajena. Es un cambio de un literal, sin error de compilación,
que convertiría a cualquier OBSERVER en corrector universal. Ver
`t-be-07-acta-ciclo-vida` RF-CIC-03.

---

## RF-OBS-API-06 — `jrv/{id}/cargos` dice qué falta registrar en esa mesa

GIVEN una JRV con cargos asignados y algunas actas ya registradas
THEN cada cargo aparece como registrado (con el estado y el id de su acta) o sin registrar

GIVEN un cargo cuya única acta está ANULADA
THEN aparece **sin registrar**: se puede volver a registrar

Es lo que decide qué ve el veedor al abrir una mesa. Si un cargo ya registrado apareciera
como pendiente, intentaría registrarlo y chocaría con la unicidad de `jrv_id + cargo_id`.

**Mutación**: quitar el filtro `.filter(a -> a.getEstado() != EstadoActa.ANULADA)`.
Debe caer un test que anule un acta y compruebe que su cargo vuelve a estar disponible.

---

## RF-OBS-API-07 — El resumen de `GET /jrvs` es global, no por usuario

GIVEN una JRV donde otro OBSERVER ya registró 2 de 6 cargos
WHEN cualquier OBSERVER lista las JRVs
THEN esa JRV muestra 2 registrados y 4 pendientes

El conteo es **global por JRV**, no filtrado por quien pregunta: es coherente con la decisión
de que la JRV se elige libremente — dos veedores en la misma mesa tienen que ver el mismo
estado para no duplicar trabajo.

Está bien así, y es exactamente la clase de cosa que alguien «arregla» a filtrado por usuario
en seis meses. Queda escrito aquí y en el Javadoc del DTO.

---

## RF-OBS-API-08 — `GET /jrvs` responde en tiempo usable desde un móvil

GIVEN las ~450 JRVs de la provincia (Santa Elena 200 + La Libertad 150 + Salinas 100)
WHEN un OBSERVER abre la aplicación
THEN la lista llega en un tiempo razonable en una red móvil

**Hoy el endpoint hace N+1 consultas.** `toJrvConEstadoDto` se ejecuta por cada JRV y dentro
llama a `jrvCargoRepository.findByJrvIdAndActivo` y a `actaRepository.findByJrvId`: ~900
consultas por petición, más todas las actas de cada mesa traídas a memoria para contar cargos
distintos.

En desarrollo, con 15 JRVs de seed, son 30 consultas y no se nota. Es §4.5 del playbook: el
lado permisivo es el de desarrollo, y el problema aparece con datos de producción.

**Requisito de medición, no de opinión**: cargar ~450 JRVs con actas y medir. La decisión de
corregirlo o no se toma con ese número delante, no con una sospecha.

---

## RF-OBS-API-09 — La subida de imagen del acta existe

GIVEN un OBSERVER y su acta
WHEN sube la foto
THEN se persiste y el acta queda con su referencia

Hoy `POST /api/v1/observer/actas/{actaId}/imagen` devuelve `501 NOT_IMPLEMENTED`.
Se implementa en `t-be-14-storage-acta`. Queda registrado aquí como parte del contrato
incompleto de este controlador.

---

## RNF-OBS-API-01 — El controlador devuelve DTOs, no entidades

GIVEN cualquier respuesta de `ObserverController`
THEN devuelve `ActaResponseDto`, `JrvConEstadoDto` o `CargoConEstadoDto`

`ObserverController` lo cumple. `CoordinatorController` **no**: devuelve
`ResponseEntity<Acta>`, la entidad JPA cruda, con sus relaciones perezosas.

Dos criterios en el mismo dominio (§4.2). Se resuelve en `t-be-12-cierre-coordinacion`.
