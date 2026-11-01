import { CommonModule } from '@angular/common';
import {
  Component,
  ElementRef,
  OnDestroy,
  OnInit,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { InputText } from 'primeng/inputtext';
import { Steps } from 'primeng/steps';
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
import { IDEMPOTENCY_HEADER } from '../../../../core/offline/sync.service';
import {
  ActaDetalleItem,
  ActaRequest,
  CandidatoInfo,
} from '../../models/observer.model';
import { ActaStorageService } from '../../services/acta-storage.service';
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

/**
 * Tope absoluto de un campo de votos mientras el padrón todavía no se
 * conoce. Es el valor declarado en el atributo `max`: el navegador marca
 * el campo como fuera de rango, pero NO recorta lo que el observador
 * escribió (recortarlo cambiaría el acta).
 */
const MAX_VOTOS_ABSOLUTO = 1_000_000;

/**
 * Espera del autoguardado del borrador durable (IndexedDB).
 *
 * Escribir en IndexedDB en cada tecla castiga a un teléfono viejo y no
 * aporta nada: lo que se protege es la caída del proceso, no el instante.
 */
const DELAY_GUARDADO_BORRADOR_MS = 400;

/**
 * Normaliza lo que el observador escribe en un campo de votos.
 *
 * Sólo admite enteros no negativos: `1.5` → `1`, `-5` → `0`, `abc` → `0`.
 * El valor devuelto es EXACTAMENTE el que se registra en el acta y el que
 * se escribe de vuelta en el DOM: lo que el veedor ve tiene que ser lo que
 * se guarda (S5).
 */
export function normalizarCantidad(raw: string | null | undefined): number {
  const parsed = Number.parseInt((raw ?? '').trim(), 10);
  if (!Number.isFinite(parsed) || parsed <= 0) {
    return 0;
  }
  return parsed;
}

/**
 * Escribe el valor ya normalizado de vuelta en el input.
 *
 * Angular sólo escribe un binding cuando el valor enlazado cambia: si el
 * valor normalizado es el mismo que ya había (por ejemplo `0` después de
 * un `-5`), el DOM conservaría lo que el observador escribió y la pantalla
 * discreparía del acta. La escritura explícita cierra esa brecha.
 */
function escribirCantidad(input: HTMLInputElement, valor: number): void {
  const texto = String(valor);
  if (input.value !== texto) {
    input.value = texto;
  }
}

@Component({
  selector: 'app-registrar-acta',
  standalone: true,
  imports: [
    CommonModule,
    CardComponent,
    ButtonComponent,
    LoadingStateComponent,
    ErrorStateComponent,
    InputText,
    Steps,
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
<<<<<<< HEAD
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
=======
   * Paso activo del formulario.
   *
   * Es una señal y no una propiedad suelta: Angular 21 sólo re-renderiza la
   * vista cuando un estado del que la plantilla depende es una señal o
   * cambia dentro de un evento. Con una propiedad común, cambiar de paso
   * desde el código dejaba la pantalla con el paso anterior.
   */
  readonly currentStep = signal<Step>('VOTOS');

  /** The model of the step strip, shown read-only above the form. */
  readonly stepModel = STEP_LABELS;

  /** Index of the active step in the strip. */
  get stepIndex(): number {
    return STEP_INDEX[this.currentStep()];
  }
  readonly loading = signal(true);
  readonly submitting = signal(false);

  /** Error de la operación: se anuncia con `role="alert"`. */
  readonly error = signal<string | null>(null);

  /**
   * Mensaje de éxito: es un estado PROPIO, no un error. Se anuncia con
   * `role="status"`; un acta encolada no se presenta como un fallo, porque
   * eso invita a enviarla otra vez (S4).
   */
  readonly success = signal<string | null>(null);

  /** El acta se registró (o se encoló): la pantalla ya no ofrece envío. */
  readonly submitSuccess = signal(false);

  /** El acta quedó en la cola offline: no se vuelve a enviar. */
  readonly actaEncolada = signal(false);

  /** Aviso transitorio de guardado de borrador. */
  readonly draftSavedMessage = signal<string | null>(null);

  /** Razón por la que la vía principal no deja continuar. */
  readonly motivoBloqueo = signal<string | null>(null);

  /** Pide confirmación antes de abandonar el formulario con datos sin enviar. */
  readonly confirmarSalida = signal(false);

  /** Panel de confirmación de salida (recibe el foco al abrirse). */
  private readonly panelConfirmacion =
    viewChild<ElementRef<HTMLElement>>('panelConfirmacion');

  /** Explicación del checksum (recibe el foco cuando bloquea el avance). */
  private readonly explicacionChecksum = viewChild<ElementRef<HTMLElement>>(
    'explicacionChecksum',
  );
>>>>>>> origin/develop

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
<<<<<<< HEAD
        this.loading = false;
=======
        // Restaurar borrador local si existe
        this.restaurarBorradorLocal();
        this.loading.set(false);
>>>>>>> origin/develop
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
<<<<<<< HEAD
    this.temporizadorGuardado = setTimeout(() => {
      this.temporizadorGuardado = null;
      void this.guardarBorradorOffline();
    }, DELAY_GUARDADO_BORRADOR_MS);
=======
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
    this.draftSavedMessage.set(msg);
    setTimeout(() => {
      if (this.draftSavedMessage() === msg) {
        this.draftSavedMessage.set(null);
      }
    }, 3000);
>>>>>>> origin/develop
  }

  /**
   * Autoguardado del paso 1.
   *
   * El borrador local se escribe de inmediato (es barato) y el borrador
   * durable de IndexedDB con un pequeño debounce. Los votos escritos no
   * dependen de que el observador llegue al paso 3 para quedar guardados:
   * una caída del proceso, un "atrás" del navegador o un bloqueo del
   * sistema no pueden llevarse el conteo (S6).
   */
  private registrarCambio(): void {
    this.guardarBorradorLocal();

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

  /**
   * Voto de un candidato.
   *
   * El valor se normaliza (sólo enteros no negativos) y se escribe de vuelta
   * en el input: el campo muestra exactamente lo que se registra (S5).
   */
  setVoto(candidatoId: number, event: Event): void {
    const input = event.target as HTMLInputElement;
<<<<<<< HEAD
    const value = Math.max(0, parseInt(input.value, 10) || 0);
    this.votosPorCandidato.set(candidatoId, value);
    this.registrarCambio();
  }

  onSufragantesChange(): void {
    this.registrarCambio();
=======
    const valor = normalizarCantidad(input.value);
    escribirCantidad(input, valor);
    this.votosPorCandidato.set(candidatoId, valor);
    this.registrarCambio();
  }

  /** Total de sufragantes del padrón de la mesa. */
  onTotalSufragantesChange(event: Event): void {
    const input = event.target as HTMLInputElement;
    const valor = normalizarCantidad(input.value);
    escribirCantidad(input, valor);
    this.totalSufragantes = valor;
    this.registrarCambio();
  }

  /** Votos en blanco. */
  onVotosBlancosChange(event: Event): void {
    const input = event.target as HTMLInputElement;
    const valor = normalizarCantidad(input.value);
    escribirCantidad(input, valor);
    this.votosBlancos = valor;
    this.registrarCambio();
  }

  /** Votos nulos. */
  onVotosNulosChange(event: Event): void {
    const input = event.target as HTMLInputElement;
    const valor = normalizarCantidad(input.value);
    escribirCantidad(input, valor);
    this.votosNulos = valor;
    this.registrarCambio();
  }

  /**
   * Tope declarado de un campo de votos: el padrón de la mesa cuando ya se
   * conoce. Es el rango que el navegador valida, NO un recorte: el acta
   * registra lo que el observador escribió aunque no cuadre.
   */
  get maxVotos(): number {
    return this.totalSufragantes > 0
      ? this.totalSufragantes
      : MAX_VOTOS_ABSOLUTO;
  }

  /**
   * Tope declarado del padrón: no hay un tope conocido de verdad, así que
   * se declara el máximo absoluto. El padrón manda sobre el resto de los
   * campos, no al revés.
   */
  get maxSufragantes(): number {
    return MAX_VOTOS_ABSOLUTO;
>>>>>>> origin/develop
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

<<<<<<< HEAD
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
=======
>>>>>>> origin/develop
  get isChecksumValid(): boolean {
    return (
      this.totalSufragantes > 0 &&
      this.totalVotosComputados === this.totalSufragantes
    );
  }

  /**
<<<<<<< HEAD
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
=======
   * Explicación concreta de la diferencia entre la suma de votos y el
   * padrón: "suma 12 ≠ padrón 15 — faltan 3".
   *
   * Un estado inválido sin cifras no le dice nada al veedor bajo presión;
   * lo que necesita es saber cuántos votos faltan o sobran (S6).
   */
  get descripcionDiferencia(): string {
    const suma = this.totalVotosComputados;
    const padron = this.totalSufragantes;

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
    return `suma ${suma} ≠ padrón ${padron}`;
  }

  /**
   * Hay datos escritos por el observador que todavía no se han enviado.
   */
  get hayVotosSinEnviar(): boolean {
    return this.totalVotosComputados > 0 || this.totalSufragantes > 0;
  }

  /**
   * El voto de un candidato supera el padrón de la mesa: más votos que
   * gente inscrita no puede ser correcto. El campo se marca como inválido
   * (WCAG 3.3.1) sin recortar el valor: el acta registra lo que el
   * observador escribió y la diferencia se explica en el checksum.
   */
  votoFueraDeRango(candidatoId: number): boolean {
    return (
      this.totalSufragantes > 0 &&
      this.getVoto(candidatoId) > this.totalSufragantes
>>>>>>> origin/develop
    );
  }

  /**
<<<<<<< HEAD
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
=======
   * Vía principal: continuar a la evidencia fotográfica.
   *
   * El botón NO se deshabilita cuando el checksum no cuadra: se valida al
   * pulsar y se explica la diferencia concreta. Un botón apagado sin
   * explicación deja al veedor encerrado (S6).
   */
  continuarAEvidencia(): void {
    if (this.isChecksumValid) {
      this.motivoBloqueo.set(null);
      this.irAPasoImagen();
      return;
    }

    this.motivoBloqueo.set(
      `No se puede continuar por la vía principal: ${this.descripcionDiferencia}.`,
    );
    this.enfocarExplicacion();
  }

  /**
   * Vía de escape: continuar DELIBERADAMENTE con un acta que no cuadra.
   * La evidencia fotográfica es la que respalda la inconsistencia, así que
   * el camino está disponible siempre que el checksum no cuadre, incluso
   * con cero votos (S6).
   */
  continuarConInconsistencia(): void {
    this.motivoBloqueo.set(null);
    this.irAPasoImagen();
  }

  /** Mueve el foco a la explicación del checksum (la razón del bloqueo). */
  private enfocarExplicacion(): void {
    setTimeout(() => {
      this.explicacionChecksum()?.nativeElement.focus();
    });
  }

  irAPasoImagen(): void {
    this.error.set(null);
    // T-FE-09 B.3 / V.4 — patrón "Guardar antes de capturar":
    // persistir el estado actual en IndexedDB antes de abrir la cámara
    // para sobrevivir un eventual LMK de Android.
>>>>>>> origin/develop
    void this.guardarBorradorOffline();
    this.currentStep.set('IMAGEN');
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
      this.error.set(null);

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
    this.error.set(null);
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
    this.error.set(null);
    this.currentStep.set('REVISION');
  }

  volverAVotos(): void {
    this.currentStep.set('VOTOS');
  }

  volverAImagen(): void {
    this.currentStep.set('IMAGEN');
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
    // T-FE-13 B.1 / S4: el tope duro de la CNE bloquea el envío. No es una
    // mesa especial: es un error de tipeo en el padrón.
    if (this.excedeTopeSufragantes) {
      this.error = this.alertaSufragantes;
      return;
    }

    if (!this.isChecksumValid) {
      // El botón de envío nunca se deshabilita: se valida al pulsar y se
      // dice exactamente cuántos votos faltan o sobran (S6).
      this.error.set(
        'Verificación electoral inválida: los votos no cuadran (' +
          this.descripcionDiferencia +
          ').',
      );
      return;
    }

    this.submitting.set(true);
    this.error.set(null);

    const payload = this.buildActaRequest();
    this.observerService
      .enviarActaDirecta(payload, {
        headers: { [IDEMPOTENCY_HEADER]: this.idempotencyKey },
      })
      .subscribe({
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
          // T-FE-09 S2 / G1: encolar en IndexedDB para sync posterior.
          void this.encolarActaOffline(payload, err);
        },
      });
  }

  /**
   * Clave de idempotencia del envío directo.
   *
   * Es derivada de (jrvId, cargoId) y no de un valor aleatorio: el mismo
   * acta necesita la misma clave en cada reintento para que el servidor la
   * deduplique en lugar de registrar dos.
   */
  private get idempotencyKey(): string {
    return `acta-${this.jrvId}-${this.cargoId}`;
  }

  /**
   * T-FE-09 S2: encolar acta + foto en IndexedDB tras fallo HTTP.
   * El SyncService la transmitirá al recuperar señal.
   */
  private async encolarActaOffline(
    payload: ActaRequest,
    err: unknown,
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
      // El acta está a salvo. Eso es un ÉXITO, no un error: se anuncia en la
      // región de estado (`role="status"`), no en la de error (`role="alert"`).
      // Presentarlo como fallo invita a enviar el acta otra vez (S4).
      this.error.set(null);
      this.actaEncolada.set(true);
      this.success.set(
        'Acta guardada en este dispositivo. Se transmitirá en cuanto recupere señal.',
      );
    } catch (enqueueErr) {
      console.error('Error al encolar acta offline:', enqueueErr);
      // El acta no quedó a salvo: esto sí es un error.
      this.error.set(
        'Error al enviar el acta: ' +
          ((err as { error?: { message?: string } })?.error?.message ||
            'Revisa la conexión.'),
      );
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
    this.submitSuccess.set(true);
    this.success.set(
      '¡Acta procesada exitosamente! Redirigiendo a las dignidades de la mesa...',
    );
    setTimeout(() => {
      this.router.navigate(['/observer/jrv', this.jrvId, 'cargos']);
    }, 1800);
  }

<<<<<<< HEAD
  private mostrarMensajeBorrador(msg: string): void {
    this.draftSavedMessage = msg;
    setTimeout(() => {
      if (this.draftSavedMessage === msg) {
        this.draftSavedMessage = null;
      }
    }, 3000);
  }

=======
  /**
   * "Cancelar y volver".
   *
   * Si el observador escribió votos que no se han enviado, la salida pide
   * confirmación: salir sin registrar el acta es una decisión, no un clic
   * (S6).
   */
>>>>>>> origin/develop
  cancelar(): void {
    if (this.hayVotosSinEnviar) {
      this.confirmarSalida.set(true);
      setTimeout(() => {
        this.panelConfirmacion()?.nativeElement.focus();
      });
      return;
    }
    this.salirDelFormulario();
  }

  /** El observador decide seguir escribiendo: se cierra la confirmación. */
  seguirEscribiendo(): void {
    this.confirmarSalida.set(false);
  }

  /** El observador confirma: vuelve a la lista de dignidades. */
  salirDelFormulario(): void {
    this.confirmarSalida.set(false);
    this.router.navigate(['/observer/jrv', this.jrvId, 'cargos']);
  }
}
