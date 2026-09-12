import { Component, computed, inject, input } from '@angular/core';
import { IonIcon } from '@ionic/angular/standalone';

import { AuthStore } from '../../core/auth/auth.store';
import { Role } from '../../core/auth/domain/user.model';
import { NetworkStatusService } from '../../core/network/network-status.service';
import { LayoutService } from '../layout.service';

/** Roles that can only observe — never write. Drives the "Solo lectura" badge. */
const READ_ONLY_ROLES: Role[] = ['ADMIN'];

@Component({
  selector: 'app-topbar',
  standalone: true,
  imports: [IonIcon],
  templateUrl: './topbar.component.html',
  styleUrl: './topbar.component.scss',
})
export class TopbarComponent {
  private readonly store = inject(AuthStore);
  readonly layout = inject(LayoutService);
  readonly network = inject(NetworkStatusService);

  readonly title = input('');
  readonly subtitle = input('');
  readonly notifications = input(0);

  readonly isReadOnly = computed(() => {
    const role = this.store.user()?.role;
    return role ? READ_ONLY_ROLES.includes(role) : false;
  });
}
