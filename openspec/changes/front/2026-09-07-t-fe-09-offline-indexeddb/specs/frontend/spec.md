# Spec — T-FE-09: Resiliencia Offline con IndexedDB

## Escenarios

### S1 — Offline: guardar en IDB

```gherkin
GIVEN    usuario está offline (navigator.onLine = false)
AND      intenta POST /api/observer/actas
WHEN     POST falla con error
THEN     ActaService guarda en IDB en table pending_actas
AND      UI muestra "Acta guardada offline. Se sincronizará automáticamente."
AND      Observable resuelve (no error)
```

### S2 — Conexión vuelve: reintento automático

```gherkin
GIVEN    acta en IDB (pending_actas)
AND      usuario conecta de nuevo (navigator.onLine = true)
WHEN     ActaService detecta online
THEN     reintenta POST /api/observer/actas con payload guardado
AND      si éxito (200), elimina de IDB
AND      UI muestra "Acta sincronizada"
```

### S3 — Reintento cada 30s

```gherkin
GIVEN    acta en IDB, navegador sigue offline
WHEN     30 segundos pasan
THEN     ActaService intenta POST nuevamente (automaticamente)
WHEN     aún offline, se vuelve a guardar en log (no reintenta infinitamente)
```

### S4 — IDB lleno, fallback a Filesystem

```gherkin
GIVEN    IndexedDB falla (quota exceeded o error)
WHEN     ActaService intenta guardar en IDB
THEN     cae a Capacitor Filesystem
AND      guarda en directorio privado: JRV-{jrvId}_{timestamp}_{userId}.json
```

## Mutaciones

### M1 — No guardar en IDB si offline

Si se quita el saveOffline() call, acta debe perderse. Verificación: offline, guardar, cierra app, abre de nuevo, acta debe estar (detectar falla).

### M2 — No reintento automático

Si se comenta el polling setInterval(), acta queda en IDB pero nunca se reintenta. Verificación: guardar offline, conectar, esperar 30s, acta sigue en IDB (detectar falla).

## Garantías

- **G1:** Acta no se pierde offline
- **G2:** Reintento automático cada 30s
- **G3:** Fallback a Filesystem
- **G4:** Eliminación de IDB tras éxito
