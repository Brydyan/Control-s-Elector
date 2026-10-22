import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { RouterTestingModule } from '@angular/router/testing';
import { of } from 'rxjs';
import { AuthService } from '../../core/auth/services/auth.service';
import { AuthStore } from '../../core/auth/auth.store';
import { MenuService } from '../../core/menu/menu.service';
import { SidebarComponent } from './sidebar.component';

describe('SidebarComponent', () => {
  let component: SidebarComponent;
  let fixture: ComponentFixture<SidebarComponent>;
  let menuServiceSpy: jasmine.SpyObj<MenuService>;
  let authServiceSpy: jasmine.SpyObj<AuthService>;
  let store: AuthStore;

  beforeEach(async () => {
    menuServiceSpy = jasmine.createSpyObj('MenuService', ['getMyMenus']);
    authServiceSpy = jasmine.createSpyObj('AuthService', ['logout']);

    menuServiceSpy.getMyMenus.and.returnValue(
      of([
        {
          id: '1',
          parentId: null,
          codigo: 'dashboard',
          label: 'Dashboard',
          icono: 'home',
          ruta: '/home',
          orden: 1,
          activo: true,
          children: [],
        },
        {
          id: '2',
          parentId: null,
          codigo: 'actas',
          label: 'Actas',
          icono: 'document',
          ruta: '/actas',
          orden: 2,
          activo: true,
          children: [
            {
              id: '3',
              parentId: '2',
              codigo: 'actas.registrar',
              label: 'Registrar acta',
              icono: 'add-circle',
              ruta: '/actas/nueva',
              orden: 1,
              activo: true,
              children: [],
            },
          ],
        },
      ]),
    );

    await TestBed.configureTestingModule({
      imports: [SidebarComponent, RouterTestingModule],
      providers: [
        AuthStore,
        { provide: MenuService, useValue: menuServiceSpy },
        { provide: AuthService, useValue: authServiceSpy },
      ],
    }).compileComponents();

    store = TestBed.inject(AuthStore);
    store.setSession('token', {
      id: 'uuid-1',
      email: 'juan.perez@cne.gob.ec',
      role: 'OBSERVER',
      permissions: ['acta:read'],
    });

    fixture = TestBed.createComponent(SidebarComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create and load dynamic menu sections', () => {
    expect(component).toBeTruthy();
    expect(component.sections().length).toBeGreaterThan(0);
  });

  it('should map user display name and initials', () => {
    expect(component.displayName()).toBe('Juan Perez');
    expect(component.initials()).toBe('JP');
    expect(component.roleLabel()).toBe('OBSERVER');
  });

  it('should not display Operacion Electoral for ADMIN role', () => {
    store.setSession('token-admin', {
      id: 'uuid-admin',
      email: 'admin@cne.gob.ec',
      role: 'ADMIN',
      permissions: ['dashboard:read'],
    });

    component.cargarMenus();
    const hasOperacion = component
      .sections()
      .some((s) => s.label === 'Operación Electoral');
    expect(hasOperacion).toBe(false);
  });

  it('should render the shell sidebar with a link per menu item', () => {
    const aside = fixture.nativeElement.querySelector('aside.layout-sidebar');
    expect(aside).toBeTruthy();

    const links = fixture.nativeElement.querySelectorAll(
      'aside.layout-sidebar a',
    );
    expect(links.length).toBeGreaterThan(0);
  });

  it('should map the catalog icon keys to PrimeIcons', () => {
    const icons = component
      .sections()
      .flatMap((section) => section.items)
      .map((item) => item.icon);

    expect(icons.every((icon) => icon.startsWith('pi pi-'))).toBe(true);
    expect(icons).toContain('pi pi-home');
    expect(icons).toContain('pi pi-plus-circle');
  });

  /*
   * S11: the sidebar used to carry its own switch of eleven backend keys and
   * the app carried a second table of sixty Ionic names. There is one table
   * now, keyed by what the API sends.
   */
  it('should resolve the icons of the backend catalog through the one table', () => {
    const byLabel = new Map(
      component
        .sections()
        .flatMap((section) => section.items)
        .map((item) => [item.label, item.icon]),
    );

    expect(byLabel.get('Dashboard')).toBe('pi pi-home');
    expect(byLabel.get('Registrar acta')).toBe('pi pi-plus-circle');
  });
});
