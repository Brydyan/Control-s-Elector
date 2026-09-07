# Design — T-IN-06: Supabase Realtime

## D1 — Qué eventos escuchar

**Decisión:** INSERT + UPDATE. No DELETE (actas immutable, solo estado cambia).

**Alternativa descartada — escuchar todo.** Ruido innecesario. Solo cambios relevantes.

## D2 — A qué tablas

**Decisión:** "acta" + "notificacion" (cambios que el dashboard debe reflejar).

**Alternativa descartada — todas las tablas.** Tablas de config (cargo, partido, etc.) raramente cambian.

## D3 — Manejo de desconexión

**Decisión:** SDK maneja reconnect automático (está built-in en @supabase/supabase-js).

**Alternativa descartada — custom reconnect logic.** Overkill, SDK es suficiente.

## D4 — Filtro de eventos por rol

**Decisión:** No filtrar en Realtime. Cliente suscribirse a todo, backend valida con RLS.

**Alternativa descartada — subscripción con política.** Más complejo, RLS + cliente juntos es mejor.
