# Spec — T-FE-06: Observer: Lista de Actas Propias

## Escenarios

### S1 — Cargar lista de actas

```gherkin
GIVEN    usuario OBSERVER autenticado
WHEN     navega a /observer/home
THEN     HomeComponent carga
AND      llama GET /api/observer/mis-actas
AND      backend retorna [Acta{}, Acta{}, ...]
AND      lista se muestra en pantalla
```

### S2 — Filtrar por estado

```gherkin
GIVEN    lista de actas cargada
WHEN     usuario elige "ENVIADA" en dropdown estado
THEN     solo actas con estado ENVIADA se muestran
AND      otras desaparecen
```

### S3 — Click en acta navega a detalles

```gherkin
GIVEN    lista de actas
WHEN     usuario hace click en una acta
THEN     navega a /observer/acta-detail/{acta-id}
```

### S4 — Loading spinner mientras se carga

```gherkin
GIVEN    componente inicializado
WHEN     GET /api/observer/mis-actas está pending
THEN     spinner se muestra
WHEN     respuesta llega, spinner desaparece
```

### S5 — Lista vacía

```gherkin
GIVEN    usuario no ha registrado actas aún
WHEN     navega a /observer/home
AND      GET /api/observer/mis-actas retorna []
THEN     se muestra mensaje "Sin actas registradas"
```

## Mutaciones

### M1 — Remover GET en onInit

Si se comenta el this.http.get() en ngOnInit(), lista debe estar vacía siempre. Verificación: navegar, lista vacía (detectar falla).

### M2 — Filtro no funciona

Si se comenta la lógica de filtrado (this.actas.filter(...)), dropdown no tiene efecto. Verificación: cambiar filtro, lista no cambia (detectar falla).

## Garantías

- **G1:** GET /api/observer/mis-actas se llama en init
- **G2:** Filtro por estado funciona en frontend
- **G3:** Click navega a detalles
