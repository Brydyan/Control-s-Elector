# T-IN-05 — Configurar proyecto Supabase

**Estado:** TODO
**Dificultad:** Intermedio
**Tiempo estimado:** 1.5 horas
**Depende de:** ninguna (paralelo a T-IN-01 a T-IN-04)


> **⚠️ HISTÓRICO — ya no describe producción.** Esta tarea planteaba usar Supabase como
> base de datos *gestionada*. **La base de producción es Neon** (PostgreSQL serverless) y el
> storage de evidencias es **Cloudflare R2**. El contenido de abajo se conserva como registro del
> procedimiento con Supabase; lo vigente está en `documents/task-control/infra.md` y en
> `openspec/changes/infra/2026-11-08-t-in-13-neon-database/`.

---

## ¿Qué vas a construir?

Vas a crear un proyecto Supabase en la nube, que será la BD de **producción** (no staging local). Supabase es PostgreSQL + auth + storage + realtime alojado en la nube.

**Por qué:**
- BD administrada (backups automáticos, monitoreo, escalado)
- Object Storage para fotos (reemplaza RustFS)
- Realtime WebSocket nativo (para dashboard en vivo)
- RLS integrado

---

## Conceptos clave

**¿Qué es Supabase?**
Plataforma "Firebase open-source" basada en PostgreSQL. Proporciona:
- Base de datos PostgreSQL hostizada
- Auth (emails, OAuth, etc.)
- Object Storage (S3-compatible)
- Realtime listeners (WebSocket)
- Dashboard web

**¿Cuál es la diferencia entre staging (local) y prod (Supabase)?**
| Aspecto | Staging (Docker) | Producción (Supabase) |
|---|---|---|
| BD | PostgreSQL en Docker | PostgreSQL en Supabase |
| Storage | RustFS local | Supabase Object Storage |
| Realtime | Polling HTTP | Supabase Realtime (WebSocket) |
| Acceso | localhost:5432 | supabase.co URL + auth |
| Backups | Manual (volumen) | Automático |

**¿Qué es una "region"?**
Ubicación geográfica del servidor. Elige la más cercana a tus usuarios para latencia baja. Para Ecuador, **São Paulo (Brasil)** o **us-east-1 (Virginia)** son opciones.

---

## Archivos / Cambios

```
Nada en el código aún. Todo es configuración web de Supabase.
Después conectaremos via DATABASE_URL en T-IN-08.
```

---

## Pasos

### Paso 1 — Crear cuenta Supabase

1. Ve a https://supabase.com
2. Haz clic en "Start your project"
3. Regístrate con GitHub o email

### Paso 2 — Crear nuevo proyecto

1. Dashboard → "New Project"
2. Nombre: `control-electoral-prod`
3. Contraseña de BD: genera una fuerte (≥16 caracteres)
   - **Guárdala en lugar seguro** (1Password, Bitwarden, etc.)
   - Usarás esto en `DATABASE_URL` más tarde
4. Región: **South America (São Paulo)** (más cerca de Ecuador)
5. Haz clic en "Create new project"
6. Espera ~3 min mientras Supabase provee la BD

### Paso 3 — Obtener credenciales

Una vez el proyecto esté listo:

1. Ve a **Settings** → **Database**
   - Copia la conexión URI (DATABASE_URL):
     ```
     postgresql://postgres:[PASSWORD]@[HOST]:[PORT]/postgres
     ```
   - Reemplaza `[PASSWORD]` con la que creaste en Paso 2
   - Ejemplo: `postgresql://postgres:abc123xyz@db.xyz.supabase.co:5432/postgres`

2. Ve a **Settings** → **API**
   - Copia `Project URL`: `https://abc123xyz.supabase.co`
   - Copia `anon key` (public): usarás en frontend
   - Copia `service_role key` (secret): usarás en backend

3. Guardar en lugar seguro (archivo `.env.prod` NO versionado):
   ```
   DATABASE_URL=postgresql://postgres:abc123@db.xyz.supabase.co:5432/postgres
   SUPABASE_URL=https://abc123.supabase.co
   SUPABASE_ANON_KEY=eyJhbGc...
   SUPABASE_SERVICE_KEY=eyJhbGc...
   ```

### Paso 4 — Crear bucket Object Storage

Object Storage es donde se guardan las fotos (reemplaza RustFS).

