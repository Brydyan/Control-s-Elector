# Spec — T-FE-09: Resiliencia Offline, Cola IndexedDB y Desbloqueo por PIN

## Escenarios

### S1 — Desbloqueo por PIN en campo sin internet

```gherkin
GIVEN    veedor autenticado previamente que configuró un PIN de 4 dígitos
AND      se encuentra en una zona rural sin señal (!navigator.onLine)
AND      el JWT de 15 minutos ha expirado
WHEN     el veedor abre o interactúa con la aplicación
THEN     la aplicación no redirige a /login
AND      presenta la pantalla modal de desbloqueo por PIN
AND      al ingresar el PIN correcto, se restablece el acceso a las vistas de trabajo
```

### S2 — Fallo de transmisión enruta a cola IndexedDB

```gherkin
GIVEN    veedor transmite un acta con cuadre correcto pero falla la conexión HTTP
WHEN     ObserverService detecta error de red (HTTP 0 o timeout)
THEN     el acta (datos + foto) se almacena en el almacén pending_actas de IndexedDB
AND      la interfaz informa: "Acta guardada en este dispositivo. Se transmitirá en cuanto recupere señal."
```

### S3 — Sincronización automática al recuperar conexión

```gherkin
GIVEN    1 o más actas pendientes en la cola IndexedDB
WHEN     el dispositivo recupera señal de red (evento 'online')
THEN     SyncService inicia la transmisión secuencial
AND      por cada acta: envía datos a POST /api/v1/observer/actas
AND      seguidamente envía la foto a POST /api/v1/observer/actas/:id/imagen
AND      al recibir HTTP 200/201 en ambos, elimina el registro de IndexedDB
AND      notifica visualmente: "Sincronización completada"
```

### S4 — Reintento periódico de salvaguarda cada 30 segundos

```gherkin
GIVEN    actas encoladas en IndexedDB
AND      la conexión es inestable (oscila entre 2G y desconexión)
WHEN     transcurren 30 segundos
THEN     SyncService verifica si la red está disponible y reintenta el envío de la cola
```

## Garantías

- **G1:** Cero pérdida de datos ante caídas de red o expiración de JWT en campo.
- **G2:** Eliminación de cola local únicamente tras confirmación HTTP 2xx de datos e imagen.
- **G3:** Protección de acceso mediante PIN local de 4 dígitos cuando no hay conexión.
