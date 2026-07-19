# T-BE-14 — StorageService (Supabase Storage)

**Dificultad:** Intermedio
**Tiempo estimado:** 2 horas
**Depende de:** T-BE-11

---

## ¿Qué vas a construir?

Un servicio que sube imágenes de actas al bucket de Supabase Storage. Cuando el observer saca la foto del acta física, el backend la recibe y la guarda en Supabase. La URL de la imagen se guarda en el campo `acta.storageUrl`.

---

## Conceptos clave

### Supabase Storage — ¿qué es?
Es como Google Drive pero para tu app. Guarda archivos (imágenes, PDFs, etc.) y te da una URL pública para acceder a ellos. Expone una API REST compatible con la API de AWS S3.

### RestTemplate en Spring
Permite hacer llamadas HTTP desde el backend a otros servicios:
```java
RestTemplate restTemplate = new RestTemplate();
restTemplate.put(url, entity);  // PUT request
```

### Headers de autenticación Supabase
Para subir archivos, Supabase necesita el `service_role` key (clave de servicio, NO la `anon` key):
```
Authorization: Bearer {SUPABASE_SERVICE_KEY}
Content-Type: image/webp
```

### Nombre del archivo
Formato obligatorio del proyecto:
```
JRV-{jrvId}_{YYYYMMDD}_{HHmm}_{observerId}.webp
```
Ejemplo: `JRV-42_20270302_1430_7.webp`

---

## Archivos a crear

```
src/main/java/ec/twintailcs/electoral/service/
  StorageService.java
src/main/resources/
  application.properties     ← agregar config Supabase
```

---

## Pasos

### Paso 1 — Agregar configuración en application.properties

```properties
# application.properties — agregar
supabase.url=https://tuproyecto.supabase.co
supabase.service-key=eyJhbGciOiJIUzI1NiIsInR5cCI6...  # service_role key de Supabase
supabase.bucket=actas-images
```

**Importante:** En producción, usa variables de entorno, nunca el valor real en el archivo.

En `application-prod.properties`:
```properties
supabase.url=${SUPABASE_URL}
supabase.service-key=${SUPABASE_SERVICE_KEY}
supabase.bucket=actas-images
```

### Paso 2 — StorageService

```java
// src/main/java/ec/twintailcs/electoral/service/StorageService.java
package ec.twintailcs.electoral.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class StorageService {

    // Lee los valores de application.properties
    @Value("${supabase.url}")
    private String supabaseUrl;

    @Value("${supabase.service-key}")
    private String serviceKey;

    @Value("${supabase.bucket}")
    private String bucket;

    private final RestTemplate restTemplate;
    private final AuditLogService auditLogService;

    public StorageService(AuditLogService auditLogService) {
        this.restTemplate = new RestTemplate();
        this.auditLogService = auditLogService;
    }

    /**
     * Sube una imagen WebP a Supabase Storage.
     *
     * @param imageBytes  Los bytes de la imagen (ya comprimida en el cliente)
     * @param jrvId       ID de la JRV para el nombre del archivo
     * @param observerId  ID del observer para el nombre del archivo
     * @return URL pública del archivo subido
     */
    public String upload(byte[] imageBytes, Long jrvId, Long observerId) {

        // 1. Generar nombre único para el archivo
        String filename = generarNombreArchivo(jrvId, observerId);

        // 2. Construir la URL del endpoint de Supabase Storage
        // Formato: {supabase_url}/storage/v1/object/{bucket}/{filename}
        String uploadUrl = supabaseUrl + "/storage/v1/object/" + bucket + "/" + filename;

        // 3. Configurar los headers del request
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + serviceKey);
        headers.setContentType(MediaType.parseMediaType("image/webp"));

        // 4. Crear el objeto del request con los bytes de la imagen
        HttpEntity<byte[]> requestEntity = new HttpEntity<>(imageBytes, headers);

        try {
            // 5. Hacer el PUT a Supabase (igual que guardar en un S3)
            ResponseEntity<String> response = restTemplate.exchange(
                uploadUrl,
                HttpMethod.PUT,
                requestEntity,
                String.class
            );

            // 6. Verificar que la subida fue exitosa
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new RuntimeException("Error al subir imagen a Supabase: " + response.getStatusCode());
            }

            // 7. Retornar la URL pública del archivo
            return generarUrlPublica(filename);

        } catch (Exception e) {
            // Si falla la subida, registrar en AuditLog y lanzar excepción
            auditLogService.registrar(
                observerId,
                "ERROR_UPLOAD_IMAGEN",
                "storage",
                null,
                "Error al subir imagen para JRV " + jrvId + ": " + e.getMessage()
            );
            throw new RuntimeException("No se pudo subir la imagen: " + e.getMessage(), e);
        }
    }

    /**
     * Genera el nombre del archivo según el formato del proyecto:
     * JRV-{jrvId}_{YYYYMMDD}_{HHmm}_{observerId}.webp
     */
    private String generarNombreArchivo(Long jrvId, Long observerId) {
        LocalDateTime ahora = LocalDateTime.now();
        String fecha = ahora.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String hora = ahora.format(DateTimeFormatter.ofPattern("HHmm"));

        return String.format("JRV-%d_%s_%s_%d.webp", jrvId, fecha, hora, observerId);
    }

    /**
     * Genera la URL pública para acceder al archivo desde el frontend.
     */
    private String generarUrlPublica(String filename) {
        return supabaseUrl + "/storage/v1/object/public/" + bucket + "/" + filename;
    }
}
```

