# Spec — T-IN-12: Tailscale Staging

## Escenarios

### S1 — Tailscale instalado y autenticado

```gherkin
GIVEN    máquina de desarrollo
WHEN     instala Tailscale
AND      login con GitHub OAuth
THEN     Tailscale asigna IP privada (ej: 100.100.100.1)
AND      aparece en Tailscale dashboard
```

### S2 — Backend accesible desde otro dispositivo

```gherkin
GIVEN    backend Docker corriendo en dev machine en localhost:8081
AND      otro dispositivo (QA laptop) conectado a Tailscale
WHEN     desde QA laptop: curl http://100.100.100.1:8081/swagger-ui.html
THEN     respuesta HTTP 200 (Swagger carga)
```

### S3 — Latency bajo

```gherkin
GIVEN    dos dispositivos en Tailscale
WHEN     ping entre ellos
THEN     latency < 50ms (local VPN)
```

## Mutaciones

### M1 — Desconectar Tailscale

Si se desactiva Tailscale, conexión debe fallar. Verificación: curl timeout.

### M2 — Remover otro dispositivo de red

Si se quita otro dispositivo del Tailscale account, debe perder acceso. Verificación: curl forbidden/refused.

## Garantías

- **G1:** Tailscale VPN privada
- **G2:** Backend accesible desde otro dispositivo
- **G3:** Sin exponer a internet público
