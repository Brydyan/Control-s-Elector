import {
  CargoRequestDto,
  PartidoRequestDto,
  CandidatoRequestDto,
} from '../models/catalogo.model';

export function validarCargo(data: Partial<CargoRequestDto>): string | null {
  if (!data.nombre || !data.nombre.trim()) {
    return 'El nombre del cargo es obligatorio.';
  }
  if (!data.puestos || data.puestos < 1) {
    return 'Debe haber al menos 1 puesto.';
  }
  return null;
}

export function validarPartido(
  data: Partial<PartidoRequestDto>,
): string | null {
  if (!data.nombre || !data.nombre.trim()) {
    return 'El nombre del partido es obligatorio.';
  }
  if (!data.siglas || !data.siglas.trim()) {
    return 'Las siglas son obligatorias.';
  }
  if (
    data.numeroLista === undefined ||
    data.numeroLista === null ||
    data.numeroLista < 1
  ) {
    return 'El número de lista debe ser mayor o igual a 1.';
  }
  return null;
}

export function validarCandidato(
  data: Partial<CandidatoRequestDto>,
): string | null {
  if (!data.nombre || !data.nombre.trim()) {
    return 'El nombre del candidato es obligatorio.';
  }
  if (!data.cargoId) {
    return 'Debe seleccionar un cargo.';
  }
  if (!data.partidoId) {
    return 'Debe seleccionar un partido.';
  }
  if (data.orden === undefined || data.orden === null || data.orden < 1) {
    return 'El orden en la papeleta debe ser mayor o igual a 1.';
  }
  return null;
}
