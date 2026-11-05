import { Component, Input } from '@angular/core';
import { Card } from 'primeng/card';

/**
 * Card primitive backed by PrimeNG.
 *
 * The header keeps the title, the subtitle and the action slot together, and
 * the footer keeps its own slot, so the existing pages do not change.
 */
@Component({
  selector: 'app-card',
  standalone: true,
  imports: [Card],
  templateUrl: './card.component.html',
})
export class CardComponent {
  @Input() headerTitle?: string;
  @Input() subtitle?: string;
  @Input() bordered = true;
  @Input() elevated = false;

  /** Extra classes for the PrimeNG card root, used by the feature pages. */
  @Input() styleClass = '';

  /** `p-card` has no bordered/elevated switch; the shadow is applied here. */
  get cardStyleClass(): string {
    return [this.styleClass, this.elevated ? 'app-card--elevated' : '']
      .filter(Boolean)
      .join(' ');
  }
}
