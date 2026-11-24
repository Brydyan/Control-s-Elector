import { CommonModule } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Select } from 'primeng/select';
import { Skeleton } from 'primeng/skeleton';
import { TableModule } from 'primeng/table';
import { Message } from 'primeng/message';
import { ButtonModule } from 'primeng/button';
import { EmptyStateComponent } from '../../../../shared/ui';
import {
  RecintoApi,
  RecintoResponse,
  RecintoFiltros,
} from '../../data/recinto.api';
import { RecintoFormModalComponent } from './recinto-form.modal';
import { RecintoJrvsModalComponent } from './recinto-jrvs.modal';

@Component({
  selector: 'app-recintos-list',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    ButtonModule,
    EmptyStateComponent,
    Select,
    Skeleton,
    TableModule,
    Message,
    RecintoFormModalComponent,
    RecintoJrvsModalComponent,
  ],
  templateUrl: './recintos-list.page.html',
  styleUrls: ['./recintos-list.page.scss'],
})
export class RecintosListPage implements OnInit {
  private readonly api = inject(RecintoApi);

  readonly recintos = signal<RecintoResponse[]>([]);
  readonly loading = signal(true);
  readonly submitting = signal(false);
  readonly error = signal<string | null>(null);

  page = 0;
  size = 20;
  readonly totalElements = signal(0);

  modalFormAbierto = false;
  modalJrvsAbierto = false;
  recintoSeleccionado: RecintoResponse | null = null;
  modoEdicion = false;

  terminoBusqueda = '';
  estadoSeleccionado = '';

  ngOnInit(): void {
    this.cargarRecintos();
  }

  cargarRecintos(): void {
    this.loading.set(true);
    this.error.set(null);

    const filtros: RecintoFiltros = {
      page: this.page,
      size: this.size,
    };

    if (this.estadoSeleccionado) {
      filtros.estado = this.estadoSeleccionado;
    }

    this.api.listarRecintos(filtros).subscribe({
      next: (response) => {
        this.recintos.set(response.data || []);
        this.totalElements.set(response.meta?.totalElements || 0);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(err.message || 'Error al cargar recintos');
        this.loading.set(false);
      },
    });
  }

  abrirFormularioCrear(): void {
    this.modoEdicion = false;
    this.recintoSeleccionado = null;
    this.modalFormAbierto = true;
  }

  abrirFormularioEditar(recinto: RecintoResponse): void {
    this.modoEdicion = true;
    this.recintoSeleccionado = recinto;
    this.modalFormAbierto = true;
  }

  abrirModalJrvs(recinto: RecintoResponse): void {
    this.recintoSeleccionado = recinto;
    this.modalJrvsAbierto = true;
  }

  confirmarEliminar(recinto: RecintoResponse): void {
    if (
      confirm(
        `¿Está seguro de que desea eliminar el recinto "${recinto.nombre}"?`,
      )
    ) {
      this.eliminarRecinto(recinto.id);
    }
  }

  eliminarRecinto(id: number): void {
    this.submitting.set(true);
    this.api.eliminarRecinto(id).subscribe({
      next: () => {
        this.submitting.set(false);
        this.cargarRecintos();
      },
      error: (err) => {
        this.submitting.set(false);
        this.error.set(err.message || 'Error al eliminar recinto');
      },
    });
  }

  onFormGuardado(): void {
    this.modalFormAbierto = false;
    this.recintoSeleccionado = null;
    this.cargarRecintos();
  }

  onFormCancelado(): void {
    this.modalFormAbierto = false;
    this.recintoSeleccionado = null;
  }

  onJrvsGuardado(): void {
    this.modalJrvsAbierto = false;
    this.recintoSeleccionado = null;
    this.cargarRecintos();
  }

  onJrvsCancelado(): void {
    this.modalJrvsAbierto = false;
    this.recintoSeleccionado = null;
  }

  onPaginationChange(event: any): void {
    this.page = event.page || 0;
    this.size = event.rows || 20;
    this.cargarRecintos();
  }

  onEstadoChange(): void {
    this.page = 0;
    this.cargarRecintos();
  }
}
