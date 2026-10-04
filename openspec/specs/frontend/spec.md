# Spec — Frontend Auth & Routing (T-FE-02, T-FE-03)

## Overview

This spec covers the frontend authentication and authorization subsystem:
- **T-FE-03**: Core auth services (TokenService, AuthService, session management)
- **T-FE-02**: Routing guards and RBAC enforcement

---

## T-FE-03: AuthService y Manejo de Tokens

### S1 — Login exitoso

```gherkin
GIVEN    AuthService inyectado
AND      endpoint POST /api/auth/login disponible
WHEN     llamas authService.login('user@example.com', 'pass123')
AND      backend retorna {accessToken: '...', user: {id, rol, email}}
THEN     TokenService.getToken() retorna el accessToken
AND      AuthService.currentUser$ emite {id, rol, email}
AND      Observable resuelve sin error
```

### S2 — Logout

```gherkin
GIVEN    usuario logged in
AND      TokenService tiene token
AND      currentUser$ tiene user info
WHEN     llamas authService.logout()
THEN     TokenService.getToken() retorna null
AND      currentUser$ emite null
```

### S3 — Refresh de token

```gherkin
GIVEN    TokenService tiene token expirado (pero no inválido aún)
AND      endpoint POST /api/auth/refresh disponible
WHEN     llamas authService.refresh()
AND      backend retorna nuevo {accessToken: '...'}
THEN     TokenService.getToken() es el nuevo token
AND      Observable resuelve sin error
```

### S4 — Token NO persiste en localStorage

```gherkin
GIVEN    usuario logged in
WHEN     inspeccionas localStorage en DevTools
THEN     no hay clave 'token' o similar
AND      token solo vive en memoria (TokenService)
```

### Mutaciones (T-FE-03)

#### M1 — Si TokenService guarda en localStorage

Si editas TokenService para usar localStorage.setItem('token', token), el token debe persistir. Verificación: logout, recargar, token debe seguir ahí (detectar falla).

#### M2 — currentUser$ no emite en login

Si comentas currentUser$.next(user) en login(), currentUser$ debe quedar sin emitir. Verificación: en un test, suscribirse a currentUser$ y verificar que no emite nada.

### Garantías (T-FE-03)

- **G1:** Token vive en memoria, no localStorage
- **G2:** logout borra token y currentUser
- **G3:** refresh obtiene nuevo token
- **G4:** currentUser$ es observable reactivo

---

## T-FE-02: Routing con RBAC Guards

### S1 — Sin token, intenta acceder a /observer/home

```gherkin
GIVEN    usuario sin token en memoria
WHEN     navega a /observer/home
THEN     authGuard retorna '/login'
AND      navegador redirige a /login
```

### S2 — Token válido, rol OBSERVER, accede a /observer/home

```gherkin
GIVEN    usuario con token rol=OBSERVER
WHEN     navega a /observer/home
THEN     authGuard retorna true
AND      roleGuard retorna true
AND      componente HomeComponent carga
```

### S3 — Token válido, rol ADMIN, intenta acceder a /master/config

```gherkin
GIVEN    usuario con token rol=ADMIN
AND      ruta /master/config requiere MASTER
WHEN     navega a /master/config
THEN     authGuard retorna true
AND      roleGuard retorna false (rol no permitido)
AND      navegador redirige a /
```

### S4 — Lazy-load: feature no se carga hasta necesitarse

```gherkin
GIVEN    app inicializada
AND      rutas con loadChildren() para features
WHEN     se lista archivos cargados en browser
THEN     features/{observer,coordinator,master}/observer.routes.js no está cargado
WHEN     navega a /observer/home
THEN     archivo observer.routes.js se carga dinámicamente
```

### Mutaciones (T-FE-02)

#### M1 — Remover authGuard de una ruta

