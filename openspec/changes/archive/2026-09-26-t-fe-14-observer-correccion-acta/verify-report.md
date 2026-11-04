# Verify Report — T-FE-14: Corrección de Acta Disputada en Campo (OBSERVER)

**Verificador**: sdd-verify (Ronda 1)  
**Fecha**: 2026-11-03  
**Rama**: brydyan/sc-361/t-fe-13-modo-foto-primero-capture-run-y-cotas  
**Cambio**: openspec/changes/front/2026-09-26-t-fe-14-observer-correccion-acta/  
**Veredicto**: **FIXES REQUIRED** (5 CRITICAL, 3 MAJOR)

---

## Compuertas Ejecutadas

### frontend/app baseline

| Compuerta | Comando | Resultado | Exit Code |
|-----------|---------|-----------|-----------|
| format:check | `pnpm format:check` | ✅ PASS | 0 |
| lint | `pnpm lint` | ✅ PASS (0 warnings) | 0 |
| test:ci | `pnpm test:ci` | ✅ PASS (529 tests, 61 archivos) | 0 |
| build | `pnpm build` | ✅ PASS (1 warning pre-existente) | 0 |

**Línea base anterior**: 508 tests  
**Línea base actual**: 529 tests  
**Delta**: +21 tests (T-FE-14 C.1)  
**Verificación**: ✅ Tests nuevos se ejecutan correctamente y pasan

---

## Mutaciones Aplicadas

Todas las mutaciones fueron ejecutadas en vivo y produjeron caídas de test. Esto demuestra que la cobertura es efectiva y no hay falsos positivos.

### Mutación 1: Quitar `checksumValido` de `botonEnvioBloqueado`

- **Ubicación**: `correccion-acta.page.ts:126` (getter `botonEnvioBloqueado()`)
- **Cambio**: `return !this.checksumValido || this.submitting()` → `return this.submitting()`
- **Comando**: `pnpm test:ci`
- **Resultado**: ✅ **TEST CAÍDO** — `"should mark checksum invalid if sufragantes is 0"`
- **Interpretación**: El botón **sí está** bloqueado cuando checksum es inválido. La condición es crítica.
- **Restauración**: Completada

### Mutación 2: Quitar validación `EN_DISPUTA` en `ngOnInit`

- **Ubicación**: `correccion-acta.page.ts:177` (método `procesarActaCargada`)
- **Cambio**: Bloque `if (acta.estado !== 'EN_DISPUTA') { navigate(...) }` comentado
- **Comando**: `pnpm test:ci`
- **Resultado**: ✅ **TEST CAÍDO** — `"should redirect to mis-jrvs if acta is not in EN_DISPUTA state"`
- **Interpretación**: La redirección **sí se ejecuta** cuando el estado no es EN_DISPUTA. Guard es efectivo.
- **Restauración**: Completada

### Mutación 3: Multiplicar `totalSufragantes × 2` en payload

- **Ubicación**: `correccion-acta.page.ts:252` (método `enviarCorreccion`)
- **Cambio**: `totalSufragantes: this.totalSufragantes()` → `totalSufragantes: this.totalSufragantes() * 2`
- **Comando**: `pnpm test:ci`
- **Resultado**: ✅ **TEST CAÍDO** — `"should call corregirActa with correct payload and navigate on success"`
- **Interpretación**: El payload **sí es verificado** contra el espacio de entrada. Transmisión correcta.
- **Restauración**: Completada

---

## QA Visual — Auditoría Accesibilidad y UX

**Herramientas**: ux-designer, taste-skill

### [CRITICAL] Hallazgos que Bloquean

#### 1. `role="alert"` + `aria-live="polite"` — Contradicción WCAG 4.1.3
- **Ubicación**: `correccion-acta.page.html:44–46` (banner de disputa)
- **Problema**: `role="alert"` implica automáticamente `aria-live="assertive"`. Añadir explícitamente `aria-live="polite"` crea conflicto que algunos lectores de pantalla resuelven de formas incompatibles (NVDA, VoiceOver, TalkBack). Resultado: banner puede no anunciar o anunciar dos veces.
- **Impacto**: Violación legal WCAG 4.1.3 (Status Messages). En contexto de campo, el coordinador no puede confiar en que el motivo de disputa se comunique.
- **Tipo**: CRITICAL — Accesibilidad obligatoria

#### 2. `aria-describedby="help-sufragantes"` Referencia Elemento Inexistente — WCAG 1.3.1
- **Ubicación**: `correccion-acta.page.html:134` (input Total Sufragantes)
- **Problema**: `id="help-sufragantes"` nunca se renderiza. Referencia rota causa que asistentes técnicas reporten error de estructura. Usuario de pantalla pierde la descripción del rango 1–500.
- **Impacto**: Violación WCAG 1.3.1 (Info and Relationships). En terreno, usuario con discapacidad visual no entiende restricción de entrada.
- **Tipo**: CRITICAL — Accesibilidad obligatoria

