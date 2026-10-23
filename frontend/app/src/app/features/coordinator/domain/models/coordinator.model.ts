import type { SortDir } from '../../../shared/models/page.model';
import { EstadoActa } from '../../observer/models/observer.model';

export type { EstadoActa };

export interface ActaItem {
  id: number;
  jrvId: number;
  cargoId: number;
  registradaPorId?: number;
  estado: EstadoActa;
  /** T-FE-10 E.3: URL prefirmada S3 (reemplaza `storageUrl`, columna inexistente). */
  urlEvidencia?: string | null;
  totalSufragantes: number;
  votosBlancos: number;
  votosNulos: number;
  fechaRegistro: string;
  motivoAnulacion?: string | null;
  nombreCargo?: string | null;
  numeroJrv?: string | null;
  totalVotosCandidatos?: number | null;
  motivoDisputa?: string | null;
  /** T-FE-10 E.2: votos reales por candidato (devuelto por ActaDetalleCompletoDto). */
  detalles?: Array<{
    candidatoId: number;
    votos: number;
  }> | null;
}

export interface CandidatoResumen {
  id: number;
  nombre: string;
  orden: number;
  cargoId: number;
  partidoId: number;
  partidoNombre?: string;
  partidoSiglas?: string;
  partidoNumeroLista?: number;
  votos: number;
}

export interface JrvResumen {
  id: number;
  numero: string;
  parroquia: string | null;
  canton?: string | null;
  recinto?: string | null;
}

export interface CargoResumen {
  id: number;
  nombre: string;
  nivel: string;
}

export interface CantonResumen {
  id: number;
  nombre: string;
  codigo: string;
}

export interface HistorialEntry {
  id: number;
  accion: string;
  timestamp: string;
  registradoPorNombre?: string | null;
  registradoPorRol?: string | null;
  ipAddress?: string | null;
  detalle?: string | null;
}

export interface ActaDetalleCompleto {
  acta: ActaItem;
  jrv?: JrvResumen;
  cargo?: CargoResumen;
  candidatos: CandidatoResumen[];
  totalVotosCalculado: number;
  diferenciaChecksum: number;
  checksumValido: boolean;
  historial: HistorialEntry[];
}

export interface DisputarActaPayload {
  motivo: string;
}

export interface CorregirActaPayload {
  jrvId: number;
  cargoId: number;
  totalSufragantes: number;
  votosBlancos: number;
  votosNulos: number;
  detalles: Array<{
    candidatoId: number;
    votos: number;
  }>;
}

export const ACTA_SORT_FIELD = {
  FECHA_REGISTRO: 'fechaRegistro',
  ID: 'id',
  ESTADO: 'estado',
} as const;

export type ActaSortField =
  (typeof ACTA_SORT_FIELD)[keyof typeof ACTA_SORT_FIELD];

export interface ActaFiltros {
  estado?: EstadoActa | 'TODAS';
  jrvId?: number;
  cargoId?: number;
  dignidad?: number;
  cantonId?: number;
  busqueda?: string;
  search?: string;
  page?: number;
  size?: number;
  sort?: ActaSortField;
  dir?: SortDir;
}
