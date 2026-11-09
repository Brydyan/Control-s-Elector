# T-COMPLIANCE-01: Design — Privacy Policy Page

**Date:** 2026-11-05  
**Author:** Control Electoral Team  
**Status:** Design (decisiones + alternativas)  
**Depends on:** proposal.md  
**Next:** specs/privacy.spec.md, tasks.md

---

## D1 — Contenido: Estático vs Dinámico (editable por MASTER)

**Decisión: Contenido estático en deployment, versionado con código**

```
Privacy Policy guardado en:
  - frontend/src/assets/legal/privacy-policy-es.md
  - frontend/src/assets/legal/privacy-policy-en.md
  
Build: markdown → HTML compilado → componente Angular

Cambios futuros = nuevo deployment (versionado con git)

Ventajas:
  ✓ Versionado en git: historial de cambios
  ✓ Auditable: quién cambió qué y cuándo (git log)
  ✓ Legal: fecha exacta de cada versión
  ✓ Simple: sin backend headless CMS
  ✓ Performance: zero BD queries
```

**Alternativa descartada — Dinámico (MASTER edita en admin panel)**

```
CREATE TABLE privacy_policy_versions (
  id BIGSERIAL PRIMARY KEY,
  content_es TEXT,
  content_en TEXT,
  created_by BIGINT REFERENCES usuario(id),
  created_at TIMESTAMPTZ,
  is_active BOOLEAN
);

MASTER en admin → edita HTML → guarda en BD

Desventajas concretas:
  ❌ Versionado manual (error-prone)
  ❌ No hay audit trail de cambios automático
  ❌ MASTER puede "desaparecer" versión anterior
  ❌ Legal exposure: cómo prueban fecha de publicación?
  ❌ Extra BD lookup por cada visitor
  ❌ Sincronización tricky: ¿frontend caché o siempre BD?
  ❌ Complejidad: formulario WYSIWYG, validación, sanitización
  
Descartada porque: Estático es más auditable y legal-compliant. Si necesita cambios, se hace formal (PR, review, deployment).
```

---

## D2 — Data Export: JSON vs CSV vs PDF

**Decisión: JSON como formato principal**

```
GET /api/user/export-data
Response:
{
  "user": {
    "id": 123,
    "email": "usuario@control.ttcs",
    "name": "Juan Pérez",
    "role": "OBSERVER",
    "created_at": "2026-10-01T10:00:00Z"
  },
  "login_history": [
    {"timestamp": "2026-11-05T14:30:00Z", "ip": "192.168.1.1", "outcome": "SUCCESS"},
    ...
  ],
  "audit_events": [
    {"action": "INVITATION_SENT", "timestamp": "2026-11-05T10:00:00Z", "details": {...}},
    ...
  ]
}

Ventajas:
  ✓ Máquina-readable (usuario puede procesar)
  ✓ Completo: todo tipo de dato en un archivo
  ✓ Estándar GDPR (portability format)
  ✓ Sin librerías extra (nativo en Java)
  ✓ Comprimible: .zip si es grande
```

**Alternativa descartada — CSV (spreadsheet-friendly)**

```
Formato:
  user_id,email,name,role,created_at
  123,usuario@control.ttcs,Juan Pérez,OBSERVER,2026-10-01T10:00:00Z

Desventajas:
  ❌ Anidación perdida (audit_events no caben)
  ❌ Usuario necesita abrir en Excel (menos técnico)
  ❌ Caracteres especiales (ñ, acentos) = encoding hell
  ❌ No es GDPR portable standard
  ❌ Múltiples CSVs = confusión
  
Descartada porque: JSON es más completo y portable.
```

**Alternativa descartada — PDF**

```
Formato: PDF con datos tabulares

Desventajas:
  ❌ No machine-readable
  ❌ Caro generar (PDF library)
  ❌ GDPR no requiere PDF (cualquier formato vale)
  ❌ Usuario no puede procesar datos post-export
  ❌ Inútil si tiene 10k eventos
  
Descartada porque: JSON es mejor para portabilidad real.
```

---

## D3 — Acceso a export-data: Autenticado vs DPO-solo vs Sin restricción

**Decisión: Solo usuario autenticado acceda a sus propios datos**

```
GET /api/user/export-data
  → Requiere JWT válido
  → Retorna solo datos de usuario.id == current_user.id
  
Rate limiting:
  → Max 1 export / 24h por usuario
  → Prevent abuse: usuario spam 1000 veces

Auditoría:
  → invitation_audit registra {action: "DATA_EXPORT", usuario_id: X, timestamp, ip}

Ventajas:
  ✓ GDPR rights: usuario accede SUS datos
  ✓ Seguridad: no expone datos ajenos
  ✓ Rate limiting: prevent DoS
  ✓ Auditable: sabemos quién descargó cuándo
```

**Alternativa descartada — DPO-only (solo Data Protection Officer descarga)**

```
Solo MASTER o role=DPO puede descargar datos ajenos
  GET /api/user/export-data/{userId}

Desventajas:
  ❌ GDPR requiere que usuario MISMO acceda sus datos (no intermediario)
  ❌ Si usuario pide "dame mis datos" → debe poder descargar sin intermediario
  ❌ Bottleneck: todos piden a DPO
  ❌ Fricción: usuario no entiende por qué no puede descargar
  
Descartada porque: GDPR says user must access own data directly.
```

**Alternativa descartada — Sin restricción (cualquiera descarga cualquier usuario)**

