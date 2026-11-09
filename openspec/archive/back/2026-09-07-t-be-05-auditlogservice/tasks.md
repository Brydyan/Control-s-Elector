# Tasks: 2026-09-07-t-be-05-auditlogservice

Origen: `documents/task-control/backendtask/COMPLETADO-T-BE-05.md`
Rama: `verify/t-be-05-auditoria`, sacada de `develop`.

**Change retrospectivo**: verifica y documenta. La reparación de los huecos de evidencia
pertenece a `t-be-14-storage-acta`.

**Bloqueado por** `back/2026-09-07-fix-liquibase-rbac-order`.

---

## A · Escribir el primer test de auditoría que existe

- [ ] **A.1** — `git status --short` limpio. Línea base de la suite.

- [ ] **A.2** — Confirmar que **hoy no hay ninguno**:

  ```bash
  grep -rn "audit_log\|AuditLog" backend/electoral-api/src/test/java
  ```

  Anotar el resultado en `apply-progress.md`. Si sale vacío, la regla más dura de
  `CLAUDE.md` no tiene ni un test detrás.

- [ ] **A.3** — Escribir el test: registrar un acta y comprobar que aparece una fila en
  `audit_log` con el usuario, la acción `REGISTRO_ACTA`, la entidad `acta` y el id correctos.

- [ ] **A.4** — **Mutación**: quitá `auditLogService.registrar` de `ActaService.registrar`.
  Debe caer A.3. Anotá el nombre. Restaurá con `cp`, nunca con `git checkout --`.

---

## B · Inventariar qué se audita de verdad

- [ ] **B.1** — Completar la tabla de RF-AUD-01 **ejecutando**, no leyendo:

  ```bash
  grep -rn "auditLogService\|AuditLogService" backend/electoral-api/src/main/java
  ```

  Confirmar los cinco casos de `ActaService`, y confirmar las tres ausencias de
  `EvidenciaService`. Verificar los dos que están marcados «verificar»: login/logout en
  `AuthService`, y la configuración preelectoral en `ConfiguracionService`.

  Corregir la tabla del spec con lo que salga. Si mi inventario está mal, **el tuyo manda**:
  decilo en `apply-progress.md`.

- [ ] **B.2** — RF-AUD-02, la IP detrás del proxy. Enviar una petición con cabecera
  `X-Forwarded-For` y ver qué IP queda en la fila.

  **Si se guarda la del proxy**, es un hallazgo: en Railway todas las filas de producción
  tendrán la misma IP y el campo no servirá. En local no se ve porque no hay proxy delante.

  Anotalo con la reproducción. **No lo arregles aquí**: cambiar cómo se resuelve la IP tiene
  implicaciones de seguridad (una cabecera `X-Forwarded-For` es falsificable si no se confía
  sólo en el último salto) y merece su propio change.

---

## C · Las dos decisiones pendientes

- [ ] **C.1** — RF-AUD-03. Proponer el test de arquitectura que haga verificable «nunca
  directo»: falla si alguna clase distinta de `AuditLogService` referencia
  `AuditLogRepository`.

  Escribí la propuesta en `apply-progress.md`. Implementarlo es opcional en este change;
  dejarlo sin proponer, no.

- [ ] **C.2** — RF-AUD-04. Responder: ¿la auditoría es parte de la transacción de negocio, o
  best-effort?

  Mirar qué hace hoy `AuditLogService.registrar` ante un fallo, y decir si ese comportamiento
  es el decidido o el accidental. Anotar la respuesta.

  **Referencia de lo que no hay que hacer**: `RlsInterceptor` captura cualquier excepción y
  sigue con un `warn`. Una garantía que se desactiva sola sin que nadie se entere.

---

## Qué NO hacer en esta fase

- **No** añadir la auditoría a `EvidenciaService`. Está asignada a `t-be-14-storage-acta`
  (D5), que ya toca esos métodos. Duplicarlo aquí produce dos arreglos del mismo hueco.
- **No** cambiar cómo se resuelve la IP. Reportalo (B.2) y seguí.
- **No** cambiar el modelo de `AuditLog`: es T-BE-03.
- **No** dar por buena la tabla de RF-AUD-01 sin haber corrido el `grep` de B.1. La escribí
  leyendo código, y leer no es verificar.

---

## Compuertas antes de dar por terminada la fase

```
cd backend/electoral-api
./mvnw -B test > /tmp/after.log 2>&1; echo "exit=$?"
    0 errores de ApplicationContext
    total = línea base + los tests de A.3  (contados)
```

Y la mutación de A.4 anotada con el nombre del test que cayó.

Si A.4 no hizo caer nada, A.3 es decorativo: rehacelo antes de cerrar.
