# Tasks: T-IN-14

Rama: `infra/t-in-14-r2-storage`, de `develop`.
Bloqueador: T-IN-13 (env vars Neon en Railway) + ODD #503 (dominio).

---

## A · Crear R2 bucket en Cloudflare

- [ ] **A.1** — Accede a Cloudflare → R2:
  - Crea bucket `control-electoral-mvp`
  - Region: nearest Cloudflare Edge (auto, Cloudflare lo elije)
  - Anotar en `apply-progress.md`:
    ```
    R2_BUCKET_NAME = control-electoral-mvp
    R2_ACCOUNT_ID = [cloudflare account ID]
    R2_ACCESS_KEY = [gen nuevo API token]
    R2_SECRET_KEY = [gen nuevo API token secret]
    R2_ENDPOINT = https://[account].r2.cloudflarestorage.com
    ```

- [ ] **A.2** — Crea API token con permisos:
  - Scope: `control-electoral-mvp` bucket only
  - Permissions: GetObject, PutObject, ListBucket, DeleteObject
  - TTL: ninguno (o 1 año, revisable)
  - Anotar token ID en `apply-progress.md`.

---

## B · CORS configuration

- [ ] **B.1** — En R2 bucket settings, ve a "CORS rules":
  ```json
  [{
    "AllowedMethods": ["GET", "PUT", "HEAD"],
    "AllowedOrigins": ["https://app.controls.brydyan.me", "https://api.controls.brydyan.me"],
    "AllowedHeaders": ["Authorization", "Content-Type", "X-Amz-*"],
    "MaxAgeSeconds": 3600
  }]
  ```
  Anotar en `apply-progress.md`: "CORS configured".

---

## C · Backend S3Client integration

- [ ] **C.1** — Actualiza `application.properties`:
  ```properties
  aws.s3.endpoint=https://[account].r2.cloudflarestorage.com
  aws.s3.region=auto
  aws.s3.bucket=control-electoral-mvp
  aws.access.key.id=[R2_ACCESS_KEY]
  aws.secret.access.key=[R2_SECRET_KEY]
  aws.s3.force-path-style=false
  ```

- [ ] **C.2** — En `src/main/java/.../storage/ImageService.java`:
  Implementa (ya existe stub, solo verificar):
  ```java
  public String generatePresignedUploadUrl(String evidenciaId) {
    GetPresignedObjectUrlRequest req = GetPresignedObjectUrlRequest.builder()
      .bucket(bucket)
      .key("evidencias/" + evidenciaId + ".webp")
      .expiration(Duration.ofMinutes(15))
      .build();
    return s3.generatePresignedObjectUrl(req).url().toString();
  }
  
  public String generatePresignedDownloadUrl(String evidenciaId) {
    // Similar, GET en vez de PUT
  }
  ```

- [ ] **C.3** — En `EvidenciaResource.java`, endpoint POST para upload URL:
  ```java
  @PostMapping("/{id}/upload-url")
  public UploadUrlResponse getUploadUrl(@PathVariable UUID id) {
    String url = imageService.generatePresignedUploadUrl(id.toString());
    return new UploadUrlResponse(url, 900); // 15 min
  }
  ```

---

## D · Health check storage

- [ ] **D.1** — En `HealthController.java`:
  ```java
  @GetMapping("/storage")
  public ResponseEntity<Map> storageHealth(S3Client s3) {
    try {
      s3.headBucket(r -> r.bucket("control-electoral-mvp"));
      return ResponseEntity.ok(Map.of("status", "UP", "s3", "bucket-ok"));
    } catch (Exception e) {
      return ResponseEntity.status(503).body(Map.of("status", "DOWN", "error", e.getMessage()));
    }
  }
  ```

---

## E · Tests

