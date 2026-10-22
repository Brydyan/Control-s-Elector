import { CommonModule } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import {
  BadgeComponent,
  ButtonComponent,
  CardComponent,
  EmptyStateComponent,
  ErrorStateComponent,
  LoadingStateComponent,
} from '../../../../shared/ui';
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
  ],
  templateUrl: './jrv-cargos.page.html',
  styleUrls: ['./jrv-cargos.page.scss'],
})
export class JrvCargosPage implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly observerService = inject(ObserverService);

  jrvId!: number;
  readonly cargos = signal<CargoConEstado[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);

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

  volver(): void {
    this.router.navigate(['/observer/mis-jrvs']);
  }
}
