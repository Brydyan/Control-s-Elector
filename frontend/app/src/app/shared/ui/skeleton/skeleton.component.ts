import { CommonModule } from '@angular/common';
import { Component, Input } from '@angular/core';

export type SkeletonVariant = 'text' | 'rect' | 'circle';

/**
 * Local loading placeholder with an optional projected loading boundary.
 *
 * When `loadingBoundary` is enabled, projected content is rendered only while
 * `loading` is true. Otherwise, the component renders its built-in placeholder.
 */
@Component({
  selector: 'app-skeleton',
  standalone: true,
  imports: [CommonModule],
  template: `
    <ng-container *ngIf="loading && isLoadingBoundary">
      <ng-content></ng-content>
    </ng-container>
    <ng-container *ngIf="loading && !isLoadingBoundary">
      <ng-container *ngIf="variant === 'text' && lines > 1; else single">
        <span
          *ngFor="let line of lineArray; let last = last"
          class="skeleton skeleton--text"
          [style.width]="last ? '60%' : '100%'"
          [style.height]="height"
          [class.skeleton--no-animation]="animate === 'none'"
          [class.skeleton--transition]="transition"
          aria-hidden="true"
        ></span>
      </ng-container>
      <ng-template #single>
        <span
          class="skeleton"
          [class.skeleton--rect]="variant === 'rect'"
          [class.skeleton--circle]="variant === 'circle'"
          [style.width]="width"
          [style.height]="height"
          [class.skeleton--no-animation]="animate === 'none'"
          [class.skeleton--transition]="transition"
          aria-hidden="true"
        ></span>
      </ng-template>
    </ng-container>
  `,
  styleUrls: ['./skeleton.component.scss'],
  host: {
    '[class.skeleton--no-animation]': "animate === 'none'",
    '[class.skeleton--transition]': 'transition',
  },
})
export class SkeletonComponent {
  @Input() loading = true;
  @Input() name = '';
  @Input() animate: 'shimmer' | 'none' = 'shimmer';
  @Input() transition = true;
  @Input() variant: SkeletonVariant = 'rect';
  @Input() lines = 1;
  @Input() width = '100%';
  @Input() height = '1em';

  /** Render projected loading markup instead of the built-in placeholder. */
  @Input() loadingBoundary = false;

  /** @deprecated Use loadingBoundary. Kept for compatibility with existing consumers. */
  @Input() hasProjectedContent = false;

  get isLoadingBoundary(): boolean {
    return this.loadingBoundary || this.hasProjectedContent;
  }

  get lineArray(): unknown[] {
    return Array.from({ length: Math.max(1, this.lines) });
  }
}
