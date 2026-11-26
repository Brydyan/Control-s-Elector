# Task: Corregir carga de evidencias fotográficas de actas

Slug: fix-evidencia-carga
Status: active
Date: 2026-11-26

## Context

(S1) ConsultarActaDetalleUseCase:58 deja de tragarse excepciones: log WARN con actaId, publicId, s3Key y tipo de error (sin credenciales ni tokens). (S2) primeraEvidencia elige la evidencia más reciente no RECHAZADA (orden id desc), con fallback a la más reciente si todas están rechazadas. (S3) FallbackStorageService: eliminar la auto-inyección @FallbackProvider que se resuelve a sí misma y provoca StackOverflowError; sin fallback resoluble se propaga la StorageException original. (S4) ConsultarEvidenciaUseCase y EliminarEvidenciaUseCase usan StorageService en vez de S3StorageService, consistente con la subida. (S5) Frontend acta-detalle (coordinator y observer): en (error) del <img> recargar el detalle una vez para obtener URL prefirmada nueva (expiración 15 min). (S6) Tests: unit de selección de evidencia y de FallbackStorageService sin fallback; integración del detalle de acta con evidencia rechazada + nueva. Fuera de alcance: nuevo LocalStorageAdapter, cambiar expiración, migrar a quarkus-amazon-s3.

## Specs

S1. Corregir carga de evidencias fotográficas de actas: (S1) ConsultarActaDetalleUseCase:58 deja de tragarse excepciones: log WARN con actaId, publicId, s3Key y tipo de error (sin credenciales ni tokens). (S2) primeraEvidencia elige la evidencia más reciente no RECHAZADA (orden id desc), con fallback a la más reciente si todas están rechazadas. (S3) FallbackStorageService: eliminar la auto-inyección @FallbackProvider que se resuelve a sí misma y provoca StackOverflowError; sin fallback resoluble se propaga la StorageException original. (S4) ConsultarEvidenciaUseCase y EliminarEvidenciaUseCase usan StorageService en vez de S3StorageService, consistente con la subida. (S5) Frontend acta-detalle (coordinator y observer): en (error) del <img> recargar el detalle una vez para obtener URL prefirmada nueva (expiración 15 min). (S6) Tests: unit de selección de evidencia y de FallbackStorageService sin fallback; integración del detalle de acta con evidencia rechazada + nueva. Fuera de alcance: nuevo LocalStorageAdapter, cambiar expiración, migrar a quarkus-amazon-s3.

## Tasks

- [ ] T1: Implementación del work item

## Log

- L1: Task scaffolded for slug "fix-evidencia-carga": Corregir carga de evidencias fotográficas de actas.
