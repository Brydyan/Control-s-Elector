# ODD — Allow Edit Email Personal in Usuario Modal

**Reference:** `admin-usuarios` page, edit modal  
**Status:** Ready for Builder  
**Effort:** 1-2 hours (frontend + backend)  
**Criticality:** LOW — User convenience, non-blocking

---

## Specs

### S1 — Backend: Add emailPersonal to UpdateUsuarioRequest

**Current:** `UpdateUsuarioRequest` has nombre, apellido, rolNombre, habilitado (NO emailPersonal).

**Desired:** Add optional `emailPersonal` field so MASTER can update email when editing.

**Acceptance:**
- ✅ UpdateUsuarioRequest includes `String emailPersonal` (optional)
- ✅ UsuarioAdminService.actualizar() reads and applies emailPersonal
- ✅ No validation change (backend already validates email format on create)
- ✅ Existing clients ignore field (backward compatible)

**Example Request:**
```json
{
  "nombre": "Juan",
  "apellido": "Pérez",
  "rolNombre": "COORDINATOR",
  "emailPersonal": "juan.nuevo@example.com"  // NEW
}
```

---

### S2 — Frontend: Load and Send emailPersonal in Edit Mode

**Current:** `abrirEditar()` loads nombre, apellido, rol but NOT emailPersonal.  
Current: `guardar()` sends those fields but NOT emailPersonal.

**Desired:** When editing (modoEdicion = true), include emailPersonal in request.

**Acceptance:**
- ✅ `abrirEditar(usuario)` loads `this.emailPersonal = usuario.emailPersonal`
- ✅ Modal shows emailPersonal field (read when modoEdicion = true)
- ✅ `guardar()` includes emailPersonal in payload when modoEdicion = true
- ✅ Email validation on form (regex pattern or native input)
- ✅ All existing tests still pass

---

### S3 — UI: Show Email Field Only in Edit Mode

**Requirement:** Email field should only be editable when editing (modoEdicion = true).  
When creating (modoEdicion = false), email is handled separately (invitación flow).

**Acceptance:**
- ✅ Email input hidden during create flow
- ✅ Email input visible during edit flow
- ✅ Clear label: "Correo Personal"
- ✅ Help text: "(correo de contacto para invitaciones)"

---

## Tasks

| ID | Linked Spec | Description | Route |
|-----|-------------|-------------|-------|
| **T1** | S1 | Backend: Add emailPersonal to UpdateUsuarioRequest | Backend |
| **T2** | S1 | Backend: Update UsuarioAdminService.actualizar() | Backend |
| **T3** | S2 | Frontend: Load emailPersonal in abrirEditar() | Frontend |
| **T4** | S2 | Frontend: Send emailPersonal in guardar() | Frontend |
| **T5** | S3 | Frontend: Add email field to modal HTML | Frontend |
| **T6** | — | Tests: Verify edit + email update works | Tests |

---

## Implementation Checklist

### T1 — Backend: UpdateUsuarioRequest

**File:** `backend/electoral-api-quarkus/src/main/java/ec/twintailcs/electoral/auth/web/dto/UpdateUsuarioRequest.java`

**Current:**
```java
public record UpdateUsuarioRequest(
  @NotBlank String nombre,
  @NotBlank String apellido,
  @NotBlank String rolNombre,
  boolean habilitado
) {}
```

**New:**
```java
public record UpdateUsuarioRequest(
  @NotBlank String nombre,
  @NotBlank String apellido,
  @NotBlank String rolNombre,
  boolean habilitado,
  @Email String emailPersonal  // NEW — optional, can be null
) {}
```

**Deliverable:**
- ✅ Record updated with emailPersonal field
- ✅ @Email annotation for validation

---

### T2 — Backend: UsuarioAdminService.actualizar()

**File:** `backend/electoral-api-quarkus/src/main/java/ec/twintailcs/electoral/auth/application/UsuarioAdminService.java`

**Current:**
```java
public Usuario actualizar(String publicId, UpdateUsuarioRequest req) {
  Usuario usuario = obtenerOThrow(publicId);
  usuario.setNombre(req.nombre());
  usuario.setApellido(req.apellido());
  usuario.setRol(rolRepository.obtenerOThrow(req.rolNombre()));
  usuario.setHabilitado(req.habilitado());
  return usuarioRepository.persistir(usuario);
}
```

**New:**
```java
public Usuario actualizar(String publicId, UpdateUsuarioRequest req) {
  Usuario usuario = obtenerOThrow(publicId);
  usuario.setNombre(req.nombre());
  usuario.setApellido(req.apellido());
  usuario.setRol(rolRepository.obtenerOThrow(req.rolNombre()));
  usuario.setHabilitado(req.habilitado());
  
  // NEW: Allow MASTER to update email personal
  if (req.emailPersonal() != null && !req.emailPersonal().isBlank()) {
    usuario.setEmailPersonal(req.emailPersonal());
  }
  
  return usuarioRepository.persistir(usuario);
}
```

**Deliverable:**
- ✅ Check if emailPersonal provided
- ✅ Update if present and non-blank
- ✅ No breaking change if null

---

### T3 — Frontend: abrirEditar()

**File:** `frontend/app/src/app/features/admin/pages/usuarios/admin-usuarios.page.ts`

**Current (lines 264-274):**
```typescript
abrirEditar(usuario: UsuarioResponseDto): void {
  if (!this.esMaster()) return;
  this.modoEdicion = true;
  this.usuarioSeleccionado = usuario;
  this.nombre = usuario.nombre;
  this.apellido = usuario.apellido;
  this.rol = usuario.rolNombre as RolUsuario;
  this.passwordTemporal = null;
  this.error.set(null);
  this.modalAbierto = true;
}
```

