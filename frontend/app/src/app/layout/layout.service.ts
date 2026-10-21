import { computed, effect, Injectable, signal } from '@angular/core';

/**
 * How the sidebar behaves on desktop viewports.
 *
 * - `static`: the sidebar is always visible and pushes the content to the right.
 * - `overlay`: the sidebar floats above the content and is dismissed on navigation.
 */
export type MenuMode = 'static' | 'overlay';

const COLLAPSED_KEY = 'electoral_sidebar_collapsed';
const MENU_MODE_KEY = 'electoral_menu_mode';

/** Viewport width (px) from which the static sidebar layout is used. */
const DESKTOP_BREAKPOINT = 768;

function readStored(key: string): string | null {
  if (typeof localStorage === 'undefined') return null;
  try {
    return localStorage.getItem(key);
  } catch {
    return null;
  }
}

function writeStored(key: string, value: string): void {
  if (typeof localStorage === 'undefined') return;
  try {
    localStorage.setItem(key, value);
  } catch {
    // Ignore storage quotas or restrictions.
  }
}

/**
 * State of the application shell: sidebar visibility, collapse state and menu
 * mode. The shell components read it; nothing else mutates it.
 */
@Injectable({
  providedIn: 'root',
})
export class LayoutService {
  /** Mobile slide-in drawer state (< 768px). */
  private readonly _sidebarOpen = signal(false);
  readonly sidebarOpen = this._sidebarOpen.asReadonly();

  /** Desktop collapsed state (>= 768px: slim icon-only sidebar). */
  private readonly _desktopCollapsed = signal(
    readStored(COLLAPSED_KEY) === 'true',
  );
  readonly desktopCollapsed = this._desktopCollapsed.asReadonly();

  /** How the sidebar behaves on desktop viewports. */
  private readonly _menuMode = signal<MenuMode>(
    readStored(MENU_MODE_KEY) === 'overlay' ? 'overlay' : 'static',
  );
  readonly menuMode = this._menuMode.asReadonly();

  /** Current viewport width, kept in sync with the window resize event. */
  private readonly _viewportWidth = signal(
    typeof window === 'undefined' ? DESKTOP_BREAKPOINT : window.innerWidth,
  );
  readonly viewportWidth = this._viewportWidth.asReadonly();

  /** True when the viewport is wide enough for the static sidebar layout. */
  readonly isDesktop = computed(() => this._viewportWidth() >= DESKTOP_BREAKPOINT);

  constructor() {
    if (typeof window !== 'undefined') {
      window.addEventListener('resize', () =>
        this._viewportWidth.set(window.innerWidth),
      );
    }

    // A mobile drawer left open would trap the layout when the viewport grows.
    effect(() => {
      if (this.isDesktop() && this._sidebarOpen()) {
        this._sidebarOpen.set(false);
      }
    });
  }

  /** Toggles the sidebar: drawer on mobile, collapse on desktop. */
  toggleSidebar(): void {
    if (this.isDesktop()) {
      this.toggleDesktopCollapse();
    } else {
      this._sidebarOpen.update((open) => !open);
    }
  }

  /** Alias kept for the shell templates, matching the Sakai naming. */
  onMenuToggle(): void {
    this.toggleSidebar();
  }

  toggleDesktopCollapse(): void {
    const next = !this._desktopCollapsed();
    this._desktopCollapsed.set(next);
    writeStored(COLLAPSED_KEY, String(next));
  }

  openSidebar(): void {
    this._sidebarOpen.set(true);
  }

  closeSidebar(): void {
    this._sidebarOpen.set(false);
  }

  setMenuMode(mode: MenuMode): void {
    this._menuMode.set(mode);
    writeStored(MENU_MODE_KEY, mode);
  }

  toggleMenuMode(): void {
    this.setMenuMode(this._menuMode() === 'static' ? 'overlay' : 'static');
  }
}
