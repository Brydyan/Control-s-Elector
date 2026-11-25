import { ComponentFixture, TestBed } from '@angular/core/testing';
import { SimpleChange } from '@angular/core';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { GeografiaApi, GeografiaTreeDto } from '../../data/geografia.api';
import { RecintoApi, RecintoResponse } from '../../data/recinto.api';
import { RecintoJrvsModalComponent } from './recinto-jrvs.modal';

describe('RecintoJrvsModalComponent', () => {
  let component: RecintoJrvsModalComponent;
  let fixture: ComponentFixture<RecintoJrvsModalComponent>;
  let recintoApi: {
    generarJrvsPorRango: ReturnType<typeof vi.fn>;
    asignarJrvsAlRecinto: ReturnType<typeof vi.fn>;
  };
  let geografiaApi: {
    obtenerGeografia: ReturnType<typeof vi.fn>;
    eliminarJrv: ReturnType<typeof vi.fn>;
    eliminarJrvsBatch: ReturnType<typeof vi.fn>;
  };

  const recinto: RecintoResponse = {
    id: 5,
    nombre: 'Escuela Central',
    parroquiaId: 100,
    estado: 'ACTIVO',
  };

  const geografia: GeografiaTreeDto = {
    provincias: [
      {
        id: 1,
        nombre: 'Santa Elena',
        cantones: [
          {
            id: 10,
            nombre: 'La Libertad',
            parroquias: [
              {
                id: 100,
                nombre: 'Centro',
                esUrbana: true,
                jrvs: [
                  {
                    id: 51,
                    numero: 'JRV-SE-051',
                    recinto: 'Otro recinto',
                    totalElectores: 300,
                    recintoId: null,
                  },
                  {
                    id: 52,
                    numero: 'JRV-SE-052',
                    recinto: 'Escuela Central',
                    totalElectores: 350,
                    recintoId: 5,
                  },
                  {
                    id: 53,
                    numero: 'JRV-SE-053',
                    recinto: 'Otro recinto',
                    totalElectores: 200,
                    recintoId: 8,
                  },
                ],
              },
            ],
          },
        ],
      },
    ],
  } as GeografiaTreeDto;

  beforeEach(async () => {
    recintoApi = {
      generarJrvsPorRango: vi.fn().mockReturnValue(of([])),
      asignarJrvsAlRecinto: vi.fn().mockReturnValue(of(undefined)),
    };
    geografiaApi = {
      obtenerGeografia: vi.fn().mockReturnValue(of(geografia)),
      eliminarJrv: vi.fn().mockReturnValue(of(undefined)),
      eliminarJrvsBatch: vi.fn().mockReturnValue(of(undefined)),
    };

    await TestBed.configureTestingModule({
      imports: [RecintoJrvsModalComponent],
      providers: [
        { provide: RecintoApi, useValue: recintoApi },
        { provide: GeografiaApi, useValue: geografiaApi },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(RecintoJrvsModalComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
    component.abierto = true;
    component.recinto = recinto;
    component.ngOnChanges({
      abierto: new SimpleChange(false, true, false),
      recinto: new SimpleChange(null, recinto, true),
    });
    fixture.detectChanges();
  });

  it('carga las JRVs sin asignar y las asignadas al recinto actual', () => {
    expect(geografiaApi.obtenerGeografia).toHaveBeenCalled();
    expect(component.jrvs.map((jrv) => jrv.id)).toEqual([51, 52]);
    expect(component.jrvs[1].asignada).toBe(true);
  });

  it('calcula automáticamente el siguiente número disponible al cargar o cambiar sufijo', () => {
    // Al cargar con sufijo vacio o SE (jrv 51, 52), el maximo es 52 -> siguiente 53
    expect(component.generacion.desde).toBe(53);
    expect(component.generacion.hasta).toBe(53);
  });

  it('genera JRVs por rango y las agrega a la tabla', () => {
    const generadas = [
      {
        id: 53,
        numero: 'JRV-SE-053',
        recinto: recinto.nombre,
        totalElectores: 400,
        parroquiaId: recinto.parroquiaId,
        recintoId: recinto.id,
      },
    ];
    recintoApi.generarJrvsPorRango.mockReturnValue(of(generadas));
    component.generacion = {
      desde: 53,
      hasta: 53,
      sufijoGenero: 'SE',
      electoresPorMesa: 400,
    };

    component.generarJrvs();

    expect(recintoApi.generarJrvsPorRango).toHaveBeenCalledWith(5, {
      desde: 53,
      hasta: 53,
      sufijoGenero: 'SE',
      electoresPorMesa: 400,
    });
    expect(component.jrvs.some((jrv) => jrv.id === 53)).toBe(true);
  });

  it('asigna únicamente las JRVs seleccionadas', () => {
    component.jrvsSeleccionadas = [component.jrvs[0]];

    component.guardar();

    expect(recintoApi.asignarJrvsAlRecinto).toHaveBeenCalledWith(5, [51]);
  });

  it('elimina una JRV individual confirmada con el dialog compartido', () => {
    const jrvAEliminar = component.jrvs[0];
    const openSpy = vi.spyOn(component.confirmDialog!, 'open');

    component.eliminarJrv(jrvAEliminar);

    expect(openSpy).toHaveBeenCalledWith(
      expect.objectContaining({
        title: 'Eliminar JRV',
        variant: 'danger',
      }),
    );

    // Simula la confirmación del diálogo
    component.ejecutarEliminacionConfirmada();

    expect(geografiaApi.eliminarJrv).toHaveBeenCalledWith(jrvAEliminar.id);
    expect(component.jrvs.some((j) => j.id === jrvAEliminar.id)).toBe(false);
  });

  it('elimina un lote de JRVs seleccionadas confirmadas con el dialog compartido', () => {
    component.jrvsSeleccionadas = [component.jrvs[0]];
    const openSpy = vi.spyOn(component.confirmDialog!, 'open');

    component.eliminarSeleccionadas();

    expect(openSpy).toHaveBeenCalledWith(
      expect.objectContaining({
        title: 'Eliminar JRVs seleccionadas',
        variant: 'danger',
      }),
    );

    // Simula la confirmación del diálogo
    component.ejecutarEliminacionConfirmada();

    expect(geografiaApi.eliminarJrvsBatch).toHaveBeenCalledWith([51]);
    expect(component.jrvs.some((j) => j.id === 51)).toBe(false);
    expect(component.jrvsSeleccionadas.length).toBe(0);
  });
});
