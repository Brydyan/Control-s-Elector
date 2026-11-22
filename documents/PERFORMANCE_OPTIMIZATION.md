# Optimización de Performance — T-PERF-OPT-01

> Spec: `openspec/changes/back/2026-11-19-performance-optimization/`
> Estado: implementado en código. Verificación de integración pendiente de un
> entorno con PostgreSQL (ver "Qué falta verificar").

## Qué cambió

| Área | Cambio | Archivo |
|------|--------|---------|
| Índices | 5 índices nuevos en `acta`, 1 en `acta_detalle` | `database/changelog/048-*.sql`, `049-*.sql` |
| ORM | `Usuario.rol` pasa de EAGER a LAZY con `@BatchSize(25)` | `auth/domain/Usuario.java` |
| API | Endpoint batch `GET /api/v1/observer/cargos-with-candidatos` | `acta/web/ObserverResource.java` |
| Red | gzip nivel 6 desde 1 KB | `application.properties` |
| Pool | 20 → 50 conexiones, mínimo 10 | `application.properties` |

La caché del frontend (Fase C del SDD) ya estaba implementada; su detalle vive en
`documents/CACHING_STRATEGY.md`.

## Por qué

El escrutinio concentra toda la carga en una ventana de tres horas. Los tres
problemas que se atacan:

1. **Full table scan en `acta`.** Los listados filtran por veedor, JRV, cargo y
   estado, y el dashboard ordena por fecha. Sin índice, cada consulta recorre la
   tabla completa: con 50K actas el costo crece lineal.
2. **Formulario del observador con once peticiones.** Cinco cargos = una petición
   de cargos, una de partidos y una de candidatos por cargo. Cada una paga
   latencia de red y handshake; en móvil por 4G domina el tiempo total.
3. **Respuestas sin comprimir y pool corto.** El JSON del dashboard es muy
   repetitivo (ratio ~10:1 con gzip), y 20 conexiones se agotan antes que la CPU.

## Qué NO se hizo, y por qué

- **`idx_acta_estado`**: ya existía desde `044-acta-add-approval-fields.sql`.
  Duplicarlo solo agrega costo de escritura.
- **`idx_acta_detalle_acta_id`**: `acta_detalle` ya tiene
  `UNIQUE(acta_id, candidato_id)`, cuyo índice btree sirve cualquier filtro por
  `acta_id`. Un índice suelto sería redundante.
- **Nombres `ix_acta_*`**: el SDD los pedía así, pero el changelog usa `idx_*`
  desde la migración `044`. Se siguió la convención viva.
- **Números `011`/`012`**: ocupados por `011-rls.sql` y
  `012-acta-anular-permiso.sql`. Los índices van en `048`/`049`.

## Verificación

| Gate | Estado | Evidencia |
|------|--------|-----------|
| Compilación backend | ✅ | `mvnw -DskipTests test-compile` |
| Formato (spotless) | ✅ | `mvnw spotless:apply` sin cambios pendientes |
| Unit tests backend | ✅ | `UsuarioMapperTest`: 8/8 |
| Suite frontend | ✅ | 1153 tests + 5 a11y, lint limpio |
| IT del endpoint batch | ⏳ | `ObserverCargosConCandidatosIT` escrito; requiere PostgreSQL |
| EXPLAIN ANALYZE | ⏳ | requiere base migrada |
| Load test 50 usuarios | ⏳ | requiere backend vivo |

### Qué falta verificar

El entorno de desarrollo de esta sesión no tiene Docker ni PostgreSQL, así que
los tests que levantan un contenedor (`PostgresTestResource`) no se pueden
ejecutar aquí. No se declaran en verde: se ejecutan en CI o contra una base real
con `-Dtest.postgres.url=...`.

## Monitoreo en producción

| Métrica | Objetivo | Alerta si |
|---------|----------|-----------|
| Latencia P95 | < 2 s | > 3 s |
| Latencia P99 | < 3 s | > 5 s |
| Uso del pool | 40–60 % | > 80 % |
| Conexiones en espera | 0 | > 0 sostenido |
| Tiempo de query promedio | < 100 ms | > 500 ms |
| Tamaño de respuesta gzip | < 100 KB | > 500 KB |

Si el pool llega a 80 % sostenido, subir `DB_POOL_MAX_SIZE` **solo** hasta el
límite de conexiones del plan de Neon: por encima, las conexiones nuevas fallan
en vez de esperar.

## Rollback

Todo es reversible sin pérdida de datos:

- Índices: `DROP INDEX IF EXISTS idx_acta_*` (el `-- rollback` de cada changeset
  lo hace por sí solo).
- Lazy: volver a `FetchType.EAGER` y quitar `@BatchSize`.
- Batch endpoint: es aditivo; los endpoints viejos siguen existiendo.
- gzip y pool: son propiedades; volver a los valores anteriores.
