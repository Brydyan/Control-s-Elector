# Tasks: 2026-09-07-t-be-13-dashboard

Origen: `documents/task-control/backendtask/T-BE-13.md`
Rama: `feat/t-be-13-dashboard`, sacada de `develop`.

**Bloqueado por** `back/2026-09-07-fix-liquibase-rbac-order`. Sin contexto de Spring, los
tests que escribas aquí no se distinguen de los 78 que ya fallan.

Orden obligatorio: **A antes que B antes que C antes que D.** A descubre qué existe de
verdad; sin eso, B se escribe contra métodos imaginarios.

---

## A · Averiguar qué hay, antes de escribir nada

- [ ] **A.1** — Confirmar qué de esto **existe realmente**. El task doc los da por hechos y
  ya se equivocó con otras tareas:

  ```
  CantonRepository                              ¿existe?
  JrvRepository.countByParroquia_Canton(Canton) ¿existe?
  ActaRepository.countByEstado(EstadoActa)      existe (verificado 2026-09-07)
  ActaRepository.findByEstado(EstadoActa)       existe (verificado 2026-09-07)
  Acta.fechaRegistro                            ¿cómo se llama? findWithFilters ordena por
                                                a.fechaRegistro, pero el doc dice registradaEn
  Acta.motivoDisputa                            ¿existe? el doc lo llama getMotivo_disputa()
  Usuario.nombre / Usuario.apellido             añadidos en 007-usuario-nombre-apellido.sql
  ```

  Anotar en `apply-progress.md` cuáles existen y con qué nombre exacto. **Los que falten se
  crean en B.1**, no se asumen.

  **Trampa**: el task doc mezcla nombres de dos épocas del código
  (`registrada_por`, `getMotivo_disputa()`, paquete `ec.twintailcs.electoral.dto`). El
  paquete real es `ec.twintailcs.electoral.<módulo>.web.dto`.

---

## B · Queries de agregación

- [ ] **B.1** — Crear lo que falte de A.1 (`CantonRepository`, el `countBy...` de JRV).

- [ ] **B.2** — En `ActaDetalleRepository`: query de votos por candidato agrupada por cargo,
  **con `WHERE d.acta.estado != 'ANULADA'`**, ordenada por cargo y votos descendente.

- [ ] **B.3** — En `ActaRepository`: `COUNT(DISTINCT a.jrv.id)` agrupado por
  `a.jrv.parroquia.canton.id`, **con el filtro de ANULADA**.

  El `DISTINCT` no es opcional: una JRV con actas de 6 cargos debe contar una vez.

- [ ] **B.4** — Comprobar el nombre de la columna de ordenación. `findWithFilters` usa
  `a.fechaRegistro`; el modelo de `backend.md:46` dice `registrada_en`. Uno de los dos está
  mal. Averiguar cuál **ejecutando**, no leyendo.

---

## C · Controlador, DTOs y tests

- [ ] **C.1** — DTOs en `ec.twintailcs.electoral.dashboard.web.dto`:
  `ResumenActasDto`, `ResultadoCargoDto`, `ProgresoCantonDto`, `NovedadDto`.

  `NovedadDto` lleva `tipo` desde el principio (hoy siempre `"ACTA_EN_DISPUTA"`), para que
  añadir evidencias en el futuro sea aditivo. Ver `design.md` D3.

  **Trampa**: en `ProgresoCantonDto`, el porcentaje se calcula con `100.0`, no `100`.
  Con enteros da siempre 0. Y `totalJrvs == 0` debe dar `0`, no división por cero.

- [ ] **C.2** — `DashboardController` con los cuatro `@GetMapping` y
  `@PreAuthorize("hasAnyRole('ADMIN','MASTER','COORDINATOR')")` de clase.
  **Sólo GET.** Ver `design.md` D2.

- [ ] **C.3** — `minutosTranscurridos` con `Instant.now()`, **sin** `ZoneId.systemDefault()`.
  Ver `design.md` D4 y RF-DASH-07.

