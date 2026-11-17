# Spec — T-FE-10: Coordinator: Validación, Auditoría y Gestión Global de Actas

## Escenarios

### S1 — Carga de actas con sobre paginado `{ data, meta }`

```gherkin
GIVEN    usuario con rol COORDINATOR o MASTER autenticado
WHEN     navega a /coordinator/actas
THEN     CoordinatorService.getActas() consulta GET /api/v1/coordinator/actas
AND      valida que la respuesta contenga el sobre { data: [...], meta: { page, size, totalElements, totalPages } }
AND      renderiza las filas de actas con badges de estado y datos de JRV
```

### S2 — Filtrado en servidor por estado y dignidad

```gherkin
GIVEN    listado de actas cargado
WHEN     el coordinador selecciona estado="EN_DISPUTA" o una dignidad específica
THEN     se dispara una nueva petición GET /api/v1/coordinator/actas con los query params correspondientes
AND      la tabla se refresca con los resultados devueltos por el backend
```

### S3 — Navegación al detalle y Split-View 50/50

```gherkin
GIVEN    tabla de actas
WHEN     el coordinador hace click en un acta
THEN     navega a /coordinator/actas/:id
AND      en pantalla de escritorio (>= 992px) se presenta el diseño Split-View:
         - Panel izquierdo: votos, cheques de integridad, historial y acciones
         - Panel derecho: visor de foto de evidencia con controles de zoom/rotación
```

### S4 — Aprobación de acta con diálogo de confirmación

```gherkin
GIVEN    acta en estado ENVIADA o CORREGIDA con cuadre válido
WHEN     el coordinador presiona "Aprobar Acta" y confirma el diálogo
THEN     se envía PUT /api/v1/coordinator/actas/:id/aprobar
AND      al responder 200, recarga el detalle y muestra banner de éxito
```

### S5 — Poner acta en disputa con motivo obligatorio

```gherkin
GIVEN    acta con inconsistencias o tachaduras visibles en la foto
WHEN     el coordinador presiona "Poner en Disputa"
THEN     se abre el modal de disputa
AND      si intenta enviar con motivo vacío, se muestra error de validación
WHEN     ingresa el motivo y confirma
THEN     se envía PUT /api/v1/coordinator/actas/:id/disputar con { motivo: "..." }
AND      el acta pasa al estado EN_DISPUTA
```

### S6 — Corrección aritmética de votos

```gherkin
GIVEN    acta en estado EN_DISPUTA
WHEN     el coordinador abre el modal de corrección y modifica los valores numéricos
THEN     el modal valida en tiempo real que la suma de candidatos + blancos + nulos coincida con el total de sufragantes
WHEN     el cuadre es exacto y confirma
THEN     se envía PUT /api/v1/coordinator/actas/:id/corregir con el payload corregido
AND      el acta pasa al estado CORREGIDA lista para posterior aprobación
```

### S7 — Detección de conflicto concurrente (HTTP 409)

```gherkin
GIVEN    dos coordinadores auditando la misma acta al mismo tiempo
WHEN     el primer coordinador aprueba el acta
AND      el segundo intenta disputarla
THEN     el backend rechaza con HTTP 409 Conflict
AND      la interfaz detecta el conflicto, notifica al usuario y recarga la verdad actualizada del backend
```

## Garantías

- **G1:** Estricto cumplimiento del sobre `{ data, meta }` en todos los listados de actas.
- **G2:** Verbos RESTful correctos (`PUT` para transiciones de estado, `DELETE` para anulación).
- **G3:** Auditoría de cuadre matemático antes de permitir correcciones.
- **G4:** Cero llamadas a columnas inexistentes (`acta.storage_url`).
