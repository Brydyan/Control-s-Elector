import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { IonIcon } from '@ionic/angular/standalone';
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
import {
  ActaFiltros,
  ActaItem,
  CantonResumen,
  CargoResumen,
  EstadoActa,
} from '../../models/coordinator.model';
import { CoordinatorService } from '../../services/coordinator.service';

@Component({
  selector: 'app-actas-lista',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    IonIcon,
    BadgeComponent,
    ButtonComponent,
    EmptyStateComponent,
    ErrorStateComponent,
    PaginatorComponent,
    SkeletonComponent,
  ],
  templateUrl: './actas-lista.page.html',
  styleUrls: ['./actas-lista.page.scss'],
})
export class ActasListaPage implements OnInit, OnDestroy {
  private readonly coordinatorService = inject(CoordinatorService);
  private readonly router = inject(Router);
  private readonly busqueda$ = new Subject<string>();
  private readonly destroy$ = new Subject<void>();

  actas: ActaItem[] = [];
  cargos: CargoResumen[] = [];
  cantones: CantonResumen[] = [];
  loading = true;
  error: string | null = null;

  esMaster = false;
  estadoSeleccionado: EstadoActa | 'TODAS' = 'TODAS';
  cargoSeleccionado: number | null = null;
  cantonSeleccionado: number | null = null;
  terminoBusqueda = '';

  page = 0;
  size = 20;
  totalElements = 0;
  totalPages = 0;

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
    this.esMaster = this.detectarRolMaster();
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
        this.cargos = cargos;
      },
      error: (err) => {
        console.error('Error cargando cargos:', err);
      },
    });
  }

  cargarCantones(): void {
    this.coordinatorService.getCantones().subscribe({
      next: (cantones) => {
        this.cantones = cantones;
      },
      error: (err) => {
        console.error('Error cargando cantones:', err);
      },
    });
  }

  /**
   * Determina si el usuario actual tiene rol MASTER para mostrar la acción
   * "Anular" en la bandeja. La autoridad real la aplica el backend con
   * @PreAuthorize("hasAuthority('acta:anular')"); este método sólo controla
   * la visibilidad en la UI y nunca es un control de seguridad.
   */
  private detectarRolMaster(): boolean {
    try {
      const sources = [
        sessionStorage.getItem('app.user.roles'),
        localStorage.getItem('app.user.roles'),
      ];
      const joined = sources.filter(Boolean).join(',').toUpperCase();
      return joined.includes('MASTER');
    } catch {
      return false;
    }
  }

  cargarActas(): void {
    this.loading = true;
    this.error = null;

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
        this.actas = paged.data;
        this.totalElements = paged.meta.totalElements;
        this.totalPages = paged.meta.totalPages;
        this.loading = false;
      },
      error: (err) => {
        this.error =
          err?.error?.message ||
          'No se pudieron cargar las actas para supervisión.';
        this.loading = false;
      },
    });
  }

  cambiarEstado(estado: EstadoActa | 'TODAS'): void {
    if (this.estadoSeleccionado === estado) return;
    this.estadoSeleccionado = estado;
    this.page = 0;
    this.cargarActas();
  }

  cambiarCargo(event: Event): void {
    const select = event.target as HTMLSelectElement;
    this.cargoSeleccionado = select.value ? Number(select.value) : null;
    this.page = 0;
    this.cargarActas();
  }

  cambiarCanton(event: Event): void {
    const select = event.target as HTMLSelectElement;
    this.cantonSeleccionado = select.value ? Number(select.value) : null;
    this.page = 0;
    this.cargarActas();
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
    return this.esMaster && acta.estado === 'APROBADA';
  }

  anular(actaId: number): void {
    const motivo = prompt(
      'Motivo de anulación (obligatorio):',
      'Datos verificados como fraudulentos',
    );
    if (!motivo || !motivo.trim()) {
      this.error = 'El motivo de anulación es obligatorio.';
      return;
    }
    this.loading = true;
    this.coordinatorService.anularActa(actaId, motivo.trim()).subscribe({
      next: () => this.cargarActas(),
      error: (err) => {
        const mapped = mapError(err);
        this.error = mapped.message;
        this.loading = false;

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
    return this.actas;
  }

  contarPorEstado(estado: EstadoActa): number {
    // Page-local count only. Exact global per-tab counts need a counts
    // endpoint (follow-up); see conteoTab for the active-tab total.
    return this.actas.filter((a) => a.estado === estado).length;
  }

  conteoTab(tab: EstadoActa | 'TODAS'): number {
    // The active filter's badge is exact (meta.totalElements of the current
    // query). Inactive tabs fall back to the page-local count.
    if (tab === this.estadoSeleccionado) return this.totalElements;
    if (tab === 'TODAS') return this.actas.length;
    return this.contarPorEstado(tab);
  }

  mapBadgeVariant(estado: EstadoActa): BadgeVariant {
    return estado;
  }

  obtenerNombreCargo(acta: ActaItem): string {
    // Prefer the backend-provided denominative (no per-row lookup needed).
    if (acta.nombreCargo) return acta.nombreCargo;
    const cargo = this.cargos.find((c) => c.id === acta.cargoId);
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
}
