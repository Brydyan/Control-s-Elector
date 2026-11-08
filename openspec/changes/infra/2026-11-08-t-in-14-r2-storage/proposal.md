# T-IN-14: R2 Image Storage Setup para MVP

## El problema

MVP almacena fotos de evidencia en cloud object storage.
Hoy: RustFS (MinIO) en compose.yaml local.
Producción: Cloudflare R2 (S3-compatible, económico).

## Alcance

✅ R2 bucket creation + API tokens (read/write)
✅ CORS configuration (frontend accede a presigned URLs)
✅ Presigned URLs: backend genera, frontend descarga sin auth adicional
✅ Backend ImageService integration (S3Client → R2)
✅ Health check endpoint: `/api/v1/health/storage` valida acceso

❌ Image optimization (post-MVP, WebP en cliente)
❌ CDN edge caching (post-MVP, Cloudflare Cache Rules)
❌ Expiration policies (post-MVP, object lifecycle)

## Razón de criticidad

**CRÍTICO**: Sin storage, evidencias de actas no se pueden subir.
Sin subidas, T-IN-07r no levanta completamente.
