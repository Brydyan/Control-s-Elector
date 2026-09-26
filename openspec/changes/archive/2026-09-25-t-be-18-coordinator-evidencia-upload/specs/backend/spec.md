# Spec — T-BE-18: Subida de Evidencia Fotográfica por Coordinador

## Escenarios

### S1 — Subida exitosa de evidencia fotográfica por coordinador

```gherkin
GIVEN    acta existente con estado ENVIADA o EN_DISPUTA sin evidencia fotográfica
AND      usuario con rol COORDINATOR autenticado
WHEN     envía POST /api/v1/coordinator/actas/{id}/imagen con multipart clave 'imagen'
THEN     backend almacena el archivo en S3
AND      persiste el registro en la tabla evidencia vinculada al acta
AND      registra evento de auditoría EVIDENCIA_CONTINGENCIA_COORDINADOR
AND      retorna HTTP 201 Created con EvidenciaResponseDto
```

### S2 — Rechazo a roles no autorizados

```gherkin
GIVEN    usuario con rol OBSERVER o GUEST
WHEN     intenta POST /api/v1/coordinator/actas/{id}/imagen
THEN     Spring Security rechaza con HTTP 403 Forbidden
```

### S3 — Rechazo de clave multipart incorrecta

```gherkin
GIVEN    usuario COORDINATOR
WHEN     envía petición multipart con clave 'file' o 'foto'
THEN     backend responde HTTP 400 Bad Request por MissingServletRequestPartException
```

### S4 — Compensación transaccional ante fallo de BD

```gherkin
GIVEN    fallo simulado en la persistencia del entity Evidencia
WHEN     el coordinador sube una imagen
THEN     la transacción de BD hace rollback
AND      el listener de sincronización elimina el objeto subido a S3
AND      no quedan archivos huérfanos en el bucket
```

## Garantías

- **G1:** Autorización exclusiva a `COORDINATOR` y `MASTER`.
- **G2:** Reutilización de validaciones de formato y tamaño de `T-BE-14`.
- **G3:** Trazabilidad forense completa en tabla `audit_log`.
