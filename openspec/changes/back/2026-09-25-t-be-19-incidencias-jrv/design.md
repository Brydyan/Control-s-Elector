# Design — T-BE-19: Registro de Incidencias Operativas de JRV sin Acta

---

## D1 — Modelo Relacional y Enum de Dominio

**Decisión**: Tabla `incidencia_jrv` aislada de `acta`:
```sql
CREATE TABLE incidencia_jrv (
    id BIGSERIAL PRIMARY KEY,
    jrv_id BIGINT NOT NULL REFERENCES jrv(id),
    tipo VARCHAR(50) NOT NULL,
    descripcion TEXT NOT NULL,
    evidencia_url VARCHAR(500),
    reportada_por_id BIGINT NOT NULL REFERENCES usuario(id),
    fecha_registro TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_incidencia_jrv_id ON incidencia_jrv(jrv_id);
CREATE INDEX idx_incidencia_fecha ON incidencia_jrv(fecha_registro);
```

Valores de `TipoIncidenciaJrv`:
`JRV_NO_INSTALADA`, `IMPEDIMENTO_VEEDOR`, `URNA_VIOLENTADA`, `FALTA_PAPELETAS`, `CORTE_ENERGIA`, `DISTURBIOS_RECINTO`, `OTRO`.

**Alternativa descartada — Guardar como un estado más de Acta**: Un acta representa votos de una dignidad. Si la mesa no se instaló, no existe acta física ni votos que registrar. Tratarlo como acta corrompe el modelo de datos.

---

## D2 — Endpoints y RBAC

**Decisión**:
- `POST /api/v1/observer/incidencias`:
  - Requiere `@PreAuthorize("@authz.has('acta:upload')")`.
  - El veedor solo puede reportar sobre JRVs asignadas a su recinto (validación en servicio).
- `GET /api/v1/coordinator/incidencias`:
  - Requiere `@PreAuthorize("hasAnyRole('COORDINATOR', 'MASTER', 'ADMIN')")`.
  - Retorna `List<IncidenciaResponseDto>` ordenado por `fechaRegistro DESC`.

---

## D3 — Notificación hacia Sala de Guerra

**Decisión**: Al persistir una incidencia, `IncidenciaService` emite un evento de aplicación Spring (`IncidenciaRegistradaEvent`) que queda registrado en `audit_log` para ser consultado inmediatamente por los monitores del dashboard.
