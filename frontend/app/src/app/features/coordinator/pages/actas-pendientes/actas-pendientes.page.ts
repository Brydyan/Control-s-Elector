import { CommonModule } from '@angular/common';
import { Component, OnInit, OnDestroy, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Tab, TabList, Tabs } from 'primeng/tabs';
import { TableModule } from 'primeng/table';
import { Dialog } from 'primeng/dialog';
import { InputText } from 'primeng/inputtext';
import { Skeleton } from 'primeng/skeleton';
import { Message } from 'primeng/message';
import {
  ButtonComponent,
  EmptyStateComponent,
  ErrorStateComponent,
  SkeletonComponent,
} from '../../../../shared/ui';
import { CoordinatorApi } from '../../data/coordinator.api';
import { NotificacionApi } from '../../data/notificacion.api';
import { ActaCardComponent } from './acta-card.component';
import { ActaDetalleModalComponent } from './acta-detalle.modal';
import { RechazoFormModalComponent } from './rechazo-form.modal';
import { Subject, interval } from 'rxjs';
import { takeUntil, switchMap, tap } from 'rxjs/operators';

export interface ActaSummary {
  id: number;
  jrvNumero: string;
  cargo: string;
  observer: string;
  estado: 'ENVIADA' | 'APROBADA' | 'EN_DISPUTA' | 'CORREGIDA';
  fechaRegistro: string;
  totalMesas?: number;
}

@Component({
  selector: 'app-actas-pendientes',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    Tab,
    TabList,
    Tabs,
    TableModule,
    Dialog,
    InputText,
    Skeleton,
    Message,
    ButtonComponent,
    EmptyStateComponent,
    ErrorStateComponent,
    SkeletonComponent,
    ActaCardComponent,
    ActaDetalleModalComponent,
    RechazoFormModalComponent,
  ],
  templateUrl: './actas-pendientes.page.html',
  styleUrls: ['./actas-pendientes.page.scss'],
})
export class ActasPendientesPage implements OnInit, OnDestroy {
  private readonly api = inject(CoordinatorApi);
  private readonly notificacionApi = inject(NotificacionApi);
  private readonly destroy$ = new Subject<void>();
  private readonly pollInterval$ = new Subject<void>();

  readonly actasPendientes = signal<ActaSummary[]>([]);
  readonly actasRevisadas = signal<ActaSummary[]>([]);
  readonly loading = signal(true);
  readonly submitting = signal(false);
  readonly error = signal<string | null>(null);

  tabActivo: string | number = 0;
  modalDetalleAbierto = false;
  modalRechazoAbierto = false;
  actaSeleccionada: ActaSummary | null = null;

  ngOnInit(): void {
    this.cargarActasPendientes();
    this.iniciarPolling();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private cargarActasPendientes(): void {
    this.loading.set(true);
    this.error.set(null);

    this.api
      .obtenerActasPendientes('ENVIADA', 100)
      .pipe(
        tap((response) => {
          this.actasPendientes.set(response.data || []);
          this.loading.set(false);
        }),
        takeUntil(this.destroy$),
      )
      .subscribe({
        error: (err) => {
          this.error.set(err.message || 'Error al cargar actas pendientes');
          this.loading.set(false);
        },
      });
  }

  private cargarActasRevisadas(): void {
    this.api
      .obtenerActasPendientes('APROBADA', 100)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (response) => {
          this.actasRevisadas.set(response.data || []);
        },
        error: (err) => {
          console.error('Error loading revisadas:', err);
        },
      });
  }

  private iniciarPolling(): void {
    interval(5000)
      .pipe(
        switchMap(() => this.api.obtenerActasPendientes('ENVIADA', 100)),
        tap((response) => {
          this.actasPendientes.set(response.data || []);
        }),
        takeUntil(this.destroy$),
      )
      .subscribe();
  }

  onTabChange(activeIndex: string | number): void {
    if (
      (activeIndex === 1 || activeIndex === '1') &&
      this.actasRevisadas().length === 0
    ) {
      this.cargarActasRevisadas();
    }
  }

  verDetalle(acta: ActaSummary): void {
    this.actaSeleccionada = acta;
    this.modalDetalleAbierto = true;
  }

  aprobarActa(acta: ActaSummary, comentario?: string): void {
    this.submitting.set(true);

    this.api
      .aprobarActa(acta.id, comentario)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: () => {
          this.submitting.set(false);
          this.modalDetalleAbierto = false;
          this.cargarActasPendientes();
        },
        error: (err) => {
          this.submitting.set(false);
          this.error.set(err.message || 'Error al aprobar acta');
        },
      });
  }

  rechazarActa(acta: ActaSummary, motivo: string): void {
    this.submitting.set(true);

    this.api
      .rechazarActa(acta.id, motivo)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: () => {
          this.submitting.set(false);
          this.modalDetalleAbierto = false;
          this.modalRechazoAbierto = false;
          this.cargarActasPendientes();
        },
        error: (err) => {
          this.submitting.set(false);
          this.error.set(err.message || 'Error al rechazar acta');
        },
      });
  }

  onModalCerrado(): void {
    this.modalDetalleAbierto = false;
    this.actaSeleccionada = null;
  }
}
