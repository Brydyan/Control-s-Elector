# Tasks — T-IN-05: Supabase Project

## Fase A: Crear proyecto Supabase

- [ ] **A.1** — Ir a https://supabase.com, registrarse (si no existe)
- [ ] **A.2** — Crear nuevo proyecto:
  - Nombre: "Control-Electoral-Prod"
  - Región: us-east-1
  - Password BD: generar aleatorio, guardar seguro
- [ ] **A.3** — Esperar a que BD esté ready (~2 minutos)
- [ ] **A.4** — Verificar acceso: SQL Editor en dashboard

**Compuerta A:** Dashboard muestra proyecto activo, BD ready.

## Fase B: Object Storage + Bucket

- [ ] **B.1** — En dashboard, ir a Storage
- [ ] **B.2** — Crear bucket "evidencias" (public, permite upload)
- [ ] **B.3** — Crear carpeta dentro: evidencias/ (para organizar por JRV/fecha)
- [ ] **B.4** — Prueba: subir archivo dummy 1KB, verificar URL pública funciona

**Compuerta B:** URL pública del archivo es accesible (`https://...storage.supabase.co/object/public/evidencias/test.txt`).

## Fase C: API Keys

- [ ] **C.1** — En Settings → API, copiar:
  - `anon_key` (public, para cliente)
  - `service_role_key` (secreto, solo backend)
  - `PROJECT_URL` (https://[id].supabase.co)
  
- [ ] **C.2** — Crear `.env.production.local`:
  ```
  SUPABASE_URL=https://[id].supabase.co
  SUPABASE_ANON_KEY=[anon_key]
  SUPABASE_SERVICE_ROLE_KEY=[service_role_key]
  ```
  NO commitear .env.production.local (agregar a .gitignore)

- [ ] **C.3** — Documentar en `CREDENCIALES.md` (solo para dev local):
  - URL del proyecto
  - Nota: no incluir keys aquí (en .env.production.local)

**Compuerta C:** .env.production.local existe, keys configuradas.

## Fase D: Verificación

- [ ] **D.1** — Test desde Angular (en dev):
  ```typescript
  import { createClient } from '@supabase/supabase-js';
  const supabase = createClient(SUPABASE_URL, SUPABASE_ANON_KEY);
  const { data, error } = await supabase.from('usuario').select('*').limit(1);
  ```
  Debe retornar error (tabla vacía) o datos, no auth error.

**Compuerta D:** Conexión cliente → Supabase exitosa.

## Qué NO hacer

- **No usar Supabase free tier para prod** — usar Pro plan
- **No compartir service_role_key** — solo en backend seguro
- **No crear múltiples buckets** — organizar con carpetas en uno
- **No habilitar Realtime aún** — T-IN-06

## Compuertas

| Fase | Verifica | Expected |
|------|----------|----------|
| A | Dashboard | Proyecto visible, BD ready |
| B | URL bucket | Archivo público accesible |
| C | .env | Keys configuradas |
| D | Conexión TypeScript | SELECT retorna datos/error (no auth fail) |

## Verificación por Mutación

- **V.1** — Deshabilitar anon_key → login debe fallar → restaurar
- **V.2** — Hacer bucket privado → upload 403 → restaurar
