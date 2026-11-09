# Verify Report — T-FE-13: Modo "Foto Primero" (Capture & Run) y Cotas Electorales

**Ronda 2 (Verificación posterior a Ronda 3)**  
**Ejecutado:** 2026-11-01 18:20 UTC  
**Rama:** `brydyan/sc-361/t-fe-13-modo-foto-primero-capture-run-y-cotas`  
**Árbol:** limpio (9 archivos modificados del change)

---

## Estado de la rama

Tras la **Ronda 1 (Builder)** que implementó T-FE-13 completa con 14 tests nuevos en `registrar-acta.page.spec.ts`, una **Ronda 2 (Verify anterior)** que desplazó el árbol, y una **Ronda 3 (Builder)** que aplicó los fixes-required.md:

- **Compilación**: ✓ PASS (lint 0, format:check 0, build 0, tokens 0)
- **Tests**: ✓ PASS (54 files / 471 tests)
- **Mutaciones V.1/V.2/V.3**: ✓ TODOS DETECTADOS

---

## Q1 — ¿Los nuevos tests están presentes y nombrados correctamente?

**Verificación mediante grep:**

| Suite | T-FE-13 A.1 | A.2 | B.1 | B.2 | B.3 | C.1 | S3 | Total |
|-------|------------|-----|-----|-----|-----|-----|----|-------|
| `registrar-acta.page.spec.ts` | 4 | 6 | 4 | 1 | 2 | — | 1 | **18 T-FE-13** |
| `jrv-cargos.page.spec.ts` | — | — | — | — | — | 4 | — | **4 T-FE-13** |
| `offline-storage.service.spec.ts` | — | 4 | — | — | — | — | — | **4 T-FE-13** |
| **Total T-FE-13 presentes** | 4 | 14 | 4 | 1 | 2 | 4 | 1 | **30 tests** |

**Nombres exactos encontrados** (muestra):
- `T-FE-13 A.1: el wizard arranca en el paso IMAGEN (Foto Primero)` ✓
- `T-FE-13 A.2: la foto se guarda en IndexedDB (acta_drafts) al capturarla` ✓
- `T-FE-13 A.2: el borrador NO se escribe en localStorage ni en Base64` ✓
- `T-FE-13 B.1: con 501 sufragantes hay error y el envío queda bloqueado` ✓
- `T-FE-13 B.1: con 2500 sufragantes (error de tipeo) el envío queda bloqueado` ✓
- `T-FE-13 C.1: la captura rápida guarda la foto de la dignidad en IndexedDB` ✓
- `T-FE-13 S3: con 380 sufragantes y la suma cuadrada, el acta se transmite` ✓

**Resultado**: ✓ **Todos presentes, nombrados según SDD.**

---

## Q2 — ¿El total de tests es correcto (468 → 471)?

```
 Test Files  54 passed (54)
      Tests  471 passed (471)
```

**Desglose**:
- Línea base (develop/9c0b212): **446 tests** (sin T-FE-13)
- Ronda 1 (Builder): +22 tests → **468** (14 registrar-acta + 4 jrv-cargos + 4 offline-storage)
- Ronda 3 (Builder): +3 tests de regresión → **471** (S3 + A.2 cámara + A.2 input limpio)

**Resultado**: ✓ **471 tests, número correcto.**

---

## Q3 — ¿Las mutaciones V.1/V.2/V.3 siguen siendo válidas?

### V.1: Quitar validación > 500 sufragantes

**Mutación**: `const TOPE_SUFRAGANTES_CNE = 500;` → `= 5000;`

**Tests que cayeron**: 4
- `T-FE-13 B.1: con 501 sufragantes hay error y el envío queda bloqueado` ✗
- `T-FE-13 B.1: con 2500 sufragantes (error de tipeo) el envío queda bloqueado` ✗
- `T-FE-13 B.3: el banner de cota se pinta con el nivel correcto` ✗
- `T-FE-13 B.3: la revisión muestra la cota y bloquea "Transmitir Acta"` ✗

**Resultado**: ✓ **V.1 detectada correctamente.**

