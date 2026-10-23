import { CommonModule } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Dialog } from 'primeng/dialog';
import { InputText } from 'primeng/inputtext';
import { Message } from 'primeng/message';
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
import { CatalogoService } from '../../services/catalogo.service';
import {
  PartidoRequestDto,
  PartidoResponseDto,
} from '../../models/catalogo.model';

@Component({
  selector: 'app-master-partidos',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    ButtonComponent,
    EmptyStateComponent,
    ErrorStateComponent,
    SkeletonComponent,
    Dialog,
    InputText,
    Message,
    Skeleton,
    TableModule,
    Menu,
  ],
  templateUrl: './master-partidos.page.html',
  styleUrls: ['./master-partidos.page.scss'],
})
export class MasterPartidosPage implements OnInit {
  private readonly service = inject(CatalogoService);

  readonly partidos = signal<PartidoResponseDto[]>([]);
  readonly loading = signal(true);
  readonly submitting = signal(false);
  readonly error = signal<string | null>(null);
  readonly modalError = signal<string | null>(null);

  readonly terminoBusqueda = signal('');

  modalAbierto = false;
  modoEdicion = false;
  partidoEditandoId: number | null = null;
  nombre = '';
  siglas = '';
  numeroLista: number | null = 1;

  readonly partidosFiltrados = computed(() => {
    const list = this.partidos();
    const busqueda = this.terminoBusqueda().trim().toLowerCase();

    return list.filter((p) => {
      if (!busqueda) return true;
      return (
        p.nombre.toLowerCase().includes(busqueda) ||
        p.siglas.toLowerCase().includes(busqueda) ||
        p.numeroLista.toString().includes(busqueda)
      );
    });
  });

  ngOnInit(): void {
    this.cargarPartidos();
  }

  cargarPartidos(): void {
    this.loading.set(true);
    this.error.set(null);
    this.service.listarPartidos().subscribe({
      next: (data) => {
        this.partidos.set(data);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(err.message ?? 'No se pudo cargar la lista de partidos');
        this.loading.set(false);
      },
    });
  }

  tieneFiltrosActivos(): boolean {
    return this.terminoBusqueda().trim() !== '';
  }

  limpiarFiltros(): void {
    this.terminoBusqueda.set('');
  }

  abrirCrear(): void {
    this.modoEdicion = false;
    this.partidoEditandoId = null;
    this.nombre = '';
    this.siglas = '';
    this.numeroLista = 1;
    this.modalError.set(null);
    this.modalAbierto = true;
  }

  abrirEditar(partido: PartidoResponseDto): void {
    this.modoEdicion = true;
    this.partidoEditandoId = partido.id;
    this.nombre = partido.nombre;
    this.siglas = partido.siglas;
    this.numeroLista = partido.numeroLista;
    this.modalError.set(null);
    this.modalAbierto = true;
  }

  cerrarModal(): void {
    this.modalAbierto = false;
    this.modalError.set(null);
  }

  guardar(): void {
    if (!this.nombre.trim()) {
      this.modalError.set('El nombre del partido es obligatorio.');
      return;
    }
    if (!this.siglas.trim()) {
      this.modalError.set('Las siglas son obligatorias.');
      return;
    }
    if (this.numeroLista === null || this.numeroLista < 1) {
      this.modalError.set('El número de lista debe ser mayor o igual a 1.');
      return;
    }

    const payload: PartidoRequestDto = {
      nombre: this.nombre.trim(),
      siglas: this.siglas.trim().toUpperCase(),
      numeroLista: this.numeroLista,
    };

    this.submitting.set(true);
    this.modalError.set(null);

    const call$ =
      this.modoEdicion && this.partidoEditandoId !== null
        ? this.service.actualizarPartido(this.partidoEditandoId, payload)
        : this.service.crearPartido(payload);

    call$.subscribe({
      next: () => {
        this.submitting.set(false);
        this.cerrarModal();
        this.cargarPartidos();
      },
      error: (err) => {
        this.submitting.set(false);
        this.modalError.set(err.message ?? 'Error al guardar el partido');
      },
    });
  }

  eliminarPartido(partido: PartidoResponseDto): void {
    if (
      !confirm(
        `¿Estás seguro de que deseas eliminar el partido "${partido.siglas} - ${partido.nombre}"?`,
      )
    ) {
      return;
    }

    this.service.eliminarPartido(partido.id).subscribe({
      next: () => {
        this.cargarPartidos();
      },
      error: (err) => {
        alert(err.message ?? 'Error al eliminar el partido');
      },
    });
  }

  obtenerAccionesPartido(partido: PartidoResponseDto): MenuItem[] {
    return [
      {
        label: 'Editar',
        icon: 'pi pi-pencil',
        command: () => this.abrirEditar(partido),
      },
      {
        separator: true,
      },
      {
        label: 'Eliminar',
        icon: 'pi pi-trash',
        styleClass: 'text-red-500',
        command: () => this.eliminarPartido(partido),
      },
    ];
  }
}
