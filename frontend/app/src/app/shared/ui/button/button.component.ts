import { Component, EventEmitter, Input, Output } from '@angular/core';
import { Button } from 'primeng/button';

export type ButtonVariant = 'primary' | 'secondary' | 'danger' | 'ghost';
export type ButtonSize = 'sm' | 'md' | 'lg';

/**
 * Button primitive backed by PrimeNG.
 *
 * The public API is unchanged so the existing pages keep working; only the
 * rendering moves to `p-button`, which brings the PrimeNG severity scale and
 * the institutional theme tokens.
 */
@Component({
  selector: 'app-button',
  standalone: true,
  imports: [Button],
  templateUrl: './button.component.html',
})
export class ButtonComponent {
  @Input() variant: ButtonVariant = 'primary';
  @Input() size: ButtonSize = 'md';
  @Input() type: 'button' | 'submit' | 'reset' = 'button';
  @Input() disabled = false;
  @Input() loading = false;
  @Input() block = false;

  @Output() clicked = new EventEmitter<MouseEvent>();

  /** `p-button` only accepts `small` and `large`; the default size is unset. */
  get primeSize(): 'small' | 'large' | undefined {
    if (this.size === 'sm') return 'small';
    if (this.size === 'lg') return 'large';
    return undefined;
  }

  /** Ghost buttons are the text variant; secondary keeps the outlined look. */
  get isGhost(): boolean {
    return this.variant === 'ghost';
  }

  get isSecondary(): boolean {
    return this.variant === 'secondary';
  }

  get severity(): 'secondary' | 'danger' | undefined {
    if (this.variant === 'secondary') return 'secondary';
    if (this.variant === 'danger') return 'danger';
    return undefined;
  }

  onClick(event: MouseEvent): void {
    if (!this.disabled && !this.loading) {
      this.clicked.emit(event);
    }
  }
}
