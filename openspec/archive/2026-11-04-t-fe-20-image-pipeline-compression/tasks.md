# Tasks — T-FE-20: Pipeline de Compresión de Imágenes en Cliente (Web Worker + WebP)

## Fase A: Actualización y Robustecimiento de `ImageCompressionService`

- [ ] **A.1** — En `frontend/app/src/app/core/services/image-compression.service.ts`:
  - Actualizar `MAX_WIDTH` a `2048` (para cubrir legibilidad en firmas y actas densas de 48-108 MP).
  - Configurar umbral `TARGET_BYTES = 650 * 1024` (650 KB).
  - Implementar doble pasada: calidad `0.80` en primera pasada, calidad `0.65` si el blob excede `TARGET_BYTES`.
  - Asegurar cierre estricto de bitmaps con `bitmap.close()` en bloque `finally`.
  - Añadir soporte para recibir y retornar tanto `Blob` como `File` (manteniendo nombre original con extensión `.webp`).
  - Fallback a `image/jpeg` en caso de que el entorno no soporte `image/webp`.
  - ✅ Completar cuando esté implementado.

- [ ] **A.2** — Tests unitarios en `image-compression.service.spec.ts`:
  - Test de compresión con redimensionamiento a 2048px máx.
  - Test de activación de segunda pasada si el blob supera 650 KB.
  - Test de liberación de memoria (invocación de `bitmap.close()`).
  - Test de manejo de error ante blob corrupto o no decodificable.
  - ✅ Completar cuando esté implementado con 100% de tests pasando.

## Fase B: Conexión del Pipeline en `RegistrarActaPage`

- [ ] **B.1** — En `frontend/app/src/app/features/observer/pages/registrar-acta/registrar-acta.page.ts`:
  - Agregar signal `comprimiendoImagen = signal<boolean>(false)`.
  - En `onFileSelected(event)`:
    - Generar preview inmediato con el archivo original para latencia percibida 0.
    - Activar `comprimiendoImagen.set(true)`.
    - Invocar `await this.imageCompression.compress(file)`.
    - Asignar el resultado comprimido a `this.selectedImageFile`.
    - Guardar en IndexedDB mediante `guardarFotoOffline(compressedFile)`.
    - Desactivar `comprimiendoImagen.set(false)`.
    - Capturar errores de compresión mostrando mensaje amigable en `this.error` sin limpiar votos digitados.
  - En el método de envío (`enviarActaDirecta` / `guardarBorrador`): asegurar que si la compresión está en curso, se espere a que finalice antes de disparar la petición HTTP.
  - ✅ Completar cuando esté implementado.

- [ ] **B.2** — En `registrar-acta.page.html`:
  - Mostrar un badge o micro-spinner ("Optimizando acta para envío rápido...") mientras `comprimiendoImagen()` sea true.
  - Deshabilitar el botón de envío mientras `comprimiendoImagen()` esté activo para evitar enviar un archivo incompleto o en transición.
  - ✅ Completar cuando esté implementado.

- [ ] **B.3** — Tests unitarios en `registrar-acta.page.spec.ts`:
  - Test de llamada obligatoria a `imageCompression.compress()` al seleccionar archivo.
  - Test de asignación de archivo comprimido WebP a `selectedImageFile`.
  - Test de manejo de error si `compress()` rechaza la promesa (no borra datos digitados).
  - Test de bloqueo de envío mientras la compresión esté en curso.
  - ✅ Completar cuando esté implementado.

## Fase C: Conexión en `CorreccionActaPage` y `JrvCargosPage` (Captura Rápida)

- [ ] **C.1** — En `frontend/app/src/app/features/observer/pages/correccion-acta/correccion-acta.page.ts`:
  - Replicar la compresión en el manejador de archivos de nueva evidencia.
  - Asegurar que la nueva foto en disputa sea comprimida antes de persistirse o enviarse con `corregirActa`.
  - ✅ Completar cuando esté implementado.

- [ ] **C.2** — En `frontend/app/src/app/features/observer/pages/jrv-cargos/jrv-cargos.page.ts`:
  - En el flujo de foto rápida, conectar la compresión antes de almacenar el borrador en IndexedDB.
  - ✅ Completar cuando esté implementado.

- [ ] **C.3** — Tests unitarios de integración en ambas páginas:
  - Verificar que las evidencias fotográficas se almacenen en IndexedDB como Blobs WebP optimizados.
  - ✅ Completar cuando esté implementado.

## Fase D: Verificación por Mutación y Compuertas de Calidad

- [ ] **D.1** — Verificación por mutación en `RegistrarActaPage`:
  - **Mutación 1**: Omitir la llamada a `this.imageCompression.compress(file)` en `onFileSelected` (usar `file` crudo). Comprobar que **cae** el test que valida que `selectedImageFile` sea el archivo comprimido. Anotar el nombre del test en `apply-progress.md`.
  - **Mutación 2**: Quitar la validación de `comprimiendoImagen` en el botón de envío. Comprobar que **cae** el test de prevención de envío en tránsito. Anotar el nombre del test en `apply-progress.md`.
  - ✅ Completar cuando ambas mutaciones causen caída verificada.

---

## Qué NO hacer en esta fase

- NO convertir imágenes a Base64 ni guardarlas como string (prohibición estricta de Heap JS).
- NO comprimir por debajo de 1600px de resolución mínima (afecta auditoría visual de firmas).
- NO alterar la estructura de IndexedDB que ya almacena Blobs binarios de forma óptima.
- NO depender de servicios o APIs externas para la compresión; el 100% ocurre en el cliente (offline-first).

---

## Compuertas antes de dar por terminada la fase

```bash
cd frontend/app
pnpm lint
pnpm test:ci --run
pnpm build
```
- `pnpm lint`: **0 errores**.
- `pnpm test:ci --run`: **100% suites pasando** (≥556 tests base + tests nuevos de compresión).
- `pnpm build`: **0 errores de compilación**.
- **Mutaciones**: Ambas mutaciones de D.1 ejecutadas y anotadas en `apply-progress.md`.
