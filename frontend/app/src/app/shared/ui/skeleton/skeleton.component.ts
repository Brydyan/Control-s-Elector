import { CommonModule } from '@angular/common';
import { Component, Input } from '@angular/core';

export type SkeletonVariant = 'text' | 'rect' | 'circle';

/**
 * Placeholder animado para indicar carga sin spinners genéricos.
 *
 * <p>Uso típico:
 * <pre>
 *   <app-skeleton variant="rect" [width]="'100%'" [height]="'40px'"></app-skeleton>
 *   <app-skeleton variant="text" [lines]="3"></app-skeleton>
 *   <app-skeleton variant="circle" [width]="'48px'" [height]="'48px'"></app-skeleton>
 * </pre>
 *
 * <p>Las dimensiones se aceptan como string CSS (porcentaje, px, rem, etc.).
 * La variante `text` con \`lines > 1\` apila \`lines\` placeholders con un
 * 60% de ancho en la última línea para sugerir texto natural.
 *
 * <p>El shimmer se aplica con CSS animation \`@keyframes shimmer\` (definido en
 * el SCSS); respeta \`prefers-reduced-motion\` desactivando la animación.
 */
@Component({
  selector: 'app-skeleton',
  standalone: true,
  imports: [CommonModule],
  template: `
    <ng-container *ngIf="variant === 'text' && lines > 1; else single">
      <span
        *ngFor="let line of lineArray; let last = last"
        class="skeleton skeleton--text"
        [style.width]="last ? '60%' : '100%'"
        [style.height]="height"
      ></span>
    </ng-container>
    <ng-template #single>
      <span
        class="skeleton"
        [class.skeleton--rect]="variant === 'rect'"
        [class.skeleton--circle]="variant === 'circle'"
        [style.width]="width"
        [style.height]="height"
        aria-hidden="true"
      ></span>
    </ng-template>
  `,
  styleUrls: ['./skeleton.component.scss'],
})
export class SkeletonComponent {
  /** Forma del placeholder. text genera líneas de párrafo, rect un bloque, circle un círculo. */
  @Input() variant: SkeletonVariant = 'rect';
  /** Cantidad de líneas (solo aplica a variant=text). Default 1 → bloque único. */
  @Input() lines = 1;
  /** Ancho CSS (default 100%). */
  @Input() width = '100%';
  /** Alto CSS (default 1em). */
  @Input() height = '1em';

  /** Expuesto solo para el *ngFor del template. */
  get lineArray(): unknown[] {
    return Array.from({ length: Math.max(1, this.lines) });
  }
}
