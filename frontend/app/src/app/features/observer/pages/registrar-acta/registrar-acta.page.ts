import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import {
  ButtonComponent,
  CardComponent,
  ErrorStateComponent,
  LoadingStateComponent,
} from '../../../../shared/ui';

import {
  ActaDetalleItem,
  ActaRequest,
  CandidatoInfo,
} from '../../models/observer.model';
import { ActaStorageService } from '../../services/acta-storage.service';
import { ObserverService } from '../../services/observer.service';

type Step = 'VOTOS' | 'IMAGEN' | 'REVISION';

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
export class RegistrarActaPage implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly observerService = inject(ObserverService);
  private readonly storageService = inject(ActaStorageService);

  jrvId!: number;
  cargoId!: number;

  currentStep: Step = 'VOTOS';
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

        // Restaurar borrador local si existe
        this.restaurarBorradorLocal();
        this.loading = false;
      },
      error: (err) => {
        this.error = 'No se pudieron cargar los candidatos para esta dignidad.';
        this.loading = false;
        console.error('Error loading candidatos:', err);
      },
    });
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
    this.error = null;
    this.currentStep = 'IMAGEN';
  }

  onFileSelected(event: Event): void {
    const target = event.target as HTMLInputElement;
    if (target.files && target.files[0]) {
      const file = target.files[0];
      this.selectedImageFile = file;

      const reader = new FileReader();
      reader.onload = () => {
        this.imagePreviewUrl = reader.result as string;
      };
      reader.readAsDataURL(file);
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
          this.finalizarEnvio();
        }
      },
      error: (err) => {
        this.submitting = false;
        this.error =
          'Error al enviar el acta: ' +
          (err.error?.message || 'Revisa la conexión.');
      },
    });
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

  cancelar(): void {
    this.router.navigate(['/observer/jrv', this.jrvId, 'cargos']);
  }
}
