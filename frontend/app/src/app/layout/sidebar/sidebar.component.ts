import { Component, computed, inject, OnDestroy, OnInit, signal } from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterLinkActive } from '@angular/router';
import { filter, Subject, takeUntil } from 'rxjs';

import { AuthService } from '../../core/auth/services/auth.service';
import { AuthStore } from '../../core/auth/auth.store';
import { MenuNode } from '../../core/menu/menu.model';
import { MenuService } from '../../core/menu/menu.service';
import { LayoutService } from '../layout.service';

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
    this.menuService.getMyMenus().subscribe({
      next: (nodes) => {
        const sections = this.mapNodesToSections(nodes);
        this._sections.set(sections);
      },
      error: (err) => {
        console.warn(
          'No se pudieron cargar los menús dinámicos, usando fallback',
          err,
        );
        this._sections.set(this.getFallbackSections());
      },
    });
  }

  private mapNodesToSections(nodes: MenuNode[]): DynamicNavSection[] {
    if (!nodes || nodes.length === 0) {
      return this.getFallbackSections();
    }

    const sections: DynamicNavSection[] = [];

    nodes.forEach((node) => {
      // Si el nodo padre tiene hijos, el padre es la sección
      if (node.children && node.children.length > 0) {
        sections.push({
          label: node.label,
          items: node.children.map((child) => ({
            label: child.label,
            icon: this.mapIcon(child.icono),
            route: this.mapRoute(child.ruta),
            disabled: !child.activo,
          })),
        });
      } else {
        // Nodo raíz sin hijos (ej: Dashboard)
        let mainSection = sections.find((s) => s.label === 'Principal');
        if (!mainSection) {
          mainSection = { label: 'Principal', items: [] };
          sections.unshift(mainSection);
        }
        mainSection.items.push({
          label: node.label,
          icon: this.mapIcon(node.icono),
          route: this.mapRoute(node.ruta),
          disabled: !node.activo,
        });
      }
    });

    // Agregar sección de operación electoral solo a roles con acceso al flujo (OBSERVER y MASTER)
    const role = this.user()?.role;
    if (role === 'OBSERVER' || role === 'MASTER') {
      sections.push({
        label: 'Operación Electoral',
        items: [
          {
            label: 'Mis JRVs',
            icon: 'pi pi-th-large',
            route: '/observer/mis-jrvs',
          },
        ],
      });
    }

    return sections;
  }

  private mapRoute(ruta: string): string {
    if (ruta === '/home') return '/dashboard';
    if (ruta === '/actas/nueva') return '/observer/mis-jrvs';
    if (ruta === '/actas/aprobar') return '/coordinator/actas';
    return ruta;
  }

  /**
   * Maps the `menu.icono` catalog values to PrimeIcons.
   *
   * The backend stores stable icon keys ("home", "document", "people", …), not
   * a library-specific name, so the mapping lives in the frontend and the
   * catalog needs no change when the icon library does.
   */
  private mapIcon(icono?: string): string {
    switch (icono) {
      case 'home':
        return 'pi pi-home';
      case 'document':
        return 'pi pi-file';
      case 'add-circle':
        return 'pi pi-plus-circle';
      case 'checkmark':
        return 'pi pi-check-circle';
      case 'image':
        return 'pi pi-image';
      case 'cloud-upload':
        return 'pi pi-cloud-upload';
      case 'settings':
        return 'pi pi-cog';
      case 'people':
        return 'pi pi-users';
      case 'pulse':
        return 'pi pi-chart-line';
      default:
        return 'pi pi-file';
    }
  }

  private getFallbackSections(): DynamicNavSection[] {
    const sections: DynamicNavSection[] = [
      {
        label: 'Dashboard',
        items: [
          {
            label: 'Resumen General',
            icon: 'pi pi-home',
            route: '/dashboard',
          },
        ],
      },
    ];

    const role = this.user()?.role;
    if (role === 'OBSERVER' || role === 'MASTER') {
      sections.push({
        label: 'Operación Electoral',
        items: [
          {
            label: 'Mis JRVs',
            icon: 'pi pi-th-large',
            route: '/observer/mis-jrvs',
          },
        ],
      });
    }

    if (role === 'COORDINATOR' || role === 'MASTER') {
      sections.push({
        label: 'Supervisión',
        items: [
          {
            label: 'Aprobar Actas',
            icon: 'pi pi-check-circle',
            route: '/coordinator/actas',
          },
        ],
      });
    }

    return sections;
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
