# T-COMPLIANCE-03: Design — Licenses & IP Attribution

**Date:** 2026-11-05

---

## D1 — License format: HTML generated vs Manual markdown

**Decisión: HTML generated from package.json + licenses/ folder**

```
npm run licenses:build
→ genera frontend/src/assets/legal/licenses.html

Estructura:
  - Tabla con: Library | Version | License | Link
  - Full license text (collapsible)
  - Last updated: [build date]

Ventajas:
  ✓ Auto-sync con package.json
  ✓ Auditable: versionado con código
  ✓ No duplicación manual
```

**Alternativa descartada — Manual markdown**

```
Desventajas:
  ❌ Outdated cuando upgrade lib
  ❌ Olvidan actualizar
  ❌ Manual error-prone
```

---

## D2 — License compliance: Permissive (MIT/Apache) vs Restrictive (GPL/AGPL)

**Decisión: Aceptar permissive + requiere disclosure (GPL/AGPL)**

```
Permitidas:
  - MIT (permissive)
  - Apache 2.0 (permissive)
  - BSD (permissive)
  - ISC (permissive)
  
GPL/AGPL:
  - Requiere source code disclosure
  - Documentado claramente en /licenses
  - Link a source repository
  
Rechazadas (future):
  - BSL (Boost Software Licence)
  - Custom proprietary
```

---

## D3 — Icons/Assets: List vs Auto-detect

**Decisión: Manual list en assets-attribution.json**

```
assets-attribution.json:
[
  {
    "asset": "icon-check.svg",
    "source": "Material Design Icons",
    "url": "https://...",
    "license": "Apache 2.0",
    "author": "Google"
  }
]

Razón: Icons de múltiples fuentes, hard auto-detect
```

---

**Next:** specs, tasks  
**Last Updated:** 2026-11-05
