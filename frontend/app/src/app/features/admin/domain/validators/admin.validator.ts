import { CreateUsuarioRequest } from '../models/admin.model';

export function validarUsuario(
  data: Partial<CreateUsuarioRequest>,
): string | null {
  if (!data.nombre || !data.nombre.trim()) {
    return 'El nombre es obligatorio.';
  }
  if (!data.apellido || !data.apellido.trim()) {
    return 'El apellido es obligatorio.';
  }
  if (!data.rol) {
    return 'El rol es obligatorio.';
  }
  return null;
}
