# Tasks — T-FE-09: Resiliencia Offline con IndexedDB

## Fase A: IndexedDB Setup

- [ ] **A.1** — Instalar librería `idb` (si no está):
  ```bash
  npm install idb
  ```

- [ ] **A.2** — Crear `core/services/offline.service.ts`.
  - Método: `openDB()` → abre DB 'Electoral'
  - Crea table 'pending_actas' con schema:
    - `id: string (key)`
    - `payload: string (JSON)`
    - `imageRef: string`
    - `timestamp: number`
    - `retries: number`

- [ ] **A.3** — Métodos en OfflineService:
  - `addPending(acta)` → INSERT en IDB
  - `getPending()` → SELECT * from pending_actas
  - `removePending(id)` → DELETE

**Compuerta A:** `ng build`. OfflineService funciona.

## Fase B: ActaService integración

- [ ] **B.1** — Modificar ActaService (o crear ActaRepository):
  - Inyectar: HttpClient, OfflineService
  - Método: `saveActa(payload): Observable`:
    - chequea navigator.onLine
    - Si online: POST /api/observer/actas
    - Si offline: guardaOffline() → emitir Observable sin error

- [ ] **B.2** — Método `saveActaOffline(payload)`:
  ```typescript
  saveActaOffline(payload) {
    const id = generateUUID();
    this.offlineService.addPending({
      id,
      payload: JSON.stringify(payload),
      timestamp: Date.now(),
      retries: 0
    });
    return of({ id, offline: true });
  }
  ```

**Compuerta B:** ActaService usa OfflineService.

## Fase C: Sync automático

- [ ] **C.1** — Crear `core/services/sync.service.ts`.
  - Constructor: inicia polling cada 30s
  - Método: `syncPendingActas()`:
    - Si !navigator.onLine, skip
    - getPending() actas
    - Para cada una: intenta POST
    - Si éxito: removePending(), continúa
    - Si error: incrementa retries, espera siguiente intervalo

- [ ] **C.2** — En app.config.ts o main.ts, crear SyncService como provider (para que inicie automáticamente).

- [ ] **C.3** — Test: mock offline, guardar, setInterval simula tiempo, verificar reintento.

**Compuerta C:** setInterval ejecuta syncPendingActas() cada 30s.

## Fase D: Fallback a Filesystem

- [ ] **D.1** — Modificar OfflineService.addPending():
  - try: guardar en IDB
  - catch: fallback a Filesystem
    ```typescript
    catch(err) {
      const filename = `JRV-${payload.jrvId}_${Date.now()}_${userId}.json`;
      Filesystem.writeFile({
        path: 'Electoral/' + filename,
        data: JSON.stringify(payload),
        directory: FilesystemDirectory.Documents,
        encoding: Encoding.UTF8
      });
    }
    ```

**Compuerta D:** `ng build`. OfflineService tiene fallback.

## Qué NO hacer

- **No sync bidireccional** — solo push de actas pendientes
- **No resolver conflictos** — acta registrada siempre gana
- **No service worker** — IndexedDB polling es suficiente
- **No caché de lecturas** — solo escrituras pending

## Compuertas

| Fase | Comando | Expected |
|------|---------|----------|
| A | `ng build` | OfflineService importable |
| B | ActaService.saveActa() offline | guardaOffline() ejecutado |
| C | Pasar 30s en simulación | syncPendingActas() llamado |
| D | IDB quota exceeded | Filesystem fallback ejecutado |

## Verificación por Mutación

- **V.1** — Comentar addPending() → offline, guardar, acta debe perderse → restaurar
- **V.2** — Comentar syncPendingActas() → conexión, acta sigue en IDB (no se reintenta) → restaurar
