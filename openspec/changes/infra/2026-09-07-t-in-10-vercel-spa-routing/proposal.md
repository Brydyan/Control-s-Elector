# Proposal — T-IN-10: vercel.json para SPA

## Qué se construye
Archivo `vercel.json`: configuración que redirige todas las rutas a `index.html` para que Angular Router funcione correctamente.

## Por qué
- Sin esto: refresh en `/observer/mis-actas` retorna 404 (servidor busca archivo físico)
- Con esto: todas las rutas 404 → index.html → Angular Router maneja ruta
- Standard SPA pattern

## Alcance

| Elemento | Incluye | No incluye |
|----------|---------|-----------|
| Routing | rewrite 404 → index.html | custom status codes |
| Headers | Content-Type, cache headers | authentication headers |
| Redirects | 404 catch-all | 301/302 redirects |

## Criterio de aceptación

- [ ] Archivo vercel.json creado en `frontend/app/`
- [ ] Configuración: rewrite `/**` → `index.html` con status 200
- [ ] Refresh en rutas (ej: /observer/home) no da 404
- [ ] Test: dos deploy con/sin vercel.json, medir diferencia

## Fuera de alcance

- Custom 404 page
- Redireccion específica (ej: /admin → /dashboard)

## Dependencias

- T-IN-09 (deploy Vercel ya running)

## Decisiones a tomar

**D1 — Status code:** 200 (rewrite silencioso, no redirect 301).
