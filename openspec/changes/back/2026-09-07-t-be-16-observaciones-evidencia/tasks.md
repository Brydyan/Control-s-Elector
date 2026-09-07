# Tasks: 2026-09-07-t-be-16-observaciones-evidencia

Origen: `documents/task-control/backendtask/T-BE-16.md`
Rama: `feat/t-be-16-observaciones`, sacada de `develop`.

---

## ⛔ NO EMPEZAR — bloqueo activo

**Este `tasks.md` no se ejecuta hasta que el usuario defina los valores de
`TipoObservacion`.** Ver `design.md` D0 y RF-OBS-00.

Los valores del task doc (`FOTO_BORROSA`, `DATOS_NO_COINCIDEN`, `FIRMA_FALTANTE`,
`NUMERO_INCORRECTO`, `OTRA`) son placeholders sacados de los mockups, no un set acordado.

Si estás leyendo esto como Builder y nadie te pasó las categorías definitivas: **parás y lo
decís.** No inventes valores para desbloquear. Un enum mal elegido es una migración de datos
sobre evidencias ya clasificadas, y la elección ocurre una sola vez.

También conviene ir **después** de `back/2026-09-07-t-be-14-storage-acta`: ese change repara
la compensación de S3 y mete `AuditLogService` en `EvidenciaService`, que es el mismo servicio
que aquí se extiende.

---

## A · Preparación (sólo cuando el bloqueo se levante)

- [ ] **A.1** — `git status --short` limpio. Línea base **sin pipe**:

  ```bash
  cd backend/electoral-api
  ./mvnw -B test > /tmp/base.log 2>&1; echo "exit=$?"
  ```

- [ ] **A.2** — Escribir el enum `TipoObservacion` con **los valores que dio el usuario**.
  Anotar en `apply-progress.md` cuáles son y de dónde salieron (mensaje, fecha).

- [ ] **A.3** — Comprobar si `EvidenciaService` ya tiene `AuditLogService` inyectado. Si
  T-BE-14 no se ejecutó todavía, inyectarlo aquí.

---

## B · Modelo y persistencia

- [ ] **B.1** — Migración Liquibase: tabla junction `evidencia_observacion`
  (`evidencia_id` FK, `tipo`, `creado_en`), **no** una columna `jsonb`.
  Ver `design.md` D1.

  **Trampa**: el changelog nuevo va **al final**, con el número siguiente, y no puede
  referenciar nada creado en un changelog posterior. Es la regla que este repo aprendió por
  las malas — ver `back/2026-09-07-fix-liquibase-rbac-order`.

- [ ] **B.2** — Restricción en la base que impida más de 2 filas por `evidencia_id`.

  Si el motor lo pone difícil y se descarta, **escribilo en `apply-progress.md`**: la
  garantía pasa a ser sólo de aplicación. Una limitación declarada es aceptable; una
  limitación escondida detrás de un comentario optimista, no.

- [ ] **B.3** — Relación en `Evidencia`. Lista **vacía por defecto, nunca `null`**
  (RF-OBS-05).

---

## C · API y validación

- [ ] **C.1** — `PATCH /api/v1/evidencias/{id}/observaciones`.

- [ ] **C.2** — Comprobación de **autoría** en el servicio: el `usuario_id` de la evidencia
  contra el del llamante (RF-OBS-01, `design.md` D3).

  **Trampa**: `@PreAuthorize("hasRole('OBSERVER')")` autoriza a cualquier OBSERVER, también
  al que no subió la foto. El task doc pone el rol en la anotación y menciona la autoría sólo
  de pasada, en el cuerpo del ejemplo. Hacen falta las dos.

- [ ] **C.3** — Validación de máximo 2 **en el servicio** (RF-OBS-02, RF-OBS-03).
  Decidir y documentar qué pasa con categorías repetidas.

- [ ] **C.4** — Auditar la operación (RF-OBS-04).

- [ ] **C.5** — Lectura para COORDINATOR, MASTER y ADMIN; escritura **no** para ADMIN
  (RF-OBS-06).

- [ ] **C.6** — Tests, uno por requisito RF-OBS-01 … RF-OBS-06.

---

## D · Las mutaciones

`cp` al `.bak`, mutar, correr, **anotar el nombre del test que cayó**, restaurar con `cp`,
`rm` del `.bak`, `git status --short` limpio. **Nunca `git checkout --`.**

- [ ] **D.1** — RF-OBS-01. Quitá la comprobación de autoría, dejando sólo el `@PreAuthorize`
  de rol. Debe caer el test de OBSERVER ajeno → 403.

- [ ] **D.2** — RF-OBS-02. Subí el límite a 5 en el servicio.
  Debe caer el test de 3 categorías → 400.

- [ ] **D.3** — RF-OBS-03. Quitá la validación del servicio, dejándola sólo en el setter.
  Debe caer el test que persiste saltándose el setter.

  Si ese test no existe, escribilo: la garantía es más débil de lo que el spec afirma.

- [ ] **D.4** — RF-OBS-04. Quitá la llamada a `AuditLogService`.
  Debe caer el test que cuenta filas de `audit_log`.

- [ ] **D.5** — RF-OBS-06. Añadí ADMIN a los roles del `PATCH`.
  Debe caer el test de ADMIN → 403.

---

## Qué NO hacer en esta fase

- **No** inventar los valores del enum para desbloquear. Es el bloqueo entero de este change.
- **No** usar `@Column(columnDefinition = "jsonb")`. Ata la entidad a PostgreSQL y deja
  «máximo 2» viviendo sólo en un setter de Java. Ver `design.md` D1 y D2.
- **No** apoyarse en RLS para el aislamiento entre OBSERVERs. Hoy no está demostrado que RLS
  esté actuando (ver `t-be-15-tests-integracion`, RF-TEST-05).
- **No** tocar el flujo de subida. Marcar observaciones es un paso posterior y opcional.
- **No** implementar la parte de `/novedades` que las expone: es un change aparte, después de
  que T-BE-13 y éste cierren.

---

## Compuertas antes de dar por terminada la fase

```
cd backend/electoral-api
./mvnw -B test > /tmp/after.log 2>&1; echo "exit=$?"

    0 errores de ApplicationContext
    total = línea base de A.1 + los tests de C.6  (contados, no estimados)
```

Y las cinco mutaciones de D anotadas con el nombre del test que cayó en cada una.
