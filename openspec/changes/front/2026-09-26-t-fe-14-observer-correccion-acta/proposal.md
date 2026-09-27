# Proposal — T-FE-14: Corrección de Acta Disputada en Campo (OBSERVER)

## Qué se construye
Pantalla operativa móvil para el Veedor (`OBSERVER`) que permite subsanar inconsistencias notificadas por la mesa de coordinación electoral, correspondiente al mockup [`OBSERVER/07-correccion-solicitada.html`](file:///home/andy/Escritorio/PROYECTOS/CONTROLs/Control-s-Elector/documents/mockups/OBSERVER/07-correccion-solicitada.html):
1. **Ruta y Guard de Acceso**:
   - `/observer/acta/:id/corregir` con `authGuard` y `roleGuard` (`OBSERVER`, `MASTER`).
   - Verificación de que el acta se encuentre en estado `EN_DISPUTA`.
2. **Visualización de la Disputa y Evidencia**:
   - Banner de advertencia con motivo formal de inconsistencia registrado por el coordinador (`motivoDisputa`) y nombre del coordinador responsable.
   - Visor de la foto original cargada previamente con zoom básico para cotejar los números físicos del acta.
3. **Reingreso de Votos y Checksum Dinámico**:
   - Formulario reactivo pre-poblado con los valores digitados inicialmente, destacando visualmente los campos modificados.
   - Cálculo en tiempo real de la invariante electoral: $\sum \text{votosCandidatos} + \text{blancos} + \text{nulos} = \text{totalSufragantes}$.
   - Tarjeta reactiva de checksum con estado visual verde (cuadrado) o rojo con diferencia numérica explícita.
4. **Tratamiento de Imagen de Respaldo**:
   - Selector "Conservar foto original" o "Tomar nueva foto" (integrado con compresión WebP en memoria vía Canvas API si se toma nueva captura).
5. **Transmisión de Subsanación**:
   - Envío a través de `PUT /api/v1/observer/actas/{id}/corregir`.
   - Transición del estado del acta a `CORREGIDA`.
   - Redirección con feedback de confirmación a `/observer/mis-jrvs`.

## Por qué
- En el flujo electoral, las actas marcadas con discrepancias numéricas o borrones por el coordinador requieren subsanación urgente en territorio antes del cierre de recepción del CNE.
- El backend ya cuenta con el endpoint transaccional probado `PUT /api/v1/observer/actas/{id}/corregir` y el DTO `ActaRequestDto`, pero el frontend no disponía de la pantalla ni ruta para que el veedor ejecute esta subsanación en campo.

## Alcance

| Elemento | Incluye | No incluye |
|---|---|---|
| **Vistas** | `CorreccionActaPage` (`/observer/acta/:id/corregir`) | Aprobación directa o anulación de acta |
| **Componentes** | Banner de motivo, visor de evidencia previa, inputs de votos, tarjeta de checksum, selector de foto | Modificación de metadatos fijos (JRV, recinto, dignidad) |
| **Servicios** | `ObserverService.corregirActa(id, payload)` y `ObserverService.getActa(id)` | Servicios de administración de usuarios |
| **Validaciones** | Checksum matemático en tiempo real, cota de sufragantes (máx 500, aviso > 350) | Algoritmos de adjudicación de escaños |

## Criterios de Aceptación

- [ ] La ruta `/observer/acta/:id/corregir` carga los detalles del acta y valida que su estado sea `EN_DISPUTA`; de lo contrario, redirige con aviso de estado inválido.
- [ ] Muestra de manera prominente el motivo de la disputa emitido por el coordinador.
- [ ] Renderiza la imagen original del acta para cotejo visual.
- [ ] Recalcula el checksum inmediatamente ante cada pulsación en los inputs numéricos.
- [ ] Bloquea el botón "Enviar Corrección" mientras la suma difiera de `totalSufragantes`.
- [ ] Permite opcionalmente capturar una nueva foto si el motivo de disputa reporta "Foto ilegible".
- [ ] Al presionar "Enviar Corrección", realiza la llamada `PUT` al backend y al recibir 200 transiciona a estado `CORREGIDA`, mostrando toast de éxito y volviendo a la lista de JRVs.

## Fuera de Alcance
- Descarga de expedientes PDF (función exclusiva de coordinación y administración).

## Dependencias
- Backend `T-BE-21` (`GET /api/v1/observer/actas/{id}` con `ActaDetalleCompletoDto`).
