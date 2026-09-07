# Spec — T-FE-08: Captura y Compresión de Imagen

## Escenarios

### S1 — Captura y compresión exitosa

```gherkin
GIVEN    ImageService.captureAndCompress() llamado
AND      usuario toma foto desde cámara (Capacitor Camera)
WHEN     foto original = 800KB, formato JPEG
THEN     servicio convierte a WebP calidad 0.82
AND      resultado ~350KB
AND      Observable emite Blob(WebP, 350KB)
```

### S2 — Recompresión si >500KB

```gherkin
GIVEN    foto original = 2MB JPEG
WHEN     compresión a 0.82 = 700KB (>500KB)
THEN     recomprimir a calidad 0.65
AND      resultado ~400KB
AND      Observable emite Blob(WebP, 400KB)
```

### S3 — Blob es formato WebP

```gherkin
GIVEN    Blob emitido
WHEN     inspecciona type
THEN     type === 'image/webp'
```

### S4 — Usuario cancela cámara

```gherkin
GIVEN    usuario hace click "Tomar foto"
AND      abre cámara
AND      cierra sin tomar foto
WHEN     Capacitor Camera retorna error
THEN     Observable emite error
AND      llamador debe manejar catchError
```

## Mutaciones

### M1 — Usar JPEG en lugar de WebP

Si Canvas.toBlob() usa 'image/jpeg' en lugar de 'image/webp', Observable debe emitir JPEG (no WebP). Verificación: inspeccionar type del Blob, debe ser 'image/jpeg' (detectar falla).

### M2 — No hacer recompresión

Si se quita la lógica de recompresión en 500KB, imagen >500KB pasa como está. Verificación: Blob.size >500KB → debe recomprimirse pero no lo hace (detectar falla).

## Garantías

- **G1:** Captura desde cámara Capacitor
- **G2:** Formato WebP
- **G3:** Tamaño <500KB (recompresión)
- **G4:** Observable<Blob>
