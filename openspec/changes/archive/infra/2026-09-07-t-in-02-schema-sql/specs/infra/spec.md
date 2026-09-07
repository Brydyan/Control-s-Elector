# Spec — T-IN-02: Schema SQL (Retrospective)

## Garantías

### G1: Script SQL ejecuta
```bash
psql -h localhost -U electoral_user -d electoral_db -f 01-schema.sql
# sin errores
```

### G2: Tablas existen
```sql
SELECT tablename FROM pg_tables WHERE schemaname='public';
-- debe listar usuario, acta, jrv, cargo, etc.
```

## Status
✅ Auditoría de especificación.
