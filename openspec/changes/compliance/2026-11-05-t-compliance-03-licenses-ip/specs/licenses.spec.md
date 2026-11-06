# Specs: T-COMPLIANCE-03 — Licenses & IP

**Last Updated:** 2026-11-05

---

## Feature: Licenses Page

```gherkin
Feature: Página /licenses muestra atribuciones

  Scenario: Página /licenses accesible
    GIVEN usuario navega a /licenses
    THEN ve tabla de open source libraries
    AND columns: Library | Version | License | Link

  Scenario: License text expandable
    GIVEN /licenses page
    WHEN clickea "Ver License" en row
    THEN collapsible abre con full license text (MIT, Apache, etc)

  Scenario: GPL/AGPL claramente marked
    GIVEN /licenses
    WHEN busca GPL/AGPL libraries
    THEN ve warning: "This library requires source disclosure"
    AND link to source repository

  Scenario: Google Play compliance
    GIVEN /licenses
    WHEN auditor revisa
    THEN:
      - ✓ Accesible in app
      - ✓ Todos los open source libraries listados
      - ✓ License text disponible
      - ✓ Link a sources (si applicable)

  Scenario: Assets attribution
    GIVEN /licenses
    WHEN scroll a "Assets & Icons"
    THEN ve lista:
      - Material Icons (Google, Apache 2.0)
      - Font Awesome (MIT)
      - etc.
    AND cada uno con link a original source
```

---

**Total Scenarios:** 5  
**Last Updated:** 2026-11-05
