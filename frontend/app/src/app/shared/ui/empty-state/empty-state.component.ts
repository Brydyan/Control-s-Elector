import { Component, Input } from '@angular/core';
import { toPrimeIcon } from '../icon-map';

/**
 * Empty state shown when a list has no results.
 *
 * PrimeNG has no empty-state component, so the block keeps its own markup and
 * is styled with the PrimeNG theme tokens. The icon is translated from the
 * Ionic name to its PrimeIcons class.
 */
@Component({
  selector: 'app-empty-state',
  standalone: true,
  template: `
    <div class="empty-state">
      @if (icon) {
        <div class="empty-state__icon-wrapper">
          <i
            [class]="iconClass"
            class="empty-state__icon"
            aria-hidden="true"
          ></i>
        </div>
      }
      @if (title) {
        <h3 class="empty-state__title">{{ title }}</h3>
      }
      @if (description) {
        <p class="empty-state__description">{{ description }}</p>
      }
      <div class="empty-state__actions">
        <ng-content select="[empty-actions]"></ng-content>
        <ng-content></ng-content>
      </div>
    </div>
  `,
  styleUrls: ['./empty-state.component.scss'],
})
export class EmptyStateComponent {
  @Input() icon?: string;
  @Input() title = 'No hay datos';
  @Input() description?: string;

  get iconClass(): string {
    return toPrimeIcon(this.icon);
  }
}
