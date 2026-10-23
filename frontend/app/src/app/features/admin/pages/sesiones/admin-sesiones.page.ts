import { CommonModule } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Dialog } from 'primeng/dialog';
import { InputText } from 'primeng/inputtext';
import { Skeleton } from 'primeng/skeleton';
import { TableModule } from 'primeng/table';
import { Menu } from 'primeng/menu';
import { MenuItem } from 'primeng/api';
import {
  ButtonComponent,
  EmptyStateComponent,
  ErrorStateComponent,
  SkeletonComponent,
} from '../../../../shared/ui';
import { PaginatorComponent } from '../../../../shared/components/paginator/paginator.component';
import { AuthStore } from '../../../../core/auth/auth.store';
import { AdminApi } from '../../data/admin.api';
import {
  SesionFiltros,
  SesionResponseDto,
} from '../../domain/models/admin.model';

@Component({
  selector: 'app-admin-sesiones',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    ButtonComponent,
    EmptyStateComponent,
    ErrorStateComponent,
    SkeletonComponent,
    PaginatorComponent,
    Dialog,
    InputText,
    Skeleton,
    TableModule,
    Menu,
  ],
  templateUrl: './admin-sesiones.page.html',
  styleUrls: ['./admin-sesiones.page.scss'],
})
export class AdminSesionesPage implements OnInit {
  private readonly service = inject(AdminApi);
  private readonly authStore = inject(AuthStore);
  readonly sesiones = signal<SesionResponseDto[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  /**
   * MASTER-only controls. The role comes from the in-memory session, so a
   * session restored after a reload lights the controls up on its own.
   */
  readonly esMaster = this.authStore.isMaster;
  terminoBusqueda = '';
  page = 0;
  size = 20;
  readonly totalElements = signal(0);
  modalAbierto = false;
  sesionSeleccionada: SesionResponseDto | null = null;
  ngOnInit(): void {
    this.cargarSesiones();
  }
  cargarSesiones(): void {
    this.loading.set(true);
    this.error.set(null);
    const filtros: SesionFiltros = {
      search: this.terminoBusqueda.trim() || undefined,
      page: this.page,
      size: this.size,
    };
    this.service.listarSesiones(filtros).subscribe({
      next: (res) => {
        this.sesiones.set(res.data);
        this.totalElements.set(res.meta.totalElements);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(err?.message || 'No se pudieron cargar las sesiones.');
        this.loading.set(false);
      },
    });
  }
  puedeRevocar(): boolean {
    return this.esMaster();
  }
  verDetalles(sesion: SesionResponseDto): void {
    this.sesionSeleccionada = sesion;
    this.modalAbierto = true;
  }
  cerrarModal(): void {
    this.modalAbierto = false;
  }
  revocar(sesion: SesionResponseDto): void {
    if (
      !this.puedeRevocar() ||
      !confirm(`¿Revocar la sesión de ${sesion.usuarioEmail}?`)
    )
      return;
    this.service.revocarSesion(sesion.id).subscribe({
      next: () => this.cargarSesiones(),
      error: (err) =>
        this.error.set(err?.message || 'No se pudo revocar la sesión.'),
    });
  }
  onPageChange(page: number): void {
    this.page = page;
    this.cargarSesiones();
  }
  onSizeChange(size: number): void {
    this.size = size;
    this.page = 0;
    this.cargarSesiones();
  }

  obtenerAccionesSesion(sesion: SesionResponseDto): MenuItem[] {
    const items: MenuItem[] = [
      {
        label: 'Ver detalles',
        icon: 'pi pi-eye',
        command: () => this.verDetalles(sesion),
      },
    ];

    if (this.puedeRevocar()) {
      items.push({
        label: 'Revocar',
        icon: 'pi pi-ban',
        styleClass: 'text-red-500',
        command: () => this.revocar(sesion),
      });
    }

    return items;
  }
}