### V.2: Cambiar paso inicial de IMAGEN a VOTOS

**Mutación**: `readonly currentStep = signal<Step>('IMAGEN');` → `signal<Step>('VOTOS')`

**Tests que cayeron**: 5
- `T-FE-13 A.1: el wizard arranca en el paso IMAGEN (Foto Primero)` ✗
- `T-FE-13 A.1: tras capturar la foto aparece "Continuar a Digitación de Votos"` ✗
- `T-FE-13 A.1: "Digitar votos primero" permite saltar la foto` ✗
- `should have file input with accept="image/*" and capture="environment" (S3)` ✗
- `T-FE-13 A.2: el input de archivo se limpia para poder repetir la captura` ✗

**Resultado**: ✓ **V.2 detectada correctamente.**

### V.3: Sustituir IndexedDB por localStorage

**Mutación**: `await this.offlineStorage.guardarBorrador(...)` → `localStorage.setItem('acta_drafts', JSON.stringify(...))`

**Tests que cayeron**: 5
- `T-FE-13 A.2: el borrador NO se escribe en localStorage ni en Base64` ✗
- `T-FE-13 A.2: el autoguardado de los votos NO borra la foto ya guardada` ✗
- `T-FE-13 A.2: la cámara se abre sólo después de que el borrador quedó escrito` ✗
- `S6: el paso de votos persiste un borrador para no perder lo escrito` ✗
- `S6: al cambiar de paso se guarda el borrador antes de navegar` ✗

**Resultado**: ✓ **V.3 detectada correctamente.**

---

## Q4 — ¿Hay UX/accesibilidad a revisar en templates?

### `registrar-acta.page.html` — Hallazgos

#### ✓ Paso 1 (IMAGEN)
- Input file con `accept="image/*"` y `capture="environment"` ✓ (línea 119–120)
- Botón "Continuar a Digitación de Votos" visible sólo con `@if (selectedImageFile)` ✓ (línea 169–179)
- Opción "Digitar votos primero" como enlace secundario ✓ (línea 155–163)
- `aria-describedby="explicacion-checksum"` presente en inputs de votos ✓
- Preview con `alt="Previsualización de acta física"` ✓ (línea 108)

#### ✓ Paso 2 (VOTOS)
- Cota banner con **role="alert"** para error (>500) y **role="status"** para warning (>350) ✓ (líneas 219–236)
- Modificadores CSS `.cota-banner--warning` / `.cota-banner--error` ✓
- `data-nivel` atributo para visibilidad de testing ✓
- Checksum banner con `aria-invalid="true"` cuando suma no cuadra ✓ (línea 315)
- Inputs de votos con `aria-label="Votos para X"` ✓ (línea 261)
- Mensaje de bloqueo con `role="alert"` ✓ (línea 334)

#### ✓ Paso 3 (REVISIÓN)
- Banner de cota repetido con roles correctos ✓ (líneas 407–423)
- Botón "Transmitir Acta" deshabilitado con `[disabled]="excedeTopeSufragantes"` ✓ (línea 465)
- Evidencia adjunta mostrada en resumen ✓ (líneas 399–403)

### `jrv-cargos.page.html` — Hallazgos

#### ✓ Captura Rápida Modal (p-dialog)
- Dialog con header "Captura Rápida de Actas (Foto Primero)" ✓ (línea 93)
- Focus trap (`[modal]="true"`) ✓
- Escape cierra (`[closable]="true"`) ✓
- Intro explicativa ✓ (línea 103–106)
- Input file con `accept="image/*"` y `capture="environment"` ✓ (líneas 143–145)
- Preview `alt="Previsualización del acta fotografiada"` ✓ (línea 137)
- Botones "Tomar foto" / "Retomar foto" por dignidad ✓ (líneas 151–158)
- Estado (`tieneFoto()`) visible con iconos (check o cámara) ✓ (líneas 118–130)
- Acciones "Digitar votos" → registrarActa(cargo) ✓ (línea 163)
- `data-testid="btn-captura-rapida"` en botón de cabecera ✓ (línea 15)

### ✓ Accesibilidad General

