import { CommonModule } from '@angular/common';
import {
  Component,
  OnDestroy,
  OnInit,
  ViewChild,
  inject,
  signal,
} from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Dialog } from 'primeng/dialog';
import { Message } from 'primeng/message';
import { TableModule } from 'primeng/table';
import {
  BadgeComponent,
  BadgeVariant,
  ButtonComponent,
  CardComponent,
  ErrorStateComponent,
  LoadingStateComponent,
} from '../../../../shared/ui';
import { mapError } from '../../../../shared/errors/error-handler';
import {
  ActaDetalleCompleto,
  ActaItem,
  CandidatoResumen,
  CorregirActaPayload,
  DisputarActaPayload,
  EstadoActa,
} from '../../domain/models/coordinator.model';
import { CoordinatorApi } from '../../data/coordinator.api';
import {
  ModalAprobarComponent,
  ModalDisputarComponent,
  ModalCorregirComponent,
  ModalAnularComponent,
} from '../../components';

type ErrorTarget = 'main' | 'disputa' | 'corregir';

@Component({
  selector: 'app-acta-detalle',
  standalone: true,
  imports: [
    CommonModule,
    BadgeComponent,
    ButtonComponent,
    CardComponent,
    LoadingStateComponent,
    ErrorStateComponent,
    Dialog,
    Message,
    TableModule,
    ModalAprobarComponent,
    ModalDisputarComponent,
    ModalCorregirComponent,
    ModalAnularComponent,
  ],
  templateUrl: './acta-detalle.page.html',
  styleUrls: ['./acta-detalle.page.scss'],
})
export class ActaDetallePage implements OnInit, OnDestroy {
  @ViewChild(ModalCorregirComponent) modalCorregir?: ModalCorregirComponent;

  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly coordinatorService = inject(CoordinatorApi);

  /** Cuánto tiempo permanece visible el mensaje de éxito antes de auto-limpiarse. */
  private static readonly SUCCESS_VISIBLE_MS = 4000;

  actaId!: number;
  readonly data = signal<ActaDetalleCompleto | null>(null);

  /**
   * The loaded detail.
   *
   * Only read it inside a `data()` guard: the detail is null while the
   * request is in flight, and the assertion would take the screen down.
   * Outside a guard, read the signal with the optional chaining instead.
   */
  get detalle(): ActaDetalleCompleto {
    return this.data()!;
  }
  readonly loading = signal(true);
  readonly submitting = signal(false);
  readonly error = signal<string | null>(null);
  actionSuccessMessage: string | null = null;
  /** True cuando el último error vino de un 409 y debemos recargar al reintentar. */
  errorEsConflicto = false;
  private successTimer: ReturnType<typeof setTimeout> | null = null;

  // Modal visibility signals
  readonly modalAprobarVisible = signal(false);
  readonly modalDisputarVisible = signal(false);
  readonly modalCorregirVisible = signal(false);
  readonly modalAnularVisible = signal(false);

  // Image modal preview
  imagenZoomAbierto = false;

  // T-FE-10 E.4: estado de subida contingencia + descarga PDF
  uploadingContingencia = false;
  downloadingPdf = false;

  ngOnDestroy(): void {
    if (this.successTimer != null) {
      clearTimeout(this.successTimer);
      this.successTimer = null;
    }
  }

  ngOnInit(): void {
    const idParam = this.route.snapshot.paramMap.get('id');
    if (idParam) {
      this.actaId = Number(idParam);
      this.cargarDetalle();
    } else {
      this.error.set('No se proporcionó el identificador del acta.');
      this.loading.set(false);
    }
  }

  cargarDetalle(): void {
    this.loading.set(true);
    this.error.set(null);
    this.errorEsConflicto = false;

    this.coordinatorService.getActaDetalleCompleto(this.actaId).subscribe({
      next: (detalle) => {
        this.data.set(detalle);
        this.loading.set(false);
      },
      error: (err) => {
        this.submitting.set(false);
        this.handleActionError(err, 'main', /* reloadOnConflict */ false);
      },
    });
  }

