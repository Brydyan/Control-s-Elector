# Design: T-IN-14

## D1 — R2 vs S3 vs Supabase Storage

**Decisión**: Cloudflare R2 (serverless, S3-compatible, $0.015/GB, sin egress costs).

**Alternativas descartadas**:
- AWS S3: egress costs, overkill para MVP. ~$0.09/GB + API fees.
- Supabase Storage: también bueno, pero R2 integra mejor con Cloudflare (el dominio).

## D2 — Presigned URLs en backend vs frontend

**Decisión**: Backend genera presigned URLs. Frontend descarga via `<a href>` o `fetch`.

**Razón**: Control de expiración (15 min), trazabilidad (audit log), RLS enforcement.

**Alternativa descartada**: Frontend firma requests directamente.
- Exponería credenciales R2 en el código.
- Sin control de expiración por acta.

## D3 — CORS: quién accede

**Decisión**: CORS allows:
- Origin: `*.controls.brydyan.me` (app.*, api.*, etc.)
- Methods: GET, PUT (foto sube con PUT presigned)
- Headers: `Authorization`, `Content-Type`

**Alternativa descartada**: CORS abierto (`*`).
- Riesgo: cualquier sitio puede listar buckets.

## D4 — Compensación transaccional

**Decisión**: Si la transacción de `acta` falla pero la foto se subió a R2,
un job background limpia fotos huérfanas (post-MVP, T-BE-99).

**Alternativa descartada**: Two-phase commit (sobrecomplejo).
- MVP: riesgo bajo (pocas actas fallidas), tolerable.
- Fix: T-BE-99 limpia fotos sin evidencia asociada.

## D5 — Bucket naming

**Decisión**: `control-electoral-mvp` (único a nivel global en Cloudflare).

**Convención**: objetos dentro: `evidencias/{acta_id}/{evidencia_public_id}.webp`
