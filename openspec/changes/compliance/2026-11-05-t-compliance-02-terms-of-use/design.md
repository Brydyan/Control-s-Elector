# T-COMPLIANCE-02: Design — Terms of Use Page

**Date:** 2026-11-05  
**Author:** Control Electoral Team  
**Status:** Design  
**Depends on:** proposal.md

---

## D1 — Términos: Estático vs Dinámico / Editable

**Decisión: Estático, versionado con código**

```
Terms guardado en:
  - frontend/src/assets/legal/terms-of-use-es.md
  - frontend/src/assets/legal/terms-of-use-en.md
  
Cambios = nueva versión, nuevo deployment
Versión: almacenada en environment.ts (ej: TERMS_VERSION = "1.0")

Ventajas:
  ✓ Auditable: git history
  ✓ Versionado legal: fecha = commit date
  ✓ Previene cambios accidentales
```

**Alternativa descartada — Dinámico en BD**

```
Desventajas:
  ❌ Sin audit trail de cambios
  ❌ Usuario no sabe si términos cambió entre visitas
  ❌ Legal: cómo prueban fecha de aceptación?
  
Descartada porque: Legal compliance requiere versionado inmutable.
```

---

## D2 — Cambios de términos: Notificación vs Silent

**Decisión: Versión nueva = notificación obligatoria al login**

```
Si TERMS_VERSION cambió desde último login del usuario:
  - Login completa
  - Modal aparece: "Nuevos Términos de Uso - por favor revisar"
  - Usuario debe scroll todo el contenido
  - Botón "Aceptar y continuar"
  - Sin botón "declinar" (aceptación mandatoria para continuar)

Auditoría: tabla terms_acceptance_audit
  {usuario_id, terms_version, accepted_at, ip}

Ventajas:
  ✓ Usuario notificado de cambios
  ✓ Evidencia de aceptación
  ✓ Legal: timestamp de aceptación
```

**Alternativa descartada — Silent (cambios sin notificación)**

```
Usuario accede /terms, ve versión nueva sin saber si cambió.

Desventajas:
  ❌ Usuario no informado
  ❌ Aceptación implícita (weak legal)
  ❌ GDPR: cambios significativos requieren consent explícito
  
Descartada porque: Legal requiere notificación explícita.
```

---

## D3 — Liability: Limited vs Unlimited vs Specific caps

**Decisión: Liability limitada a amount paid (o zero si free)**

```
Términos especifican:
  "Liability limitada al monto pagado en últimos 12 meses.
   Si servicio es gratis, liability limitada a $0 (no hay damages)."

Ejemplos:
  - Usuario MASTER pagó $100/mes → max liability = $100
  - Usuario OBSERVER (gratis) → max liability = $0
  - Exceptions: gross negligence, data breach (unlimited)

Ventajas:
  ✓ Predictable risk
  ✓ Legal defense (capped exposure)
  ✓ Clear para usuarios
```

**Alternativa descartada — Sin límite ("liable for all damages")**

```
Desventajas:
  ❌ Infinite liability exposure
  ❌ Seguro costoso / imposible
  ❌ Riesgo de una falla = ruina financiera
  
Descartada porque: Unsustainable para negocio.
```

**Alternativa descartada — Specific cap en monto fijo**

```
"Liability max $10,000 regardless of payment"

Desventajas:
  ❌ Inequitable: MASTER paga $10k/año, liable = $10k total?
  ❌ Injusto para usuarios
  ❌ Legal: juez puede rechazar como unreasonable
  
Descartada porque: Payment-based es más justo.
```

---

## D4 — Prohibiciones: Específicas vs General

**Decisión: Específicas con ejemplos concretos**