- [ ] **C.4** — Tests, uno por requisito del spec:
  - RF-DASH-01: OBSERVER → 403; los otros tres roles → 200; sin token → 401
  - RF-DASH-02: por reflexión, ningún método con anotación de escritura
  - RF-DASH-03: `/resumen` no cuenta la ANULADA
  - RF-DASH-04: dos actas (una APROBADA de 90, una ANULADA de 500) → 90
  - RF-DASH-05: cantón 5 JRVs / 2 con acta → 40.0 · JRV con 3 cargos cuenta 1 · cantón vacío → 0
  - RF-DASH-06: sólo aparecen las EN_DISPUTA
  - RF-DASH-07: mismo número con `TZ=UTC` y `TZ=America/Guayaquil`
  - RNF-DASH-01: base vacía → cuatro `200`

---

## D · Las mutaciones

Cada una: `cp` al `.bak`, mutar, correr, **anotar el nombre del test que cayó**, restaurar
con `cp`, `rm` del `.bak`, `git status --short` limpio. **Nunca `git checkout --`.**

- [ ] **D.1** — RF-DASH-04. Quitá `WHERE d.acta.estado != 'ANULADA'` de la query de B.2.
  Debe caer el test de los 90 vs 590 votos.

  Si no cae ninguno, ese test es decorativo — y es el test que impide publicar un resultado
  electoral falso. Rehacelo antes de seguir.

- [ ] **D.2** — RF-DASH-05, dos mutaciones separadas:
  - `COUNT(DISTINCT a.jrv.id)` → `COUNT(a.jrv.id)`: debe caer el test de la JRV con 3 cargos
  - quitar el filtro de ANULADA: debe caer el test de la JRV con acta anulada

- [ ] **D.3** — RF-DASH-03. Sumá `countByEstado(ANULADA)` a `totalActivas`.
  Debe caer el test de exclusión en `/resumen`.

- [ ] **D.4** — RF-DASH-01. Quitá el `@PreAuthorize` de clase.
  Debe caer el test de OBSERVER → 403.

- [ ] **D.5** — RF-DASH-07. Reintroducí el `ZoneId.systemDefault()` del task doc.
  Debe caer el test de las dos zonas horarias.

  ```bash
  TZ=UTC              ./mvnw -B test -Dtest=DashboardControllerTest > /tmp/a.log 2>&1
  TZ=America/Guayaquil ./mvnw -B test -Dtest=DashboardControllerTest > /tmp/b.log 2>&1
  ```

- [ ] **D.6** — RF-DASH-02. Añadí un `@PostMapping` de mentira al controlador.
  Debe caer el test de reflexión. Quitalo.

---

## Qué NO hacer en esta fase

- **No** implementar realtime. Es T-IN-06 + T-FE-11.
- **No** inventar los valores del enum `TipoObservacion` para completar `/novedades`.
  Está bloqueado por una decisión de producto, no por código. Ver `design.md` D3.
- **No** añadir paginación sin haber medido que hace falta. Ver `design.md` D5.
- **No** filtrar ANULADA en Java «por si acaso» además de en la query: si el test de
  exclusión pasa con la query rota, la mutación D.1 no detecta nada y la garantía queda sin
  probar.
- **No** tocar el frontend.

---

## Compuertas antes de dar por terminada la fase

```
cd backend/electoral-api
./mvnw -B test > /tmp/after.log 2>&1; echo "exit=$?"

    0 errores de ApplicationContext
    total de tests = línea base + los de C.4 (cuéntalos, no los estimes)

TZ=UTC ./mvnw -B test -Dtest=DashboardControllerTest > /tmp/utc.log 2>&1; echo "exit=$?"
```

Comparar contra la línea base del change bloqueante. Si el total no sube al añadir los tests
de C.4, los tests nuevos no se están ejecutando: es un fallo de la fase, no un detalle.
