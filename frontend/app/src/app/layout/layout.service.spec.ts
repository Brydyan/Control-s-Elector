import { describe, it, expect, beforeEach, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { LayoutService } from './layout.service';

describe('LayoutService', () => {
  let service: LayoutService;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({ providers: [LayoutService] });
    service = TestBed.inject(LayoutService);
  });

  it('should start with the sidebar closed and expanded', () => {
    expect(service.sidebarOpen()).toBe(false);
    expect(service.desktopCollapsed()).toBe(false);
    expect(service.menuMode()).toBe('static');
  });

  it('should open and close the sidebar', () => {
    service.openSidebar();
    expect(service.sidebarOpen()).toBe(true);

    service.closeSidebar();
    expect(service.sidebarOpen()).toBe(false);
  });

  it('should toggle the desktop collapse state and persist it', () => {
    service.toggleDesktopCollapse();

    expect(service.desktopCollapsed()).toBe(true);
    expect(localStorage.getItem('electoral_sidebar_collapsed')).toBe('true');
  });

  it('should restore the persisted collapse state', () => {
    localStorage.setItem('electoral_sidebar_collapsed', 'true');

    const restored = TestBed.runInInjectionContext(
      () => new LayoutService(),
    );

    expect(restored.desktopCollapsed()).toBe(true);
  });

  it('should switch the menu mode and persist it', () => {
    service.setMenuMode('overlay');

    expect(service.menuMode()).toBe('overlay');
    expect(localStorage.getItem('electoral_menu_mode')).toBe('overlay');

    service.toggleMenuMode();
    expect(service.menuMode()).toBe('static');
  });

  it('should treat a viewport below the breakpoint as mobile', () => {
    vi.stubGlobal('innerWidth', 480);
    const mobile = TestBed.runInInjectionContext(() => new LayoutService());

    expect(mobile.isDesktop()).toBe(false);

    vi.unstubAllGlobals();
  });
});
