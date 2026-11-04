import { TestBed } from '@angular/core/testing';
import {
  DashboardRealtimeService,
  SUPABASE_REALTIME_CLIENT,
  SupabaseChannel,
  SupabaseRealtimeClient,
} from './dashboard-realtime.api';

/**
 * Tests del servicio de invalidación reactiva (T-FE-11, C.1, V.1).
 *
 * V.1: si se quita `debounceTime`, una ráfaga de 10 eventos dispara 10
 * invocaciones de `next`. Este test captura el comportamiento agrupado.
 *
 * Nota: usamos `setTimeout` real en lugar de `fakeAsync + tick` porque
 * vitest+jsdom no provee `ProxyZone` para `zone-testing` (ver apply-progress
 * de T-FE-07 / T-FE-08). El debounce de 500ms se cubre con un margen.
 */
describe('DashboardRealtimeService (T-FE-11 C.1)', () => {
  let service: DashboardRealtimeService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [DashboardRealtimeService],
    });
    service = TestBed.inject(DashboardRealtimeService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should debounce burst of events into a single reload (V.1)', async () => {
    let received = 0;
    const sub = service._subscribeReload(() => received++);

    // Ráfaga de 10 eventos en 100 ms.
    for (let i = 0; i < 10; i++) {
      service.pushEvent('INSERT');
    }
    // Esperamos > 500ms para que el debounce dispare.
    await new Promise((resolve) => setTimeout(resolve, 700));

    expect(received).toBe(1);
    sub.unsubscribe();
  });

  it('should allow DELETE events through (sin filtrar)', async () => {
    const seen: string[] = [];
    const sub = service._subscribeReload((e) => seen.push(e));

    service.pushEvent('DELETE');
    await new Promise((resolve) => setTimeout(resolve, 700));

    expect(seen).toEqual(['DELETE']);
    sub.unsubscribe();
  });

  it('should emit two reloads when bursts are 600ms apart', async () => {
    let count = 0;
    const sub = service._subscribeReload(() => count++);

    service.pushEvent('INSERT');
    await new Promise((resolve) => setTimeout(resolve, 700));
    expect(count).toBe(1);

    service.pushEvent('UPDATE');
    await new Promise((resolve) => setTimeout(resolve, 700));
    expect(count).toBe(2);

    sub.unsubscribe();
  });

  it('should subscribe to Supabase channel when client is injected', () => {
    const channel = {
      on: vi.fn().mockReturnThis(),
      subscribe: vi.fn().mockReturnThis(),
      unsubscribe: vi.fn().mockResolvedValue(undefined),
    };
    const client: SupabaseRealtimeClient = {
      channel: vi.fn().mockReturnValue(channel as unknown as SupabaseChannel),
      removeChannel: vi.fn().mockResolvedValue(undefined),
    };
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        DashboardRealtimeService,
        { provide: SUPABASE_REALTIME_CLIENT, useValue: client },
      ],
    });
    const svc = TestBed.inject(DashboardRealtimeService);
    svc.start();

    expect(client.channel).toHaveBeenCalledWith('acta-realtime');
    expect(channel.on).toHaveBeenCalledWith(
      'postgres_changes',
      { event: 'INSERT', schema: 'public', table: 'acta' },
      expect.any(Function),
    );
    expect(channel.on).toHaveBeenCalledWith(
      'postgres_changes',
      { event: 'UPDATE', schema: 'public', table: 'acta' },
      expect.any(Function),
    );
    expect(channel.subscribe).toHaveBeenCalled();
  });

  it('should not throw when no Supabase client is injected (test mode)', () => {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [DashboardRealtimeService],
    });
    const svc = TestBed.inject(DashboardRealtimeService);
    expect(() => svc.start()).not.toThrow();
  });

  it('should remove channel on ngOnDestroy', () => {
    const channel = {
      on: vi.fn().mockReturnThis(),
      subscribe: vi.fn().mockReturnThis(),
      unsubscribe: vi.fn().mockResolvedValue(undefined),
    };
    const client: SupabaseRealtimeClient = {
      channel: vi.fn().mockReturnValue(channel as unknown as SupabaseChannel),
      removeChannel: vi.fn().mockResolvedValue(undefined),
    };
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        DashboardRealtimeService,
        { provide: SUPABASE_REALTIME_CLIENT, useValue: client },
      ],
    });
    const svc = TestBed.inject(DashboardRealtimeService);
    svc.start();
    svc.ngOnDestroy();

    expect(client.removeChannel).toHaveBeenCalledWith(channel);
  });
});
