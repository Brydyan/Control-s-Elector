# Spec — T-FE-02: Routing con RBAC Guards

## Escenarios

### S1 — Sin token, intenta acceder a /observer/home

```gherkin
GIVEN    usuario sin token en memoria
WHEN     navega a /observer/home
THEN     authGuard retorna '/login'
AND      navegador redirige a /login
```

### S2 — Token válido, rol OBSERVER, accede a /observer/home

```gherkin
GIVEN    usuario con token rol=OBSERVER
WHEN     navega a /observer/home
THEN     authGuard retorna true
AND      roleGuard retorna true
AND      componente HomeComponent carga
```

### S3 — Token válido, rol ADMIN, intenta acceder a /master/config

```gherkin
GIVEN    usuario con token rol=ADMIN
AND      ruta /master/config requiere MASTER
WHEN     navega a /master/config
THEN     authGuard retorna true
AND      roleGuard retorna false (rol no permitido)
AND      navegador redirige a /
```

### S4 — Lazy-load: feature no se carga hasta necesitarse

```gherkin
GIVEN    app inicializada
AND      rutas con loadChildren() para features
WHEN     se lista archivos cargados en browser
THEN     features/{observer,coordinator,master}/observer.routes.js no está cargado
WHEN     navega a /observer/home
THEN     archivo observer.routes.js se carga dinámicamente
```

## Mutaciones

### M1 — Remover authGuard de una ruta

Si se quita `canActivate: [authGuard]` de /coordinator/*, debe ser accesible sin token. Verificación: intentar acceder sin token, debe cargar (detectar falla).

### M2 — Cambiar roleGuard a permitir ADMIN en /master/*

Cambiar guard para que ADMIN pueda entrar a /master/*. Debe cargarse (luego restaurar).

## Garantías

- **G1:** Sin token → no acceso a rutas protegidas
- **G2:** Rol incorrecto → redirección
- **G3:** Lazy-load funciona (features se cargan bajo demanda)
