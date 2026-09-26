# Spec — T-FE-07: Formulario de Registro de Acta (Wizard 3 Pasos)

## Escenarios

### S1 — Carga dinámica de candidatos y restauración de borrador

```gherkin
GIVEN    veedor en /observer/jrv/:jrvId/cargo/:cargoId/registrar
WHEN     RegistrarActaPage inicializa
THEN     consulta candidatos y partidos para el cargoId
AND      si existe un borrador previo en local storage, rellena automáticamente los votos ingresados
```

### S2 — Validación en tiempo real del cuadre de votos

```gherkin
GIVEN    formulario en paso VOTOS con candidatos cargados
WHEN     usuario ingresa votos a candidatos, votos en blanco, nulos y total sufragantes
THEN     se actualizan reactivamente: totalVotosComputados, diferenciaChecksum e isChecksumValid
AND      si totalVotosComputados === totalSufragantes (y totalSufragantes > 0), el indicador muestra cuadre exacto
```

### S3 — Navegación del Wizard: Votos a Imagen

```gherkin
GIVEN    usuario en paso VOTOS
WHEN     presiona "Siguiente: Respaldo Fotográfico"
THEN     currentStep cambia a 'IMAGEN'
AND      se despliega el selector de fotografía y captura
```

### S4 — Carga y previsualización de fotografía

```gherkin
GIVEN    usuario en paso IMAGEN
WHEN     selecciona o captura una imagen
THEN     se genera la URL de vista previa (DataURL)
AND      se habilita el botón "Siguiente: Revisión"
```

### S5 — Guardado como Borrador (Permite Inconsistencias Aritméticas)

```gherkin
GIVEN    acta con digitación parcial (suma aún no cuadra)
WHEN     usuario presiona "Guardar como Borrador"
THEN     envía payload a POST /api/v1/observer/actas/borrador
AND      backend responde con éxito (HTTP 200)
AND      si hay imagen adjunta, se envía a POST /api/v1/observer/actas/:id/imagen
```

### S6 — Envío de Acta Final (Exige Cuadre Estricto)

```gherkin
GIVEN    acta con cuadre perfecto (isChecksumValid === true)
WHEN     usuario presiona "Confirmar y Transmitir Acta"
THEN     envía payload a POST /api/v1/observer/actas
AND      si hay imagen adjunta, ejecuta POST /api/v1/observer/actas/:id/imagen
AND      redirige a /observer/jrv/:jrvId/cargos tras confirmación visual
```

### S7 — Bloqueo de Envío Final ante Descuadre

```gherkin
GIVEN    acta con diferenciaChecksum !== 0
WHEN     usuario intenta transmitir acta final
THEN     la acción se rechaza
AND      se muestra mensaje de error: "Verificación electoral inválida: los votos no cuadran"
```

## Garantías

- **G1:** Invariante matemático verificado antes del envío final.
- **G2:** Contrato JSON idéntico a `ActaRequestDto` del backend Spring Boot.
- **G3:** Subida de evidencia encadenada usando `@RequestPart("imagen")`.