  /**
   * Wrapper unificado para errores de acciones. Setea el mensaje en el slot
   * correspondiente (main / modal) y, si la app es `conflict`, dispara recarga
   * automática del detalle para reflejar el estado real en el backend.
   */
  private handleActionError(
    err: unknown,
    target: ErrorTarget,
    reloadOnConflict: boolean,
  ): void {
    const mapped = mapError(err);
    // Modal errors handled by modal components; only set main page error
    if (target === 'main') {
      this.error.set(mapped.message);
    }
    this.submitting.set(false);

    if (mapped.type === 'conflict') {
      if (reloadOnConflict) {
        this.cargarDetalle();
      }
    }
  }

  /**
   * Wrapper unificado para éxito de acciones. Recarga el detalle (fuente de
   * verdad) en lugar de hacer update optimista del DTO, y agenda auto-clear
   * del banner de éxito.
   */
  private handleActionSuccess(message: string): void {
    this.actionSuccessMessage = message;
    this.submitting.set(false);
    this.cargarDetalle();

    if (this.successTimer != null) {
      clearTimeout(this.successTimer);
    }
    this.successTimer = setTimeout(() => {
      this.actionSuccessMessage = null;
      this.successTimer = null;
    }, ActaDetallePage.SUCCESS_VISIBLE_MS);
  }

  /** Botón visible en la sección de error cuando el último fallo fue transitorio. */
  reintentarCargar(): void {
    this.cargarDetalle();
  }

  volver(): void {
    this.router.navigate(['/coordinator/actas']);
  }

  // APROBAR
  abrirModalAprobar(): void {
    this.modalAprobarVisible.set(true);
  }

  onAprobarConfirm(): void {
    this.submitting.set(true);
    this.error.set(null);
    this.errorEsConflicto = false;

    this.coordinatorService.aprobarActa(this.actaId).subscribe({
      next: () => {
        this.modalAprobarVisible.set(false);
        this.handleActionSuccess('¡Acta APROBADA exitosamente!');
      },
      error: (err) => {
        this.handleActionError(err, 'main', true);
      },
    });
  }

  // DISPUTAR
  abrirModalDisputar(): void {
    this.modalDisputarVisible.set(true);
  }

  onDisputarConfirm(payload: DisputarActaPayload): void {
    this.submitting.set(true);
    this.error.set(null);
    this.errorEsConflicto = false;

    this.coordinatorService.disputarActa(this.actaId, payload).subscribe({
      next: () => {
        this.modalDisputarVisible.set(false);
        this.handleActionSuccess('El acta ha sido puesta EN DISPUTA.');
      },
      error: (err) => this.handleActionError(err, 'main', true),
    });
  }

  // CORREGIR
  abrirModalCorregir(): void {
    if (!this.data() || !this.modalCorregir) return;

    const votesPerCandidate = new Map<number, number>();
    this.detalle.candidatos.forEach((c) => {
      votesPerCandidate.set(c.id, c.votos || 0);
    });

    this.modalCorregir.initializeForm({
      totalSufragantes: this.detalle.acta.totalSufragantes,
      votosBlancos: this.detalle.acta.votosBlancos,
      votosNulos: this.detalle.acta.votosNulos,
      votesPerCandidate,
    });

    this.modalCorregirVisible.set(true);
  }

  onCorregirConfirm(payload: CorregirActaPayload): void {
    this.submitting.set(true);
    this.error.set(null);
    this.errorEsConflicto = false;

    this.coordinatorService.corregirActa(this.actaId, payload).subscribe({
      next: () => {
        this.modalCorregirVisible.set(false);
        this.handleActionSuccess(
          'El acta ha sido CORREGIDA y quedó lista para aprobación.',
        );
      },
      error: (err) => this.handleActionError(err, 'main', true),
    });
  }

  // ANULAR
  abrirModalAnular(): void {
    this.modalAnularVisible.set(true);
  }

  onAnularConfirm(motivo: string): void {
    this.submitting.set(true);
    this.error.set(null);
    this.errorEsConflicto = false;

    this.coordinatorService.anularActa(this.actaId, motivo).subscribe({
      next: () => {
        this.modalAnularVisible.set(false);
        this.handleActionSuccess('El acta ha sido ANULADA exitosamente.');
      },
      error: (err) => this.handleActionError(err, 'main', true),
    });
  }