#### 3. Botón "Enviar Corrección" Deshabilitado Sin Explicación Visible
- **Ubicación**: `correccion-acta.page.html:233–243` (botón submit)
- **Problema**: Cuando checksum es inválido, botón se deshabilita. No hay mensaje visible en línea al botón explicando **por qué** está bloqueado. Tarjeta de checksum está abajo del formulario en orden de lectura.
- **Impacto**: Usuario con pantalla pequeña o en modo lectura (accesibilidad) ve botón muerto sin contexto. Viola WCAG 1.4.3 (Contrast) y heurística de visibilidad de estado del sistema.
- **Tipo**: CRITICAL — UX operacional

#### 4. Event `(change)` en Inputs Number No Dispara por Keystroke
- **Ubicación**: `correccion-acta.page.html:131, 149, 166` (Total Sufragantes, Votos Blancos, Votos Nulos)
- **Problema**: Inputs de votos candidatos usan `(ngModelChange)` (actualiza en tiempo real). Inputs de totales usan `(change)` que solo dispara en blur. Resultado: checksum no se actualiza en tiempo real para estos campos, contradice spec "Recalcula checksum inmediatamente ante cada pulsación."
- **Impacto**: Veedor digita "350" en sufragantes, mira checksum sin blur, ve datos obsoletos. Flujo de corrección interrumpido.
- **Tipo**: CRITICAL — Funcional

#### 5. Redirección Silenciosa Si Acta No Está EN_DISPUTA
- **Ubicación**: `correccion-acta.page.ts:177–181` (ngOnInit, con comentario "toast sería ideal pero no en scope")
- **Problema**: Si backend retorna acta en estado APROBADA/CORREGIDA/PENDIENTE, veedor es redirigido silenciosamente a `/observer/mis-jrvs` sin mensaje. En terreno sin WiFi consistente, usuario no entiende por qué formulario "desapareció".
- **Impacto**: Operación interrumpida, veedor sin feedback. Viola WCAG 4.1.3 (Status Messages).
- **Tipo**: CRITICAL — UX operacional

### [MAJOR] Hallazgos Mejorables

#### 6. `max="1000"` en Total Sufragantes Inconsistente con Lógica CPCCS
- **Ubicación**: `correccion-acta.page.html:128`
- **Problema**: TOPE_SUFRAGANTES_CNE = 500 pero HTML max="1000". Validación nativa del browser bloquea entrada 501–1000 incluso si CPCCS lo permite. Browser muestra tooltip nativo confuso.
- **Impacto**: Reporte confuso pero Angular ignora constraint, lógica sigue funcionando. UX degradada, no bloqueante.
- **Tipo**: MAJOR — Consistencia

#### 7. Encabezado "Acta ID #{{ actaId }}" Visible Durante Carga
- **Ubicación**: `correccion-acta.page.html:15–20` (fuera del bloque de loading)
- **Problema**: Header renderizado mientras spinner carga. ID acta (#42) visible antes de que usuario confirme es el acta correcto.
- **Impacto**: Momentáneo, sin impacto funcional. UX minor.
- **Tipo**: MINOR — UX

#### 8. `role="alert"` Interrumpe en Cada Keystroke
- **Ubicación**: `correccion-acta.page.html:222` (tarjeta error checksum)
- **Problema**: Cada transición checksum invalido → válido genera anuncio `assertive` en lectores de pantalla. Usuario digitando candidatos sufre interrupciones múltiples.
- **Impacto**: Distracción en pantalla, pero no bloquea tarea. Mejor UX con `aria-live="polite"` para validación inline.
- **Tipo**: MAJOR — UX accesibilidad

### Pasó (Sin Problema)

- ✅ Inputs candidatos tienen `<label>` + `aria-label` (redundante pero correcto)
- ✅ Botón Cancelar correctamente deshabilitado mientras envía
- ✅ Mensaje éxito usa `role="status"` (polite) — correcto
- ✅ Campos usan `type="number"`, `min="0"`, `step="1"` — correcto
- ✅ `takeUntil(destroy$)` previene memory leaks

---

## Síntesis

**Compuertas**: ✅ Todas pasan (format, lint, test:ci, build)  
**Mutaciones**: ✅ Las 3 caen correctamente (cobertura efectiva)  
**Accesibilidad/UX**: ❌ 5 CRITICAL, 3 MAJOR (ver fixes-required.md)

**Veredicto**: **FIXES REQUIRED** — La implementación funciona, pero hay violaciones de accesibilidad WCAG y UX operacional que bloquean despliegue a campo.
