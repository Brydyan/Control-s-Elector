# Spec — T-IN-01: Docker Compose (Retrospective Audit)

## Garantías que documentamos

### G1: Docker Compose ejecuta sin errores
```bash
docker-compose up -d
docker-compose ps  # muestra "healthy"
```

### G2: PostgreSQL inicia
```bash
psql -h localhost -U electoral_user -d electoral_db
# conexión exitosa
```

## Mutaciones (verificación por eliminación)

- M1: Remover volumen → datos se pierden (confirma persistencia)
- M2: Cambiar locale a C → ordenamiento texto cambia

## Status
✅ Auditoría de especificación — no requiere implementación.
