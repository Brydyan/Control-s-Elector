import {
  HttpClientTestingModule,
  HttpTestingController,
} from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { environment } from '../../../environments/environment';
import { MenuService } from './menu.service';

describe('MenuService', () => {
  let service: MenuService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [MenuService],
    });
    service = TestBed.inject(MenuService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created and have empty signals initially', () => {
    expect(service).toBeTruthy();
    expect(service.menus()).toEqual([]);
    expect(service.loading()).toBe(false);
    expect(service.error()).toBeNull();
  });

  it('should load menus into signal state via loadMenus', () => {
    const mockMenus = [
      {
        id: '1',
        parentId: null,
        codigo: 'dashboard',
        label: 'Dashboard',
        icono: 'home',
        ruta: '/dashboard',
        orden: 1,
        activo: true,
        children: [],
      },
    ];

    service.loadMenus();
    expect(service.loading()).toBe(true);

    const req = httpMock.expectOne(`${environment.apiUrl}/menus/me`);
    expect(req.request.method).toBe('GET');
    req.flush({ menus: mockMenus });

    expect(service.menus()).toEqual(mockMenus);
    expect(service.loading()).toBe(false);
    expect(service.error()).toBeNull();
  });
});
