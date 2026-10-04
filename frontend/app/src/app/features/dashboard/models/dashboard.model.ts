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

export interface ResultadoCargo {
  cargoId: number;
  cargoNombre: string;
  candidatos: ResultadoCandidato[];
}

export interface ProgresoCanton {
  cantonId: number;
  cantonNombre: string;
  totalJrvs: number;
  jrvsConActa: number;
  /** 0..100 (double). */
  porcentaje: number;
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