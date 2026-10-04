import { Component, Input } from '@angular/core';
import { BadgeComponent, BadgeVariant } from '../../ui/badge/badge.component';

/**
 * T-FE-12 S1: badge específico para estados electorales.
 * Wrapper sobre BadgeComponent para ergonomía de import.
 */
@Component({
  selector: 'app-estado-badge',
  standalone: true,
  imports: [BadgeComponent],
  template: `
    <app-badge [variant]="variant">
      <ng-content></ng-content>
    </app-badge>
  `,
})
export class EstadoBadgeComponent {
  @Input() estado:
    | 'BORRADOR'
    | 'ENVIADA'
    | 'EN_DISPUTA'
    | 'CORREGIDA'
    | 'APROBADA'
    | 'ANULADA' = 'ENVIADA';

  get variant(): BadgeVariant {
    return this.estado as BadgeVariant;
  }
}