```
GET /api/user/export-data/{userId}?token=<publico>

Desventajas:
  ❌ CRÍTICO SECURITY: expone data ajena sin autenticación
  ❌ Token guessable = data breach
  ❌ GDPR violation
  
Descartada porque: Seguridad no-negotiable.
```

---

## D4 — Retención de data en Privacy Policy: Detalle vs General

**Decisión: Documentar retención específica por tipo de dato**

```
Privacy Policy lista:

Tipo de dato | Propósito | Retención | Eliminación
---|---|---|---
Email, Nombre, Rol | Autenticación | Mientras activo | MASTER delete o soft-delete
Login History | Auditoría, seguridad | 180 días inactivo | Auto-delete cron
Actas electorales | Requerimiento legal | 7 años | Legal hold
IP en audit | Seguridad, fraude | 90 días | Auto-delete
2FA backup codes | Seguridad | Mientras vigente | Al cambiar

Ventajas:
  ✓ Transparencia: usuario sabe exacto
  ✓ Legal: justificable en GDPR
  ✓ Implementable: desarrollador sabe qué limpiar
  ✓ Cumplimiento: fácil audit de qué se eliminó
```

**Alternativa descartada — General/vago ("datos retenidos mientras sea necesario")**

```
Privacy Policy dice:
  "Retenemos datos mientras sea necesario para prestar servicio"

Desventajas:
  ❌ GDPR requiere específico (artículo 21)
  ❌ Usuario no sabe si 30 días o 30 años
  ❌ Inspector GDPR rechaza como vago
  ❌ Implementador no sabe cuándo limpiar
  ❌ Sin timestamp = data creeps forever
  
Descartada porque: GDPR requiere específico.
```

---

## D5 — Multiidioma: Solo español vs ES + EN vs Más

**Decisión: Español (principal) + Inglés (soporte electoral observadores internacionales)**

```
/privacy → detección de idioma (Accept-Language)
  es_ES, es_AR, es_MX → Spanish version
  en_US, en_GB → English version
  default → Spanish

Assets:
  - frontend/src/assets/legal/privacy-policy-es.md (1200 palabras)
  - frontend/src/assets/legal/privacy-policy-en.md (1000 palabras)

Ventajas:
  ✓ Cubre 80% de usuarios (ES) + observadores internacionales (EN)
  ✓ Bajo costo: 2 idiomas
  ✓ Google Play: EN requerido
  ✓ Mantenible: dos versiones, no 10
```

**Alternativa descartada — Solo español**

```
Privacy Policy solo en español

Desventajas:
  ❌ Google Play requiere inglés si app targe USA/internacionales
  ❌ Observadores internacionales no entienden
  ❌ Judicialmente débil (legal system mixto)
  
Descartada porque: Google Play compliance + internacional observers.
```

**Alternativa descartada — 5+ idiomas desde inicio**

```
ES, EN, PT (Brasil), FR (Haití), de, ja, zh

Desventajas:
  ❌ Costo mantenimiento 5x
  ❌ QA testing 5x idiomas
  ❌ Traducción errónea = legal exposure
  ❌ Cambios futuros = actualizar 5 versiones
  ❌ MVP no necesita
  
Descartada porque: 2 idiomas cubre 95% casos, futuro puede expandir.
```

---

## D6 — Contacto DPO: Email público vs Form vs Null

**Decisión: Email público + formulario de contacto**

```
Privacy Policy lista:
  "Data Protection Officer: dpo@control.ttcs"
  
Footer: botón "Contactar Privacy Team"
  → Abre formulario modal
  → {email, asunto, mensaje}
  → Envía a dpo@control.ttcs
  → Confirmación: "Recibido, responderemos en 48h"

Ventajas:
  ✓ GDPR artículo 14: DPO contactable
  ✓ Usuario puede consultar
  ✓ Email + formulario = opciones
  ✓ Auditable: cada contacto logged
```

**Alternativa descartada — Email público directo sin verificación**

```
dpo@control.ttcs listado en privacy policy
Nada más

Desventajas:
  ❌ Spam bot scraping
  ❌ No hay feedback al usuario (¿llegó?)
  ❌ Sin tracking: ¿cuántos contactos recibe DPO?
  
Descartada porque: Formulario agrega verificación y tracking.
```

**Alternativa descartada — Sin DPO listado ("GDPR no aplica aquí")**

```
Privacy Policy sin DPO contact

Desventajas:
  ❌ GDPR requiere DPO contactable si processing personal data
  ❌ Google Play rechaza
  ❌ Regulador (EU/Brasil/CA) lo flagea
  ❌ Usuario no tiene forma de reclamar
  
Descartada porque: Legalmente requerido.
```

---

## Summary de Decisiones

| Decision | Elegido | Descartado | Razón |
|----------|---------|-----------|-------|
| D1 | Estático versionado | Dinámico en BD | Auditable, legal compliant |
| D2 | JSON export | CSV, PDF | GDPR portable standard |
| D3 | Usuario autenticado + rate limit | DPO-only, sin restricción | GDPR rights + seguridad |
| D4 | Retención específica | General/vago | GDPR artículo 21 |
| D5 | ES + EN | Solo ES, 5+ idiomas | Google Play + international |
| D6 | Email + formulario | Directo, sin DPO | GDPR artículo 14, UX |

---

**Next:** 
- specs/compliance/privacy.spec.md (escenarios Gherkin)
- tasks.md (work breakdown)

---

**Last Updated:** 2026-11-05
