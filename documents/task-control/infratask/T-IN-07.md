# T-IN-07 — Deploy Spring Boot en Railway

**Estado:** TODO
**Dificultad:** Intermedio
**Tiempo estimado:** 1 hora
**Depende de:** T-IN-05 ✅ (Supabase BD ready)

---

## ¿Qué vas a construir?

Vas a desplegar el backend Spring Boot en **Railway**: plataforma que ejecuta tu app desde GitHub automáticamente, con CI/CD incluido.

---

## Conceptos clave

**¿Qué es Railway?**
Plataforma PaaS ("Platform as a Service") que despliega apps desde Git. Conectas tu repo de GitHub y Railway automáticamente:
1. Detecta cambios en `main`
2. Construye la app (`mvn clean package`)
3. Ejecuta el binario
4. Maneja logs, monitoreo, escalado

**¿Cómo asigna el puerto?**
Railway asigna un puerto via variable `$PORT` (ej: 8080). Tu `application-prod.properties` debe usar `server.port=${PORT:8080}`.

---

## Pasos

### Paso 1 — Crear cuenta Railway

1. Ve a https://railway.app
2. Regístrate con GitHub
3. Autoriza acceso a tus repos

### Paso 2 — Crear nuevo proyecto

1. Dashboard → **New Project**
2. Selecciona tu repo `Control-s-Elector`
3. Railway detecta automáticamente que es Spring Boot (Maven)
4. Configura variables de entorno (Paso 3)
5. Deploy

### Paso 3 — Configurar variables de entorno

En Railway Dashboard → **Variables**:

```
DATABASE_URL=postgresql://postgres:PASSWORD@db.xyz.supabase.co:5432/postgres
SPRING_PROFILES_ACTIVE=prod
SUPABASE_URL=https://abc123.supabase.co
SUPABASE_SERVICE_KEY=eyJhbGc...
JWT_SECRET=abc123xyz... (gen. strong 32+ chars)
JWT_ISSUER=control-elector
```

### Paso 4 — Conectar dominio custom

1. Railway → **Settings** → **Domains**
2. Agrega dominio: `api.brydyan.me`
3. Railway te da un CNAME; actualiza tu DNS

### Paso 5 — Verificar deploy

```bash
curl https://api.brydyan.me/actuator/health
# Esperado: { "status": "UP" }
```

---

## Checklist

- [ ] Cuenta Railway creada
- [ ] Repo conectado a Railway
- [ ] Variables de entorno configuradas
- [ ] Dominio custom apuntando
- [ ] Health check respondiendo
