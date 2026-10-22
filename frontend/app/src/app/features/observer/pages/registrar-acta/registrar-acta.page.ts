import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { InputText } from 'primeng/inputtext';
import { Message } from 'primeng/message';
import { Steps } from 'primeng/steps';
import {
  ButtonComponent,
  CardComponent,
  ErrorStateComponent,
  LoadingStateComponent,
} from '../../../../shared/ui';

import { ImageCompressionService } from '../../../../core/services/image-compression.service';
import { OfflineStorageService } from '../../../../core/offline/offline-storage.service';
import {
  ActaDetalleItem,
  ActaRequest,
  CandidatoInfo,
} from '../../models/observer.model';
import { ActaStorageService } from '../../services/acta-storage.service';
import { ObserverService } from '../../services/observer.service';

type Step = 'VOTOS' | 'IMAGEN' | 'REVISION';

/** The steps of the form, in the order they are shown. */
const STEP_LABELS: Array<{ label: string }> = [
  { label: 'Votos' },
  { label: 'Evidencia' },
  { label: 'Revisión' },
];

const STEP_INDEX: Record<Step, number> = {
  VOTOS: 0,
  IMAGEN: 1,
  REVISION: 2,
};

@Component({
  selector: 'app-registrar-acta',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    CardComponent,
    ButtonComponent,
    LoadingStateComponent,
    ErrorStateComponent,
    InputText,
    Message,
    Steps,
  ],
  templateUrl: './registrar-acta.page.html',
  styleUrls: ['./registrar-acta.page.scss'],
})
export class RegistrarActaPage implements OnInit, OnDestroy {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly observerService = inject(ObserverService);
  private readonly storageService = inject(ActaStorageService);
  private readonly imageCompression = inject(ImageCompressionService);
  private readonly offlineStorage = inject(OfflineStorageService);

  jrvId!: number;
  cargoId!: number;

  currentStep: Step = 'VOTOS';

  /** The model of the step strip, shown read-only above the form. */
  readonly stepModel = STEP_LABELS;

  /** Index of the active step in the strip. */
  get stepIndex(): number {
    return STEP_INDEX[this.currentStep];
  }
  readonly loading = signal(true);
  readonly submitting = signal(false);
  readonly error = signal<string | null>(null);
  submitSuccess = false;
  draftSavedMessage: string | null = null;

  totalSufragantes = 0;
  votosBlancos = 0;
  votosNulos = 0;

  candidatos: CandidatoInfo[] = [];
  votosPorCandidato: Map<number, number> = new Map();

  selectedImageFile: File | null = null;
  imagePreviewUrl: string | null = null;
  /** D7 (T-FE-08 S5): true cuando el usuario cancela cámara o no selecciona archivo. */
  captureCancelled = false;

  ngOnInit(): void {
    const jrvParam = this.route.snapshot.paramMap.get('jrvId');
    const cargoParam = this.route.snapshot.paramMap.get('cargoId');

    if (jrvParam && cargoParam) {
      this.jrvId = Number(jrvParam);
      this.cargoId = Number(cargoParam);
      this.cargarCandidatos();
    } else {
      this.error.set('Parámetros incompletos en la ruta.');
      this.loading.set(false);
    }
  }

  cargarCandidatos(): void {
    this.loading.set(true);
    this.error.set(null);
    this.observerService.getCandidatosPorCargo(this.cargoId).subscribe({
      next: (data) => {
        this.candidatos = data;
        this.candidatos.forEach((c) => {
          if (!this.votosPorCandidato.has(c.id)) {
            this.votosPorCandidato.set(c.id, 0);
          }
        });

        // T-FE-09 V.5: restaurar borrador IndexedDB si existe (resiliencia LMK).
        void this.restaurarBorradorOffline();
        // Restaurar borrador local si existe
        this.restaurarBorradorLocal();
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(
          'No se pudieron cargar los candidatos para esta dignidad.',
        );
        this.loading.set(false);
        console.error('Error loading candidatos:', err);
      },
    });
  }

  /**
   * T-FE-09 V.5: tras cargar candidatos, consulta `acta_drafts` y, si hay
   * un borrador para (jrvId, cargoId), repobla el estado del formulario.
   */
  private async restaurarBorradorOffline(): Promise<void> {
    const draftId = OfflineStorageService.draftId(this.jrvId, this.cargoId);
    const draft = await this.offlineStorage.obtenerBorrador(draftId);
    if (!draft) return;
    this.totalSufragantes = draft.totalSufragantes;
    this.votosBlancos = draft.votosBlancos;
    this.votosNulos = draft.votosNulos;
    Object.entries(draft.votosPorCandidato).forEach(([id, votos]) => {
      this.votosPorCandidato.set(Number(id), Number(votos));
    });
    this.mostrarMensajeBorrador(
      'Borrador restaurado desde almacenamiento offline.',
    );
  }

