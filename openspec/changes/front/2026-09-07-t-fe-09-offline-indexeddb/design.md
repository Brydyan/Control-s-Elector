# Design — T-FE-09: Resiliencia Offline, Cola IndexedDB y Desbloqueo por PIN

---

## D1 — Estrategia Anti-Lockout: Desbloqueo por PIN Local

**Decisión**: 
- Cuando el veedor se autentica exitosamente contra el backend por primera vez, se le solicita definir un PIN de 4 dígitos.
- Se almacena un salt criptográfico y el hash SHA-256 del PIN en almacenamiento local seguro (`Preferences` de Capacitor o `localStorage`).
- En el `authGuard` / `auth.interceptor`, si el token expira y `!navigator.onLine`, en vez de forzar redirección a `/login` (que requiere red y fallaría), se activa la ruta `/offline-unlock` o un modal bloqueante solicitando el PIN de 4 dígitos.
- Validado el PIN, se autoriza al veedor a seguir operando en modo desconectado.

**Alternativa descartada — Extender la vida del JWT a 24 horas**: Aumenta gravemente el riesgo de seguridad si un dispositivo es robado o extraviado. El token de 15 minutos en el backend se preserva, resolviendo la usabilidad en campo mediante autenticación local diferida.

---

## D2 — Estructura de Datos en IndexedDB (`pending_actas`)

**Decisión**: Utilizar la biblioteca ligera `idb` para gestionar una base de datos `'ElectoralOfflineDB'` con el almacén `'pending_actas'`:
- `id`: UUID generado en cliente.
- `jrvId`: Identificador numérico de la JRV.
- `cargoId`: Identificador numérico de la dignidad.
- `payload`: JSON string del `ActaRequest`.
- `imageBlob`: `Blob` WebP capturado en el dispositivo.
- `createdAt`: Timestamp de creación local.
- `retries`: Contador de reintentos ejecutados.
- `lastError`: Último mensaje de error devuelto por la red/servidor.

**Alternativa descartada — Guardar solo el JSON sin la imagen**: Un acta transmitida sin su foto viola el protocolo CNE y obliga al veedor a volver a capturar la mesa.

---

## D3 — Sincronizador Bidireccional de Cola (`SyncService`)

**Decisión**: 
1. Subscripción al evento del navegador:
   ```typescript
   window.addEventListener('online', () => this.syncPendingActas());
   ```
2. Timer periódico de salvaguarda cada 30 segundos mientras existan actas en la cola.
3. Transmisión atómica en dos pasos para cada elemento:
   - Paso 1: `POST /api/v1/observer/actas` con el `payload`.
   - Paso 2: Si el acta se crea exitosamente, `POST /api/v1/observer/actas/{actaId}/imagen` con el `imageBlob`.
   - Paso 3: Borrado del registro en IndexedDB únicamente tras completar ambos pasos.

**Alternativa descartada — Reintento infinito descontrolado**: Si el servidor rechaza con 400 por inconsistencia de datos (ej. mesa cerrada), se incrementa el contador de reintentos y tras 5 fallos HTTP 4xx se marca como `REQUIERE_REVISION_MANUAL` para no saturar la red con requests condenados al fracaso.
