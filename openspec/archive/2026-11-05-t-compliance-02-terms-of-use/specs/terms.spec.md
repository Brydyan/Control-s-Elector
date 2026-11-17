# Specs: T-COMPLIANCE-02 — Terms of Use

**Format:** Gherkin  
**Depends on:** design.md  
**Last Updated:** 2026-11-05

---

## Feature: Terms of Use Page

```gherkin
Feature: Usuario puede acceder y leer Términos de Uso

  Scenario: Página /terms accesible públicamente
    GIVEN usuario no autenticado
    WHEN navega a /terms
    THEN ve página Terms of Use completa
    AND contains: uso aceptable, prohibiciones, liability, cambios
    AND footer muestra: "Effective Date: 5 de noviembre de 2026"
    AND version number: "v1.0"

  Scenario: Terms of Use en español e inglés
    GIVEN navegador con Accept-Language: es
    WHEN accede /terms
    THEN ve Terms en español
    AND link "View in English" disponible

  Scenario: Footer global tiene link a Terms
    GIVEN usuario en cualquier página
    WHEN mira footer
    THEN ve link "Términos de Uso"
    AND clickea → /terms

  Scenario: Secciones claras y navigables
    GIVEN usuario en /terms
    WHEN mira contenido
    THEN ve índice con secciones:
      - Uso Aceptable
      - Prohibiciones
      - Responsabilidad del Usuario
      - Limitación de Responsabilidad
      - IP Rights
      - Disclaimer
      - Cambios en Términos
      - Contacto / Disputes
    AND cada sección es scrollable y linkeable
    AND tabla de contenidos con jump links
```

---

## Feature: Acceptance on Login (New Terms)

```gherkin
Feature: Usuario debe aceptar términos nuevos al login

  Scenario: Términos nuevos triggean modal en login
    GIVEN usuario con TERMS_VERSION = "1.0"
    AND nuevos términos deploydos: TERMS_VERSION = "1.1"
    WHEN usuario hace login exitoso
    THEN modal aparece: "Nuevos Términos de Uso"
    AND muestra cambios principales entre 1.0 y 1.1
    AND botón: "Leer Términos Completos" → /terms
    AND botón: "Aceptar y Continuar"
    AND no hay botón "Decline" (aceptación mandatoria)

  Scenario: Modal scroll-to-bottom antes de aceptar
    GIVEN modal con términos largo
    WHEN usuario intenta clickear "Aceptar"
    AND no scrolleó hasta el final
    THEN botón deshabilitado
    AND tooltip: "Please read entire document"
    AND cuando scrollea a bottom → botón habilita

  Scenario: Aceptación auditada
    GIVEN usuario clickea "Aceptar"
    WHEN action completa
    THEN terms_acceptance_audit tiene row:
      - usuario_id: 123
      - terms_version: "1.1"
      - accepted_at: NOW()
      - ip: "192.168.1.1"
      - browser_user_agent: "Mozilla/..."
    AND usuario.terms_version actualizado a "1.1"

  Scenario: Usuario sin cambios de términos ≠ modal
    GIVEN usuario con TERMS_VERSION = "1.0"
    AND términos siguen siendo "1.0"
    WHEN login
    THEN NO modal aparece
    AND login normal

  Scenario: Cambios menores (typos) ≠ notificación
    GIVEN TERMS_VERSION sigue "1.0"
    AND solo typo fijo: "responsibilty" → "responsibility"
    WHEN usuario con "1.0" hace login
    THEN NO modal (versión = versión)
    AND términos file actualizado, pero version string igual
```

---

## Feature: Liability & Warranty Clauses

```gherkin
Feature: Liability términos son claros

  Scenario: Liability cap por pago
    GIVEN Terms /terms
    WHEN auditor revisa "Limitation of Liability"
    THEN ve claro:
      "Liability limitada al amount pagado en últimos 12 meses.
       Si usuario es free tier, liability = $0."

  Scenario: Exceptions a liability (gross negligence, data breach)
    GIVEN Terms
    WHEN auditor busca excepciones
    THEN ve:
      - Gross negligence: unlimited liability
      - Data breach: unlimited liability (if proved)
      - Breach confidentiality: unlimited
      - IP infringement: unlimited

  Scenario: Warranty disclaimer claro
    GIVEN Terms
    WHEN auditor revisa "Warranties"
    THEN ve:
      "Service provided AS-IS. No warranties express or implied.
       EXCEPT: daily backups (99% data retention),
               99.5% uptime SLA."
```

---

## Feature: Prohibitions

```gherkin
Feature: Prohibiciones explícitas y ejemplos

  Scenario: Hacking/unauthorized access prohibido
    GIVEN /terms "Prohibiciones" section
    WHEN usuario revisa
    THEN ve:
      "Prohibido: acceso no autorizado a BD, APIs, o systems.
       Ejemplo: intentar password brute-force, SQL injection."

  Scenario: Spam prohibido
    GIVEN /terms "Prohibiciones"
    THEN ve:
      "Prohibido: enviar > 500 mensajes en < 1 hora.
       Consecuencia: account suspension."

  Scenario: Vote manipulation prohibido
    GIVEN /terms "Prohibiciones"
    THEN ve:
      "Prohibido: alterar votos ajenos, cambiar results,
       modificar acta electoral. Consecuencia: legal action."

  Scenario: Data export abuse prohibido
    GIVEN /terms "Prohibiciones"
    THEN ve:
      "Prohibido: scrapar > 1000 registros/día via API.
       Rate limiting: 100 req/min. Violation = IP block."

  Scenario: DoS prohibido
    GIVEN /terms "Prohibiciones"
    THEN ve:
      "Prohibido: enviar > 10,000 requests/min.
       Consecuencia: IP bloqueada + legal action."
```

---

## Feature: Jurisdiction & Dispute Resolution

```gherkin
Feature: Jurisdicción clara para disputes

  Scenario: Governing law especificado
    GIVEN /terms
    WHEN auditor revisa "Governing Law"
    THEN ve:
      "Governed by laws of [País Operación].
       EXCEPT: EU users = GDPR + EU court jurisdiction."

  Scenario: Dispute forum especificado
    GIVEN /terms "Dispute Resolution"
    THEN ve:
      "Exclusive forum: courts of [Ciudad/País].
       Binding arbitration option (if both parties agree)."
```

---

## Feature: Version History & Changelog

```gherkin
Feature: Cambios de términos documentados

  Scenario: Version number visible
    GIVEN /terms page
    WHEN usuario revisa footer
    THEN ve: "v1.0 - Effective: 5 de noviembre de 2026"

  Scenario: Changelog de versiones anteriores
    GIVEN /terms
    WHEN usuario clickea "Ver historial de cambios"
    THEN ve page con:
      - v1.0 (2026-11-05): Initial version
      - v0.9 (2026-10-15): [cambios]
      - etc.
    AND link a cada versión anterior

  Scenario: 30 días notice en cambios significativos
    GIVEN new Terms v1.1 deployed
    AND cambio significativo (liability cap change)
    WHEN usuario loggin antes de 30 días
    THEN modal: "Términos de Servicio Cambiarán el 5 de diciembre"
    AND "Cambios: liability cap de $100 → $50"
    AND link a full comparison

  Scenario: Minor changes no notification
    GIVEN Terms v1.0 actualizado: typo "responsibilty" → "responsibility"
    AND version string sigue "v1.0"
    WHEN usuario login
    THEN NO notification (internal fix)
```

---

**Total Scenarios:** 18 escenarios  
**Last Updated:** 2026-11-05
