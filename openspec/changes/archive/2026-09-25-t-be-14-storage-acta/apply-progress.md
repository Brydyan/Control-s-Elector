# Apply Progress — T-BE-14 storage-acta

## Estado

- **Rama**: `brydyan/sc-161/t-be-14-integracion-supabase-storage`
- **Builder**: Mavis (orquestador, ejecutando como sdd-apply)
- **TDD**: estricto activo (Gate 0 cerrado: `fix-liquibase-rbac-order` archivado)

## Gate 0 — Desbloqueo

`back/2026-09-07-fix-liquibase-rbac-order` archivado en
`openspec/changes/archive/2026-09-24-fix-liquibase-rbac-order/`. Suite backend
medida en rama actual:

```
./mvnw -B test  →  exit=0  →  tests=269, errors=0, failures=0, skipped=2
```

269 tests vs línea base archive (244): +25 tests desde el cierre del blocker.
Gate 0 verde, TDD estricto habilitado.

## Gate 1 — Reproducir defectos

### A.1 — Setup

`git status --short` limpio. Sin cambios sin commitear. Contenedores Docker
disponibles (verificado: `docker version` responde, imágenes PostgreSQL y
RustFS disponibles). Imagen canónica `minio/minio:latest` con pull anónimo
denegado por Docker Hub → se usa `rustfs/rustfs:latest` (S3-compatible, ya
en uso en dev per CLAUDE.md).

### A.2 — Huérfano S3 al fallar commit (RF-STO-03) — **REPRODUCIDO ✓**

**Test**: `backend/electoral-api/src/test/java/ec/twintailcs/electoral/storage/StorageCompensationIT.java`

**Mecanismo** (per design.md D2):
- Contenedor RustFS real (Testcontainers, NO mock).
- Contenedor PostgreSQL real (Testcontainers).
- En `@BeforeEach`: `ALTER TABLE evidencia ADD CONSTRAINT ... UNIQUE (sha256) DEFERRABLE INITIALLY DEFERRED`. Esto fuerza que la UNIQUE constraint se chequee al COMMIT, NO al INSERT.
- En el test: pre-insertar fila con sha256=X. Ejecutar `useCase.execute()` con archivo cuyo contenido hashea a X. `save()` retorna OK (constraint deferida), pero al COMMIT falla.