  /**
   * Lista de chequeos de integridad del acta. Cada item dice si el acta está OK o si
   * tiene una inconsistencia visible para el coordinator.
   */
  chequeos(): Array<{ ok: boolean; texto: string }> {
    if (!this.data()) return [];
    const acta = this.detalle.acta;
    const checks: Array<{ ok: boolean; texto: string }> = [];

    checks.push({
      ok: acta.totalSufragantes > 0,
      texto:
        acta.totalSufragantes > 0
          ? `Total sufragantes registrado (${acta.totalSufragantes}).`
          : 'Falta el total de sufragantes (acta sin sufragantes registrados).',
    });

    checks.push({
      ok: !!acta.urlEvidencia,
      texto: acta.urlEvidencia
        ? 'Evidencia fotográfica adjunta.'
        : 'Sin evidencia fotográfica. Solicitá al OBSERVER subir el respaldo.',
    });

    if (acta.totalVotosCandidatos == null) {
      checks.push({
        ok: false,
        texto:
          'El backend no devuelve el subtotal de votos a candidatos. El checksum sólo puede verificar blancos + nulos.',
      });
    } else {
      checks.push({
        ok: this.detalle.checksumValido,
        texto: this.detalle.checksumValido
          ? 'Cuadre verificado: candidatos + blancos + nulos = total sufragantes.'
          : `Cuadre inconsistente: diferencia ${this.detalle.diferenciaChecksum}.`,
      });
    }

    if (acta.estado === 'EN_DISPUTA') {
      checks.push({
        ok: false,
        texto: 'Acta en disputa. Esperando corrección o anulación.',
      });
    }

    if (acta.estado === 'ANULADA') {
      checks.push({
        ok: false,
        texto: acta.motivoAnulacion
          ? `Acta ANULADA. Motivo: ${acta.motivoAnulacion}.`
          : 'Acta ANULADA. Falta registrar motivo.',
      });
    }

    return checks;
  }

  mapBadgeVariant(estado: EstadoActa): BadgeVariant {
    return estado;
  }

  obtenerEstadoLabel(estado: EstadoActa): string {
    switch (estado) {
      case 'APROBADA':
        return 'Aprobada';
      case 'EN_DISPUTA':
        return 'En Disputa';
      case 'CORREGIDA':
        return 'Corregida';
      case 'ENVIADA':
        return 'Enviada';
      case 'BORRADOR':
        return 'Borrador';
      case 'ANULADA':
        return 'Anulada';
      default:
        return estado;
    }
  }

  // T-FE-10 E.4: handlers de subida contingencia y descarga PDF.

  /**
   * Maneja selección de archivo de contingencia: sube la imagen al backend
   * y recarga el detalle para reflejar el nuevo `urlEvidencia`.
   */
  onContingenciaImagenSeleccionada(event: Event): void {
    const target = event.target as HTMLInputElement;
    const file = target.files?.[0];
    if (!file) return;
    this.uploadingContingencia = true;
    this.coordinatorService
      .subirImagenContingencia(this.actaId, file)
      .subscribe({
        next: () => {
          this.uploadingContingencia = false;
          this.actionSuccessMessage = 'Foto de contingencia subida.';
          this.cargarDetalle();
        },
        error: (err) => {
          this.uploadingContingencia = false;
          this.handleActionError(err, 'main', false);
        },
      });
    target.value = ''; // permite re-subir el mismo archivo
  }

  /**
   * Descarga el expediente de impugnación CNE como PDF (T-BE-20).
   * Dispara descarga en navegador via Blob URL temporal.
   */
  descargarExpedientePdf(): void {
    this.downloadingPdf = true;
    this.coordinatorService.descargarExpedientePdf(this.actaId).subscribe({
      next: (blob) => {
        this.downloadingPdf = false;
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `expediente-acta-${this.actaId}.pdf`;
        a.click();
        URL.revokeObjectURL(url);
      },
      error: (err) => {
        this.downloadingPdf = false;
        this.handleActionError(err, 'main', false);
      },
    });
  }
}
