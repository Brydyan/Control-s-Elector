# Tasks: 2026-09-07-t-be-14-storage-acta

Origen: `documents/task-control/backendtask/T-BE-14.md`
Rama: `feat/t-be-14-storage-acta`, sacada de `develop`.

**Bloqueado por** `back/2026-09-07-fix-liquibase-rbac-order`.

Orden obligatorio: **A antes que B.** A prueba que el defecto de compensación existe. Si
arreglás antes de reproducirlo, no vas a saber si lo arreglaste.

---

## A · Reproducir los defectos antes de tocarlos

- [ ] **A.1** — `git status --short` limpio. Levantar MinIO de test.

- [ ] **A.2** — **Reproducir el huérfano de S3** (RF-STO-03). Escribí un test que:
  1. suba un archivo por `POST /api/v1/evidencias`,
  2. fuerce una violación de constraint que explote **en el commit**, no en el `save()`,
  3. compruebe con `s3.exists(key)` si el objeto quedó.

  **Debe quedar.** Ése es el estado actual. Anotá en `apply-progress.md` que lo reprodujiste
  y con qué constraint.

  Si **no** queda huérfano, parás y lo decís: significa que mi diagnóstico de `design.md` D2
  es incorrecto y `fixes-required.md` tiene que corregirlo antes de que nadie escriba código.

  **Trampa**: con un mock de `S3StorageService` este test pasa siempre y no prueba nada.
  Tiene que ser MinIO real (`design.md` D3).

- [ ] **A.3** — **Reproducir el borrado que miente** (RF-STO-04). Test que haga fallar el
  `delete` de S3 y compruebe que el endpoint devuelve `204` igualmente. Debe devolverlo.

- [ ] **A.4** — Contar las filas de `audit_log` tras un upload y un delete de evidencia.
  **Deben ser cero.** Anotalo: es la línea base de RF-STO-05.

---

## B · El endpoint que falta

- [ ] **B.1** — Implementar `POST /api/v1/observer/actas/{actaId}/imagen` en
  `ObserverController`, reemplazando el `501` de las líneas 191-196.

  Reglas: mismas que evidencia — 10 MB, `Content-Type` `image/*`, SHA-256. Ver RF-STO-02.

  **Trampa**: no dupliques la validación. Si `EvidenciaService.validate` y la ruta de acta
  tienen cada una su copia, divergen. Extraé una y usala en las dos, o el día que alguien
  suba el límite lo hará en un solo sitio (§4.2).

- [ ] **B.2** — Persistir la **clave**, no la URL prefirmada (RF-STO-06, `design.md` D1).

  Si el campo se llama `storageUrl` y guarda una clave, renombralo a `storageKey` con su
  migración Liquibase. Si no lo renombrás, dejá escrito en la entidad exactamente qué guarda
  — pero el nombre seguirá mintiendo, así que preferí renombrar.

- [ ] **B.3** — Arreglar la compensación (RF-STO-03, `design.md` D2): la limpieza de S3 se
  engancha al final de la transacción, no al `try/catch` de `save()`.

- [ ] **B.4** — `delete` deja de tragarse el error de S3 (RF-STO-04, `design.md` D4).

- [ ] **B.5** — Auditar upload y delete, de acta y de evidencia (RF-STO-05, `design.md` D5).

- [ ] **B.6** — La expiración de la presigned URL sale de configuración (RF-STO-07). Corregir
  `T-BE-14.md:92`, que dice 24 h cuando el código usa 15 min.

---

## C · Las mutaciones

`cp` al `.bak`, mutar, correr, **anotar el nombre del test que cayó**, restaurar con `cp`,
`rm` del `.bak`, `git status --short` limpio. **Nunca `git checkout --`.**

- [ ] **C.1** — RF-STO-03. Devolvé la compensación al `try/catch` alrededor de `save()`.
  Debe caer el test de A.2.

  **Si no cae, el test está mockeando S3** y toda esta tarea es decorativa. Rehacelo con
  MinIO real antes de seguir.

- [ ] **C.2** — RF-STO-04. Reintroducí el `catch` que se traga el error de S3.
  Debe caer el test de A.3.

- [ ] **C.3** — RF-STO-05. Quitá `auditLogService.registrar` de `delete`.
  Debe caer el test que cuenta filas de `audit_log`.

- [ ] **C.4** — RF-STO-02. Subí el límite a 100 MB en **una sola** de las dos rutas.
  Debe caer el test de tamaño de esa ruta. Si sólo cae en una porque la otra no tiene test,
  la otra no está protegida: escribilo.

- [ ] **C.5** — RF-STO-01. Quitá la persistencia de la referencia, dejando sólo el upload.
  Debe caer el test que lee el acta después de subir.

---

## Qué NO hacer en esta fase

- **No** reescribir `S3StorageService`. Funciona; lo que falla es cómo se usa.
- **No** implementar compresión WebP en el backend. `CLAUDE.md` la sitúa en el cliente
  (Canvas API, calidad 0.82 → 0.65 si supera 500 KB). El backend valida el resultado.
- **No** implementar la resiliencia offline: IndexedDB y Private Storage son T-FE-06.
- **No** mockear `S3StorageService` en los tests de A.2 y A.3. Es exactamente lo que hace
  que este defecto lleve meses sin detectarse.
- **No** marcar T-BE-14 como completada sin que A.2 haya pasado de «el objeto queda» a
  «el objeto no queda». Ya se marcó completada una vez mirando el servicio en vez del flujo.

---

## Compuertas antes de dar por terminada la fase

```
cd backend/electoral-api
./mvnw -B test > /tmp/after.log 2>&1; echo "exit=$?"

    0 errores de ApplicationContext
    total = línea base + los tests nuevos (contados, no estimados)

curl -X POST http://localhost:8081/api/v1/observer/actas/1/imagen \
     -H "Authorization: Bearer TOKEN_OBSERVER" -F "imagen=@foto.webp"
    → 201, NO 501
```

Si el total de tests no sube, los tests nuevos no se están ejecutando.
