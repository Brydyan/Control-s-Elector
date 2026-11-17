import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';

/**
 * Campo de texto que no puede ser solo espacios.
 *
 * <p>El backend valida esos campos con `@NotBlank`: "   " llega al servidor y vuelve un 400.
 * Si el formulario lo acepta, el operador ve un error del servidor en vez de un aviso en el
 * campo, y el formulario deja de ser una primera linea de defensa.
 */
export const sinSoloEspacios: ValidatorFn = (
  control: AbstractControl,
): ValidationErrors | null => {
  const valor = control.value;
  if (typeof valor !== 'string' || valor.trim().length > 0) {
    return null;
  }
  return { espacios: true };
};
