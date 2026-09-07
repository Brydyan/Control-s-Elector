# Tasks: 2026-09-07-t-be-02-entidades-electorales

Origen: `documents/task-control/backendtask/COMPLETADO-T-BE-02.md`
Rama: `verify/t-be-02-entidades-electorales`, sacada de `develop`.

**Change retrospectivo**: verifica, no construye. Excepción: B.2, si RF-ELE-05 resulta ser un
hueco real.

**Bloqueado por** `back/2026-09-07-fix-liquibase-rbac-order`.

---

## A · Verificar por mutación

`cp` al `.bak`, mutar, correr, **anotar el nombre del test que cayó**, restaurar con `cp`,
`rm` del `.bak`, `git status --short` limpio. **Nunca `git checkout --`.**

- [ ] **A.1** — `git status --short` limpio. Correr `ConfiguracionServiceTest` y anotar.
  Esperado: 7 tests en verde (es de las pocas suites que ya pasaban).

- [ ] **A.2** — RF-ELE-02. Hacé que `findByJrvIdAndActivo` ignore el flag `activo`.
  Debe caer un test que desactive un `JrvCargo` y compruebe que deja de listarse.

  Si no existe ese test, escribilo: `ObserverController.cargosDeJrv` y el cálculo de
  pendientes dependen de él.

- [ ] **A.3** — RF-ELE-04. Hacé opcional la FK de `Candidato` a `Cargo`.
  Debe caer el test de candidato sin cargo.

- [ ] **A.4** — RF-ELE-06. Insertar dos veces el mismo par (JRV, cargo) debe fallar.

- [ ] **A.5** — RNF-ELE-01. Cambiá un mapper para exponer el `id` interno.
  Debe caer un test de forma del DTO.

---

## B · Los dos huecos

- [ ] **B.1** — RF-ELE-03, coherencia nivel ↔ geografía.

  1. Comprobar si **algún** código impide asignar un cargo de nivel PARROQUIA a una JRV en
     parroquia URBANA:

     ```bash
     grep -rn "NivelCargo\|getNivel()" backend/electoral-api/src/main/java
     ```

  2. Comprobar qué hace realmente el seed:

     ```bash
     grep -n -A5 "jrv_cargo" backend/electoral-api/src/main/resources/database/infra/sql/03-seed.sql
     ```

     El comentario de `infra.md` T-IN-04 dice «todos los cargos en todas las JRVs (cross
     join)». Verificá si el SQL hace eso de verdad — el comentario puede mentir en cualquiera
     de las dos direcciones.

  3. Decidir **una** y hacerla completa:
     - validar en el endpoint de configuración de MASTER (T-BE-10) y abrir allí el change; **o**
     - declarar por escrito que la coherencia es responsabilidad del MASTER y que el seed de
       dev no la respeta a propósito.

  Anotar la decisión en `apply-progress.md`. Lo que no vale es dejar una regla en `CLAUDE.md`
  que ningún código aplica y un seed que la contradice en silencio.

- [ ] **B.2** — RF-ELE-05, candidatos de otro cargo.

  Comprobar si `ActaService.registrar` valida que cada `candidatoId` pertenezca al
  `cargoId` del acta. Por lo leído el 2026-09-07, **no lo valida**: resuelve el candidato por
  id y lo asocia sin más.

  - Si no valida: **es un hallazgo**. Anotalo con su reproducción (registrar un acta de
    Alcalde con votos de un candidato a Prefecto: la suma cuadra, la validación pasa, el
    dashboard atribuye mal). **No lo arregles aquí** — proponelo, porque tocar
    `ActaService.registrar` pertenece a T-BE-06 y necesita su propia verificación.
  - Si valida: mutá quitando la comprobación y comprobá que cae su test.

---

## Qué NO hacer en esta fase

- **No** reescribir las entidades.
- **No** tocar `ActaService`: es T-BE-06 / T-BE-07.
- **No** tocar los endpoints de configuración: es T-BE-10.
- **No** arreglar el seed sin haber decidido B.1.3. Cambiar datos de prueba sin decidir dónde
  vive la regla sólo mueve el problema.

---

## Compuertas antes de dar por terminada la fase

```
cd backend/electoral-api
./mvnw -B test > /tmp/after.log 2>&1; echo "exit=$?"
    ConfiguracionServiceTest: 7 tests + los que añadas, 0 error
    total: no baja respecto de la línea base
```

Las mutaciones de A anotadas con el nombre del test que cayó en cada una.
