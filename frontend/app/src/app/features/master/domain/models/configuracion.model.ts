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
  nivelTerritorial: 'PROVINCIAL' | 'CANTONAL' | 'PARROQUIAL' | 'NACIONAL';
  orden: number;
  estado: string;
}

/**
 * CargoRequest — Payload para crear o actualizar un cargo electoral.
 *
 * @example
 * {
 *   "nombre": "Vicepresidente",
 *   "nivelTerritorial": "NACIONAL",
 *   "orden": 2,
 *   "estado": "ACTIVO"
 * }
 */
export interface CargoRequest {
  nombre: string;
  nivelTerritorial: 'PROVINCIAL' | 'CANTONAL' | 'PARROQUIAL' | 'NACIONAL';
  orden: number;
  estado: string;
}

// ============================================================================
// PARTIDO (Organización Política)
// ============================================================================

/**
 * PartidoDto — Respuesta del backend para un partido político.
 *
 * @example
 * {
 *   "id": 1,
 *   "nombre": "Fuerza Compromiso Social",
 *   "siglas": "FCS",
 *   "numeroLista": 20,
 *   "logoUrl": "https://cdn.electoral.ec/logos/fcs.png",
 *   "color": "#FF0000",
 *   "estado": "ACTIVO"
 * }
 */
export interface PartidoDto {
  id: number;
  nombre: string;
  siglas: string;
  numeroLista: number;
  logoUrl?: string;
  color?: string;
  estado: string;
}

/**
 * PartidoRequest — Payload para crear o actualizar un partido político.
 *
 * @example
 * {
 *   "nombre": "Unión por el Cambio",
 *   "siglas": "UC",
 *   "numeroLista": 21,
 *   "logoUrl": "https://cdn.electoral.ec/logos/uc.png",
 *   "color": "#0000FF",
 *   "estado": "ACTIVO"
 * }
 */
export interface PartidoRequest {
  nombre: string;
  siglas: string;
  numeroLista: number;
  logoUrl?: string;
  color?: string;
  estado: string;
}

// ============================================================================
// CANDIDATO (Persona como aspirante a cargo)
// ============================================================================

/**
 * CandidatoDto — Respuesta del backend para un candidato electoral.
 *
 * @example
 * {
 *   "id": 1,
 *   "nombre": "Juan Pérez García",
 *   "fotoUrl": "https://cdn.electoral.ec/fotos/juan-perez.jpg",
 *   "cargoId": 1,
 *   "partidoId": 1,
 *   "ordenLista": 1,
 *   "estado": "ACTIVO"
 * }
 */
export interface CandidatoDto {
  id: number;
  nombre: string;
  fotoUrl?: string;
  cargoId: number;
  partidoId: number;
  ordenLista: number;
  estado: string;
}

/**
 * CandidatoRequest — Payload para crear o actualizar un candidato electoral.
 *
 * @example
 * {
 *   "nombre": "María López Rodríguez",
 *   "fotoUrl": "https://cdn.electoral.ec/fotos/maria-lopez.jpg",
 *   "cargoId": 2,
 *   "partidoId": 2,
 *   "ordenLista": 2,
 *   "estado": "ACTIVO"
 * }
 */
export interface CandidatoRequest {
  nombre: string;
  fotoUrl?: string;
  cargoId: number;
  partidoId: number;
  ordenLista: number;
  estado: string;
}
