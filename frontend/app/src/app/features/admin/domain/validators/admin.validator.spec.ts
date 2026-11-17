import { describe, it, expect } from 'vitest';
import { validarUsuario } from './admin.validator';

describe('Admin Validators (Domain)', () => {
  it('debe requerir nombre, apellido y rol', () => {
    expect(
      validarUsuario({ nombre: '', apellido: 'Pérez', rolNombre: 'ADMIN' }),
    ).toBe('El nombre es obligatorio.');
    expect(
      validarUsuario({ nombre: 'Juan', apellido: '', rolNombre: 'ADMIN' }),
    ).toBe('El apellido es obligatorio.');
    expect(
      validarUsuario({
        nombre: 'Juan',
        apellido: 'Pérez',
        rolNombre: undefined,
      }),
    ).toBe('El rol es obligatorio.');
  });

  it('debe pasar cuando los datos son válidos', () => {
    expect(
      validarUsuario({ nombre: 'Juan', apellido: 'Pérez', rolNombre: 'ADMIN' }),
    ).toBeNull();
  });
});
