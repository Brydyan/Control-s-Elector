import { CommonModule } from '@angular/common';
import { Component, Input } from '@angular/core';

export type BadgeVariant =
  | 'ENVIADA'
  | 'APROBADA'
  | 'EN_DISPUTA'
  | 'CORREGIDA'
  | 'BORRADOR'
  | 'ANULADA'
  | 'default';

@Component({
  selector: 'app-badge',
  standalone: true,
  imports: [CommonModule],
  template: `
    <span class="badge" [ngClass]="'badge-' + variant">
      <ng-content></ng-content>
    </span>
  `,
  styleUrls: ['./badge.component.scss'],
})
export class BadgeComponent {
  @Input() variant: BadgeVariant = 'default';
}
