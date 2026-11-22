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
import { Message } from 'primeng/message';
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
} from '../../domain/models/observer.model';
import { ObserverApi } from '../../data/observer.api';

/**
 * Los tres pasos del wizard, en el orden en que se muestran.
 *
 * T-FE-13 D1 invierte el orden histórico: la evidencia fotográfica se captura
 * PRIMERO, antes de que el veedor escriba un solo voto. Si las autoridades
 * desalojan el aula mientras se digita, la foto del acta ya está asegurada en
 * el dispositivo.
 */
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

/** The steps of the form, in the order they are shown. */
const STEP_LABELS: Array<{ label: string }> = [
  { label: 'Foto' },
  { label: 'Votos' },
  { label: 'Revisión' },
];

const STEP_INDEX: Record<Step, number> = {
  IMAGEN: 0,
  VOTOS: 1,
  REVISION: 2,
};

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
    Message,
  ],
  templateUrl: './registrar-acta.page.html',
  styleUrls: ['./registrar-acta.page.scss'],
})
export class RegistrarActaPage implements OnInit, OnDestroy {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly observerService = inject(ObserverApi);
  private readonly imageCompression = inject(ImageCompressionService);
  private readonly offlineStorage = inject(OfflineStorageService);

  jrvId!: number;
  cargoId!: number;
  actaIdEnDisputa: number | null = null;
  readonly esModoDisputa = signal(false);
  readonly motivoDisputa = signal<string | null>(null);

  /**
   * Paso activo del wizard (T-FE-13 D1).
   *
   * Es una señal y no una propiedad suelta: Angular 21 sólo re-renderiza la
   * vista cuando un estado del que la plantilla depende es una señal o
   * cambia dentro de un evento. Con una propiedad común, cambiar de paso
   * desde el código dejaba la pantalla con el paso anterior.
   *
   * El valor inicial es `'IMAGEN'`: el modo "Foto Primero" empieza por la
   * evidencia, no por el conteo.
   */
  readonly currentStep = signal<Step>('IMAGEN');

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

  /**
   * T-FE-20 B.1: Compresión de imagen en curso.
   * Usado para deshabilitar botón envío y mostrar badge "Optimizando...".
   */
  readonly comprimiendoImagen = signal(false);

  /** Panel de confirmación de salida (recibe el foco al abrirse). */
  private readonly panelConfirmacion =
    viewChild<ElementRef<HTMLElement>>('panelConfirmacion');

  /** Explicación del checksum (recibe el foco cuando bloquea el avance). */
  private readonly explicacionChecksum = viewChild<ElementRef<HTMLElement>>(
    'explicacionChecksum',
  );

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

      const qpActaId =
        this.route.snapshot.queryParamMap?.get('actaId') ??
        this.route.snapshot.queryParams?.['actaId'];
      const qpModo =
        this.route.snapshot.queryParamMap?.get('modo') ??
        this.route.snapshot.queryParams?.['modo'];
      if (qpModo === 'disputa' && qpActaId) {
        this.actaIdEnDisputa = Number(qpActaId);
        this.esModoDisputa.set(true);
        this.cargarDisputaInfo();
      }

