# Specification — T-BE-21: Acta Detalle Completo DTO (Votos, Evidencia, Motivo) & GET Observer

## Requirement: Consulta de Detalle de Acta para Coordinación
El sistema debe proveer al rol COORDINATOR o MASTER el detalle completo de un acta con los votos por candidato, la foto de evidencia prefirmada y el motivo de disputa si aplica.

### Scenario: Coordinador consulta acta con votos y evidencia fotográfica
- **GIVEN** un acta ID `10` en estado `ENVIADA` con 3 candidatos que recibieron votos (100, 50, 25)
- **AND** existe una evidencia física asociada en S3
- **WHEN** un usuario con rol COORDINATOR realiza `GET /api/v1/coordinator/actas/10`
- **THEN** la respuesta es HTTP 200 con `ActaDetalleCompletoDto`
- **AND** `detalles` contiene los 3 candidatos con sus votos correspondientes
- **AND** `urlEvidencia` contiene una URL prefirmada HTTPS no nula
- **AND** `totalVotosCandidatos` es `175`.

### Scenario: Coordinador consulta acta en disputa
- **GIVEN** un acta ID `12` en estado `EN_DISPUTA` con motivo *"Falta firma de presidente de mesa"*
- **WHEN** el coordinador consulta `GET /api/v1/coordinator/actas/12`
- **THEN** `motivoDisputa` en el DTO contiene *"Falta firma de presidente de mesa"*.

---

## Requirement: Consulta de Acta para el Veedor (OBSERVER)
El sistema debe permitir a un usuario con rol OBSERVER obtener el detalle de un acta para su subsanación en campo, restringiendo el acceso exclusivamente a sus propias actas.

### Scenario: Veedor consulta su propia acta en disputa
- **GIVEN** un veedor con ID `5` autenticado con rol OBSERVER
- **AND** el acta `20` fue registrada por el veedor `5` y se encuentra en estado `EN_DISPUTA`
- **WHEN** el veedor realiza `GET /api/v1/observer/actas/20`
- **THEN** la respuesta es HTTP 200 con `ActaDetalleCompletoDto`
- **AND** contiene los votos originales y el motivo de la disputa.

### Scenario: Veedor intenta consultar acta de otro veedor
- **GIVEN** un veedor con ID `5` autenticado
- **AND** el acta `30` fue registrada por el veedor con ID `99`
- **WHEN** el veedor `5` realiza `GET /api/v1/observer/actas/30`
- **THEN** la respuesta es HTTP 403 Forbidden
- **AND** no se exponen los datos del acta.

---

## Requirement: Preservación de Rendimiento en Listado Masivo
El listado paginado general no debe verse degradado por la consulta de colecciones de candidatos.

### Scenario: Listado paginado de actas conserva DTO ligero
- **WHEN** el coordinador realiza `GET /api/v1/coordinator/actas?page=0&size=20`
- **THEN** cada elemento devuelto en `data` es de tipo `ActaResponseDto` (plano)
- **AND** no se ejecutan consultas N+1 sobre `acta_detalle`.
