# Task: Evidencias múltiples por acta (máx. 5) con mosaico y visor

Slug: evidencias-multiples
Status: active
Date: 2026-11-26

## Context

Cambio de producto aprobado por el humano (2026-11-26): un acta admite hasta 5 evidencias fotográficas. (S1) Backend: SubirEvidenciaUseCase rechaza con 400 la sexta evidencia contando solo las no RECHAZADA; la regla vive en domain y tiene test de integración. (S2) Backend: ActaDetalleCompletoDto expone evidencias: lista de {publicId, url prefirmada, estado, creadoEn} ordenada de la más vieja a la más nueva; urlEvidencia se mantiene (deprecado) apuntando a la más reciente no rechazada. Un fallo al firmar una URL no tumba el detalle: esa entrada va con url null y se loguea. Matriz de roles sin cambios (OBSERVER autor y COORDINATOR/MASTER). RLS aplica. (S3) Frontend coordinador acta-detalle: mosaico (1 foto grande; 2-5 en cuadrícula, la más reciente primero), etiqueta #n · hora · estado, rechazadas atenuadas y visibles. (S4) Visor a pantalla completa ordenado de la más vieja a la más nueva, abre en la foto tocada, flechas/swipe, contador 'n de N', aprobar/rechazar por foto (COORDINATOR/MASTER). Reutiliza la renovación de URL caducada. (S5) Observador (registrar-acta, modificar-acta, correccion-acta): subir hasta 5 fotos, cada una comprimida a WebP en cliente y subida por separado con reintento individual sin duplicar; muestra el cupo restante. (S6) Expediente PDF: incluye todas las no RECHAZADA de la más vieja a la más nueva. (S7) Tests: integración del tope y del orden; unit del mosaico y visor; E2E de flujo crítico contra la API real (registrar acta con 2 fotos y verlas en el coordinador). Fuera de alcance: edición/recorte de fotos, más de 5, reordenar manualmente.

## Specs

S1. Evidencias múltiples por acta (máx. 5) con mosaico y visor: Cambio de producto aprobado por el humano (2026-11-26): un acta admite hasta 5 evidencias fotográficas. (S1) Backend: SubirEvidenciaUseCase rechaza con 400 la sexta evidencia contando solo las no RECHAZADA; la regla vive en domain y tiene test de integración. (S2) Backend: ActaDetalleCompletoDto expone evidencias: lista de {publicId, url prefirmada, estado, creadoEn} ordenada de la más vieja a la más nueva; urlEvidencia se mantiene (deprecado) apuntando a la más reciente no rechazada. Un fallo al firmar una URL no tumba el detalle: esa entrada va con url null y se loguea. Matriz de roles sin cambios (OBSERVER autor y COORDINATOR/MASTER). RLS aplica. (S3) Frontend coordinador acta-detalle: mosaico (1 foto grande; 2-5 en cuadrícula, la más reciente primero), etiqueta #n · hora · estado, rechazadas atenuadas y visibles. (S4) Visor a pantalla completa ordenado de la más vieja a la más nueva, abre en la foto tocada, flechas/swipe, contador 'n de N', aprobar/rechazar por foto (COORDINATOR/MASTER). Reutiliza la renovación de URL caducada. (S5) Observador (registrar-acta, modificar-acta, correccion-acta): subir hasta 5 fotos, cada una comprimida a WebP en cliente y subida por separado con reintento individual sin duplicar; muestra el cupo restante. (S6) Expediente PDF: incluye todas las no RECHAZADA de la más vieja a la más nueva. (S7) Tests: integración del tope y del orden; unit del mosaico y visor; E2E de flujo crítico contra la API real (registrar acta con 2 fotos y verlas en el coordinador). Fuera de alcance: edición/recorte de fotos, más de 5, reordenar manualmente.

## Tasks

- [ ] T1: Implementación del work item

## Log

- L1: Task scaffolded for slug "evidencias-multiples": Evidencias múltiples por acta (máx. 5) con mosaico y visor.
