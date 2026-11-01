import { DOCUMENT, isPlatformBrowser } from '@angular/common';
import { inject, Injectable, PLATFORM_ID, signal } from '@angular/core';
import { DARK_MODE_CLASS } from './primeng-theme';

const STORAGE_KEY = 'electoral_theme_v1';

/**
 * Owns the active colour scheme.
 *
 * The dark scheme is driven by the `app-dark` class on the document element,
 * which is the selector PrimeNG's `darkModeSelector` option and the Tailwind
 * `dark` variant both react to. Keeping the class as the single source of
 * truth means the component library and the utility CSS never disagree.
 *
 * The class is applied synchronously rather than through an `effect`, so it is
 * already on `<html>` before the first paint and the app never flashes the
 * wrong scheme on boot.
 */
@Injectable({ providedIn: 'root' })
export class ThemeService {
  private readonly document = inject(DOCUMENT);
  private readonly platformId = inject(PLATFORM_ID);

  /** True when the dark scheme is active. */
  readonly darkMode = signal(false);

  constructor() {
    const enabled = this.readPreference();
    this.darkMode.set(enabled);
    this.applyClass(enabled);
  }

  /** Switches between light and dark mode. */
  toggle(): void {
    this.setDarkMode(!this.darkMode());
  }

  /** Activates or deactivates the dark scheme and persists the choice. */
  setDarkMode(enabled: boolean): void {
    this.darkMode.set(enabled);
    this.applyClass(enabled);
    this.writePreference(enabled);
  }

  private readPreference(): boolean {
    if (!isPlatformBrowser(this.platformId)) return false;
    try {
      return (
        this.document.defaultView?.localStorage?.getItem(STORAGE_KEY) === 'dark'
      );
    } catch {
      return false;
    }
  }

  private writePreference(enabled: boolean): void {
    if (!isPlatformBrowser(this.platformId)) return;
    try {
      this.document.defaultView?.localStorage?.setItem(
        STORAGE_KEY,
        enabled ? 'dark' : 'light',
      );
    } catch {
      // Storage may be unavailable (private mode, quota). The theme still
      // applies for the current session.
    }
  }

  private applyClass(enabled: boolean): void {
    if (!isPlatformBrowser(this.platformId)) return;
    const root = this.document.documentElement;
    if (enabled) {
      root.classList.add(DARK_MODE_CLASS);
    } else {
      root.classList.remove(DARK_MODE_CLASS);
    }
  }
}
