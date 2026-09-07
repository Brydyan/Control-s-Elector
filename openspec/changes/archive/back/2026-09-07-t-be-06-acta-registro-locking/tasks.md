# Tasks: 2026-09-07-t-be-06-acta-registro-locking

Origen: `documents/task-control/backendtask/COMPLETADO-T-BE-06.md`
Rama: `verify/t-be-06-registro-locking`, sacada de `develop`.

**Change retrospectivo**: verifica, no construye.

**Bloqueado por** `back/2026-09-07-fix-liquibase-rbac-order`.
Los tests de concurrencia y de RLS los aporta `back/2026-09-07-t-be-15-tests-integracion`: si
no cerró, este change se limita a **medir** y **reportar**, sin escribirlos.

---

## A · Las dos garantías centrales

`cp` al `.bak`, mutar, correr, **anotar el nombre del test que cayó**, restaurar con `cp`,
`rm` del `.bak`, `git status --short` limpio. **Nunca `git checkout --`.**

- [ ] **A.1** — `git status --short` limpio. Línea base de la suite.

- [ ] **A.2** — RF-REG-01, el lock. Sustituí `findActaActivaConLock` por `findActaActiva` en
  `ActaService.registrar`. Debe caer el test de concurrencia.

  **Trampa**: dos llamadas secuenciales pasan igual sin lock — la segunda encuentra el acta
  de la primera con un `SELECT` normal. Si tu mutación no hace caer nada, la unicidad de acta
  no está probada, aunque exista un test con «locking» en el nombre.

- [ ] **A.3** — RF-REG-02, el invariante. Invertí el `!=` de la validación.
  Debe caer el test de suma inválida.

  Comprobar además que **no queda fila** en `acta` ni en `acta_detalle` tras el rechazo: si
  el acta se creó y sólo falló el detalle, la transacción no está haciendo su trabajo.

- [ ] **A.4** — RF-REG-04, auditoría. Quitá `auditLogService.registrar`.
  Debe caer el test que cuenta filas de `audit_log`.

- [ ] **A.5** — RF-REG-05, permiso JWT. Quitá
  `@PreAuthorize("@authz.has('acta:upload')")` de `ObserverController.registrarActa`.
  Debe caer el test de COORDINATOR o ADMIN → 403.

---

## B · Las tres preguntas que hay que responder midiendo

- [ ] **B.1** — RF-REG-05, **¿RLS está actuando?** Es la pregunta más importante de este
  change: la arquitectura de registro libre de JRV descansa en ella.

  Dentro de una petición autenticada, en la misma transacción que usan los repositorios:

  ```sql
  SELECT current_setting('app.current_user_id', true);
  ```

  **Es probable que devuelva `null`**: `RlsInterceptor` hace `SET LOCAL` desde un filtro de
  servlet, fuera de la transacción del repositorio, y `SET LOCAL` sólo vive dentro de la suya.

  - Si devuelve el id: RLS actúa. Anotalo — es una buena noticia que hoy nadie puede afirmar.
  - Si devuelve `null`: **CRITICAL**. El comentario de `ActaService:77-82` documenta una
    defensa que no existe. **No lo arregles aquí**: anotalo con su reproducción exacta y
    dejá que el Arquitecto decida si el permiso JWT basta o hace falta reponer otra defensa.

- [ ] **B.2** — RF-REG-06, candidatos de otro cargo. Reproducí el defecto:

  1. crear un cargo A (Alcalde) y un cargo B (Prefecto), con un candidato en cada uno;
  2. registrar un acta del cargo A con un detalle que referencie al candidato de B;
  3. comprobar que **se acepta**.

  Si se acepta, es un hallazgo con reproducción. Anotalo. **No lo arregles**: añadir esa
  validación cambia el comportamiento de `registrar` y necesita su propia verificación —
  incluido qué pasa con las actas ya guardadas que la incumplan.

- [ ] **B.3** — RF-REG-03, el estado BORRADOR. Comprobar si **algún** camino produce un acta
  en BORRADOR:

  ```bash
  grep -rn "BORRADOR" backend/electoral-api/src/main/java
  ```

  Si no lo produce nadie: proponer quitarlo del enum, o documentar para qué flujo futuro se
  reserva. Un estado inalcanzable es una promesa de comportamiento que no existe.

---

## Qué NO hacer en esta fase

- **No** arreglar RLS. Medí (B.1) y reportá. Cambiar un control de acceso es un change con
  su propia verificación, y este es retrospectivo.
- **No** añadir la validación candidato/cargo. Reproducila (B.2) y reportala.
- **No** tocar el ciclo de vida posterior (disputa, corrección, aprobación, anulación):
  es T-BE-07.
- **No** quitar `asignacionRepository` del constructor aquí. Está asignado a
  `t-be-04-repositorios` B.2, que ya decide qué pasa con la entidad entera.
- **No** dar por buena la garantía del lock porque el `@Lock` está escrito. Un `@Lock`
  invocado fuera de transacción no bloquea nada y no da ningún error.

---

## Compuertas antes de dar por terminada la fase

```
cd backend/electoral-api
./mvnw -B test > /tmp/after.log 2>&1; echo "exit=$?"
    0 errores de ApplicationContext
    total: no baja respecto de la línea base
```

Las cuatro mutaciones de A anotadas con el nombre del test que cayó, y las tres preguntas de
B respondidas con su medición.

**Si A.2 no hizo caer ningún test, decilo en la primera línea de `apply-progress.md`.** Es la
garantía que impide dos resultados oficiales para la misma mesa.
