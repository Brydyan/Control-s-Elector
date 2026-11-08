import { Component, Input } from '@angular/core';

/**
 * The tones of the electoral board.
 *
 * A tone picks two colors of the card: the edge that crowns it and the figure
 * it carries. The meaning is the one the board already had —an acta sent, in
 * dispute, corrected or approved— so a page says which tone it is and the
 * theme says which color that is.
 */
export type KpiTone = 'brand' | 'success' | 'warning' | 'corrected' | 'neutral';

/**
 * The card of a single indicator.
 *
 * The dashboard, the cargo results and the coordinator report each had their
 * own markup for the same thing —a label, a figure and a line of support— and
 * each one chose its colors by hand. Here the three are the same in all of
 * them, and the colors are the tokens of the theme.
 *
 * The card is a `div` and not a `p-card` on purpose: `base.scss` pins the
 * border of `p-card` with `!important` to win the cascade against the styled
 * engine, and that pin also pins the border of the edge that tells the tone
 * of the card. A card that has to say something owns its own border.
 */
@Component({
  selector: 'app-kpi-card',
  standalone: true,
  templateUrl: './kpi-card.component.html',
  styleUrl: './kpi-card.component.scss',
})
export class KpiCardComponent {
  /** The name of the indicator. */
  @Input() label = '';

  /** The figure: a quantity or a percentage, written as it arrives. */
  @Input() value: string | number = 0;

  /** The line of support under the figure. */
  @Input() hint?: string;

  /** A PrimeIcons class list, such as `pi pi-check`. */
  @Input() icon?: string;

  /** The tone of the board the indicator belongs to. */
  @Input() tone: KpiTone = 'brand';
}
