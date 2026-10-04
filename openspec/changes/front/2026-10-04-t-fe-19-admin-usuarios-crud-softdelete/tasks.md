# Tasks — T-FE-19: Gestión de Usuarios MASTER — CRUD, Soft-Delete y Acciones con Íconos

Orden estricto de ejecución: **Fase A antes que B, y B antes que C.**

---

## Fase A — Registro de Íconos y Dependencias

- [ ] **A.1** — Registrar íconos requeridos en [`frontend/app/src/main.ts`](file:///home/andy/Escritorio/PROYECTOS/CONTROLs/Control-s-Elector/frontend/app/src/main.ts) y [`frontend/app/src/test-setup.ts`](file:///home/andy/Escritorio/PROYECTOS/CONTROLs/Control-s-Elector/frontend/app/src/test-setup.ts):
  - `eyeOutline` (`eye-outline`)
  - `createOutline` (`create-outline`)
  - `trashOutline` (`trash-outline`)
  - `refreshOutline` (`refresh-outline`)
- [ ] **A.2** — Importar `IconComponent` en los `imports: [...]` de `AdminUsuariosPage`.

---

## Fase B — Corrección de Detección de Rol MASTER en Componente

- [ ] **B.1** — Inyectar `AuthStore` en [`AdminUsuariosPage`](file:///home/andy/Escritorio/PROYECTOS/CONTROLs/Control-s-Elector/frontend/app/src/app/features/admin/usuarios/admin-usuarios.page.ts).
- [ ] **B.2** — Eliminar el método obsoleto `detectarRolMaster()` que accedía a `localStorage.getItem('app.user.roles')`.
- [ ] **B.3** — Reemplazar por lectura limpia:
  ```typescript
  get esMaster(): boolean {
    return this.authStore.user()?.role === 'MASTER';
  }
  ```

---

## Fase C — Refactorización de UI y Columna de Acciones

- [ ] **C.1** — En [`admin-usuarios.page.html`](file:///home/andy/Escritorio/PROYECTOS/CONTROLs/Control-s-Elector/frontend/app/src/app/features/admin/usuarios/admin-usuarios.page.html), actualizar la cabecera:
  - Verificar que el botón `Crear usuario` esté condicionado a `esMaster`.
- [ ] **C.2** — En la tabla desktop (`<td class="actions">`):
  - Reemplazar botón de texto "Ver detalles" por botón con `<app-icon name="eye-outline" />`.
  - Reemplazar botón de texto "Editar" por botón con `<app-icon name="create-outline" />` (`*ngIf="esMaster"`).
  - Reemplazar botón de texto "Deshabilitar" por botón con `<app-icon name="trash-outline" />` (`*ngIf="puedeAnular(usuario)"`), con estilo de peligro/danger.
  - Reemplazar botón de texto "Re-habilitar" por botón con `<app-icon name="refresh-outline" />` (`*ngIf="esMaster && !usuario.habilitado"`).
  - Añadir `aria-label` y `title` a cada botón.
- [ ] **C.3** — Replicar la misma barra de íconos en la vista móvil de tarjetas (`cards mobile-only`).
- [ ] **C.4** — En [`admin-usuarios.page.scss`](file:///home/andy/Escritorio/PROYECTOS/CONTROLs/Control-s-Elector/frontend/app/src/app/features/admin/usuarios/admin-usuarios.page.scss):
  - Estilar `.icon-btn`, `.btn-view`, `.btn-edit`, `.btn-delete` y `.btn-restore` con padding uniforme, hover y transiciones fluidas.
- [ ] **C.5** — En `admin-usuarios.page.ts`, añadir confirmación previa a `deshabilitar()`:
  - Diálogo de advertencia alertando que el usuario perderá acceso (soft-delete).

---

## Fase D — Tests Unitarios y Verificación

- [ ] **D.1** — En [`admin-usuarios.page.spec.ts`](file:///home/andy/Escritorio/PROYECTOS/CONTROLs/Control-s-Elector/frontend/app/src/app/features/admin/usuarios/admin-usuarios.page.spec.ts):
  - Probar que con usuario `MASTER` en `AuthStore` se renderizan el botón crear y los íconos completos de acción.
  - Probar que con usuario `ADMIN` (no MASTER) solo se renderiza el ícono `eye-outline`.
  - Probar llamada a `AdminService.deshabilitarUsuario()` al confirmar soft-delete.
  - Probar llamada a `AdminService.actualizarUsuario()` con `habilitado: true` al pulsar restaurar.
- [ ] **D.2** — Ejecutar suite completa con `pnpm test:ci` y verificar que los 35+ suites y todos los tests pasan con código 0.
- [ ] **D.3** — Ejecutar `pnpm lint` y `pnpm build`.

---

## Qué NO hacer en esta fase

- **No** cambiar el backend para borrar filas físicamente con SQL `DELETE FROM usuario`.
- **No** volver a almacenar credenciales ni roles en `localStorage`.
- **No** romper el formato responsive móvil de tarjetas.
