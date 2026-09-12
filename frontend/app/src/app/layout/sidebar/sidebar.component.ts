import { CommonModule } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { Router, RouterLink, RouterLinkActive } from '@angular/router';

import { AuthService } from '../../core/auth/auth.service';
import { AuthStore } from '../../core/auth/auth.store';
import { MenuNode } from '../../core/menu/menu.model';
import { MenuService } from '../../core/menu/menu.service';
import { LayoutService } from '../layout.service';

import { IonIcon } from '@ionic/angular/standalone';

export interface DynamicNavSection {
  label: string;
  items: DynamicNavItem[];
}

export interface DynamicNavItem {
  label: string;
  icon: string;
  route: string;
  disabled?: boolean;
  tag?: string;
}

@Component({
  selector: 'app-sidebar',
  standalone: true,
  imports: [CommonModule, RouterLink, RouterLinkActive, IonIcon],
  templateUrl: './sidebar.component.html',
  styleUrl: './sidebar.component.scss',
})
export class SidebarComponent implements OnInit {
  private readonly store = inject(AuthStore);
  private readonly auth = inject(AuthService);
  private readonly menuService = inject(MenuService);
  private readonly router = inject(Router);
  readonly layout = inject(LayoutService);

  private readonly _sections = signal<DynamicNavSection[]>([]);
  readonly sections = this._sections.asReadonly();

  readonly user = this.store.user;

  ngOnInit(): void {
    this.cargarMenus();
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
            icon: 'grid-outline',
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
    return ruta;
  }

  private mapIcon(icono?: string): string {
    switch (icono) {
      case 'home':
        return 'home-outline';
      case 'document':
      case 'add-circle':
        return 'document-text-outline';
      case 'checkmark':
        return 'checkmark-circle-outline';
      case 'image':
      case 'cloud-upload':
        return 'camera-outline';
      case 'settings':
        return 'settings-outline';
      case 'people':
        return 'people-outline';
      case 'pulse':
        return 'analytics-outline';
      default:
        return 'document-text-outline';
    }
  }

  private getFallbackSections(): DynamicNavSection[] {
    const sections: DynamicNavSection[] = [
      {
        label: 'Dashboard',
        items: [
          {
            label: 'Resumen General',
            icon: 'home-outline',
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
            icon: 'grid-outline',
            route: '/observer/mis-jrvs',
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
