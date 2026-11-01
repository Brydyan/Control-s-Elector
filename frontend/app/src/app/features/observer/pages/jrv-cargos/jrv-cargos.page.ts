import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Dialog } from 'primeng/dialog';
import {
  BadgeComponent,
  ButtonComponent,
  CardComponent,
  EmptyStateComponent,
  ErrorStateComponent,
  LoadingStateComponent,
} from '../../../../shared/ui';
import { OfflineStorageService } from '../../../../core/offline/offline-storage.service';
import { CargoConEstado } from '../../models/observer.model';
import { ObserverService } from '../../services/observer.service';

@Component({
  selector: 'app-jrv-cargos',
  standalone: true,
  imports: [
    CommonModule,
    CardComponent,
    BadgeComponent,
    ButtonComponent,
    LoadingStateComponent,
    EmptyStateComponent,
    ErrorStateComponent,
    Dialog,
  ],
  templateUrl: './jrv-cargos.page.html',
  styleUrls: ['./jrv-cargos.page.scss'],
})
export class JrvCargosPage implements OnInit, OnDestroy {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly observerService = inject(ObserverService);
  private readonly offlineStorage = inject(OfflineStorageService);

  jrvId!: number;
  readonly cargos = signal<CargoConEstado[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);

  /**
   * Captura Rápida de Actas (T-FE-13 C.1).
   *
   * Es el flujo "Foto Primero" a nivel de mesa: el veedor recorre las
   * dignidades de la JRV y fotografía el acta física de cada una, sin tener
   * que digitar votos para pasar a la siguiente.
   *
   * La foto se guarda como `Blob` binario en el almacén `acta_drafts` de
   * IndexedDB —nunca en `localStorage` ni en Base64 (D4)—, de modo que
   * sobreviva al cierre de la pestaña por Android LMK.
   */
  readonly capturaRapidaVisible = signal(false);
  readonly capturaCargando = signal(false);
  /** Ids de cargo cuya acta ya tiene fotografía persistida en el dispositivo. */
  readonly cargosConFoto = signal<Set<number>>(new Set());
  /** Vistas previas efímeras de las fotos capturadas en esta sesión. */
  readonly previewsCaptura = signal<Map<number, string>>(new Map());

  /** La dignidad para la que se está tomando la foto en este momento. */
  private cargoEnCaptura: CargoConEstado | null = null;

  ngOnInit(): void {
    const idParam = this.route.snapshot.paramMap.get('jrvId');
    if (idParam) {
      this.jrvId = Number(idParam);
      this.cargarCargos();
    } else {
      this.error.set('Identificador de JRV inválido.');
      this.loading.set(false);
    }
  }

  cargarCargos(): void {
    this.loading.set(true);
    this.error.set(null);
    this.observerService.getCargosDeJrv(this.jrvId).subscribe({
      next: (data) => {
        this.cargos.set(data);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set('No se pudieron cargar las dignidades para esta mesa.');
        this.loading.set(false);
        console.error('Error loading cargos:', err);
      },
    });
  }

  registrarActa(cargo: CargoConEstado): void {
    this.router.navigate([
      '/observer/jrv',
      this.jrvId,
      'cargo',
      cargo.cargoId,
      'registrar',
    ]);
  }

  // -----------------------------------------------------------------
  // Captura Rápida de Actas (T-FE-13 C.1)
  // -----------------------------------------------------------------

  /** Abre el panel de captura rápida y recupera las fotos ya tomadas. */
  async abrirCapturaRapida(): Promise<void> {
    this.capturaRapidaVisible.set(true);
    await this.cargarFotosDeLaJrv();
  }

  /**
   * Lee los borradores de la JRV para saber qué actas ya tienen fotografía.
   *
   * Sin esto el veedor fotografía dos veces la misma acta: el trabajo de
   * campo es el recurso más escaso de la jornada.
   */
  private async cargarFotosDeLaJrv(): Promise<void> {
    this.capturaCargando.set(true);
    try {
      const borradores = await this.offlineStorage.obtenerBorradoresDeJrv(
        this.jrvId,
      );
      const conFoto = new Set<number>();
      Object.values(borradores).forEach((borrador) => {
        if (!borrador.imageBlob) return;
        conFoto.add(borrador.cargoId);
        this.crearPreview(borrador.cargoId, borrador.imageBlob);
      });
      this.cargosConFoto.set(conFoto);
    } catch (error) {
      console.error('Error al leer las fotos de la JRV:', error);
    } finally {
      this.capturaCargando.set(false);
    }
  }

  /** Marca la dignidad para la que se abrirá la cámara. */
  prepararCaptura(cargo: CargoConEstado, input: HTMLInputElement): void {
    this.cargoEnCaptura = cargo;
    input.click();
  }

  /**
   * Guarda la foto de la dignidad en captura como `Blob` binario.
   *
   * Se escribe en `acta_drafts` con la misma clave que usa el formulario de
   * registro (`draft_{jrvId}_{cargoId}`): la captura rápida y el formulario
   * comparten un único borrador por dignidad.
   */
  async onFotoCapturada(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    const cargo = this.cargoEnCaptura;
    this.cargoEnCaptura = null;
    if (!file || !cargo) return;

    try {
      await this.offlineStorage.guardarFotoBorrador(
        OfflineStorageService.draftId(this.jrvId, cargo.cargoId),
        this.jrvId,
        cargo.cargoId,
        file,
        file.name,
      );
      this.cargosConFoto.update((actuales) =>
        new Set(actuales).add(cargo.cargoId),
      );
      this.crearPreview(cargo.cargoId, file);
    } catch (error) {
      console.error('Error al guardar la foto de la captura rápida:', error);
      this.error.set(
        'No se pudo guardar la fotografía de esta dignidad en el dispositivo.',
      );
    } finally {
      // Se limpia el input para que volver a elegir el mismo archivo dispare
      // el evento `change`.
      input.value = '';
    }
  }

  /**
   * Crea la vista previa efímera de una foto.
   *
   * `URL.createObjectURL` no copia la imagen: la referencia al Blob. Se
   * revocan todas al salir de la pantalla (T-FE-08 D1).
   */
  private crearPreview(cargoId: number, blob: Blob): void {
    if (!(blob instanceof Blob)) return;
    const previaAnterior = this.previewsCaptura().get(cargoId);
    if (previaAnterior) {
      URL.revokeObjectURL(previaAnterior);
    }
    const url = URL.createObjectURL(blob);
    this.previewsCaptura.update((actuales) => {
      const copia = new Map(actuales);
      copia.set(cargoId, url);
      return copia;
    });
  }

  /** ¿Esta dignidad ya tiene fotografía persistida en el dispositivo? */
  tieneFoto(cargoId: number): boolean {
    return this.cargosConFoto().has(cargoId);
  }

  /** Vista previa de la foto de una dignidad, si la hay. */
  previewDe(cargoId: number): string | null {
    return this.previewsCaptura().get(cargoId) ?? null;
  }

  ngOnDestroy(): void {
    this.previewsCaptura().forEach((url) => URL.revokeObjectURL(url));
    this.previewsCaptura.set(new Map());
  }

  volver(): void {
    this.router.navigate(['/observer/mis-jrvs']);
  }
}
