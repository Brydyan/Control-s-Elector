# Spec — T-IN-09: Vercel Deploy

## Escenarios

### S1 — Deploy inicial

```gherkin
GIVEN    GitHub repo con Angular app
WHEN     conecta repo a Vercel project
THEN     Vercel detecta Angular (package.json)
AND      inicia build automático
AND      deploy completa
```

### S2 — App accesible en prod

```gherkin
GIVEN    deploy completado
WHEN     navega a https://control.twintailcs.xyz
THEN     Angular app carga (HTML + bundles)
AND      navbar visible, login funciona
```

### S3 — SPA routing: refresh no da 404

```gherkin
GIVEN    app cargada en /observer/home
WHEN     usuario refresca navegador (F5)
THEN     servidor retorna index.html (no 404)
AND      Angular router parsea ruta
AND      componente observer/home carga nuevamente
```

### S4 — Preview en PR

```gherkin
GIVEN    PR abierto en GitHub
WHEN     Vercel detecta PR
THEN     genera Preview URL (ej: https://control-pr-123.vercel.app)
AND      URL funciona, muestra cambios del PR
```

## Mutaciones

### M1 — No configurar SPA routing

Si falta vercel.json, refresh en /observer/home retorna 404. Verificación: refresh debe funcionar.

### M2 — Build falla

Si output dir es incorrecto, Vercel no encuentra archivos. Verificación: build debe tener dist/app/browser/index.html.

## Garantías

- **G1:** App accesible en https://control.twintailcs.xyz
- **G2:** SPA routing funciona (no 404 en refresh)
- **G3:** Deploy automático en push
- **G4:** Preview URLs en PRs
