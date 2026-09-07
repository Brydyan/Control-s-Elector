# Design — T-FE-09: Resiliencia Offline con IndexedDB

## D1 — Dónde chequea offline

**Decisión:** ActaService. Método `saveOffline(acta)` en lugar de POST directo.

**Alternativa descartada — en interceptor.** Interceptor es para todos los requests. ActaService es específico para actas.

## D2 — Qué guardar en IDB

**Decisión:** Payload completo (candidatos, votos) + referencia a imagen (si la hay). Imagen en Filesystem por tamaño.

**Alternativa descartada — guardar Blob en IDB.** IndexedDB puede, pero lento. Filesystem es más eficiente.

## D3 — Polling cada 30s

**Decisión:** setInterval(30000). Mientras offline, reintenta cada 30s.

**Alternativa descartada — WebSocket realtime.** Más complejo. Polling es suficiente para MVP.

## D4 — Schema IDB

**Decisión:** Table `pending_actas`:
- id (key)
- payload (JSON string)
- imageRef (archivo path en Filesystem)
- timestamp (cuándo se guardó)
- retries (contador)

**Alternativa descartada — guardar por acta.id.** Acta no tiene ID hasta que se guarda en servidor. Usar UUID local temporal.
