import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { IonIcon } from '@ionic/angular/standalone';
import {
  BadgeComponent,
  BadgeVariant,
  ButtonComponent,
  CardComponent,
  ErrorStateComponent,
  LoadingStateComponent,
} from '../../../../shared/ui';
import { mapError } from '../../../../shared/errors/error-handler';
import { FocusTrapDirective } from '../../../../shared/a11y/focus-trap.directive';
import {
  ActaDetalleCompleto,
  ActaItem,
  CandidatoResumen,
  CorregirActaPayload,
  DisputarActaPayload,
  EstadoActa,
} from '../../models/coordinator.model';
import { CoordinatorService } from '../../services/coordinator.service';

type ErrorTarget = 'main' | 'disputa' | 'corregir';

@Component({
  selector: 'app-acta-detalle',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    IonIcon,
    BadgeComponent,
    ButtonComponent,
    CardComponent,
    LoadingStateComponent,
    ErrorStateComponent,
    FocusTrapDirective,
  ],
  templateUrl: './acta-detalle.page.html',
  styleUrls: ['./acta-detalle.page.scss'],
})
export class ActaDetallePage implements OnInit, OnDestroy {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly coordinatorService = inject(CoordinatorService);

  /** Cuánto tiempo permanece visible el mensaje de éxito antes de auto-limpiarse. */
  private static readonly SUCCESS_VISIBLE_MS = 4000;

  actaId!: number;
  data: ActaDetalleCompleto | null = null;
  loading = true;
  submitting = false;
  error: string | null = null;
  actionSuccessMessage: string | null = null;
  /** True cuando el último error vino de un 409 y debemos recargar al reintentar. */
  errorEsConflicto = false;
  private successTimer: ReturnType<typeof setTimeout> | null = null;

  // Modal / Action states
  modalDisputarAbierto = false;
  motivoDisputa = '';
  errorDisputa: string | null = null;

  modalCorregirAbierto = false;
  correcionSufragantes = 0;
  correcionBlancos = 0;
  correcionNulos = 0;
  votosCorregidosMap: Map<number, number> = new Map();
  errorCorregir: string | null = null;

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
      this.error = 'No se proporcionó el identificador del acta.';
      this.loading = false;
    }
  }

  cargarDetalle(): void {
    this.loading = true;
    this.error = null;
    this.errorEsConflicto = false;

    this.coordinatorService.getActaDetalleCompleto(this.actaId).subscribe({
      next: (detalle) => {
        this.data = detalle;
        this.loading = false;
      },
      error: (err) => {
        this.submitting = false;
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
    if (target === 'disputa') {
      this.errorDisputa = mapped.message;
    } else if (target === 'corregir') {
      this.errorCorregir = mapped.message;
    } else {
      this.error = mapped.message;
    }
    this.submitting = false;

    if (mapped.type === 'conflict') {
      this.errorEsConflicto = true;
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
    this.submitting = false;
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
  abrirAprobar(): void {
    if (!confirm('¿Confirma que desea APROBAR esta acta electoral?')) {
      return;
    }
    this.submitting = true;
    this.error = null;
    this.errorEsConflicto = false;

    this.coordinatorService.aprobarActa(this.actaId).subscribe({
      next: () => this.handleActionSuccess('¡Acta APROBADA exitosamente!'),
      error: (err) => this.handleActionError(err, 'main', true),
    });
  }

  // DISPUTAR
  abrirModalDisputar(): void {
    this.motivoDisputa = '';
    this.errorDisputa = null;
    this.modalDisputarAbierto = true;
  }

  cerrarModalDisputar(): void {
    this.modalDisputarAbierto = false;
  }

  confirmarDisputa(): void {
    if (!this.motivoDisputa.trim()) {
      this.errorDisputa = 'El motivo de la disputa es obligatorio.';
      return;
    }

    this.submitting = true;
    this.errorDisputa = null;

    const payload: DisputarActaPayload = {
      motivo: this.motivoDisputa.trim(),
    };

    this.coordinatorService.disputarActa(this.actaId, payload).subscribe({
      next: () => {
        this.modalDisputarAbierto = false;
        this.handleActionSuccess('El acta ha sido puesta EN DISPUTA.');
      },
      error: (err) => this.handleActionError(err, 'disputa', true),
    });
  }

  // CORREGIR
  abrirModalCorregir(): void {
    if (!this.data) return;
    this.correcionSufragantes = this.data.acta.totalSufragantes;
    this.correcionBlancos = this.data.acta.votosBlancos;
    this.correcionNulos = this.data.acta.votosNulos;
    this.votosCorregidosMap.clear();

    this.data.candidatos.forEach((c) => {
      this.votosCorregidosMap.set(c.id, c.votos || 0);
    });

    this.errorCorregir = null;
    this.modalCorregirAbierto = true;
  }

  cerrarModalCorregir(): void {
    this.modalCorregirAbierto = false;
  }

  setVotoCorregido(candidatoId: number, event: Event): void {
    const input = event.target as HTMLInputElement;
    const value = Math.max(0, parseInt(input.value, 10) || 0);
    this.votosCorregidosMap.set(candidatoId, value);
  }

  get sumaVotosCorregidos(): number {
    let total = 0;
    this.votosCorregidosMap.forEach((v) => {
      total += v;
    });
    return total + (this.correcionBlancos || 0) + (this.correcionNulos || 0);
  }

  get diferenciaChecksumCorregida(): number {
    return this.sumaVotosCorregidos - (this.correcionSufragantes || 0);
  }

  get isChecksumCorregidoValido(): boolean {
    return (
      this.correcionSufragantes > 0 &&
      this.sumaVotosCorregidos === this.correcionSufragantes
    );
  }

  confirmarCorrecion(): void {
    if (!this.data) return;

    if (!this.isChecksumCorregidoValido) {
      this.errorCorregir =
        'La suma de votos corregidos (candidatos + blancos + nulos) debe ser exactamente igual al total de sufragantes.';
      return;
    }

    const detalles = Array.from(this.votosCorregidosMap.entries()).map(
      ([candidatoId, votos]) => ({
        candidatoId,
        votos,
      }),
    );

    const payload: CorregirActaPayload = {
      jrvId: this.data.acta.jrvId,
      cargoId: this.data.acta.cargoId,
      totalSufragantes: this.correcionSufragantes,
      votosBlancos: this.correcionBlancos,
      votosNulos: this.correcionNulos,
      detalles,
    };

    this.submitting = true;
    this.errorCorregir = null;

    this.coordinatorService.corregirActa(this.actaId, payload).subscribe({
      next: () => {
        this.modalCorregirAbierto = false;
        this.handleActionSuccess(
          'El acta ha sido CORREGIDA y quedó lista para aprobación.',
        );
      },
      error: (err) => this.handleActionError(err, 'corregir', true),
    });
  }

  /**
   * Lista de chequeos de integridad del acta. Cada item dice si el acta está OK o si
   * tiene una inconsistencia visible para el coordinator.
   */
  chequeos(): Array<{ ok: boolean; texto: string }> {
    if (!this.data) return [];
    const acta = this.data.acta;
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
        ok: this.data.checksumValido,
        texto: this.data.checksumValido
          ? 'Cuadre verificado: candidatos + blancos + nulos = total sufragantes.'
          : `Cuadre inconsistente: diferencia ${this.data.diferenciaChecksum}.`,
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
    this.coordinatorService.subirImagenContingencia(this.actaId, file).subscribe({
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
