import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { SkeletonComponent as BoneyardSkeletonComponent } from 'boneyard-js/angular';
import {
  ButtonComponent,
  EmptyStateComponent,
  ErrorStateComponent,
} from '../../../shared/ui';
import { PaginatorComponent } from '../../../shared/components/paginator/paginator.component';
import { FocusTrapDirective } from '../../../shared/a11y/focus-trap.directive';
import { AdminService } from '../services/admin.service';
import {
  RolUsuario,
  UsuarioConPasswordTemporal,
  UsuarioFiltros,
  UsuarioResponseDto,
} from '../models/admin.model';

@Component({
  selector: 'app-admin-usuarios',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    ButtonComponent,
    EmptyStateComponent,
    ErrorStateComponent,
    BoneyardSkeletonComponent,
    PaginatorComponent,
    FocusTrapDirective,
  ],
  templateUrl: './admin-usuarios.page.html',
  styleUrls: ['./admin-usuarios.page.scss'],
})
export class AdminUsuariosPage implements OnInit {
  private readonly service = inject(AdminService);
  usuarios: UsuarioResponseDto[] = [];
  loading = true;
  submitting = false;
  error: string | null = null;
  esMaster = false;
  terminoBusqueda = '';
  rolSeleccionado: RolUsuario | '' = '';
  habilitadoSeleccionado: 'TODOS' | 'HABILITADOS' | 'DESHABILITADOS' = 'TODOS';
  page = 0;
  size = 20;
  totalElements = 0;
  modalAbierto = false;
  modalDetalles = false;
  modoEdicion = false;
  usuarioSeleccionado: UsuarioResponseDto | null = null;
  nombre = '';
  apellido = '';
  rol: RolUsuario = 'OBSERVER';
  passwordTemporal: string | null = null;
  readonly roles: RolUsuario[] = ['MASTER', 'COORDINATOR', 'OBSERVER', 'ADMIN'];

  ngOnInit(): void {
    this.esMaster = this.detectarRolMaster();
    this.cargarUsuarios();
  }

  cargarUsuarios(): void {
    this.loading = true;
    this.error = null;
    const filtros: UsuarioFiltros = {
      search: this.terminoBusqueda.trim() || undefined,
      rol: this.rolSeleccionado || undefined,
      habilitado:
        this.habilitadoSeleccionado === 'TODOS'
          ? undefined
          : this.habilitadoSeleccionado === 'HABILITADOS',
      page: this.page,
      size: this.size,
    };
    this.service.listarUsuarios(filtros).subscribe({
      next: (res) => {
        this.usuarios = res.data;
        this.totalElements = res.meta.totalElements;
        this.loading = false;
      },
      error: (err) => {
        this.error = err?.message || 'No se pudieron cargar los usuarios.';
        this.loading = false;
      },
    });
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
  puedeAnular(usuario: UsuarioResponseDto): boolean {
    return this.esMaster && usuario.habilitado;
  }
  tieneFiltrosActivos(): boolean {
    return (
      !!this.terminoBusqueda.trim() ||
      !!this.rolSeleccionado ||
      this.habilitadoSeleccionado !== 'TODOS'
    );
  }
  limpiarFiltros(): void {
    this.terminoBusqueda = '';
    this.rolSeleccionado = '';
    this.habilitadoSeleccionado = 'TODOS';
    this.page = 0;
    this.cargarUsuarios();
  }
  onPageChange(page: number): void {
    this.page = page;
    this.cargarUsuarios();
  }
  onSizeChange(size: number): void {
    this.size = size;
    this.page = 0;
    this.cargarUsuarios();
  }

  verDetalles(usuario: UsuarioResponseDto): void {
    this.usuarioSeleccionado = usuario;
    this.modalDetalles = true;
  }
  abrirCrear(): void {
    this.modoEdicion = false;
    this.usuarioSeleccionado = null;
    this.nombre = '';
    this.apellido = '';
    this.rol = 'OBSERVER';
    this.passwordTemporal = null;
    this.error = null;
    this.modalAbierto = true;
  }
  abrirEditar(usuario: UsuarioResponseDto): void {
    if (!this.esMaster) return;
    this.modoEdicion = true;
    this.usuarioSeleccionado = usuario;
    this.nombre = usuario.nombre;
    this.apellido = usuario.apellido;
    this.rol = usuario.rolNombre as RolUsuario;
    this.passwordTemporal = null;
    this.error = null;
    this.modalAbierto = true;
  }
  cerrarModal(): void {
    if (!this.submitting) this.modalAbierto = false;
  }
  cerrarDetalles(): void {
    this.modalDetalles = false;
  }
  guardar(): void {
    if (!this.nombre.trim() || !this.apellido.trim()) {
      this.error = 'Nombre y apellido son obligatorios.';
      return;
    }
    this.submitting = true;
    this.error = null;
    const payload = {
      nombre: this.nombre.trim(),
      apellido: this.apellido.trim(),
      rol: this.rol,
    };
    const request =
      this.modoEdicion && this.usuarioSeleccionado
        ? this.service.actualizarUsuario(
            this.usuarioSeleccionado.publicId,
            payload,
          )
        : this.service.crearUsuario(payload);
    request.subscribe({
      next: (usuario) => {
        this.submitting = false;
        if (!this.modoEdicion)
          this.passwordTemporal =
            (usuario as UsuarioConPasswordTemporal).passwordTemporal ?? null;
        this.cargarUsuarios();
        if (this.modoEdicion) this.modalAbierto = false;
      },
      error: (err) => {
        this.submitting = false;
        this.error = err?.message || 'No se pudo guardar el usuario.';
      },
    });
  }
  deshabilitar(usuario: UsuarioResponseDto): void {
    if (
      !this.puedeAnular(usuario) ||
      !confirm(`¿Deshabilitar a ${usuario.email}?`)
    )
      return;
    this.submitting = true;
    this.service.deshabilitarUsuario(usuario.publicId).subscribe({
      next: () => this.cargarUsuarios(),
      error: (err) => {
        this.submitting = false;
        this.error = err?.message || 'No se pudo deshabilitar el usuario.';
      },
    });
  }
  rehabilitar(usuario: UsuarioResponseDto): void {
    if (
      !this.esMaster ||
      usuario.habilitado ||
      !confirm(`¿Re-habilitar a ${usuario.email}?`)
    )
      return;
    this.service
      .actualizarUsuario(usuario.publicId, {
        nombre: usuario.nombre,
        apellido: usuario.apellido,
        rol: usuario.rolNombre as RolUsuario,
        habilitado: true,
      })
      .subscribe({
        next: () => this.cargarUsuarios(),
        error: (err) =>
          (this.error = err?.message || 'No se pudo re-habilitar el usuario.'),
      });
  }
}
