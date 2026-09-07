# Spec — T-FE-10: Coordinator: Gestión Global de Actas

## Escenarios

### S1 — Cargar todas las actas

```gherkin
GIVEN    usuario COORDINATOR autenticado
WHEN     navega a /coordinator/actas
THEN     GET /api/coordinator/actas
AND      tabla muestra todas las actas (globales, no solo suyas)
```

### S2 — Filtrar por estado

```gherkin
GIVEN    tabla cargada
WHEN     usuario elige "EN_DISPUTA" en dropdown estado
THEN     solo actas en estado EN_DISPUTA se muestran
```

### S3 — Disputar acta

```gherkin
GIVEN    acta con estado ENVIADA
WHEN     usuario hace click en botón "Disputar"
THEN     ConfirmDialog se abre
AND      usuario confirma
AND      PUT /api/coordinator/actas/{id}/disputar
AND      backend retorna estado=EN_DISPUTA
AND      tabla se actualiza
```

### S4 — Aprobar acta

```gherkin
GIVEN    acta con estado CORREGIDA
WHEN     usuario hace click "Aprobar"
THEN     ConfirmDialog, usuario confirma
AND      PUT /api/coordinator/actas/{id}/aprobar
AND      estado → APROBADA
```

### S5 — Usuario OBSERVER no puede acceder

```gherkin
GIVEN    usuario OBSERVER
WHEN     intenta navegar a /coordinator/actas
THEN     roleGuard bloquea
AND      redirige a /observer/home (o /)
```

## Mutaciones

### M1 — Remover GET en init

Si se comenta GET /api/coordinator/actas, tabla vacía siempre. Verificación: navegar, tabla vacía (detectar falla).

### M2 — Remover confirmación

Si se quita ConfirmDialog, acción ocurre inmediatamente sin confirmar. Verificación: click botón, debe abrir dialog pero no lo hace (detectar falla).

## Garantías

- **G1:** GET /api/coordinator/actas trae todas las actas
- **G2:** Filtro por estado funciona
- **G3:** Acciones (disputar, aprobar) disparan PUT
- **G4:** roleGuard previene acceso de OBSERVER
