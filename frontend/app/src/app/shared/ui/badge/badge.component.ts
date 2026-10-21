import { Component, Input } from '@angular/core';
import { Tag } from 'primeng/tag';

export type BadgeVariant =
  | 'ENVIADA'
  | 'APROBADA'
  | 'EN_DISPUTA'
  | 'CORREGIDA'
  | 'BORRADOR'
  | 'ANULADA'
  | 'default';

/**
 * Badge primitive backed by PrimeNG.
 *
 * The electoral states keep their meaning through the PrimeNG severity scale.
 * `CORREGIDA` has no PrimeNG equivalent, so it keeps its institutional purple
 * through a scoped override instead of collapsing into the neutral severity.
 */
@Component({
  selector: 'app-badge',
  standalone: true,
  imports: [Tag],
  template: `
    <p-tag [severity]="severity" [styleClass]="styleClass" [rounded]="true">
      <ng-content></ng-content>
    </p-tag>
  `,
  styles: `
    :host ::ng-deep .badge-CORREGIDA {
      background: var(--primitive-purple-50);
      color: var(--primitive-purple-800);
    }
  `,
})
export class BadgeComponent {
  @Input() variant: BadgeVariant = 'default';

  get severity():
    | 'success'
    | 'info'
    | 'warn'
    | 'danger'
    | 'secondary'
    | undefined {
    switch (this.variant) {
      case 'APROBADA':
        return 'success';
      case 'ENVIADA':
        return 'info';
      case 'EN_DISPUTA':
        return 'warn';
      case 'ANULADA':
        return 'danger';
      case 'CORREGIDA':
        return 'secondary';
      default:
        return 'secondary';
    }
  }

  /** Keeps the institutional palette for the states PrimeNG does not cover. */
  get styleClass(): string {
    return this.variant === 'CORREGIDA' ? 'badge-CORREGIDA' : '';
  }
}
