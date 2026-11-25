import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { DialogService } from 'primeng/dynamicdialog';

import { ToastService } from '../../../../core/services/toast.service';
import { GeografiaApi } from '../../data/geografia.api';
import { GeografiaTreeDto } from '../../domain/models/geografia.model';
import { ConfiguracionGeograficaPage } from './configuracion-geografica.page';

/**
 * La pagina de configuracion geografica.
 *
 * <p>Lo que se prueba aqui es la logica de la pantalla: armar el arbol desde la respuesta del
 * backend, expandir y colapsar, el menu contextual segun el nivel del nodo, y que cada alta
 * abra el modal del nivel correcto con el padre del que salio.
 */
describe('ConfiguracionGeograficaPage', () => {
  let component: ConfiguracionGeograficaPage;
  let api: {
    obtenerArbol: ReturnType<typeof vi.fn>;
    eliminarProvincia: ReturnType<typeof vi.fn>;
    eliminarCanton: ReturnType<typeof vi.fn>;
    eliminarParroquia: ReturnType<typeof vi.fn>;
  };
  let dialogService: { open: ReturnType<typeof vi.fn> };
  let toast: {
    success: ReturnType<typeof vi.fn>;
    error: ReturnType<typeof vi.fn>;
  };

  const arbol: GeografiaTreeDto = {
    provincias: [
      {
        id: 1,
        nombre: 'Santa Elena',
        codigo: '24',
        cantones: [
          {
            id: 10,
            nombre: 'Santa Elena',
            codigo: '2401',
            parroquias: [
              {
                id: 100,
                nombre: 'Santa Elena',
                esUrbana: true,
                jrvs: [
                  {
                    id: 1001,
                    numero: 'JRV-SE-001',
                    recinto: 'Escuela Abdón Calderón',
                    totalElectores: 320,
                    recintoId: null,
                  },
                ],
              },
            ],
          },
        ],
      },
    ],
  };

  beforeEach(() => {
    api = {
      obtenerArbol: vi.fn().mockReturnValue(of(arbol)),
      eliminarProvincia: vi.fn().mockReturnValue(of(undefined)),
      eliminarCanton: vi.fn().mockReturnValue(of(undefined)),
      eliminarParroquia: vi.fn().mockReturnValue(of(undefined)),
    };
    // El dialogo simulado cierra con `true`: es lo que el dialogo de confirmacion devuelve
    // cuando el operador acepta, y lo que el modal de alta devuelve cuando guardo.
    dialogService = {
      open: vi.fn().mockImplementation(() => ({
        onClose: { subscribe: (fn: (valor: unknown) => void) => fn(true) },
      })),
    };
    toast = { success: vi.fn(), error: vi.fn() };

    TestBed.configureTestingModule({
      providers: [
        ConfiguracionGeograficaPage,
        { provide: GeografiaApi, useValue: api },
        { provide: DialogService, useValue: dialogService },
        { provide: ToastService, useValue: toast },
      ],
    });

    component = TestBed.inject(ConfiguracionGeograficaPage);
  });

  it('carga el arbol completo al arrancar', () => {
    component.ngOnInit();

    expect(api.obtenerArbol).toHaveBeenCalled();
    expect(component.loading()).toBe(false);
    expect(component.error()).toBeNull();
    expect(component.nodos().length).toBe(1);
    expect(component.nodos()[0].label).toBe('Santa Elena');
  });

  it('arma provincia, canton y parroquia, dejando la parroquia como hoja', () => {
    component.ngOnInit();

    const provincia = component.nodos()[0];
    const canton = provincia.children?.[0];
    const parroquia = canton?.children?.[0];

    expect(canton?.label).toBe('Santa Elena');
    expect(parroquia?.label).toBe('Santa Elena');
    expect(parroquia?.children).toBeUndefined();
  });

  it('el nodo lleva el resumen de sus hijos', () => {
    component.ngOnInit();

    const provincia = component.nodos()[0];
    const canton = provincia.children?.[0];
    const parroquia = canton?.children?.[0];

    expect((provincia.data as any).meta).toBe('1 canton');
    expect((canton?.data as any).meta).toBe('1 parroquia');
    expect((parroquia?.data as any).meta).toBe('urbana');
  });

  it('expande y colapsa todo el arbol', () => {
    component.ngOnInit();

    component.expandirTodo();
    expect(component.nodos()[0].expanded).toBe(true);
    expect(component.nodos()[0].children?.[0].expanded).toBe(true);
    expect(component.nodos()[0].children?.[0].children?.[0].expanded).toBe(
      false,
    );
    expect(
      component.nodos()[0].children?.[0].children?.[0].children,
    ).toBeUndefined();

    component.colapsarTodo();
    expect(component.nodos()[0].expanded).toBe(false);
    expect(component.nodos()[0].children?.[0].expanded).toBe(false);
  });

  it('el menu contextual ofrece editar y eliminar en la parroquia hoja', () => {
    component.ngOnInit();

    const canton = component.nodos()[0].children?.[0];
    component.nodoContexto.set(canton);
    expect(component.menuItems().map((i) => i.label)).toEqual([
      'Editar',
      'Eliminar',
    ]);

    const parroquia = component.nodos()[0].children?.[0].children?.[0];
    component.nodoContexto.set(parroquia);
    expect(component.menuItems().map((i) => i.label)).toEqual([
      'Editar',
      'Eliminar',
    ]);
  });

  it('el alta de cada nivel abre el modal con el padre del nodo', () => {
    component.ngOnInit();

    const provincia = component.nodos()[0].data as any;
    component.crearCanton(provincia);
    expect(dialogService.open).toHaveBeenCalledTimes(1);
    expect(dialogService.open.mock.calls[0][1].data).toMatchObject({
      mode: 'create',
      provinciaId: 1,
    });

    const canton = component.nodos()[0].children?.[0].data as any;
    component.crearParroquia(canton);
    expect(dialogService.open.mock.calls[1][1].data).toMatchObject({
      mode: 'create',
      cantonId: 10,
    });

    const parroquia = component.nodos()[0].children?.[0].children?.[0]
      .data as any;
    expect(dialogService.open).toHaveBeenCalledTimes(2);
  });

  it('la edicion manda el padre del nodo, que el backend exige en el PUT', () => {
    component.ngOnInit();

    const parroquia = component.nodos()[0].children?.[0].children?.[0]
      .data as any;
    component.editar(parroquia);

    expect(dialogService.open.mock.calls[0][1].data).toMatchObject({
      mode: 'edit',
      cantonId: 10,
    });
  });

  it('el error de carga deja la pantalla en estado de error con reintento', () => {
    api.obtenerArbol.mockReturnValue(
      throwError(() => ({ error: { message: 'Se cayo la base' } })),
    );

    component.ngOnInit();

    expect(component.loading()).toBe(false);
    expect(component.error()).toBe('Se cayo la base');
  });

  it('el 409 al borrar avisa con el motivo del backend', () => {
    api.eliminarCanton.mockReturnValue(
      throwError(() => ({
        status: 409,
        error: {
          message: 'No se puede eliminar el canton: tiene 2 parroquias',
        },
      })),
    );
    component.ngOnInit();

    component.eliminar(component.nodos()[0].children?.[0].data as any);

    expect(toast.error).toHaveBeenCalledWith(
      'No se pudo eliminar',
      'No se puede eliminar el canton: tiene 2 parroquias',
    );
  });
});
