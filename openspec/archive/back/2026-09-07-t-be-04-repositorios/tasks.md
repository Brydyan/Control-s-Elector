# Tasks: 2026-09-07-t-be-04-repositorios

Origen: `documents/task-control/backendtask/COMPLETADO-T-BE-04.md`
Rama: `verify/t-be-04-repositorios`, sacada de `develop`.

**Change retrospectivo**: verifica, no construye. Excepción: B, que retira código muerto.

**Bloqueado por** `back/2026-09-07-fix-liquibase-rbac-order`.
El test de concurrencia de A.2 lo aporta `back/2026-09-07-t-be-15-tests-integracion`: si ese
change no cerró todavía, A.2 se limita a comprobar que el test **no existe** y anotarlo.

---

## A · Verificar por mutación

`cp` al `.bak`, mutar, correr, **anotar el nombre del test que cayó**, restaurar con `cp`,
`rm` del `.bak`, `git status --short` limpio. **Nunca `git checkout --`.**

- [ ] **A.1** — `git status --short` limpio. Línea base de la suite.

- [ ] **A.2** — RF-REP-01, el lock. Sustituí la llamada de `ActaService.registrar` por
  `findActaActiva` (la gemela **sin** `@Lock`).

  Debe caer el test de concurrencia.

  **Trampa**: un test con dos llamadas secuenciales pasa igual sin lock — la segunda
  encuentra el acta de la primera con un `SELECT` normal. Si tu mutación no hace caer nada,
  la garantía de unicidad no está probada, por mucho que exista un test con «locking» en el
  nombre.

- [ ] **A.3** — RF-REP-02. Quitá `AND a.estado != 'ANULADA'` de `findActaActiva` y
  `findActaActivaConLock`. Debe caer el test de re-registro tras anulación.

- [ ] **A.4** — RF-REP-03. Quitá la cláusula `(:registradaPorId IS NULL OR ...)`.
  Debe caer el test de filtrado por OBSERVER.

  Repetí con las otras tres cláusulas: son cuatro filtros y cada uno necesita su test. Es
  §4.2 — la misma regla cuatro veces, y basta con que una no esté cubierta.

---

## B · Retirar lo muerto

- [ ] **B.1** — RF-REP-06. `RefreshTokenRepository`:

  ```bash
  grep -rn "RefreshTokenRepository\|RefreshToken" backend/electoral-api/src/main/java
  ```

  Se resuelve junto con la entidad en `t-be-01-entidades-base` tarea B.3. Si ese change ya
  cerró, comprobar que el repositorio se fue con ella.

- [ ] **B.2** — RF-REP-06. `ObserverJrvAsignacionRepository`, marcado `@Deprecated`.

  `ActaService` sigue recibiéndolo por constructor (`asignacionRepository`) **sin usarlo**:
  una dependencia muerta en el servicio más crítico del sistema.

  Decidir **una**:
  - borrar el repositorio, la entidad, la dependencia del constructor y la tabla (changelog
    Liquibase nuevo, **al final**); **o**
  - documentar en el propio archivo por qué se conserva y quién lo va a usar.

  Anotar la decisión en `apply-progress.md`. Un `@Deprecated` sin fecha de retirada es una
  invitación a que alguien lo use.

- [ ] **B.3** — RF-REP-05, orden estable. Comprobar si `findWithFilters` ordena sólo por
  fecha. Si sí, anotarlo como riesgo diferido: **no lo arregles ahora** —sin paginación no es
  un defecto— pero dejalo escrito para que T-BE-13 y el frontend lo tengan delante cuando
  paginen.

---

## Qué NO hacer en esta fase

- **No** añadir las queries de dashboard: son T-BE-13.
- **No** tocar la lógica de `ActaService`: es T-BE-06. Aquí se muta y se restaura.
- **No** implementar el filtro por cantón: se decide en `t-be-12-cierre-coordinacion` C.2.
- **No** borrar `ObserverJrvAsignacion` sin correr antes el `grep`. La decisión de que el
  OBSERVER elige JRV libremente está tomada, pero hay que confirmar que nada la usa todavía.
- **No** editar `006-geografia.sql` en su sitio para borrar `refresh_token`. El changelog de
  borrado va al final, con el número siguiente.

---

## Compuertas antes de dar por terminada la fase

```
cd backend/electoral-api
./mvnw -B test > /tmp/after.log 2>&1; echo "exit=$?"
    0 errores de ApplicationContext
    total: no baja respecto de la línea base
```

Las mutaciones de A anotadas con el nombre del test que cayó. Si A.2 no hizo caer nada,
decilo explícitamente: es el hallazgo más importante que puede salir de este change.
