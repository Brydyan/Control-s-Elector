import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, Output } from '@angular/core';

/**
 * T-FE-12 S4: campanita de notificaciones (stub).
 *
 * Diseño D4: el contador es `@Input`, no se calcula desde API en MVP.
 * Futuras iteraciones pueden hidratar `count` desde un endpoint
 * `GET /api/v1/me/notificaciones`.
 */
@Component({
  selector: 'app-notificacion-bell',
  standalone: true,
  imports: [CommonModule],
  template: `
    <button
      type="button"
      class="bell-button"
      [attr.aria-label]="ariaLabel"
      (click)="bellClick.emit()"
      data-testid="bell-button"
    >
      <span class="bell-icon" aria-hidden="true">🔔</span>
      @if (count > 0) {
      <span class="bell-badge" data-testid="bell-count">{{ count }}</span>
      }
    </button>
  `,
  styles: [
    `
      .bell-button {
        position: relative;
        background: transparent;
        border: 0;
        cursor: pointer;
        font-size: 1.4rem;
      }
      .bell-icon {
        display: inline-block;
      }
      .bell-badge {
        position: absolute;
        top: -4px;
        right: -8px;
        background: #c0392b;
        color: #fff;
        font-size: 0.75rem;
        border-radius: 10px;
        padding: 2px 6px;
        font-weight: 700;
      }
    `,
  ],
})
export class NotificacionBellComponent {
  @Input() count = 0;
  @Input() ariaLabel = `${this.count} notificaciones pendientes`;

  @Output() bellClick = new EventEmitter<void>();
}