1. En Supabase Dashboard → **Storage**
2. Haz clic en **New bucket**
3. Nombre: `actas-images`
4. Privado: **SÍ** (solo acceso autenticado)
5. Crear

### Paso 5 — Configurar políticas de acceso

Por default, nadie puede leer/escribir en el bucket. Configura permisos:

1. Selecciona el bucket `actas-images`
2. Haz clic en **Policies**
3. **New Policy** para INSERT:
   - Permitir: `service_role` (backend) puede escribir
   - MIME types: `image/webp` solamente
   
4. **New Policy** para SELECT:
   - Permitir: usuarios autenticados (`auth.uid()`) pueden leer

(Las políticas exactas dependen de tu modelo RLS en T-IN-03. Por ahora, configuración básica.)

### Paso 6 — Ejecutar scripts en BD remota

Ahora que tienes BD Supabase, necesitas cargar el schema y seed:

1. **En Supabase Dashboard → SQL Editor**
2. Copia el contenido de `01-schema.sql` (T-IN-02)
3. Pégalo y ejecuta
4. Repite con `02-rls.sql` (T-IN-03)
5. Repite con `03-seed.sql` (T-IN-04)

O alternativa: desde terminal con `psql`:
```bash
# DATABASE_URL obtenida en Paso 3
psql "postgresql://postgres:PASSWORD@db.xyz.supabase.co:5432/postgres" -f backend/electoral-api/src/main/resources/database/infra/sql/01-schema.sql

psql "postgresql://postgres:PASSWORD@db.xyz.supabase.co:5432/postgres" -f backend/electoral-api/src/main/resources/database/infra/sql/02-rls.sql

psql "postgresql://postgres:PASSWORD@db.xyz.supabase.co:5432/postgres" -f backend/electoral-api/src/main/resources/database/infra/sql/03-seed.sql
```

### Paso 7 — Verificar conexión

```bash
# Desde tu máquina, conecta a la BD remota
psql "postgresql://postgres:PASSWORD@db.xyz.supabase.co:5432/postgres" -c "SELECT COUNT(*) FROM usuario;"

# Debería retornar 2+ (MASTER + OBSERVER del seed)
```

---

## Cómo verificar que funciona

1. **Proyecto Supabase creado:**
   - Dashboard muestra el proyecto
   - BD está "online" (status green)

2. **Credenciales funcionales:**
   ```bash
   psql "postgresql://postgres:PASSWORD@db.xyz.supabase.co:5432/postgres" -c "\dt"
   # Lista todas las tablas (provincia, canton, acta, etc.)
   ```

3. **Bucket Object Storage:**
   - Storage → `actas-images` aparece
   - Puedes hacer upload de test

4. **RLS funciona en prod:**
   ```bash
   psql "postgresql://postgres:PASSWORD@db.xyz.supabase.co:5432/postgres" -c "SELECT * FROM pg_policies LIMIT 3;"
   # Debería mostrar policies que creaste en T-IN-03
   ```

---

## Errores comunes

**Error: `FATAL: password authentication failed`**
Razón: PASSWORD incorrecto o malformado en DATABASE_URL.
Solución: Copia la URL exacta de Supabase Dashboard (Settings → Database).

**Error: `could not translate host name "db.xyz.supabase.co" to address`**
Razón: Host incorrecto o firewall bloquea conexión.
Solución: Verifica que la URL es correcta. Supabase requiere SSL por default; psql lo maneja automático.

**Error: `relation "usuario" does not exist`**
Razón: Scripts SQL (01, 02, 03) no se ejecutaron.
Solución: Ejecuta manualmente en Supabase SQL Editor o via `psql`.

**Bucket `actas-images` creado pero puedo leer/escribir sin autenticación**
Razón: Las políticas no están configuradas correctamente.
Solución: Revisa Policies del bucket; asegúrate de que solo `service_role` puede escribir.

---

## Checklist original (de infra.md)

- [ ] Crear proyecto en Supabase (región más cercana — São Paulo o US East)
- [ ] Obtener `DATABASE_URL` (prod), `SUPABASE_URL`, `SUPABASE_ANON_KEY`, `SUPABASE_SERVICE_KEY`
- [ ] Ejecutar scripts RLS en Supabase SQL Editor
- [ ] Crear bucket en Object Storage: `actas-images` (privado)
- [ ] Configurar política de acceso al bucket: solo service key puede escribir
