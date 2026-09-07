# Spec — T-IN-10: vercel.json SPA Routing

## Escenarios

### S1 — vercel.json existe

```gherkin
GIVEN    frontend/app/vercel.json presente
WHEN     Vercel lee configuración
THEN     ruta rewrite `/**` → `/index.html` activada
```

### S2 — Refresh en ruta funciona

```gherkin
GIVEN    app en /observer/home
WHEN     usuario refresca (F5)
THEN     servidor retorna /index.html (no 404)
AND      Angular Router parsea URL
AND      componente observer/home carga
```

### S3 — /api requests no son rewriteadas

```gherkin
GIVEN    cliente hace GET /api/auth/login
WHEN     Vercel procesa request
THEN     ruta NO es rewriteada (pasa a API Railway)
AND      request llega a backend
```

### S4 — Status code 200

```gherkin
GIVEN    refresh en /unknown-route
WHEN     servidor retorna index.html
THEN     HTTP status = 200 (no 404)
```

## Mutaciones

### M1 — Remover vercel.json

Si no existe archivo, refresh en ruta debe dar 404. Verificación: refresh falla.

### M2 — Cambiar pattern a solo /app/**

Si rewrite es muy restrictivo, rutas fuera /app/ dan 404. Verificación: /observer/home → 404 (debe funcionar).

## Garantías

- **G1:** vercel.json existe
- **G2:** Rewrite 404 → index.html funciona
- **G3:** Refresh en rutas no da 404
- **G4:** /api requests no son afectadas
