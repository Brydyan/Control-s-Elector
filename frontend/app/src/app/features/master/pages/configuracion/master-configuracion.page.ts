import { CommonModule } from '@angular/common';
import { Component, OnInit, OnDestroy, inject, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Subject, takeUntil, forkJoin } from 'rxjs';
import { DialogService } from 'primeng/dynamicdialog';

import {
  CardComponent,
  ErrorStateComponent,
  KpiCardComponent,
  LoadingStateComponent,
  TableCellTemplate,
  TableColumn,
  TableComponent,
} from '../../../../shared/ui';
import { ConfiguracionApi } from '../../data/configuracion.api';
import {
  CargoDto,
  PartidoDto,
  CandidatoDto,
} from '../../domain/models/configuracion.model';

/**
 * T-FE-16 Fase B.1: MasterConfiguracionPage — Gestión de Configuración Electoral
 *
 * Componente tabulado único (D1 decision) que unifica 3 entidades:
 * - Cargos (dignidades)
 * - Partidos políticos
 * - Candidatos (por cargo y partido)
 *
 * **Escenarios**:
 * - S1: Cargar datos de las 3 entidades en un solo payload (sin duplicar HTTP).
 * - S2: Cambiar entre tabs sin nuevas llamadas HTTP.
 * - S3: Mostrar resumen de totales consolidados (total cargos, partidos, candidatos).
 * - S4: Tablas con acciones CRUD (editar, eliminar) por fila.
 * - S5: Modales de creación y edición.
 * - S6: Manejo de error con retry.
 *
 * **Accesibilidad**:
 * - role="tablist" en barra de tabs
 * - aria-label en tabs y botones
 * - role="alert" para estados de carga/error
 */
@Component({
  selector: 'app-master-configuracion',
  standalone: true,
  imports: [
    CommonModule,
    CardComponent,
    KpiCardComponent,
    LoadingStateComponent,
    ErrorStateComponent,
    TableComponent,
    TableCellTemplate,
  ],
  providers: [DialogService],
  templateUrl: './master-configuracion.page.html',
  styleUrls: ['./master-configuracion.page.scss'],
})
export class MasterConfiguracionPage implements OnInit, OnDestroy {
  private readonly api = inject(ConfiguracionApi);
  private readonly route = inject(ActivatedRoute);
  private readonly dialogService = inject(DialogService);
  private readonly destroy$ = new Subject<void>();

