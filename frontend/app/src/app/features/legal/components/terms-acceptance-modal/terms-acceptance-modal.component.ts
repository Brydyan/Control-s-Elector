import { Component, EventEmitter, Input, Output, inject } from '@angular/core';
import { Dialog } from 'primeng/dialog';
import { Message } from 'primeng/message';

import { ButtonComponent } from '../../../../shared/ui/button/button.component';
import { TermsAcceptanceService } from '../../services/terms-acceptance.service';
import { describirErrorLegal } from '../../services/legal.service';

/**
 * Modal de aceptacion de terminos (T-LEGAL-01.7).
 *
 * <p>Es la pantalla que el usuario no puede saltarse: no hay boton de "declinar",
 * porque declinar significaria seguir usando el sistema sin haber aceptado el
 * documento vigente. Lo que si existe es cerrar, y cerrar <b>cierra la sesion</b>
 * (spec de privacidad, escenario 4): si no quiere aceptar, no entra.
 *
 * <p>La X de la barra y la tecla Escape son la unica salida sin aceptar, y las
 * dos disparan `cerrado`: quien lo escucha cierra la sesion y devuelve al
 * login. Cerrar el modal y quedarse adentro no es una opcion — por eso el
 * boton de la X no es un "rechazar": el efecto derejectar es salir del
 * sistema.
 *
 * <p>Los enlaces a `/terms` y `/privacy` apuntan a los documentos que sirve el
 * backend: en produccion los enruta el nginx, en dev el proxy de Vite.
 */
@Component({
  selector: 'app-terms-acceptance-modal',
  standalone: true,
  imports: [Dialog, Message, ButtonComponent],
  template: `
    <p-dialog
      [visible]="visible"
      (visibleChange)="onVisibleChange($event)"
      [modal]="true"
      [draggable]="false"
      [resizable]="false"
      [closable]="true"
      [closeOnEscape]="true"
      [dismissableMask]="false"
      [style]="{ width: 'min(32rem, 94vw)' }"
      header="Términos de Uso Actualizados"
      data-testid="terms-acceptance-modal"
    >
      <p class="aviso" data-testid="terms-acceptance-summary">
        Para seguir usando el sistema necesitas leer y aceptar la versión
        {{ version ? 'v' + version : 'vigente' }} de los Términos de Uso y de la
        Política de Privacidad.
      </p>

      <ul class="enlaces">
        <li>
          <a
            href="/terms"
            target="_blank"
            rel="noopener"
            data-testid="link-terms"
          >
            Ver los Términos de Uso completos
          </a>
        </li>
        <li>
          <a
            href="/privacy"
            target="_blank"
            rel="noopener"
            data-testid="link-privacy"
          >
            Ver la Política de Privacidad
          </a>
        </li>
      </ul>

      @if (errorMessage) {
        <p-message
          class="error"
          severity="error"
          [text]="errorMessage!"
          data-testid="terms-acceptance-error"
        />
      }

      <ng-template #footer>
        <app-button
          label="Acepto"
          [loading]="enviando"
          (clicked)="onAceptar()"
          data-testid="terms-accept"
        ></app-button>
      </ng-template>
    </p-dialog>
  `,
  styles: `
    .aviso {
      margin: 0 0 1rem;
      color: var(--color-text-muted-on-surface);
    }
    .enlaces {
      margin: 0 0 1rem;
      padding-left: 1.25rem;
    }
    .enlaces li {
      margin-bottom: 0.5rem;
    }
    .enlaces a {
      color: var(--color-brand-primary);
    }
    .error {
      margin-top: 0.5rem;
    }
  `,
})
export class TermsAcceptanceModalComponent {
  private readonly terms = inject(TermsAcceptanceService);

  @Input() visible = true;

  /** Version vigente de los documentos; se muestra en el texto. */
  @Input() version: string | null = null;

  /** El usuario acepto: se puede cerrar el modal y seguir. */
  @Output() accepted = new EventEmitter<void>();

  /** El usuario cerro el modal sin aceptar: hay que cerrar la sesion. */
  @Output() closed = new EventEmitter<void>();

  enviando = false;
  errorMessage: string | null = null;

  /**
   * Registra la aceptacion.
   *
   * Si el backend rechaza (red caída, sesion vencida) el modal sigue abierto
   * con el error a la vista: aceptar a medias es peor que mostrar que no se
   * pudo.
   */
  onAceptar(): void {
    if (this.enviando) {
      return;
    }
    this.enviando = true;
    this.errorMessage = null;

    this.terms.aceptar().subscribe({
      next: () => {
        this.enviando = false;
        this.accepted.emit();
      },
      error: (err: unknown) => {
        this.enviando = false;
        this.errorMessage = describirErrorLegal(err).mensaje;
      },
    });
  }

  /**
   * Cierre del modal sin aceptar.
   *
   * Se ignora el `false` que emite el dialog cuando se cierra porque el propio
   * componente acaba de aceptar: en ese caso ya se emitio `accepted` y la
   * sesion debe seguir viva.
   */
  onVisibleChange(next: boolean): void {
    this.visible = next;
    if (!next) {
      this.closed.emit();
    }
  }
}
