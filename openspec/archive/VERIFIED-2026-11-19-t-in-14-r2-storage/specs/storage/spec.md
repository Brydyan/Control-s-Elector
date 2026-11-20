# Spec: R2 Storage T-IN-14

## RF-STG-01: R2 bucket creado

GIVEN usuario accede a Cloudflare R2 console
WHEN crea bucket `control-electoral-mvp`
THEN bucket existe vacío con location = nearest

## RF-STG-02: API tokens con permisos correctos

GIVEN R2 API token creado
WHEN tiene permisos
THEN permite:
- s3:GetObject (lectura)
- s3:PutObject (escritura)
- s3:ListBucket (listar)
- s3:DeleteObject (borrado para limpieza)

## RF-STG-03: CORS habilitado

GIVEN R2 bucket
WHEN frontend intenta acceder a presigned URL
THEN responde con headers CORS válidos:
- Access-Control-Allow-Origin: app.controls.brydyan.me
- Access-Control-Allow-Methods: GET, PUT

## RF-STG-04: Backend crea presigned URLs

GIVEN backend con S3Client configurado a R2
WHEN endpoint `POST /api/v1/evidencias/{id}/upload-url` es llamado
THEN responde 200 JSON:
```json
{
  "uploadUrl": "https://control-electoral-mvp.r2.cloudflarestorage.com/evidencias/xxx?X-Amz-Algorithm=...",
  "expiresIn": 900
}
```

## RF-STG-05: Frontend sube evidencia via presigned URL

GIVEN presigned URL del backend
WHEN frontend hace `PUT <url>` con file blob
THEN R2 responde 200 OK y foto se almacena

## RF-STG-06: Frontend descarga evidencia

GIVEN presigned URL de descarga (GET)
WHEN frontend hace `GET <url>`
THEN R2 responde 200 + Content-Type: image/webp (o jpeg)

## RF-STG-07: Health check storage OK

GIVEN backend
WHEN hace `GET /api/v1/health/storage`
THEN responde 200 JSON: `{"status":"UP","s3":"bucket-ok"}`

## RNF-STG-01: Latency aceptable

GIVEN foto de 2 MB subida a R2
WHEN descarga desde navegador
THEN termina en <2s (normal CDN latency)

## RNF-STG-02: Presigned URL expira

GIVEN presigned URL con 15 min expiry
WHEN cliente intenta acceder a los 16 min
THEN R2 responde 403 Forbidden (URL expirada)

## RNF-STG-03: Cancellation de upload

GIVEN upload de 50 MB en curso
CUANDO cliente cancela (ej: en 5 MB)
THEN R2 no factura el objeto; partición incompleta se limpia