```
Prohibiciones explícitas:
  - Hacking: "Intentar acceder BD sin autorización"
  - Spam: "500+ mensajes en < 1 hora"
  - Data export abuse: "Scraping de > 1000 registros/día"
  - Vote manipulation: "Cambiar votos de otro usuario"
  - Impersonation: "Usar credentials de otro usuario"
  - Malware: "Executar scripts en servidor"
  - DoS: "Enviar > 10k requests/min"

Consecuencia: Suspensión o ban de cuenta

Ventajas:
  ✓ Clear boundaries
  ✓ Precedent para enforcement
  ✓ Usuarios saben qué evitar
```

**Alternativa descartada — General ("no illegal activity")**

```
Desventajas:
  ❌ Vago: qué es "illegal activity"?
  ❌ Usuario no sabe límites
  ❌ Enforcement inconsistente
  
Descartada porque: Específico es más justo y enforceable.
```

---

## D5 — Jurisdicción: Local vs Neutral vs EU

**Decisión: Jurisdicción local (donde opera) + EU backup**

```
Términos especifican:
  "Estos Términos se rigen por leyes de [país operación].
   Disputes: foro exclusivo en [ciudad/país].
   EXCEPTO: si usuario en EU, GDPR + local courts aplican."

Justificación:
  - Operación es local → local jurisdiction natural
  - Pero EU usuarios tienen special protections (GDPR)

Ventajas:
  ✓ Claro: qué ley aplica
  ✓ GDPR compliant (no exclude EU)
  ✓ Enforceable
```

**Alternativa descartada — Arbitration (sin court)**

```
"Todas disputes resueltos via binding arbitration"

Desventajas:
  ❌ GDPR: no puedes excluir court access de EU users
  ❌ User friendly: arbitration es caro/opaco
  ❌ Google Play penaliza
  
Descartada porque: Courts more transparent, GDPR compliant.
```

---

## D6 — Warranty: Express vs Implied vs None

**Decisión: No warranties (AS-IS service)**

```
Términos dicen:
  "Servicio proveído AS-IS, sin warranties expresas o implícitas.
   EXCEPTO: garantía de no data loss (backups diarios),
            garantía de 99.5% uptime (SLA)."

Ventajas:
  ✓ Clear: qué se garantiza (backups, uptime)
  ✓ Clear: qué no (merchantability, fitness for purpose)
  ✓ Legal: standard para SaaS
```

**Alternativa descartada — Implied warranties**

```
"Software adecuado para propósito de usuario"

Desventajas:
  ❌ Vago: qué propósito?
  ❌ Liability exposure
  ❌ Imposible de cumplir siempre
  
Descartada porque: AS-IS es estándar y enforceable.
```

---

## D7 — Cambios futuros: 30 días notice vs Immediate

**Decisión: 30 días de aviso antes de cambios significativos**

```
Términos especifican:
  "Cambios significativos en Términos:
   - Notificación 30 días antes
   - Publicado en /terms + email a users activos
   - Cambios menores (links, typos) = inmediato"

Definición "significativo":
  - Liability limits change
  - Uso permitido change
  - Prohibiciones nuevas
  - Fees/pricing change

Ventajas:
  ✓ Fair: usuario tiempo para decidir
  ✓ Legal: standard practice
  ✓ GDPR: consent required para cambios
```

**Alternativa descartada — Cambios inmediatos**

```
"Términos pueden cambiar en cualquier momento sin aviso"

Desventajas:
  ❌ Injusto para usuarios
  ❌ GDPR violation (no consent)
  ❌ Google Play rejects
  
Descartada porque: Legal require notice period.
```

---

## Summary de Decisiones

| D | Elegido | Descartado | Razón |
|---|---------|-----------|-------|
| D1 | Estático | Dinámico | Auditable, legal |
| D2 | Notificación 30d | Silent | GDPR consent |
| D3 | Cap = payment | Unlimited, Fixed | Fair, sustainable |
| D4 | Específicas | General | Clear, enforceable |
| D5 | Local + EU | Arbitration only | GDPR + transparent |
| D6 | AS-IS + guarantees | Implied | Standard SaaS |
| D7 | 30 días notice | Immediate | Fair, legal |

---

**Next:** specs/terms.spec.md, tasks.md  
**Last Updated:** 2026-11-05
