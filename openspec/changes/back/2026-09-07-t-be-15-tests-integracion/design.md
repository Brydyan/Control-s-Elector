# Design — T-BE-15 · Tests de integración

---

## D1 — PostgreSQL con Testcontainers, no H2

**Decisión**: los tests de integración corren contra un contenedor PostgreSQL 16 real, con
Liquibase aplicando el changelog completo.

**Alternativa descartada — H2 en memoria con `ddl-auto=create-drop`**, que es lo que propone
`T-BE-15.md:48-97`. Es más rápida y mide otra cosa:

| Garantía | Qué pasa en H2 |
|---|---|
| Locking pesimista | `SELECT FOR UPDATE` no se comporta igual; el test de duplicados probaría un mecanismo distinto del que corre en producción |
| RLS | **no existe en H2**. El test de acceso denegado pasaría siempre, sin medir nada |
| Esquema | `create-drop` salta Liquibase: el orden roto de migraciones que tenemos hoy quedaría invisible |
| Tipos | `jsonb`, `gen_random_uuid()`, `TIMESTAMPTZ` no tienen equivalente exacto |

Un test verde que corre contra un motor distinto del de producción es el peor resultado
posible: da confianza sin dar garantía. Es §4.5 —comportamiento que depende de dónde corre—
con el lado permisivo en el arnés.

**Coste aceptado**: la suite tarda más porque levanta contenedores. Se mitiga reutilizando
un contenedor por suite (`@Testcontainers` con contenedor `static`), no uno por test.

**Lo que ya está**: `pom.xml` trae `spring-boot-testcontainers`,
`testcontainers-junit-jupiter` y `testcontainers-postgresql`. No hay que añadir dependencias
— hay que **usar** las que ya están.

---

## D2 — El test de locking usa concurrencia real

**Decisión**: dos hilos que intentan registrar la misma `jrv_id + cargo_id` a la vez,
sincronizados con un `CountDownLatch`. Se afirma que exactamente uno gana y el otro recibe
`JrvCargoYaRegistradoException`.

**Alternativa descartada — dos llamadas secuenciales.** Es lo que un test ingenuo hace, y
pasa igual **sin ningún lock**: la segunda llamada encuentra el acta de la primera con un
`SELECT` normal. El test quedaría verde con `findActaActivaConLock` sustituido por
`findActaActiva`, es decir, sin proteger nada.

**Cómo se distingue una de otra**: la mutación. Cambiar `findActaActivaConLock` por
`findActaActiva` **debe** hacer caer el test. Si no cae, el test es secuencial disfrazado.

**Riesgo conocido**: los tests de concurrencia son propensos a ser intermitentes. Se acota
con `CountDownLatch` para el arranque simultáneo y un timeout explícito. Si resulta
intermitente, se arregla la sincronización — **no se borra el test**: es el único que prueba
la garantía de unicidad, que es la que impide dos resultados para la misma mesa.

---

## D3 — El test de RLS tiene que poder fallar

**Decisión**: el test de acceso entre OBSERVERs se escribe de forma que **falle** si se
desactiva `RlsInterceptor`.

**Por qué importa aquí más que en otros sitios.** `RlsInterceptor` hoy:

- no tiene **ningún** test que lo cubra (verificado con el grafo de código);
- ejecuta `SET LOCAL app.current_user_id = ...` desde un filtro de servlet, es decir
  **fuera de la transacción** que después abrirá el repositorio. `SET LOCAL` sólo dura lo que
  dura la transacción en curso;
- **se traga cualquier excepción** (`catch (Exception e) { logger.warn(...) }`) y deja pasar
  la petición. Si la variable no se establece, no hay error: hay ausencia silenciosa de RLS;
- mientras tanto, `ActaService.registrar:77-82` afirma en un comentario que «RLS PostgreSQL
  + permisos JWT son el control de acceso vigente».

Un comentario que afirma una garantía, un interceptor sin tests, y un mecanismo que puede ser
un no-op. Es §4.3 y §4.6 a la vez.

**Alternativa descartada — dar por buena la RLS porque `02-rls.sql` existe y el interceptor
está registrado en `SecurityConfig`.** Que el archivo exista no dice que la policy se aplique
en tiempo de petición. **Ejecutá, no leas.**

**La comprobación que decide**: dentro de una petición autenticada, consultar
`SELECT current_setting('app.current_user_id', true)` y ver si devuelve el id o `null`. Si
devuelve `null`, RLS no está actuando y el resultado es un CRITICAL, no un detalle de tests.

---

## D4 — Un arnés de datos explícito, sin `@Sql` mágico

**Decisión**: una clase de apoyo que crea provincia → cantón → parroquia → JRV → cargo →
candidatos → usuarios por rol, y devuelve los ids que el test necesita.

**Alternativa descartada — depender del seed de Liquibase (`003-seed`, `009`).** Ata los
tests a datos que existen por otra razón y que cambian cuando cambie el seed. Un test que
falla porque alguien editó un `INSERT` de producción no dice nada sobre el código.

**Alternativa descartada — `@Sql` con scripts por test.** Se convierte en un segundo esquema
paralelo que hay que mantener sincronizado con Liquibase, y nadie comprueba que coincidan
(§4.1).

**La pregunta que hay que hacerle al arnés**: *¿qué NO sobreescribe?* En TASE, esa pregunta
encontró dos defectos. Aquí, lo que hay que mirar es si limpia `audit_log` y `notificacion`
entre tests: si no, los tests que cuentan filas de auditoría se contaminan entre sí y pasan o
fallan según el orden de ejecución.
