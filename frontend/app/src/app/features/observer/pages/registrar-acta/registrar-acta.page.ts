import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import {
  ButtonComponent,
  CardComponent,
  ErrorStateComponent,
  LoadingStateComponent,
} from '../../../../shared/ui';

import { ImageCompressionService } from '../../../../core/services/image-compression.service';
import {
  ActaDraft,
  OfflineStorageService,
} from '../../../../core/offline/offline-storage.service';
import {
  ActaDetalleItem,
  ActaRequest,
  CandidatoInfo,
} from '../../models/observer.model';
import { ObserverService } from '../../services/observer.service';

type Step = 'IMAGEN' | 'VOTOS' | 'REVISION';

/**
 * Padrón estándar CNE de una JRV (T-FE-13 D2).
 *
 * Superarlo NO invalida el acta: es una mesa especial y el veedor tiene que
 * confirmarlo. Por eso es un aviso, no un bloqueo.
 */
const PADRON_ESTANDAR_CNE = 350;

/**
 * Tope duro de sufragantes de una JRV (T-FE-13 D2).
 *
 * Ninguna JRV puede superar los 500 electores. Superarlo no es una mesa
 * especial: es un error de tipeo (2500, 3500) y por eso bloquea el envío.
 */
const TOPE_SUFRAGANTES_CNE = 500;

/**
 * Espera del autoguardado del borrador durable (IndexedDB).
 *
 * Escribir en IndexedDB en cada tecla castiga a un teléfono viejo y no
 * aporta nada: lo que se protege es la caída del proceso, no el instante.
 */
const DELAY_GUARDADO_BORRADOR_MS = 400;

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
  ],
  templateUrl: './registrar-acta.page.html',
  styleUrls: ['./registrar-acta.page.scss'],
})
export class RegistrarActaPage implements OnInit, OnDestroy {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly observerService = inject(ObserverService);
  private readonly imageCompression = inject(ImageCompressionService);
  private readonly offlineStorage = inject(OfflineStorageService);

  jrvId!: number;
  cargoId!: number;

  /**
   * Paso activo del wizard (T-FE-13 D1).
   *
   * El orden es "Foto Primero": la evidencia se captura en el paso 1, antes
   * de que el veedor escriba un solo voto. Si las autoridades desalojan el
   * aula mientras se digita, la foto ya está asegurada en el dispositivo.
   *
   * La excepción documentada en D1 es "Digitar votos primero": el botón
   * secundario de `continuarAVotos()` deja saltarlo cuando no hay cámara.
   */
  currentStep: Step = 'IMAGEN';
  loading = true;
  submitting = false;
  error: string | null = null;
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

  /** Temporizador del autoguardado del borrador durable. */
  private temporizadorGuardado: ReturnType<typeof setTimeout> | null = null;

  ngOnInit(): void {
    const jrvParam = this.route.snapshot.paramMap.get('jrvId');
    const cargoParam = this.route.snapshot.paramMap.get('cargoId');

    if (jrvParam && cargoParam) {
      this.jrvId = Number(jrvParam);
      this.cargoId = Number(cargoParam);
      this.cargarCandidatos();
    } else {
      this.error = 'Parámetros incompletos en la ruta.';
      this.loading = false;
    }
  }

