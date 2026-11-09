# Spec: Neon Database T-IN-13

## RF-DB-01: Neon project creado

GIVEN usuario accede a Neon console
WHEN crea project `control-electoral-mvp`
THEN proyecto existe con:
- Database name: `electoral_db`
- Username: `electoral_user`
- Region: la misma que Railway (ej: us-east)

## RF-DB-02: Connection string válida

GIVEN Neon project
WHEN usuario copia connection string
THEN tiene forma: `postgresql://user:pass@host:5432/electoral_db?sslmode=require`

## RF-DB-03: Liquibase migrations se ejecutan

GIVEN backend apunta a Neon + SPRING_DATASOURCE_URL correct
WHEN backend arranca con `ddl-auto=validate`
THEN schema de Neon coincide exactamente con entidades JPA:
- 25 tablas (usuario, sesion, menu, permiso, acta, jrv, etc.)
- Constraints de FK íntegros
- Índices presentes (uuid, estado, etc.)

## RF-DB-04: VPC endpoint configurado (o IP whitelist)

GIVEN Neon VPC endpoint habilitado
WHEN Railway intenta conectar
THEN conecta exitosamente (no rechaza por IP)

## RF-DB-05: Healthcheck DB funciona

GIVEN backend en Railway
WHEN hace `SELECT 1` contra Neon
THEN responde en <100ms (normal latency)

## RF-DB-06: Backup automation activa

GIVEN Neon project
WHEN usuario ve "Backups" en console
THEN muestra snapshots automáticos diarios + opción PITR

## RF-DB-07: Connection pooling respeta límites

GIVEN HikariCP MaxPoolSize = 10
WHEN hay 10 conexiones activas
THEN la 11ª espera en cola (no abre nueva)
Y tras timeout (30s) falla con `HikariPool cannot get a connection`

## RNF-DB-01: Performance en Neon aceptable

GIVEN query de dashboard que agrupa actas (COUNT, GROUP BY)
WHEN ejecuta contra Neon
THEN responde en <500ms

## RNF-DB-02: Zero downtime migration

GIVEN schema ya en Neon
WHEN deploy nuevo add column
THEN Liquibase non-blocking (no bloquea reads)

## RNF-DB-03: RLS verificado

GIVEN RLS policies en `usuario`, `acta`, `evidencia`
WHEN usuario registrada_por_id = 5
THEN no puede ver actas con registrada_por_id ≠ 5
