# Spec — T-FE-08: Captura y Compresión de Imagen (WebP)

## Escenarios

### S1 — Captura y compresión nativa exitosa

```gherkin
GIVEN    usuario en dispositivo móvil con Capacitor
WHEN     activa la captura de foto
THEN     la cámara del dispositivo se abre
AND      la fotografía capturada se dibuja en Canvas y se exporta a WebP (calidad 0.82)
AND      si el tamaño es <= 500 KB, se emite el Blob resultante
```

### S2 — Recompresión adaptativa si excede 500 KB

```gherkin
GIVEN    fotografía capturada de alta resolución
WHEN     la primera pasada a calidad 0.82 resulta en 750 KB (> 500 KB)
THEN     se ejecuta una segunda compresión automática a calidad 0.65
AND      el Blob final resultante tiene tamaño < 500 KB
```

### S3 — Fallback web para navegadores de escritorio

```gherkin
GIVEN    usuario utilizando la app en navegador web sin Capacitor runtime
WHEN     presiona "Tomar o seleccionar foto"
THEN     se activa el input HTML de archivo (<input type="file" accept="image/*">)
AND      el archivo seleccionado pasa por la misma tubería de compresión WebP en Canvas
```

### S4 — Transmisión Multipart con clave 'imagen'

```gherkin
GIVEN    Blob WebP listo para subida
WHEN     se invoca ObserverService.subirImagenActa(actaId, file)
THEN     se construye un FormData con la clave exacta 'imagen' (formData.append('imagen', blob, 'acta.webp'))
AND      se envía vía HTTP POST a /api/v1/observer/actas/:id/imagen
AND      el backend Spring Boot acepta la petición sin MissingServletRequestPartException
```

### S5 — Manejo de permisos o cancelación de cámara

```gherkin
GIVEN    diálogo de permisos de cámara del sistema operativo
WHEN     el usuario cancela o niega el permiso
THEN     la aplicación captura el error limpiamente
AND      permite al usuario seleccionar un archivo desde el almacenamiento del dispositivo
```

## Garantías

- **G1:** Formato de salida estricto `image/webp`.
- **G2:** Tamaño acotado (< 500 KB) para permitir transmisión rápida en redes 2G/3G.
- **G3:** Clave multipart obligatoria `'imagen'` compatible con `@RequestPart("imagen")` de Spring Boot.
