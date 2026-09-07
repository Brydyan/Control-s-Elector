# Design — T-IN-12: Tailscale Staging

## D1 — Admin console

**Decisión:** Usar Tailscale web dashboard (login.tailscale.com).

**Alternativa descartada — CLI only.** Dashboard es más user-friendly.

## D2 — Auth method

**Decisión:** GitHub OAuth (no password, social login).

**Alternativa descartada — email/pass.** GitHub OAuth es más seguro.

## D3 — ACLs (Access Control Lists)

**Decisión:** Allow all devices en red (todos pueden hablar entre sí).

**Alternativa descartada — granular ACLs.** Para MVP, todos en equipo = todos pueden acceder.

## D4 — Subnet routes (si hay que acceder a BD privada)

**Decisión:** No necesario aún. Backend (T-IN-01) expone solo aplicación, no BD.

**Alternativa descartada — compartir subnet DB.** Más complejo, no requerido.
