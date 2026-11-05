# Specs: T-COMPLIANCE-07 — Data Transparency & Consent

**Last Updated:** 2026-11-05

---

## Feature: Cookie Consent Banner

```gherkin
Feature: Usuario puede gestionar consentimiento

  Scenario: Banner aparece first-visit
    GIVEN usuario sin consent stored
    WHEN carga app
    THEN banner sticky aparece at bottom:
      - "Usamos cookies para mejorar servicio"
      - Botón: "Aceptar Todo"
      - Botón: "Customizar"
      - Link: "Política de Privacidad"

  Scenario: Customizar preferences
    GIVEN banner visible
    WHEN clickea "Customizar"
    THEN modal abre:
      - [ ] Essential (required, disabled)
      - [ ] Analytics (Google Analytics, Sentry)
      - [ ] Marketing (tracking)
      - [ ] Preferences (localStorage)
    AND botones: "Rechazar Todo", "Aceptar Personalizadas"

  Scenario: Consent stored locally
    GIVEN usuario aceptó preferences
    WHEN refreshea página
    THEN banner no aparece
    AND localStorage tiene: {consent_given: true, timestamp, prefs}

  Scenario: Analytics no carga sin consent
    GIVEN usuario con analytics: false
    WHEN carga app
    THEN Google Analytics no inicializa
    AND Sentry no captura events (only errors)

  Scenario: Cambiar consentimiento en Settings
    GIVEN usuario en Settings → Privacy
    WHEN ve "Administrar Cookies"
    AND puede cambiar preferences
    THEN cambios persisten

  Scenario: Consentimiento versionado
    GIVEN consent v1 aceptado
    AND nueva privacy policy v2 publicada
    WHEN usuario login siguiente vez
    THEN banner re-aparece: "Política cambió, revisa"
```

---

**Total Scenarios:** 6  
**Last Updated:** 2026-11-05
