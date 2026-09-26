# Proposal — T-FE-10: Coordinator: Validación, Auditoría y Gestión Global de Actas

## Qué se construye
Módulo integral para los coordinadores electorales (COORDINATOR y MASTER) que garantiza la integridad, auditoría y resolución de discrepancias en las actas de toda la provincia de Santa Elena:
1. **Listado Paginado Global (`/coordinator/actas`)**:
   - Consumo de `GET /api/v1/coordinator/actas` con sobre de paginación del servidor `{ data, meta }`.
   - Filtros avanzados por estado (`ENVIADA`, `EN_DISPUTA`, `CORREGIDA`, `APROBADA`, `EN_OBSERVACION`, `ANULADA`), dignidad/cargo, cantón, JRV y búsqueda textual por número de mesa o recinto.
   - Paginador interactivo con control de tamaño de página y ordenamiento.
2. **Detalle de Auditoría y Split-View 50/50 (`/coordinator/actas/:id`)**:
   - En pantallas de escritorio (`>= 992px`), diseño dividido en dos columnas:
     - **Columna Izquierda**: Resumen electoral, desglose de votos por candidato, cálculo del invariante ($\sum \text{votos} = \text{sufragantes}$), historial de auditoría de cambios y barra de transiciones de estado.
     - **Columna Derecha**: Visor de la fotografía original de escrutinio con controles de zoom, rotación y realce de contraste, más enlace directo de contingencia por WhatsApp con el veedor.
3. **Flujo de Acciones y Resolución de Inconsistencias**:
   - **Aprobar**: `PUT /api/v1/coordinator/actas/{id}/aprobar`.
   - **Disputar**: Modal con motivo obligatorio que envía `PUT /api/v1/coordinator/actas/{id}/disputar` con `{ motivo }`.
   - **Corregir**: Modal interactivo de corrección aritmética que valida el cuadre antes de enviar `PUT /api/v1/coordinator/actas/{id}/corregir`.
   - **Manejo de Concurrencia**: Detección de bloqueos o cambios concurrentes (HTTP 409 Conflict) que recarga automáticamente la verdad del servidor.

## Por qué
- La noche de las elecciones, los coordinadores auditan cientos de actas en paralelo. La comparación visual simultánea (foto vs números digitados) es la única forma de detectar fraudes o errores humanos con rapidez.
- Los filtros en el servidor evitan saturar la memoria del navegador con miles de registros de toda la provincia.

## Alcance

| Elemento | Incluye | No incluye |
|----------|---------|-----------|
| Vistas | `ActasListaPage` (`/coordinator/actas`), `ActaDetallePage` (`/coordinator/actas/:id`) | Creación de actas nuevas desde cero (rol Observer) |
| Paginación | Contrato `{ data, meta }` de Spring Boot con ordenamiento y filtros remotos | Paginación puramente en cliente |
| Auditoría | Desglose por candidato, cheques de cuadre, historial de eventos | Modificación directa en base de datos sin auditoría |
| Visor | Zoom CSS, rotación, contraste, WhatsApp wa.me enlace | Edición destructiva del archivo de imagen |
| Acciones | Aprobar, Disputar (con motivo), Corregir (con recálculo), Anular (MASTER) | Aprobación automática masiva sin revisión |

## Criterio de aceptación

- [ ] `ActasListaPage` renderiza la tabla paginada consumiendo `GET /api/v1/coordinator/actas`.
- [ ] Filtros por estado, dignidad, cantón y búsqueda actualizan la query en el servidor.
- [ ] Enlace de fila navega a `/coordinator/actas/:id`.
- [ ] `ActaDetallePage` despliega el layout Split-View 50/50 en pantallas de escritorio.
- [ ] Muestra el estado del cuadre (invariante) y los votos detallados.
- [ ] Despliega la fotografía del acta obtenida del servicio de evidencias.
- [ ] Modal de disputa exige motivo no vacío y ejecuta `PUT /api/v1/coordinator/actas/{id}/disputar`.
- [ ] Modal de corrección recalcula en tiempo real el invariante y ejecuta `PUT /api/v1/coordinator/actas/{id}/corregir`.
- [ ] Si otra sesión modificó el acta (409 Conflict), el sistema informa y recarga los datos actualizados.

## Dependencias

- Backend `CoordinatorController` (`/api/v1/coordinator/actas/*`).
- `T-FE-02` (Rutas con `roleGuard(['COORDINATOR', 'MASTER'])`).
- `T-FE-12` (Componentes compartidos de UI y Paginador).