  private restaurarBorradorLocal(): void {
    const draft = this.storageService.recuperarBorrador(
      this.jrvId,
      this.cargoId,
    );
    if (!draft) return;

    this.totalSufragantes = draft.totalSufragantes || 0;
    this.votosBlancos = draft.votosBlancos || 0;
    this.votosNulos = draft.votosNulos || 0;

    if (draft.votosPorCandidato) {
      Object.entries(draft.votosPorCandidato).forEach(([id, votos]) => {
        this.votosPorCandidato.set(Number(id), Number(votos));
      });
    }
    this.mostrarMensajeBorrador(
      'Borrador recuperado del almacenamiento local.',
    );
  }

  guardarBorradorLocal(): void {
    const votosObj: Record<number, number> = {};
    this.votosPorCandidato.forEach((v, k) => {
      votosObj[k] = v;
    });

    this.storageService.guardarBorrador({
      jrvId: this.jrvId,
      cargoId: this.cargoId,
      totalSufragantes: this.totalSufragantes,
      votosBlancos: this.votosBlancos,
      votosNulos: this.votosNulos,
      votosPorCandidato: votosObj,
      updatedAt: new Date().toISOString(),
    });

    this.mostrarMensajeBorrador('Guardado localmente en este dispositivo.');
  }

  private mostrarMensajeBorrador(msg: string): void {
    this.draftSavedMessage = msg;
    setTimeout(() => {
      if (this.draftSavedMessage === msg) {
        this.draftSavedMessage = null;
      }
    }, 3000);
  }

  getVoto(candidatoId: number): number {
    return this.votosPorCandidato.get(candidatoId) || 0;
  }

  setVoto(candidatoId: number, event: Event): void {
    const input = event.target as HTMLInputElement;
    const value = Math.max(0, parseInt(input.value, 10) || 0);
    this.votosPorCandidato.set(candidatoId, value);
    this.guardarBorradorLocal();
  }

  onSufragantesChange(): void {
    this.guardarBorradorLocal();
  }

  get sumaVotosCandidatos(): number {
    let total = 0;
    this.votosPorCandidato.forEach((v) => {
      total += v;
    });
    return total;
  }

  get totalVotosComputados(): number {
    return (
      this.sumaVotosCandidatos +
      (this.votosBlancos || 0) +
      (this.votosNulos || 0)
    );
  }

  get diferenciaChecksum(): number {
    return this.totalVotosComputados - this.totalSufragantes;
  }

  get isChecksumValid(): boolean {
    return (
      this.totalSufragantes > 0 &&
      this.totalVotosComputados === this.totalSufragantes
    );
  }

  irAPasoImagen(): void {
    this.error.set(null);
    // T-FE-09 B.3 / V.4 — patrón "Guardar antes de capturar":
    // persistir el estado actual en IndexedDB antes de abrir la cámara
    // para sobrevivir un eventual LMK de Android.
    void this.guardarBorradorOffline();
    this.currentStep = 'IMAGEN';
  }

  /**
   * Persiste el estado actual del wizard en IndexedDB (`acta_drafts`).
   * Idempotente: reemplaza el borrador previo para esta (jrvId, cargoId).
   */
  private async guardarBorradorOffline(): Promise<void> {
    const votosObj: Record<number, number> = {};
    this.votosPorCandidato.forEach((v, k) => {
      votosObj[k] = v;
    });
    await this.offlineStorage.guardarBorrador({
      draftId: OfflineStorageService.draftId(this.jrvId, this.cargoId),
      jrvId: this.jrvId,
      cargoId: this.cargoId,
      totalSufragantes: this.totalSufragantes,
      votosBlancos: this.votosBlancos,
      votosNulos: this.votosNulos,
      votosPorCandidato: votosObj,
      updatedAt: Date.now(),
    });
  }

  onFileSelected(event: Event): void {
    const target = event.target as HTMLInputElement;
    if (target.files && target.files[0]) {
      const file = target.files[0];
      this.selectedImageFile = file;
      this.captureCancelled = false;
      this.error.set(null);

      // D1 (T-FE-08): usar URL.createObjectURL (Blob binario) en vez de
      // FileReader.readAsDataURL (que produce data:image/... Base64 en heap).
      if (this.imagePreviewUrl) {
        this.imageCompression.revokePreviewUrl(this.imagePreviewUrl);
      }
      this.imagePreviewUrl = this.imageCompression.createPreviewUrl(file);
      return;
    }

    // D7 (T-FE-08 S5): usuario canceló cámara o no seleccionó archivo.
    // Estado informativo; NO resetear datos digitados (currentStep, votos, etc.).
    this.captureCancelled = true;
    this.error.set(null);
  }

