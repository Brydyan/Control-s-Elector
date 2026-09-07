# Proposal — T-IN-12: Staging con Tailscale

## Qué se construye
Tailscale: red privada que permite acceder a localhost:8081 (backend staging) desde cualquier otro dispositivo del equipo, sin exponer puertos públicos.

## Por qué
- Staging local accesible al equipo (QA, diseño, etc.)
- Sin exponer internet público (seguro)
- Tailscale crea VPN privada entre dispositivos
- Más fácil que ngrok o port-forwarding manual

## Alcance

| Elemento | Incluye | No incluye |
|----------|---------|-----------|
| Tailscale | crear network, invitar dispositivos | custom DNS |
| Acceso | localhost:8081 desde otro dispositivo via Tailscale | internet público |
| Networking | VPN privada, auto-routing | firewall rules |

## Criterio de aceptación

- [ ] Tailscale instalado en máquina dev
- [ ] Tailscale account creado
- [ ] Backend Docker corriendo localmente (T-IN-01)
- [ ] Otro dispositivo (laptop/móvil) conectado a Tailscale
- [ ] Desde otro dispositivo: curl `http://[dev-machine-tailscale-ip]:8081/swagger-ui.html`
- [ ] Responde (no timeout, no conexión rechazada)

## Fuera de alcance

- ngrok
- Cloudflare Tunnel
- AWS VPC

## Dependencias

- T-IN-01 (Docker Compose backend running)

## Decisiones a tomar

**D1 — Admin console:** usar Tailscale web dashboard.

**D2 — Auth:** GitHub OAuth (simple, sin pass).
