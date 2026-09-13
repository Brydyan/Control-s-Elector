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
  LoadingStateComponent,
} from '../../../../shared/ui';
import { PaginatorComponent } from '../../../../shared/components/paginator/paginator.component';
import {
  ActaFiltros,
  ActaItem,
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
    LoadingStateComponent,
    PaginatorComponent,
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
  loading = true;
  error: string | null = null;

  estadoSeleccionado: EstadoActa | 'TODAS' = 'TODAS';
  cargoSeleccionado: number | null = null;
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
    this.cargarCargos();
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

  cargarActas(): void {
    this.loading = true;
    this.error = null;

    const filtros: ActaFiltros = {
      estado: this.estadoSeleccionado,
      cargoId: this.cargoSeleccionado ?? undefined,
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

  onBusquedaChange(): void {
    this.busqueda$.next(this.terminoBusqueda);
  }

  limpiarBusqueda(): void {
    this.terminoBusqueda = '';
    this.page = 0;
    this.cargarActas();
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
