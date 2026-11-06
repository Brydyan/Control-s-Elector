import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { IconField } from 'primeng/iconfield';
import { InputIcon } from 'primeng/inputicon';
import { InputText } from 'primeng/inputtext';
import { Select } from 'primeng/select';
import { Skeleton } from 'primeng/skeleton';
import { Tab, TabList, Tabs } from 'primeng/tabs';
import { TableModule } from 'primeng/table';
import { Menu } from 'primeng/menu';
import { MenuItem } from 'primeng/api';
import { Subject, debounceTime, distinctUntilChanged, takeUntil } from 'rxjs';
import {
  BadgeComponent,
  BadgeVariant,
  ButtonComponent,
  EmptyStateComponent,
  ErrorStateComponent,
  SkeletonComponent,
} from '../../../../shared/ui';
import { PaginatorComponent } from '../../../../shared/components/paginator/paginator.component';
import { mapError } from '../../../../shared/errors/error-handler';
import { AuthStore } from '../../../auth/services/auth.store';
import {
  ActaFiltros,
  ActaItem,
  CantonResumen,
  CargoResumen,
  EstadoActa,
} from '../../domain/models/coordinator.model';
import { CoordinatorApi } from '../../data/coordinator.api';

@Component({
  selector: 'app-actas-lista',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    BadgeComponent,
    ButtonComponent,
    EmptyStateComponent,
    ErrorStateComponent,
    PaginatorComponent,
    SkeletonComponent,
    IconField,
    InputIcon,
    InputText,
    Select,
    Skeleton,
    Tab,
    TabList,
    Tabs,
    TableModule,
    Menu,
  ],
  templateUrl: './actas-lista.page.html',
  styleUrls: ['./actas-lista.page.scss'],
})
export class ActasListaPage implements OnInit, OnDestroy {
  private readonly coordinatorService = inject(CoordinatorApi);
  private readonly router = inject(Router);
  private readonly authStore = inject(AuthStore);
  private readonly busqueda$ = new Subject<string>();
  private readonly destroy$ = new Subject<void>();

  readonly actas = signal<ActaItem[]>([]);
  readonly cargos = signal<CargoResumen[]>([]);
  readonly cantones = signal<CantonResumen[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);

  /**
   * MASTER-only controls ("Anular" in the tray). The role comes from the
   * in-memory session, so a session restored after a reload lights the
   * controls up on its own.
   *
   * La autoridad real la aplica el backend con
   * @PreAuthorize("hasAuthority('acta:anular')"); esto sólo controla la
   * visibilidad en la UI y nunca es un control de seguridad.
   */
  readonly esMaster = this.authStore.isMaster;
  estadoSeleccionado: EstadoActa | 'TODAS' = 'TODAS';
  cargoSeleccionado: number | null = null;
  cantonSeleccionado: number | null = null;
  terminoBusqueda = '';

  page = 0;
  size = 20;
  readonly totalElements = signal(0);
  readonly totalPages = signal(0);

  readonly tabsEstado: Array<{
    estado: EstadoActa | 'TODAS';
    label: string;
  }> = [
    { estado: 'TODAS', label: 'Todas' },
    { estado: 'ENVIADA', label: 'Enviadas' },
    { estado: 'EN_DISPUTA', label: 'En Disputa' },
    { estado: 'CORREGIDA', label: 'Corregidas' },
    { estado: 'APROBADA', label: 'Aprobadas' },
  ];

