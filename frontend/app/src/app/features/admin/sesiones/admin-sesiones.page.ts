import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import {
  ButtonComponent,
  EmptyStateComponent,
  ErrorStateComponent,
  SkeletonComponent,
} from '../../../shared/ui';
import { PaginatorComponent } from '../../../shared/components/paginator/paginator.component';
import { FocusTrapDirective } from '../../../shared/a11y/focus-trap.directive';
import { AdminService } from '../services/admin.service';
import { SesionFiltros, SesionResponseDto } from '../models/admin.model';

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
    FocusTrapDirective,
  ],
  templateUrl: './admin-sesiones.page.html',
  styleUrls: ['./admin-sesiones.page.scss'],
})
export class AdminSesionesPage implements OnInit {
  private readonly service = inject(AdminService);
  sesiones: SesionResponseDto[] = [];
  loading = true;
  error: string | null = null;
  esMaster = false;
  terminoBusqueda = '';
  page = 0;
  size = 20;
  totalElements = 0;
  modalAbierto = false;
  sesionSeleccionada: SesionResponseDto | null = null;
  ngOnInit(): void {
    this.esMaster = this.detectarRolMaster();
    this.cargarSesiones();
  }
  private detectarRolMaster(): boolean {
    try {
      return [
        sessionStorage.getItem('app.user.roles'),
        localStorage.getItem('app.user.roles'),
      ]
        .filter(Boolean)
        .join(',')
        .toUpperCase()
        .includes('MASTER');
    } catch {
      return false;
    }
  }
  cargarSesiones(): void {
    this.loading = true;
    this.error = null;
    const filtros: SesionFiltros = {
      search: this.terminoBusqueda.trim() || undefined,
      page: this.page,
      size: this.size,
    };
    this.service.listarSesiones(filtros).subscribe({
      next: (res) => {
        this.sesiones = res.data;
        this.totalElements = res.meta.totalElements;
        this.loading = false;
      },
      error: (err) => {
        this.error = err?.message || 'No se pudieron cargar las sesiones.';
        this.loading = false;
      },
    });
  }
  puedeRevocar(): boolean {
    return this.esMaster;
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
        (this.error = err?.message || 'No se pudo revocar la sesión.'),
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
}
