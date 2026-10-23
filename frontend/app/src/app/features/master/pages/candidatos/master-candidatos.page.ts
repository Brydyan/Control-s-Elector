import { CommonModule } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { forkJoin } from 'rxjs';
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
  CandidatoRequestDto,
  CandidatoResponseDto,
  CargoResponseDto,
  PartidoResponseDto,
} from '../../domain/models/catalogo.model';

@Component({
  selector: 'app-master-candidatos',
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
  templateUrl: './master-candidatos.page.html',
  styleUrls: ['./master-candidatos.page.scss'],
})
export class MasterCandidatosPage implements OnInit {
  private readonly service = inject(CatalogoApi);

  readonly candidatos = signal<CandidatoResponseDto[]>([]);
  readonly cargos = signal<CargoResponseDto[]>([]);
  readonly partidos = signal<PartidoResponseDto[]>([]);

  readonly loading = signal(true);
  readonly submitting = signal(false);
  readonly error = signal<string | null>(null);
  readonly modalError = signal<string | null>(null);

  readonly terminoBusqueda = signal('');
  readonly cargoFiltroId = signal<number | 'TODOS'>('TODOS');
  readonly partidoFiltroId = signal<number | 'TODOS'>('TODOS');

  readonly cargoFilterOptions = computed(() => [
    { label: 'Todos los cargos', value: 'TODOS' as const },
    ...this.cargos().map((c) => ({
      label: `${c.nombre} (${c.nivel})`,
      value: c.id,
    })),
  ]);

  readonly partidoFilterOptions = computed(() => [
    { label: 'Todos los partidos', value: 'TODOS' as const },
    ...this.partidos().map((p) => ({
      label: `Lista ${p.numeroLista} - ${p.siglas}`,
      value: p.id,
    })),
  ]);

  readonly cargoOptions = computed(() =>
    this.cargos().map((c) => ({
      label: `${c.nombre} (${c.nivel})`,
      value: c.id,
    })),
  );

  readonly partidoOptions = computed(() =>
    this.partidos().map((p) => ({
      label: `Lista ${p.numeroLista} - ${p.siglas} (${p.nombre})`,
      value: p.id,
    })),
  );

  modalAbierto = false;
  modoEdicion = false;
  candidatoEditandoId: number | null = null;
  nombre = '';
  cargoId: number | null = null;
  partidoId: number | null = null;
  orden: number | null = 1;

  readonly candidatosFiltrados = computed(() => {
    const list = this.candidatos();
    const busqueda = this.terminoBusqueda().trim().toLowerCase();
    const cFiltro = this.cargoFiltroId();
    const pFiltro = this.partidoFiltroId();

    return list.filter((cand) => {
      const matchBusqueda =
        !busqueda ||
        cand.nombre.toLowerCase().includes(busqueda) ||
        this.obtenerNombreCargo(cand.cargoId)
          .toLowerCase()
          .includes(busqueda) ||
        this.obtenerNombrePartido(cand.partidoId)
          .toLowerCase()
          .includes(busqueda);

      const matchCargo = cFiltro === 'TODOS' || cand.cargoId === cFiltro;
      const matchPartido = pFiltro === 'TODOS' || cand.partidoId === pFiltro;

      return matchBusqueda && matchCargo && matchPartido;
    });
  });

  ngOnInit(): void {
    this.cargarDatos();
  }

  cargarDatos(): void {
    this.loading.set(true);
    this.error.set(null);

    forkJoin({
      cargos: this.service.listarCargos(),
      partidos: this.service.listarPartidos(),
      candidatos: this.service.listarCandidatos(),
    }).subscribe({
      next: (res) => {
        this.cargos.set(res.cargos);
        this.partidos.set(res.partidos);
        this.candidatos.set(res.candidatos);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(
          err.message ?? 'No se pudo cargar la información de candidatos',
        );
        this.loading.set(false);
      },
    });
  }

  obtenerNombreCargo(cargoId: number): string {
    const cargo = this.cargos().find((c) => c.id === cargoId);
    return cargo ? cargo.nombre : `Cargo #${cargoId}`;
  }

