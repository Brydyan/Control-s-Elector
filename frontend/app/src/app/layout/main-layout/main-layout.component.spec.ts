import { ComponentFixture, TestBed } from '@angular/core/testing';
import { RouterTestingModule } from '@angular/router/testing';
import { MainLayoutComponent } from './main-layout.component';
import { LayoutService } from '../layout.service';
import { afterEach } from 'vitest';
import { OfflineStorageService } from '../../core/offline/offline-storage.service';

describe('MainLayoutComponent — T8: Accessibility Hardening', () => {
  let component: MainLayoutComponent;
  let fixture: ComponentFixture<MainLayoutComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [MainLayoutComponent, RouterTestingModule],
      providers: [LayoutService],
    }).compileComponents();

    fixture = TestBed.createComponent(MainLayoutComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  afterEach(async () => {
    const storage = TestBed.inject(OfflineStorageService);
    await storage.resetForTesting();
  });

  describe('T8.S14: Skip Link for Keyboard Navigation', () => {
    it('should render a skip link as first focusable element', () => {
      const skipLink = fixture.nativeElement.querySelector('.layout-skip-link');
      expect(skipLink).toBeTruthy();
      expect(skipLink.textContent).toContain('Saltar al contenido principal');
    });

    it('skip link should have correct href', () => {
      const skipLink = fixture.nativeElement.querySelector('.layout-skip-link');
      expect(skipLink.getAttribute('href')).toBe('#layout-main');
    });

    it('should have main content with id="layout-main" and tabindex="-1"', () => {
      const mainElement = fixture.nativeElement.querySelector('#layout-main');
      expect(mainElement).toBeTruthy();
      expect(mainElement.getAttribute('tabindex')).toBe('-1');
    });
  });
});
