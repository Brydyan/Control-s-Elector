/**
 * DTOs del bloque legal. Reflejan 1:1 el contrato del backend
 * (ec.twintailcs.electoral.legal.web). Los nombres de los campos son los del
 * contrato: no se traducen al pasar por esta capa.
 */

/**
 * Version vigente de los documentos legales.
 *
 * `version` es la version vigente del bloque completo. `terms_version` y
 * `privacy_version` vienen por separado porque terminos y politica se pueden
 * versionar por separado sin que el frontend tenga que suponer que son la
 * misma.
 */
export interface LegalVersionDto {
  version: string;
  terms_version?: string;
  privacy_version?: string;
}

/** Respuesta del registro de aceptacion de terminos. */
export interface AcceptTermsResponseDto {
  status: string;
  version: string;
  accepted_at: string | null;
}

/** Datos del formulario de contacto con el DPO (contrato dpo-contact). */
export interface DpoContactRequestDto {
  name: string;
  email: string;
  subject: string;
  message: string;
}

/** Confirmacion de recepcion de la solicitud al DPO. */
export interface DpoContactResponseDto {
  status: string;
  message: string;
}

/** Datos personales incluidos en la exportacion (GDPR Art. 15). */
export interface ExportUserDto {
  id: number;
  public_id: string;
  email: string;
  rol: string;
  first_name: string | null;
  last_name: string | null;
  created_at: string | null;
  accepted_terms_at: string | null;
  accepted_terms_version: string | null;
  accepted_privacy_at: string | null;
  accepted_privacy_version: string | null;
}

/** Acta registrada por el usuario, en la exportacion. */
export interface ExportActaDto {
  id: number;
  public_id: string;
  jrv_id: number;
  cargo_id: number;
  estado: string | null;
  total_sufragantes: number | null;
  votos_blancos: number | null;
  votos_nulos: number | null;
  fecha_registro: string | null;
}

/** Incidencia reportada por el usuario, en la exportacion. */
export interface ExportDisputeDto {
  id: number;
  jrv_id: number;
  tipo: string | null;
  descripcion: string | null;
  evidencia_url: string | null;
  fecha_registro: string | null;
}

/** Entrada del historial de exportaciones. */
export interface ExportHistoryDto {
  export_id: string;
  exported_at: string | null;
  record_count: number;
}

/**
 * Exportacion completa de los datos del usuario.
 *
 * Solo contiene datos propios del usuario que la pide: el backend arma la
 * consulta con el id del token, asi que no hay forma de exportar los datos de
 * otra persona.
 */
export interface ExportDataDto {
  exported_at: string;
  export_request_id: string;
  user: ExportUserDto;
  actas_registradas: ExportActaDto[];
  dispute_submissions: ExportDisputeDto[];
  exports_data: ExportHistoryDto[];
}
