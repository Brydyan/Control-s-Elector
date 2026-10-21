import { Component, computed, inject, input, viewChild } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { Avatar } from 'primeng/avatar';
import { Badge } from 'primeng/badge';
import { Popover } from 'primeng/popover';

import { AuthService } from '../../core/auth/services/auth.service';
import { AuthStore } from '../../core/auth/auth.store';
import { Role } from '../../core/auth/domain/user.model';
import { NetworkStatusService } from '../../core/network/network-status.service';
import { LayoutService } from '../layout.service';
import { ThemeService } from '../../../theme/theme.service';

/** Roles that can only observe — never write. Drives the "Solo lectura" badge. */
const READ_ONLY_ROLES: Role[] = ['ADMIN'];

@Component({
  selector: 'app-topbar',
  standalone: true,
  imports: [RouterLink, Avatar, Badge, Popover],
  templateUrl: './topbar.component.html',
})
export class TopbarComponent {
  private readonly store = inject(AuthStore);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  readonly layout = inject(LayoutService);
  readonly network = inject(NetworkStatusService);
  readonly theme = inject(ThemeService);

  readonly title = input('');
  readonly subtitle = input('');
  readonly notifications = input(0);

  readonly userPanel = viewChild<Popover>('userPanel');

  readonly isReadOnly = computed(() => {
    const role = this.store.user()?.role;
    return role ? READ_ONLY_ROLES.includes(role) : false;
  });

  readonly displayName = computed(() => {
    const email = this.store.user()?.email;
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

  readonly roleLabel = computed(() => this.store.user()?.role ?? '');

  toggleUserPanel(event: Event): void {
    this.userPanel()?.toggle(event);
  }

  toggleDarkMode(): void {
    this.theme.toggle();
  }

  logout(): void {
    this.auth.logout().subscribe({
      next: () => this.router.navigateByUrl('/login'),
      error: () => this.router.navigateByUrl('/login'),
    });
  }
}
