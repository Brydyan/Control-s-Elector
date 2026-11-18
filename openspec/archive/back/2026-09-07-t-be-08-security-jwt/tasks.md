# Tasks: 2026-09-07-t-be-08-security-jwt

Origen: `documents/task-control/backendtask/COMPLETADO-T-BE-08.md`
Rama: `verify/t-be-08-security-jwt`, sacada de `develop`.

**Change retrospectivo**: verifica y documenta. Excepción: B.1 (CORS), que es un defecto de
producción con fecha de vencimiento.

**Bloqueado por** `back/2026-09-07-fix-liquibase-rbac-order`.

---

## A · Verificar por mutación

`cp` al `.bak`, mutar, correr, **anotar el nombre del test que cayó**, restaurar con `cp`,
`rm` del `.bak`, `git status --short` limpio. **Nunca `git checkout --`.**

- [ ] **A.1** — `git status --short` limpio. Línea base. `JwtServiceTest` debería dar 3 en
  verde: es de las pocas suites que ya pasaban.

- [ ] **A.2** — RF-SEC-01. Cambiá `.anyRequest().authenticated()` por `.permitAll()`.
  Debe caer el test de petición sin token → 401.

- [ ] **A.3** — RF-SEC-03. Quitá la comprobación de longitud del secreto en `JwtService`.
  Debe caer el test que arranca el contexto con un secreto corto.

- [ ] **A.4** — RF-SEC-04. Guardá el refresh en claro en `Sesion.refreshHash`.
  Debe caer el test que compara el valor almacenado con el token emitido.

- [ ] **A.5** — RF-SEC-05. Quitá la actualización de `refreshHash` en `AuthService.refresh`.
  Debe caer el test de reutilización del refresh.

- [ ] **A.6** — RF-SEC-06. Quitá el `if (!sesion.activa())`.
  Debe caer el test de refresh sobre sesión revocada.

- [ ] **A.7** — RF-SEC-02. Quitá `@PreAuthorize("@authz.has('acta:upload')")` de
  `ObserverController.registrarActa`. Debe caer el test de rol sin permiso → 403.

---

## B · CORS: el defecto que vence el día del despliegue

- [ ] **B.1** — RF-SEC-09. Configurar CORS para el origen del frontend, con credenciales.

  **Reproducí primero el fallo.** No basta con leer `SecurityConfig:43`:

  ```bash
  curl -i -X OPTIONS http://localhost:8081/api/v1/auth/login \
    -H "Origin: https://control.brydyan.me" \
    -H "Access-Control-Request-Method: POST"
  ```

  Mirá si vuelve `Access-Control-Allow-Origin`. Anotá lo que salga **antes** de tocar nada.

  Después configurá: origen explícito (nunca `*`, es incompatible con credenciales),
  `allowCredentials(true)`, y los métodos y cabeceras que la app usa. El origen sale de
  configuración, no hardcodeado: dev y prod son distintos.

  **Trampa**: en local no se ve el problema porque `proxy.conf.json` hace que todo sea mismo
  origen. Verificá con `curl` y una cabecera `Origin` explícita, no abriendo el frontend.

- [ ] **B.2** — Comprobar que el login completo funciona cross-origin: que la cookie
  `refresh_token` se **establece** y se **reenvía**. `SameSite=Strict` con dominios distintos
  (`control.` y `api.` bajo `brydyan.me`) merece una comprobación explícita: son
  same-site pero cross-origin, y el comportamiento no es obvio.

  Si `SameSite=Strict` impide el flujo, es un hallazgo con su reproducción — **no lo cambies
  a `Lax` o `None` sin decirlo**: es una decisión de seguridad.

---

## C · Alinear la documentación con el código

- [ ] **C.1** — RF-SEC-08. Corregir `CLAUDE.md`, sección Seguridad:

  - el refresh **no es opaco**: es un JWT firmado con claim `typ: "refresh"`;
  - el TTL **no es 8h**: es 7 días (`app.jwt.refresh-ttl:7d`, cookie `7*24*3600`,
    `Sesion.expiraEn` +7 días).

  **Antes de corregir, preguntá**: ¿los 7 días son la decisión, o el valor por defecto que
  nadie cambió? Si se quería 8h por la duración del proceso electoral, lo que hay que cambiar
  es el código, no el documento. Anotá cuál elegiste y por qué.

- [ ] **C.2** — RF-SEC-07. Declarar en `CLAUDE.md` el alcance real del logout: revoca la
  sesión y los refresh futuros, **y el access token vigente sobrevive hasta 15 minutos**.
  Hoy sólo consta en un comentario de `JwtAuthFilter`.

- [ ] **C.3** — RF-SEC-10. Swagger. Decidir **una**:
  - restringir `/v3/api-docs/**` y `/swagger-ui/**` por perfil (`prod` los cierra); **o**
  - dejarlos abiertos y borrar el comentario que dice que hay que cerrarlos.

  Lo que no vale es el estado actual: un comentario que describe una protección inexistente.

---

## D · Los dos hallazgos que se reportan, no se arreglan

- [ ] **D.1** — RF-SEC-11, RLS. La medición vive en `t-be-15-tests-integracion` RF-TEST-05.
  Si ese change ya cerró, traé aquí el resultado. Si no, anotá que está pendiente y que
  `ActaService:77-82` afirma una garantía todavía sin comprobar.

  **No arregles `RlsInterceptor` aquí.** Mover el `SET LOCAL` dentro de la transacción cambia
  el modelo de acceso a datos y necesita su propia verificación.

- [ ] **D.2** — RNF-SEC-01. El SQL concatenado de `RlsInterceptor:56`. Hoy `userId` es un
  `Long` de la base, así que no es explotable — pero es la forma que se copia. Proponer el
  cambio a parámetro. Si D.1 acaba reescribiendo el interceptor, se arregla ahí.

---

## Qué NO hacer en esta fase

- **No** reescribir el modelo de sesión. Es mejor que el del task doc y funciona.
- **No** arreglar `RlsInterceptor`. Medí y reportá (D.1).
- **No** cambiar `SameSite` de la cookie sin declararlo como decisión de seguridad.
- **No** poner `Access-Control-Allow-Origin: *`. Es incompatible con `withCredentials` y
  abriría la API a cualquier origen.
- **No** corregir `CLAUDE.md` para que coincida con el código sin preguntarte antes si el
  código es lo que se quería (C.1).

---

## Compuertas antes de dar por terminada la fase

```
cd backend/electoral-api
./mvnw -B test > /tmp/after.log 2>&1; echo "exit=$?"
    0 errores de ApplicationContext
    total = línea base + los tests añadidos (contados)

curl -i -X OPTIONS http://localhost:8081/api/v1/auth/login \
  -H "Origin: https://control.brydyan.me" \
  -H "Access-Control-Request-Method: POST"
    → Access-Control-Allow-Origin presente y con el origen exacto
    → Access-Control-Allow-Credentials: true
```

Las seis mutaciones de A anotadas con el nombre del test que cayó en cada una.
