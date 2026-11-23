# Optimización de Performance — T-PERF-OPT-01

> Spec: `openspec/changes/back/2026-11-19-performance-optimization/`
> Estado: implementado y verificado contra PostgreSQL local (Docker, Postgres 16).
> Lo que depende de Neon queda pendiente post-deploy: ver "Monitoreo en
> producción".

## Qué cambió

| Área | Cambio | Archivo |
|------|--------|---------|
| Índices | 5 índices nuevos en `acta`, 1 en `acta_detalle` | `database/changelog/048-*.sql`, `049-*.sql` |
| ORM | `Usuario.rol` pasa de EAGER a LAZY con `@BatchSize(25)` | `auth/domain/Usuario.java` |
| API | Endpoint batch `GET /api/v1/observer/cargos-with-candidatos` | `acta/web/ObserverResource.java` |
| Red | gzip nivel 6 para JSON/HTML/texto | `application.properties` |
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
| Compilación backend | ✅ | `mvnw clean verify` |
| Formato (spotless) | ✅ | `mvnw spotless:apply` sin cambios pendientes |
| Suite backend completa | ✅ | **298 tests, 0 fallos, 0 errores** contra base limpia |
| Suite frontend | ✅ | 1153 tests + 5 a11y, lint limpio |
| IT del endpoint batch | ✅ | `ObserverCargosConCandidatosIT`: 4/4 verde, dataset propio |
| Índices en `pg_indexes` | ✅ | `IndicesActaIT`: 8 índices `idx_acta*` con las columnas esperadas |
| EXPLAIN ANALYZE | ✅ | Index Scan / Bitmap Index Scan con 50K filas sintéticas |
| Lazy sin N+1 | ✅ | `UsuarioLazyBatchIT`: el conteo de sentencias no escala con el número de usuarios |
| gzip en vivo | ✅ | `content-encoding: gzip` en `/menus/me`, `/admin/usuarios`, `/dashboard/resultados` |
| Load test local | ✅ | 100 usuarios × 50 requests (5000): 0 errores, p95 36 ms, 2383 req/s |
| Pool sin errores de adquisición | ✅ | 0 "unable to acquire connection" en 5000 requests |
| E2E del flujo Observer | ✅ | Playwright contra la API real, 3 proyectos de navegador |
| Medición del pool contra Neon | ⏸ | requiere el compute real: post-deploy |

El detalle de cada gate, con los comandos y las cifras, vive en
`openspec/changes/back/2026-11-19-performance-optimization/verification-report.md`.

### Qué queda pendiente de medir

Lo único que falta es la medición **contra Neon**, y no es simulable: el
comportamiento del pool con PgBouncer delante y el cold start tras la suspensión
del compute solo se ven en el entorno real. Los criterios que se van a medir
están definidos en `tasks.md` (D.2-a a D.2-d) y en la tabla de monitoreo de más
abajo.

## Monitoreo en producción

La topología real es **Railway (backend) + Neon (PostgreSQL) + Cloudflare R2**, y
Neon pone un **PgBouncer (`pool_mode=transaction`) delante de cada compute**. Eso
cambia qué se puede medir: el "80 % de ocupación del pool" del SDD es un número
de PgBouncer, no del backend, y el backend no lo observa.

| Métrica | Objetivo | Alerta si | Dónde se ve |
|---------|----------|-----------|-------------|
| Latencia P95 | < 2 s | > 3 s | `/q/metrics` (http) |
| Latencia P99 | < 3 s | > 5 s | `/q/metrics` (http) |
| Conexiones esperando | 0 | > 0 sostenido | `agroal_active_count` vs `max-size` |
| Errores de adquisición | 0 | > 0 | log: "unable to acquire connection" |
| Espera por conexión | ~0 ms | blocking time creciente | `agroal_blocking_time_max_milliseconds` |
| Tiempo de query promedio | < 100 ms | > 500 ms | APM / logs |
| Tamaño de respuesta gzip | < 100 KB | > 500 KB | logs HTTP |
| Errores de conexión tras reanudar Neon | 0 | > 0 en la primera hora | logs: `SQLException: connection closed` |

### Riesgo abierto: el pool no está calibrado para Neon

Los valores actuales (`min-size=10`, `max-size=50`, `idle-removal-interval=5m`)
son razonables contra Postgres directo, pero **no** contra Neon con PgBouncer:

- **`max-size=50` sobra.** Con multiplexación, el techo lo impone
  `default_pool_size` de PgBouncer (`0.9 × max_connections` del compute), no el
  pool del backend. 50 sockets no compran concurrencia: compran slots del
  compute y memoria.
- **`min-size=10` + `idle-removal-interval=5m` chocan con la suspensión de
  Neon.** Los planes Free/Launch suspenden el compute tras ~5 min de inactividad;
  el pool cierra sus sockets justo en esa ventana. El síntoma esperado son
  errores transitorios de conexión después del cold start, no saturación.
- **Slots del compute.** Con 0.25 CU, `max_connections=112` (105 útiles). Un
  `min-size=10` retiene 10 slots permanentemente, y PgBouncer mantiene su
  propio pool hacia el compute.

El backend **es compatible con PgBouncer**: el RLS usa
`set_config('app.current_user_id', ..., true)` (= `SET LOCAL`, soportado en
transaction mode) y el código no usa advisory locks, `LISTEN`/`NOTIFY`, tablas
temporales ni `SET` de sesión.

**Ajuste recomendado, una vez haya métricas del compute real:** `max-size` 10-15,
`min-size` 1-2, `idle-removal-interval` 2m, `max-lifetime` 10m,
`acquisition-timeout` 5s. La tabla completa con el porqué de cada valor está en
`openspec/changes/back/2026-11-19-performance-optimization/tasks.md`
("Riesgo abierto: pool sin calibrar"). **Ese cambio no se aplica en este
commit**: recalibrar a ciegas, sin medir contra Neon, sería cambiar el pool a
ciegas.

### Rollback

Todo es reversible sin pérdida de datos:

- Índices: `DROP INDEX IF EXISTS idx_acta_*` (el `-- rollback` de cada changeset
  lo hace por sí solo).
- Lazy: volver a `FetchType.EAGER` y quitar `@BatchSize`.
- Batch endpoint: es aditivo; los endpoints viejos siguen existiendo.
- gzip y pool: son propiedades; volver a los valores anteriores.
