# Tasks: 2026-09-07-t-be-12-cierre-coordinacion

Origen: `documents/task-control/backendtask/T-BE-12.md`
Rama: `fix/t-be-12-cierre`, sacada de `develop`.

**Bloqueado por** `back/2026-09-07-fix-liquibase-rbac-order`. Sin contexto de Spring, los 11
tests dan ERROR y ninguna mutación de abajo significa nada. No empieces hasta que esa suite
esté en verde.

Orden obligatorio: **A antes que B antes que C.**

---

## A · Confirmar que hay algo que verificar

- [ ] **A.1** — `git status --short` limpio.

- [ ] **A.2** — Correr sólo esta suite, **sin pipe**:

  ```bash
  cd backend/electoral-api
  ./mvnw -B test -Dtest=CoordinatorControllerTest > /tmp/coord.log 2>&1; echo "exit=$?"
  ```

  Anotar en `apply-progress.md`: tests / failures / errors leídos del XML de
  `target/surefire-reports/`. **Esperado tras el change bloqueante: 11 tests, 0 error.**
  Si siguen en ERROR, parar: el bloqueante no cerró.

- [ ] **A.3** — Contar los endpoints reales de `CoordinatorController` y compararlos con los
  del spec. Son 6. Si no son 6, el spec está desactualizado y hay que arreglarlo **antes** de
  seguir, no después.

---

## B · Las mutaciones — una por garantía

Cada una: `cp` del archivo, mutar, correr, **anotar el nombre del test que cayó**, restaurar
con `cp`, `rm` del `.bak`, `git status --short` limpio.
**Nunca `git checkout --`.**

- [ ] **B.1** — RF-COORD-02 (sólo MASTER anula). Quitá
  `@PreAuthorize("hasRole('MASTER')")` del método `anular` en `CoordinatorController`.
  Debe caer el test de COORDINATOR → 403.

  **Trampa**: el `@PreAuthorize` de clase permite COORDINATOR. Si el de método no está, el
  de clase lo autoriza y el 403 desaparece **sin error de compilación**. Es el fallo que
  esta mutación existe para detectar.

- [ ] **B.2** — RF-COORD-03 (sólo APROBADAS se anulan). Quitá el guard
  `if (acta.getEstado() != EstadoActa.APROBADA)` de `ActaService.anular`.
  Debe caer el test de anular un acta ENVIADA.

- [ ] **B.3** — RF-COORD-04 (máquina de estados). En `ActaService.aprobar`, ampliá el guard
  para aceptar también `EN_DISPUTA`.
  Debe caer el test de aprobar un acta en disputa.

- [ ] **B.4** — RF-COORD-05 (motivo obligatorio). Quitá `@NotBlank` de `DisputaDto.motivo`.
  Debe caer el test de motivo vacío → 400.

- [ ] **B.5** — RF-COORD-01 (RBAC de módulo). Quitá el `@PreAuthorize` de clase.
  Debe caer el test de ADMIN → 403.

- [ ] **B.6** — **Si alguna de B.1…B.5 no hace caer ningún test**, esa garantía no está
  probada. Anotalo en `apply-progress.md` como hueco, con el número de la garantía, y
  escribí el test que falta. No lo dejes pasar: es exactamente lo que estas casillas existen
  para encontrar.

---

## C · Los dos huecos conocidos

- [ ] **C.1** — RF-COORD-06 (auditoría). Comprobá si existe **algún** test que verifique que
  disputar/aprobar/anular escriben en `audit_log`.

  Si no existe: escribilo (contar filas de `audit_log` antes y después), y después mutá
  quitando `auditLogService.registrar` de `ActaService.aprobar` para comprobar que cae.

  **Es probable que no exista.** La auditoría es una regla dura de `CLAUDE.md` y hoy nada
  la protege.

- [ ] **C.2** — RF-COORD-07 (filtro por cantón). `backend.md:192` lo promete;
  `ActaRepository.findWithFilters` no lo tiene.

  Decidí **una** de las dos y hacela completa:
  - **implementarlo**: añadir `cantonId` a `findWithFilters` filtrando por
    `a.jrv.parroquia.canton.id`, exponerlo como `@RequestParam` opcional, y su test;
  - **retirarlo**: quitar la promesa de `backend.md:192`.

  Anotá en `apply-progress.md` cuál elegiste y por qué. Lo que no vale es dejarlo como está.

---

## D · Alinear las tres fuentes

- [ ] **D.1** — Renombrar `documents/task-control/backendtask/T-BE-12.md` a
  `COMPLETADO-T-BE-12.md` (convención del repo).

- [ ] **D.2** — Corregir la cabecera del doc: hoy dice «❌ NO IMPLEMENTADO — Falta
  CoordinatorController» y el controlador existe desde el PR #66.

  Poné el estado **con la medición al lado**: «✅ COMPLETADO — 11 tests en verde, 5 garantías
  verificadas por mutación (ver `openspec/changes/archive/…`)». Una casilla sin medición no
  es evidencia — es lo que pasó con T-IN-11.

- [ ] **D.3** — Anotar en el doc las divergencias reales de rutas, para que quien lo lea como
  tutorial no escriba código contra la API equivocada:
  `/api/v1/coordinator` (no `/api/coordinator`), `anular` devuelve `204`,
  `findWithFilters` tiene 4 parámetros.

---

## Qué NO hacer en esta fase

- **No** reescribir `CoordinatorController` para que se parezca al doc. El código es la
  autoridad; el doc es el que está mal.
- **No** cambiar `ActaService`: su ciclo de vida pertenece a T-BE-07.
- **No** arreglar tests de otras suites que fallen. Anotalos y seguí.
- **No** marcar D.1/D.2 antes de que B y C estén cerradas. Renombrar a `COMPLETADO-` sin
  verificación es exactamente el defecto que este change repara.

---

## Compuertas antes de dar por terminada la fase

```
./mvnw -B test -Dtest=CoordinatorControllerTest > /tmp/coord.log 2>&1; echo "exit=$?"
    11 tests + los que hayas añadido en B.6 / C.1 / C.2 · 0 error · 0 failure

./mvnw -B test > /tmp/full.log 2>&1; echo "exit=$?"
    el total NO baja respecto de la línea base del change bloqueante
```

Si el total de tests no sube al añadir tests, los tests nuevos no se están ejecutando.
