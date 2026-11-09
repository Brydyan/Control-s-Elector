import { Injectable, inject } from '@angular/core';
import { MessageService } from 'primeng/api';

/**
 * Cuánto vive un mensaje que se cierra solo (ms).
 *
 * <p>Está en el rango de 4 a 8 s que la casa usa para el feedback efímero: menos
 * de 4 s no alcanza a leerse y más de 8 s tapa el trabajo que sigue.
 */
export const TOAST_LIFE_MS = 5000;

/** Severidades que el sistema entiende. */
export type ToastSeverity = 'success' | 'info' | 'warn' | 'error';

/**
 * Un mensaje de feedback.
 *
 * <p>`life` es el tiempo que vive el mensaje en ms. Sin valor el mensaje se
 * queda hasta que la persona lo cierre: es lo que corresponde a un error y a un
 * aviso, que no se pueden perder de vista.
 */
export interface ToastMessage {
  severity: ToastSeverity;
  summary: string;
  detail?: string;
  life?: number;
}

/**
 * La puerta de entrada al feedback de la app (ODD 505, A.2).
 *
 * <p>Habla con el `MessageService` de PrimeNG, que vive en el root: el toast
 * está siempre montado y cualquier pantalla puede avisar sin registrar nada.
 * Un servicio que depende de que un componente se registre tiene una ventana en
 * la que el feedback se pierde en silencio, y eso es peor que no tener toast.
 */
@Injectable({ providedIn: 'root' })
export class ToastService {
  private readonly messages = inject(MessageService);

  /** Acción completada: el mensaje se cierra solo. */
  success(summary: string, detail?: string, life?: number): void {
    this.add({ severity: 'success', summary, detail, life });
  }

  /** Información que no requiere acción: el mensaje se cierra solo. */
  info(summary: string, detail?: string, life?: number): void {
    this.add({ severity: 'info', summary, detail, life });
  }

  /**
   * Aviso: se queda hasta que se cierre.
   *
   * <p>Un aviso que se va solo puede pasar desapercibido justo cuando la
   * persona está mirando otra cosa.
   */
  warn(summary: string, detail?: string): void {
    this.add({ severity: 'warn', summary, detail });
  }

  /** Error: se queda hasta que se cierre. */
  error(summary: string, detail?: string): void {
    this.add({ severity: 'error', summary, detail });
  }

  /** Encola un mensaje con la severidad que le corresponda. */
  add(message: ToastMessage): void {
    this.messages.add({
      severity: message.severity,
      summary: message.summary,
      detail: message.detail,
      life:
        message.life ??
        (message.severity === 'success' || message.severity === 'info'
          ? TOAST_LIFE_MS
          : undefined),
    });
  }

  /** Quita todos los mensajes en cola. */
  clear(): void {
    this.messages.clear();
  }
}
