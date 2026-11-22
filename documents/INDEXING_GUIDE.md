# Guía de índices

> Cómo se decide, se nombra y se verifica un índice en este proyecto.

## Convención de nombres

`idx_<tabla>_<columnas>` para índices comunes y `uk_<tabla>_<regla>` para únicos.
Las migraciones viejas (`010`) usan `ix_`, pero desde `044` la convención viva es
`idx_`; un índice nuevo sigue la convención viva, no la del SDD que lo pida.

## Inventario actual sobre `acta`

| Índice | Columnas | Para qué |
|--------|----------|----------|
| `uk_acta_activa` | `(jrv_id, cargo_id)` parcial por estado | impide dos actas activas de la misma JRV y cargo |
| `idx_acta_estado` | `(estado)` | filtros por estado en el dashboard |
| `idx_acta_aprobada_por` | `(aprobada_por)` | actas aprobadas por un usuario |
| `idx_acta_registrada_por` | `(registrada_por)` | actas de un veedor |
| `idx_acta_jrv_id` | `(jrv_id)` | listados por JRV |
| `idx_acta_cargo_id` | `(cargo_id)` | agregados por cargo |
| `idx_acta_fecha_registro_desc` | `(fecha_registro DESC)` | orden del dashboard sin sort |
| `idx_acta_jrv_cargo_estado` | `(jrv_id, cargo_id, estado)` | verificación de acta activa |

Y sobre `acta_detalle`: `UNIQUE(acta_id, candidato_id)` (de `010`) e
`idx_acta_detalle_candidato_id`.

## Regla del prefijo

Antes de crear un índice, revisar si otro ya cubre el filtro. Un btree
`(a, b)` sirve las consultas por `a` y por `(a, b)`, pero no por `b` sola.

Ese fue el motivo de descartar `idx_acta_detalle_acta_id`: el `UNIQUE(acta_id,
candidato_id)` ya resuelve todo filtro por `acta_id`. Un índice suelto solo
agrega costo de escritura.

## Cuándo agregar uno

1. La consulta está en un camino caliente (listado, dashboard, validación de
   acta), no en un reporte esporádico.
2. El filtro o el orden usa la columna.
3. `EXPLAIN ANALYZE` muestra `Seq Scan` sobre una tabla que ya tiene volumen.

## Cómo verificar

```sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT * FROM acta
WHERE jrv_id = 1 AND cargo_id = 2 AND estado = 'ENVIADA';
```

Se espera `Index Scan` o `Index Only Scan` sobre `idx_acta_jrv_cargo_estado`.
Un `Seq Scan` con pocas filas es normal: PostgreSQL prefiere recorrer la tabla
cuando el resultado es una fracción grande de ella.

## Migraciones

- Una migración por tema, con `-- rollback` al final.
- `CREATE INDEX IF NOT EXISTS` para que re-ejecutar no rompa.
- Se registra en `db.changelog-master.yaml` al final, en orden.
- En producción el `CREATE INDEX` bloquea escrituras: con volumen real, evaluar
  `CREATE INDEX CONCURRENTLY` en un changeset aparte con `runInTransaction: false`.
