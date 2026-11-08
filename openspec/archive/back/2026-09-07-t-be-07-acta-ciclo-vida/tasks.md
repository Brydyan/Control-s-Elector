# Tasks: 2026-09-07-t-be-07-acta-ciclo-vida

Origen: `documents/task-control/backendtask/COMPLETADO-T-BE-07.md`
Rama: `verify/t-be-07-ciclo-vida`, sacada de `develop`.

**Change retrospectivo**: verifica. Excepción: C.2, que cierra el hueco de auditoría.

**Bloqueado por** `back/2026-09-07-fix-liquibase-rbac-order`.

---

## A · Los guards de estado

`cp` al `.bak`, mutar, correr, **anotar el nombre del test que cayó**, restaurar con `cp`,
`rm` del `.bak`, `git status --short` limpio. **Nunca `git checkout --`.**

- [ ] **A.1** — `git status --short` limpio. Línea base de la suite.

- [ ] **A.2** — RF-CIC-01. Quitá el guard de estado de `marcarEnDisputa`.
  Debe caer el test de disputar un acta APROBADA.

- [ ] **A.3** — RF-CIC-02. Quitá el guard de `corregir`.
  Debe caer el test de corregir un acta APROBADA.

- [ ] **A.4** — RF-CIC-06. Ampliá el guard de `aprobar` para aceptar EN_DISPUTA.
  Debe caer el test de aprobar en disputa.

- [ ] **A.5** — RF-CIC-07. Quitá el guard de `anular`.
  Debe caer el test de anular un acta ENVIADA.

- [ ] **A.6** — Comprobar que **ninguna** transición acepta ANULADA como estado de partida.
  Es la terminalidad del estado, y hoy se cumple por ausencia de código, no por una regla.

---

## B · Autoría, invariante y detalles

- [ ] **B.1** — RF-CIC-03, **dos mutaciones**:

  1. quitá la comprobación
     `!esCoordinator && !acta.getRegistradaPor().getId().equals(usuarioId)` →
     debe caer el test de corrección ajena;
  2. dejá la comprobación y hacé que `ObserverController.corregirActa` pase `true` en vez de
     `false` → debe caer el mismo test.

  **La segunda es la que importa.** Cambiar ese literal convierte a cualquier OBSERVER en
  corrector universal sin un solo error de compilación. Si sólo la primera hace caer algo, el
  flag no está cubierto donde se puede romper.

- [ ] **B.2** — RF-CIC-04. Invertí el `!=` del invariante **sólo dentro de `corregir`**.
  Debe caer un test específico de corrección inválida.

  Si sólo cae el de `registrar`, la segunda copia de la regla no está protegida: escribí el
  test que falta (§4.2).

- [ ] **B.3** — RF-CIC-05. Quitá `actaDetalleRepository.deleteByActa(acta)` de `corregir`.
  Debe caer un test que cuente detalles después de corregir.

  Sin ese test, una corrección duplicaría los detalles y la suma dejaría de cuadrar de forma
  invisible.

- [ ] **B.4** — RF-CIC-08. Quitá `notificacionService.notificar` de `marcarEnDisputa`.
  Debe caer un test que cuente notificaciones.

---

## C · Las dos decisiones

- [ ] **C.1** — RF-CIC-08, la asimetría de notificaciones. `marcarEnDisputa` y `anular`
  notifican; `corregir` y `aprobar` no. `backend.md:117` promete notificación en **todo**
  cambio de estado.

  Decidir **una** y hacerla completa:
  - **es deliberado** (sólo se avisa lo que exige acción del OBSERVER) → corregir
    `backend.md:117` para que lo diga; **o**
  - **faltan dos** → implementarlas, con sus tests y sus mutaciones.

  Anotar la decisión y el motivo en `apply-progress.md`.

- [ ] **C.2** — RF-CIC-09, el `audit_log` de `corregir`. Hoy registra
  `"Acta corregida por usuario " + usuarioId`, sin distinguir OBSERVER de COORDINATOR,
  aunque el método **ya recibe** `esCoordinator`.

  Incluir la distinción en el detalle, y un test que la compruebe.

  **Por qué no se deja para después**: reconstruirlo cruzando el id con la tabla de roles
  sólo funciona si ese usuario no cambió de rol entretanto. Cuando se impugne una corrección,
  la pregunta va a ser exactamente ésa.

---

## Qué NO hacer en esta fase

- **No** tocar `registrar`: es T-BE-06. Aquí se muta y se restaura.
- **No** implementar el envío real de notificaciones por Supabase Realtime: es T-IN-06.
  `NotificacionService` sólo persiste la fila, y eso es lo que se verifica.
- **No** tocar los controladores salvo la mutación de B.1.2, que se restaura.
- **No** cambiar el `@PreAuthorize("hasRole('MASTER')")` de anulación: pertenece a
  `t-be-12-cierre-coordinacion` RF-COORD-02.
- **No** cerrar C.1 eligiendo la opción cómoda sin escribir por qué. Una asimetría documentada
  es una decisión; una asimetría silenciosa es un olvido esperando a ser «arreglado» por
  alguien que no sabía que era intencional.

---

## Compuertas antes de dar por terminada la fase

```
cd backend/electoral-api
./mvnw -B test > /tmp/after.log 2>&1; echo "exit=$?"
    0 errores de ApplicationContext
    total = línea base + los tests que hayas añadido (contados)
```

Las mutaciones de A y B anotadas con el nombre del test que cayó en cada una, y las dos
decisiones de C escritas.
