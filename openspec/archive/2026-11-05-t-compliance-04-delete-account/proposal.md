# T-COMPLIANCE-04: Propuesta — Delete Account (GDPR Right to Forget)

**Date:** 2026-11-05  
**Priority:** CRITICAL  
**Estimación:** 4-5 puntos → 1-2 sprints

---

## Qué se construye

Sistema de eliminación de cuenta que cumple GDPR artículo 17 (Right to Erasure):
- Usuario puede solicitar eliminar su cuenta + datos personales
- Soft-delete con 30 días de "grace period" (recuperable)
- Hard-delete después 30 días (permanente, irreversible)
- Auditoría de cada eliminación
- Reporte de datos que se mantienen por requisitos legales (actas electorales)

## Por qué es necesario

- GDPR artículo 17: "Derecho al olvido"
- Google Play: "usuarios pueden pedir account deletion"
- Protección de privacidad
- Legal compliance para retención de datos

## Alcance

✅ DENTRO:
- UI: "Eliminar Cuenta" en settings
- Soft-delete: estado = "DELETED", marcado con timestamp
- Hard-delete: cron job después 30 días
- Confirmación: email de seguridad antes de eliminar
- Exceptions: actas electorales retenidas por ley (7 años)
- Auditoría completa

❌ FUERA:
- Data anonymization (futuro)
- Selective deletion (delete only some data) → futuro
- API data export antes de delete (T-COMPLIANCE-01)

---

**Status:** Ready for design  
**Last Updated:** 2026-11-05
