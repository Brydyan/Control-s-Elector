import { Component } from '@angular/core';

type ActaStatus = 'ENVIADA' | 'APROBADA' | 'EN_DISPUTA' | 'CORREGIDA';

interface StatCard {
  key: string;
  label: string;
  value: number;
  sub: string;
}

interface CantonProgress {
  name: string;
  code: string;
  done: number;
  total: number;
}

interface RecentActa {
  jrv: string;
  canton: string;
  cargo: string;
  status: ActaStatus;
  time: string;
}

/**
 * ADMIN dashboard — "Resumen General".
 *
 * NOTE: the figures below are placeholder data taken from the mockup.
 * There is no dashboard/summary endpoint yet; when it lands, replace
 * these fields with data fetched through a DashboardService and render
 * with signals. The template already binds to these properties.
 */
@Component({
  selector: 'app-dashboard',
  standalone: true,
  templateUrl: './dashboard.page.html',
  styleUrl: './dashboard.page.scss',
})
export class DashboardPage {
  readonly totalJrv = 450;
  readonly sentActas = 305;
  readonly overallPct = Math.round((this.sentActas / this.totalJrv) * 100);

  readonly stats: StatCard[] = [
    { key: 'total', label: 'Total Actas', value: 291, sub: 'De 450 JRVs' },
    {
      key: 'enviada',
      label: 'Enviadas',
      value: 142,
      sub: 'Pendientes revisión',
    },
    {
      key: 'disputa',
      label: 'En Disputa',
      value: 18,
      sub: 'Requieren corrección',
    },
    { key: 'aprobada', label: 'Aprobadas', value: 128, sub: 'Confirmadas' },
    {
      key: 'anulada',
      label: 'Anuladas',
      value: 3,
      sub: 'Excluidas del conteo',
    },
  ];

  readonly cantons: CantonProgress[] = [
    { name: 'La Libertad', code: '2402', done: 150, total: 150 },
    { name: 'Santa Elena', code: '2401', done: 144, total: 200 },
    { name: 'Salinas', code: '2403', done: 11, total: 100 },
  ];

  readonly recentActas: RecentActa[] = [
    {
      jrv: 'JRV 088',
      canton: 'La Libertad',
      cargo: 'Prefecto/a Provincial',
      status: 'APROBADA',
      time: '16:42',
    },
    {
      jrv: 'JRV 120',
      canton: 'Salinas',
      cargo: 'Consejero/a CPCCS',
      status: 'CORREGIDA',
      time: '16:38',
    },
    {
      jrv: 'JRV 002',
      canton: 'Santa Elena',
      cargo: 'Alcalde/sa Cantón',
      status: 'EN_DISPUTA',
      time: '16:21',
    },
    {
      jrv: 'JRV 015',
      canton: 'Chanduy',
      cargo: 'Vocal Junta Parroquial',
      status: 'ENVIADA',
      time: '16:10',
    },
    {
      jrv: 'JRV 001',
      canton: 'Santa Elena',
      cargo: 'Prefecto/a Provincial',
      status: 'APROBADA',
      time: '14:32',
    },
  ];

  pct(canton: CantonProgress): number {
    return canton.total === 0
      ? 0
      : Math.round((canton.done / canton.total) * 100);
  }

  /** Color tier by completion: full → green, mid → blue, low → amber. */
  tier(canton: CantonProgress): 'green' | 'blue' | 'amber' {
    const p = this.pct(canton);
    return p >= 100 ? 'green' : p >= 50 ? 'blue' : 'amber';
  }
}
