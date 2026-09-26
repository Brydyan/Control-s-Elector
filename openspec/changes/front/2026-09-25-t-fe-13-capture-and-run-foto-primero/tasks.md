# Tasks — T-FE-13: Modo "Foto Primero" (Capture & Run) y Cotas Electorales

## Fase A: Reordenamiento del Wizard

- [ ] **A.1** — En `RegistrarActaPage`:
  - Cambiar valor inicial de `currentStep` a `'IMAGEN'`.
  - Botón "Continuar a Votos" visible tras seleccionar o capturar la foto.
  - Opción secundaria de texto "Digitar votos primero" para casos excepcionales sin cámara.
- [ ] **A.2** — Persistir dataURL o referencia de la foto en `ActaStorageService` para no perderla si se refresca la pantalla.

## Fase B: Cotas de Sufragantes y Validaciones de Suma

- [ ] **B.1** — Implementar getters de validación de cota:
  - `alertaSufragantes`: Warning si > 350, Error si > 500.
  - `esSufragantesValido`: `totalSufragantes > 0 && totalSufragantes <= 500`.
- [ ] **B.2** — Ajustar regla de cuadre para soportar CPCCS (hasta 3 votos por elector si el cargo corresponde a CPCCS).
- [ ] **B.3** — Integrar banners visuales de advertencia y error en el template `registrar-acta.page.html`.

## Fase C: Botón de Captura Rápida en JrvCargosPage

- [ ] **C.1** — En `JrvCargosPage`:
  - Botón de acción superior "Captura Rápida de Actas (Foto Primero)".
  - Despliega modal o flujo secuencial de toma de fotos por dignidad.

## Fase D: Tests Unitarios

- [ ] **D.1** — En `registrar-acta.page.spec.ts`:
  - Test inicio en paso IMAGEN.
  - Test advertencia con 380 sufragantes y bloqueo con 501.
  - Test validación de cuadre exacto.
- [ ] **D.2** — Medir con `npm test -- --run`.

## Verificación por Mutación

- **V.1** — Quitar la validación de > 500 sufragantes -> test con 2500 sufragantes debe fallar.
- **V.2** — Cambiar el paso inicial a 'VOTOS' -> test de flujo Foto Primero debe fallar.