Si se quita `canActivate: [authGuard]` de /coordinator/*, debe ser accesible sin token. Verificación: intentar acceder sin token, debe cargar (detectar falla).

#### M2 — Cambiar roleGuard a permitir ADMIN en /master/*

Cambiar guard para que ADMIN pueda entrar a /master/*. Debe cargarse (luego restaurar).

### Garantías (T-FE-02)

- **G1:** Sin token → no acceso a rutas protegidas
- **G2:** Rol incorrecto → redirección
- **G3:** Lazy-load funciona (features se cargan bajo demanda)

---

## T-FE-04: HTTP Interceptors (JWT + Refresh Automático)

### S1 — JwtInterceptor agrega Authorization header

```gherkin
GIVEN    TokenService tiene token='abc123'
WHEN     llamas http.get('/api/observer/mis-actas')
AND      interceptors se ejecutan
THEN     request HTTP tiene header Authorization: Bearer abc123
```

### S2 — GET /api/observer/mis-actas sin token

```gherkin
GIVEN    TokenService token=null
WHEN     llamas http.get('/api/observer/mis-actas')
THEN     request NO tiene Authorization header (o es 'Bearer null')
AND      backend retorna 401
AND      RefreshInterceptor detecta 401
AND      llama authService.refresh()
```

### S3 — Refresh exitoso, reintento de request original

```gherkin
GIVEN    request original retorna 401
AND      refresh() retorna nuevo token
WHEN     RefreshInterceptor maneja el 401
THEN     request original se reintenta con nuevo token
AND      si reintento retorna 200, Observable emite ese 200
```

### S4 — Refresh falla (401), propaga error

```gherkin
GIVEN    request original retorna 401
AND      refresh() también retorna 401 (token inválido)
WHEN     RefreshInterceptor intenta refresh()
THEN     la cadena propaga error 401
AND      Observable del caller retorna error
```

## Mutaciones (T-FE-04)

### M1 — Remover JwtInterceptor

Si se quita JwtInterceptor, requests no tienen Authorization header. Verificación: hacer request, inspeccionar headers, no debe tener Authorization.

### M2 — RefreshInterceptor no reintenta en 401

Si se borra la lógica de reintento, 401 propaga directamente sin intentar refresh. Verificación: generar 401, debe fallar en lugar de reintentarse.

## Garantías (T-FE-04)

- **G1:** Authorization header se agrega automáticamente
- **G2:** 401 dispara refresh automático
- **G3:** Request original se reintenta con nuevo token

---

## T-FE-05: Login Component

### S1 — Login exitoso como OBSERVER

```gherkin
GIVEN    LoginComponent cargado en /login
WHEN     usuario ingresa email 'observer@example.com' + password 'pass123'
AND      hace click en "Login"
AND      backend retorna {accessToken, user: {id, rol: 'OBSERVER', email}}
THEN     AuthService.login() emite {token, user}
AND      LoginComponent redirige a /observer/mis-jrvs
```

### S2 — Login exitoso como COORDINATOR

```gherkin
GIVEN    usuario ingresa email 'coordinator@example.com' + password 'pass123'
WHEN     hace click en "Login"
AND      backend retorna rol: 'COORDINATOR'
THEN     LoginComponent redirige a /dashboard
```

### S3 — Credenciales incorrectas

```gherkin
GIVEN    usuario ingresa email incorrecto o password incorrecto
WHEN     hace click en "Login"
AND      backend retorna 401
THEN     formulario muestra mensaje "Email o contraseña incorrecto"
AND      campo password se limpia
AND      usuario permanece en /login
```

### S4 — Validación en tiempo real

```gherkin
GIVEN    LoginComponent cargado
WHEN     usuario deja campo email vacío y hace blur
THEN     mensaje de error "Email es requerido"
WHEN     ingresa texto no válido como email (ej: 'abc')
THEN     mensaje "Email inválido"
WHEN     ingresa email válido (ej: 'test@ex.com')
THEN     error desaparece
```

### S5 — Loading spinner durante login

```gherkin
GIVEN    formulario está esperando respuesta del servidor
WHEN     hace click en "Login"
THEN     botón "Login" se deshabilita
AND      spinner gira mientras AuthService.login() está pending
AND      cuando respuesta llega, spinner desaparece
```

### Mutaciones (T-FE-05)

#### M1 — Comentar redirección en LoginComponent

Si se quita el Router.navigate() tras login(), usuario permanece en /login tras éxito. Verificación: login exitoso, debe estar en /login (detectar falla).

#### M2 — Quitar validación de email

Si se quita `Validators.email`, campo acepta 'abc' como email válido. Verificación: ingresa 'abc', error debe desaparecer (detectar falla).

### Garantías (T-FE-05)

- **G1:** Login redirects según rol
- **G2:** Validación previene envíos inválidos
- **G3:** Errores se muestran solo si campo fue tocado

---

# Spec — T-FE-06: Observer: Flujo de JRVs Asignadas, Dignidades y Consulta de Actas

## Escenarios

### S1 — Cargar lista de JRVs asignadas

```gherkin
GIVEN    usuario con rol OBSERVER autenticado
WHEN     navega a /observer/mis-jrvs
THEN     MisJrvsPage inicializa
AND      llama a ObserverService.getMisJrvs() (GET /api/v1/observer/jrvs)
AND      muestra las tarjetas de JRVs con número, parroquia y contadores de cargos pendientes
```

### S2 — Filtrar JRVs por estado de avance

```gherkin
GIVEN    lista de JRVs cargada en MisJrvsPage
WHEN     usuario presiona la pestaña "PENDIENTES"
THEN     se muestran únicamente las JRVs con cargosPendientes > 0
WHEN     usuario presiona "COMPLETADAS"
THEN     se muestran únicamente las JRVs con cargosPendientes === 0
```

### S3 — Navegar a las dignidades de una JRV

```gherkin
GIVEN    lista de JRVs visible
WHEN     usuario hace click en una tarjeta de JRV
THEN     el router navega a /observer/jrv/:jrvId/cargos
```

### S4 — Cargar dignidades de la JRV seleccionada

```gherkin
GIVEN    usuario en /observer/jrv/:jrvId/cargos
WHEN     JrvCargosPage inicializa
THEN     llama a ObserverService.getCargosDeJrv(jrvId) (GET /api/v1/observer/jrv/:jrvId/cargos)
AND      muestra cada dignidad (Alcalde, Prefecto, etc.) con su estado (registrado / sin registrar)
```

### S5 — Iniciar registro de un acta

```gherkin
GIVEN    lista de dignidades de la JRV
WHEN     usuario presiona "Registrar Acta" en una dignidad sin registrar
THEN     el router navega a /observer/jrv/:jrvId/cargo/:cargoId/registrar
```

### S6 — Manejo de errores de conexión con reintento

```gherkin
GIVEN    falla temporal de conectividad al consultar JRVs o dignidades
WHEN     el servicio retorna error HTTP 5xx o de red
THEN     se muestra ErrorStateComponent con mensaje amigable
AND      el botón "Reintentar" permite volver a solicitar los datos al backend
```

## Garantías

- **G1:** Peticiones HTTP dirigidas a `/api/v1/observer/*` con Bearer Token inyectado.
- **G2:** Segregación estricta de rutas con `roleGuard(['OBSERVER', 'MASTER'])`.
- **G3:** Mapeo de estados de acta compatible con el backend (`BORRADOR`, `ENVIADA`, `APROBADA`, `EN_DISPUTA`, `CORREGIDA`, `ANULADA`).

---

# Spec — T-FE-07: Formulario de Registro de Acta (Wizard 3 Pasos)

## Escenarios

### S1 — Carga dinámica de candidatos y restauración de borrador

```gherkin
GIVEN    veedor en /observer/jrv/:jrvId/cargo/:cargoId/registrar
WHEN     RegistrarActaPage inicializa
THEN     consulta candidatos y partidos para el cargoId
AND      si existe un borrador previo en local storage, rellena automáticamente los votos ingresados
```

### S2 — Validación en tiempo real del cuadre de votos

```gherkin
GIVEN    formulario en paso VOTOS con candidatos cargados
WHEN     usuario ingresa votos a candidatos, votos en blanco, nulos y total sufragantes
THEN     se actualizan reactivamente: totalVotosComputados, diferenciaChecksum e isChecksumValid
AND      si totalVotosComputados === totalSufragantes (y totalSufragantes > 0), el indicador muestra cuadre exacto
```

### S3 — Navegación del Wizard: Votos a Imagen

```gherkin
GIVEN    usuario en paso VOTOS
WHEN     presiona "Siguiente: Respaldo Fotográfico"
THEN     currentStep cambia a 'IMAGEN'
AND      se despliega el selector de fotografía y captura
```

### S4 — Carga y previsualización de fotografía

```gherkin
GIVEN    usuario en paso IMAGEN
WHEN     selecciona o captura una imagen
THEN     se genera la URL de vista previa (DataURL)
AND      se habilita el botón "Siguiente: Revisión"
```

### S5 — Guardado como Borrador (Permite Inconsistencias Aritméticas)

```gherkin
GIVEN    acta con digitación parcial (suma aún no cuadra)
WHEN     usuario presiona "Guardar como Borrador"
THEN     envía payload a POST /api/v1/observer/actas/borrador
AND      backend responde con éxito (HTTP 200)
AND      si hay imagen adjunta, se envía a POST /api/v1/observer/actas/:id/imagen
```

### S6 — Envío de Acta Final (Exige Cuadre Estricto)

```gherkin
GIVEN    acta con cuadre perfecto (isChecksumValid === true)
WHEN     usuario presiona "Confirmar y Transmitir Acta"
THEN     envía payload a POST /api/v1/observer/actas
AND      si hay imagen adjunta, ejecuta POST /api/v1/observer/actas/:id/imagen
AND      redirige a /observer/jrv/:jrvId/cargos tras confirmación visual
```

### S7 — Bloqueo de Envío Final ante Descuadre

```gherkin
GIVEN    acta con diferenciaChecksum !== 0
WHEN     usuario intenta transmitir acta final
THEN     la acción se rechaza
AND      se muestra mensaje de error: "Verificación electoral inválida: los votos no cuadran"
```

## Garantías

- **G1:** Invariante matemático verificado antes del envío final.
- **G2:** Contrato JSON idéntico a `ActaRequestDto` del backend Spring Boot.
- **G3:** Subida de evidencia encadenada usando `@RequestPart("imagen")`.

---

# T-FE-08: Captura y Compresión de Imagen (WebP)

## Escenarios

### S1 — Captura y compresión nativa exitosa

```gherkin
GIVEN    usuario en dispositivo móvil con Capacitor
WHEN     activa la captura de foto
THEN     la cámara del dispositivo se abre
AND      la fotografía capturada se dibuja en Canvas y se exporta a WebP (calidad 0.82)
AND      si el tamaño es <= 500 KB, se emite el Blob resultante
```

### S2 — Recompresión adaptativa si excede 500 KB

```gherkin
GIVEN    fotografía capturada de alta resolución
WHEN     la primera pasada a calidad 0.82 resulta en 750 KB (> 500 KB)
THEN     se ejecuta una segunda compresión automática a calidad 0.65
AND      el Blob final resultante tiene tamaño < 500 KB
```

### S3 — Fallback web para navegadores de escritorio

```gherkin
GIVEN    usuario utilizando la app en navegador web sin Capacitor runtime
WHEN     presiona "Tomar o seleccionar foto"
THEN     se activa el input HTML de archivo (<input type="file" accept="image/*">)
AND      el archivo seleccionado pasa por la misma tubería de compresión WebP en Canvas
```

### S4 — Transmisión Multipart con clave 'imagen'

```gherkin
GIVEN    Blob WebP listo para subida
WHEN     se invoca ObserverService.subirImagenActa(actaId, file)
THEN     se construye un FormData con la clave exacta 'imagen' (formData.append('imagen', blob, 'acta.webp'))
AND      se envía vía HTTP POST a /api/v1/observer/actas/:id/imagen
AND      el backend Spring Boot acepta la petición sin MissingServletRequestPartException
```

### S5 — Manejo de permisos o cancelación de cámara

```gherkin
GIVEN    diálogo de permisos de cámara del sistema operativo
WHEN     el usuario cancela o niega el permiso
THEN     la aplicación captura el error limpiamente
AND      permite al usuario seleccionar un archivo desde el almacenamiento del dispositivo
```

## Garantías

- **G1:** Formato de salida estricto `image/webp`.
- **G2:** Tamaño acotado (< 500 KB) para permitir transmisión rápida en redes 2G/3G.
- **G3:** Clave multipart obligatoria `'imagen'` compatible con `@RequestPart("imagen")` de Spring Boot.

---

## T-FE-09: Resiliencia Offline, Cola IndexedDB y Desbloqueo por PIN

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

