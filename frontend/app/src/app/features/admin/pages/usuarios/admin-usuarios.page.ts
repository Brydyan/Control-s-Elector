import { CommonModule } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Dialog } from 'primeng/dialog';
import { InputText } from 'primeng/inputtext';
import { Message } from 'primeng/message';
import { Select } from 'primeng/select';
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
import { AuthStore } from '../../../auth/services/auth.store';
import { AdminApi } from '../../data/admin.api';
import {
  RolUsuario,
  UsuarioConPasswordTemporal,
  UsuarioFiltros,
  UsuarioResponseDto,
} from '../../domain/models/admin.model';

@Component({
  selector: 'app-admin-usuarios',
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
    Message,
    Select,
    Skeleton,
    TableModule,
    Menu,
  ],
  templateUrl: './admin-usuarios.page.html',
  styleUrls: ['./admin-usuarios.page.scss'],
})
export class AdminUsuariosPage implements OnInit {
  private readonly service = inject(AdminApi);
  private readonly authStore = inject(AuthStore);
  readonly usuarios = signal<UsuarioResponseDto[]>([]);
  readonly loading = signal(true);
  readonly submitting = signal(false);
  readonly error = signal<string | null>(null);
  /**
   * MASTER-only controls. The role comes from the in-memory session, so a
   * session restored after a reload lights the controls up on its own.
   */
  readonly esMaster = this.authStore.isMaster;
  terminoBusqueda = '';
  rolSeleccionado: RolUsuario | '' = '';
  habilitadoSeleccionado: 'TODOS' | 'HABILITADOS' | 'DESHABILITADOS' = 'TODOS';
  page = 0;
  size = 20;
  readonly totalElements = signal(0);
  modalAbierto = false;
  modalDetalles = false;
  modoEdicion = false;
  usuarioSeleccionado: UsuarioResponseDto | null = null;
  nombre = '';
  apellido = '';
  rol: RolUsuario = 'OBSERVER';
  passwordTemporal: string | null = null;
  readonly roles: RolUsuario[] = ['MASTER', 'COORDINATOR', 'OBSERVER', 'ADMIN'];

  /** Filter options for the enabled state. */
  readonly estadoOptions: Array<{ label: string; value: string }> = [
    { label: 'Todos', value: 'TODOS' },
    { label: 'Habilitados', value: 'HABILITADOS' },
    { label: 'Deshabilitados', value: 'DESHABILITADOS' },
  ];

  /** Filter options for the role, with the "all roles" entry first. */
  readonly rolFilterOptions: Array<{ label: string; value: RolUsuario | '' }> =
    [
      { label: 'Todos los roles', value: '' },
      { label: 'MASTER', value: 'MASTER' },
      { label: 'COORDINATOR', value: 'COORDINATOR' },
      { label: 'OBSERVER', value: 'OBSERVER' },
      { label: 'ADMIN', value: 'ADMIN' },
    ];

  ngOnInit(): void {
    this.cargarUsuarios();
  }

  cargarUsuarios(): void {
    this.loading.set(true);
    this.error.set(null);
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
        this.usuarios.set(res.data);
        this.totalElements.set(res.meta.totalElements);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(err?.message || 'No se pudieron cargar los usuarios.');
        this.loading.set(false);
      },
    });
  }

  puedeAnular(usuario: UsuarioResponseDto): boolean {
    return this.esMaster() && usuario.habilitado;
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
    this.error.set(null);
    this.modalAbierto = true;
  }
  abrirEditar(usuario: UsuarioResponseDto): void {
    if (!this.esMaster()) return;
    this.modoEdicion = true;
    this.usuarioSeleccionado = usuario;
    this.nombre = usuario.nombre;
    this.apellido = usuario.apellido;
    this.rol = usuario.rolNombre as RolUsuario;
    this.passwordTemporal = null;
    this.error.set(null);
    this.modalAbierto = true;
  }
  cerrarModal(): void {
    if (!this.submitting()) this.modalAbierto = false;
  }
  cerrarDetalles(): void {
    this.modalDetalles = false;
  }
  guardar(): void {
    if (!this.nombre.trim() || !this.apellido.trim()) {
      this.error.set('Nombre y apellido son obligatorios.');
      return;
    }
    this.submitting.set(true);
    this.error.set(null);
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
        this.submitting.set(false);
        if (!this.modoEdicion)
          this.passwordTemporal =
            (usuario as UsuarioConPasswordTemporal).passwordTemporal ?? null;
        this.cargarUsuarios();
        if (this.modoEdicion) this.modalAbierto = false;
      },
      error: (err) => {
        this.submitting.set(false);
        this.error.set(err?.message || 'No se pudo guardar el usuario.');
      },
    });
  }
  deshabilitar(usuario: UsuarioResponseDto): void {
    if (!this.puedeAnular(usuario)) return;
    const mensaje = `¿Estás seguro de que deseas deshabilitar a ${usuario.email}? Perderá el acceso al sistema.`;
    if (!confirm(mensaje)) return;
    this.submitting.set(true);
    this.service.deshabilitarUsuario(usuario.publicId).subscribe({
      next: () => this.cargarUsuarios(),
      error: (err) => {
        this.submitting.set(false);
        this.error.set(err?.message || 'No se pudo deshabilitar el usuario.');
      },
    });
  }
  rehabilitar(usuario: UsuarioResponseDto): void {
    if (
      !this.esMaster() ||
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
          this.error.set(err?.message || 'No se pudo re-habilitar el usuario.'),
      });
  }

  obtenerAccionesUsuario(usuario: UsuarioResponseDto): MenuItem[] {
    const items: MenuItem[] = [
      {
        label: 'Ver detalles',
        icon: 'pi pi-eye',
        command: () => this.verDetalles(usuario),
      },
    ];

    if (this.esMaster()) {
      items.push({
        label: 'Editar',
        icon: 'pi pi-pencil',
        command: () => this.abrirEditar(usuario),
      });
    }

    if (this.puedeAnular(usuario)) {
      items.push({
        label: 'Deshabilitar',
        icon: 'pi pi-ban',
        styleClass: 'text-red-500',
        command: () => this.deshabilitar(usuario),
      });
    }

    if (this.esMaster() && !usuario.habilitado) {
      items.push({
        label: 'Re-habilitar',
        icon: 'pi pi-check',
        command: () => this.rehabilitar(usuario),
      });
    }

    return items;
  }
}
