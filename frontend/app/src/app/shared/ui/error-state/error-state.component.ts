import { Component, EventEmitter, Input, Output } from '@angular/core';
import { Button } from 'primeng/button';

/**
 * Full-page error state.
 *
 * PrimeNG's `p-message` is an inline banner, not a page-level block, so the
 * layout stays local while the retry action moves to `p-button` and the
 * colours come from the PrimeNG theme tokens.
 */
@Component({
  selector: 'app-error-state',
  standalone: true,
  imports: [Button],
  template: `
    <div class="error-state" role="alert">
      <div class="error-state__icon-wrapper">
        <i class="pi pi-exclamation-circle error-state__icon" aria-hidden="true"></i>
      </div>
      <h3 class="error-state__title">{{ title }}</h3>
      @if (message) {
        <p class="error-state__message">{{ message }}</p>
      }
      @if (showRetry) {
        <div class="error-state__actions">
          <p-button
            [label]="retryLabel"
            icon="pi pi-refresh"
            severity="secondary"
            [outlined]="true"
            (onClick)="retry.emit()"
          />
        </div>
      }
      <ng-content></ng-content>
    </div>
  `,
  styleUrls: ['./error-state.component.scss'],
})
export class ErrorStateComponent {
  @Input() title = 'Ocurrió un error';
  @Input() message?: string;
  @Input() showRetry = false;
  @Input() retryLabel = 'Reintentar';

  @Output() retry = new EventEmitter<void>();
}
