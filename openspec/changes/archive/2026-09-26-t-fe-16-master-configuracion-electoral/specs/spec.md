# Specification — T-FE-16: Módulo de Configuración Preelectoral MASTER (Cargos, Partidos, Candidatos)

## Requirement: Navegación Tabulada de Configuración Preelectoral
El sistema debe permitir al usuario MASTER alternar entre las pestañas de Cargos, Partidos y Candidatos preservando el estado visual.

### Scenario: Acceso directo a pestaña partidos
- **GIVEN** que el usuario MASTER navega a `/master/partidos`
- **WHEN** la página se inicializa
- **THEN** la pestaña "Partidos Políticos" se encuentra activa
- **AND** la tabla muestra los partidos registrados con sus logos, listas y siglas.

---

## Requirement: Creación y Edición de Registro Preelectoral
El sistema debe permitir registrar nuevas organizaciones políticas, cargos y candidatos mediante modales interactivos validados.

### Scenario: Alta de un nuevo partido político
- **GIVEN** que el usuario MASTER se encuentra en la pestaña "Partidos Políticos"
- **WHEN** pulsa "+ Nuevo Registro"
- **AND** completa el formulario con Nombre: *"Movimiento Comunitario"*, Lista: `150`, Siglas: *"MC"*
- **AND** pulsa "Guardar Registro"
- **THEN** se emite una petición `POST /api/v1/configuracion/partidos`
- **AND** al recibir 201 Created, el modal se cierra y la tabla incluye el nuevo partido.

### Scenario: Edición de un cargo existente
- **GIVEN** un cargo "Alcalde/sa" con orden `2`
- **WHEN** el usuario pulsa el botón editar en la fila correspondiente
- **AND** cambia el orden a `3` y pulsa "Actualizar"
- **THEN** se emite una petición `PUT /api/v1/configuracion/cargos/{id}`
- **AND** la tabla actualiza el orden visualmente.

---

## Requirement: Restricción de Acceso por Rol
Solo usuarios con rol MASTER pueden acceder a estas rutas operativas.

### Scenario: Intento de acceso por un OBSERVADOR
- **GIVEN** que un usuario autenticado con rol OBSERVER intenta acceder a `/master/cargos`
- **WHEN** el enrutador evalúa el `roleGuard`
- **THEN** el acceso es denegado y el usuario es redirigido a `/dashboard`.
