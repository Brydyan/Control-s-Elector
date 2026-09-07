# Tasks: 2026-09-07-t-be-10-configuracion-preelectoral

Origen: `documents/task-control/backendtask/COMPLETADO-T-BE-10.md`
Rama: `verify/t-be-10-configuracion`, sacada de `develop`.

**Change retrospectivo**: verifica. Excepción: C.1, si se decide implementar el endpoint de
`JRV_CARGO`.

**Bloqueado por** `back/2026-09-07-fix-liquibase-rbac-order`.

---

## A · Verificar por mutación

`cp` al `.bak`, mutar, correr, **anotar el nombre del test que cayó**, restaurar con `cp`,
`rm` del `.bak`, `git status --short` limpio. **Nunca `git checkout --`.**

- [ ] **A.1** — `git status --short` limpio. Línea base:

  ```
  ConfiguracionServiceTest    7 tests   (esperado: ya en verde)
  CargoControllerTest        10 tests
  PartidoControllerTest       9 tests
  CandidatoControllerTest    10 tests
  ```

  Esperado tras el change bloqueante: **36 tests, 0 error**. Si no, parar.

- [ ] **A.2** — RF-CFG-01, **tres mutaciones, una por controlador**. Quitá el
  `@PreAuthorize("hasRole('MASTER')")` de un endpoint de escritura de `CargoController`,
  después de `PartidoController`, después de `CandidatoController`.

  Cada una debe hacer caer su test de → 403.

  **Es la misma regla escrita tres veces.** Si sólo dos tienen test, la tercera está
  desprotegida y nadie lo sabe (§4.2).

- [ ] **A.3** — RF-CFG-04. Quitá la comprobación de existencia del cargo en el alta de
  candidato. Debe caer el test de cargo inexistente.

- [ ] **A.4** — RF-CFG-05. Cambiá un mapper para exponer el `id` interno.
  Debe caer un test de forma del DTO.

- [ ] **A.5** — RF-CFG-02. Recorrer endpoint por endpoint y anotar en `apply-progress.md`
  cuáles son de lectura y cuáles de escritura, y qué guard lleva cada uno.

  **Lo que hay que buscar**: un endpoint de escritura sin guard, o uno de lectura con guard
  de MASTER que impida a un OBSERVER cargar los candidatos que necesita para registrar.

---

## B · Los dos huecos que se miden

- [ ] **B.1** — RF-CFG-07, auditoría:

  ```bash
  grep -rn "AuditLogService" \
       backend/electoral-api/src/main/java/ec/twintailcs/electoral/electoral
  ```

  Si sale vacío, la configuración preelectoral no deja rastro. Anotalo con esa evidencia.

  Decidir si se añade aquí o se abre un change. **Cambiar la papeleta en mitad del proceso
  es lo que alguien va a querer reconstruir después**, y a diferencia de las actas no hay un
  histórico del que deducirlo.

- [ ] **B.2** — RF-CFG-08, borrado. Reproducí: crear un cargo → registrar un acta de ese
  cargo → intentar borrar el cargo.

  - Si se impide o se desactiva: bien. Mutá quitando la protección y comprobá que cae su test.
  - Si se borra y deja el acta colgando: es un hallazgo. Anotalo con la reproducción.
    **No lo arregles aquí**: cambiar el borrado afecta a los tres CRUD.

- [ ] **B.3** — Comprobar si `POST /usuarios` y `GET /observers` existen
  (`backend.md:166-167`). El alta de usuarios vive en `POST /api/v1/auth/register` (T-BE-09),
  no bajo `/usuarios`.

  Corregir `backend.md` para que apunte a la ruta real, o anotar qué falta.

---

## C · El endpoint que falta

- [ ] **C.1** — RF-CFG-06, `JRV_CARGO`. Confirmá primero que no existe:

  ```bash
  grep -rn "jrvs.*cargos\|JrvCargo" \
       backend/electoral-api/src/main/java/ec/twintailcs/electoral/electoral/web
  ```

  Decidí **una** y hacela completa:

  - **Implementarlo**: `POST /api/v1/jrvs/{jrvId}/cargos`, sólo MASTER, con auditoría, flag
    `activo`, y tests (MASTER asigna / no-MASTER → 403 / asignación duplicada). **O**
  - **Retirar la promesa** de `backend.md:164` — y escribir cómo se corrige entonces la
    configuración el día antes de la elección, porque hoy la única vía es SQL a mano.

  **Antes de implementarlo, resolvé la pregunta de `t-be-02-entidades-electorales` B.1**:
  ¿este endpoint valida la coherencia nivel ↔ geografía (RF-CFG-03), o no? Si la validación
  no vive aquí, no vive en ninguna parte: éste es el único punto por el que debería entrar
  esa relación.

  Anotá la decisión y su motivo.

---

## Qué NO hacer en esta fase

- **No** tocar el alta de usuarios: es T-BE-09.
- **No** implementar la validación nivel ↔ geografía sin haber decidido dónde vive
  (`t-be-02-entidades-electorales` B.1). Meterla en dos sitios es peor que en ninguno.
- **No** cambiar el comportamiento de borrado a partir de B.2. Reportalo.
- **No** cerrar C.1 con «retirar la promesa» sin escribir cómo se corrige la configuración a
  última hora. Quitar una capacidad es válido; dejar el escenario sin respuesta, no.

---

## Compuertas antes de dar por terminada la fase

```
cd backend/electoral-api
./mvnw -B test > /tmp/after.log 2>&1; echo "exit=$?"
    ConfiguracionServiceTest + los tres ControllerTest: 36 tests + añadidos, 0 error
    total: no baja respecto de la línea base
```

Las mutaciones de A anotadas con el nombre del test que cayó — **las tres de A.2 por
separado**, no una sola línea diciendo «RBAC verificado».
