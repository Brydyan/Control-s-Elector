import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { of, throwError, Subject } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute } from '@angular/router';

import { SidebarComponent } from './sidebar.component';
import { AuthStore } from '../../features/auth/services/auth.store';
import { AuthService } from '../../features/auth/services/auth.service';
import { MenuService } from '../../core/menu/menu.service';
import { LayoutService } from '../layout.service';
import { Router } from '@angular/router';

describe('SidebarComponent (T-FE-18)', () => {
  let component: SidebarComponent;
  let fixture: ComponentFixture<SidebarComponent>;
  let authStoreMock: jasmine.SpyObj<AuthStore>;
  let authServiceMock: jasmine.SpyObj<AuthService>;
  let menuServiceMock: jasmine.SpyObj<MenuService>;
  let layoutServiceMock: jasmine.SpyObj<LayoutService>;
  let routerMock: any;
  let routerEventsSubject: Subject<any>;
  let userSignal: ReturnType<typeof signal>;

  beforeEach(async () => {
    authServiceMock = jasmine.createSpyObj('AuthService', ['logout']);
    menuServiceMock = jasmine.createSpyObj('MenuService', ['getMyMenus']);
    layoutServiceMock = jasmine.createSpyObj('LayoutService', [
      'isDesktop',
      'closeSidebar',
    ]);

    // Create a Subject for router.events to allow piping
    routerEventsSubject = new Subject();
    routerMock = {
      navigateByUrl: jasmine.createSpy('navigateByUrl'),
      events: routerEventsSubject.asObservable(),
    };

    // Create mutable user signal shared across all tests in this suite
    userSignal = signal({
      role: 'OBSERVER',
      email: 'test@example.com',
    });

    // authStoreMock as plain object with user Signal (not jasmine spy)
    authStoreMock = {
      user: userSignal,
    } as jasmine.SpyObj<AuthStore>;

    // Create mock for ActivatedRoute (required by RouterLink)
    const activatedRouteMock = {
      snapshot: {
        url: [{ path: '' }],
      },
    };

    await TestBed.configureTestingModule({
      imports: [SidebarComponent],
      providers: [
        { provide: AuthStore, useValue: authStoreMock },
        { provide: AuthService, useValue: authServiceMock },
        { provide: MenuService, useValue: menuServiceMock },
        { provide: LayoutService, useValue: layoutServiceMock },
        { provide: Router, useValue: routerMock },
        { provide: ActivatedRoute, useValue: activatedRouteMock },
      ],
    }).compileComponents();
  });

  const setUserRole = (
    role: 'OBSERVER' | 'COORDINATOR' | 'ADMIN' | 'MASTER',
  ) => {
    // Mutate shared signal (all tests see the updated value)
    userSignal.set({
      role,
      email: `${role.toLowerCase()}@example.com`,
    });
  };

  const createComponentWithRole = (
    role: 'OBSERVER' | 'COORDINATOR' | 'ADMIN' | 'MASTER',
  ) => {
    fixture = TestBed.createComponent(SidebarComponent);
    component = fixture.componentInstance;
    setUserRole(role);
  };

  // ============================================================================
  // C.1 — Cobertura completa de 4 roles
  // ============================================================================

  describe('C.1: Role-based rendering', () => {
    it('should render OBSERVER role with ONLY Operación Electoral section', async () => {
      createComponentWithRole('OBSERVER');

      menuServiceMock.getMyMenus.and.returnValue(of([]));

      fixture.detectChanges();

      await new Promise((resolve) => setTimeout(resolve, 0));

      const sections = component.sections();
      expect(sections.length).toBe(1);
      expect(sections[0].label).toBe('Operación Electoral');
      expect(sections[0].items.map((i) => i.route)).toEqual([
        '/observer/mis-jrvs',
        '/observer/mis-actas',
      ]);

      // Verify no Dashboard, Actas, Administración
      const labels = sections.map((s) => s.label.toLowerCase());
      expect(labels).not.toContain('principal');
      expect(labels).not.toContain('actas');
      expect(labels).not.toContain('administración');
    });

    it('should render COORDINATOR role with Principal and Actas sections', async () => {
      createComponentWithRole('COORDINATOR');

      menuServiceMock.getMyMenus.and.returnValue(of([]));

      fixture.detectChanges();

      await new Promise((resolve) => setTimeout(resolve, 0));

      const sections = component.sections();
      const labels = sections.map((s) => s.label);
      expect(labels).toEqual(['Principal', 'Actas']);

      // Verify expected routes
      const allRoutes = sections.flatMap((s) => s.items.map((i) => i.route));
      expect(allRoutes).toContain('/dashboard');
      expect(allRoutes).toContain('/coordinator/actas');
      expect(allRoutes).toContain('/coordinator/reportes');

      // Verify no Operación Electoral, no Admin, no Evidencias
      expect(labels).not.toContain('Operación Electoral');
      expect(labels).not.toContain('Administración');
      expect(labels).not.toContain('Evidencias');
    });

    it('should render ADMIN role with ONLY Principal section', async () => {
      createComponentWithRole('ADMIN');

      menuServiceMock.getMyMenus.and.returnValue(of([]));

      fixture.detectChanges();

      await new Promise((resolve) => setTimeout(resolve, 0));

      const sections = component.sections();
      expect(sections.length).toBe(1);
      expect(sections[0].label).toBe('Principal');
      expect(sections[0].items.length).toBe(3);

      const routes = sections[0].items.map((i) => i.route);
      expect(routes).toEqual([
        '/dashboard',
        '/dashboard/resultados',
        '/dashboard/progreso',
      ]);

      // Verify no operación electoral, no actas
      const labels = sections.map((s) => s.label.toLowerCase());
      expect(labels).not.toContain('operación electoral');
      expect(labels).not.toContain('actas');
    });

    it('should render MASTER role with all 5 sections consolidated', async () => {
      createComponentWithRole('MASTER');

      menuServiceMock.getMyMenus.and.returnValue(of([]));

      fixture.detectChanges();

      await new Promise((resolve) => setTimeout(resolve, 0));

      const sections = component.sections();
      const labels = sections.map((s) => s.label);
      expect(labels).toEqual([
        'Principal',
        'Actas',
        'Operación Electoral',
        'Configuración',
        'Administración',
      ]);

      // Verify critical routes present
      const allRoutes = sections.flatMap((s) => s.items.map((i) => i.route));
      expect(allRoutes).toContain('/dashboard');
      expect(allRoutes).toContain('/observer/mis-jrvs');
      expect(allRoutes).toContain('/admin/usuarios');
      expect(allRoutes).toContain('/master/configuracion');
    });
  });

  // ============================================================================
  // C.2 — Test de Cero Placeholders en el DOM
  // ============================================================================

  describe('C.2: No placeholders in DOM', () => {
    it('should NOT render any placeholder routes like /actas, /evidencias, /admin, /master/cargos', async () => {
      createComponentWithRole('MASTER');

      menuServiceMock.getMyMenus.and.returnValue(of([]));

      fixture.detectChanges();

      await new Promise((resolve) => setTimeout(resolve, 0));

      const sections = component.sections();
      const routes = sections.flatMap((s) => s.items.map((i) => i.route));

      const placeholders = [
        '/actas',
        '/evidencias',
        '/evidencias/subir',
        '/admin',
        '/master/cargos',
        '/master/partidos',
        '/master/candidatos',
      ];

      placeholders.forEach((placeholder) => {
        expect(routes).not.toContain(
          placeholder,
          `Placeholder route ${placeholder} should not be in sidebar`,
        );
      });
    });

    it('should map admin submenus (Cargos, Partidos, Candidatos) to /master/configuracion', async () => {
      createComponentWithRole('MASTER');

      menuServiceMock.getMyMenus.and.returnValue(of([]));

      fixture.detectChanges();

      await new Promise((resolve) => setTimeout(resolve, 0));

      const sections = component.sections();
      const configSection = sections.find((s) => s.label === 'Configuración');
      expect(configSection).toBeTruthy();
      expect(configSection?.items[0].route).toBe('/master/configuracion');
    });
  });

  // ============================================================================
  // C.3 — Test de Resiliencia ante error HTTP 500
  // ============================================================================

  describe('C.3: Resilience on HTTP 500 error', () => {
    it('should render NAV_CONFIG sections immediately when getMyMenus fails with HTTP 500', async () => {
      createComponentWithRole('OBSERVER');

      menuServiceMock.getMyMenus.and.returnValue(
        throwError(
          () =>
            new HttpErrorResponse({ status: 500, statusText: 'Server Error' }),
        ),
      );

      fixture.detectChanges();

      await new Promise((resolve) => setTimeout(resolve, 0));

      const sections = component.sections();
      expect(sections.length).toBe(1);
      expect(sections[0].label).toBe('Operación Electoral');
      expect(sections[0].items.length).toBe(2);
    });

    it('should maintain sidebar functionality offline without loading states', async () => {
      createComponentWithRole('COORDINATOR');

      menuServiceMock.getMyMenus.and.returnValue(
        throwError(() => new Error('Network error')),
      );

      fixture.detectChanges();

      await new Promise((resolve) => setTimeout(resolve, 0));

      const sections = component.sections();
      expect(sections.length).toBeGreaterThan(0);
      expect(sections[0].label).toBe('Principal');
    });
  });

  // ============================================================================
  // C.4 — Test de No-Duplicación en el Árbol DOM
  // ============================================================================

  describe('C.4: Zero duplicates in DOM', () => {
    it('should have ZERO duplicate routes within role sections', async () => {
      createComponentWithRole('MASTER');

      menuServiceMock.getMyMenus.and.returnValue(of([]));

      fixture.detectChanges();

      await new Promise((resolve) => setTimeout(resolve, 0));

      const sections = component.sections();
      const routes = sections.flatMap((s) => s.items.map((i) => i.route));
      const uniqueRoutes = new Set(routes);

      expect(routes.length).toBe(
        uniqueRoutes.size,
        `Duplicate routes detected: ${routes.filter((r) => routes.indexOf(r) !== routes.lastIndexOf(r)).join(', ')}`,
      );
    });

    it('should have ZERO duplicate section labels within role', async () => {
      createComponentWithRole('MASTER');

      menuServiceMock.getMyMenus.and.returnValue(of([]));

      fixture.detectChanges();

      await new Promise((resolve) => setTimeout(resolve, 0));

      const sections = component.sections();
      const labels = sections.map((s) => s.label);
      const uniqueLabels = new Set(labels);

      expect(labels.length).toBe(
        uniqueLabels.size,
        `Duplicate section labels: ${labels.filter((l) => labels.indexOf(l) !== labels.lastIndexOf(l)).join(', ')}`,
      );
    });

    it('should render "Operación Electoral" EXACTLY ONCE for OBSERVER', async () => {
      createComponentWithRole('OBSERVER');

      menuServiceMock.getMyMenus.and.returnValue(of([]));

      fixture.detectChanges();

      await new Promise((resolve) => setTimeout(resolve, 0));

      const sections = component.sections();
      const operacionCount = sections.filter(
        (s) => s.label.toLowerCase() === 'operación electoral',
      ).length;
      expect(operacionCount).toBe(1);
    });

    it('should render "/observer/mis-jrvs" EXACTLY ONCE for MASTER (not duplicated)', async () => {
      createComponentWithRole('MASTER');

      menuServiceMock.getMyMenus.and.returnValue(of([]));

      fixture.detectChanges();

      await new Promise((resolve) => setTimeout(resolve, 0));

      const sections = component.sections();
      const allItems = sections.flatMap((s) => s.items);
      const misJrvsCount = allItems.filter(
        (i) => i.route === '/observer/mis-jrvs',
      ).length;
      expect(misJrvsCount).toBe(
        1,
        'Route /observer/mis-jrvs should appear exactly once in MASTER sidebar',
      );
    });

    it('should not re-inject "Operación Electoral" if backend already provides it', async () => {
      createComponentWithRole('OBSERVER');

      const backendNodes = [
        {
          label: 'Operación Electoral',
          ruta: '/operacion',
          icono: 'grid',
          activo: true,
          children: [
            {
              label: 'Mis JRVs',
              ruta: '/observer/mis-jrvs',
              icono: 'grid',
              activo: true,
            },
          ],
        },
      ];

      menuServiceMock.getMyMenus.and.returnValue(of(backendNodes));

      fixture.detectChanges();

      await new Promise((resolve) => setTimeout(resolve, 0));

      const sections = component.sections();
      const operacionCount = sections.filter(
        (s) => s.label.toLowerCase() === 'operación electoral',
      ).length;
      expect(operacionCount).toBe(1);
    });
  });

  // ============================================================================
  // Additional Integration Tests
  // ============================================================================

  it('should initialize with immediate NAV_CONFIG before backend call completes', async () => {
    createComponentWithRole('ADMIN');

    menuServiceMock.getMyMenus.and.returnValue(of([]));

    fixture.detectChanges();

    // Sections should be set from NAV_CONFIG immediately
    let sectionsAfterInit = component.sections();
    expect(sectionsAfterInit.length).toBeGreaterThan(0);

    await new Promise((resolve) => setTimeout(resolve, 0));

    let sectionsAfterLoad = component.sections();
    expect(sectionsAfterLoad[0].label).toBe('Principal');
  });

  it('should have all items properly typed with icon and label', async () => {
    createComponentWithRole('COORDINATOR');

    menuServiceMock.getMyMenus.and.returnValue(of([]));

    fixture.detectChanges();

    await new Promise((resolve) => setTimeout(resolve, 0));

    const sections = component.sections();
    for (const section of sections) {
      expect(section.label).toBeTruthy();
      for (const item of section.items) {
        expect(item.label).toBeTruthy();
        expect(item.icon).toBeTruthy();
        expect(item.route).toBeTruthy();
      }
    }
  });
});
