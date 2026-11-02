import { CommonModule } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Tab, TabList, Tabs } from 'primeng/tabs';
import {
  BadgeComponent,
  ButtonComponent,
  CardComponent,
  EmptyStateComponent,
  ErrorStateComponent,
  LoadingStateComponent,
} from '../../../../shared/ui';
import { JrvConEstado } from '../../domain/models/observer.model';
import { ObserverApi } from '../../data/observer.api';

type FilterTab = 'TODAS' | 'PENDIENTES' | 'COMPLETADAS';

@Component({
  selector: 'app-mis-jrvs',
  standalone: true,
  imports: [
    CommonModule,
    CardComponent,
    BadgeComponent,
    ButtonComponent,
    LoadingStateComponent,
    EmptyStateComponent,
    ErrorStateComponent,
    Tab,
    TabList,
    Tabs,
  ],
  templateUrl: './mis-jrvs.page.html',
  styleUrls: ['./mis-jrvs.page.scss'],
})
export class MisJrvsPage implements OnInit {
  private readonly observerService = inject(ObserverApi);
  private readonly router = inject(Router);

  readonly jrvs = signal<JrvConEstado[]>([]);
  filteredJrvs: JrvConEstado[] = [];
  activeFilter: FilterTab = 'TODAS';

  /** The tab strip reports a loose value; narrow it back. */
  onFilterTabChange(value: string | number | undefined): void {
    this.applyFilter(String(value ?? 'TODAS') as FilterTab);
  }
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.cargarJrvs();
  }

  cargarJrvs(): void {
    this.loading.set(true);
    this.error.set(null);
    this.observerService.getMisJrvs().subscribe({
      next: (data) => {
        this.jrvs.set(data);
        this.applyFilter(this.activeFilter);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(
          'No se pudieron cargar las mesas asignadas. Verifica tu conexión.',
        );
        this.loading.set(false);
        console.error('Error fetching JRVs:', err);
      },
    });
  }

  applyFilter(filter: FilterTab): void {
    this.activeFilter = filter;
    if (filter === 'PENDIENTES') {
      this.filteredJrvs = this.jrvs().filter((j) => j.cargosPendientes > 0);
    } else if (filter === 'COMPLETADAS') {
      this.filteredJrvs = this.jrvs().filter((j) => j.cargosPendientes === 0);
    } else {
      this.filteredJrvs = [...this.jrvs()];
    }
  }

  get totalJrvs(): number {
    return this.jrvs().length;
  }

  get totalPendientes(): number {
    return this.jrvs().filter((j) => j.cargosPendientes > 0).length;
  }

  get totalCompletadas(): number {
    return this.jrvs().filter((j) => j.cargosPendientes === 0).length;
  }

  seleccionarJrv(jrv: JrvConEstado): void {
    this.router.navigate(['/observer/jrv', jrv.jrvId, 'cargos']);
  }
}
