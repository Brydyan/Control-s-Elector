import { Component, Input } from '@angular/core';
import { ProgressSpinner } from 'primeng/progressspinner';

/**
 * Loading indicator.
 *
 * The spinner comes from PrimeNG; the screen-reader text stays so the state
 * is still announced.
 */
@Component({
  selector: 'app-loading-state',
  standalone: true,
  imports: [ProgressSpinner],
  template: `
    <div class="loading-state" [class.loading-state--overlay]="overlay">
      <div class="loading-state__spinner" role="status" aria-live="polite">
        <p-progressspinner
          [style]="{ width: '2.5rem', height: '2.5rem' }"
          [ariaLabel]="'Cargando'"
        />
        <span class="loading-state__sr-text">Cargando...</span>
      </div>
      @if (message) {
        <p class="loading-state__message">{{ message }}</p>
      }
    </div>
  `,
  styleUrls: ['./loading-state.component.scss'],
})
export class LoadingStateComponent {
  @Input() message = 'Cargando información...';
  @Input() overlay = false;
}