  cargarCandidatos(): void {
    this.loading = true;
    this.error = null;
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
        this.loading = false;
      },
      error: (err) => {
        this.error = 'No se pudieron cargar los candidatos para esta dignidad.';
        this.loading = false;
        console.error('Error loading candidatos:', err);
      },
    });
  }

  /**
   * T-FE-09 V.5: tras cargar candidatos, consulta `acta_drafts` y, si hay
   * un borrador para (jrvId, cargoId), repobla el estado del formulario.
   *
   * T-FE-13 A.2: además de los votos, restaura la foto de la evidencia. Es
   * lo que hace que el modo "Foto Primero" sobreviva a un cierre de pestaña
   * por Android LMK: la foto no está en la RAM, está en el disco.
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
    this.restaurarFotoBorrador(draft);
    this.mostrarMensajeBorrador(
      'Borrador restaurado desde almacenamiento offline.',
    );
  }

  /**
   * Reconstruye la foto que venía en el borrador.
   *
   * Un IndexedDB real conserva el `Blob` intacto. Si el entorno lo devolvió
   * aplano (o si el borrador es anterior a T-FE-13 y no tiene foto), se
   * ignora en lugar de romper la pantalla: el veedor puede volver a tomar la
   * fotografía.
   */
  private restaurarFotoBorrador(draft: ActaDraft): void {
    const blob = draft.imageBlob;
    if (!blob || !(blob instanceof Blob)) return;

    const file = new File([blob], draft.imageName ?? 'acta-foto.webp', {
      type: blob.type || 'image/webp',
    });
    this.selectedImageFile = file;
    this.imagePreviewUrl = this.imageCompression.createPreviewUrl(file);
  }

  /**
   * Autoguardado del borrador electoral (T-FE-13 A.2).
   *
   * El borrador durable es IndexedDB, nunca `localStorage`: el almacenamiento
   * síncrono bloquea el hilo principal, no admite Blobs y revienta con
   * `QuotaExceededError` a los 5 MB (D4).
   */
  private registrarCambio(): void {
    if (this.temporizadorGuardado !== null) {
      clearTimeout(this.temporizadorGuardado);
    }
    this.temporizadorGuardado = setTimeout(() => {
      this.temporizadorGuardado = null;
      void this.guardarBorradorOffline();
    }, DELAY_GUARDADO_BORRADOR_MS);
  }

  getVoto(candidatoId: number): number {
    return this.votosPorCandidato.get(candidatoId) || 0;
  }

  setVoto(candidatoId: number, event: Event): void {
    const input = event.target as HTMLInputElement;
    const value = Math.max(0, parseInt(input.value, 10) || 0);
    this.votosPorCandidato.set(candidatoId, value);
    this.registrarCambio();
  }

  onSufragantesChange(): void {
    this.registrarCambio();
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

  /**
   * Cuadre electoral estricto 1:1 (T-FE-13 B.2, D3).
   *
   * La suma de candidatos + blancos + nulos tiene que ser EXACTAMENTE el
   * padrón de la JRV. No hay tolerancia: el backend rechaza con 400 ante
   * cualquier discrepancia y la base de datos guarda el mismo invariante.
   */
  get isChecksumValid(): boolean {
    return (
      this.totalSufragantes > 0 &&
      this.totalVotosComputados === this.totalSufragantes
    );
  }

  /**
   * Cota de sufragantes: el aviso que el veedor tiene que leer (T-FE-13 B.1).
   *
   * Dos niveles, porque son dos hechos distintos: superar el padrón estándar
   * es una mesa especial (aviso); superar el tope duro es un error de tipeo
   * (bloqueo).
   */
  get alertaSufragantes(): string | null {
    if (this.totalSufragantes > TOPE_SUFRAGANTES_CNE) {
      return 'Límite CNE excedido: ninguna JRV puede superar 500 sufragantes.';
    }
    if (this.totalSufragantes > PADRON_ESTANDAR_CNE) {
      return 'Advertencia: El padrón estándar es de 350 electores. Verifique si es una mesa especial.';
    }
    return null;
  }

  /**
   * Nivel del aviso de cota, para el color del banner (S3/S4).
   *
   * `warning` es una mesa especial; `error` es un error de tipeo que impide
   * transmitir el acta.
   */
  get nivelAlertaSufragantes(): 'warning' | 'error' | null {
    if (this.totalSufragantes > TOPE_SUFRAGANTES_CNE) return 'error';
    if (this.totalSufragantes > PADRON_ESTANDAR_CNE) return 'warning';
    return null;
  }

  /**
   * El padrón está dentro de la cota CNE (T-FE-13 B.1).
   *
   * Cero no es válido porque el padrón todavía no se ha escrito: eso lo
   * bloquea el checksum, no la cota.
   */
  get esSufragantesValido(): boolean {
    return (
      this.totalSufragantes > 0 && this.totalSufragantes <= TOPE_SUFRAGANTES_CNE
    );
  }

  /**
   * El padrón excede el tope duro: el acta no se puede transmitir (S4).
   */
  get excedeTopeSufragantes(): boolean {
    return this.totalSufragantes > TOPE_SUFRAGANTES_CNE;
  }

  /**
   * Avanza del paso de evidencia al de votos (T-FE-13 D1).
   *
   * La foto ya está asegurada: se capturó en el paso 1 y se guardó en
   * IndexedDB. Este paso es el que se puede saltar, con "Digitar votos
   * primero", cuando el dispositivo no tiene cámara utilizable.
   */
  continuarAVotos(): void {
    if (!this.selectedImageFile) {
      this.error =
        'Toma la fotografía del acta antes de digitar los votos, o usa "Digitar votos primero".';
      return;
    }
    this.error = null;
    this.currentStep = 'VOTOS';
    void this.guardarBorradorOffline();
  }

  /**
   * Excepción documentada en D1: digitar los votos sin cámara.
   *
   * El default operacional sigue siendo la foto primero; esto es para el
   * caso en que el dispositivo no puede fotografiar.
   */
  digitarVotosPrimero(): void {
    this.error = null;
    this.currentStep = 'VOTOS';
    void this.guardarBorradorOffline();
  }

  /**
   * Vuelve al paso de evidencia, por ejemplo para repetir una foto borrosa.
   *
   * Antes de abrir la cámara se persiste el borrador: es el patrón "Guardar
   * antes de capturar" (T-FE-09 V.4) que sobrevive a un LMK de Android.
   */
  irAPasoImagen(): void {
    this.error = null;
    void this.guardarBorradorOffline();
    this.currentStep = 'IMAGEN';
  }

  /**
   * Abre la cámara después de persistir el borrador.
   *
   * El disparo de la cámara es el momento de mayor riesgo de que el proceso
   * muera (LMK de Android). Con el estado ya en IndexedDB, perder el proceso
   * no pierde lo escrito.
   */
  abrirCamara(input: HTMLInputElement): void {
    void this.guardarBorradorOffline();
    input.click();
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
    });
  }

  /**
   * Persiste la foto de la evidencia, sin tocar los votos (T-FE-13 A.2).
   *
   * La escritura es inmediata y va a su propio canal: si el proceso muere
   * justo después del disparo, la foto sigue en el dispositivo.
   */
  private async guardarFotoOffline(): Promise<void> {
    if (!this.selectedImageFile) return;
    await this.offlineStorage.guardarFotoBorrador(
      OfflineStorageService.draftId(this.jrvId, this.cargoId),
      this.jrvId,
      this.cargoId,
      this.selectedImageFile,
      this.selectedImageFile.name,
    );
  }

  onFileSelected(event: Event): void {
    const target = event.target as HTMLInputElement;
    if (target.files && target.files[0]) {
      const file = target.files[0];
      this.selectedImageFile = file;
      this.captureCancelled = false;
      this.error = null;

      // D1 (T-FE-08): usar URL.createObjectURL (Blob binario) en vez de
      // FileReader.readAsDataURL (que produce data:image/... Base64 en heap).
      if (this.imagePreviewUrl) {
        this.imageCompression.revokePreviewUrl(this.imagePreviewUrl);
      }
      this.imagePreviewUrl = this.imageCompression.createPreviewUrl(file);

      // T-FE-13 A.2: la foto se asegura en el dispositivo en el mismo
      // instante en que se captura, antes de que se escriba un solo voto.
      void this.guardarFotoOffline();
      return;
    }

    // D7 (T-FE-08 S5): usuario canceló cámara o no seleccionó archivo.
    // Estado informativo; NO resetear datos digitados (currentStep, votos, etc.).
    this.captureCancelled = true;
    this.error = null;
  }

  ngOnDestroy(): void {
    if (this.temporizadorGuardado !== null) {
      clearTimeout(this.temporizadorGuardado);
      this.temporizadorGuardado = null;
      // Lo último escrito no se pierde al salir de la pantalla.
      void this.guardarBorradorOffline();
    }
    // D1 cleanup: revocar URL efímera al desmontar la página.
    if (this.imagePreviewUrl) {
      this.imageCompression.revokePreviewUrl(this.imagePreviewUrl);
    }
  }

  irARevision(): void {
    this.error = null;
    this.currentStep = 'REVISION';
  }

  volverAVotos(): void {
    this.currentStep = 'VOTOS';
  }

  volverAImagen(): void {
    this.currentStep = 'IMAGEN';
  }

  guardarComoBorrador(): void {
    this.submitting = true;
    this.error = null;

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
        this.submitting = false;
        this.error =
          'Error al guardar el borrador. ' + (err.error?.message || '');
      },
    });
  }

  enviarActaFinal(): void {
    // T-FE-13 B.1 / S4: el tope duro de la CNE bloquea el envío. No es una
    // mesa especial: es un error de tipeo en el padrón.
    if (this.excedeTopeSufragantes) {
      this.error = this.alertaSufragantes;
      return;
    }

    if (!this.isChecksumValid) {
      this.error = 'Verificación electoral inválida: los votos no cuadran.';
      return;
    }

    this.submitting = true;
    this.error = null;

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
        this.submitting = false;
        this.error =
          'Error al enviar el acta: ' +
          (err.error?.message || 'Revisa la conexión.');
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
      this.error =
        'Acta guardada en este dispositivo. Se transmitirá en cuanto recupere señal.';
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
    this.submitting = false;
    this.submitSuccess = true;
    setTimeout(() => {
      this.router.navigate(['/observer/jrv', this.jrvId, 'cargos']);
    }, 1800);
  }

  private mostrarMensajeBorrador(msg: string): void {
    this.draftSavedMessage = msg;
    setTimeout(() => {
      if (this.draftSavedMessage === msg) {
        this.draftSavedMessage = null;
      }
    }, 3000);
  }

  cancelar(): void {
    this.router.navigate(['/observer/jrv', this.jrvId, 'cargos']);
  }
}
