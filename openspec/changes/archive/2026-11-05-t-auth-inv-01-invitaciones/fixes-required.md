# Fixes Required — T-AUTH-INV-01

**Status:** ✅ NINGÚN BLOQUEANTE IDENTIFICADO

---

## Hallazgos Bloqueantes (CRITICAL)

**Ninguno.** SDD está completo y verificado para construcción.

---

## Recomendaciones Opcionales (MINOR)

Estos **no bloquean** la construcción. Son mejoras de claridad que pueden aplicarse después:

### MINOR 1 — Aclaración en D1 (Design)

**Sección:** `design.md` → D1  
**Agregar nota:**
```
La VALIDACIÓN del JWT es stateless: TokenValidationService 
no necesita query a tabla usuario (no lookups BD).

El almacenamiento de claves públicas es externo 
(embebido en JAR o Redis caché), también stateless.

Esto mantiene la garantía D1: escalable sin session store.
```

**Por qué:** Evita ambigüedad sobre si hay consultas a BD. No es bloqueante.

---

### MINOR 2 — Especificar LoginUseCase en D6 (Design)

**Sección:** `design.md` → D6  
**Agregar pseudocódigo:**
```
LoginUseCase.authenticate(email, password):
  Usuario u = usuarioRepository.findByEmail(email);
  
  // Verificar estado
  if (u.estadoUsuario != EstadoUsuario.ACTIVO) {
    throw BusinessException("Cuenta aún no activada. " +
      "Revisa tu email para completar el registro.");
  }
  
  // Verificar password...
```

**Por qué:** Tasks.md lo especifica, pero agregar a design.md cierra la brecha. No es bloqueante.

---

### MINOR 3 — Especificar Queue en D3 (Design)

**Sección:** `design.md` → D3  
**Reemplazar:**
```
"Envía emails async (rate-limited: 100/min, retry 3x)"
```

**Con:**
```
"Envía emails async vía Redis Streams (alojado en stack T-COMPLIANCE-06).
Rate limiting: 100/min via Bucket4j. Retry: 3x con backoff exponencial (1s, 2s, 4s).
El consumer batch-procesa: 100 emails/min, reintenta automáticamente en fallo."
```

**Por qué:** Concretiza tecnología (Redis elegido en stack). No es bloqueante.

---

## Qué NO hay que rehacer

- ✅ **Todas las 6 decisiones (D1-D6)** están bien justificadas
- ✅ **Todos los 8 scenarios** son ejecutables en Gherkin
- ✅ **Las 17 tasks + 30 subtasks** tienen acceptance criteria claras
- ✅ **Las mutaciones** están especificadas por task

**NO tocar esto.** Es sólido. Las recomendaciones MINOR son "nice to have", no correcciones.

---

## Conclusión

**SDD está APROBADO para construcción sin correcciones bloqueantes.**

Las recomendaciones MINOR pueden aplicarse en próxima revisión o dejarse para versión 1.1 del SDD.

**El Builder puede comenzar ahora con Phase 1 (DB Schema) en task T-AUTH-01.15.**

---

**Verificador:** Arquitecto  
**Fecha:** 2026-11-06
