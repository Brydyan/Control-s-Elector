import { Component, computed, inject } from '@angular/core';
import { Router, RouterLink, RouterLinkActive } from '@angular/router';

import { AuthService } from '../../core/auth/auth.service';
import { AuthStore } from '../../core/auth/auth.store';
import { Role } from '../../core/auth/domain/user.model';

interface NavItem {
  label: string;
  icon: string;
  route?: string;
  /** Roles allowed to use this item. Empty = everyone. */
  roles?: Role[];
  /** Rendered but not yet implemented — shown disabled. */
  disabled?: boolean;
  /** Small tag on the right (e.g. "Solo MASTER"). */
  tag?: string;
}

interface NavSection {
  label: string;
  items: NavItem[];
}

@Component({
  selector: 'app-sidebar',
  standalone: true,
  imports: [RouterLink, RouterLinkActive],
  templateUrl: './sidebar.component.html',
  styleUrl: './sidebar.component.scss',
})
export class SidebarComponent {
  private readonly store = inject(AuthStore);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  readonly sections: NavSection[] = [
    {
      label: 'Dashboard',
      items: [
        { label: 'Resumen General', icon: '📊', route: '/dashboard' },
        { label: 'Resultados por Cargo', icon: '📈', disabled: true },
        { label: 'Progreso por Cantón', icon: '🗺️', disabled: true },
        { label: 'Lista de Actas', icon: '📋', disabled: true },
      ],
    },
    {
      label: 'Sistema',
      items: [
        { label: 'Configuración', icon: '⚙️', roles: ['MASTER'], disabled: true, tag: 'Solo MASTER' },
        { label: 'Usuarios', icon: '👥', roles: ['MASTER'], disabled: true, tag: 'Solo MASTER' },
      ],
    },
  ];

  readonly user = this.store.user;

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
    const chars = parts.length >= 2 ? parts[0][0] + parts[1][0] : name.slice(0, 2);
    return chars.toUpperCase();
  });

  readonly roleLabel = computed(() => this.user()?.role ?? '');

  logout(): void {
    this.auth.logout().subscribe({
      next: () => this.router.navigateByUrl('/login'),
      // Even if the server call fails, the local session is already cleared.
      error: () => this.router.navigateByUrl('/login'),
    });
  }
}
