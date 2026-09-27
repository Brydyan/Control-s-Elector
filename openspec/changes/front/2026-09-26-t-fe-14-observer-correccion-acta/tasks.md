# Tasks — T-FE-14: Corrección de Acta Disputada en Campo (OBSERVER)

## Fase A: Ruta, Modelo y Servicio

- [ ] **A.1** — En `frontend/app/src/app/features/observer/models/observer.model.ts`:
  - Asegurar interfaces completas para `ActaDisputaDetalle` (incluyendo `motivoDisputa`, `urlEvidencia`, `coordinadorNombre`).
- [ ] **A.2** — En `frontend/app/src/app/features/observer/services/observer.service.ts`:
  - Verificar método `corregirActa(id: number, request: ActaRequestDto): Observable<ActaResponseDto>` contra `PUT /api/v1/observer/actas/{id}/corregir`.
  - Añadir método `getActaParaCorregir(id: number): Observable<ActaDetalleResponse>`.
- [ ] **A.3** — En `frontend/app/src/app/app.routes.ts`:
  - Registrar ruta `observer/acta/:id/corregir` con `canActivate: [roleGuard]`, `roles: ['OBSERVER', 'MASTER']`.

## Fase B: Componente de Corrección de Acta

- [ ] **B.1** — Crear `CorreccionActaPage` en `frontend/app/src/app/features/observer/pages/correccion-acta/`:
  - Carga inicial del acta; validación estricta de estado `EN_DISPUTA`.
  - Banner naranja de motivo de disputa con texto del coordinador.
  - Visor de imagen de evidencia original con toggle para ampliar.
  - Tabla/lista de inputs de votos de candidatos precargados.
  - Inputs para total sufragantes, votos blancos y votos nulos.
- [ ] **B.2** — Implementar lógica de checksum dinámico:
  - Comparación en tiempo real entre $\sum \text{votos}$ y `totalSufragantes`.
  - Soporte de regla CPCCS ($\sum \text{votos} \le \text{totalSufragantes} \times 3$).
  - Indicador visual de estado válido/inválido con delta numérico.
- [ ] **B.3** — Selector de imagen:
  - Opción de mantener foto previa o invocar cámara/archivo para reemplazar.
- [ ] **B.4** — Envío y feedback:
  - Llamada a `corregirActa()`.
  - Spinner/estado de carga durante la transmisión.
  - Toast de éxito y redirección a `/observer/mis-jrvs`.

## Fase C: Tests Unitarios y Verificación de Mutaciones

- [ ] **C.1** — En `correccion-acta.page.spec.ts`:
  - Test de redirección si el acta no está en estado `EN_DISPUTA`.
  - Test de renderizado de motivo de disputa.
  - Test de cálculo de checksum: botón deshabilitado con descuadre y habilitado con suma exacta.
  - Test de llamada a `corregirActa()` al enviar formulario válido.
- [ ] **C.2** — Verificación por mutación, ejecutada por quien implementa:
  - Quitar la comprobación `checksumValido` en la condición del botón `[disabled]` y comprobar que **cae** el test de bloqueo de envío. Anotá en `apply-progress.md` **el nombre del test que cayó**.
  - Quitar la verificación de estado `EN_DISPUTA` en el `ngOnInit` y comprobar que **cae** el test de redirección por estado inválido. Anotá en `apply-progress.md` **el nombre del test que cayó**.

---

## Qué NO hacer en esta fase

- NO añadir funcionalidad para anular o aprobar actas desde la vista de veedor (exclusivo de coordinación).
- NO re-implementar compresión de imagen desde cero; reutilizar `ImageCompressionService`.
- NO usar `localStorage` para tokens de sesión; respetar la arquitectura auth existente.

---

## Compuertas antes de dar por terminada la fase

```bash
cd frontend/app
pnpm lint
pnpm test:ci
```
- `0 errores de lint`
- Todos los tests unitarios nuevos en verde y suites previas pasando intactas (mínimo 118 tests base + nuevos tests de C.1).
