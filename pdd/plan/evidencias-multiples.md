# Plan: Evidencias múltiples por acta (máx. 5) con mosaico y visor

Status: plan

## 1. Descripción y alcance
Cambio de producto aprobado por el humano (2026-11-26): un acta admite hasta 5 evidencias fotográficas. (S1) Backend: SubirEvidenciaUseCase rechaza con 400 la sexta evidencia contando solo las no RECHAZADA; la regla vive en domain y tiene test de integración. (S2) Backend: ActaDetalleCompletoDto expone evidencias: lista de {publicId, url prefirmada, estado, creadoEn} ordenada de la más vieja a la más nueva; urlEvidencia se mantiene (deprecado) apuntando a la más reciente no rechazada. Un fallo al firmar una URL no tumba el detalle: esa entrada va con url null y se loguea. Matriz de roles sin cambios (OBSERVER autor y COORDINATOR/MASTER). RLS aplica. (S3) Frontend coordinador acta-detalle: mosaico (1 foto grande; 2-5 en cuadrícula, la más reciente primero), etiqueta #n · hora · estado, rechazadas atenuadas y visibles. (S4) Visor a pantalla completa ordenado de la más vieja a la más nueva, abre en la foto tocada, flechas/swipe, contador 'n de N', aprobar/rechazar por foto (COORDINATOR/MASTER). Reutiliza la renovación de URL caducada. (S5) Observador (registrar-acta, modificar-acta, correccion-acta): subir hasta 5 fotos, cada una comprimida a WebP en cliente y subida por separado con reintento individual sin duplicar; muestra el cupo restante. (S6) Expediente PDF: incluye todas las no RECHAZADA de la más vieja a la más nueva. (S7) Tests: integración del tope y del orden; unit del mosaico y visor; E2E de flujo crítico contra la API real (registrar acta con 2 fotos y verlas en el coordinador). Fuera de alcance: edición/recorte de fotos, más de 5, reordenar manualmente.

Tamaño estimado: L (~500-800 líneas).
Estrategia de entrega: Stacked PRs a develop, tras mergear fix/evidencia-carga: PR1 backend (S1, S2, S6 y sus tests); PR2 coordinador mosaico y visor (S3, S4) sobre PR1; PR3 observador subida múltiple (S5) y E2E (S7) sobre PR2.

## 2. Dependencias previas
- Ninguna

## 3. Specs numeradas
- **S1**: Evidencias múltiples por acta (máx. 5) con mosaico y visor — Cambio de producto aprobado por el humano (2026-11-26): un acta admite hasta 5 evidencias fotográficas. (S1) Backend: SubirEvidenciaUseCase rechaza con 400 la sexta evidencia contando solo las no RECHAZADA; la regla vive en domain y tiene test de integración. (S2) Backend: ActaDetalleCompletoDto expone evidencias: lista de {publicId, url prefirmada, estado, creadoEn} ordenada de la más vieja a la más nueva; urlEvidencia se mantiene (deprecado) apuntando a la más reciente no rechazada. Un fallo al firmar una URL no tumba el detalle: esa entrada va con url null y se loguea. Matriz de roles sin cambios (OBSERVER autor y COORDINATOR/MASTER). RLS aplica. (S3) Frontend coordinador acta-detalle: mosaico (1 foto grande; 2-5 en cuadrícula, la más reciente primero), etiqueta #n · hora · estado, rechazadas atenuadas y visibles. (S4) Visor a pantalla completa ordenado de la más vieja a la más nueva, abre en la foto tocada, flechas/swipe, contador 'n de N', aprobar/rechazar por foto (COORDINATOR/MASTER). Reutiliza la renovación de URL caducada. (S5) Observador (registrar-acta, modificar-acta, correccion-acta): subir hasta 5 fotos, cada una comprimida a WebP en cliente y subida por separado con reintento individual sin duplicar; muestra el cupo restante. (S6) Expediente PDF: incluye todas las no RECHAZADA de la más vieja a la más nueva. (S7) Tests: integración del tope y del orden; unit del mosaico y visor; E2E de flujo crítico contra la API real (registrar acta con 2 fotos y verlas en el coordinador). Fuera de alcance: edición/recorte de fotos, más de 5, reordenar manualmente.

## 4. Contrato de entrada
A definir por el orquestador.

## 5. Contrato de salida
A definir por el orquestador.

## 6. Seguridad y resiliencia
- Sin riesgos adicionales identificados. Validación existente cubre regresiones.

## 7. Archivos afectados
- Ninguno
