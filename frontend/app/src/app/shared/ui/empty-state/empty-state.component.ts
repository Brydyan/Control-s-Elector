import { CommonModule } from '@angular/common';
import { Component, Input } from '@angular/core';

@Component({
  selector: 'app-empty-state',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="empty-state">
      <div class="empty-state__icon-wrapper" *ngIf="icon">
        <ion-icon [name]="icon" class="empty-state__icon"></ion-icon>
      </div>
      <h3 class="empty-state__title" *ngIf="title">{{ title }}</h3>
      <p class="empty-state__description" *ngIf="description">{{ description }}</p>
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
}
