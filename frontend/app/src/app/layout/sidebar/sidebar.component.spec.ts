import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { RouterTestingModule } from '@angular/router/testing';
import { of } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
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
});
