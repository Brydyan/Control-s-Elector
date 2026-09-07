# Tasks — T-IN-07: Railway Deploy

## Fase A: Crear cuenta Railway + proyecto

- [ ] **A.1** — Ir a https://railway.app, login con GitHub
- [ ] **A.2** — Crear nuevo proyecto (vacío)
- [ ] **A.3** — Conectar a GitHub repo `Control-s-Elector`
- [ ] **A.4** — Seleccionar rama `develop` (o main si prefieres)

**Compuerta A:** Railway muestra "Connected to GitHub repo".

## Fase B: Configurar ambiente (Railway Settings)

- [ ] **B.1** — En Railway project → Settings → Environment variables, agregar:
  - `SUPABASE_URL` (valor: URL del proyecto T-IN-05)
  - `SUPABASE_SERVICE_ROLE_KEY` (valor: key secreto, marcar como secret)
  - `APP_ENV` = `prod`
  - `SERVER_PORT` = `8081`

- [ ] **B.2** — Verificar que el archivo `Dockerfile` existe en raíz. Si no:
  ```dockerfile
  FROM eclipse-temurin:21-jre
  COPY backend/electoral-api/target/*.jar app.jar
  ENTRYPOINT ["java", "-jar", "app.jar"]
  ```

**Compuerta B:** Variables configuradas, Dockerfile presente.

## Fase C: Deploy manual (trigger)

- [ ] **C.1** — En Railway, ir a Deployments
- [ ] **C.2** — Hacer click "Deploy" (trigger build manual)
- [ ] **C.3** — Esperar a que build complete (~5 min)
- [ ] **C.4** — Ver logs en Railway: debe ver "Tomcat started" o "Application started"

**Compuerta C:** Build exitoso, aplicación started sin errores.

## Fase D: Verificar accesibilidad

- [ ] **D.1** — Railway asigna URL temporal (ej: `https://electric-pony-prod.railway.app`)
- [ ] **D.2** — Probar: `curl -s https://<railway-url>/swagger-ui.html | grep -i "swagger"`
- [ ] **D.3** — Configurar domain twintailcs.xyz → Railway (DNS CNAME a Railway domain)
- [ ] **D.4** — Esperar DNS propagación (~30 min), luego probar https://api.twintailcs.xyz

**Compuerta D:** API responde en https://api.twintailcs.xyz/swagger-ui.html.

## Fase E: Automático on push (GitHub integration)

- [ ] **E.1** — En Railway Integrations, GitHub está conectado (automático si hiciste A.3)
- [ ] **E.2** — Hacer commit pequeño y push a develop/main
- [ ] **E.3** — Railway debe auto-trigger nuevo deploy
- [ ] **E.4** — Ver en Deployments: nueva entrada en progreso

**Compuerta E:** Push automáticamente dispara nuevo deploy.

## Qué NO hacer

- **No usar Railway free tier para prod** — usar plan Pro
- **No commitear .env.production** — usar Railway Settings variables
- **No exponer service_role_key en logs** — marcar como secret
- **No compilar localmente antes** — Railway compila en pipeline

## Compuertas

| Fase | Verifica | Expected |
|------|----------|----------|
| A | Railway dashboard | Project connected to GitHub |
| B | Environment vars | SUPABASE_URL + SERVICE_ROLE_KEY visible |
| C | Build logs | "Application started" sin errors |
| D | curl https://api.twintailcs.xyz | HTTP 200/302 (no 500) |
| E | Push + Deployments | Nueva entrada 1-2 min después push |

## Verificación por Mutación

- **V.1** — Remover SUPABASE_URL → build debe fallar al iniciar (connection error)
- **V.2** — Cambiar puerto a 9000 → Railway no puede acceder (healthcheck timeout)
