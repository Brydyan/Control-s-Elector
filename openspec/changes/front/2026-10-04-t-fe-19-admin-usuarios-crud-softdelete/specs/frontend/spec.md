# Spec — T-FE-19: Gestión de Usuarios MASTER — CRUD, Soft-Delete y Acciones con Íconos

---

## Requerimientos Funcionales

### RF-USUARIOS-01: Detección de Rol MASTER vía AuthStore
El componente `AdminUsuariosPage` debe obtener el rol del usuario conectado a través de `AuthStore.user()?.role`.
- **GIVEN** un usuario autenticado con rol `MASTER` en `AuthStore`
- **WHEN** se monta `AdminUsuariosPage`
- **THEN** la propiedad `esMaster` es `true` y se habilitan los controles de creación, edición y deshabilitación.

### RF-USUARIOS-02: Visualización del Botón "Crear usuario"
- **GIVEN** un usuario con `esMaster === true`
- **WHEN** se visualiza la cabecera de la página
- **THEN** el botón `Crear usuario` está presente en el DOM.
- **AND** al hacer clic abre el modal de alta de usuario.

### RF-USUARIOS-03: Columna de Acciones con Íconos
Cada fila de usuario en la tabla y cada tarjeta en vista móvil debe contener una barra de acciones compuesta por botones tipo icono:
- **Vista previa / Detalles**: Botón con ícono `eye-outline` presente para todos los roles con acceso.
- **Editar**: Botón con ícono `create-outline`, visible solo si `esMaster === true`.
- **Deshabilitar (Soft-Delete)**: Botón con ícono `trash-outline`, visible solo si `esMaster === true` y el usuario tiene `habilitado === true`.
- **Re-habilitar**: Botón con ícono `refresh-outline`, visible solo si `esMaster === true` y el usuario tiene `habilitado === false`.

### RF-USUARIOS-04: Ejecución de Soft-Delete
- **GIVEN** un usuario activo en la lista
- **WHEN** el administrador pulsa el botón con ícono `trash-outline` y confirma la acción
- **THEN** se envía `DELETE /api/v1/admin/usuarios/{publicId}`
- **AND** tras recibir HTTP 204 No Content, el usuario se marca localmente con `habilitado: false` o se recarga la lista.
- **AND** el registro permanece en la base de datos sin ser purgado físicamente.

### RF-USUARIOS-05: Modal de Creación y Credencial Temporal
- **GIVEN** el modal de creación abierto
- **WHEN** se ingresan `nombre`, `apellido` y se selecciona un `rol`
- **AND** se hace clic en `Guardar`
- **THEN** se envía `POST /api/v1/admin/usuarios` con el payload
- **AND** al recibir HTTP 201 Created, se despliega la tarjeta con el email corporativo generado y la `passwordTemporal`.

---

## Escenarios de Prueba (Gherkin)

### Escenario 1: Usuario con rol ADMIN (no MASTER) solo ve ícono de detalles
```gherkin
Scenario: Rol no-MASTER tiene acceso de solo lectura
  Given el usuario autenticado tiene rol "ADMIN" en AuthStore
  When se carga la página "/admin/usuarios"
  Then el botón "Crear usuario" NO debe existir en el DOM
  And en la columna de acciones solo debe existir el botón con icono "eye-outline"
  And los botones con iconos "create-outline", "trash-outline" y "refresh-outline" NO deben existir
```

### Escenario 2: Usuario con rol MASTER ve barra completa de acciones
```gherkin
Scenario: Rol MASTER visualiza acciones completas
  Given el usuario autenticado tiene rol "MASTER" en AuthStore
  When se carga la página "/admin/usuarios" con 1 usuario habilitado y 1 deshabilitado
  Then el botón "Crear usuario" debe estar visible en la cabecera
  And para el usuario habilitado se muestran iconos "eye-outline", "create-outline" y "trash-outline"
  And para el usuario deshabilitado se muestran iconos "eye-outline", "create-outline" y "refresh-outline"
```

### Escenario 3: Soft-delete exitoso actualiza estado sin borrado físico
```gherkin
Scenario: Deshabilitación de usuario mediante soft-delete
  Given un usuario habilitado con ID "usr-123"
  And el administrador MASTER confirma el cuadro de diálogo de deshabilitación
  When se dispara la acción deshabilitar
  Then se ejecuta AdminService.deshabilitarUsuario("usr-123")
  And el estado visual cambia a "Deshabilitado"
  And el icono "trash-outline" es reemplazado por "refresh-outline"
```
