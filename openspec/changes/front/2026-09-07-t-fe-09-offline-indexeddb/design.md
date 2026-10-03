# Design — T-FE-09: Resiliencia Offline, Cola IndexedDB, Blindaje PWA y Desbloqueo por PIN

---

## D1 — Estrategia Anti-Lockout: Desbloqueo por PIN Local

**Decisión**: 
- Cuando el veedor se autentica exitosamente contra el backend por primera vez, se le solicita definir un PIN de 4 dígitos.
- Se almacena un salt criptográfico y el hash SHA-256 del PIN en almacenamiento local seguro (`Preferences` de Capacitor o IndexedDB cifrado).
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

---

## D4 — Blindaje de Persistencia con `navigator.storage.persist()`

**Decisión**: 
- Durante el flujo de login exitoso o inicialización de la PWA, se ejecuta `navigator.storage.persist()` para solicitar almacenamiento persistente al navegador.
- Se inspecciona el estado resultante con `navigator.storage.persisted()`.
- Esta llamada blinda la base de datos `ElectoralOfflineDB` y `CacheStorage` frente a políticas automáticas de evicción de almacenamiento que Android / Chromium ejecutan cuando el almacenamiento del dispositivo está por debajo de umbrales críticos.

**Alternativa descartada — Almacenamiento por defecto "best-effort"**: En móviles económicos con almacenamiento saturado, Chromium purga silenciosamente los almacenes IndexedDB de orígenes web no persistentes, eliminando las actas pendientes de transmisión antes de que el veedor logre recuperar cobertura.

---

## D5 — Patrón "Guardar antes de capturar" en Almacén `acta_drafts` y Resiliencia ante Android LMK

**Decisión**: 
- En `ElectoralOfflineDB` se crea un segundo almacén `'acta_drafts'` indexado por clave de mesa/dignidad (`draft_${jrvId}_${cargoId}`).
- **Guardado preventivo**: Inmediatamente antes de invocar la captura de cámara nativa (mediante el input file o plugin de cámara), el wizard guarda atómicamente el estado actual del formulario (números ingresados, mesa, sufragantes) en `acta_drafts`.
- **Protección contra Android LMK**: En dispositivos de 2GB de RAM, abrir la aplicación de cámara del sistema operativo suele forzar al kernel Linux / Android LMK (Low Memory Killer) a terminar el proceso en background de la pestaña de Chrome. Al reabrir la PWA o refrescarse la página tras la captura, el ciclo de vida del componente (`ngOnInit` / `ionViewWillEnter`) consulta `acta_drafts`, restaura automáticamente el avance y prosigue el flujo sin pérdida de información.
- Una vez completada la acción y pasada el acta a `pending_actas` o transmitida con éxito, el borrador en `acta_drafts` se elimina limpiamente.

**Alternativa descartada — Confiar únicamente en el estado en memoria de Angular**: Si el sistema operativo mata la pestaña en segundo plano mientras se toma la foto, el veedor pierde todo lo digitado y debe reiniciar desde cero.
**Alternativa descartada — `localStorage` sincrónico**: Bloquea el hilo de UI y no es adecuado para almacenar borradores con referencias a blobs o estructuras complejas.

---

## D6 — Service Worker para Application Shell 100% Offline (PWA)

**Decisión**: 
- Configuración de Service Worker mediante `@angular/service-worker` (`ngsw-config.json`) con política de precacheo de instalación estricta (`installMode: 'prefetch'`) para el Application Shell: HTML, bundles JS/CSS, webmanifest, iconos Ionic y tipografías.
- Garantiza que la PWA arranque en frío y funcione de forma totalmente autónoma en recintos electorales rurales o subterráneos sin cobertura móvil alguna.

**Alternativa descartada — HTTP Caching estándar**: El caché HTTP no asegura disponibilidad offline completa ante recargas forzadas o pérdidas completas de conectividad celular en campo.
