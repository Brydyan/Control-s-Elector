# T-COMPLIANCE-03: Propuesta — Licenses & IP Attribution Page

**Date:** 2026-11-05  
**Priority:** CRITICAL  
**Estimación:** 2-3 puntos → 1 sprint

---

## Qué se construye

Página `/licenses` que documenta:
- Open source libraries + sus licenses (MIT, Apache 2.0, GPL, etc)
- Icons, assets, third-party code attributions
- Google Play requirements: license compliance
- GDPR-compliant acknowledgments

## Por qué es necesario

- Google Play rechaza apps sin attribution clara
- Legal risk: GPL/AGPL violation si no se documenta
- Transparencia: usuarios saben qué es used
- Compliance: open source foundation requirements

## Alcance

✅ DENTRO:
- Página /licenses con lista de librerías
- License text (full) para cada dependency
- Icon/asset attributions
- Google Play compliance
- Auto-generated from package.json/pom.xml

❌ FUERA:
- Custom license creation
- License automation via CI/CD (future)

---

**Status:** Ready for design  
**Last Updated:** 2026-11-05
