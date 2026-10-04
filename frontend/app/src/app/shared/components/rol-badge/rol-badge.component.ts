import { Component, Input } from '@angular/core';
import { BadgeComponent, BadgeVariant } from '../../ui/badge/badge.component';

/**
 * T-FE-12 S2: badge específico para roles de usuario.
 * Wrapper sobre BadgeComponent.
 */
@Component({
  selector: 'app-rol-badge',
  standalone: true,
  imports: [BadgeComponent],
  template: `
    <app-badge [variant]="variant">
      <ng-content></ng-content>
    </app-badge>
  `,
})
export class RolBadgeComponent {
  @Input() rol:
    | 'OBSERVER'
    | 'COORDINATOR'
    | 'ADMIN'
    | 'MASTER' = 'OBSERVER';

  get variant(): BadgeVariant {
    // Mapeo simple — roles fuera del enum variante usan 'default'.
    switch (this.rol) {
      case 'COORDINATOR':
        return 'CORREGIDA';
      case 'MASTER':
        return 'APROBADA';
      case 'ADMIN':
        return 'ENVIADA';
      default:
        return 'default';
    }
  }
}