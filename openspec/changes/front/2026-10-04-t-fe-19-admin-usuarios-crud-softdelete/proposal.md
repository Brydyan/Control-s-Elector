# Proposal — T-FE-19: Gestión de Usuarios MASTER — CRUD, Soft-Delete y Acciones con Íconos

Origen: Requerimiento de usuario / Auditoría de roles · Área: frontend / admin · Fecha: 2026-10-04

---

## Qué se construye

Implementación formal y modernización visual del módulo de **Gestión de Usuarios** (`/admin/usuarios`):

1. **Resolución de la Causa Raíz de Permisos (`AuthStore`)**:
   - Corrección inmediata de `detectarRolMaster()` en `AdminUsuariosPage`.
   - Reemplazo de la lectura fallida de `localStorage.getItem('app.user.roles')` por la señal en memoria oficial `authStore.user()?.role === 'MASTER'`.
   - Habilitación reactiva de los privilegios administrativos para el usuario `MASTER`.

2. **Botón Principal de Alta en Cabecera**:
   - Botón visible para `MASTER`: **"Crear usuario"** (`primary`).
   - Modal de creación reactivo con validación de campos obligatorios (`nombre`, `apellido`, `rol`).
   - Consumo de `POST /api/v1/admin/usuarios`: el backend genera automáticamente el email corporativo `{3nom}{3ape}{3rol}@control.ttcs` y retorna la contraseña temporal.
   - Presentación de tarjeta destacada con la contraseña temporal de un solo uso para que el administrador la copie.

3. **Columna de Acciones con Íconos Descriptivos y Accesibles**:
   - Sustitución de los botones de texto plano por una barra de acciones compacta basada en íconos SVG (`app-icon` / Ionicons) con `title` y `aria-label`:
     - 👁️ **Ver detalles / Vista previa (`eye-outline`)**: Modal informativo con datos consolidados del usuario (nombre completo, email, rol, estado, ID público y fecha de creación).
     - ✏️ **Editar usuario (`create-outline`)**: Modal interactivo para modificar nombre, apellido y rol del usuario (`PUT /api/v1/admin/usuarios/{publicId}`).
     - 🗑️ **Borrar / Deshabilitar (`trash-outline`)**: Acción de **Soft-Delete** con confirmación explícita previa. Consume `DELETE /api/v1/admin/usuarios/{publicId}` fijando `habilitado = false`. Preserva intacto el historial en `audit_log` y registros electorales.
     - 🔄 **Re-habilitar usuario (`refresh-outline`)**: Visible condicionalmente cuando el usuario está en estado deshabilitado para reactivar su acceso (`PUT /api/v1/admin/usuarios/{publicId}` con `habilitado: true`).

4. **Compatibilidad Responsive y Estados Visuales**:
   - Tabla con scroll horizontal optimizado para escritorio.
   - Formato en tarjetas (`cards`) para dispositivos móviles con los mismos íconos de acción.
   - Badges semánticos de estado: `Habilitado` (verde) y `Deshabilitado` (gris/rojo).

---

## Por qué

- Actualmente, un usuario autenticado como `MASTER` solo observa el botón "Ver detalles" en la tabla, debido a que `admin-usuarios.page.ts` busca los roles en `localStorage`. Como `AuthStore` almacena la sesión exclusivamente en memoria (`signals`) para mitigar vulnerabilidades XSS, la condición `esMaster` siempre evalúa a `false`, bloqueando la visualización del botón "Crear usuario", "Editar" y "Deshabilitar".
- El uso de texto plano ("Ver detalles", "Editar", "Deshabilitar") en la columna de acciones satura la tabla en pantallas intermedias y degrada la experiencia de usuario. Los íconos universales aportan mayor agilidad operativa.
- En un sistema electoral, **el borrado físico de usuarios está terminantemente prohibido** por normativas de auditoría y trazabilidad CNE. Toda eliminación debe ser estrictamente un **soft-delete** que inhabilite el login pero conserve la autoría de las actas registradas.

---

## Alcance

| Elemento | Incluye | No incluye |
|---|---|---|
| **Vistas** | [`AdminUsuariosPage`](file:///home/andy/Escritorio/PROYECTOS/CONTROLs/Control-s-Elector/frontend/app/src/app/features/admin/usuarios/admin-usuarios.page.ts) (`/admin/usuarios`) | Pantallas operativas de digitación |
| **Acciones** | Crear, Ver detalles, Editar, Soft-delete (deshabilitar), Re-habilitar | Borrado físico en PostgreSQL (`DELETE FROM usuario`) |
| **Iconografía** | `eye-outline`, `create-outline`, `trash-outline`, `refresh-outline` | Íconos decorativos no funcionales |
| **Permisos** | Exclusividad de mutación para rol `MASTER` vía `AuthStore` | Bypass de seguridad o escalada de privilegios |
| **Móvil** | Renderizado en `cards` con fila de íconos alineados | Tablas con scroll horizontal roto |

---

## Criterios de Aceptación

- [ ] **CA-1 (Detección de Rol MASTER)**: Al iniciar sesión con un usuario `MASTER`, `esMaster` evalúa a `true` inmediatamente leyendo de `AuthStore`.
- [ ] **CA-2 (Botón Crear Usuario)**: El botón "Crear usuario" se renderiza en la cabecera si `esMaster` es `true`. Al pulsar, abre modal y tras crear muestra la contraseña temporal generada.
- [ ] **CA-3 (Ícono Ver Detalles)**: En la columna de acciones, el ícono `eye-outline` abre modal de vista previa con datos de sólo lectura.
- [ ] **CA-4 (Ícono Editar)**: El ícono `create-outline` (visible solo para `MASTER`) abre modal con datos precargados para modificar nombre, apellido y rol.
- [ ] **CA-5 (Soft-Delete Estricto)**: El ícono `trash-outline` (visible para `MASTER` en usuarios habilitados) solicita confirmación y llama a `DELETE /api/v1/admin/usuarios/{publicId}`, actualizando el estado a `Deshabilitado` sin eliminar la fila en BD.
- [ ] **CA-6 (Re-habilitación)**: Para usuarios con `habilitado: false`, se muestra el ícono `refresh-outline` permitiendo reactivar al usuario mediante `PUT`.
- [ ] **CA-7 (Accesibilidad)**: Todos los botones de acción cuentan con `aria-label` y `title` descriptivo para lectores de pantalla.
