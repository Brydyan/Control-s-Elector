# Tasks — T-IN-12: Tailscale Staging

## Fase A: Instalar Tailscale en dev machine

- [ ] **A.1** — Descargar desde https://tailscale.com/download
- [ ] **A.2** — Instalar según SO (Windows/Mac/Linux)
- [ ] **A.3** — Ejecutar Tailscale (buscador de aplicaciones o terminal)
- [ ] **A.4** — Login con GitHub OAuth

**Compuerta A:** Tailscale instalado, conectado. IP privada visible en Tailscale dashboard (ej: 100.100.100.1).

## Fase B: Verificar backend accesible

- [ ] **B.1** — Verificar que backend Docker está running:
  ```bash
  docker-compose up -d
  curl localhost:8081/swagger-ui.html
  ```
  Debe retornar Swagger HTML.

- [ ] **B.2** — Anotar IP privada de dev machine (desde Tailscale dashboard)

**Compuerta B:** Backend responde en localhost:8081.

## Fase C: Conectar otro dispositivo

- [ ] **C.1** — En otro dispositivo (laptop QA, móvil, etc.):
  - Descargar e instalar Tailscale
  - Login con GitHub OAuth (mismo equipo/organization)

- [ ] **C.2** — Verificar que aparece en Tailscale dashboard (debe ver dev machine)

**Compuerta C:** Otro dispositivo visible en dashboard, conectado a red.

## Fase D: Test acceso

- [ ] **D.1** — Desde otro dispositivo, abrir navegador:
  ```
  http://[dev-machine-tailscale-ip]:8081/swagger-ui.html
  ```
  Ejemplo: `http://100.100.100.1:8081/swagger-ui.html`

- [ ] **D.2** — Debe cargar Swagger sin timeout

- [ ] **D.3** — Test request: POST /api/auth/login
  ```bash
  curl -X POST http://100.100.100.1:8081/api/auth/login \
    -H "Content-Type: application/json" \
    -d '{"email":"test","password":"test"}'
  ```
  Debe retornar respuesta (auth error, pero no connection error)

**Compuerta D:** Backend accesible desde otro dispositivo.

## Fase E: Documentación

- [ ] **E.1** — Agregar a DOCKER.md o README:
  ```markdown
  ## Tailscale Staging

  1. Instalar Tailscale desde https://tailscale.com/download
  2. Login con GitHub OAuth
  3. Anotar IP privada (Tailscale dashboard)
  4. Otros dispositivos en el equipo pueden acceder: http://[private-ip]:8081
  ```

**Compuerta E:** Documentación presente.

## Qué NO hacer

- **No usar ngrok** — Tailscale es más simple + privado
- **No exponer puertos al internet** — solo VPN privada
- **No usar credenciales compartidas** — OAuth GitHub es mejor
- **No activar subnet routes** — no necesario para MVP

## Compuertas

| Fase | Verifica | Expected |
|------|----------|----------|
| A | Tailscale app | Conectado, IP privada asignada |
| B | curl localhost:8081 | HTTP 200, Swagger |
| C | Dashboard | otro dispositivo visible |
| D | curl [private-ip]:8081 | HTTP responde (no timeout) |
| E | Docs actualizada | instrucciones claras |

## Verificación por Mutación

- **V.1** — Desconectar Tailscale → curl debe timeout → reconectar
- **V.2** — Remover otro dispositivo de red → debe perder acceso → re-invitar
