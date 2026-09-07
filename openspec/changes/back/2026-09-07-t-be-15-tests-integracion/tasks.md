# Tasks: 2026-09-07-t-be-15-tests-integracion

Origen: `documents/task-control/backendtask/T-BE-15.md`
Rama: `test/t-be-15-integracion`, sacada de `develop`.

**Bloqueado por** `back/2026-09-07-fix-liquibase-rbac-order`. Este change **empieza** con esa
suite en verde: si no, un test nuevo que falla no se distingue de los 78 que ya fallaban.

Orden obligatorio: **A antes que B antes que C.** El arnés (A) lo usan todos los demás.

---

## A · El arnés

- [ ] **A.1** — `git status --short` limpio. Medir la línea base **sin pipe** y anotarla:

  ```bash
  cd backend/electoral-api
  ./mvnw -B test > /tmp/base.log 2>&1; echo "exit=$?"
  ```

  Contar desde `target/surefire-reports/*.xml`. Es el número contra el que se compara al
  final: si no sube, los tests nuevos no se ejecutan.

- [ ] **A.2** — Contenedor PostgreSQL 16 compartido por suite (`@Testcontainers` con campo
  `static`), con Liquibase aplicando el changelog completo.

  **No añadas H2 al `pom.xml`.** `design.md` D1 explica por qué el task doc se equivoca.
  Testcontainers ya está en `pom.xml`: hay que usarlo, no añadir nada.

- [ ] **A.3** — Arnés de datos: provincia → cantón → parroquia → JRV → cargo → candidatos →
  usuarios de los cuatro roles. Devuelve los ids que el test necesita.

- [ ] **A.4** — **Preguntale al arnés qué NO limpia.** Comprobá explícitamente que trunca
  `audit_log` y `notificacion` entre tests.

  ```bash
  grep -n "truncate\|deleteAll\|@Sql" <arnés>
  ```

  Si no las limpia, los tests que cuentan filas de auditoría dependerán del orden de
  ejecución. Esa pregunta encontró dos defectos en el proyecto de referencia.

---

## B · Los seis tests

Uno por requisito del spec. Cada uno con su mutación en C.

- [ ] **B.1** — RF-TEST-01, unicidad bajo **concurrencia real**: dos hilos con
  `CountDownLatch` para arrancar a la vez, timeout explícito. Uno gana, el otro recibe
  `JrvCargoYaRegistradoException`, y en la base queda **una** fila activa.

  Segundo escenario: con un acta ANULADA para ese par, registrar debe permitirse.

  **Trampa**: dos llamadas secuenciales pasan igual **sin ningún lock**. Si tu test no
  distingue una cosa de la otra, C.1 no va a caer y no vas a saber por qué.

- [ ] **B.2** — RF-TEST-02, invariante de votos hasta el HTTP, en `registrar` **y** en
  `corregir`. Comprobar además que no queda fila en `acta` ni en `acta_detalle`.

- [ ] **B.3** — RF-TEST-03, ciclo completo en **un solo test**:
  ENVIADA → EN_DISPUTA → CORREGIDA → APROBADA → ANULADA, verificando `audit_log` y
  `notificacion` en cada paso, y que desde ANULADA no hay salida.

- [ ] **B.4** — RF-TEST-04, aislamiento entre OBSERVERs: listado y corrección ajena.

- [ ] **B.5** — RF-TEST-05, **RLS realmente activa**. Dentro de una petición autenticada,
  en la misma transacción que usan los repositorios:

  ```sql
  SELECT current_setting('app.current_user_id', true);
  ```

  Debe devolver el id interno, no `null`.

  **Es probable que devuelva `null`.** `RlsInterceptor` hace `SET LOCAL` desde un filtro de
  servlet —fuera de la transacción del repositorio— y se traga cualquier excepción. Si sale
  `null`, **no lo arregles aquí**: anotalo como CRITICAL en `apply-progress.md` con la
  reproducción exacta. El comentario de `ActaService:77-82` estaría documentando una defensa
  inexistente, y eso se decide con el Arquitecto.

- [ ] **B.6** — RF-TEST-06, filtro `registradaPorId` en el listado global.

---

## C · Las mutaciones — sin esto, B es decorativo

`cp` al `.bak`, mutar, correr, **anotar el nombre del test que cayó**, restaurar con `cp`,
`rm` del `.bak`, `git status --short` limpio. **Nunca `git checkout --`.**

- [ ] **C.1** — RF-TEST-01. `findActaActivaConLock` → `findActaActiva` en
  `ActaService.registrar`. Debe caer B.1.

  Si no cae: tu test es secuencial. Rehacelo antes de seguir — es la garantía que impide dos
  resultados oficiales para la misma mesa.

- [ ] **C.2** — RF-TEST-02. Invertí el `!=` del invariante en `registrar`.
  Debe caer B.2. **Repetí en `corregir`**: son dos sitios con la misma regla, y hay que
  comprobar que los dos están protegidos (§4.2).

- [ ] **C.3** — RF-TEST-03. Ampliá el guard de `aprobar` para aceptar EN_DISPUTA.
  Debe caer B.3.

- [ ] **C.4** — RF-TEST-04. Quitá la comprobación de `registradaPor` en `corregir`.
  Debe caer el test de corrección ajena.

- [ ] **C.5** — RF-TEST-04 / 05. Desactivá `RlsInterceptor` (quitalo de `SecurityConfig`).
  Debe caer el test de aislamiento del listado.

  **Si no cae, RLS no estaba haciendo nada** y B.5 ya lo habrá mostrado. Anotá las dos
  observaciones juntas: son la misma conclusión por dos caminos.

- [ ] **C.6** — RF-TEST-06. Quitá `(:registradaPorId IS NULL OR ...)` de `findWithFilters`.
  Debe caer B.6.

---

## Qué NO hacer en esta fase

- **No** añadir H2 al `pom.xml` aunque el task doc lo pida. Ver `design.md` D1.
- **No** arreglar lo que encuentres en B.5. Si RLS no actúa, es un CRITICAL que se **reporta**
  con su reproducción. Arreglarlo aquí mezcla el diagnóstico con la cura.
- **No** borrar el test de concurrencia si sale intermitente. Arreglá la sincronización: es
  el único que prueba la unicidad.
- **No** perseguir un porcentaje de cobertura. Seis garantías probadas de verdad valen más
  que un número.
- **No** usar el seed de Liquibase como datos de prueba. Cambia por razones ajenas al test.

---

## Compuertas antes de dar por terminada la fase

```
cd backend/electoral-api
./mvnw -B test > /tmp/after.log 2>&1; echo "exit=$?"

    0 errores de ApplicationContext
    total = línea base de A.1 + los tests de B  (CONTADOS, no estimados)
    0 failures
```

Y las seis mutaciones de C anotadas con **el nombre del test que cayó en cada una**.

Una mutación que sólo consta en un `.md` no es evidencia. El Verificador las va a volver a
correr por su cuenta.
