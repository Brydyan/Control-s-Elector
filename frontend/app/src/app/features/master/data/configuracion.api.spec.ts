import { TestBed } from '@angular/core/testing';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { firstValueFrom } from 'rxjs';

import { ConfiguracionApi } from './configuracion.api';
import { ObserverApi } from '../../observer/data/observer.api';
import { DashboardApi } from '../../dashboard/data/dashboard.api';
import { environment } from '../../../../environments/environment';

/**
 * T-CACHE-STRAT-01: la invalidacion del frontend esta cableada.
 *
 * <p>Un MASTER que crea un partido lo cambia en el backend, pero el formulario
 * del observador tiene los partidos cacheados en memoria (`ObserverApi`) y el
 * dashboard tiene sus cuatro caches (`DashboardApi`). Si la accion que rompe el
 * dato no invalida esas caches, el dato viejo sigue en pantalla hasta que se
 * recarga la pagina: la invalidacion existia, pero nadie la llamaba.
 */
describe('ConfiguracionApi — invalidacion de las caches de otros servicios', () => {
  let api: ConfiguracionApi;
  let observerApi: ObserverApi;
  let dashboardApi: DashboardApi;
  let httpMock: HttpTestingController;

  const baseUrl = `${environment.apiUrl}/configuracion`;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        ConfiguracionApi,
        ObserverApi,
        DashboardApi,
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
    api = TestBed.inject(ConfiguracionApi);
    observerApi = TestBed.inject(ObserverApi);
    dashboardApi = TestBed.inject(DashboardApi);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('invalida la cache de partidos del observador al invalidar partidos', async () => {
    // 1) El observador pide los partidos: quedan cacheados.
    const partidos = [{ id: 1, nombre: 'MC', siglas: 'MC', numeroLista: 5 }];
    const peticion = firstValueFrom(observerApi.getCandidatosPorCargo(1));
    httpMock.expectOne(`${baseUrl}/partidos`).flush(partidos as never);
    httpMock
      .expectOne((req) => req.url.includes('/candidatos'))
      .flush([] as never);
    await peticion;

    // 2) Sin invalidar, la segunda llamada NO vuelve a pedir partidos.
    const segunda = firstValueFrom(observerApi.getCandidatosPorCargo(2));
    httpMock
      .expectOne((req) => req.url.includes('/candidatos'))
      .flush([] as never);
    await segunda;
    // Sin peticion a /partidos: si saliera, `httpMock.verify()` fallaria.

    // 3) El MASTER invalida la cache de partidos.
    api.invalidateCache('partidos');

    // 4) Ahora la siguiente llamada SI vuelve a pedir los partidos.
    const tercera = firstValueFrom(observerApi.getCandidatosPorCargo(3));
    httpMock
      .expectOne((req) => req.url.includes('/candidatos'))
      .flush([] as never);
    httpMock.expectOne(`${baseUrl}/partidos`).flush(partidos as never);
    await tercera;
  });

  it('invalida las caches del dashboard al cambiar la configuracion', async () => {
    // 1) El dashboard carga su resumen: queda cacheado.
    const primera = firstValueFrom(dashboardApi.getResumen());
    httpMock
      .expectOne(`${environment.apiUrl}/dashboard/resumen`)
      .flush({ enviadas: 1 } as never);
    await primera;

    // 2) Sin invalidar, la segunda llamada no vuelve a pedirlo.
    const segunda = firstValueFrom(dashboardApi.getResumen());
    await segunda;

    // 3) El MASTER invalida la configuracion.
    api.invalidateCache('candidatos');

    // 4) Ahora si vuelve a pedirlo.
    const tercera = firstValueFrom(dashboardApi.getResumen());
    httpMock
      .expectOne(`${environment.apiUrl}/dashboard/resumen`)
      .flush({ enviadas: 2 } as never);
    await tercera;
  });
});
