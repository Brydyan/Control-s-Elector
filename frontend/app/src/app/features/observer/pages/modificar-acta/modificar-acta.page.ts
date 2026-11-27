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
import { ImageCompressionService } from '../../../../core/services/image-compression.service';
import {
  ActaDetalleItem,
  ActaRequest,
  ActaResponse,
  CandidatoInfo,
} from '../../domain/models/observer.model';
import { ObserverApi } from '../../data/observer.api';

/**
 * Tope duro de sufragantes de una JRV (T-FE-13 D2).
 */
const TOPE_SUFRAGANTES_CNE = 500;

/**
 * Tope absoluto de un campo de votos mientras el padrón todavía no se conoce.
 */
const MAX_VOTOS_ABSOLUTO = 1_000_000;

/**
 * Normaliza lo que el observador escribe en un campo de votos.
 * Sólo admite enteros no negativos: `1.5` → `1`, `-5` → `0`, `abc` → `0`.
 */
function normalizarCantidad(raw: string | null | undefined): number {
  const parsed = Number.parseInt((raw ?? '').trim(), 10);
  if (!Number.isFinite(parsed) || parsed < 0) {
    return 0;
  }
  return parsed;
}

/**
 * Página dedicada para modificar un acta ya registrada.
 *
 * A diferencia del wizard de registro (RegistrarActaPage), esta pantalla es
 * una vista única sin pasos: el observador ve todos los datos del acta
 * existente, los edita en tiempo real y guarda los cambios.
 *
 * Flujo:
 * 1. Carga el acta existente vía `getActaParaCorregir(actaId)`.
 * 2. Precarga candidatos del cargo asociado.
 * 3. Muestra evidencia fotográfica actual y permite reemplazarla.
 * 4. Formulario con totales y votos por candidato.
 * 5. Checksum dinámico 1:1 con validación en tiempo real.
 * 6. Guarda con PUT `corregirActa()` y, si hay nueva foto, `subirImagen()`.
 */
@Component({
  selector: 'app-modificar-acta',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    CardComponent,
    ButtonComponent,
    LoadingStateComponent,
    ErrorStateComponent,
  ],
  templateUrl: './modificar-acta.page.html',
  styleUrls: ['./modificar-acta.page.scss'],
})
export class ModificarActaPage implements OnInit, OnDestroy {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly observerService = inject(ObserverApi);
  private readonly imageCompression = inject(ImageCompressionService);
  private readonly destroy$ = new Subject<void>();

  actaId!: number;

  // Signals para estado de carga y UI
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly submitting = signal(false);
  readonly success = signal<string | null>(null);

  // Datos del acta cargados
  readonly jrvNumero = signal<string | null>(null);
  readonly cargoNombre = signal<string | null>(null);
  readonly urlEvidencia = signal<string | null>(null);

  // Formulario de edición
  readonly totalSufragantes = signal(0);
  readonly votosBlancos = signal(0);
  readonly votosNulos = signal(0);

  readonly detalles = signal<
    Array<
      ActaDetalleItem & { candidatoNombre?: string; partidoSiglas?: string }
    >
  >([]);

  // Candidatos disponibles para esta dignidad
  candidatos: CandidatoInfo[] = [];

  // Imagen nueva seleccionada por el usuario
  selectedImageFile: File | null = null;
  imagePreviewUrl: string | null = null;

