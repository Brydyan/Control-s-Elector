import { Component, Input } from '@angular/core';
import { Skeleton } from 'primeng/skeleton';

export type SkeletonVariant = 'text' | 'rect' | 'circle';

/**
 * Loading placeholder backed by PrimeNG, with an optional projected loading
 * boundary.
 *
 * When `loadingBoundary` is enabled, projected content is rendered only while
 * `loading` is true. Otherwise, the component renders its built-in placeholder.
 */
@Component({
  selector: 'app-skeleton',
  standalone: true,
  imports: [Skeleton],
  template: `
    @if (loading && isLoadingBoundary) {
      <ng-content></ng-content>
    }
    @if (loading && !isLoadingBoundary) {
      @if (variant === 'text' && lines > 1) {
        @for (line of lineArray; track $index; let last = $last) {
          <p-skeleton
            [shape]="shape"
            [width]="last ? '60%' : '100%'"
            [height]="height"
            [animation]="animation"
          />
        }
      } @else {
        <p-skeleton
          [shape]="shape"
          [width]="width"
          [height]="height"
          [animation]="animation"
        />
      }
    }
  `,
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

  /** PrimeNG only special-cases the circle shape; text renders as a rect. */
  get shape(): string {
    return this.variant === 'circle' ? 'circle' : 'rect';
  }

  /** PrimeNG implements the shimmer as the `wave` animation. */
  get animation(): string {
    return this.animate === 'none' ? 'none' : 'wave';
  }

  get lineArray(): unknown[] {
    return Array.from({ length: Math.max(1, this.lines) });
  }
}
