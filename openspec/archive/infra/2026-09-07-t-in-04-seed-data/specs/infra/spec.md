# Spec — T-IN-04: Seed Data (Retrospective)

## Garantías

### G1: Datos existen
```sql
SELECT COUNT(*) FROM provincia;  -- 1
SELECT COUNT(*) FROM canton;     -- 3
SELECT COUNT(*) FROM jrv;        -- ~450
SELECT COUNT(*) FROM cargo;      -- ~12
```

### G2: Usuario MASTER existe
```sql
SELECT * FROM usuario WHERE email='admin@electoral.ec';
-- retorna fila del admin
```

## Status
✅ Auditoría de especificación.
