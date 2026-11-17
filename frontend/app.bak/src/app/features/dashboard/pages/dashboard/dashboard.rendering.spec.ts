import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { providePrimeNG } from 'primeng/config';
import { DashboardPage } from './dashboard.page';
import { DashboardApi } from '../../data/dashboard.api';
import { DashboardRealtimeService } from '../../data/dashboard-realtime.api';

/**
 * Rendering contract of the dashboard.
 *
 * The dashboard loads four endpoints through a forkJoin; if any of them fails
 * to arrive, the page would stay in its loading state forever. This asserts
 * that the state leaves the loading phase and that the summary reaches the DOM.
 */
describe('DashboardPage (rendering)', () => {
  let fixture: ComponentFixture<DashboardPage>;
  let page: DashboardPage;

  const dashService = {
    getDashboardCompleto: () =>
      of({
        resumen: {
          enviadas: 1,
          enDisputa: 1,
          corregidas: 1,
          aprobadas: 1,
          totalActivas: 4,
        },
        resultados: [],
        progreso: [],
        novedades: [],
      }),
  };

  const realtime = {
    start: () => {},
    stop: () => {},
    reload$: of(null),
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DashboardPage],
      providers: [
        { provide: DashboardApi, useValue: dashService },
        { provide: DashboardRealtimeService, useValue: realtime },
        providePrimeNG(),
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(DashboardPage);
    page = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  });

  it('should leave the loading state once the data arrives', () => {
    expect(page.loading()).toBe(false);
    expect(page.data()).not.toBeNull();
    expect(fixture.nativeElement.querySelector('app-loading-state')).toBeNull();
  });

  it('should render the four summary counters', () => {
    const values = Array.from(
      fixture.nativeElement.querySelectorAll(
        '.stat-card-value',
      ) as NodeListOf<HTMLElement>,
    ).map((el) => el.textContent?.trim());

    expect(values).toEqual(['1', '1', '1', '1']);
  });

  it('should render the progress banner', () => {
    expect(
      fixture.nativeElement.querySelector('.progress-banner'),
    ).toBeTruthy();
  });
});