  // Exponer constantes para template
  readonly MAX_VOTOS_ABSOLUTO = MAX_VOTOS_ABSOLUTO;
  readonly TOPE_SUFRAGANTES_CNE = TOPE_SUFRAGANTES_CNE;

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
    return (
      this.totalSufragantes() > 0 && this.sumaVotos === this.totalSufragantes()
    );
  }

  get diferenciaChecksum(): number {
    return this.sumaVotos - this.totalSufragantes();
  }

  get descripcionDiferencia(): string {
    const suma = this.sumaVotos;
    const padron = this.totalSufragantes();

    if (padron <= 0) {
      return `suma ${suma} ≠ padrón sin registrar — falta indicar el total de sufragantes`;
    }

    const delta = padron - suma;
    if (delta > 0) {
      return `suma ${suma} ≠ padrón ${padron} — faltan ${delta}`;
    }
    if (delta < 0) {
      return `suma ${suma} ≠ padrón ${padron} — sobran ${-delta}`;
    }
    return '';
  }

  get alertaSufragantes(): string | null {
    if (this.totalSufragantes() > TOPE_SUFRAGANTES_CNE) {
      return 'Límite CNE excedido: ninguna JRV puede superar 500 sufragantes.';
    }
    return null;
  }

  get nivelAlertaSufragantes(): 'warning' | 'error' | null {
    if (this.totalSufragantes() > TOPE_SUFRAGANTES_CNE) return 'error';
    return null;
  }

  get excedeTopeSufragantes(): boolean {
    return this.totalSufragantes() > TOPE_SUFRAGANTES_CNE;
  }

  ngOnInit(): void {
    const idParam = this.route.snapshot.paramMap.get('id');
    if (!idParam) {
      this.error.set('ID del acta no proporcionado');
      this.loading.set(false);
      return;
    }

    this.actaId = Number(idParam);
    if (!Number.isFinite(this.actaId)) {
      this.error.set('ID del acta inválido');
      this.loading.set(false);
      return;
    }

    this.cargarActa();
  }

  /**
   * Carga los detalles del acta existente y los candidatos del cargo.
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
          console.error('Error al cargar acta para modificar:', err);
          this.error.set(
            'No se pudo cargar el acta. Por favor intenta nuevamente.',
          );
          this.loading.set(false);
        },
      });
  }

  private evidenceReloadAttempted = false;

  /**
   * La URL prefirmada de la evidencia caduca (15 min). Si la imagen falla, se pide una URL
   * nueva una sola vez, actualizando solo la evidencia para no perder los votos ya editados.
   */
  onEvidenceError(): void {
    if (this.evidenceReloadAttempted) return;
    this.evidenceReloadAttempted = true;
    this.observerService
      .getActaParaCorregir(this.actaId)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (acta) => this.urlEvidencia.set(acta.urlEvidencia || null),
        error: (err) =>
          console.error('No se pudo renovar la URL de la evidencia:', err),
      });
  }

  /**
   * Procesa el acta cargada: precarga datos y carga candidatos.
   */
  private procesarActaCargada(acta: ActaResponse): void {
    // Precarga de datos del acta
    this.jrvNumero.set(String(acta.jrvId));
    this.cargoNombre.set(acta.cargoId?.toString() ?? null);
    this.urlEvidencia.set(acta.urlEvidencia || null);
    this.totalSufragantes.set(acta.totalSufragantes);
    this.votosBlancos.set(acta.votosBlancos);
    this.votosNulos.set(acta.votosNulos);

    // Precarga de detalles de votos
    this.detalles.set(
      acta.detalles.map((d) => ({ ...d, votos: d.votos || 0 })),
    );

    // Cargar candidatos para obtener nombres y partidos
    this.cargarCandidatos(acta.jrvId, acta.cargoId);
  }

  /**
   * Carga los candidatos del cargo para mostrar nombres y partidos.
   */
  private cargarCandidatos(jrvId: number, cargoId: number): void {
    this.observerService
      .getCatalogoDeJrv(jrvId)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (catalogo) => {
          const cargo = catalogo.find((c) => c.id === cargoId);
          this.candidatos = cargo?.candidatos ?? [];
          if (cargo?.nombre) {
            this.cargoNombre.set(cargo.nombre);
          }

          // Enriquecer detalles con información del candidato
          const detalles = this.detalles();
          detalles.forEach((d) => {
            const candidato = this.candidatos.find(
              (c) => c.id === d.candidatoId,
            );
            if (candidato) {
              d.candidatoNombre = candidato.nombre;
              d.partidoSiglas = candidato.partidoSiglas;
            }
          });
          this.detalles.set(detalles);

          this.loading.set(false);
        },
        error: (err) => {
          console.error('Error al cargar candidatos:', err);
          this.loading.set(false);
        },
      });
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
   * Maneja la selección de una nueva foto para reemplazar la evidencia.
   */
  onFileSelected(event: Event): void {
    const target = event.target as HTMLInputElement;
    if (target.files && target.files[0]) {
      const file = target.files[0];

      // Revocar URL previa si existe
      if (this.imagePreviewUrl) {
        this.imageCompression.revokePreviewUrl(this.imagePreviewUrl);
      }

      // Preview inmediato del archivo original
      this.imagePreviewUrl = this.imageCompression.createPreviewUrl(file);

      // Comprimir en background
      this.imageCompression.compress(file).then(
        (comprimido) => {
          if (comprimido instanceof File) {
            this.selectedImageFile = comprimido;
          }
        },
        (err) => {
          console.error('Error en compresión de imagen:', err);
          // Mantener el archivo original si falla la compresión
          this.selectedImageFile = file;
        },
      );

      // Limpiar input para permitir re-selección del mismo archivo
      target.value = '';
    }
  }

  /**
   * Guarda las modificaciones del acta.
   *
   * Envía PUT a `corregirActa()` y, si hay nueva foto, sube la imagen
   * vía `subirImagen()`. Al terminar, redirige a la lista de cargos de la mesa.
   */
  guardarModificaciones(): void {
    if (!this.checksumValido || this.submitting()) {
      return;
    }

    if (this.excedeTopeSufragantes) {
      this.error.set(
        'Límite CNE excedido: ninguna JRV puede superar 500 sufragantes. Verifica el total de sufragantes del padrón antes de enviar el acta.',
      );
      return;
    }

    this.submitting.set(true);
    this.error.set(null);

    const request: ActaRequest = {
      jrvId: 0, // El backend ignora estos campos en PUT /corregir
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
          // Si hay nueva foto, subirla
          if (this.selectedImageFile) {
            this.observerService
              .subirImagenActa(this.actaId, this.selectedImageFile)
              .pipe(takeUntil(this.destroy$))
              .subscribe({
                next: () => {
                  this.finalizarGuardado();
                },
                error: (imgErr) => {
                  console.warn(
                    'Acta modificada pero falló la subida de nueva evidencia:',
                    imgErr,
                  );
                  this.finalizarGuardado();
                },
              });
          } else {
            this.finalizarGuardado();
          }
        },
        error: (err) => {
          console.error('Error al modificar acta:', err);
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

  private finalizarGuardado(): void {
    this.success.set('Modificaciones guardadas exitosamente');
    this.submitting.set(false);
    // Redirigir a la lista de cargos de la mesa después de 2 segundos
    setTimeout(() => {
      this.router.navigate(['/observer/mis-jrvs']);
    }, 2000);
  }

  /**
   * Cancela la modificación y vuelve a la lista de JRVs.
   */
  cancelar(): void {
    this.router.navigate(['/observer/mis-jrvs']);
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();

    // Revocar URL de preview al desmontar
    if (this.imagePreviewUrl) {
      this.imageCompression.revokePreviewUrl(this.imagePreviewUrl);
    }
  }
}
