import {
  Component,
  afterNextRender,
  computed,
  inject,
  signal,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';

import { SidebarComponent } from '../sidebar/sidebar.component';
import { TopbarComponent } from '../topbar/topbar.component';
import { LayoutService } from '../layout.service';

interface RouteMeta {
  title: string;
  subtitle: string;
  notifications: number;
}

/**
 * Authenticated app shell: topbar + sidebar + routed content.
 *
 * The topbar title/subtitle come from the active child route's `data`.
 */
@Component({
  selector: 'app-main-layout',
  standalone: true,
  imports: [CommonModule, RouterOutlet, SidebarComponent, TopbarComponent],
  templateUrl: './main-layout.component.html',
})
export class MainLayoutComponent {
  private readonly router = inject(Router);
  readonly layout = inject(LayoutService);

  readonly meta = signal<RouteMeta>({
    title: '',
    subtitle: '',
    notifications: 0,
  });

  /** Layout geometry classes derived from the shell state. */
  readonly containerClass = computed(() => {
    const overlay = this.layout.menuMode() === 'overlay';
    const open = this.layout.sidebarOpen();

    return {
      'layout-static': !overlay,
      'layout-overlay': overlay,
      'layout-static-inactive':
        !overlay && this.layout.desktopCollapsed() && this.layout.isDesktop(),
      'layout-overlay-active': overlay && open,
      'layout-mobile-active': !this.layout.isDesktop() && open,
    };
  });

  constructor() {
    // Later navigations: recompute whenever a navigation finishes.
    this.router.events
      .pipe(
        filter((e) => e instanceof NavigationEnd),
        takeUntilDestroyed(),
      )
      .subscribe(() => this.applyMeta());

    // Initial navigation: the child route may not be activated yet when the
    // constructor runs, so read once after the first render is settled.
    afterNextRender(() => this.applyMeta());
  }

  private applyMeta(): void {
    const data = this.deepestData();
    this.meta.set({
      title: (data['title'] as string) ?? '',
      subtitle: (data['subtitle'] as string) ?? '',
      notifications: (data['notifications'] as number) ?? 0,
    });
  }

  /**
   * Collects `data` from the whole active route tree. Reads the global
   * router snapshot (not the injected ActivatedRoute) because, under
   * Ionic's IonRouterOutlet, the nested child is not exposed through the
   * component's own ActivatedRoute.
   */
  private deepestData(): Record<string, unknown> {
    let node = this.router.routerState.snapshot.root.firstChild;
    let data: Record<string, unknown> = {};
    while (node) {
      data = { ...data, ...node.data };
      node = node.firstChild;
    }
    return data;
  }

  /**
   * T8.S14: Skip link handler — focuses main content for keyboard navigation.
   * Allows users to skip the topbar and sidebar and jump directly to content.
   */
  focusMainContent(event: Event): void {
    event.preventDefault();
    const mainElement = document.getElementById('layout-main');
    if (mainElement) {
      mainElement.focus();
      mainElement.scrollIntoView();
    }
  }
}
