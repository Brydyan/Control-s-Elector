# Design — T-FE-19: Gestión de Usuarios MASTER — CRUD, Soft-Delete y Acciones con Íconos

---

## D1 — Inyección de `AuthStore` para Control de Acceso RBAC

**Decisión**:
Eliminar completamente el método `detectarRolMaster()` que accedía a `localStorage` y `sessionStorage`.
Inyectar `AuthStore` directamente en `AdminUsuariosPage` y definir una propiedad reactiva o getter derivado:

```typescript
private readonly authStore = inject(AuthStore);

get esMaster(): boolean {
  return this.authStore.user()?.role === 'MASTER';
}
```

**Alternativa descartada — Guardar el rol en `localStorage`**:
Descartada por violación del ADR de seguridad del proyecto. Los tokens y roles residen únicamente en memoria (`signals`) para prevenir robo de sesión ante ataques XSS.

---

## D2 — Sistema de Iconografía en Columna de Acciones

**Decisión**:
Sustituir los botones con etiquetas de texto por botones compactos tipo ícono (`icon-btn`) utilizando el componente reusable `app-icon`:

```html
<td class="actions">
  <!-- 1. Vista previa / Ver detalles -->
  <button
    type="button"
    class="icon-btn btn-view"
    (click)="verDetalles(usuario)"
    title="Ver detalles del usuario"
    aria-label="Ver detalles del usuario"
  >
    <app-icon name="eye-outline" [size]="18" />
  </button>

  <!-- 2. Editar (solo MASTER) -->
  <button
    type="button"
    *ngIf="esMaster"
    class="icon-btn btn-edit"
    (click)="abrirEditar(usuario)"
    title="Editar usuario"
    aria-label="Editar usuario"
  >
    <app-icon name="create-outline" [size]="18" />
  </button>

  <!-- 3. Soft-delete / Borrar (solo MASTER y usuario habilitado) -->
  <button
    type="button"
    *ngIf="puedeAnular(usuario)"
    class="icon-btn btn-delete"
    (click)="deshabilitar(usuario)"
    title="Deshabilitar usuario"
    aria-label="Deshabilitar usuario"
  >
    <app-icon name="trash-outline" [size]="18" />
  </button>

  <!-- 4. Re-habilitar (solo MASTER y usuario deshabilitado) -->
  <button
    type="button"
    *ngIf="esMaster && !usuario.habilitado"
    class="icon-btn btn-restore"
    (click)="rehabilitar(usuario)"
    title="Re-habilitar usuario"
    aria-label="Re-habilitar usuario"
  >
    <app-icon name="refresh-outline" [size]="18" />
  </button>
</td>
```

**Registro de Íconos requeridos**:
Asegurar que `eye-outline`, `create-outline`, `trash-outline` y `refresh-outline` estén registrados vía `addIcons(...)` en:
- [`frontend/app/src/main.ts`](file:///home/andy/Escritorio/PROYECTOS/CONTROLs/Control-s-Elector/frontend/app/src/main.ts)
- [`frontend/app/src/test-setup.ts`](file:///home/andy/Escritorio/PROYECTOS/CONTROLs/Control-s-Elector/frontend/app/src/test-setup.ts)

---

## D3 — Semántica de Soft-Delete e Invariante de Auditoría

**Decisión**:
1. El backend expone `DELETE /api/v1/admin/usuarios/{publicId}`, el cual ejecuta internamente:
   `usuario.setHabilitado(false); usuarios.save(usuario);`
2. El frontend solicita confirmación antes de disparar la petición:
   `¿Estás seguro de que deseas deshabilitar a este usuario? Perderá el acceso al sistema inmediatamente.`
3. Al completarse la respuesta 204 No Content:
   - Se actualiza la lista local o se refresca `cargarUsuarios()`.
   - El badge del usuario cambia de "Habilitado" (verde) a "Deshabilitado" (gris/rojo).
   - El botón de borrar (`trash-outline`) se reemplaza automáticamente por el botón de restaurar (`refresh-outline`).

**Alternativa descartada — Borrado físico en base de datos**:
Completamente descartada. Si un usuario digitó o aprobó actas, borrar su registro rompería la integridad referencial (`ON DELETE RESTRICT`) o eliminaría la trazabilidad de auditoría legal requerida por el CNE.

---

## D4 — Modal de Creación y Tratamiento de Contraseña Temporal

**Decisión**:
1. Formulario reactivo modal con campos:
   - Nombre (mínimo 2 caracteres)
   - Apellido (mínimo 2 caracteres)
   - Rol (Selector: `MASTER`, `COORDINATOR`, `ADMIN`, `OBSERVER`)
2. Al presionar "Guardar", se envía `POST /api/v1/admin/usuarios`.
3. El backend devuelve HTTP 201 Created con el DTO que incluye:
   - `email`: generado automáticamente por la convención `{3nom}{3ape}{3rol}@control.ttcs`.
   - `passwordTemporal`: cadena aleatoria Base64Url de un solo uso.
4. El modal no se cierra de golpe: pasa al estado de éxito mostrando una tarjeta de alerta prominente con el email generado, la contraseña temporal y un botón para copiarla.
