import { HttpErrorResponse } from '@angular/common/http';

/**
 * Tipos de error normalizados que el frontend puede distinguir y mostrar/recargar
 * de forma diferente. El backend nunca debe mandar mensajes crípticos — pero
 * si lo hace, preferimos categorizar antes que mostrar 'undefined'.
 */
export type AppErrorType =
  | 'conflict' // 409 — la entidad cambió de estado entre lecturas
  | 'forbidden' // 403 — RBAC
  | 'unauthorized' // 401 — sesión expirada o ausente
  | 'validation' // 400 — payload inválido
  | 'not-found' // 404
  | 'server' // 5xx
  | 'network' // 0 / error de red sin respuesta
  | 'unknown'; // fallback

export interface AppError {
  type: AppErrorType;
  message: string;
  status: number | null;
  retriable: boolean;
}

const DEFAULT_MESSAGES: Record<AppErrorType, string> = {
  conflict:
    'El acta cambió de estado mientras se procesaba la solicitud. Refrescá para ver la versión actualizada.',
  forbidden: 'No tenés permisos para realizar esta acción.',
  unauthorized: 'Tu sesión expiró. Volvé a iniciar sesión.',
  validation: 'Los datos enviados no son válidos.',
  'not-found': 'El recurso solicitado no existe.',
  server:
    'El servidor tuvo un error inesperado. Intentá nuevamente en unos segundos.',
  network: 'No se pudo contactar al servidor. Revisá tu conexión.',
  unknown: 'Ocurrió un error inesperado.',
};

/**
 * Normaliza cualquier error (HTTP, de red, de runtime) a un {@link AppError}
 * con tipo, mensaje legible y flag de retry.
 */
export function mapError(err: unknown): AppError {
  if (err instanceof HttpErrorResponse) {
    const status = err.status;
    const backendMessage = readBackendMessage(err);

    if (status === 0) {
      return {
        type: 'network',
        message: backendMessage ?? DEFAULT_MESSAGES['network'],
        status,
        retriable: true,
      };
    }
    if (status === 401) {
      return {
        type: 'unauthorized',
        message: backendMessage ?? DEFAULT_MESSAGES['unauthorized'],
        status,
        retriable: false,
      };
    }
    if (status === 403) {
      return {
        type: 'forbidden',
        message: backendMessage ?? DEFAULT_MESSAGES['forbidden'],
        status,
        retriable: false,
      };
    }
    if (status === 404) {
      return {
        type: 'not-found',
        message: backendMessage ?? DEFAULT_MESSAGES['not-found'],
        status,
        retriable: false,
      };
    }
    if (status === 400) {
      return {
        type: 'validation',
        message: backendMessage ?? DEFAULT_MESSAGES['validation'],
        status,
        retriable: false,
      };
    }
    if (status === 409) {
      return {
        type: 'conflict',
        message: backendMessage ?? DEFAULT_MESSAGES['conflict'],
        status,
        retriable: false,
      };
    }
    if (status >= 500) {
      return {
        type: 'server',
        message: backendMessage ?? DEFAULT_MESSAGES['server'],
        status,
        retriable: true,
      };
    }
    return {
      type: 'unknown',
      message: backendMessage ?? DEFAULT_MESSAGES['unknown'],
      status,
      retriable: false,
    };
  }

  if (err instanceof Error) {
    return {
      type: 'unknown',
      message: err.message,
      status: null,
      retriable: false,
    };
  }

  return {
    type: 'unknown',
    message: DEFAULT_MESSAGES['unknown'],
    status: null,
    retriable: false,
  };
}

/**
 * Lee el mensaje de error del backend desde diferentes formas de payload.
 * Spring suele mandar `{ message, error, status, path }` o `{ message: string }`.
 */
function readBackendMessage(err: HttpErrorResponse): string | null {
  const body: unknown = err.error;
  if (!body || typeof body !== 'object') return null;
  const obj = body as Record<string, unknown>;
  const candidate = obj['message'] ?? obj['error'] ?? obj['detail'];
  if (typeof candidate === 'string' && candidate.trim().length > 0) {
    return candidate;
  }
  return null;
}
