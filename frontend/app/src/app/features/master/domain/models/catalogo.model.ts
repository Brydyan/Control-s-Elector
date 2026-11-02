export type NivelCargo = 'NACIONAL' | 'PROVINCIA' | 'CANTON' | 'PARROQUIA';

export interface CargoResponseDto {
  id: number;
  nombre: string;
  nivel: NivelCargo;
  puestos: number;
}

export interface CargoRequestDto {
  nombre: string;
  nivel: NivelCargo;
  puestos: number;
}

export interface PartidoResponseDto {
  id: number;
  nombre: string;
  siglas: string;
  numeroLista: number;
}

export interface PartidoRequestDto {
  nombre: string;
  siglas: string;
  numeroLista: number;
}

export interface CandidatoResponseDto {
  id: number;
  nombre: string;
  orden: number;
  cargoId: number;
  partidoId: number;
}

export interface CandidatoRequestDto {
  nombre: string;
  orden: number;
  cargoId: number;
  partidoId: number;
}
