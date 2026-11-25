/**
 * Modelos de la configuracion geografica jerarquica (SDD 2026-11-16-config-geografica).
 *
 * <p>Cuatro niveles: provincia → canton → parroquia → JRV. El backend devuelve el arbol
 * completo en `GET /api/v1/master/geografia` y los nombres de campo siguen la convencion
 * camelCase del resto de la API (`esUrbana`, `totalElectores`).
 */

/** JRV: junta de recepcion de votos, ultimo nivel de la jerarquia. */
export interface JrvNodoDto {
  id: number;
  numero: string;
  recinto: string;
  totalElectores: number;
  recintoId: number | null;
}

/** Parroquia: tercer nivel. `esUrbana` distingue la parroquia urbana de la rural. */
export interface ParroquiaNodoDto {
  id: number;
  nombre: string;
  esUrbana: boolean;
  jrvs: JrvNodoDto[];
}

/** Canton: segundo nivel. */
export interface CantonNodoDto {
  id: number;
  nombre: string;
  codigo: string;
  parroquias: ParroquiaNodoDto[];
}

/** Provincia: raiz del arbol. El codigo puede venir null (la provincia no lo tiene). */
export interface ProvinciaNodoDto {
  id: number;
  nombre: string;
  codigo: string | null;
  cantones: CantonNodoDto[];
}

/** Arbol geografico completo. */
export interface GeografiaTreeDto {
  provincias: ProvinciaNodoDto[];
}

/** Payload de alta/edicion de una provincia. */
export interface ProvinciaRequestDto {
  nombre: string;
  codigo?: string | null;
}

/** Respuesta del backend para una provincia. */
export interface ProvinciaResponseDto {
  id: number;
  nombre: string;
  codigo: string | null;
}

/** Payload de alta/edicion de un canton. */
export interface CantonRequestDto {
  nombre: string;
  codigo: string;
  provinciaId: number;
}

/** Respuesta del backend para un canton. */
export interface CantonResponseDto {
  id: number;
  nombre: string;
  codigo: string;
  provinciaId: number;
}

/** Payload de alta/edicion de una parroquia. */
export interface ParroquiaRequestDto {
  nombre: string;
  esUrbana: boolean;
  cantonId: number;
}

/** Respuesta del backend para una parroquia. */
export interface ParroquiaResponseDto {
  id: number;
  nombre: string;
  esUrbana: boolean;
  cantonId: number;
}

/** Payload de alta/edicion de una JRV. */
export interface JrvRequestDto {
  numero: string;
  recinto: string;
  totalElectores: number;
  parroquiaId: number;
}

/** Respuesta del backend para una JRV. */
export interface JrvResponseDto {
  id: number;
  numero: string;
  recinto: string;
  totalElectores: number;
  parroquiaId: number;
  recintoId: number | null;
}
