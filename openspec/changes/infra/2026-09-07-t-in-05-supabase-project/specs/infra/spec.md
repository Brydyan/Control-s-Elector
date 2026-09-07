# Spec — T-IN-05: Supabase Project

## Escenarios

### S1 — Proyecto creado y accesible

```gherkin
GIVEN    usuario registrado en supabase.com
WHEN     crea nuevo proyecto "Control-Electoral-Prod" en us-east-1
THEN     BD PostgreSQL está lista
AND      proyecto visible en dashboard Supabase
AND      URL API: https://[project-id].supabase.co/rest/v1
```

### S2 — Object Storage bucket creado

```gherkin
GIVEN    proyecto Supabase ready
WHEN     crear bucket "evidencias" public
THEN     bucket visible en Storage dashboard
AND      carpeta interior: evidencias/ (para organizar)
```

### S3 — API keys generadas

```gherkin
GIVEN    proyecto listo
WHEN     generar keys en Settings → API
THEN     dos keys disponibles:
         - anon_key (para cliente, público)
         - service_role_key (para backend, secreto)
```

### S4 — Conexión desde local

```gherkin
GIVEN    anon_key y URL disponibles
WHEN     cliente Angular hace supabase.auth.signUp() o supabase.from('table').select()
THEN     request llega a Supabase
AND      respuesta retorna datos (o auth challenge)
```

## Mutaciones

### M1 — API key anon deshabilitada

Si se deshabilita anon_key, cliente no puede autenticarse. Verificación: login debe fallar.

### M2 — Bucket "evidencias" privado

Si bucket es privado (acceso solo con service_role), cliente no puede subir fotos. Verificación: upload debe fallar con 403.

## Garantías

- **G1:** Proyecto Supabase existe
- **G2:** BD PostgreSQL accesible
- **G3:** Object Storage bucket ready
- **G4:** API keys disponibles y funcionales
