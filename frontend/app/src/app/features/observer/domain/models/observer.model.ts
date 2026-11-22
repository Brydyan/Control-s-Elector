export type EstadoActa =
  'BORRADOR' | 'ENVIADA' | 'APROBADA' | 'EN_DISPUTA' | 'CORREGIDA' | 'ANULADA';

export interface JrvConEstado {
  jrvId: number;
  numero: string;
  parroquia: string | null;
  cargosPendientes: number;
  cargosRegistrados: number;
}

export interface CargoConEstado {
  cargoId: number;
  nombre: string;
  nivel: string;
  registrado: boolean;
  estadoActa?: EstadoActa;
  actaId?: number;
}

export interface CandidatoInfo {
  id: number;
  nombre: string;
  orden: number;
  cargoId: number;
  partidoId: number;
  partidoNombre?: string;
  partidoSiglas?: string;
  partidoNumeroLista?: number;
}

/**
 * Catalog of a JRV cargo as returned by the batch endpoint:
 * GET /observer/cargos-with-candidatos?jrvId=
 * Candidatos arrive enriched with partido info on the client.
 */
export interface CargoCatalogo {
  id: number;
  nombre: string;
  candidatos: CandidatoInfo[];
}

export interface ActaDetalleItem {
  candidatoId: number;
  votos: number;
  candidatoNombre?: string;
  partidoSiglas?: string;
}

export interface ActaRequest {
  jrvId: number;
  cargoId: number;
  totalSufragantes: number;
  votosBlancos: number;
  votosNulos: number;
  detalles: ActaDetalleItem[];
}

export interface ActaResponse {
  id: number;
  jrvId: number;
  cargoId: number;
  estado: EstadoActa;
  totalSufragantes: number;
  votosBlancos: number;
  votosNulos: number;
  motivoDisputa?: string;
  coordinadorNombre?: string;
  urlEvidencia?: string | null;
  detalles: Array<{
    id: number;
    candidatoId: number;
    votos: number;
  }>;
}