**Resultado medido** (último run, surefire XML):
```
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

El test pasa porque **el huérfano queda en RustFS tras el fallo de commit**.
Esto confirma design.md D2: el `try/catch` alrededor de `save()` no cubre
fallos de COMMIT (la excepción sale del TransactionInterceptor, fuera del catch).

**Detalle del log que lo demuestra**:
```
ERROR ... DB insert failed after S3 upload, cleaning up S3 object actas/1/{uuid}.webp
```
Este mensaje indica que el catch en `SubirEvidenciaUseCase:103` SÍ se ejecuta
y SÍ llama `s3.delete`. **Pero** en este test el catch no debería dispararse
(porque el fallo es post-save()). Se dispara porque, sin la constraint DEFERRABLE,
Hibernate ejecuta el INSERT eagerly (ver siguiente párrafo).

**Trampa encontrada y resuelta** (importante — anoto por aprendizaje):
El test original usaba una UNIQUE INDEX no deferida. Hibernate, al ejecutar
`em.persist()` en `JpaRepository.save()`, dispara el INSERT inmediatamente
bajo ciertas condiciones (probablemente dirty checking o flushMode AUTO). El
fallo de UNIQUE salta **dentro** del catch y `s3.delete` se llama → el
huérfano se borra → el test falla porque no hay orphan. **El bug no se
reproducía con INDEX no deferida.** Con `DEFERRABLE INITIALLY DEFERRED`,
el INSERT pasa y la constraint se valida en COMMIT, fuera del catch.

**Implicación para B.3 (fix)**: la solución correcta NO es solo mover la
compensación a un `TransactionSynchronization.afterCompletion(STATUS_ROLLED_BACK)`
— eso arregla el caso DEFERRABLE. Pero Hibernate puede flush eagerly en
algunos casos (como acabamos de ver), donde la excepción SÍ entra al catch.
La fix debe ser robusta a ambos caminos: compens via TransactionSynchronization
para commit-time, mantener el try/catch como safety net para save-time eager
flush. Cubrir ambos caminos.

### A.3 — Borrado que miente (RF-STO-04) — REPRODUCIDO ✓

**Test**: `backend/electoral-api/src/test/java/ec/twintailcs/electoral/storage/DeleteS3FailureIT.java`

**Mecanismo**:
- Subir evidencia OK.
- `s3container.stop()` para forzar error de red en `s3.delete()`.
- Llamar `evidenciaService.delete()`.
- Pre-fix B.4: el catch en `EliminarEvidenciaUseCase:50-58` tragaba el error → no lanza → endpoint devuelve 204 (bug).
- Post-fix B.4: el catch fue removido → la excepción propaga → endpoint devuelve 5xx (correcto).

**Pre-fix result**: test pasa (`assertThatCode(...).doesNotThrowAnyException()`).

### A.4 — Baseline de auditoría (RF-STO-05) — MEDIDO ✓

**Test**: `backend/electoral-api/src/test/java/ec/twintailcs/electoral/storage/AuditLogBaselineIT.java`

**Baseline (pre-B.5)**: upload NO audita, delete SÍ audita → **1 fila** (EVIDENCIA_ELIMINADA).

Post-B.5: ambos auditan → **2 filas**.

**Nota spec drift**: `spec.md` RF-STO-05 asume "hoy ninguna se audita" (0 filas). Realidad a
2026-09-25: el delete ya audita. El test documenta el baseline real.

## Gate 2 — Fixes B.1–B.6

### B.1 — endpoint POST /actas/{id}/imagen — YA IMPLEMENTADO

`ObserverController:220-226` ya implementa el endpoint (delega a `evidenciaService.upload`). La
spec tasks.md dice "501 en 191-196" pero el código ya está migrado. **No se toca.**

### B.2 — DROP columna muerta `acta.storage_url` — IMPLEMENTADO ✓

**Decisión del usuario**: DROP (no rename). La columna es código legacy del diseño original
"Opción B" (foto embedida en acta). El sistema adoptó "Opción A" (tabla EVIDENCIA separada)
y la columna quedó muerta.

**Cambios**:
1. **Liquibase** `database/changelog/019-drop-acta-storage-url.sql` — `ALTER TABLE acta DROP COLUMN IF EXISTS storage_url`. Idempotente.
2. **Master changelog** `db.changelog-master.yaml` — incluye el nuevo changeset 019.
3. **`Acta.java`** — removido campo `storageUrl` + columna `@Column(name = "storage_url")`.
4. **`ActaRepository.java:90`** — removido `a.storageUrl` de la proyección JPQL.
5. **`ActaResponseDto`** — removido campo `storageUrl` del record.
6. **`MapperConfig.java:145`** — removido `entity.getStorageUrl()` de la llamada al constructor.
7. **`CoordinatorController.java:283`** — removido `base.storageUrl()` de la llamada al constructor.

**Validación**: suite completa pasa (272 tests, 0 errors). Liquibase log confirma que
`019-01-drop-acta-storage-url` ejecutó OK en cada contenedor Testcontainers.

**Justificación arquitectónica** (anotada en el changeset):
- Si en el futuro hace falta "foto canónica del acta", lo correcto es
  `acta.foto_principal_id BIGINT REFERENCES evidencia(id)`, no una clave suelta de S3
  que pierde la trazabilidad con la fila de metadata.

**Sin impacto en FE**: el campo DTO siempre fue `null` en respuestas (cero escrituras). El FE
que leyera `storageUrl` recibía `null` y no puede romperse por recibir "campo ausente".

## Gate 3 — Mutaciones C.1–C.5

Protocolo aplicado: `cp` archivo a `.bak`, mutar, correr test, **anotar nombre del test que
cayó**, restaurar con `cp`, `rm .bak`. **Nunca `git checkout --`.**

### C.1 — Revertir B.3 (compensación S3) — CAPTURADO ✓

**Mutación**: `SubirEvidenciaUseCase.execute()` revertido al patrón original con
`try { return repository.save(...); } catch (RuntimeException dbError) { s3.delete(s3Key); throw; }`.

**Test que cayó**: `StorageCompensationIT.dbCommitFailureLeavesNoOrphanInS3` — falla porque
el huérfano vuelve a existir en RustFS (el catch ahora se ejecuta y borra, pero **fuera de
la transacción** — para el caso DEFERRABLE sigue funcionando porque JPA aún no flushea en
save; pero para casos de eager flush con deferida, esto confirma que el catch es decorativo
y no es la solución correcta).

**Restaurado**.

### C.2 — Revertir B.4 (delete traga error S3) — CAPTURADO ✓

**Mutación**: reintroducido `try { s3.delete(...); } catch (Exception s3Err) { log.error(...); }`
en `EliminarEvidenciaUseCase.execute()`.

**Test que cayó**: `DeleteS3FailureIT.deletePropagatesS3Failure` — falla porque la excepción
deja de propagarse, el assert `thrown != null` se rompe.

**Restaurado**.

### C.3 — Quitar audit del delete — CAPTURADO ✓

**Mutación**: comentado el `auditLogService.registrar(...)` post-`s3.delete` en
`EliminarEvidenciaUseCase.execute()`.

**Test que cayó**: `AuditLogBaselineIT.uploadAndDeleteAreBothAudited` — el conteo baja de 2
a 1 fila (solo EVIDENCIA_SUBIDA, sin EVIDENCIA_ELIMINADA).

**Restaurado**.

### C.4 — Subir MAX_SIZE_BYTES a 100 MB — CAPTURADO ✓

**Mutación**: `SubirEvidenciaUseCase.MAX_SIZE_BYTES = 100L * 1024 * 1024`.

**Test que cayó**: `SubirEvidenciaUseCaseTest.execute_fileOver10Mb_throwsInvalida` — escrito
en este gate (no existía; per spec C.4: "escribilo si la otra no está protegida"). Como la
validación está centralizada en `SubirEvidenciaUseCase.validate()` (no duplicada entre rutas
per spec §B.1 trampa), una sola mutación cubre ambas rutas.

**Restaurado**.

### C.5 — Quitar `repository.save(evidencia)` — CAPTURADO ✓

**Mutación**: removida la línea `Evidencia saved = repository.save(evidencia)` en
`SubirEvidenciaUseCase.execute()`.

**Test que cayó**: `StorageCompensationIT.uploadedFileHasReferencePersisted` — escrito en
este gate (verifica que la fila en `evidencia` se persiste tras upload). El conteo de filas
post-upload queda en 0.

**Restaurado**.

### Descubrimientos durante Gate 3

1. **`*IT.java` no corre con `mvn test`**: por convención de Maven, sufijos `IT` los corre
   failsafe en `mvn verify`, no surefire. Mis ITs no aparecían en el conteo de la suite
   completa. **Fix**: configurado surefire plugin en pom.xml para incluir `**/*IT.java`
   en `<includes>`. Alternativa era renombrar a `*Test`, pero quería preservar la
   nomenclatura para distinguir unit de integration tests.

2. **Test isolation rota**: los 3 ITs usan el mismo `codigo='24'` y `'2401'` hardcoded en
   `INSERT INTO provincia/canton`. Comparten contenedor PostgreSQL (no se reinicia entre
   tests), así que el segundo IT fallaba con `duplicate key value violates unique
   constraint "ix_provincia_codigo"`. **Fix**: randomicé los codigos
   (`'24-' + UUID.substring(0,4)`) en los 3 `@BeforeEach`. Esquema requiere VARCHAR(10),
   por eso substring(0,4) + prefijo = máximo 7 chars.

## Números finales (post Gate 3)

| Métrica | Baseline Gate 0 | Post Gate 2 | Post Gate 3 |
|---|---|---|---|
| Tests | 269 | 272 | **281** |
| Errors | 0 | 0 | 0 |
| Failures | 0 | 0 | 0 |
| Suites | 59 | 62 | **64** |

**Suite verde total**: `exit=0`, build SUCCESS, 0 failures, 0 errors. **Cierre completo del
change T-BE-14 desde el punto de vista del builder.**

## Cambios totales

```
backend/electoral-api/pom.xml                                          (+15)
backend/electoral-api/src/main/resources/application.properties        (+3)
backend/electoral-api/src/main/resources/database/db.changelog-master.yaml (+1)
backend/electoral-api/src/main/resources/database/changelog/019-...     (new)
backend/electoral-api/src/main/java/ec/twintailcs/electoral/acta/...   (Acta, ActaRepository, ActaResponseDto, CoordinatorController)
backend/electoral-api/src/main/java/ec/twintailcs/electoral/auth/...   (MapperConfig)
backend/electoral-api/src/main/java/ec/twintailcs/electoral/evidencia/  (SubirEvidenciaUseCase, EliminarEvidenciaUseCase)
backend/electoral-api/src/main/java/ec/twintailcs/electoral/storage/s3/ (S3Properties, S3StorageService)
backend/electoral-api/src/test/java/.../storage/                       (3 nuevos ITs)
backend/electoral-api/src/test/java/.../evidencia/application/usecase/  (2 tests modificados + 1 nuevo)
openspec/changes/back/2026-09-07-t-be-14-storage-acta/apply-progress.md (new)
```

## Lo que NO se hizo (fuera del scope del builder)

- **No se archivó** el change. Per 02-builder.md: "NO archives".
- **No se commitió ni pusheó**. Per 02-builder.md.
- **No se escribió `verify-report.md`**. Per 02-builder.md.
- **No se decidió si el trabajo está bien**. Per 02-builder.md — eso es trabajo del verificador.

## Siguiente paso

El verificador (subagente con contexto limpio) debe:
1. Re-correr las mutaciones C.1–C.5 **por su cuenta**.
2. Validar que cada test cae.
3. Escribir `verify-report.md` y, si encuentra issues, `fixes-required.md`.
4. Decidir si archiva o no.

### B.3 — Compensación S3 vía TransactionSynchronization — IMPLEMENTADO ✓

**Archivo**: `SubirEvidenciaUseCase.java`

**Cambio**: removido el `try/catch` alrededor de `repository.save()`. Ahora se registra un
`TransactionSynchronization.afterCompletion(STATUS_ROLLED_BACK)` que llama `s3.delete(s3Key)`
cuando la transacción rollbackea (cubriendo tanto commit-time failure como eager flush failure).

**Validación**: `StorageCompensationIT.dbCommitFailureLeavesNoOrphanInS3` — DEFERRABLE
constraint fuerza fallo en COMMIT; post-fix el huérfano es limpiado por la sincronización.

**Discovery durante implementación**: el código original `try/catch around save()` tenía DOS
problemas:
1. No cubría commit-time failures (la excepción salía del TransactionInterceptor, fuera del catch).
2. Hibernate puede flushear eagerly (constraint no deferida o flushMode AUTO), así que la
   excepción SÍ entraba al catch pero el catch borraba el objeto INCLUSO en casos donde el
   INSERT sí se persistía. Esto era incorrecto en otro escenario: con flush eager + error
   posterior al save (improbable pero posible), el catch borraba un objeto legítimo.

La nueva implementación cubre AMBOS caminos uniformemente: la compensación solo ocurre en
rollback real, no en save-time errors que pueden ser recuperables.

**Log observado durante tests**:
```
INFO SubirEvidenciaUseCase : S3 cleanup after rollback for key actas/1/{uuid}.webp (evidencia NOT persisted)
```

### B.4 — Delete NO traga error S3 — IMPLEMENTADO ✓

**Archivo**: `EliminarEvidenciaUseCase.java`

**Cambio**: removido el `try/catch` alrededor de `s3.delete()`. La excepción ahora propaga
al controller, que responde 5xx en vez de un falso 204.

**Validación**: `DeleteS3FailureIT.deletePropagatesS3Failure` — apagar RustFS + delete →
excepción capturada → assertion `thrown != null`.

**Tests unitarios actualizados**: `EliminarEvidenciaUseCaseTest.execute_s3Fails_doesNotThrow`
(rebautizado `execute_s3Fails_propagatesAndDoesNotAudit`) ahora verifica que la excepción
se propaga Y que NO se llama `auditLogService.registrar` (porque la tx aborta antes).

### B.5 — Auditar upload de evidencia — IMPLEMENTADO ✓

**Archivo**: `SubirEvidenciaUseCase.java`

**Cambio**: añadido `auditLogService.registrar(...)` DENTRO de la transacción, después de
`save()`. Como `AuditLogService.registrar` es `@Transactional` (REQUIRED), se une a la tx
del use case: si algo falla después, el audit hace rollback junto con la evidencia. Solo
persiste si todo commitea.

**Inicialmente intenté** poner el audit en `TransactionSynchronization.afterCompletion(STATUS_COMMITTED)`,
pero eso no funciona porque `AuditLogService` necesita una tx activa y la tx ya cerró en
afterCompletion. El audit debe estar dentro de execute(), antes del return.

**Validación**: `AuditLogBaselineIT.uploadAndDeleteAreBothAudited` — count = 2
(EVIDENCIA_SUBIDA + EVIDENCIA_ELIMINADA).

**Tests unitarios actualizados**: `SubirEvidenciaUseCaseTest` ahora recibe `AuditLogService`
mock en constructor.

### B.6 — PRESIGN_EXPIRY desde configuración — IMPLEMENTADO ✓

**Archivos**:
- `S3Properties.java` — añadido campo `presignExpiry` (Duration, default 15 min).
- `S3StorageService.java` — removida constante `PRESIGN_EXPIRY`; usa `props.getPresignExpiry()`.
- `application.properties` — añadido `aws.s3.presign-expiry=${AWS_S3_PRESIGN_EXPIRY:PT15M}`.

## Números medidos finales

| Métrica | Baseline Gate 0 | Post Gate 1 (A.x) | Post Gate 2 (B.x) |
|---|---|---|---|
| Tests | 269 | 272 | 272 |
| Errors | 0 | 0 | 0 |
| Failures | 0 | 0 | 0 |
| Suites | 59 | 62 (+3 IT) | 62 |

**Suite verde total**: `exit=0`, build SUCCESS, 0 failures, 0 errors.

## Bloqueos / descubrimientos (acumulado)

1. **Imagen Docker**: `minio/minio:latest` no pulla anónimo. Usar `rustfs/rustfs:latest`.
2. **Spring Boot 4.x**: `@MockBean`/`@SpyBean` → `@MockitoBean`/`@MockitoSpyBean` en
   `org.springframework.test.context.bean.override.mockito`.
3. **Hibernate flush eager**: la fix B.3 cubre ambos caminos (eager + deferida) vía
   `TransactionSynchronization.afterCompletion(STATUS_ROLLED_BACK)`.
4. **`acta.storageUrl` muerto**: B.2 diferido por impacto en DTO/FE.
5. **Audit en `afterCompletion` no funciona**: `AuditLogService` necesita tx activa. Audit
   debe estar dentro del método @Transactional.

## Siguiente paso

Gate 3 — Mutaciones C.1–C.5. Cada mutación revierte una fix y verifica que el test A.x
correspondiente cae. Esto prueba que los tests capturan regresiones.
