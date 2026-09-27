import {
  AfterViewInit,
  Directive,
  ElementRef,
  HostListener,
  Input,
  OnChanges,
  OnDestroy,
  inject,
} from '@angular/core';

/**
 * Selector de elementos focusables dentro de un contenedor. El orden importa
 * para que el orden de Tab sea estable (primero inputs nativos, luego botones).
 */
const FOCUSABLE_SELECTOR = [
  'a[href]',
  'button:not([disabled])',
  'textarea:not([disabled])',
  'input:not([disabled])',
  'select:not([disabled])',
  '[tabindex]:not([tabindex="-1"])',
].join(',');

/**
 * Directiva que, mientras esté activa en un contenedor (típicamente un modal),
 * - Mueve el foco al primer elemento focusable al activarse.
 * - Mantiene el foco dentro del contenedor al recibir Tab/Shift+Tab.
 * - Escucha Escape y emite un evento (no cerramos el modal desde aquí: eso es
 *   responsabilidad del componente que conoce la lógica de cierre).
 *
 * Por qué no usar cdkTrapFocus: ya tenemos un paquete propio de a11y en shared,
 * y esta directiva es ~30 líneas sin dependencias adicionales.
 */
@Directive({
  selector: '[appFocusTrap]',
  standalone: true,
})
export class FocusTrapDirective implements AfterViewInit, OnChanges, OnDestroy {
  private readonly host = inject(ElementRef<HTMLElement>);

  /** Si está en false, la directiva no hace nada (útil mientras el modal está cerrado). */
  @Input() appFocusTrap = false;

  /** Emitido cuando el usuario presiona Escape. */
  @Input() escapeCallback: (() => void) | null = null;

  private previouslyFocused: HTMLElement | null = null;

  ngAfterViewInit(): void {
    if (this.appFocusTrap) {
      this.activate();
    }
  }

  ngOnChanges(): void {
    // Detección simple: si cambia appFocusTrap, alternar.
    if (this.appFocusTrap) {
      this.activate();
    } else {
      this.deactivate();
    }
  }

  ngOnDestroy(): void {
    this.deactivate();
  }

  private activate(): void {
    this.previouslyFocused = (document.activeElement as HTMLElement) ?? null;
    // Mueve el foco al primer focusable del contenedor (o al host mismo).
    // setTimeout(0) espera al próximo tick — suficiente para que Angular termine
    // de procesar el cambio de @Input() y el DOM esté listo para focus.
    setTimeout(() => this.focusFirst());
  }

  private deactivate(): void {
    if (
      this.previouslyFocused &&
      typeof this.previouslyFocused.focus === 'function'
    ) {
      this.previouslyFocused.focus();
    }
    this.previouslyFocused = null;
  }

  private focusFirst(): void {
    const focusables = this.focusableElements();
    if (focusables.length > 0) {
      focusables[0].focus();
    } else {
      this.host.nativeElement.setAttribute('tabindex', '-1');
      this.host.nativeElement.focus();
    }
  }

  private focusableElements(): HTMLElement[] {
    const root = this.host.nativeElement;
    const nodes: NodeListOf<HTMLElement> =
      root.querySelectorAll(FOCUSABLE_SELECTOR);
    return Array.from(nodes);
  }

  @HostListener('keydown.Tab', ['$event'])
  onTab(event: Event): void {
    const ke = event as KeyboardEvent;
    if (!this.appFocusTrap) return;
    const focusables = this.focusableElements();
    if (focusables.length === 0) return;
    const first = focusables[0];
    const last = focusables[focusables.length - 1];
    const active = document.activeElement as HTMLElement | null;
    if (ke.shiftKey && active === first) {
      ke.preventDefault();
      last.focus();
    } else if (!ke.shiftKey && active === last) {
      ke.preventDefault();
      first.focus();
    }
  }

  @HostListener('keydown.Escape')
  onEscape(): void {
    if (!this.appFocusTrap) return;
    if (this.escapeCallback) {
      this.escapeCallback();
    }
  }
}
