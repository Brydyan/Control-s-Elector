import { CommonModule } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
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
import { CatalogoApi } from '../../data/catalogo.api';
import {
  CargoRequestDto,
  CargoResponseDto,
  NivelCargo,
} from '../../domain/models/catalogo.model';

@Component({
  selector: 'app-master-cargos',
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
    Select,
    Skeleton,
    TableModule,
    Menu,
  ],
  templateUrl: './master-cargos.page.html',
  styleUrls: ['./master-cargos.page.scss'],
})
export class MasterCargosPage implements OnInit {
  private readonly service = inject(CatalogoApi);

  readonly cargos = signal<CargoResponseDto[]>([]);
  readonly loading = signal(true);
  readonly submitting = signal(false);
  readonly error = signal<string | null>(null);
  readonly modalError = signal<string | null>(null);

  readonly terminoBusqueda = signal('');
  readonly nivelSeleccionado = signal<NivelCargo | 'TODOS'>('TODOS');

  readonly nivelFilterOptions: {
    label: string;
    value: NivelCargo | 'TODOS';
  }[] = [
    { label: 'Todos los niveles', value: 'TODOS' },
    { label: 'Nacional', value: 'NACIONAL' },
    { label: 'Provincia', value: 'PROVINCIA' },
    { label: 'Cantón', value: 'CANTON' },
    { label: 'Parroquia', value: 'PARROQUIA' },
  ];

  readonly niveles: { label: string; value: NivelCargo }[] = [
    { label: 'Nacional', value: 'NACIONAL' },
    { label: 'Provincia', value: 'PROVINCIA' },
    { label: 'Cantón', value: 'CANTON' },
    { label: 'Parroquia', value: 'PARROQUIA' },
  ];

  modalAbierto = false;
  modoEdicion = false;
  cargoEditandoId: number | null = null;
  nombre = '';
  nivel: NivelCargo = 'NACIONAL';
  puestos = 1;

  readonly cargosFiltrados = computed(() => {
    const list = this.cargos();
    const busqueda = this.terminoBusqueda().trim().toLowerCase();
    const nivelFiltro = this.nivelSeleccionado();

    return list.filter((c) => {
      const matchBusqueda =
        !busqueda ||
        c.nombre.toLowerCase().includes(busqueda) ||
        c.nivel.toLowerCase().includes(busqueda);
      const matchNivel = nivelFiltro === 'TODOS' || c.nivel === nivelFiltro;
      return matchBusqueda && matchNivel;
    });
  });

  ngOnInit(): void {
    this.cargarCargos();
  }

  cargarCargos(): void {
    this.loading.set(true);
    this.error.set(null);
    this.service.listarCargos().subscribe({
      next: (data) => {
        this.cargos.set(data);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(err.message ?? 'No se pudo cargar la lista de cargos');
        this.loading.set(false);
      },
    });
  }

  tieneFiltrosActivos(): boolean {
    return (
      this.terminoBusqueda().trim() !== '' ||
      this.nivelSeleccionado() !== 'TODOS'
    );
  }

  limpiarFiltros(): void {
    this.terminoBusqueda.set('');
    this.nivelSeleccionado.set('TODOS');
  }

  abrirCrear(): void {
    this.modoEdicion = false;
    this.cargoEditandoId = null;
    this.nombre = '';
    this.nivel = 'NACIONAL';
    this.puestos = 1;
    this.modalError.set(null);
    this.modalAbierto = true;
  }

  abrirEditar(cargo: CargoResponseDto): void {
    this.modoEdicion = true;
    this.cargoEditandoId = cargo.id;
    this.nombre = cargo.nombre;
    this.nivel = cargo.nivel;
    this.puestos = cargo.puestos;
    this.modalError.set(null);
    this.modalAbierto = true;
  }

  cerrarModal(): void {
    this.modalAbierto = false;
    this.modalError.set(null);
  }

  guardar(): void {
    if (!this.nombre.trim()) {
      this.modalError.set('El nombre del cargo es obligatorio.');
      return;
    }
    if (!this.puestos || this.puestos < 1) {
      this.modalError.set('Debe haber al menos 1 puesto.');
      return;
    }

    const payload: CargoRequestDto = {
      nombre: this.nombre.trim(),
      nivel: this.nivel,
      puestos: this.puestos,
    };

    this.submitting.set(true);
    this.modalError.set(null);

    const call$ =
      this.modoEdicion && this.cargoEditandoId !== null
        ? this.service.actualizarCargo(this.cargoEditandoId, payload)
        : this.service.crearCargo(payload);

    call$.subscribe({
      next: () => {
        this.submitting.set(false);
        this.cerrarModal();
        this.cargarCargos();
      },
      error: (err) => {
        this.submitting.set(false);
        this.modalError.set(err.message ?? 'Error al guardar el cargo');
      },
    });
  }

  eliminarCargo(cargo: CargoResponseDto): void {
    if (
      !confirm(
        `¿Estás seguro de que deseas eliminar el cargo "${cargo.nombre}"?`,
      )
    ) {
      return;
    }

    this.service.eliminarCargo(cargo.id).subscribe({
      next: () => {
        this.cargarCargos();
      },
      error: (err) => {
        alert(err.message ?? 'Error al eliminar el cargo');
      },
    });
  }

  obtenerAccionesCargo(cargo: CargoResponseDto): MenuItem[] {
    return [
      {
        label: 'Editar',
        icon: 'pi pi-pencil',
        command: () => this.abrirEditar(cargo),
      },
      {
        separator: true,
      },
      {
        label: 'Eliminar',
        icon: 'pi pi-trash',
        styleClass: 'text-red-500',
        command: () => this.eliminarCargo(cargo),
      },
    ];
  }
}
