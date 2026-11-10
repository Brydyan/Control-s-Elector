import { CommonModule } from '@angular/common';
import { Component, inject } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { EmptyStateComponent } from '../../shared/ui/empty-state/empty-state.component';

@Component({
  selector: 'app-placeholder',
  standalone: true,
  imports: [CommonModule, RouterLink, EmptyStateComponent],
  template: `
    <div class="placeholder-container">
      <app-empty-state
        [icon]="icon"
        [title]="title"
        [description]="description"
      >
        <div class="actions">
          <a routerLink="/dashboard" class="btn-back">Volver al Dashboard</a>
        </div>
      </app-empty-state>
    </div>
  `,
  styles: [
    `
      .placeholder-container {
        display: flex;
        align-items: center;
        justify-content: center;
        min-height: 60vh;
        padding: 2rem;
      }
      .actions {
        margin-top: 1.5rem;
      }
      .btn-back {
        display: inline-block;
        padding: 0.625rem 1.25rem;
        background: var(--color-brand-primary);
        color: var(--color-text-on-brand);
        text-decoration: none;
        border-radius: 0.5rem;
        font-weight: 500;
        font-size: 0.875rem;
        transition: background 0.2s ease;
      }
      .btn-back:hover {
        background: var(--color-brand-primary-strong);
      }
    `,
  ],
})
export class PlaceholderPage {
  private readonly route = inject(ActivatedRoute);

  readonly title =
    (this.route.snapshot.data['title'] as string) || 'Módulo no disponible';
  readonly description =
    (this.route.snapshot.data['description'] as string) ||
    'Esta funcionalidad aún no está disponible. Por favor, verifica más tarde.';
  readonly icon =
    (this.route.snapshot.data['icon'] as string) || 'info-circle-outline';
}
