import { CommonModule } from '@angular/common';
import { Component, Input } from '@angular/core';

@Component({
  selector: 'app-loading-state',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="loading-state" [class.loading-state--overlay]="overlay">
      <div class="loading-state__spinner" role="status" aria-live="polite">
        <span class="loading-state__sr-text">Cargando...</span>
      </div>
      <p class="loading-state__message" *ngIf="message">{{ message }}</p>
    </div>
  `,
  styleUrls: ['./loading-state.component.scss'],
})
export class LoadingStateComponent {
  @Input() message = 'Cargando información...';
  @Input() overlay = false;
}