- [ ] **E.1** — `ImageServiceTest.java`:
  ```java
  @Test void presignedUrlContainsExpiry() {
    String url = service.generatePresignedUploadUrl("test-id");
    assertTrue(url.contains("X-Amz-Expires"));
    assertTrue(url.contains("X-Amz-Signature"));
  }
  
  @Test void presignedUrlPathCorrect() {
    String url = service.generatePresignedUploadUrl("test-id");
    assertTrue(url.contains("evidencias/test-id.webp"));
  }
  ```

- [ ] **E.2** — `EvidenciaResourceTest.java`:
  ```java
  @Test void uploadUrlEndpointReturns200() {
    mvc.perform(post("/api/v1/evidencias/{id}/upload-url", UUID.randomUUID()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.uploadUrl").exists())
      .andExpect(jsonPath("$.expiresIn").value(900));
  }
  ```

- [ ] **E.3** — `HealthControllerTest.java`:
  ```java
  @Test void healthStorageUp() {
    mvc.perform(get("/api/v1/health/storage"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.status").value("UP"));
  }
  
  @Test void healthStorageDownWhenBucketMissing() {
    // Mock S3Client para lanzar exception
    mvc.perform(get("/api/v1/health/storage"))
      .andExpect(status().is(503));
  }
  ```

---

## F · Manual integration test

- [ ] **F.1** — Sube una foto a R2 manualmente:
  ```bash
  aws s3 cp test.webp s3://control-electoral-mvp/evidencias/test-manual.webp \
    --endpoint-url https://[account].r2.cloudflarestorage.com \
    --profile r2
  ```
  Anotar: "Manual upload OK" en `apply-progress.md`.

- [ ] **F.2** — Descarga vía presigned URL:
  ```bash
  # Desde backend:
  curl https://api.controls.brydyan.me/api/v1/evidencias/test-manual/upload-url
  # Copia uploadUrl y:
  curl <uploadUrl>
  # Esperado: 200, imagen descargable
  ```

---

## G · Mutaciones

- [ ] **G.1** — RF-STG-04: Presigned URL expiry.
  En `ImageService`, cambia `Duration.ofMinutes(15)` a `Duration.ofSeconds(1)`.
  Mutación: espera que `PresignedUrlTest.expiryIs15Minutes()` falle.
  Restaura.

- [ ] **G.2** — RF-STG-03: CORS.
  En R2 settings, quita `app.controls.brydyan.me` de AllowedOrigins.
  Mutación: `curl` desde app.controls.brydyan.me debe dar 403 CORS error.
  Restaura.

- [ ] **G.3** — RF-STG-07: Health check.
  En `HealthController`, cambia `s3.headBucket()` a `s3.listObjects()` (otra operación).
  Mutación: test debe fallar si el bucket está vacio (listObjects puede devolver vacio,
  headBucket falla si no existe). Anotar fallo.
  Restaura.

---

## Qué NO hacer

- No subir fotos de prueba a producción (MVP R2 está aislado).
- No cambiar el nombre del bucket (está hardcodeado en code).
- No activar versionamiento de objetos (post-MVP).
- No implementar transformaciones de imagen (eso es T-FE-14).

---

## Compuertas

```bash
cd backend/electoral-api-quarkus

# ¿Tests pasan?
./mvnw test -Dtest=ImageServiceTest,EvidenciaResourceTest,HealthControllerTest

# ¿Bucket existe en R2?
aws s3 ls s3://control-electoral-mvp --endpoint-url https://[account].r2.cloudflarestorage.com

# ¿Health check OK?
curl https://api.controls.brydyan.me/api/v1/health/storage
# Esperado: 200 JSON {"status":"UP"}

# ¿CORS funciona?
curl -H "Origin: https://app.controls.brydyan.me" \
     https://[account].r2.cloudflarestorage.com/evidencias/test.webp \
     -v | grep Access-Control-Allow-Origin
# Esperado: Access-Control-Allow-Origin: https://app.controls.brydyan.me
```

Si todas pasan ✓, T-IN-14 cierra.