  // Estado de carga
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);

  // Datos cargados
  readonly cargosData = signal<CargoDto[]>([]);
  readonly partidosData = signal<PartidoDto[]>([]);
  readonly candidatosData = signal<CandidatoDto[]>([]);

  // Tab activo (desde ruta data o default)
  readonly tabActiva = signal<'cargos' | 'partidos' | 'candidatos'>('cargos');

  readonly cargosColumns: TableColumn<CargoDto>[] = [
    { key: 'nombre', header: 'Nombre', sortable: true },
    {
      key: 'nivel',
      header: 'Nivel Territorial',
      sortable: true,
      value: (c) => c.nivel || c.nivelTerritorial || '—',
    },
    {
      key: 'puestos',
      header: 'Puestos / Orden',
      sortable: true,
      width: '8rem',
      value: (c) =>
        c.puestos !== undefined ? `${c.puestos} puestos` : (c.orden ?? '—'),
    },
  ];

  readonly partidosColumns: TableColumn<PartidoDto>[] = [
    { key: 'numeroLista', header: 'N° Lista', sortable: true, width: '7rem' },
    { key: 'siglas', header: 'Siglas', sortable: true, width: '8rem' },
    { key: 'nombre', header: 'Nombre de la Organización', sortable: true },
  ];

  readonly candidatosColumns: TableColumn<CandidatoDto>[] = [
    {
      key: 'orden',
      header: 'Orden',
      sortable: true,
      width: '6rem',
      value: (c) => c.orden ?? c.ordenLista ?? '—',
    },
    { key: 'nombre', header: 'Nombre del Candidato', sortable: true },
    {
      key: 'cargoId',
      header: 'Cargo',
      sortable: true,
      value: (c) => {
        const cargo = this.cargosData().find((item) => item.id === c.cargoId);
        return cargo ? cargo.nombre : `Cargo #${c.cargoId}`;
      },
    },
    {
      key: 'partidoId',
      header: 'Partido',
      sortable: true,
      value: (c) => {
        const partido = this.partidosData().find(
          (item) => item.id === c.partidoId,
        );
        return partido
          ? `${partido.siglas || partido.nombre}`
          : `Partido #${c.partidoId}`;
      },
    },
  ];

  obtenerAccionesCargo(cargo: CargoDto) {
    return [
      {
        label: 'Editar',
        icon: 'pi pi-pencil',
        command: () => this.abrirModalCargo('edit', cargo),
      },
      {
        separator: true,
      },
      {
        label: 'Eliminar',
        icon: 'pi pi-trash',
        styleClass: 'text-red-500',
        command: () => this.abrirConfirmEliminarCargo(cargo),
      },
    ];
  }

  obtenerAccionesPartido(partido: PartidoDto) {
    return [
      {
        label: 'Editar',
        icon: 'pi pi-pencil',
        command: () => this.abrirModalPartido('edit', partido),
      },
      {
        separator: true,
      },
      {
        label: 'Eliminar',
        icon: 'pi pi-trash',
        styleClass: 'text-red-500',
        command: () => this.abrirConfirmEliminarPartido(partido),
      },
    ];
  }

  obtenerAccionesCandidato(candidato: CandidatoDto) {
    return [
      {
        label: 'Editar',
        icon: 'pi pi-pencil',
        command: () => this.abrirModalCandidato('edit', candidato),
      },
      {
        separator: true,
      },
      {
        label: 'Eliminar',
        icon: 'pi pi-trash',
        styleClass: 'text-red-500',
        command: () => this.abrirConfirmEliminarCandidato(candidato),
      },
    ];
  }

  ngOnInit(): void {
    // Preseleccionar tab desde ruta data
    const tabFromRoute = this.route.snapshot.data['tab'] as
      'cargos' | 'partidos' | 'candidatos' | undefined;
    if (tabFromRoute) {
      this.tabActiva.set(tabFromRoute);
    }

    this.cargarConfiguracion();
  }

  /**
   * Carga todos los datos (Cargos, Partidos, Candidatos) en un solo flujo.
   * Implementa S1: un solo payload HTTP para todas las entidades.
   * Usa forkJoin para ejecutar en paralelo.
   */
  cargarConfiguracion(): void {
    this.loading.set(true);
    this.error.set(null);

    forkJoin({
      cargos: this.api.getCargos(),
      partidos: this.api.getPartidos(),
      candidatos: this.api.getCandidatos(),
    })
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: ({ cargos, partidos, candidatos }) => {
          this.cargosData.set(cargos);
          this.partidosData.set(partidos);
          this.candidatosData.set(candidatos);
          this.loading.set(false);
        },
        error: (err) => {
          console.error('Error al cargar configuración:', err);
          this.error.set(
            'No se pudo cargar la configuración electoral. Por favor intenta nuevamente.',
          );
          this.loading.set(false);
        },
      });
  }

  /**
   * Cambia a una tab diferente sin nuevas llamadas HTTP.
   * Implementa S2: reactividad pura basada en signals.
   */
  cambiarTab(tab: 'cargos' | 'partidos' | 'candidatos'): void {
    this.tabActiva.set(tab);
  }

  /**
   * Retorna el total de registros para cada entidad.
   */
  get totalCargos(): number {
    return this.cargosData().length;
  }

  get totalPartidos(): number {
    return this.partidosData().length;
  }

  get totalCandidatos(): number {
    return this.candidatosData().length;
  }

  // ============================================================================
  // DISPATCHER — Abre modal según tab activa
  // ============================================================================

  abrirModalPorTab(): void {
    const tab = this.tabActiva();
    if (tab === 'cargos') {
      this.abrirModalCargo('create');
    } else if (tab === 'partidos') {
      this.abrirModalPartido('create');
    } else if (tab === 'candidatos') {
      this.abrirModalCandidato('create');
    }
  }

  // ============================================================================
  // CRUD MODALS — CARGOS
  // ============================================================================

  abrirModalCargo(modo: 'create' | 'edit', cargo?: CargoDto): void {
    this.dialogService
      .open(CargoModalComponent, {
        header: modo === 'create' ? 'Nuevo Cargo' : `Editar: ${cargo?.nombre}`,
        width: '500px',
        modal: true,
        data: { mode: modo, cargo },
      })!
      .onClose.subscribe((result) => {
        if (result?.success) {
          this.invalidarCargosCambia();
          this.cargarConfiguracion();
        }
      });
  }

  abrirConfirmEliminarCargo(cargo: CargoDto): void {
    this.dialogService
      .open(ConfirmDialogComponent, {
        header: 'Eliminar Cargo',
        width: '400px',
        modal: true,
        data: { message: `¿Estás seguro de eliminar "${cargo.nombre}"?` },
      })!
      .onClose.subscribe((confirmed) => {
        if (confirmed) {
          this.eliminarCargo(cargo.id);
        }
      });
  }

  private eliminarCargo(id: number): void {
    this.api.deleteCargo(id).subscribe({
      next: () => {
        this.invalidarCargosCambia();
        this.cargarConfiguracion();
      },
      error: (err) => {
        const message =
          err.status === 409
            ? 'No se puede eliminar: existen candidatos vinculados a este cargo'
            : 'Error al eliminar el cargo';
        this.error.set(message);
      },
    });
  }

  // ============================================================================
  // CRUD MODALS — PARTIDOS
  // ============================================================================

  abrirModalPartido(modo: 'create' | 'edit', partido?: PartidoDto): void {
    this.dialogService
      .open(PartidoModalComponent, {
        header:
          modo === 'create' ? 'Nuevo Partido' : `Editar: ${partido?.nombre}`,
        width: '500px',
        modal: true,
        data: { mode: modo, partido },
      })!
      .onClose.subscribe((result) => {
        if (result?.success) {
          this.invalidarPartidosCambia();
          this.cargarConfiguracion();
        }
      });
  }

  abrirConfirmEliminarPartido(partido: PartidoDto): void {
    this.dialogService
      .open(ConfirmDialogComponent, {
        header: 'Eliminar Partido',
        width: '400px',
        modal: true,
        data: { message: `¿Estás seguro de eliminar "${partido.nombre}"?` },
      })!
      .onClose.subscribe((confirmed) => {
        if (confirmed) {
          this.eliminarPartido(partido.id);
        }
      });
  }

  private eliminarPartido(id: number): void {
    this.api.deletePartido(id).subscribe({
      next: () => {
        this.invalidarPartidosCambia();
        this.cargarConfiguracion();
      },
      error: (err) => {
        const message =
          err.status === 409
            ? 'No se puede eliminar: existen candidatos vinculados a este partido'
            : 'Error al eliminar el partido';
        this.error.set(message);
      },
    });
  }

  // ============================================================================
  // CRUD MODALS — CANDIDATOS
  // ============================================================================

  abrirModalCandidato(modo: 'create' | 'edit', candidato?: CandidatoDto): void {
    this.dialogService
      .open(CandidatoModalComponent, {
        header:
          modo === 'create'
            ? 'Nuevo Candidato'
            : `Editar: ${candidato?.nombre}`,
        width: '500px',
        modal: true,
        data: {
          mode: modo,
          candidato,
          cargos: this.cargosData(),
          partidos: this.partidosData(),
        },
      })!
      .onClose.subscribe((result) => {
        if (result?.success) {
          this.invalidarCandidatosCambia();
          this.cargarConfiguracion();
        }
      });
  }

  abrirConfirmEliminarCandidato(candidato: CandidatoDto): void {
    this.dialogService
      .open(ConfirmDialogComponent, {
        header: 'Eliminar Candidato',
        width: '400px',
        modal: true,
        data: { message: `¿Estás seguro de eliminar a "${candidato.nombre}"?` },
      })!
      .onClose.subscribe((confirmed) => {
        if (confirmed) {
          this.eliminarCandidato(candidato.id);
        }
      });
  }

  private eliminarCandidato(id: number): void {
    this.api.deleteCandidato(id).subscribe({
      next: () => {
        this.invalidarCandidatosCambia();
        this.cargarConfiguracion();
      },
      error: (err) => {
        this.error.set('Error al eliminar el candidato');
      },
    });
  }

  // ============================================================================
  // INVALIDAR CACHÉ
  // ============================================================================

  private invalidarCargosCambia(): void {
    this.api.invalidateCache('cargos');
  }

  private invalidarPartidosCambia(): void {
    this.api.invalidateCache('partidos');
  }

  private invalidarCandidatosCambia(): void {
    this.api.invalidateCache('candidatos');
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
