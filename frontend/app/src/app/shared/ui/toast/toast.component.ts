import { Component } from '@angular/core';
import { Toast } from 'primeng/toast';

/**
 * El toast de la app (ODD 505, A.1).
 *
 * <p>Es la superficie de feedback que faltaba: hasta ahora cada acción
 * escribía su propio mensaje (una barra en la página, un modal) y no había una
 * forma común de avisar de que algo pasó. Este componente envuelve el toast de
 * PrimeNG y le pone el lenguaje del sistema: mismo radio, misma sombra, mismo
 * ancho.
 *
 * <p>Los mensajes los encola el {@link ToastService}, que habla con el
 * `MessageService` de PrimeNG: así el toast vive en el root de la app y
 * funciona desde cualquier pantalla sin que cada una tenga que registrarlo.
 *
 * <p>Accesibilidad: el toast de PrimeNG ya publica los mensajes con
 * `role="status"` (o `alert` para los errores) y región viva, que es lo que
 * necesita un lector de pantalla para anunciar el cambio.
 */
@Component({
  selector: 'app-toast',
  standalone: true,
  imports: [Toast],
  template: `<p-toast position="top-right" />`,
  styleUrl: './toast.component.scss',
})
export class ToastComponent {}
