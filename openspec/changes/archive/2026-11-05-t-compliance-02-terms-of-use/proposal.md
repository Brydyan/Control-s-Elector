# T-COMPLIANCE-02: Propuesta — Terms of Use Page (Términos de Uso)

**Date:** 2026-11-05  
**Author:** Control Electoral Team  
**Status:** Proposal  
**Priority:** CRITICAL  
**Estimación:** 3-4 puntos → 1 sprint

---

## Qué se construye

Una **página estática de Términos de Uso (Terms of Use)** que documenta:
- Responsabilidades del usuario y del sistema
- Prohibiciones (spam, abuse, tampering)
- Limitación de liability
- Disputes resolution
- Cambios en términos (notification)
- Google Play Store compliance

## Por qué es necesario

**Hoy:**
```
❌ Sin Terms of Use documentados
❌ Google Play rechaza sin Terms of Use claro
❌ Usuario no sabe responsabilidades
❌ Sin protección legal para cambios de servicio
❌ Ambiguedad en derechos / obligaciones
```

**Con Terms of Use:**
```
✅ Página /terms documentada
✅ Google Play compliant
✅ Responsabilidades claras (usuario ↔ sistema)
✅ Prohibiciones explícitas
✅ Liability limits para protección legal
✅ Cambios de términos notificados
```

## Problema específico

- No existe página `/terms` que explique términos de servicio
- Usuario no sabe qué está permitido / prohibido
- Google Play Store rechaza sin Terms of Use
- Sin documento legal de cambios futuros de términos
- No hay límite de responsabilidad documentado

## Solución de alto nivel

```
Flow: Frontend - Terms Page
/terms → Página estática con:
  - Uso aceptable (información electoral, sin spam/abuse)
  - Prohibiciones (hacking, data manipulation, mass export)
  - Responsabilidad del usuario (info accuracy)
  - Responsabilidad del sistema (availability SLA, limits)
  - Intellectual property (copyright, trademarks)
  - Warranty disclaimer
  - Limitation of liability
  - Termination conditions
  - Governing law (jurisdicción)
  - Cambios en términos (notificación 30 días)
  - Link a Privacy Policy
  - Effective date y version
```

## Alcance

✅ **DENTRO:**
- Página `/terms` con contenido legal completo
- Secciones: uso aceptable, prohibiciones, liability, cambios
- Responsive design
- Multiidioma: español + inglés
- Link desde footer global
- Versioning (v1.0, v1.1, etc)
- Changelog de cambios previos (en archivo aparte)
- Google Play compliance

❌ **FUERA DE ALCANCE:**
- Ley específica per país (MVP: general + "governing law" clause)
- Integración con legal team para cambios (manual process)
- A/B testing de términos (futuro)
- Waiver / acceptance tracking (futuro → T-COMPLIANCE-07)
- Dispute arbitration system (futuro)

## Dependencias / Supuestos

- ✅ Frontend: Angular 19
- ✅ Backend: logs para auditar si usuario aceptó términos
- ✅ Legal: términos pre-approved (no genera si no hay template)

## Beneficios

| Beneficio | Antes | Después |
|-----------|-------|---------|
| Google Play compliance | ❌ | ✅ |
| Legal protection | ❌ | ✅ (Limited) |
| User clarity | ❌ Cero | ✅ Completa |
| Liability limits | ❌ | ✅ |

## Criterios de aceptación

- [ ] Página `/terms` accesible públicamente
- [ ] Incluye 8+ secciones de términos
- [ ] Responsive design
- [ ] Multiidioma (ES + EN)
- [ ] Version number (v1.0)
- [ ] Effective date visible
- [ ] Google Play: checked ✅
- [ ] No PII en términos

---

**Status:** Ready for design phase  
**Last Updated:** 2026-11-05
