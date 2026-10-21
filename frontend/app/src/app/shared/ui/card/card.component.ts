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

  /** `p-card` has no bordered/elevated switch; the shadow is applied here. */
  get styleClass(): string {
    return this.elevated ? 'app-card--elevated' : '';
  }
}
