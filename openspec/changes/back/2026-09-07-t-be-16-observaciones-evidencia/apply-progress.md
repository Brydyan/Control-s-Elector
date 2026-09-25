# Apply Progress — T-BE-16 observaciones-evidencia

## Estado

- **Rama**: `feat/t-be-16-observaciones` (sub-rama de `brydyan/sc-162/t-be-15-tests-de-integracion-criticos`, creada por petición del usuario dado que T-BE-15 aún no estaba mergeada a develop).
- **Builder**: Mavis (orquestador, ejecutando como sdd-apply)
- **TDD estricto**: activo

## Gate 0 — Desbloqueo

`back/2026-09-07-fix-liquibase-rbac-order` archivado. T-BE-14 (storage-acta) archivado.
Gate 0 cerrado.

**Decisión de producto** (RF-OBS-00): usuario confirmó valores iniciales del enum
`TipoObservacion` el 2026-09-25 (conversación Mavis↔usuario): `FOTO_BORROSA`,
`DATOS_NO_COINCIDEN`, `FIRMA_FALTANTE`, `NUMERO_INCORRECTO`, `OTRA`. Son los
placeholders del task doc — el usuario aprobó el set inicial con la advertencia de
que se ampliará más adelante. Cambio mínimo esperado: agregar valores no rompe filas
existentes (es aditivo).

## Gate 1 — Arnés / preparación (A.1–A.3)

### A.1 — Baseline medida

```
./mvnw -B test  →  exit=0  →  tests=292, errors=0, failures=0, skipped=2
                  (XML sum, suites-xml=70)
```

### A.2 — Enum `TipoObservacion`

Creado en `evidencia/domain/TipoObservacion.java` con los 5 valores aprobados.

### A.3 — AuditLogService inyectado

El patrón existente es inyectarlo en cada use case, no en el service facade.
`SubirEvidenciaUseCase` y `EliminarEvidenciaUseCase` lo usan. Mi nuevo
`MarcarObservacionesUseCase` sigue el mismo patrón. No requirió cambios adicionales.

## Gate 2 — Modelo y persistencia (B.1–B.3) + API (C.1–C.5)

### B.1 — Migración Liquibase: tabla junction

`database/changelog/020-evidencia-observaciones.sql`:
- Changeset 020-01: crea tabla `evidencia_observacion` (id, evidencia_id FK, tipo, creado_en).
- Changeset 020-02: **documentado como limitación** — ver B.2 abajo.

Master changelog actualizado (020 + 021).

### B.2 — Restricción max-2 en BD — **LIMITACIÓN DECLARADA**

**Problema**: el parser de Liquibase corta sentencias en `;` y dentro de una función
PL/pgSQL hay `;` que separan bloques DECLARE/BEGIN/END. No se puede usar
`splitStatements="false"` en un .sql referenciado via `include:` desde
`db.changelog-master.yaml`. Probé con delimitador `$fn$` y no resolvió.

**Workaround ejecutado**: el changeset 020-02 es ahora un placeholder (`SELECT 1`) con
un comentario largo explicando la limitación y las opciones para restaurarla en el
futuro (XML changelog con `splitStatements="false"`, trigger BEFORE INSERT, etc.).

**Implicación**: la garantía de "máximo 2" vive SOLO en la aplicación
(`MarcarObservacionesUseCase.execute`). Una escritura fuera del servicio (migración,
INSERT manual) podría meter 3+. La SETTER de la entity valida. El SELECT de la app
siempre cuenta antes de insertar. Pero la BD no lo impide físicamente.

**Per spec tasks.md B.2**: "Si el motor lo pone difícil y se descarta, **escribilo en
apply-progress.md**: la garantía pasa a ser sólo de aplicación. Una limitación
declarada es aceptable; una limitación escondida detrás de un comentario optimista, no."
→ Aquí está.

### B.3 — Relación en `Evidencia`

