import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { toPrimeIcon } from '../icon-map';

type HeadingLevel = 'h1' | 'h2' | 'h3' | 'h4' | 'h5' | 'h6';

/**
 * Empty state shown when a list has no results.
 *
 * PrimeNG has no empty-state component, so the block keeps its own markup and
 * is styled with the PrimeNG theme tokens. The icon is translated from the
 * Ionic name to its PrimeIcons class.
 *
 * T8.S14: The heading level is configurable via headingLevel input, defaulting
 * to 'h2' to maintain semantic hierarchy.
 */
@Component({
  selector: 'app-empty-state',
  standalone: true,
  imports: [CommonModule],
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
        <ng-container [ngSwitch]="headingLevel">
          <h1 *ngSwitchCase="'h1'" class="empty-state__title">
            {{ title }}
          </h1>
          <h2 *ngSwitchCase="'h2'" class="empty-state__title">
            {{ title }}
          </h2>
          <h3 *ngSwitchCase="'h3'" class="empty-state__title">
            {{ title }}
          </h3>
          <h4 *ngSwitchCase="'h4'" class="empty-state__title">
            {{ title }}
          </h4>
          <h5 *ngSwitchCase="'h5'" class="empty-state__title">
            {{ title }}
          </h5>
          <h6 *ngSwitchCase="'h6'" class="empty-state__title">
            {{ title }}
          </h6>
        </ng-container>
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
  @Input() headingLevel: HeadingLevel = 'h2';

  get iconClass(): string {
    return toPrimeIcon(this.icon);
  }
}
