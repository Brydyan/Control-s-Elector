import { EstadoActa } from '../../observer/models/observer.model';

export type { EstadoActa };

export interface ActaItem {
  id: number;
  jrvId: number;
  cargoId: number;
  registradaPorId?: number;
  estado: EstadoActa;
  storageUrl?: string | null;
  totalSufragantes: number;
  votosBlancos: number;
  votosNulos: number;
  fechaRegistro: string;
  motivoAnulacion?: string | null;
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

export interface ActaDetalleCompleto {
  acta: ActaItem;
  jrv?: JrvResumen;
  cargo?: CargoResumen;
  candidatos: CandidatoResumen[];
  totalVotosCalculado: number;
  diferenciaChecksum: number;
  checksumValido: boolean;
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

export interface ActaFiltros {
  estado?: EstadoActa | 'TODAS';
  jrvId?: number;
  cargoId?: number;
  busqueda?: string;
}
