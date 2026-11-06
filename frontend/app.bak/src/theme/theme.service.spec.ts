import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { ThemeService } from './theme.service';
import { DARK_MODE_CLASS } from './primeng-theme';

const STORAGE_KEY = 'electoral_theme_v1';

describe('ThemeService', () => {
  beforeEach(() => {
    localStorage.removeItem(STORAGE_KEY);
    document.documentElement.classList.remove(DARK_MODE_CLASS);
    TestBed.configureTestingModule({ providers: [ThemeService] });
  });

  afterEach(() => {
    localStorage.removeItem(STORAGE_KEY);
    document.documentElement.classList.remove(DARK_MODE_CLASS);
  });

  it('should start in light mode when nothing is stored', () => {
    const service = TestBed.inject(ThemeService);

    expect(service.darkMode()).toBe(false);
    expect(document.documentElement.classList.contains(DARK_MODE_CLASS)).toBe(
      false,
    );
  });

  it('should apply the dark class to the document element when enabled', () => {
    const service = TestBed.inject(ThemeService);

    service.setDarkMode(true);

    expect(service.darkMode()).toBe(true);
    expect(document.documentElement.classList.contains(DARK_MODE_CLASS)).toBe(
      true,
    );
  });

  it('should remove the dark class when disabled', () => {
    const service = TestBed.inject(ThemeService);

    service.setDarkMode(true);
    service.setDarkMode(false);

    expect(service.darkMode()).toBe(false);
    expect(document.documentElement.classList.contains(DARK_MODE_CLASS)).toBe(
      false,
    );
  });

  it('should toggle between light and dark mode', () => {
    const service = TestBed.inject(ThemeService);

    service.toggle();
    expect(service.darkMode()).toBe(true);

    service.toggle();
    expect(service.darkMode()).toBe(false);
  });

  it('should persist the preference', () => {
    const service = TestBed.inject(ThemeService);

    service.setDarkMode(true);

    expect(localStorage.getItem(STORAGE_KEY)).toBe('dark');
  });

  it('should restore a persisted dark preference on creation', () => {
    localStorage.setItem(STORAGE_KEY, 'dark');

    const service = TestBed.inject(ThemeService);

    expect(service.darkMode()).toBe(true);
    expect(document.documentElement.classList.contains(DARK_MODE_CLASS)).toBe(
      true,
    );
  });
});
