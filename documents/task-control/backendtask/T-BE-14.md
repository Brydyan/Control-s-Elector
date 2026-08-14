# T-BE-14 — StorageService (S3-compatible Storage)

**Estado:** ✅ **COMPLETADO** — S3StorageService ya implementado. Documento pedagógico describe RestTemplate; código real usa AWS SDK.

**Dificultad:** Intermedio (referencia histórica)
**Tiempo estimado:** Ya implementado
**Depende de:** T-BE-11

---

## ⚠️ Nota de alineación con código actual

**Este documento describe una implementación vía `RestTemplate` directo a Supabase Storage.**

**El código actual ya está implementado, usando arquitectura mejorada:**
- **Librería:** AWS SDK S3 (`software.amazon.awssdk`) — compatible con S3 y Supabase Storage
- **Ubicación real:** `backend/electoral-api/src/main/java/ec/twintailcs/electoral/storage/s3/S3StorageService.java`
- **Endpoint configurado:** 
  - Dev: RustFS (MinIO compatible) en `http://localhost:9000`
  - Prod: Supabase Storage (S3 API)
- **Métodos:** `upload(bytes, key, mimeType)`, `download(key)`, `delete(key)`, `getPresignedUrl(key)`

**La tarea ya está completa.** Si necesitas subir archivos en un controller, inyecta `S3StorageService` y llama su método.

### Ejemplo real en EvidenciaController:
```java
private final S3StorageService storage;

// En upload endpoint:
String s3Key = "evidencias/" + UUID.randomUUID() + ".webp";
String url = storage.upload(bytes, s3Key, "image/webp");
// url es la URL pública (con presigned URL si bucket es privado)
```

---

## ¿Qué hace?

Un servicio que sube/descarga archivos (imágenes de actas) a un bucket S3-compatible. Cuando el observer sube la foto del acta, el backend la recibe, comprime, y guarda en storage. Retorna URL pública (presigned si bucket es privado).

### Flujo típico:
```
1. Observer sube foto de acta en EvidenciaController
2. Controller recibe MultipartFile
3. Comprime WebP si > 500KB
4. Llama storage.upload(bytes, key, mimeType)
5. S3StorageService pone objeto en bucket
6. Retorna URL presigned (válida 24h)
7. Frontend muestra preview
8. Backend guarda URL en Evidencia.s3Key
```

---

## Implementación actual (ya hecha)

| Componente | Ubicación | Status |
|-----------|-----------|--------|
| **S3StorageService** | `storage/s3/S3StorageService.java` | ✅ IMPLEMENTADO (AWS SDK) |
| **Métodos** | `upload()`, `download()`, `delete()`, `getPresignedUrl()` | ✅ FUNCIONAL |
| **Integración** | EvidenciaController usa S3StorageService | ✅ LISTO |

### Configuración en properties:

Dev (RustFS/MinIO):
```properties
aws.s3.endpoint=http://localhost:9000
aws.s3.region=us-east-1
aws.s3.bucket=actas-images
aws.s3.access-key=minioadmin
aws.s3.secret-key=minioadmin
```

Prod (Supabase Storage):
```properties
aws.s3.endpoint=${AWS_S3_ENDPOINT}
aws.s3.region=${AWS_S3_REGION}
aws.s3.bucket=${AWS_S3_BUCKET}
aws.s3.access-key=${AWS_S3_ACCESS_KEY}
aws.s3.secret-key=${AWS_S3_SECRET_KEY}
```

### Cómo usar en un controller:

```java
@Autowired
private S3StorageService storage;

// Upload:
String s3Key = "evidencias/" + UUID.randomUUID() + ".webp";
String presignedUrl = storage.upload(bytes, s3Key, "image/webp");
// presignedUrl válido 24h (seguro para buckets privados)

// Download (presigned URL):
String url = storage.getPresignedUrl(s3Key);

// Delete:
storage.delete(s3Key);
```

---

## Cómo verificar que funciona

**Dev (RustFS en Docker):**
1. Asegúrate de que MinIO está corriendo en `http://localhost:9000`
2. Bucket `actas-images` debe existir (Liquibase lo crea)

**Prueba (después de que Evidencia/Acta controllers existan):**
```bash
curl -X POST http://localhost:8081/api/evidencias \
  -H "Authorization: Bearer TOKEN_OBSERVER" \
  -F "archivo=@/ruta/a/foto.webp"
```

Respuesta esperada:
```json
{
  "id": 1,
  "s3Key": "evidencias/550e8400-e29b-41d4-a716-446655440000.webp",
  "estado": "EN_REVISION",
  "presignedUrl": "http://localhost:9000/actas-images/evidencias/550e8400...?X-Amz-Signature=..."
}
```

**Prod:** Los valores AWS_S3_* se inyectan desde variables de entorno. Supabase Storage funciona igual (S3 compatible).

---

## Notas

- **T-BE-14 ya está completado.**
- S3StorageService maneja presigned URLs automáticamente (seguro para buckets privados)
- EvidenciaController ya integra storage (verificar)
- Upload de Acta.storageUrl puede integrarse en ObserverController cuando sea necesario
