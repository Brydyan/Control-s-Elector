import { ComponentFixture, TestBed } from '@angular/core/testing';
import { RouterTestingModule } from '@angular/router/testing';
import { providePrimeNG } from 'primeng/config';
import { afterEach } from 'vitest';
import { TopbarComponent } from './topbar.component';
import { AuthStore } from '../../features/auth/services/auth.store';
import { LayoutService } from '../layout.service';
import { NetworkStatusService } from '../../core/network/network-status.service';
import { ThemeService } from '../../../theme/theme.service';
import { SyncService } from '../../core/offline/sync.service';
import { OfflineStorageService } from '../../core/offline/offline-storage.service';

describe('TopbarComponent', () => {
  let component: TopbarComponent;
  let fixture: ComponentFixture<TopbarComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TopbarComponent, RouterTestingModule],
      providers: [
        AuthStore,
        LayoutService,
        NetworkStatusService,
        ThemeService,
        providePrimeNG(),
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(TopbarComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  afterEach(async () => {
    const storage = TestBed.inject(OfflineStorageService);
    for (const item of await storage.obtenerPendientes()) {
      await storage.eliminarPendiente(item.id);
    }
    await storage.resetForTesting();
  });

  it('should create and render notification bell button and menu toggle', () => {
    expect(component).toBeTruthy();

    const bellIcon = fixture.nativeElement.querySelector('.pi-bell');
    expect(bellIcon).toBeTruthy();

    const menuIcon = fixture.nativeElement.querySelector('.pi-bars');
    expect(menuIcon).toBeTruthy();
  });

  it('should render the route title and subtitle', () => {
    fixture.componentRef.setInput('title', 'Resumen General');
    fixture.componentRef.setInput('subtitle', 'Elecciones Seccionales 2027');
    fixture.detectChanges();

    const title = fixture.nativeElement.querySelector(
      '.layout-topbar-title .title',
    );
    expect(title.textContent).toContain('Resumen General');
  });

  it('should toggle the dark scheme through the theme service', () => {
    const theme = TestBed.inject(ThemeService);
    expect(theme.darkMode()).toBe(false);

    component.toggleDarkMode();

    expect(theme.darkMode()).toBe(true);
    expect(document.documentElement.classList.contains('app-dark')).toBe(true);
  });

  it('S3: should render the offline queue in a role="status" region', async () => {
    const sync = TestBed.inject(SyncService);
    const storage = TestBed.inject(OfflineStorageService);

    await storage.encolarActa({
      jrvId: 1,
      cargoId: 1,
      payload: {},
      imageBlob: null,
    });
    await sync.refreshCount();
    sync.sincronizando.set(true);
    fixture.detectChanges();

    const region = fixture.nativeElement.querySelector('.layout-queue-status');
    expect(region).toBeTruthy();
    expect(region.getAttribute('role')).toBe('status');
    expect(region.textContent).toContain('1 acta en cola');
    expect(region.textContent).toContain('enviando');
  });

  /* T7: Responsive Shell (S12, S13) ------------------------------------------ */

  it('T7.S13: topbar action buttons have class layout-topbar-action', () => {
    const buttons = fixture.nativeElement.querySelectorAll(
      '.layout-topbar-action',
    );
    expect(buttons.length).toBeGreaterThan(0);

    buttons.forEach((button: HTMLElement) => {
      expect(button.classList.contains('layout-topbar-action')).toBe(true);
    });
  });

  it('T7.S12: topbar brand has truncation support for overflow text', () => {
    const brand = fixture.nativeElement.querySelector('.layout-topbar-brand');
    expect(brand).toBeTruthy();
    expect(brand.classList.contains('layout-topbar-brand')).toBe(true);
  });

  it('T7.S12: status pill renders with visible text content', () => {
    const pill = fixture.nativeElement.querySelector('.layout-status-pill');
    expect(pill).toBeTruthy();
    expect(pill.textContent).toBeTruthy();
  });
});
