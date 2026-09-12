import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { IonIcon } from '@ionic/angular/standalone';
import {
  BadgeComponent,
  BadgeVariant,
  ButtonComponent,
  CardComponent,
  EmptyStateComponent,
  ErrorStateComponent,
  LoadingStateComponent,
} from '../../../../shared/ui';
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
  ],
  templateUrl: './actas-lista.page.html',
  styleUrls: ['./actas-lista.page.scss'],
})
export class ActasListaPage implements OnInit {
  private readonly coordinatorService = inject(CoordinatorService);
  private readonly router = inject(Router);

  actas: ActaItem[] = [];
  cargos: CargoResumen[] = [];
  loading = true;
  error: string | null = null;

  estadoSeleccionado: EstadoActa | 'TODAS' = 'TODAS';
  cargoSeleccionado: number | null = null;
  terminoBusqueda = '';

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
    this.cargarActas();
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
    };

    this.coordinatorService.getActas(filtros).subscribe({
      next: (actas) => {
        this.actas = actas;
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
    this.cargarActas();
  }

  cambiarCargo(event: Event): void {
    const select = event.target as HTMLSelectElement;
    this.cargoSeleccionado = select.value ? Number(select.value) : null;
    this.cargarActas();
  }

  get actasFiltradas(): ActaItem[] {
    if (!this.terminoBusqueda.trim()) {
      return this.actas;
    }
    const query = this.terminoBusqueda.toLowerCase().trim();
    return this.actas.filter((a) => {
      const jrvMatch = a.jrvId.toString().includes(query);
      const idMatch = a.id.toString().includes(query);
      const estadoMatch = a.estado.toLowerCase().includes(query);
      return jrvMatch || idMatch || estadoMatch;
    });
  }

  contarPorEstado(estado: EstadoActa): number {
    return this.actas.filter((a) => a.estado === estado).length;
  }

  mapBadgeVariant(estado: EstadoActa): BadgeVariant {
    return estado;
  }

  obtenerNombreCargo(cargoId: number): string {
    const cargo = this.cargos.find((c) => c.id === cargoId);
    return cargo ? cargo.nombre : `Cargo #${cargoId}`;
  }

  verDetalle(actaId: number): void {
    this.router.navigate(['/coordinator/actas', actaId]);
  }
}
