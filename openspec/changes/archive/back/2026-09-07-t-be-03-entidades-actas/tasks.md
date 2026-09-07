# Tasks: 2026-09-07-t-be-03-entidades-actas

Origen: `documents/task-control/backendtask/COMPLETADO-T-BE-03.md`
Rama: `verify/t-be-03-entidades-actas`, sacada de `develop`.

**Change retrospectivo**: verifica, no construye.

**Bloqueado por** `back/2026-09-07-fix-liquibase-rbac-order`.

---

## A · Verificar por mutación

`cp` al `.bak`, mutar, correr, **anotar el nombre del test que cayó**, restaurar con `cp`,
`rm` del `.bak`, `git status --short` limpio. **Nunca `git checkout --`.**

- [ ] **A.1** — `git status --short` limpio. Línea base de la suite.

- [ ] **A.2** — RF-ACT-01. Cambiá `@Enumerated(EnumType.STRING)` a `ORDINAL` en `Acta.estado`.
  Debe caer un test que persista un acta y compruebe el valor almacenado.

  Si no existe, escribilo: con ordinal, insertar un estado nuevo en medio del enum
  reinterpreta en silencio todas las actas ya guardadas.

- [ ] **A.3** — RF-ACT-02. Ampliá el guard de `ActaService.corregir` para aceptar ANULADA.
  Debe caer un test que intente corregir un acta anulada.

  Si no cae, la terminalidad de ANULADA no está protegida: sólo está siendo cierta por
  ausencia de código que la rompa.

- [ ] **A.4** — RF-ACT-03. Añadí `actaDetalleRepository.deleteByActa(acta)` en
  `ActaService.anular`. Debe caer el test que cuenta detalles después de anular.

- [ ] **A.5** — RF-ACT-07. Quitá `notificacionService.notificar` de `marcarEnDisputa`.
  Debe caer un test que cuente notificaciones tras disputar.

- [ ] **A.6** — RF-ACT-04, verificación estructural: comprobar que ningún campo de `Acta`
  guarda votos de un candidato concreto. Es una lectura, no una mutación — anotá el resultado.

---

## B · Las tres preguntas abiertas

- [ ] **B.1** — RF-ACT-05, el nombre de la fecha. Averiguar **ejecutando** cuál existe:

  ```bash
  grep -n "fechaRegistro\|registradaEn\|registrada_en" \
       backend/electoral-api/src/main/java/ec/twintailcs/electoral/acta/domain/Acta.java
  ```

  Después corregir los dos documentos que usan el nombre equivocado:
  `documents/task-control/backend.md:46` y
  `documents/task-control/backendtask/T-BE-13.md:375`.

  **Trampa**: `T-BE-13.md` es el material de partida del change de dashboard. Si no se
  corrige ahora, el Builder de T-BE-13 escribirá contra un getter que no existe.

- [ ] **B.2** — RF-ACT-07, la notificación de aprobación. `marcarEnDisputa` y `anular`
  notifican; `aprobar` no. `backend.md:117` promete notificación en todo cambio de estado.

  Decidir **una** y dejarla escrita:
  - **es deliberado**: sólo se notifica lo que exige acción del OBSERVER → corregir
    `backend.md:117` para que diga eso;
  - **es un olvido**: abrir el arreglo en `t-be-12-cierre-coordinacion`, que ya toca ese
    flujo, con su test y su mutación.

  Anotar la decisión y su motivo en `apply-progress.md`.

- [ ] **B.3** — RF-ACT-06, la auditoría por convención. Hoy nada impide que un servicio
  escriba directo en `AuditLogRepository` saltándose `AuditLogService`.

  Comprobar si alguien ya lo hace:

  ```bash
  grep -rn "AuditLogRepository" backend/electoral-api/src/main/java | grep -v AuditLogService
  ```

  Si nadie lo hace: anotarlo como limitación conocida (la regla es de convención, no
  verificable) o proponer cómo hacerla verificable — por ejemplo, un test de arquitectura.
  Si alguien lo hace: es un hallazgo, con su archivo y línea.

---

## Qué NO hacer en esta fase

- **No** tocar la lógica de `ActaService`: es T-BE-06 y T-BE-07. Aquí sólo se muta para
  comprobar que hay tests, y se restaura.
- **No** implementar el envío real de notificaciones: es T-IN-06 + T-FE.
- **No** arreglar el `ResponseEntity<Acta>` de `CoordinatorController`. Es un hallazgo real
  (expone la entidad cruda con sus relaciones perezosas) pero pertenece a
  `t-be-12-cierre-coordinacion`. Anotalo y seguí.
- **No** renombrar el campo de fecha en el código. Corregí los **documentos** al nombre real:
  el código es la autoridad.

---

## Compuertas antes de dar por terminada la fase

```
cd backend/electoral-api
./mvnw -B test > /tmp/after.log 2>&1; echo "exit=$?"
    0 errores de ApplicationContext
    total: no baja respecto de la línea base
```

Las mutaciones de A anotadas con el nombre del test que cayó en cada una, y las tres
preguntas de B respondidas — no dejadas abiertas.
