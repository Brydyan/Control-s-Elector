import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
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
import {
  ActaDetalleCompleto,
  ActaItem,
  CandidatoResumen,
  CorregirActaPayload,
  DisputarActaPayload,
  EstadoActa,
} from '../../models/coordinator.model';
import { CoordinatorService } from '../../services/coordinator.service';

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
  ],
  templateUrl: './acta-detalle.page.html',
  styleUrls: ['./acta-detalle.page.scss'],
})
export class ActaDetallePage implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly coordinatorService = inject(CoordinatorService);

  actaId!: number;
  data: ActaDetalleCompleto | null = null;
  loading = true;
  submitting = false;
  error: string | null = null;
  actionSuccessMessage: string | null = null;

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

    this.coordinatorService.getActaDetalleCompleto(this.actaId).subscribe({
      next: (detalle) => {
        this.data = detalle;
        this.loading = false;
      },
      error: (err) => {
        this.error =
          err?.error?.message ||
          'No se pudo cargar el detalle del acta electoral.';
        this.loading = false;
      },
    });
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

    this.coordinatorService.aprobarActa(this.actaId).subscribe({
      next: (actaActualizada) => {
        if (this.data) {
          this.data.acta = actaActualizada;
        }
        this.submitting = false;
        this.actionSuccessMessage = '¡Acta APROBADA exitosamente!';
      },
      error: (err) => {
        this.submitting = false;
        this.error =
          err?.error?.message || 'Error al intentar aprobar el acta.';
      },
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
      next: (actaActualizada) => {
        if (this.data) {
          this.data.acta = actaActualizada;
        }
        this.submitting = false;
        this.modalDisputarAbierto = false;
        this.actionSuccessMessage = 'El acta ha sido puesta EN DISPUTA.';
      },
      error: (err) => {
        this.submitting = false;
        this.errorDisputa =
          err?.error?.message || 'Error al intentar disputar el acta.';
      },
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
      next: (actaActualizada) => {
        if (this.data) {
          this.data.acta = actaActualizada;
        }
        this.submitting = false;
        this.modalCorregirAbierto = false;
        this.actionSuccessMessage =
          'El acta ha sido CORREGIDA y quedó lista para aprobación.';
        this.cargarDetalle();
      },
      error: (err) => {
        this.submitting = false;
        this.errorCorregir =
          err?.error?.message || 'Error al guardar la corrección del acta.';
      },
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
      ok: !!acta.storageUrl,
      texto: acta.storageUrl
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
}
