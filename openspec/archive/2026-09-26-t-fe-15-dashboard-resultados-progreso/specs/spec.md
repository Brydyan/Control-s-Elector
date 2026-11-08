# Specification — T-FE-15: Analítica Electoral — Resultados por Cargo y Progreso Cantonal (ADMIN/MASTER)

## Requirement: Visualización de Resultados Electorales por Dignidad
El sistema debe presentar los resultados de votos por candidato agrupados por cargo electoral con desglose de porcentajes y votos válidos/blancos/nulos.

### Scenario: Cambio de cargo en selector de resultados
- **GIVEN** que el usuario ADMIN accede a `/dashboard/resultados`
- **AND** el backend responde con datos para "Prefecto/a" y "Alcalde/sa Santa Elena"
- **WHEN** el usuario selecciona la pestaña "Alcalde/sa Santa Elena"
- **THEN** la vista actualiza el título del cargo a "Alcalde/sa Santa Elena"
- **AND** renderiza la lista de candidatos que compiten en ese cargo con sus votos correspondientes
- **AND** destaca al candidato líder con el mayor porcentaje de votos válidos.

### Scenario: Formato de barras porcentuales
- **GIVEN** un candidato con `12,500` votos sobre un total de `25,000` votos válidos (50.0%)
- **WHEN** se renderiza la tarjeta del candidato
- **THEN** la barra horizontal tiene un ancho correspondiente al `50%`
- **AND** se muestra la etiqueta de texto con `50.0%` y `12,500 votos`.

---

## Requirement: Monitoreo de Progreso y Cobertura Territorial Cantonal
El sistema debe mostrar el avance de recepción y procesamiento de actas por cada cantón electoral.

### Scenario: Renderizado de tarjetas de cantón
- **GIVEN** que el usuario accede a `/dashboard/progreso`
- **WHEN** los datos de progreso son recibidos exitosamente
- **THEN** se renderizan 3 tarjetas para los cantones "Santa Elena", "La Libertad" y "Salinas"
- **AND** cada tarjeta indica el número de JRVs procesadas sobre el total del cantón y el porcentaje de avance
- **AND** se aplica la clase semántica correspondiente según el umbral porcentual.

### Scenario: Modo de solo lectura
- **GIVEN** que un usuario con rol ADMIN o COORDINATOR visualiza las pantallas de analítica
- **WHEN** examina la interfaz
- **THEN** no existe ningún botón o acción que permita modificar, anular o ingresar votos.
