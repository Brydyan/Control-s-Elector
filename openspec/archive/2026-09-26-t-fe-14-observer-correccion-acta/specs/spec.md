# Specification — T-FE-14: Corrección de Acta Disputada en Campo (OBSERVER)

## Requirement: Carga de Acta Disputada y Verificación de Estado
El sistema debe permitir a un usuario con rol OBSERVER o MASTER ingresar a la pantalla de corrección únicamente si el acta se encuentra en estado EN_DISPUTA.

### Scenario: Acceso a acta en disputa
- **GIVEN** que el veedor está autenticado con rol OBSERVER
- **AND** existe un acta con ID `42` en estado `EN_DISPUTA` con motivo *"Discrepancia de 10 votos en lista 5"*
- **WHEN** el veedor navega a `/observer/acta/42/corregir`
- **THEN** la pantalla renderiza el banner de advertencia naranja con el texto *"Discrepancia de 10 votos en lista 5"*
- **AND** precarga los votos de cada candidato y votos blancos/nulos registrados originalmente.

### Scenario: Intento de acceso a acta en estado no disputado
- **GIVEN** que el veedor navega a `/observer/acta/10/corregir`
- **AND** el acta `10` se encuentra en estado `APROBADA`
- **WHEN** se completa la carga inicial
- **THEN** el sistema redirige al veedor a `/observer/mis-jrvs`
- **AND** muestra una alerta indicando que el acta no admite corrección.

---

## Requirement: Validación de Checksum Dinámico
El sistema debe validar en tiempo real que los votos corregidos sumen exactamente el total de sufragantes antes de permitir el reenvío.

### Scenario: Descuadre en corrección bloquea el envío
- **GIVEN** que el veedor modifica el voto del Candidato A a `150`
- **AND** la suma total de votos es `360` mientras que `totalSufragantes` es `350`
- **WHEN** el veedor observa el pie de página
- **THEN** la tarjeta de checksum muestra estado inválido en color rojo con la diferencia `+10`
- **AND** el botón *"Enviar Corrección"* se encuentra deshabilitado.

### Scenario: Cuadre exacto habilita el envío
- **GIVEN** que el veedor ajusta los votos nulos a `10`
- **AND** la suma de todos los candidatos más blancos y nulos es exactamente `350`
- **WHEN** el valor se registra en el input
- **THEN** la tarjeta de checksum cambia a verde con el mensaje *"Checksum Válido"*
- **AND** el botón *"Enviar Corrección"* pasa a estar habilitado.

---

## Requirement: Transmisión y Cambio de Estado
Al presionar el botón de envío, el sistema debe transmitir la carga útil al endpoint REST oficial y reflejar la transición de estado.

### Scenario: Envío exitoso de corrección
- **GIVEN** que el formulario es válido y el checksum cuadra
- **WHEN** el veedor pulsa *"Enviar Corrección"*
- **THEN** se emite una petición `PUT /api/v1/observer/actas/42/corregir` con el `ActaRequestDto` actualizado
- **AND** al recibir respuesta exitosa (200 OK), el estado pasa a `CORREGIDA`
- **AND** el veedor es redirigido a `/observer/mis-jrvs` con notificación de éxito.
