import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Card } from 'primeng/card';

type HeadingLevel = 'h1' | 'h2' | 'h3' | 'h4' | 'h5' | 'h6';

/**
 * Card primitive backed by PrimeNG.
 *
 * The header keeps the title, the subtitle and the action slot together, and
 * the footer keeps its own slot, so the existing pages do not change.
 *
 * T8.S14: The heading level is configurable via headingLevel input, defaulting
 * to 'h3' for card titles. Most cards are sub-sections within a page.
 */
@Component({
  selector: 'app-card',
  standalone: true,
  imports: [CommonModule, Card],
  templateUrl: './card.component.html',
})
export class CardComponent {
  @Input() headerTitle?: string;
  @Input() subtitle?: string;
  @Input() bordered = true;
  @Input() elevated = false;
  @Input() headingLevel: HeadingLevel = 'h3';

  /** Extra classes for the PrimeNG card root, used by the feature pages. */
  @Input() styleClass = '';

  /** `p-card` has no bordered/elevated switch; the shadow is applied here. */
  get cardStyleClass(): string {
    return [this.styleClass, this.elevated ? 'app-card--elevated' : '']
      .filter(Boolean)
      .join(' ');
  }
}
