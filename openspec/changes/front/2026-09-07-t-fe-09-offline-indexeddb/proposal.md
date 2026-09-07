# Proposal — T-FE-09: Resiliencia Offline con IndexedDB

## Qué se construye
Cuando observer pierde conexión al enviar acta, el sistema guarda en IndexedDB y sincroniza cuando vuelve la conexión.

**Dos capas de fallback:**
1. **IndexedDB** — base de datos del navegador. Reintento automático cada 30s.
2. **Capacitor Filesystem** — si IndexedDB falla, guarda en privado del dispositivo.

## Por qué
- Observer en campo puede perder conexión.
- No perder la acta registrada es crítico.
- Sync automático evita manual retry.

## Alcance

| Elemento | Incluye | No incluye |
|----------|---------|-----------|
| IndexedDB | CREATE TABLE, INSERT, sync automático | replicación bidireccional |
| Capacitor FS | Fallback si IDB falla | sincronización de archivos |
| Polling | Reintento cada 30s | WebSocket, real-time sync |
| Offline detection | navigator.onLine | service worker |
| UI | Indicador "Sincronizando..." en navbar | interfaz de UI offline |

## Criterio de aceptación

- [ ] ActaService crea table en IDB al inicializar
- [ ] POST /api/observer/actas falla → guarda en IDB (no pierde data)
- [ ] Conexión vuelve → reintenta POST cada 30s
- [ ] Cuando éxito, elimina de IDB
- [ ] Si IDB falla, fallback a Filesystem
- [ ] Test: simular offline, guardar, verificar que está en IDB

## Fuera de alcance

- Real-time sync bidireccional
- Merge conflict resolution
- Service Worker precaching
- Caché de lecturas

## Dependencias

- T-FE-07 (ActaFormComponent)
- T-FE-08 (ImageService)

## Decisiones a tomar

**D1 — dónde chequea offline:** En interceptor (T-FE-04) o en ActaService? Elegir ActaService (más específico).

**D2 — qué datos guardar en IDB:** Payload completo (candidatos, votos, etc.) + Blob imagen?
