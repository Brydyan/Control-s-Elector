import { Injectable, InjectionToken, OnDestroy, inject } from '@angular/core';
import { Observable, Subject, Subscription, debounceTime, share } from 'rxjs';

/**
 * Shape mínima del cliente Supabase Realtime que necesitamos. Definida
 * aquí para que el servicio sea testeable sin import de @supabase/supabase-js
 * (que arrastra `ws` y globals de Node incompatibles con jsdom).
 *
 * En producción se inyecta el cliente real vía `SUPABASE_REALTIME_CLIENT`.
 * En tests se inyecta un stub que implementa sólo `.channel()` y `.subscribe()`.
 */
export interface SupabaseChannel {
  on(
    event: 'postgres_changes',
    filter: { event: string; schema: string; table: string },
    callback: (payload: unknown) => void,
  ): SupabaseChannel;
  subscribe(): SupabaseChannel;
  unsubscribe(): Promise<void>;
}

export interface SupabaseRealtimeClient {
  channel(name: string): SupabaseChannel;
  removeChannel(channel: SupabaseChannel): Promise<void>;
}

/** Token para inyectar el cliente Supabase (real o stub en tests). */
export const SUPABASE_REALTIME_CLIENT =
  new InjectionToken<SupabaseRealtimeClient | null>(
    'SUPABASE_REALTIME_CLIENT',
    { providedIn: 'root', factory: () => null },
  );

/** Evento bruto que Supabase entrega. Lo abstraemos para testear sin él. */
export type ActaRealtimeEvent = 'INSERT' | 'UPDATE' | 'DELETE';

/**
 * T-FE-11 C.1: servicio de invalidación reactiva del dashboard.
 *
 * Patrón:
 *   1. Subject<ActaRealtimeEvent> expuesto al componente (`events$`).
 *   2. Subject conectado vía `debounceTime(500)` que dispara la señal `reload`
 *     al consumirlo.
 *
 * Decisión: NO se usa el `RealtimeChannel` directamente en el servicio. Se
 * acepta una `start(channel)` injection para que el wiring con Supabase
 * real viva en el componente o en un factory. Esto reduce acoplamiento y
 * permite testear el debounce sin Supabase.
 */
@Injectable({ providedIn: 'root' })
export class DashboardRealtimeService implements OnDestroy {
  /** Cada cambio de acta (INSERT/UPDATE/DELETE) llega por aquí. */
  private readonly raw$ = new Subject<ActaRealtimeEvent>();

  /** Subject con debounce — usado por el componente para invalidar caché. */
  readonly reload$: Observable<ActaRealtimeEvent>;

  private readonly channelRef: { current: SupabaseChannel | null } = {
    current: null,
  };
  private readonly client = inject(SUPABASE_REALTIME_CLIENT, {
    optional: true,
  });

  constructor() {
    this.reload$ = this.raw$.pipe(debounceTime(500), share());
  }

  /**
   * Empuja un evento crudo (sin debounce). Tests lo usan para simular
   * ráfagas de Supabase sin levantar el cliente real.
   */
  pushEvent(event: ActaRealtimeEvent): void {
    this.raw$.next(event);
  }

  /**
   * Suscribe al canal Supabase `acta` filtrando INSERT y UPDATE
   * (D2 — DELETE ignorado, actas inmutables).
   */
  start(): void {
    if (!this.client) {
      // Sin cliente Supabase inyectado, el servicio opera solo con pushEvent().
      // Útil en tests y durante SSR/Node.
      return;
    }
    const channel = this.client.channel('acta-realtime');
    channel.on(
      'postgres_changes',
      { event: 'INSERT', schema: 'public', table: 'acta' },
      () => this.pushEvent('INSERT'),
    );
    channel.on(
      'postgres_changes',
      { event: 'UPDATE', schema: 'public', table: 'acta' },
      () => this.pushEvent('UPDATE'),
    );
    channel.subscribe();
    this.channelRef.current = channel;
  }

  /**
   * T-FE-11 C.2: cleanup explícito. Elimina el canal Supabase y completa
   * los Subjects para evitar fugas.
   */
  ngOnDestroy(): void {
    if (this.channelRef.current && this.client) {
      void this.client.removeChannel(this.channelRef.current);
      this.channelRef.current = null;
    }
    this.raw$.complete();
  }

  /**
   * Hook de cleanup invocable manualmente si el componente quiere destruir
   * el servicio antes de que Angular llame a ngOnDestroy.
   */
  stop(): void {
    this.ngOnDestroy();
  }

  /** Para tests: suscripción al reload$ con manejo manual de unsubscribe. */
  _subscribeReload(next: (e: ActaRealtimeEvent) => void): Subscription {
    return this.reload$.subscribe(next);
  }
}
