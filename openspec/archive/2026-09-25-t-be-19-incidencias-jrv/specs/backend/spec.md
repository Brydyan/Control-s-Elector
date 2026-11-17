# Spec — T-BE-19: Registro de Incidencias Operativas de JRV sin Acta

## Escenarios

### S1 — Reporte de incidencia por veedor

```gherkin
GIVEN    veedor autenticado asignado a la JRV 15
WHEN     envía POST /api/v1/observer/incidencias con { jrvId: 15, tipo: "IMPEDIMENTO_VEEDOR", descripcion: "Policía impidió paso al aula" }
THEN     backend almacena la incidencia en base de datos
AND      vincula automáticamente el reportadaPorId del token
AND      retorna HTTP 201 Created con el IncidenciaResponseDto generado
```

### S2 — Consulta de alertas por coordinador

```gherkin
GIVEN    coordinador autenticado
WHEN     consulta GET /api/v1/coordinator/incidencias
THEN     backend retorna la lista de incidencias registradas con datos de mesa, parroquia y timestamp
```

### S3 — Rechazo de JRV no existente

```gherkin
GIVEN    petición con jrvId inexistente (ej. 999999)
WHEN     veedor intenta registrar la incidencia
THEN     backend responde HTTP 404 Not Found por recurso no encontrado
```

## Garantías

- **G1:** Migración de esquema en Liquibase sin romper datos existentes.
- **G2:** Control de acceso estricto por rol y asignación de veedor.
- **G3:** Trazabilidad de tiempo y usuario en cada reporte.
