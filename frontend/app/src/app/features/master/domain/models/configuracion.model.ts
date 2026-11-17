/**
 * T-FE-16 Fase A.1: Modelos de Configuración Electoral
 *
 * Interfaces tipadas para entidades de configuración: Cargos, Partidos, Candidatos.
 * Sin `any` types. Incluye JSDoc con ejemplos de payload esperado.
 */

// ============================================================================
// CARGO (Dignidad Electoral)
// ============================================================================

/**
 * CargoDto — Respuesta del backend para un cargo electoral.
 *
 * @example
 * {
 *   "id": 1,
 *   "nombre": "Presidente",
 *   "nivelTerritorial": "NACIONAL",
 *   "orden": 1,
 *   "estado": "ACTIVO"
 * }
 */
export interface CargoDto {
  id: number;
  nombre: string;
  nivel?: string;
  nivelTerritorial?: string;
  puestos?: number;
  orden?: number;
  estado?: string;
}

export interface CargoRequest {
  nombre: string;
  nivel: string;
  puestos: number;
}

// ============================================================================
// PARTIDO (Organización Política)
// ============================================================================

/**
 * PartidoDto — Respuesta del backend para un partido político.
 */
export interface PartidoDto {
  id: number;
  nombre: string;
  siglas?: string;
  numeroLista?: number;
  logoUrl?: string;
  color?: string;
  estado?: string;
}

export interface PartidoRequest {
  nombre: string;
  siglas: string;
  numeroLista: number;
}

// ============================================================================
// CANDIDATO (Persona como aspirante a cargo)
// ============================================================================

/**
 * CandidatoDto — Respuesta del backend para un candidato electoral.
 */
export interface CandidatoDto {
  id: number;
  nombre: string;
  fotoUrl?: string;
  cargoId: number;
  partidoId: number;
  orden?: number;
  ordenLista?: number;
  estado?: string;
}

export interface CandidatoRequest {
  nombre: string;
  orden: number;
  cargoId: number;
  partidoId: number;
}
