# Specification — T-FE-20: Pipeline de Compresión de Imágenes en Cliente

## Requirement: Reducción y Normalización de Imágenes en Cliente
El sistema debe procesar localmente cualquier fotografía capturada desde la cámara o galería del dispositivo antes de que sea almacenada en IndexedDB o transmitida por red, convirtiéndola a WebP optimizado con resolución controlada.

### Scenario: Compresión exitosa de fotografía de alta resolución
- **GIVEN** que el veedor captura una foto de acta electoral con un celular moderno que genera un archivo JPEG de `18 MB` y resolución `6000x4000`
- **WHEN** el archivo es recibido por el manejador de selección de imagen
- **THEN** la vista genera inmediatamente una URL de previsualización local sin congelar la UI
- **AND** el servicio de compresión redimensiona la imagen a un ancho máximo de `2048px`
- **AND** convierte el contenido a formato `image/webp` con un peso resultante menor o igual a `650 KB`
- **AND** el archivo persistido en IndexedDB y enviado al servidor es el binario WebP optimizado.

### Scenario: Doble pasada de calidad para imágenes con alto nivel de detalle o ruido
- **GIVEN** una fotografía con alto nivel de ruido visual que en la primera pasada a calidad `0.80` genera un archivo de `850 KB`
- **WHEN** el tamaño supera el umbral objetivo de `650 KB`
- **THEN** el pipeline ejecuta automáticamente una segunda pasada con calidad `0.65`
- **AND** produce un archivo final dentro del rango aceptable sin intervención del usuario.

---

## Requirement: Integración Reactiva en Flujos de Captura Electoral
Las páginas de captura electoral deben exhibir feedback de procesamiento y evitar envíos concurrentes mientras la optimización esté en curso.

### Scenario: Indicador de optimización durante la compresión
- **GIVEN** que el usuario selecciona una imagen pesada
- **WHEN** el proceso de compresión está en ejecución
- **THEN** la interfaz muestra un indicador de estado de optimización
- **AND** el botón de envío o guardado definitivo se mantiene deshabilitado o espera la finalización de la compresión antes de transmitir.

### Scenario: Manejo de error ante archivo corrupto o inválido
- **GIVEN** que el usuario selecciona un archivo corrupto que no puede ser decodificado por `createImageBitmap`
- **WHEN** falla la compresión
- **THEN** se captura el error limpiamente
- **AND** se muestra un mensaje informativo al usuario solicitando repetir la captura
- **AND** los votos numéricos y datos de mesa digitados previamente no se pierden ni se reinician.
