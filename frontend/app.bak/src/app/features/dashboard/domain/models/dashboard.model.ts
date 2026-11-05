/**
 * Modelos del dashboard reactivo (T-FE-11, A.1).
 *
 * Reflejan los DTOs del backend (`backend/electoral-api/.../dashboard/web/dto/`).
 * El backend excluye ANULADA en queries (D1 backend) — FE NO debe filtrar de nuevo.
 */

export interface ResumenActas {
  enviadas: number;
  enDisputa: number;
  corregidas: number;
  aprobadas: number;
  totalActivas: number;
}

export interface ResultadoCandidato {
  candidatoId: number;
  candidatoNombre: string;
  partidoNombre: string | null;
  votos: number;
}

/**
 * T-FE-15 B.1: Resultado detallado de candidato con estado provisional y foto.
 * Extiende ResultadoCandidato con campos visuales para dashboard.
 */
export interface CandidatoResultadoDto extends ResultadoCandidato {
  /** Proporción de votos respecto a votos válidos (0..100). */
  porcentaje: number;
  /** Estado provisional: 'GANADOR_PROVISIONAL', 'EN_CONTEO', 'PERDEDOR_PROVISIONAL', etc. */
  estado: string;
  /** URL de foto del candidato (opcional). */
  fotoUrl?: string | null;
}

export interface ResultadoCargo {
  cargoId: number;
  cargoNombre: string;
  candidatos: ResultadoCandidato[];
}

/**
 * T-FE-15 B.1: Resultado de cargo con desglose de votos válidos, blancos, nulos.
 * Extiende ResultadoCargo para dashboard analítico.
 */
export interface ResultadoCargoDto extends ResultadoCargo {
  /** Candidatos con detalles adicionales (estado, foto, porcentaje). */
  candidatos: CandidatoResultadoDto[];
  /** Total de votos válidos (suma de votos candidatos). */
  votosValidos: number;
  /** Total de votos en blanco. */
  votosBlancos: number;
  /** Total de votos nulos. */
  votosNulos: number;
}

/**
 * T-FE-15 C.1: Progreso a nivel de parroquia.
 */
export interface ParroquiaProgresoDto {
  parroquiaId: number;
  parroquiaNombre: string;
  jrvsEscrutadas: number;
  jrvsTotal: number;
  /** Porcentaje de avance (0..100). */
  avance: number;
}

export interface ProgresoCanton {
  cantonId: number;
  cantonNombre: string;
  totalJrvs: number;
  jrvsConActa: number;
  /** 0..100 (double). */
  porcentaje: number;
}

/**
 * T-FE-15 C.1: Progreso cantonal detallado con desglose de parroquias.
 * Extiende ProgresoCanton con detalles territoriales.
 */
export interface ProgresoCantonDto extends ProgresoCanton {
  /** Métricas agrupadas por parroquia. */
  parroquias: ParroquiaProgresoDto[];
}

export interface Novedad {
  actaPublicId: string; // UUID público
  tipo: string; // 'ACTA_EN_DISPUTA' hoy
  jrvId: number;
  jrvNumero: string;
  cargoId: number;
  cargoNombre: string;
  observerPublicId: string | null;
  observerNombre: string | null;
  motivo: string | null;
  minutosTranscurridos: number;
}

/** Payload consolidado devuelto por `getDashboardCompleto()`. */
export interface DashboardData {
  resumen: ResumenActas;
  resultados: ResultadoCargo[];
  progreso: ProgresoCanton[];
  novedades: Novedad[];
}