  ngOnDestroy(): void {
    // D1 cleanup: revocar URL efímera al desmontar la página.
    if (this.imagePreviewUrl) {
      this.imageCompression.revokePreviewUrl(this.imagePreviewUrl);
    }
  }

  irARevision(): void {
    this.error.set(null);
    this.currentStep = 'REVISION';
  }

  volverAVotos(): void {
    this.currentStep = 'VOTOS';
  }

  volverAImagen(): void {
    this.currentStep = 'IMAGEN';
  }

  guardarComoBorrador(): void {
    this.submitting.set(true);
    this.error.set(null);

    const payload = this.buildActaRequest();
    this.observerService.guardarBorrador(payload).subscribe({
      next: (res) => {
        if (this.selectedImageFile && res.id) {
          this.observerService
            .subirImagenActa(res.id, this.selectedImageFile)
            .subscribe({
              next: () => {
                this.finalizarEnvio();
              },
              error: () => {
                this.finalizarEnvio();
              },
            });
        } else {
          this.finalizarEnvio();
        }
      },
      error: (err) => {
        this.submitting.set(false);
        this.error.set(
          'Error al guardar el borrador. ' + (err.error?.message || ''),
        );
      },
    });
  }

  enviarActaFinal(): void {
    if (!this.isChecksumValid) {
      this.error.set('Verificación electoral inválida: los votos no cuadran.');
      return;
    }

    this.submitting.set(true);
    this.error.set(null);

    const payload = this.buildActaRequest();
    this.observerService.enviarActaDirecta(payload).subscribe({
      next: (res) => {
        if (this.selectedImageFile && res.id) {
          this.observerService
            .subirImagenActa(res.id, this.selectedImageFile)
            .subscribe({
              next: () => {
                // Éxito total: limpiar draft.
                void this.offlineStorage.eliminarBorrador(
                  OfflineStorageService.draftId(this.jrvId, this.cargoId),
                );
                this.finalizarEnvio();
              },
              error: (imgErr) => {
                console.warn(
                  'Acta enviada pero falló la subida de evidencia fotográfica:',
                  imgErr,
                );
                this.finalizarEnvio();
              },
            });
        } else {
          void this.offlineStorage.eliminarBorrador(
            OfflineStorageService.draftId(this.jrvId, this.cargoId),
          );
          this.finalizarEnvio();
        }
      },
      error: (err) => {
        this.submitting.set(false);
        this.error.set(
          'Error al enviar el acta: ' +
            (err.error?.message || 'Revisa la conexión.'),
        );
        // T-FE-09 S2 / G1: encolar en IndexedDB para sync posterior.
        void this.encolarActaOffline(payload, err);
      },
    });
  }

  /**
   * T-FE-09 S2: encolar acta + foto en IndexedDB tras fallo HTTP.
   * El SyncService la transmitirá al recuperar señal.
   */
  private async encolarActaOffline(
    payload: ActaRequest,
    _err: unknown,
  ): Promise<void> {
    try {
      await this.offlineStorage.encolarActa({
        jrvId: this.jrvId,
        cargoId: this.cargoId,
        payload,
        imageBlob: this.selectedImageFile,
      });
      await this.offlineStorage.eliminarBorrador(
        OfflineStorageService.draftId(this.jrvId, this.cargoId),
      );
      this.error.set(
        'Acta guardada en este dispositivo. Se transmitirá en cuanto recupere señal.',
      );
    } catch (enqueueErr) {
      console.error('Error al encolar acta offline:', enqueueErr);
    }
  }

  private buildActaRequest(): ActaRequest {
    const detalles: ActaDetalleItem[] = this.candidatos.map((c) => ({
      candidatoId: c.id,
      votos: this.getVoto(c.id),
      candidatoNombre: c.nombre,
      partidoSiglas: c.partidoSiglas,
    }));

    return {
      jrvId: this.jrvId,
      cargoId: this.cargoId,
      totalSufragantes: this.totalSufragantes,
      votosBlancos: this.votosBlancos,
      votosNulos: this.votosNulos,
      detalles,
    };
  }

  private finalizarEnvio(): void {
    this.submitting.set(false);
    this.submitSuccess = true;
    setTimeout(() => {
      this.router.navigate(['/observer/jrv', this.jrvId, 'cargos']);
    }, 1800);
  }

  cancelar(): void {
    this.router.navigate(['/observer/jrv', this.jrvId, 'cargos']);
  }
}
