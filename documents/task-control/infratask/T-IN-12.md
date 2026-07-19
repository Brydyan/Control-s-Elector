# T-IN-12 — Staging con Tailscale (red privada)

**Estado:** TODO
**Dificultad:** Intermedio
**Tiempo estimado:** 1.5 horas
**Depende de:** T-IN-01 ✅ (Docker local)

---

## ¿Qué vas a construir?

**Tailscale**: red privada que permite acceder a `localhost:8081` (backend staging en tu máquina) desde cualquier otro dispositivo de tu equipo, sin exponer puertos públicos.

**Por qué:**
- Staging local debe ser accesible al equipo (QA, diseño, etc.)
- Pero sin exponer al público internet
- Tailscale crea VPN privada entre dispositivos

---

## Conceptos clave

**¿Qué es Tailscale?**
Software VPN ("Virtual Private Network") que conecta dispositivos de forma segura usando Wireguard. Es como una red privada solo para tu equipo.

**¿Cómo es diferente de exponer localhost públicamente?**
- Público: `http://mi-ip-publica:8081` → cualquiera en internet puede intentar conectar
- Tailscale: solo dispositivos con acceso a tu red Tailscale pueden conectar

---

## Pasos

### Paso 1 — Instalar Tailscale

**En tu máquina de staging (donde corre Docker + Spring Boot):**

```bash
# macOS
brew install tailscale
brew services start tailscale

# Linux
curl -fsSL https://tailscale.com/install.sh | sh
sudo systemctl start tailscaled

# Windows
Descarga instalador: https://tailscale.com/download
```

### Paso 2 — Autenticar

```bash
tailscale up
# Te abre navegador para login con GitHub
# Autoriza acceso a tu cuenta Tailscale
```

### Paso 3 — Obtener IP Tailscale

```bash
tailscale ip -4
# Retorna algo como: 100.64.25.123
```

### Paso 4 — Compartir en red Tailscale

```bash
tailscale share add 100.64.25.123:8081 --name=staging-backend
```

Ahora otros dispositivos de tu red Tailscale pueden acceder a:
```
http://100.64.25.123:8081
```

### Paso 5 — Actualizar documentación

En `diagram/02-deployment.md`, agregá sección "Staging":

```markdown
## Staging (Red Privada Tailscale)

**Backend**: http://100.64.25.123:8081 (IP Tailscale dinámica, reemplaza según `tailscale ip -4`)
**Frontend**: http://localhost:4200 (dev server Angular en mismo dispositivo)
**Base de datos**: localhost:5432 (Docker)

**Acceso:**
1. Instala Tailscale en tu dispositivo
2. Conéctate a red Tailscale del proyecto
3. Accede a IP Tailscale + puerto
```

---

## Verificación

Desde otro dispositivo en tu red Tailscale:

```bash
# Ver IP de la máquina de staging
tailscale status | grep staging

# Ping para verificar conectividad
ping 100.64.25.123

# Curl al backend
curl http://100.64.25.123:8081/actuator/health
# Esperado: { "status": "UP" }
```

---

## Checklist

- [ ] Tailscale instalado en máquina de staging
- [ ] Autenticado con GitHub
- [ ] IP Tailscale obtenida (tailscale ip -4)
- [ ] Puerto 8081 compartido
- [ ] Otros dispositivos pueden conectar
- [ ] Documentación en diagram/02-deployment.md actualizada
