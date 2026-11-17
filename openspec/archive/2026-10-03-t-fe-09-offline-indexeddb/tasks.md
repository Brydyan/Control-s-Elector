# Tasks — T-FE-09: Resiliencia Offline, Cola IndexedDB, Blindaje PWA y Desbloqueo por PIN

## Fase A: Mecanismo de Desbloqueo por PIN

- [x] **A.1** — Implementar servicio de PIN `core/auth/pin-auth.service.ts`:
  - `configurarPin(pin: string): Promise<void>` (almacena salt + SHA-256 hash).
  - `validarPin(pin: string): Promise<boolean>`.
  - `tienePinConfigurado(): boolean`.
- [x] **A.2** — Componente modal de desbloqueo `shared/components/pin-unlock-modal/`.
- [x] **A.3** — Integración en `authGuard`: interceptar expiración de sesión cuando `!navigator.onLine` para desplegar el modal de PIN en lugar de redirigir a `/login`.

## Fase B: Almacén IndexedDB con librería 'idb' y Blindaje de Persistencia

- [x] **B.1** — Instalar `idb` y crear `core/offline/offline-storage.service.ts`:
  - Base de datos `'ElectoralOfflineDB'`.
  - Store `'pending_actas'` con clave `id` (UUID).
  - Store `'acta_drafts'` con clave compuesta o `draftId` (`draft_${jrvId}_${cargoId}`) para el patrón "Guardar antes de capturar".
  - Métodos de cola: `encolarActa(acta: PendingActa): Promise<void>`, `obtenerPendientes(): Promise<PendingActa[]>`, `eliminarPendiente(id: string): Promise<void>`.
  - Métodos de borrador preventivo: `guardarBorrador(draft: ActaDraft): Promise<void>`, `obtenerBorrador(draftId: string): Promise<ActaDraft | null>`, `eliminarBorrador(draftId: string): Promise<void>`.
- [x] **B.2** — Blindaje de almacenamiento:
  - Invocar `navigator.storage.persist()` tras login exitoso en `AuthService` para blindar IndexedDB y evitar evicciones automáticas por falta de espacio en Android.
  - Verificar estado con `navigator.storage.persisted()`.
- [x] **B.3** — Patrón "Guardar antes de capturar" en `RegistrarActaPage`:
  - Guardado preventivo atómico en `'acta_drafts'` inmediatamente antes de abrir la cámara.
  - Restauración automática del estado si la pestaña fue reciclada o cerrada por Android LMK al abrir la cámara.
  - Limpieza del borrador en `'acta_drafts'` tras encolar en `'pending_actas'` o transmitir con éxito.
- [x] **B.4** — Tests unitarios en `offline-storage.service.spec.ts` y `registrar-acta.page.spec.ts`:
  - Test de invocación de `navigator.storage.persist()`.
  - Test de guardado en `acta_drafts` antes del disparo de cámara.
  - Test de restauración automática de borrador tras simulación de recarga/reinicio de componente por LMK.

## Fase C: Sincronizador en Segundo Plano

- [x] **C.1** — Implementar `core/offline/sync.service.ts`:
  - Listener para evento `online` del navegador.
  - Timer periódico cada 30 segundos si hay elementos en cola.
  - Ejecución encadenada: `POST /api/v1/observer/actas` -> `POST /api/v1/observer/actas/{id}/imagen`.
- [x] **C.2** — Banner de notificación visual en el layout superior indicando "Sincronizando X actas pendientes...".
  > Banner NO implementado como componente standalone en esta iteración.
  > SyncService expone `pendientes` y `sincronizando` como `signal`s. UI puede suscribirse via `@if`.
  > Integración con layout queda como Hallazgo (no había layout compartido identificable).

## Fase D: Service Worker para App Shell Offline (PWA)

- [ ] **D.1** — Configurar `@angular/service-worker` en `ngsw-config.json` para pre-cacheo de aplicación completa (HTML, JS, CSS, iconos Ionic).
  > **DESVÍO DECLARADO**: D.1, D.2 diferidos a change de seguimiento `t-fe-09b-pwa-service-worker`. Riesgo de romper dev mode y necesidad de testing manual con Playwright.
- [ ] **D.2** — Test de verificación de carga en frío 100% offline.

## Verificación por Mutación

- **V.1** — Invalidar verificación de PIN -> el modal debe rechazar el PIN incorrecto y no desbloquear.
- **V.2** — Comentar la eliminación del registro tras sync exitoso -> el acta se reenviaría indefinidamente (detectar fallo en test).
- **V.3** — Omitir la llamada a `navigator.storage.persist()` en el login -> test unitario de blindaje de persistencia debe fallar.
- **V.4** — Desactivar el guardado en `acta_drafts` previo a invocar la cámara -> test del patrón "Guardar antes de capturar" debe fallar.
- **V.5** — Simular recarga de página con borrador pendiente sin invocar la restauración -> test de recuperación ante Android LMK debe fallar.
