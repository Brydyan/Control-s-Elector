import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { Router } from '@angular/router';
import {
  BadgeComponent,
  ButtonComponent,
  CardComponent,
  EmptyStateComponent,
  ErrorStateComponent,
  LoadingStateComponent,
} from '../../../../shared/ui';
import { JrvConEstado } from '../../models/observer.model';
import { ObserverService } from '../../services/observer.service';


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
  ],
  templateUrl: './mis-jrvs.page.html',
  styleUrls: ['./mis-jrvs.page.scss'],
})
export class MisJrvsPage implements OnInit {
  private readonly observerService = inject(ObserverService);
  private readonly router = inject(Router);

  jrvs: JrvConEstado[] = [];
  filteredJrvs: JrvConEstado[] = [];
  activeFilter: FilterTab = 'TODAS';
  loading = true;
  error: string | null = null;

  ngOnInit(): void {
    this.cargarJrvs();
  }

  cargarJrvs(): void {
    this.loading = true;
    this.error = null;
    this.observerService.getMisJrvs().subscribe({
      next: (data) => {
        this.jrvs = data;
        this.applyFilter(this.activeFilter);
        this.loading = false;
      },
      error: (err) => {
        this.error = 'No se pudieron cargar las mesas asignadas. Verifica tu conexión.';
        this.loading = false;
        console.error('Error fetching JRVs:', err);
      },
    });
  }

  applyFilter(filter: FilterTab): void {
    this.activeFilter = filter;
    if (filter === 'PENDIENTES') {
      this.filteredJrvs = this.jrvs.filter((j) => j.cargosPendientes > 0);
    } else if (filter === 'COMPLETADAS') {
      this.filteredJrvs = this.jrvs.filter((j) => j.cargosPendientes === 0);
    } else {
      this.filteredJrvs = [...this.jrvs];
    }
  }

  get totalJrvs(): number {
    return this.jrvs.length;
  }

  get totalPendientes(): number {
    return this.jrvs.filter((j) => j.cargosPendientes > 0).length;
  }

  get totalCompletadas(): number {
    return this.jrvs.filter((j) => j.cargosPendientes === 0).length;
  }

  seleccionarJrv(jrv: JrvConEstado): void {
    this.router.navigate(['/observer/jrv', jrv.jrvId, 'cargos']);
  }
}