**New:**
```typescript
abrirEditar(usuario: UsuarioResponseDto): void {
  if (!this.esMaster()) return;
  this.modoEdicion = true;
  this.usuarioSeleccionado = usuario;
  this.nombre = usuario.nombre;
  this.apellido = usuario.apellido;
  this.rol = usuario.rolNombre as RolUsuario;
  this.emailPersonal = usuario.emailPersonal || '';  // NEW
  this.passwordTemporal = null;
  this.error.set(null);
  this.modalAbierto = true;
}
```

**Deliverable:**
- ✅ Load emailPersonal from usuario (default empty string if null)

---

### T4 — Frontend: guardar() Update Payload

**File:** `frontend/app/src/app/features/admin/pages/usuarios/admin-usuarios.page.ts`

**Current (lines 290-297):**
```typescript
const payload = {
  nombre: this.nombre.trim(),
  apellido: this.apellido.trim(),
  rolNombre: this.rol,
  sendInvitation: !this.modoEdicion
    ? this.enviarInvitacionPorEmail()
    : undefined,
};
```

**New:**
```typescript
const payload = {
  nombre: this.nombre.trim(),
  apellido: this.apellido.trim(),
  rolNombre: this.rol,
  emailPersonal: this.modoEdicion ? this.emailPersonal.trim() : undefined,  // NEW
  sendInvitation: !this.modoEdicion
    ? this.enviarInvitacionPorEmail()
    : undefined,
};
```

**Deliverable:**
- ✅ Include emailPersonal only in edit mode
- ✅ Trim whitespace
- ✅ Undefined when creating (not sent in create flow)

---

### T5 — Frontend: HTML Modal

**File:** `frontend/app/src/app/features/admin/pages/usuarios/admin-usuarios.page.html`

**Add (after apellido field, before rol selector):**
```html
<!-- Email Personal — only in edit mode -->
<div *ngIf="modoEdicion" class="mb-4">
  <label for="emailPersonal" class="block text-sm font-semibold mb-2">
    Correo Personal
  </label>
  <input
    id="emailPersonal"
    type="email"
    [(ngModel)]="emailPersonal"
    placeholder="juan@example.com"
    class="w-full px-3 py-2 border rounded-md"
    [disabled]="submitting()"
  />
  <small class="text-gray-500">
    (correo de contacto para invitaciones)
  </small>
</div>
```

**Deliverable:**
- ✅ Email input shown only when editing
- ✅ Type="email" for browser validation
- ✅ Disabled while submitting
- ✅ Help text visible

---

### T6 — Tests

**Files:**
- `admin-usuarios.page.spec.ts`

**New test:**
```typescript
describe('Edit usuario email personal', () => {
  it('should load emailPersonal when opening edit modal', () => {
    const usuario: UsuarioResponseDto = {
      publicId: 'user-1',
      nombre: 'Juan',
      apellido: 'Pérez',
      rolNombre: 'OBSERVER',
      emailPersonal: 'juan@example.com',
      habilitado: true,
      estadoUsuario: 'ACTIVA',
      // ... other fields
    };
    
    component.abrirEditar(usuario);
    
    expect(component.emailPersonal).toBe('juan@example.com');
    expect(component.modoEdicion).toBe(true);
  });

  it('should send emailPersonal in payload when editing', () => {
    component.modoEdicion = true;
    component.usuarioSeleccionado = mockUsuario;
    component.nombre = 'Juan Nuevo';
    component.apellido = 'Pérez';
    component.rol = 'COORDINATOR';
    component.emailPersonal = 'juan.nuevo@example.com';

    spyOn(service, 'actualizarUsuario').and.returnValue(of(mockUsuario));

    component.guardar();

    expect(service.actualizarUsuario).toHaveBeenCalledWith(
      'user-1',
      jasmine.objectContaining({
        nombre: 'Juan Nuevo',
        apellido: 'Pérez',
        rolNombre: 'COORDINATOR',
        emailPersonal: 'juan.nuevo@example.com',
      })
    );
  });

  it('should not send emailPersonal when creating new usuario', () => {
    component.modoEdicion = false;
    component.nombre = 'Nueva';
    component.apellido = 'Persona';
    component.rol = 'OBSERVER';
    component.emailPersonal = 'nueva@example.com';

    spyOn(service, 'crearUsuario').and.returnValue(of(mockUsuario));

    component.guardar();

    expect(service.crearUsuario).toHaveBeenCalledWith(
      jasmine.objectContaining({
        nombre: 'Nueva',
        apellido: 'Persona',
        rolNombre: 'OBSERVER',
        // emailPersonal NOT in payload (only in sendInvitation flow)
      })
    );
  });
});
```

**Deliverable:**
- ✅ 3 new tests (load, edit with email, create without email)
- ✅ All existing tests still pass

---

## Verification

**All pass locally:**
```bash
npm run test -- admin-usuarios.page.spec.ts
# Expected: all tests pass (existing + 3 new)

rtk mvn test -Dtest='UsuarioAdminServiceTest,UsuarioResourceIT'
# Expected: all tests pass
```

**Manual verification:**
1. MASTER navigates to `/admin/usuarios`
2. Clicks "Editar" on a user
3. Modal opens with existing email visible
4. Modifies email field
5. Clicks "Guardar"
6. Verifies in table that email updated
7. Reloads page — email persisted

---

## Risk

- None. Pure feature addition, backward compatible.
- UpdateUsuarioRequest field is optional, ignored by old clients.
- No DB changes.

---

## Notes

- Email validation happens at two levels: HTML5 (input type=email) + backend (@Email)
- EmailPersonal remains immutable for create flow (only updated via edit action)
- MASTER only can edit (existing permission model)

---

**Risk:** none