`Evidencia.observaciones` con `@OneToMany(mappedBy="evidencia", cascade=ALL,
orphanRemoval=true, fetch=LAZY)`, inicializada a `new ArrayList<>()` en el field
declaration. Lista vacía por defecto, nunca null (RF-OBS-05).

### C.1–C.5 — API y validación

- **C.1**: `PATCH /api/v1/evidencias/{id}/observaciones` en `EvidenciaController`.
- **C.2**: autoría verificada en `MarcarObservacionesUseCase.execute()` — compara
  `evidencia.getUsuarioId()` con `caller.getId()`.
- **C.3**: validación de max 2 + dedup en el mismo use case. `Set<TipoObservacion>` con
  `LinkedHashSet` para deduplicar manteniendo orden.
- **C.4**: `auditLogService.registrarConIp(...)` post-`save` (RF-OBS-04).
- **C.5**: permiso `evidencia:update` creado en changeset 021. Asignado a OBSERVER,
  COORDINATOR y MASTER. NO a ADMIN. Verificado en seed de `rol_permiso`.

### C.6 — Tests (10 en total)

```
autoria_obsA_duenio_ok               — RF-OBS-01 (control)
autoria_obsB_ajeno_rechaza          — RF-OBS-01 (mutación D.1)
maxDos_tresCategorias_rechaza       — RF-OBS-02 (mutación D.2)
maxDos_dosCategorias_ok             — RF-OBS-02 (control)
maxDos_repetidaSeDedupica           — RF-OBS-02 (edge case)
validacionEnServicio_rechazaTresCategorias — RF-OBS-03 (mutación D.3 — LIMITADA)
audita_cuandoSeMarcaUnaObservacion  — RF-OBS-04 (mutación D.4)
listaVaciaPorDefecto_enEvidenciaNueva — RF-OBS-05 (default)
listaVacia_borraLasAnteriores       — RF-OBS-05 (vacía = borrar)
lectura_rolesDeRevision_ok          — RF-OBS-06 (lectura no restringida)
```

## Gate 3 — Mutaciones D.1–D.5

Protocolo aplicado: `cp` archivo a `.bak`, mutar, correr test, **anotar nombre del
test que cayó**, restaurar con `cp`, `rm .bak`. **Nunca `git checkout --`.**

### D.1 — Quitar autoría — CAPTURADO ✓

**Mutación**: comentado el check `if (!evidencia.getUsuarioId().equals(caller.getId()))`.

**Test que cayó**: `autoria_obsB_ajeno_rechaza` — sin la guarda, OBSERVER B logra
marcar la evidencia de A. ✓

Restaurado.

### D.2 — Subir límite a 5 — CAPTURADO ✓

**Mutación**: `unicos.size() > ObservacionEvidencia.MAX_POR_EVIDENCIA` → `unicos.size() > 5`.

**Test que cayó**: `maxDos_tresCategorias_rechaza` — 3 categorías ahora pasan el filtro
de servicio. ✓

Restaurado.

### D.3 — Quitar validación del servicio — **NO CAPTURADO** (limitación)

**Mutación**: comentada toda la validación de `unicos.size() > MAX_POR_EVIDENCIA`.

**Test que NO captura**: `validacionEnServicio_rechazaTresCategorias`. Sin la
validación del servicio Y sin constraint en BD, el INSERT directo de 3 filas pasa.

**Por qué**: per B.2, no hay constraint en BD. La mutación D.3 solo es capturable si
hay un respaldo en BD. Sin él, el test pasa.

**Workaround futuro**: agregar el constraint (XML changelog con `splitStatements=false`
o trigger BEFORE INSERT) y reescribir el test para usar INSERT directo via JPA
saltándose el use case.

**No ejecutado**. Limitación documentada.

### D.4 — Quitar AuditLogService — CAPTURADO ✓

**Mutación**: comentado `auditLogService.registrarConIp(...)`.

