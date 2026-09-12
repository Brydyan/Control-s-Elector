import { Injectable, signal } from '@angular/core';

@Injectable({
  providedIn: 'root',
})
export class LayoutService {
  /** Mobile slide-in drawer state (< 768px) */
  private readonly _sidebarOpen = signal(false);
  readonly sidebarOpen = this._sidebarOpen.asReadonly();

  /** Desktop collapsed state (>= 768px: slim icon-only sidebar ~68px) */
  private readonly _desktopCollapsed = signal(
    typeof localStorage !== 'undefined' &&
      localStorage.getItem('electoral_sidebar_collapsed') === 'true',
  );
  readonly desktopCollapsed = this._desktopCollapsed.asReadonly();

  toggleSidebar(): void {
    if (typeof window !== 'undefined' && window.innerWidth <= 768) {
      this._sidebarOpen.update((open) => !open);
    } else {
      this.toggleDesktopCollapse();
    }
  }

  toggleDesktopCollapse(): void {
    const next = !this._desktopCollapsed();
    this._desktopCollapsed.set(next);
    if (typeof localStorage !== 'undefined') {
      try {
        localStorage.setItem('electoral_sidebar_collapsed', String(next));
      } catch {
        // Ignore storage quotas or restrictions
      }
    }
  }

  openSidebar(): void {
    this._sidebarOpen.set(true);
  }

  closeSidebar(): void {
    this._sidebarOpen.set(false);
  }
}
