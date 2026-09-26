# Tasks — T-FE-09: Resiliencia Offline, Cola IndexedDB y Desbloqueo por PIN

## Fase A: Mecanismo de Desbloqueo por PIN

- [ ] **A.1** — Implementar servicio de PIN `core/auth/pin-auth.service.ts`:
  - `configurarPin(pin: string): Promise<void>` (almacena salt + SHA-256 hash).
  - `validarPin(pin: string): Promise<boolean>`.
  - `tienePinConfigurado(): boolean`.
- [ ] **A.2** — Componente modal de desbloqueo `shared/components/pin-unlock-modal/`.
- [ ] **A.3** — Integración en `authGuard`: interceptar expiración de sesión cuando `!navigator.onLine` para desplegar el modal de PIN en lugar de redirigir a `/login`.

## Fase B: Almacén IndexedDB con librería 'idb'

- [ ] **B.1** — Instalar `idb` y crear `core/offline/offline-storage.service.ts`:
  - Base de datos `'ElectoralOfflineDB'`.
  - Store `'pending_actas'` con clave `id` (UUID).
  - Métodos `encolarActa(acta: PendingActa): Promise<void>`, `obtenerPendientes(): Promise<PendingActa[]>`, `eliminarPendiente(id: string): Promise<void>`.
- [ ] **B.2** — Tests unitarios de almacenamiento offline con mock de IndexedDB.

## Fase C: Sincronizador en Segundo Plano

- [ ] **C.1** — Implementar `core/offline/sync.service.ts`:
  - Listener para evento `online` del navegador.
  - Timer periódico cada 30 segundos si hay elementos en cola.
  - Ejecución encadenada: `POST /api/v1/observer/actas` -> `POST /api/v1/observer/actas/{id}/imagen`.
- [ ] **C.2** — Banner de notificación visual en el layout superior indicando "Sincronizando X actas pendientes...".

## Verificación por Mutación

- **V.1** — Invalidar verificación de PIN -> el modal debe rechazar el PIN incorrecto y no desbloquear.
- **V.2** — Comentar la eliminación del registro tras sync exitoso -> el acta se reenviaría indefinidamente (detectar fallo en test).