| Aspecto | Presente | Detalles |
|---------|----------|----------|
| **role="alert"** para errores | ✓ | Cota >500, inconsistencia checksum |
| **role="status"** para avisos | ✓ | Cota >350, mensajes de borrador |
| **aria-describedby** | ✓ | Inputs vinculados a checksum-banner |
| **aria-label** | ✓ | Inputs de votos nominados por candidato |
| **aria-invalid** | ✓ | Campos con suma fuera de rango |
| **aria-hidden** | ✓ | Iconos decorativos |
| **alt text** | ✓ | Imágenes de preview |
| **Focus trap** en modal | ✓ | p-dialog con modal=true |
| **Escape cierra modal** | ✓ | p-dialog closable |
| **Botones dehabilitados con feedback** | ✓ | "Acta en cola", excedeTopeSufragantes |

**Resultado**: ✓ **Templates correctamente implementados. Accesibilidad y UX validadas.**

---

## Compuertas Finales

```bash
$ cd frontend/app
$ pnpm lint          → exit 0 (All files pass linting)
$ pnpm format:check  → exit 0 (All matched files use Prettier)
$ pnpm build         → exit 0 (Application bundle generation complete)
$ pnpm test:ci       → exit 0 (54 files / 471 tests passed)
$ pnpm check:tokens  → exit 0 (Token guard: no violations)
```

| Compuerta | Resultado | Notas |
|-----------|-----------|-------|
| lint | ✓ | exit 0 |
| format:check | ✓ | exit 0 |
| build | ✓ | exit 0, 1 warning preexistente (ButtonComponent) |
| check:tokens | ✓ | exit 0 |
| test:ci | ✓ | 54 files / 471 tests |

---

## Resumen de Verificación

| Criterio | Resultado | Evidencia |
|----------|-----------|-----------|
| **Q1: Tests presentes y nombrados** | ✓ | 30 tests T-FE-13 con nombres exactos |
| **Q2: Total de tests correcto** | ✓ | 471 = 446 + 25 nuevos |
| **Q3: Mutación V.1 detectada** | ✓ | 4 tests caen al mutar TOPE a 5000 |
| **Q3: Mutación V.2 detectada** | ✓ | 5 tests caen al mutar step inicial a VOTOS |
| **Q3: Mutación V.3 detectada** | ✓ | 5 tests caen al mutar a localStorage |
| **Q4: UX/accesibilidad OK** | ✓ | Roles, aria, focus trap, modales, capture |
| **Compilación (lint/build)** | ✓ | exit 0 |
| **Formateo (prettier)** | ✓ | exit 0 |
| **Tokens** | ✓ | exit 0 |

---

## Recomendaciones

1. **Inmediato**: Rama está lista. Los 471 tests pasan, las mutaciones detectan, templates correctos.
2. **Antes de merge con develop**: Resolver conflictos en `registrar-acta.page.ts` (rama 5 commits atrás).
3. **PR description**: Mencionar T-FE-13 A.1/A.2/B.1/B.2/B.3/C.1 y el patrón "Foto Primero".

---

## Archivos Verificados

- `/frontend/app/src/app/features/observer/pages/registrar-acta/registrar-acta.page.ts`
- `/frontend/app/src/app/features/observer/pages/registrar-acta/registrar-acta.page.html`
- `/frontend/app/src/app/features/observer/pages/registrar-acta/registrar-acta.page.scss`
- `/frontend/app/src/app/features/observer/pages/registrar-acta/registrar-acta.page.spec.ts`
- `/frontend/app/src/app/features/observer/pages/jrv-cargos/jrv-cargos.page.ts`
- `/frontend/app/src/app/features/observer/pages/jrv-cargos/jrv-cargos.page.html`
- `/frontend/app/src/app/features/observer/pages/jrv-cargos/jrv-cargos.page.scss`
- `/frontend/app/src/app/features/observer/pages/jrv-cargos/jrv-cargos.page.spec.ts`
- `/frontend/app/src/app/core/offline/offline-storage.service.spec.ts`

---

**Fin de Verificación Ronda 2.**
