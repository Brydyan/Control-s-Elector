# Spec — T-IN-03: RLS (Retrospective)

## Garantías

### G1: RLS policies existen
```sql
SELECT * FROM pg_policies WHERE tablename='acta';
-- debe listar policies para cada rol
```

### G2: OBSERVER no ve actas de otros
```sql
SET LOCAL app.current_user_id = 'observer-1-id';
SELECT * FROM acta;
-- retorna solo actas donde observer-1 está autorizado
```

## Status
✅ Auditoría de especificación.
