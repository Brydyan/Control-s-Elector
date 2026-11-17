import {
  Component,
  EventEmitter,
  Input,
  Output,
  computed,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { Button } from 'primeng/button';

type HeadingLevel = 'h1' | 'h2' | 'h3' | 'h4' | 'h5' | 'h6';

/**
 * Full-page error state.
 *
 * PrimeNG's `p-message` is an inline banner, not a page-level block, so the
 * layout stays local while the retry action moves to `p-button` and the
 * colours come from the PrimeNG theme tokens.
 *
 * T8.S14: The heading level is configurable via headingLevel input, defaulting
 * to 'h2' to maintain semantic hierarchy.
 */
@Component({
  selector: 'app-error-state',
  standalone: true,
  imports: [CommonModule, Button],
  template: `
    <div class="error-state" role="alert">
      <div class="error-state__icon-wrapper">
        <i
          class="pi pi-exclamation-circle error-state__icon"
          aria-hidden="true"
        ></i>
      </div>
      <ng-container [ngSwitch]="headingLevel">
        <h1 *ngSwitchCase="'h1'" class="error-state__title">
          {{ title }}
        </h1>
        <h2 *ngSwitchCase="'h2'" class="error-state__title">
          {{ title }}
        </h2>
        <h3 *ngSwitchCase="'h3'" class="error-state__title">
          {{ title }}
        </h3>
        <h4 *ngSwitchCase="'h4'" class="error-state__title">
          {{ title }}
        </h4>
        <h5 *ngSwitchCase="'h5'" class="error-state__title">
          {{ title }}
        </h5>
        <h6 *ngSwitchCase="'h6'" class="error-state__title">
          {{ title }}
        </h6>
      </ng-container>
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
  @Input() headingLevel: HeadingLevel = 'h2';

  @Output() retry = new EventEmitter<void>();
}
