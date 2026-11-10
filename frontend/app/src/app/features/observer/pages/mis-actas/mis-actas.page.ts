import { CommonModule } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import {
  EmptyStateComponent,
  ErrorStateComponent,
  LoadingStateComponent,
} from '../../../../shared/ui';
import { TableComponent } from '../../../../shared/ui/table/table.component';
import { TableColumn } from '../../../../shared/ui/table/table.model';
import {
  ActaDTO,
  ActaFilters,
  MisActasService,
} from '../../data/mis-actas.api';

@Component({
  selector: 'app-mis-actas',
  standalone: true,
  imports: [
    CommonModule,
    TableComponent,
    LoadingStateComponent,
    ErrorStateComponent,
    EmptyStateComponent,
  ],
  templateUrl: './mis-actas.page.html',
  styleUrl: './mis-actas.page.scss',
})
export class MisActasPage implements OnInit {
  private readonly misActasService = inject(MisActasService);
  private readonly router = inject(Router);

  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly actas = signal<ActaDTO[]>([]);
  readonly totalItems = signal(0);
  readonly currentPage = signal(0);
  readonly pageSize = signal(10);

  readonly columns: TableColumn<ActaDTO>[] = [
    {
      key: 'numeroActa',
      header: '#Acta',
      width: '100px',
      align: 'start',
    },
    {
      key: 'jrvNombre',
      header: 'JRV',
      width: '120px',
      align: 'start',
    },
    {
      key: 'fechaTransmision',
      header: 'Fecha Transmisión',
      width: '160px',
      align: 'start',
      value: (row) => {
        const date = new Date(row.fechaTransmision);
        return date.toLocaleDateString('es-ES', {
          year: 'numeric',
          month: '2-digit',
          day: '2-digit',
          hour: '2-digit',
          minute: '2-digit',
        });
      },
    },
    {
      key: 'estado',
      header: 'Estado',
      width: '120px',
      align: 'center',
    },
  ];

  private filters = signal<ActaFilters>({
    estado: null,
    jrvId: null,
    page: 0,
    size: 10,
  });

  ngOnInit(): void {
    this.cargar();
  }

  cargar(): void {
    this.loading.set(true);
    this.error.set(null);

    this.misActasService.getMisActas(this.filters()).subscribe({
      next: (response) => {
        this.actas.set(response.content);
        this.totalItems.set(response.totalElements);
        this.loading.set(false);
      },
      error: (err) => {
        console.error('Error loading actas:', err);
        if (err.status === 403) {
          this.error.set(
            'No tienes permisos para ver estas actas. Verifica que tengas JRVs asignadas.',
          );
          // Redirect after a short delay to allow user to see the error
          setTimeout(() => {
            this.router.navigate(['/dashboard']);
          }, 2000);
        } else {
          this.error.set(
            'No se pudieron cargar las actas. Por favor, intenta más tarde.',
          );
        }
        this.loading.set(false);
      },
    });
  }

  onPageChange(page: number): void {
    this.currentPage.set(page);
    this.filters.set({ ...this.filters(), page });
    this.cargar();
  }

  onRowClick(acta: ActaDTO): void {
    this.router.navigate(['/observer/mis-actas', acta.id, 'detalles']);
  }

  volver(): void {
    this.router.navigate(['/observer/mis-jrvs']);
  }

  reintentar(): void {
    this.cargar();
  }
}
