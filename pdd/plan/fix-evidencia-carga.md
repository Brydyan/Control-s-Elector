# Plan: Corregir carga de evidencias fotográficas de actas

## 1. Descripción y alcance
(S1) ConsultarActaDetalleUseCase:58 deja de tragarse excepciones: log WARN con actaId, publicId, s3Key y tipo de error (sin credenciales ni tokens). (S2) primeraEvidencia elige la evidencia más reciente no RECHAZADA (orden id desc), con fallback a la más reciente si todas están rechazadas. (S3) FallbackStorageService: eliminar la auto-inyección @FallbackProvider que se resuelve a sí misma y provoca StackOverflowError; sin fallback resoluble se propaga la StorageException original. (S4) ConsultarEvidenciaUseCase y EliminarEvidenciaUseCase usan StorageService en vez de S3StorageService, consistente con la subida. (S5) Frontend acta-detalle (coordinator y observer): en (error) del <img> recargar el detalle una vez para obtener URL prefirmada nueva (expiración 15 min). (S6) Tests: unit de selección de evidencia y de FallbackStorageService sin fallback; integración del detalle de acta con evidencia rechazada + nueva. Fuera de alcance: nuevo LocalStorageAdapter, cambiar expiración, migrar a quarkus-amazon-s3.

Tamaño estimado: M (~100-400 líneas).
Estrategia de entrega: Single Pull Request a develop.

## 2. Dependencias previas
- Ninguna

## 3. Specs numeradas
- **S1**: Corregir carga de evidencias fotográficas de actas — (S1) ConsultarActaDetalleUseCase:58 deja de tragarse excepciones: log WARN con actaId, publicId, s3Key y tipo de error (sin credenciales ni tokens). (S2) primeraEvidencia elige la evidencia más reciente no RECHAZADA (orden id desc), con fallback a la más reciente si todas están rechazadas. (S3) FallbackStorageService: eliminar la auto-inyección @FallbackProvider que se resuelve a sí misma y provoca StackOverflowError; sin fallback resoluble se propaga la StorageException original. (S4) ConsultarEvidenciaUseCase y EliminarEvidenciaUseCase usan StorageService en vez de S3StorageService, consistente con la subida. (S5) Frontend acta-detalle (coordinator y observer): en (error) del <img> recargar el detalle una vez para obtener URL prefirmada nueva (expiración 15 min). (S6) Tests: unit de selección de evidencia y de FallbackStorageService sin fallback; integración del detalle de acta con evidencia rechazada + nueva. Fuera de alcance: nuevo LocalStorageAdapter, cambiar expiración, migrar a quarkus-amazon-s3.

## 4. Contrato de entrada
A definir por el orquestador.

## 5. Contrato de salida
A definir por el orquestador.

## 6. Seguridad y resiliencia
- Sin riesgos adicionales identificados. Validación existente cubre regresiones.

## 7. Archivos afectados
- Ninguno