### Paso 3 — Integrar en ObserverController

Actualiza el método `subirImagen` en `ObserverController.java`:

```java
// Inyectar StorageService en el controller
private final StorageService storageService;

// Actualizar el método subirImagen:
@PostMapping("/actas/{actaId}/imagen")
public ResponseEntity<Map<String, String>> subirImagen(
        @PathVariable Long actaId,
        @RequestParam("imagen") MultipartFile imagen,
        HttpServletRequest request) throws IOException {

    Long observerId = getObserverId();

    // 1. Obtener el acta
    Acta acta = actaRepository.findById(actaId)
        .orElseThrow(() -> new RuntimeException("Acta no encontrada: " + actaId));

    // 2. Subir la imagen a Supabase
    String storageUrl = storageService.upload(
        imagen.getBytes(),
        acta.getJrv().getId(),
        observerId
    );

    // 3. Guardar la URL en el acta
    acta.setStorageUrl(storageUrl);
    actaRepository.save(acta);

    // 4. Retornar la URL para que el frontend la pueda mostrar
    return ResponseEntity.ok(Map.of("storageUrl", storageUrl));
}
```

---

## Cómo verificar que funciona

**Primero, configura el bucket en Supabase:**
1. Ve a tu proyecto en supabase.com
2. Storage → New bucket → nombre: `actas-images`
3. Desactiva "Public bucket" (queremos control de acceso)
4. Ve a Settings → API → copia `service_role` key

**Prueba la subida:**
```bash
curl -X POST http://localhost:8080/api/observer/actas/1/imagen \
  -H "Authorization: Bearer TOKEN_OBSERVER" \
  -F "imagen=@/ruta/a/tu/foto.webp"
```

Respuesta esperada:
```json
{
  "storageUrl": "https://tuproyecto.supabase.co/storage/v1/object/public/actas-images/JRV-1_20270302_1430_5.webp"
}
```

Verifica en Supabase → Storage → actas-images que el archivo aparece.

---

## Errores comunes

| Error | Causa | Solución |
|-------|-------|----------|
| `401` de Supabase | Service key incorrecta | Verifica que usas `service_role` key, no `anon` key |
| `404` de Supabase | Bucket no existe | Crea el bucket en el dashboard de Supabase |
| Imagen corrompida | Bytes mal transferidos | Verifica que `MultipartFile.getBytes()` se llama correctamente |
| URL no accesible públicamente | Bucket es privado | Para ver la imagen públicamente, el bucket debe ser público, o usa una URL firmada |
| `Connection refused` | URL de Supabase incorrecta | Verifica que `supabase.url` en properties tiene el dominio correcto |
