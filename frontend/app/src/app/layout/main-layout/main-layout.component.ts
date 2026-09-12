import { Component, afterNextRender, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';

import { SidebarComponent } from '../sidebar/sidebar.component';
import { TopbarComponent } from '../topbar/topbar.component';

interface RouteMeta {
  title: string;
  subtitle: string;
  notifications: number;
}

/**
 * Authenticated app shell: sidebar + topbar + routed content.
 * The topbar title/subtitle come from the active child route's `data`.
 */
@Component({
  selector: 'app-main-layout',
  standalone: true,
  imports: [RouterOutlet, SidebarComponent, TopbarComponent],
  templateUrl: './main-layout.component.html',
  styleUrl: './main-layout.component.scss',
})
export class MainLayoutComponent {
  private readonly router = inject(Router);

  readonly meta = signal<RouteMeta>({
    title: '',
    subtitle: '',
    notifications: 0,
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
}
