# Spec — T-FE-07: Formulario de Registro de Acta

## Escenarios

### S1 — Ingresa votos, suma válida

```gherkin
GIVEN    formulario de acta cargado
AND      4 candidatos, campo nulos, blancos, sufragantes
WHEN     ingresa: candidato1=100, candidato2=50, candidato3=30, candidato4=20
AND      nulos=5, blancos=3, sufragantes=208
THEN     validación pasa (suma = 208)
AND      botón "Guardar" está enabled
```

### S2 — Suma no válida

```gherkin
GIVEN    formulario con los mismos valores
WHEN     cambia sufragantes a 200 (menor que 208)
THEN     validación falla
AND      mensaje "La suma de votos no coincide"
AND      botón "Guardar" está disabled
```

### S3 — Guardar exitoso

```gherkin
GIVEN    suma válida
WHEN     hace click en "Guardar"
AND      POST /api/observer/actas retorna {id, estado: 'ENVIADA'}
THEN     toast muestra "Acta guardada"
AND      navega a /observer/home
```

### S4 — Error al guardar

```gherkin
GIVEN    suma válida
WHEN     hace click en "Guardar"
AND      POST /api/observer/actas retorna error 500
THEN     error message se muestra
AND      usuario permanece en el form
```

### S5 — FormArray dinámico

```gherkin
GIVEN    formulario con 4 candidatos
WHEN     usuario hace click en "Agregar candidato"
THEN     nuevo FormGroup se agrega a FormArray
AND     nuevo campo de votos aparece
```

## Mutaciones

### M1 — Comentar customValidator

Si se quita el `sumaValidator()` del FormGroup, formulario debe ser válido aunque suma no cuadre. Verificación: ingresa suma incompleta, botón debe estar enabled (detectar falla).

### M2 — Remover POST en guardar

Si se comenta `this.http.post()` en guardar(), nada debe ocurrir. Verificación: click guardar, no navega ni muestra toast.

## Garantías

- **G1:** Suma se valida en tiempo real
- **G2:** Botón deshabilitado si suma no cuadra
- **G3:** POST /api/observer/actas se ejecuta al guardar
- **G4:** FormArray permite candidatos dinámicos
