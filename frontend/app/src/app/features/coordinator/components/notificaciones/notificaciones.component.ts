import { CommonModule } from '@angular/common';
import { Component, OnInit, OnDestroy, inject, signal } from '@angular/core';
import { MenuItem } from 'primeng/api';
import { Menu } from 'primeng/menu';
import { Badge } from 'primeng/badge';
import { ButtonComponent } from '../../../../shared/ui';
import { NotificacionApi, Notificacion } from '../../data/notificacion.api';
import { interval, takeUntil, switchMap, tap } from 'rxjs';
import { Subject } from 'rxjs';

@Component({
  selector: 'app-notificaciones',
  standalone: true,
  imports: [CommonModule, Menu, Badge, ButtonComponent],
  template: `
    <div class="notificaciones-wrapper">
      <button
        pButton
        type="button"
        icon="pi pi-bell"
        class="p-button-rounded p-button-text"
        (click)="op.toggle($event)"
        pBadge
        [value]="sinLeer()"
        severity="danger"
        *ngIf="sinLeer() > 0"
      ></button>
      <button
        pButton
        type="button"
        icon="pi pi-bell"
        class="p-button-rounded p-button-text"
        (click)="op.toggle($event)"
        *ngIf="sinLeer() === 0"
      ></button>

      <p-menu
        #op
        [model]="menuItems"
        [popup]="true"
        [autoZIndex]="true"
      ></p-menu>
    </div>
  `,
  styles: `
    .notificaciones-wrapper {
      display: inline-block;
      position: relative;
    }
  `,
})
export class NotificacionesComponent implements OnInit, OnDestroy {
  private readonly api = inject(NotificacionApi);
  private readonly destroy$ = new Subject<void>();

  readonly notificaciones = signal<Notificacion[]>([]);
  readonly sinLeer = signal(0);
  menuItems: MenuItem[] = [];

  ngOnInit(): void {
    this.cargarNotificaciones();
    this.iniciarPolling();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private cargarNotificaciones(): void {
    this.api
      .obtenerPendientes(10)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (response) => {
          this.notificaciones.set(response.data || []);
          this.sinLeer.set(response.meta?.sinLeer || 0);
          this.actualizarMenu();
        },
        error: (err) => {
          console.error('Error loading notificaciones:', err);
        },
      });
  }

  private iniciarPolling(): void {
    interval(10000)
      .pipe(
        switchMap(() => this.api.obtenerPendientes(10)),
        tap((response) => {
          this.notificaciones.set(response.data || []);
          this.sinLeer.set(response.meta?.sinLeer || 0);
          this.actualizarMenu();
        }),
        takeUntil(this.destroy$),
      )
      .subscribe();
  }

  private actualizarMenu(): void {
    const notifs = this.notificaciones();
    if (notifs.length === 0) {
      this.menuItems = [
        {
          label: 'No hay notificaciones',
          command: () => {},
          disabled: true,
        },
      ];
    } else {
      this.menuItems = notifs.map((notif) => ({
        label: notif.titulo,
        icon: 'pi pi-fw pi-bell',
        command: () => this.onNotificacionClick(notif),
      }));
    }
  }

  private onNotificacionClick(notif: Notificacion): void {
    if (!notif.leida) {
      this.api.marcarComoLeida(notif.id).subscribe({
        next: () => {
          this.cargarNotificaciones();
        },
        error: (err) => {
          console.error('Error marking notification as read:', err);
        },
      });
    }
    // TODO: Navigate to acta detalle if acta_id present
  }
}
