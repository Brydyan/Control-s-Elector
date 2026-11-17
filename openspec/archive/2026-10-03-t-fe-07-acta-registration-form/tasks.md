# Tasks — T-FE-07: Formulario de Registro de Acta (Wizard 3 Pasos)

## Fase A: Estructura del Wizard y Gestión de Estado

- [x] **A.1** — Implementar `features/observer/services/acta-storage.service.ts`:
  - `guardarBorrador(draft: LocalDraft): void`
  - `recuperarBorrador(jrvId: number, cargoId: number): LocalDraft | null`
  - `limpiarBorrador(jrvId: number, cargoId: number): void`
- [x] **A.2** — Implementar lógica de control de pasos (`currentStep: 'VOTOS' | 'IMAGEN' | 'REVISION'`) en `RegistrarActaPage`.
- [x] **A.3** — Carga de candidatos vía `ObserverService.getCandidatosPorCargo(cargoId)`.

## Fase B: Validación Aritmética y Digitación

- [x] **B.1** — Enlace bidireccional de votos por candidato (`votosPorCandidato: Map<number, number>`), blancos, nulos y total sufragantes.
- [x] **B.2** — Getters reactivos de cuadre:
  - `sumaVotosCandidatos`, `totalVotosComputados`, `diferenciaChecksum`, `isChecksumValid`.
- [x] **B.3** — Autoguardado local en cada pulsación con notificación visual temporal.

## Fase C: Respaldo Fotográfico y Previsualización

- [x] **C.1** — Manejador de selección de imagen (`onFileSelected`) con generación de `FileReader` dataURL para vista previa.
- [x] **C.2** — Controles de navegación entre pasos (`irAPasoImagen()`, `irARevision()`, `volverAVotos()`, `volverAImagen()`).

## Fase D: Transmisión y Pruebas

- [x] **D.1** — Método `guardarComoBorrador()` llamando a `ObserverService.guardarBorrador()`.
- [x] **D.2** — Método `enviarActaFinal()` validando `isChecksumValid` y enviando a `ObserverService.enviarActaDirecta()`.
- [x] **D.3** — Subida encadenada de imagen con `ObserverService.subirImagenActa(actaId, file)`.
- [x] **D.4** — Tests unitarios en `registrar-acta.page.spec.ts` verificando validación y llamadas al servicio.

## Verificación por Mutación

- **V.1** — Modificar `isChecksumValid` para retornar siempre true -> los tests de guardia matemática deben fallar.
- **V.2** — Cambiar clave multipart de subida de `'imagen'` a `'file'` -> el backend o los tests de integración deben rechazar con 400.
