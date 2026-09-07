# Design — T-IN-05: Supabase Project

## D1 — Región Supabase

**Decisión:** us-east-1 (Norte Virginia).

**Alternativa descartada — sa-east-1 (São Paulo).** Más cerca de Ecuador pero Supabase no tiene datacenter allá. us-east-1 es estándar.

## D2 — Backup automático

**Decisión:** Activado (por defecto).

**Alternativa descartada — desactivar para ahorrar costo.** La confiabilidad de datos electorales justifica el costo.

## D3 — Almacenamiento Object Storage

**Decisión:** Bucket único "evidencias" para todas las fotos (por JRV/cargo/fecha).

**Alternativa descartada — bucket por rol.** Más simple: un bucket, namespace por carpeta.

## D4 — Configuración de BD inicial

**Decisión:** Usar BD default que Supabase provee (postgres). No crear BD adicional.

**Alternativa descartada — crear "electoral" DB aparte.** Supabase default es suficiente.