  obtenerNombrePartido(partidoId: number): string {
    const partido = this.partidos().find((p) => p.id === partidoId);
    return partido
      ? `Lista ${partido.numeroLista} (${partido.siglas})`
      : `Partido #${partidoId}`;
  }

  tieneFiltrosActivos(): boolean {
    return (
      this.terminoBusqueda().trim() !== '' ||
      this.cargoFiltroId() !== 'TODOS' ||
      this.partidoFiltroId() !== 'TODOS'
    );
  }

  limpiarFiltros(): void {
    this.terminoBusqueda.set('');
    this.cargoFiltroId.set('TODOS');
    this.partidoFiltroId.set('TODOS');
  }

  abrirCrear(): void {
    this.modoEdicion = false;
    this.candidatoEditandoId = null;
    this.nombre = '';
    this.cargoId = this.cargos().length > 0 ? this.cargos()[0].id : null;
    this.partidoId = this.partidos().length > 0 ? this.partidos()[0].id : null;
    this.orden = 1;
    this.modalError.set(null);
    this.modalAbierto = true;
  }

  abrirEditar(candidato: CandidatoResponseDto): void {
    this.modoEdicion = true;
    this.candidatoEditandoId = candidato.id;
    this.nombre = candidato.nombre;
    this.cargoId = candidato.cargoId;
    this.partidoId = candidato.partidoId;
    this.orden = candidato.orden;
    this.modalError.set(null);
    this.modalAbierto = true;
  }

  cerrarModal(): void {
    this.modalAbierto = false;
    this.modalError.set(null);
  }

  guardar(): void {
    if (!this.nombre.trim()) {
      this.modalError.set('El nombre del candidato es obligatorio.');
      return;
    }
    if (!this.cargoId) {
      this.modalError.set('Debe seleccionar un cargo.');
      return;
    }
    if (!this.partidoId) {
      this.modalError.set('Debe seleccionar un partido.');
      return;
    }
    if (this.orden === null || this.orden < 1) {
      this.modalError.set(
        'El orden en la papeleta debe ser mayor o igual a 1.',
      );
      return;
    }

    const payload: CandidatoRequestDto = {
      nombre: this.nombre.trim(),
      cargoId: this.cargoId,
      partidoId: this.partidoId,
      orden: this.orden,
    };

    this.submitting.set(true);
    this.modalError.set(null);

    const call$ =
      this.modoEdicion && this.candidatoEditandoId !== null
        ? this.service.actualizarCandidato(this.candidatoEditandoId, payload)
        : this.service.crearCandidato(payload);

    call$.subscribe({
      next: () => {
        this.submitting.set(false);
        this.cerrarModal();
        this.service.listarCandidatos().subscribe({
          next: (list) => this.candidatos.set(list),
        });
      },
      error: (err) => {
        this.submitting.set(false);
        this.modalError.set(err.message ?? 'Error al guardar el candidato');
      },
    });
  }

  eliminarCandidato(candidato: CandidatoResponseDto): void {
    if (
      !confirm(
        `¿Estás seguro de que deseas eliminar el candidato "${candidato.nombre}"?`,
      )
    ) {
      return;
    }

    this.service.eliminarCandidato(candidato.id).subscribe({
      next: () => {
        this.service.listarCandidatos().subscribe({
          next: (list) => this.candidatos.set(list),
        });
      },
      error: (err) => {
        alert(err.message ?? 'Error al eliminar el candidato');
      },
    });
  }

  obtenerAccionesCandidato(candidato: CandidatoResponseDto): MenuItem[] {
    return [
      {
        label: 'Editar',
        icon: 'pi pi-pencil',
        command: () => this.abrirEditar(candidato),
      },
      {
        separator: true,
      },
      {
        label: 'Eliminar',
        icon: 'pi pi-trash',
        styleClass: 'text-red-500',
        command: () => this.eliminarCandidato(candidato),
      },
    ];
  }
}