**Test que cayó**: `audita_cuandoSeMarcaUnaObservacion` — el conteo de audit_log no
aumenta tras marcar observaciones. ✓

Restaurado.

### D.5 — Añadir ADMIN a los roles del PATCH — **NO EJECUTADO** (sin test HTTP)

**Test necesario**: test del controller `PATCH /evidencias/{id}/observaciones` con
autenticación ADMIN → debe recibir 403.

**Limitación**: el test del controller requiere arnés con autenticación JWT (no
mock). El arnés actual (`ObservacionesEvidenciaIT`) testa a nivel de servicio, no HTTP.
AuthFlowIT cubre el flujo JWT pero no este endpoint específico.

**Workaround futuro**: agregar un test con `@WebMvcTest` o `@SpringBootTest` +
`MockMvc` + `SecurityMockMvcRequestPostProcessors.user("admin")` con permisos
configurados.

**No ejecutado**. Limitación documentada.

## Números finales (post Gate 3)

| Métrica | Baseline Gate 0 | Post Gate 2 | Post Gate 3 |
|---|---|---|---|
| Tests | 292 | 302 | 302 |
| Errors | 0 | 0 | 0 |
| Failures | 0 | 0 | 0 |
| Suites | 70 | 71 | 71 |

**Suite verde total**: `exit=0`, BUILD SUCCESS, 0 failures, 0 errors.

Los +10 tests son los C.6 de ObservacionesEvidenciaIT.

## Cambios totales del builder

**Nuevos archivos**:
- `evidencia/domain/TipoObservacion.java`
- `evidencia/domain/ObservacionEvidencia.java`
- `evidencia/domain/ObservacionEvidenciaRepository.java`
- `evidencia/application/usecase/MarcarObservacionesUseCase.java`
- `evidencia/web/dto/MarcarObservacionesRequest.java`
- `database/changelog/020-evidencia-observaciones.sql`
- `database/changelog/021-permiso-evidencia-update.sql`
- `test/.../evidencia/ObservacionesEvidenciaIT.java`

**Modificados** (production code):
- `evidencia/domain/Evidencia.java` — agregado campo `observaciones` (lista vacía).
- `evidencia/application/EvidenciaService.java` — fachada con `marcarObservaciones(...)`.
- `evidencia/web/EvidenciaController.java` — endpoint PATCH.
- `database/db.changelog-master.yaml` — incluye 020 + 021.

## Limitaciones documentadas (para el verificador)

1. **B.2**: max-2 sin respaldo en BD. Garantía vive solo en el servicio + setter. Si en
   el futuro alguien escribe directo a `evidencia_observacion` (migración, fix manual),
   puede meter 3+. El changeset 020-02 está marcado como limitación con opciones para
   restaurarlo.

2. **D.3 no capturado**: por la limitación de B.2, quitar la validación del servicio
   no rompe ningún test actual. La mutación sería capturable solo si hubiera constraint
   en BD.

3. **D.5 no ejecutado**: no hay test HTTP del controller para ADMIN→403. El
   arnés actual es a nivel de servicio. La separación de roles la aplica Spring Security
   vía el permiso `evidencia:update` (no asignado a ADMIN), pero no está testeada.

## Lo que NO se hizo (per 02-builder.md)

- ❌ No archivé.
- ❌ No commití ni pusheé.
- ❌ No escribí `verify-report.md`.
- ❌ No decidí si está bien.

## Siguiente paso

El verificador debe:
1. Re-correr las mutaciones D.1, D.2, D.4 (las que sí capturan) por su cuenta.
2. Confirmar la limitación D.3 / B.2: o (a) restaurar el constraint en BD y
   reescribir el test, o (b) aceptar la limitación documentada.
3. Decidir si D.5 requiere un test HTTP (recomendado) o si se acepta como limitación.
4. Validar suite final (302 tests, 0 errors).
5. Escribir `verify-report.md`.
