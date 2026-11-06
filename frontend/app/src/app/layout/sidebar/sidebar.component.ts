import {
  Component,
  computed,
  inject,
  OnDestroy,
  OnInit,
  signal,
} from '@angular/core';
import {
  NavigationEnd,
  Router,
  RouterLink,
  RouterLinkActive,
} from '@angular/router';
import { filter, Subject, takeUntil } from 'rxjs';

import { AuthService } from '../../features/auth/services/auth.service';
import { AuthStore } from '../../features/auth/services/auth.store';
import { MenuService } from '../../core/menu/menu.service';
import { toPrimeIcon } from '../../shared/ui/icon-map';
import { LayoutService } from '../layout.service';
import { NAV_CONFIG, sanitizeNavSections, type UserRole } from '../nav.config';

export interface DynamicNavSection {
  label: string;
  items: DynamicNavItem[];
}

export interface DynamicNavItem {
  label: string;
  /** PrimeIcons class, e.g. `pi pi-home`. */
  icon: string;
  route: string;
  disabled?: boolean;
  tag?: string;
}

/**
 * Navigation sidebar.
 *
 * The sections come from the backend menu catalog (`MenuService`), which
 * already applies the RBAC rules, so the frontend only maps the payload into
 * the shell. The static fallback is used when the catalog is unreachable.
 */
@Component({
  selector: 'app-sidebar',
  standalone: true,
  imports: [RouterLink, RouterLinkActive],
  templateUrl: './sidebar.component.html',
})
export class SidebarComponent implements OnInit, OnDestroy {
  private readonly store = inject(AuthStore);
  private readonly auth = inject(AuthService);
  private readonly menuService = inject(MenuService);
  private readonly router = inject(Router);
  readonly layout = inject(LayoutService);

  private readonly _sections = signal<DynamicNavSection[]>([]);
  readonly sections = this._sections.asReadonly();

  readonly user = this.store.user;

  private readonly destroy$ = new Subject<void>();

  ngOnInit(): void {
    // Initialize sections from canonical NAV_CONFIG based on current role.
    // This ensures immediate, offline-first rendering without waiting for HTTP.
    const role = this.user()?.role as UserRole | undefined;
    if (role) {
      this._sections.set(NAV_CONFIG[role]);
    }

    // Attempt to load from backend and sanitize/deduplicate against NAV_CONFIG.
    this.cargarMenus();

    this.router.events
      .pipe(
        filter((event) => event instanceof NavigationEnd),
        takeUntil(this.destroy$),
      )
      .subscribe(() => {
        // The drawer is mobile-only; navigating closes it.
        if (!this.layout.isDesktop()) {
          this.layout.closeSidebar();
        }
      });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  cargarMenus(): void {
    const role = this.user()?.role as UserRole | undefined;
    if (!role) {
      return;
    }

    this.menuService.getMyMenus().subscribe({
      next: (nodes) => {
        // Sanitize and deduplicate backend response against canonical NAV_CONFIG.
        const sections = sanitizeNavSections(role, nodes);
        this._sections.set(sections);
      },
      error: (err) => {
        console.warn(
          '[sidebar] Error loading menus from backend, keeping canonical NAV_CONFIG',
          err,
        );
        // On error (HTTP 500, timeout, offline), fallback to canonical NAV_CONFIG
        // which was already set in ngOnInit() → zero flicker, always functional.
        this._sections.set(NAV_CONFIG[role]);
      },
    });
  }

  /** "ana.delgado@cne.gob.ec" -> "Ana Delgado". Falls back to the email. */
  readonly displayName = computed(() => {
    const email = this.user()?.email;
    if (!email) return '';
    const local = email.split('@')[0];
    return local
      .split(/[.\-_]/)
      .filter(Boolean)
      .map((p) => p.charAt(0).toUpperCase() + p.slice(1))
      .join(' ');
  });

  readonly initials = computed(() => {
    const name = this.displayName();
    const parts = name.split(' ').filter(Boolean);
    const chars =
      parts.length >= 2 ? parts[0][0] + parts[1][0] : name.slice(0, 2);
    return chars.toUpperCase();
  });

  readonly roleLabel = computed(() => this.user()?.role ?? '');

  logout(): void {
    this.auth.logout().subscribe({
      next: () => this.router.navigateByUrl('/login'),
      error: () => this.router.navigateByUrl('/login'),
    });
  }
}
