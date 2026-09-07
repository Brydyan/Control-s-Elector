# Spec — T-FE-04: Interceptors HTTP

## Escenarios

### S1 — JwtInterceptor agrega Authorization header

```gherkin
GIVEN    TokenService tiene token='abc123'
WHEN     llamas http.get('/api/observer/mis-actas')
AND      interceptors se ejecutan
THEN     request HTTP tiene header Authorization: Bearer abc123
```

### S2 — GET /api/observer/mis-actas sin token

```gherkin
GIVEN    TokenService token=null
WHEN     llamas http.get('/api/observer/mis-actas')
THEN     request NO tiene Authorization header (o es 'Bearer null')
AND      backend retorna 401
AND      RefreshInterceptor detecta 401
AND      llama authService.refresh()
```

### S3 — Refresh exitoso, reintento de request original

```gherkin
GIVEN    request original retorna 401
AND      refresh() retorna nuevo token
WHEN     RefreshInterceptor maneja el 401
THEN     request original se reintenta con nuevo token
AND      si reintento retorna 200, Observable emite ese 200
```

### S4 — Refresh falla (401), propaga error

```gherkin
GIVEN    request original retorna 401
AND      refresh() también retorna 401 (token inválido)
WHEN     RefreshInterceptor intenta refresh()
THEN     la cadena propaga error 401
AND      Observable del caller retorna error
```

## Mutaciones

### M1 — Remover JwtInterceptor

Si se quita JwtInterceptor, requests no tienen Authorization header. Verificación: hacer request, inspeccionar headers, no debe tener Authorization.

### M2 — RefreshInterceptor no reintenta en 401

Si se borra la lógica de reintento, 401 propaga directamente sin intentar refresh. Verificación: generar 401, debe fallar en lugar de reintentarse.

## Garantías

- **G1:** Authorization header se agrega automáticamente
- **G2:** 401 dispara refresh automático
- **G3:** Request original se reintenta con nuevo token