      this.cargarCandidatos();
    } else {
      this.error.set('Parámetros incompletos en la ruta.');
      this.loading.set(false);
    }
  }

  private cargarDisputaInfo(): void {
    if (!this.actaIdEnDisputa) return;
    this.observerService.getCorreccionesPendientes().subscribe({
      next: (actas) => {
        const acta = actas.find((a) => a.id === this.actaIdEnDisputa);
        if (acta) {
          if (acta.motivoDisputa) {
            this.motivoDisputa.set(acta.motivoDisputa);
          }
          if (acta.totalSufragantes) {
            this.totalSufragantes = acta.totalSufragantes;
          }
          if (acta.votosBlancos !== undefined) {
            this.votosBlancos = acta.votosBlancos;
          }
          if (acta.votosNulos !== undefined) {
            this.votosNulos = acta.votosNulos;
          }
          if (acta.detalles && acta.detalles.length > 0) {
            acta.detalles.forEach((d) => {
              this.votosPorCandidato.set(d.candidatoId, d.votos);
            });
          }
        }
      },
      error: (err) =>
        console.warn('No se pudo precargar el acta en disputa:', err),
    });
  }

  cargarCandidatos(): void {
    this.loading.set(true);
    this.error.set(null);
    this.observerService.getCatalogoDeJrv(this.jrvId).subscribe({
      next: (catalogo) => {
        const cargo = catalogo.find((c) => c.id === this.cargoId);
        this.candidatos = cargo?.candidatos ?? [];
        this.candidatos.forEach((c) => {
          if (!this.votosPorCandidato.has(c.id)) {
            this.votosPorCandidato.set(c.id, 0);
          }
        });

        // T-FE-09 V.5: restaurar borrador IndexedDB si existe (resiliencia LMK).
        // T-FE-13 A.2: el borrador incluye la foto de la evidencia.
        void this.restaurarBorradorOffline();
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
   * aplanado (o si el borrador es anterior a T-FE-13 y no tiene foto), se
   * ignora en lugar de romper la pantalla: el veedor puede volver a tomar la
   * fotografía.
   */
  private restaurarFotoBorrador(draft: ActaDraft): void {
    const blob = draft.imageBlob;
    // `typeof === 'string'` descarta el Base64: una foto serializada como
    // texto es exactamente lo que D4 prohíbe (y lo que el DOM no puede
    // previsualizar sin convertirlo otra vez en Base64).
    if (!blob || typeof blob === 'string' || !(blob instanceof Blob)) return;

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
   *
   * Los votos escritos no dependen de que el observador llegue al paso 3 para
   * quedar guardados: una caída del proceso, un "atrás" del navegador o un
   * bloqueo del sistema no pueden llevarse el conteo (S6).
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

  private mostrarMensajeBorrador(msg: string): void {
    this.draftSavedMessage.set(msg);
    setTimeout(() => {
      if (this.draftSavedMessage() === msg) {
        this.draftSavedMessage.set(null);
      }
    }, 3000);
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
   * Tope declarado del padrón: el máximo CNE de una JRV (T-FE-13 D2).
   *
   * El navegador marca el campo como fuera de rango, pero no recorta lo
   * escrito: el banner de cota explica el error y bloquea la transmisión.
   */
  get maxSufragantes(): number {
    return TOPE_SUFRAGANTES_CNE;
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

  /**
   * Cuadre electoral estricto 1:1 (T-FE-13 B.2, D3).
   *
   * La suma de candidatos + blancos + nulos tiene que ser EXACTAMENTE el
   * padrón de la JRV. No hay tolerancia ni margen de 3x: el backend rechaza
   * con 400 ante cualquier discrepancia y la base de datos guarda el mismo
   * invariante. Las actas de cargos pluripersonales o de listas CPCCS son
   * unívocas por cargo, así que cada acta cuadra 1:1 contra sus propios
   * sufragantes.
   */
  get isChecksumValid(): boolean {
    return (
      this.totalSufragantes > 0 &&
      this.totalVotosComputados === this.totalSufragantes
    );
  }

  /**
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
   * El padrón excede el tope duro del CNE: el acta no se puede registrar.
   *
   * A diferencia de `esSufragantesValido`, un padrón todavía vacío NO cuenta
   * como excedido: lo que bloquea al veedor en ese caso es el checksum, y ya
   * tiene su propio mensaje con las cifras.
   */
  get excedeTopeSufragantes(): boolean {
    return this.totalSufragantes > TOPE_SUFRAGANTES_CNE;
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
    );
  }

  // -----------------------------------------------------------------
  // Navegación entre pasos (T-FE-13 D1)
  // -----------------------------------------------------------------

  /**
   * Paso 1 → Paso 2: la foto ya está asegurada, se digitan los votos.
   *
   * Antes de cambiar de paso se persiste el borrador: el veedor puede cerrar
   * la pestaña en cualquier momento y lo escrito tiene que estar en el disco.
   */
  continuarAVotos(): void {
    this.error.set(null);
    this.motivoBloqueo.set(null);
    void this.guardarBorradorOffline();
    this.currentStep.set('VOTOS');
  }

  /**
   * Excepción documentada en D1: digitar los votos primero.
   *
   * Es la vía para las mesas sin cámara (o con la cámara ocupada). Deja el
   * acta sin evidencia fotográfica, así que la revisión final lo dice de
   * forma explícita: un acta sin foto no se puede transmitir con el cuadre
   * verificado.
   */
  digitarVotosPrimero(): void {
    this.error.set(null);
    this.motivoBloqueo.set(null);
    void this.guardarBorradorOffline();
    this.currentStep.set('VOTOS');
  }

  /**
   * Paso 2 → Paso 3 por la vía principal: el acta cuadra.
   *
   * El botón NO se deshabilita cuando el checksum no cuadra: se valida al
   * pulsar y se explica la diferencia concreta. Un botón apagado sin
   * explicación deja al veedor encerrado (S6).
   */
  continuarARevision(): void {
    if (this.isChecksumValid) {
      this.motivoBloqueo.set(null);
      this.irARevision();
      return;
    }

    this.motivoBloqueo.set(
      `No se puede continuar por la vía principal: ${this.descripcionDiferencia}.`,
    );
    this.enfocarExplicacion();
  }

  /**
   * Vía de escape: continuar DELIBERADAMENTE con un acta que no cuadra.
   *
   * La foto ya se capturó en el paso 1 (o se saltó por excepción), así que
   * aquí sólo queda llevar la inconsistencia a la revisión, donde se explica
   * y se puede guardar como borrador para que la coordinación la juzgue.
   */
  continuarConInconsistencia(): void {
    this.motivoBloqueo.set(null);
    this.irARevision();
  }

  /** Mueve el foco a la explicación del checksum (la razón del bloqueo). */
  private enfocarExplicacion(): void {
    setTimeout(() => {
      this.explicacionChecksum()?.nativeElement.focus();
    });
  }

  irARevision(): void {
    this.error.set(null);
    void this.guardarBorradorOffline();
    this.currentStep.set('REVISION');
  }

  /** Vuelve al paso 1 para retocar la evidencia fotográfica. */
  irAPasoImagen(): void {
    this.error.set(null);
    this.motivoBloqueo.set(null);
    // Patrón "Guardar antes de capturar" (T-FE-09 B.3 / V.4): persistir el
    // estado actual en IndexedDB antes de volver a abrir la cámara, para
    // sobrevivir un eventual LMK de Android.
    void this.guardarBorradorOffline();
    this.currentStep.set('IMAGEN');
  }

  /** Vuelve al paso 2 a corregir los votos. */
  irAPasoVotos(): void {
    this.error.set(null);
    this.currentStep.set('VOTOS');
  }

  /**
   * Persiste el estado electoral del wizard en IndexedDB (`acta_drafts`).
   *
   * Idempotente: reemplaza el borrador previo para esta (jrvId, cargoId) SIN
   * tocar la foto, que viaja en su propio canal.
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
   * Persiste la foto de la evidencia como `Blob` binario (T-FE-13 A.2/D4).
   *
   * La escritura es inmediata (no debounced): la foto se acaba de capturar y
   * es justo el momento en que el proceso puede morir. NUNCA Base64 ni
   * `data:` URL: el Base64 infla el heap del teléfono y precipita el cierre
   * por LMK en plena captura.
   */
  private async guardarFotoOffline(file: File): Promise<void> {
    const draftId = OfflineStorageService.draftId(this.jrvId, this.cargoId);
    try {
      await this.offlineStorage.guardarFotoBorrador(
        draftId,
        this.jrvId,
        this.cargoId,
        file,
        file.name,
      );
      this.mostrarMensajeBorrador('Fotografía guardada en este dispositivo.');
    } catch (error) {
      console.error('Error al guardar la fotografía del borrador:', error);
      this.error.set(
        'No se pudo guardar la fotografía en este dispositivo. Intenta de nuevo.',
      );
    }
  }

  /**
   * Abre la cámara (o el selector de archivos).
   *
   * Patrón "Guardar antes de capturar" (T-FE-13 A.2 / D4): Android LMK mata
   * el proceso de Chrome mientras la cámara está abierta. La cámara se abre
   * **después** de que el borrador quedó escrito: si se abriera antes, el
   * estado previo al disparo podría perderse justo en el instante en que la
   * evidencia corre más riesgo.
   */
  abrirCamara(input: HTMLInputElement): void {
    void this.guardarBorradorOffline().then(() => input.click());
  }

  onFileSelected(event: Event): void {
    const target = event.target as HTMLInputElement;
    if (target.files && target.files[0]) {
      const file = target.files[0];
      this.captureCancelled = false;
      this.error.set(null);

      // D1 (T-FE-08): usar URL.createObjectURL (Blob binario) en vez de
      // FileReader.readAsDataURL (que produce data:image/... Base64 en heap).
      if (this.imagePreviewUrl) {
        this.imageCompression.revokePreviewUrl(this.imagePreviewUrl);
      }

      // T-FE-20 B.1: Preview INMEDIATO del archivo original (latencia 0ms percibida).
      this.imagePreviewUrl = this.imageCompression.createPreviewUrl(file);

      // T-FE-20 B.1: Inicia compresión en background.
      this.comprimiendoImagen.set(true);
      this.procesarCompresionConGuardado(file);

      // Se limpia el input: si el veedor elige de nuevo el MISMO archivo (o
      // la cámara genera uno con el mismo nombre), el navegador no dispara
      // `change` —el valor del input no cambió— y la previsualización se
      // quedaría con la foto anterior. La Captura Rápida hace lo mismo
      // (`jrv-cargos.page.ts`).
      target.value = '';
      return;
    }

    // D7 (T-FE-08 S5): usuario canceló cámara o no seleccionó archivo.
    // Estado informativo; NO resetear datos digitados (currentStep, votos, etc.).
    this.captureCancelled = true;
    this.error.set(null);
  }

  /**
   * T-FE-20 B.1: Procesa la compresión de imagen y guardarla offline.
   * Maneja errores sin borrar votos digitados.
   * @private
   */
  private async procesarCompresionConGuardado(file: File): Promise<void> {
    try {
      // Comprimir archivo.
      const comprimido = await this.imageCompression.compress(file);
      // Asegurar que es File (con nombre .webp).
      if (!(comprimido instanceof File)) {
        throw new Error('Compresión retornó Blob, se esperaba File');
      }
      this.selectedImageFile = comprimido;
      // Guardar en IndexedDB.
      await this.guardarFotoOffline(comprimido);
    } catch (err) {
      console.error('Error en compresión:', err);
      this.error.set(
        'No se pudo optimizar la fotografía. Intenta de nuevo o usa otra foto.',
      );
      // NO limpiar votos digitados; el usuario puede reintentar sin perder progreso.
    } finally {
      this.comprimiendoImagen.set(false);
    }
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

  guardarComoBorrador(): void {
    // T-FE-20 B.1: Bloquear envío mientras la compresión esté en curso.
    if (this.comprimiendoImagen()) {
      this.error.set(
        'Espera a que se termine de optimizar la fotografía antes de guardar.',
      );
      return;
    }

    if (this.excedeTopeSufragantes) {
      this.error.set(this.mensajeTopeExcedido());
      return;
    }

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
    // T-FE-20 B.1: Bloquear envío mientras la compresión esté en curso.
    if (this.comprimiendoImagen()) {
      this.error.set(
        'Espera a que se termine de optimizar la fotografía antes de enviar.',
      );
      return;
    }

    // Tope duro del CNE (T-FE-13 B.1): un padrón de 501 sufragantes no
    // describe ninguna JRV posible. Se bloquea antes de tocar el servidor.
    if (this.excedeTopeSufragantes) {
      this.error.set(this.mensajeTopeExcedido());
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

    if (this.esModoDisputa() && this.actaIdEnDisputa) {
      this.observerService
        .corregirActa(this.actaIdEnDisputa, payload)
        .subscribe({
          next: (res) => {
            if (this.selectedImageFile && res.id) {
              this.observerService
                .subirImagenActa(res.id, this.selectedImageFile)
                .subscribe({
                  next: () => {
                    void this.offlineStorage.eliminarBorrador(
                      OfflineStorageService.draftId(this.jrvId, this.cargoId),
                    );
                    this.finalizarEnvio();
                  },
                  error: (imgErr) => {
                    console.warn(
                      'Acta corregida pero falló la subida de nueva evidencia fotográfica:',
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
              'Error al enviar la corrección del acta: ' +
                (err.error?.message || err.message || ''),
            );
          },
        });
      return;
    }

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

  /** Razón única del bloqueo por cota dura, en los dos caminos de envío. */
  private mensajeTopeExcedido(): string {
    return (
      'Límite CNE excedido: ninguna JRV puede superar 500 sufragantes. ' +
      'Verifica el total de sufragantes del padrón antes de enviar el acta.'
    );
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

  /**
   * "Cancelar y volver".
   *
   * Si el observador escribió votos que no se han enviado, la salida pide
   * confirmación: salir sin registrar el acta es una decisión, no un clic
   * (S6).
   */
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
