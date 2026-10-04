import { CommonModule } from '@angular/common';
import {
  Component,
  OnDestroy,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { Subscription } from 'rxjs';
import {
  ButtonComponent,
  ErrorStateComponent,
  LoadingStateComponent,
} from '../../shared/ui';
import {
  DashboardData,
  Novedad,
  ProgresoCanton,
  ResumenActas,
  ResultadoCargo,
} from './models/dashboard.model';
import { DashboardRealtimeService } from './services/dashboard-realtime.service';
import { DashboardService } from './services/dashboard.service';

/**
 * ADMIN/MASTER/COORDINATOR dashboard — "Resumen General".
 *
 * T-FE-11 B.1+B.2: conectado al backend via DashboardService (4 endpoints REST).
 * T-FE-11 C.1+C.2: invalidación reactiva via DashboardRealtimeService
 * (Supabase Realtime con debounce 500ms).
 *
 * G1: el backend es autoridad absoluta sobre sumas; FE no recalcula nada.
 * G3: rutas canónicas `/api/v1/dashboard/*`.
 */
@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [
    CommonModule,
    ButtonComponent,
    LoadingStateComponent,
    ErrorStateComponent,
  ],
  templateUrl: './dashboard.page.html',
  styleUrl: './dashboard.page.scss',
})
export class DashboardPage implements OnInit, OnDestroy {
  private readonly dashboardService = inject(DashboardService);
  private readonly realtime = inject(DashboardRealtimeService);

  // Estado expuesto al template.
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly data = signal<DashboardData | null>(null);

  // Derivados para el template.
  readonly resumen = computed<ResumenActas>(
    () => this.data()?.resumen ?? this.emptyResumen(),
  );
  readonly resultados = computed<ResultadoCargo[]>(
    () => this.data()?.resultados ?? [],
  );
  readonly progreso = computed<ProgresoCanton[]>(
    () => this.data()?.progreso ?? [],
  );
  readonly novedades = computed<Novedad[]>(() => this.data()?.novedades ?? []);

  /** % global: total JRVs con acta (sum jrvsConActa) / total JRVs (sum totalJrvs). */
  readonly overallPct = computed(() => {
    const ps = this.progreso();
    const totalJrvs = ps.reduce((s, p) => s + p.totalJrvs, 0);
    const jrvsConActa = ps.reduce((s, p) => s + p.jrvsConActa, 0);
    return totalJrvs === 0 ? 0 : Math.round((jrvsConActa / totalJrvs) * 100);
  });

  /** Total JRVs sumando los cantones (para `sentActos / totalJrv` del template). */
  readonly totalJrv = computed(() =>
    this.progreso().reduce((s, p) => s + p.totalJrvs, 0),
  );

  /** SentActos = JRVs con acta (sum jrvsConActa). */
  readonly sentActos = computed(() =>
    this.progreso().reduce((s, p) => s + p.jrvsConActa, 0),
  );

  private reloadSub: Subscription | null = null;

  ngOnInit(): void {
    this.cargar();
    this.realtime.start();
    // C.1/C.2: invalidar caché cuando Supabase emita eventos debounced.
    this.reloadSub = this.realtime.reload$.subscribe(() => {
      this.cargar();
    });
  }

  ngOnDestroy(): void {
    this.reloadSub?.unsubscribe();
    this.realtime.stop();
  }

  cargar(): void {
    this.loading.set(true);
    this.error.set(null);
    this.dashboardService.getDashboardCompleto().subscribe({
      next: (data) => {
        this.data.set(data);
        this.loading.set(false);
      },
      error: (err: unknown) => {
        const msg =
          err instanceof Error
            ? err.message
            : 'No se pudo cargar el dashboard.';
        this.error.set(msg);
        this.loading.set(false);
      },
    });
  }

  pct(canton: ProgresoCanton): number {
    return Math.round(canton.porcentaje);
  }

  /** Color tier: ≥100 verde, ≥50 azul, <50 ámbar. */
  tier(canton: ProgresoCanton): 'green' | 'blue' | 'amber' {
    const p = this.pct(canton);
    return p >= 100 ? 'green' : p >= 50 ? 'blue' : 'amber';
  }

  reintentar(): void {
    this.cargar();
  }

  private emptyResumen(): ResumenActas {
    return {
      enviadas: 0,
      enDisputa: 0,
      corregidas: 0,
      aprobadas: 0,
      totalActivas: 0,
    };
  }
}
