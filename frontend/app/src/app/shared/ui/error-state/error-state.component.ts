import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, Output } from '@angular/core';

@Component({
  selector: 'app-error-state',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="error-state" role="alert">
      <div class="error-state__icon-wrapper">
        <svg
          class="error-state__icon"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="2"
          stroke-linecap="round"
          stroke-linejoin="round"
          aria-hidden="true"
        >
          <circle cx="12" cy="12" r="10"></circle>
          <line x1="12" y1="8" x2="12" y2="12"></line>
          <line x1="12" y1="16" x2="12.01" y2="16"></line>
        </svg>
      </div>
      <h3 class="error-state__title">{{ title }}</h3>
      <p class="error-state__message" *ngIf="message">{{ message }}</p>
      <div class="error-state__actions" *ngIf="showRetry">
        <button type="button" class="error-state__retry-btn" (click)="retry.emit()">
          {{ retryLabel }}
        </button>
      </div>
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