  ngOnInit(): void {
    this.cargarCargos();
    this.cargarCantones();
    this.busqueda$
      .pipe(debounceTime(300), distinctUntilChanged(), takeUntil(this.destroy$))
      .subscribe(() => {
        this.page = 0;
        this.cargarActas();
      });
    this.cargarActas();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  cargarCargos(): void {
    this.coordinatorService.getCargos().subscribe({
      next: (cargos) => {
        this.cargos.set(cargos);
      },
      error: (err) => {
        console.error('Error cargando cargos:', err);
      },
    });
  }

  cargarCantones(): void {
    this.coordinatorService.getCantones().subscribe({
      next: (cantones) => {
        this.cantones.set(cantones);
      },
      error: (err) => {
        console.error('Error cargando cantones:', err);
      },
    });
  }

  cargarActas(): void {
    this.loading.set(true);
    this.error.set(null);

    const filtros: ActaFiltros = {
      estado: this.estadoSeleccionado,

      cargoId: this.cargoSeleccionado ?? undefined,

      cantonId: this.cantonSeleccionado ?? undefined,

      search: this.terminoBusqueda.trim() || undefined,

      page: this.page,

      size: this.size,
    };

    this.coordinatorService.getActas(filtros).subscribe({
      next: (paged) => {
        this.actas.set(paged.data);
        this.totalElements.set(paged.meta.totalElements);
        this.totalPages.set(paged.meta.totalPages);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(
          err?.error?.message ||
            'No se pudieron cargar las actas para supervisión.',
        );
        this.loading.set(false);
      },
    });
  }

  cambiarEstado(estado: EstadoActa | 'TODAS'): void {
    if (this.estadoSeleccionado === estado) return;
    this.estadoSeleccionado = estado;
    this.page = 0;
    this.cargarActas();
  }

  /** The tab strip reports the value as a loose string; narrow it back. */
  onEstadoTabChange(value: string | number | undefined): void {
    const estado = String(value ?? 'TODAS') as EstadoActa | 'TODAS';
    this.cambiarEstado(estado);
  }

  cambiarCargo(value: number | null): void {
    this.cargoSeleccionado = value === null ? null : Number(value);
    this.page = 0;
    this.cargarActas();
  }

  cambiarCanton(value: number | null): void {
    this.cantonSeleccionado = value === null ? null : Number(value);
    this.page = 0;
    this.cargarActas();
  }

  /** Options of the dignity filter, for the PrimeNG select. */
  get cargoOptions(): Array<{ label: string; value: number | null }> {
    return [
      { label: 'Todas las Dignidades', value: null },
      ...this.cargos().map((c) => ({
        label: `${c.nombre} (${c.nivel})`,
        value: c.id,
      })),
    ];
  }

  /** Options of the canton filter, for the PrimeNG select. */
  get cantonOptions(): Array<{ label: string; value: number | null }> {
    return [
      { label: 'Todos los Cantones', value: null },
      ...this.cantones().map((c) => ({
        label: `${c.nombre} (${c.codigo})`,
        value: c.id,
      })),
    ];
  }

  onBusquedaChange(): void {
    this.busqueda$.next(this.terminoBusqueda);
  }

  limpiarBusqueda(): void {
    this.terminoBusqueda = '';
    this.page = 0;
    this.cargarActas();
  }

  limpiarFiltros(): void {
    this.terminoBusqueda = '';
    this.cargoSeleccionado = null;
    this.cantonSeleccionado = null;
    this.estadoSeleccionado = 'TODAS';
    this.page = 0;
    this.cargarActas();
  }

  tieneFiltrosActivos(): boolean {
    return (
      this.terminoBusqueda.trim().length > 0 ||
      this.cargoSeleccionado !== null ||
      this.cantonSeleccionado !== null ||
      this.estadoSeleccionado !== 'TODAS'
    );
  }

  puedeAnular(acta: ActaItem): boolean {
    return this.esMaster() && acta.estado === 'APROBADA';
  }

  anular(actaId: number): void {
    const motivo = prompt(
      'Motivo de anulación (obligatorio):',
      'Datos verificados como fraudulentos',
    );
    if (!motivo || !motivo.trim()) {
      this.error.set('El motivo de anulación es obligatorio.');
      return;
    }
    this.loading.set(true);
    this.coordinatorService.anularActa(actaId, motivo.trim()).subscribe({
      next: () => this.cargarActas(),
      error: (err) => {
        const mapped = mapError(err);
        this.error.set(mapped.message);
        this.loading.set(false);

        // W4 FIX (S7): 409 Conflict — auto-reload like dispute/correct actions
        // When another session annuls the acta concurrently, reload the list
        // to reflect the current state and prevent stale data display.
        if (mapped.type === 'conflict') {
          this.cargarActas();
        }
      },
    });
  }

  onPageChange(next: number): void {
    this.page = next;
    this.cargarActas();
  }

  onSizeChange(next: number): void {
    this.size = next;
    this.page = 0;
    this.cargarActas();
  }

  get actasFiltradas(): ActaItem[] {
    // Server-side filtering: the backend already applied estado/cargo/search.
    return this.actas();
  }

  contarPorEstado(estado: EstadoActa): number {
    // Page-local count only. Exact global per-tab counts need a counts
    // endpoint (follow-up); see conteoTab for the active-tab total.
    return this.actas().filter((a) => a.estado === estado).length;
  }

  conteoTab(tab: EstadoActa | 'TODAS'): number {
    // The active filter's badge is exact (meta.totalElements of the current
    // query). Inactive tabs fall back to the page-local count.
    if (tab === this.estadoSeleccionado) return this.totalElements();
    if (tab === 'TODAS') return this.actas().length;
    return this.contarPorEstado(tab);
  }

  mapBadgeVariant(estado: EstadoActa): BadgeVariant {
    return estado;
  }

  obtenerNombreCargo(acta: ActaItem): string {
    // Prefer the backend-provided denominative (no per-row lookup needed).
    if (acta.nombreCargo) return acta.nombreCargo;
    const cargo = this.cargos().find((c) => c.id === acta.cargoId);
    return cargo ? cargo.nombre : `Cargo #${acta.cargoId}`;
  }

  obtenerNumeroJrv(acta: ActaItem): string {
    // Prefer the backend-provided JRV number; fall back to the raw id.
    if (acta.numeroJrv) return acta.numeroJrv;
    return `JRV-${acta.jrvId}`;
  }

  checksumTexto(acta: ActaItem): string | null {
    // Full checksum needs the candidate-votes subtotal from the backend.
    if (
      acta.totalVotosCandidatos === undefined ||
      acta.totalVotosCandidatos === null
    ) {
      return null;
    }
    const suma =
      acta.totalVotosCandidatos +
      (acta.votosBlancos || 0) +
      (acta.votosNulos || 0);
    const total = acta.totalSufragantes || 0;
    return `${suma}/${total}`;
  }

  checksumValido(acta: ActaItem): boolean | null {
    if (
      acta.totalVotosCandidatos === undefined ||
      acta.totalVotosCandidatos === null
    ) {
      return null;
    }
    const suma =
      acta.totalVotosCandidatos +
      (acta.votosBlancos || 0) +
      (acta.votosNulos || 0);
    return suma === (acta.totalSufragantes || 0);
  }

  verDetalle(actaId: number): void {
    this.router.navigate(['/coordinator/actas', actaId]);
  }

  obtenerAccionesActa(acta: ActaItem): MenuItem[] {
    const items: MenuItem[] = [
      {
        label: 'Revisar',
        icon: 'pi pi-eye',
        command: () => this.verDetalle(acta.id),
      },
    ];

    if (this.puedeAnular(acta)) {
      items.push({
        label: 'Anular',
        icon: 'pi pi-trash',
        styleClass: 'text-red-500',
        command: () => this.anular(acta.id),
      });
    }

    return items;
  }
}
