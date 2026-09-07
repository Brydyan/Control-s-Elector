# Proposal — T-IN-06: Supabase Realtime

## Qué se construye
Habilitar WebSocket Realtime en Supabase. Dashboard recibe cambios en actas al instante sin refrescar.

## Por qué
- Coordinator ve nuevas actas/cambios estado sin polling
- Dashboard actualiza en vivo
- Eficiente (WebSocket vs HTTP polling cada 5s)

## Alcance

| Elemento | Incluye | No incluye |
|----------|---------|-----------|
| Realtime | habilitar en BD, listar eventos | custom subscriptions |
| Tabla "acta" | suscribirse a INSERT/UPDATE | DELETE (no aplica) |
| Tabla "notificacion" | suscribirse a cambios | triggers complejos |
| Cliente | @supabase/supabase-js + .on() | reconnect logic |

## Criterio de aceptación

- [ ] Realtime habilitado en Supabase dashboard
- [ ] Cliente Angular suscribirse a tabla "acta" con .on('*')
- [ ] Cambio en acta (INSERT/UPDATE) → evento llega a cliente
- [ ] Dashboard chart se actualiza sin refrescar
- [ ] Test: dos navegadores, registrar acta en uno, ver actualizar en otro

## Fuera de alcance

- Reconnect automático (SDK lo hace)
- Message queuing si se pierde conexión
- Presencia de usuarios (quién está mirando qué)

## Dependencias

- T-IN-05 (Supabase proyecto listo)
- T-FE-11 (Dashboard que recibe eventos)

## Decisiones a tomar

**D1 — Qué eventos escuchar:** INSERT + UPDATE (no DELETE, actas nunca se borran).

**D2 — A qué tabla:** tabla "acta" + tabla "notificacion".
