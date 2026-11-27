import { CommonModule } from '@angular/common';
import { Component, OnInit, OnDestroy, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { Subject, takeUntil } from 'rxjs';
import {
  ButtonComponent,
  CardComponent,
  ErrorStateComponent,
  LoadingStateComponent,
} from '../../../../shared/ui';
import {
  ActaDetalleItem,
  ActaRequest,
  ActaResponse,
  CandidatoInfo,
} from '../../domain/models/observer.model';
import { ObserverApi } from '../../data/observer.api';

/**
 * Constante CPCCS: proporción máxima permitida de votos vs. sufragantes.
 * En CPCCS (voto acumulativo): SUM(votos) <= totalSufragantes * 3
 */
const COEFICIENTE_CPCCS = 3;

/**
 * Umbral de sufragantes para categorizar como mesa especial.
 */
const TOPE_SUFRAGANTES_CNE = 500;

/**
 * Tope absoluto de votos en un campo de entrada.
 */
const MAX_VOTOS_ABSOLUTO = 1_000_000;

/**
 * Normaliza la cantidad ingresada a un entero no negativo.
 * Cualquier valor inválido se convierte a 0.
 *
 * @param raw valor crudo del input
 * @returns número normalizado
 */
export function normalizarCantidad(raw: string | null | undefined): number {
  const parsed = Number.parseInt((raw ?? '').trim(), 10);
  if (!Number.isFinite(parsed) || parsed < 0) {
    return 0;
  }
  return parsed;
}

@Component({
  selector: 'app-correccion-acta',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    CardComponent,
    ButtonComponent,
    LoadingStateComponent,
    ErrorStateComponent,
  ],
  templateUrl: './correccion-acta.page.html',
  styleUrls: ['./correccion-acta.page.scss'],
})
export class CorreccionActaPage implements OnInit, OnDestroy {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly observerService = inject(ObserverApi);
  private readonly destroy$ = new Subject<void>();
  private evidenceReloadAttempted = false;

  actaId!: number;

  // Signals para estado de carga y UI
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly submitting = signal(false);
  readonly success = signal<string | null>(null);
  readonly stateWarning = signal<string | null>(null);

  // Datos del acta
  readonly motivoDisputa = signal<string | null>(null);
  readonly coordinadorNombre = signal<string | null>(null);
  readonly urlEvidencia = signal<string | null>(null);
  readonly jrvNumero = signal<string | null>(null);
  readonly cargoNombre = signal<string | null>(null);

  // Forma de corrección
  readonly totalSufragantes = signal(0);
  readonly votosBlancos = signal(0);
  readonly votosNulos = signal(0);
  readonly esCpccs = signal(false);

  readonly detalles = signal<
    Array<
      ActaDetalleItem & { candidatoNombre?: string; partidoSiglas?: string }
    >
  >([]);

  // Computed para checksum
  get sumaVotos(): number {
    const votosCandidatos = this.detalles().reduce(
      (acc, d) => acc + (Number(d.votos) || 0),
      0,
    );
    return (
      votosCandidatos +
      (Number(this.votosBlancos()) || 0) +
      (Number(this.votosNulos()) || 0)
    );
  }

  get checksumValido(): boolean {
    if (this.totalSufragantes() <= 0) {
      return false;
    }
    if (this.esCpccs()) {
      return this.sumaVotos <= this.totalSufragantes() * COEFICIENTE_CPCCS;
    }
    return this.sumaVotos === this.totalSufragantes();
  }

  get diferenciaChecksum(): number {
    return this.sumaVotos - this.totalSufragantes();
  }

  get botonEnvioBloqueado(): boolean {
    return !this.checksumValido || this.submitting();
  }

  // Exponer constantes para template
  readonly MAX_VOTOS_ABSOLUTO = MAX_VOTOS_ABSOLUTO;
  readonly TOPE_SUFRAGANTES_CNE = TOPE_SUFRAGANTES_CNE;

  ngOnInit(): void {
    const idParam = this.route.snapshot.paramMap.get('id');
    if (!idParam) {
      this.error.set('ID del acta no proporcionado');
      return;
    }

    this.actaId = Number(idParam);
    if (!Number.isFinite(this.actaId)) {
      this.error.set('ID del acta inválido');
      return;
    }

    this.cargarActa();
  }

