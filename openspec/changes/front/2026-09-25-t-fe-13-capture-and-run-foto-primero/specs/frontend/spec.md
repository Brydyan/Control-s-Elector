# Spec — T-FE-13: Modo "Foto Primero" (Capture & Run) y Cotas Electorales

## Escenarios

### S1 — Apertura del formulario inicia en paso IMAGEN

```gherkin
GIVEN    veedor en /observer/jrv/:jrvId/cargo/:cargoId/registrar
WHEN     la página carga
THEN     currentStep es igual a 'IMAGEN'
AND      el veedor ve inmediatamente la interfaz de captura o subida de fotografía
```

### S2 — Captura fotográfica habilita avance a votos

```gherkin
GIVEN    veedor en paso IMAGEN
WHEN     captura la fotografía del acta
THEN     se previsualiza la imagen
AND      se almacena en el borrador local
AND      se habilita el botón "Continuar a Digitación de Votos"
```

### S3 — Advertencia si sufragantes > 350

```gherkin
GIVEN    veedor en paso VOTOS
WHEN     ingresa totalSufragantes = 380
THEN     la aplicación muestra un aviso amarillo: "Advertencia: El padrón estándar es de 350 electores"
AND      permite continuar si la suma cuadra exactamente con 380
```

### S4 — Bloqueo estricto si sufragantes > 500

```gherkin
GIVEN    veedor en paso VOTOS
WHEN     ingresa por error totalSufragantes = 2500
THEN     la aplicación muestra un error rojo: "Límite CNE excedido: ninguna JRV puede superar 500 sufragantes"
AND      el botón de envío permanece completamente deshabilitado
```

### S5 — Invariante matemático exacto para dignidades estándar

```gherkin
GIVEN    cargo Alcalde con totalSufragantes = 300
WHEN     suma de candidatos + blancos + nulos = 295 (diferencia -5)
THEN     el sistema bloquea el envío final
WHEN     corrige los votos para sumar exactamente 300
THEN     el indicador muestra cuadre exitoso (diferencia 0) y habilita el paso final
```

## Garantías

- **G1:** Prioridad de captura de evidencia sobre la digitación.
- **G2:** Cotas electorales realistas (warning > 350, bloqueo > 500).
- **G3:** Invariante electoral estricto $\sum = \text{totalSufragantes}$.