  /**
   * Carga los detalles del acta en disputa desde el backend.
   * Valida que esté en estado EN_DISPUTA; si no, redirige.
   */
  cargarActa(): void {
    this.observerService
      .getActaParaCorregir(this.actaId)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (acta) => {
          this.procesarActaCargada(acta);
        },
        error: (err) => {
          console.error('Error al cargar acta para corregir:', err);
          this.error.set(
            'No se pudo cargar el acta. Por favor intenta nuevamente.',
          );
          this.loading.set(false);
        },
      });
  }

  onEvidenceError(): void {
    if (this.evidenceReloadAttempted) return;
    this.evidenceReloadAttempted = true;
    this.cargarActa();
  }

  /**
   * Procesa el acta cargada: valida estado y precarga datos.
   *
   * D1 — Verificar estado EN_DISPUTA. Si no lo es, mostrar feedback y redirigir (C5).
   */
  private procesarActaCargada(acta: ActaResponse): void {
    if (acta.estado !== 'EN_DISPUTA') {
      const msg = `Acta no disponible para corrección (estado: ${acta.estado}). Verifique con coordinación.`;
      console.warn('[CorreccionActa] Silent redirect prevention:', msg);
      this.stateWarning.set(msg);
      // Delay navigation to allow UI to display message (C5)
      setTimeout(() => {
        this.router.navigate(['/observer/mis-jrvs']);
      }, 2000);
      return;
    }

    // Precarga de datos
    this.motivoDisputa.set(acta.motivoDisputa || null);
    this.coordinadorNombre.set(acta.coordinadorNombre || null);
    this.urlEvidencia.set(acta.urlEvidencia || null);
    this.totalSufragantes.set(acta.totalSufragantes);
    this.votosBlancos.set(acta.votosBlancos);
    this.votosNulos.set(acta.votosNulos);

    // Precarga de detalles de votos
    this.detalles.set(
      acta.detalles.map((d) => ({ ...d, votos: d.votos || 0 })),
    );

    // Detectar si es CPCCS (por ahora, basándonos en que totalSufragantes > TOPE_SUFRAGANTES_CNE)
    // En un escenario real, esto vendría del backend o de configuración.
    this.esCpccs.set(acta.totalSufragantes > TOPE_SUFRAGANTES_CNE);

    this.loading.set(false);
  }

  /**
   * Actualiza el voto de un candidato y recomputa checksum.
   */
  actualizarVotoCandidato(candidatoId: number, nuevoVoto: number): void {
    const detalles = this.detalles();
    const detalle = detalles.find((d) => d.candidatoId === candidatoId);
    if (detalle) {
      detalle.votos = nuevoVoto;
      this.detalles.set([...detalles]);
    }
  }

  /**
   * Actualiza votos blancos y recomputa checksum.
   */
  actualizarVotosBlancos(valor: number): void {
    this.votosBlancos.set(valor);
  }

  /**
   * Actualiza votos nulos y recomputa checksum.
   */
  actualizarVotosNulos(valor: number): void {
    this.votosNulos.set(valor);
  }

  /**
   * Actualiza total de sufragantes.
   */
  actualizarTotalSufragantes(valor: number): void {
    this.totalSufragantes.set(valor);
  }

  /**
   * Envía la corrección al backend.
   *
   * B.4 — Llamada a corregirActa() con validación de checksum.
   */
  enviarCorreccion(): void {
    if (!this.checksumValido || this.submitting()) {
      return;
    }

    this.submitting.set(true);
    this.error.set(null);

    const request: ActaRequest = {
      jrvId: 0, // El backend ignore estos campos en PUT /corregir
      cargoId: 0,
      totalSufragantes: this.totalSufragantes(),
      votosBlancos: this.votosBlancos(),
      votosNulos: this.votosNulos(),
      detalles: this.detalles().map((d) => ({
        candidatoId: d.candidatoId,
        votos: Number(d.votos) || 0,
      })),
    };

    this.observerService
      .corregirActa(this.actaId, request)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: () => {
          this.success.set('Acta corregida exitosamente');
          this.submitting.set(false);
          // Redirigir después de 2 segundos
          setTimeout(() => {
            this.router.navigate(['/observer/mis-jrvs']);
          }, 2000);
        },
        error: (err) => {
          console.error('Error al corregir acta:', err);
          const mensaje =
            err.status === 400
              ? 'La suma de votos no coincide. Por favor verifica los números.'
              : err.status === 409
                ? 'El acta ya fue modificada. Por favor recarga la página.'
                : 'Error al conectar con el servidor. Intenta nuevamente.';
          this.error.set(mensaje);
          this.submitting.set(false);
        },
      });
  }

  /**
   * Cancela la corrección y vuelve a mis-jrvs.
   */
  cancelar(): void {
    this.router.navigate(['/observer/mis-jrvs']);
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
