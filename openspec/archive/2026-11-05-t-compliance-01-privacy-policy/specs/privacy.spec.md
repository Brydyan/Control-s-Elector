# Specs: T-COMPLIANCE-01 — Privacy Policy Page

**Format:** Gherkin (GIVEN/WHEN/THEN)  
**Depends on:** design.md  
**Last Updated:** 2026-11-05

---

## Feature: Privacy Policy Page Access

```gherkin
Feature: Usuario puede acceder y leer Política de Privacidad

  Scenario: Página /privacy accesible públicamente en español
    GIVEN usuario no autenticado
    WHEN navega a /privacy
    THEN ve página Privacy Policy completa
    AND contenido está en español
    AND secciones: qué datos, por qué, cuánto tiempo, derechos, contacto
    AND responsive design (mobile, tablet, desktop)
    AND footer con "Última actualización: [fecha]"
    AND link "Volver a inicio" funciona

  Scenario: Detectar idioma y mostrar inglés
    GIVEN navegador con Accept-Language: en-US
    WHEN accede a /privacy
    THEN ve Privacy Policy en inglés
    AND contiene mismas secciones que versión español
    AND link "Cambiar a español" disponible

  Scenario: Footer global contiene link a Privacy Policy
    GIVEN usuario en cualquier página
    WHEN mira footer
    THEN ve link "Política de Privacidad"
    AND clickea → navega a /privacy
    AND breadcrumb muestra: Home > Privacy Policy

  Scenario: Tabla de retención de datos es clara
    GIVEN usuario en /privacy
    WHEN scroll hasta sección "Retención de Datos"
    THEN ve tabla con columnas:
      - Tipo de dato (Email, Login History, etc)
      - Propósito (Autenticación, Auditoría, etc)
      - Período de retención (180 días, 7 años, etc)
      - Cómo se elimina (Auto-delete, MASTER delete, etc)
    AND ejemplos concretos: "Login history: retenemos 180 días de inactividad"

  Scenario: Derechos GDPR listados explícitamente
    GIVEN usuario en /privacy
    WHEN scroll hasta "Tus Derechos"
    THEN ve checklist de derechos:
      - ✓ Derecho de acceso (descargar tus datos)
      - ✓ Derecho de portabilidad (JSON format)
      - ✓ Derecho de olvido (solicitar eliminación)
      - ✓ Derecho de rectificación (corregir datos)
      - ✓ Derecho a oponerme (opt-out)
    AND cada derecho tiene instrucciones: "cómo ejercerlo"

  Scenario: Contacto DPO visible y accesible
    GIVEN usuario en /privacy
    WHEN scroll hasta "Contacto"
    THEN ve:
      - Email: dpo@control.ttcs
      - Botón: "Contactar Privacy Team"
      - Tiempo de respuesta esperado: "48 horas"
    AND clickea botón → modal abre con form

  Scenario: Privacy Policy accesible offline (service worker cached)
    GIVEN app con service worker
    AND user descargó privacy policy antes (caché)
    WHEN usuario sin conexión navega a /privacy
    THEN ve privacy policy en caché
    AND nota visual: "Modo offline - contenido en caché"
```

---

## Feature: Data Export - Export Personal Data (GDPR Portability)

```gherkin
Feature: Usuario puede descargar copia de sus datos en JSON

  Scenario: Usuario autenticado accede endpoint export-data
    GIVEN usuario autenticado (JWT válido)
    WHEN hace GET /api/user/export-data
    THEN respuesta retorna 200 OK
    AND Content-Type: application/json
    AND archivo descargado: data-[userId]-[timestamp].json

  Scenario: JSON export contiene datos personales
    GIVEN usuario Juan (id: 123)
    WHEN descarga sus datos
    THEN JSON includes:
      {
        "user": {
          "id": 123,
          "email": "juan@control.ttcs",
          "name": "Juan Pérez",
          "role": "OBSERVER",
          "created_at": "2026-10-01T10:00:00Z",
          "last_login": "2026-11-05T14:30:00Z"
        },
        "login_history": [
          {"timestamp": "2026-11-05T14:30:00Z", "ip": "192.168.1.1", "outcome": "SUCCESS"},
          {"timestamp": "2026-11-05T09:15:00Z", "ip": "192.168.1.50", "outcome": "SUCCESS"},
          ...
        ],
        "audit_events": [
          {
            "action": "INVITATION_SENT",
            "timestamp": "2026-10-01T10:00:00Z",
            "details": {"email": "juan@...", "token_exp": "..."}
          },
          ...
        ]
      }

  Scenario: Usuario no autenticado no puede descargar
    GIVEN usuario sin JWT
    WHEN hace GET /api/user/export-data
    THEN respuesta retorna 401 Unauthorized
    AND no descarga nada

  Scenario: Usuario no puede descargar datos de otro usuario
    GIVEN usuario Juan (id: 123) autenticado
    WHEN intenta GET /api/user/export-data (que requiere id de user actual)
    THEN retorna solo datos de Juan
    AND NO retorna datos de usuario X (id: 456)
    AND endpoint no acepta parámetro {userId}

  Scenario: Rate limiting: máximo 1 export / 24h
    GIVEN usuario Juan
    WHEN descarga datos a las 14:30 (export #1)
    AND intenta descargar de nuevo a las 14:35 (export #2)
    THEN export #1 es exitoso (200)
    AND export #2 falla con 429 Too Many Requests
    AND error message: "Límite: 1 descarga por 24 horas"
    AND puede reintentar después de 24h

  Scenario: Auditoría registra cada descarga
    GIVEN usuario Juan descarga datos
    WHEN POST /api/user/export-data completa
    THEN invitation_audit tiene row:
      - usuario_id: 123
      - accion: "DATA_EXPORT"
      - evento: "SUCCESS"
      - datos: {ip: "192.168.1.1", file_size: "45KB", timestamp: "2026-11-05T14:30:00Z"}
      - created_at: NOW()

  Scenario: Large dataset export (1000+ events) es comprimido
    GIVEN usuario con historial muy largo (5000 eventos)
    WHEN descarga datos
    THEN JSON es valido (no truncado)
    AND size < 5MB
    AND opcionalmente: respuesta comprimida (gzip) si .json.gz

  Scenario: Timestamps en UTC ISO 8601
    GIVEN usuario descarga datos
    WHEN revisa JSON
    THEN todos los timestamps están en formato ISO 8601 UTC:
      - "2026-11-05T14:30:00Z" (Z = UTC)
      - NO "14:30" o timestamps locales
      - NO millisegundos (segundos es suficiente)
```

---

## Feature: Contact DPO Form

```gherkin
Feature: Usuario puede contactar Data Protection Officer via form

  Scenario: Form modal abre al clickear "Contactar Privacy Team"
    GIVEN usuario en página /privacy
    WHEN clickea botón "Contactar Privacy Team"
    THEN modal abre con form:
      - Campo: Email (prefilled si autenticado)
      - Campo: Asunto (required)
      - Campo: Mensaje (required, min 20 caracteres)
      - Botón: "Enviar"
      - Botón: "Cancelar"

  Scenario: Form validación en tiempo real
    GIVEN modal abierto
    WHEN usuario digita:
      - Email: no válido "abc"
      - Asunto: vacío
      - Mensaje: "Hola" (< 20 chars)
    THEN campos muestran error inline:
      - Email: "Ingresa email válido"
      - Asunto: "Asunto requerido"
      - Mensaje: "Mínimo 20 caracteres"
    AND botón "Enviar" deshabilitado

  Scenario: Form envía email a DPO
    GIVEN usuario completa form valido:
      - Email: "juan@control.ttcs"
      - Asunto: "Solicitud de datos personales"
      - Mensaje: "Hola, me gustaría acceder a mis datos..."
    WHEN clickea "Enviar"
    THEN email enviado a dpo@control.ttcs
    AND email contiene: {user_email, asunto, mensaje, timestamp}
    AND modal cierra
    AND toast muestra: "Mensaje enviado, responderemos en 48h"

  Scenario: Contactos duplicados prevenidos
    GIVEN usuario envía form a las 14:30
    AND intenta enviar idéntico 10 segundos después
    THEN segundo envío rechazado
    AND frontend muestra: "Aguarda 30 segundos entre contactos"
    AND backend rate limita por IP + email

  Scenario: Auditoría registra cada contacto DPO
    GIVEN usuario envía form
    WHEN email a dpo@control.ttcs es enviado
    THEN invitation_audit tiene row:
      - usuario_id: 123 (o NULL si no autenticado)
      - accion: "DPO_CONTACT_FORM"
      - evento: "SUCCESS"
      - datos: {email_usuario: "juan@...", asunto: "...", ip: "..."}
```

---

## Feature: Legal Compliance

```gherkin
Feature: Privacy Policy cumple requisitos legales

  Scenario: Google Play checklist compliance
    GIVEN Privacy Policy en /privacy
    WHEN auditor revisa
    THEN verifica:
      - ✓ Accesible públicamente
      - ✓ Menciona qué datos se recopilan
      - ✓ Menciona por qué se recopilan
      - ✓ Menciona si se comparte con terceros
      - ✓ Link a Privacy Policy en App Store listing
      - ✓ Es clara y comprensible (no legalese solo)

  Scenario: GDPR compliance checklist
    GIVEN Privacy Policy + backend
    WHEN auditor GDPR revisa
    THEN verifica:
      - ✓ Artículo 13/14: Información obligatoria presente
      - ✓ Artículo 15: Derecho de acceso (export-data endpoint)
      - ✓ Artículo 17: Derecho de olvido (delete account)
      - ✓ Artículo 20: Derecho de portabilidad (JSON format)
      - ✓ Artículo 21: Cómo oponerse (contacto en privacy policy)
      - ✓ Artículo 38: DPO identificable y contactable

  Scenario: Versionado de Privacy Policy
    GIVEN Privacy Policy versión 1.0 desplegada 2026-11-05
    WHEN MASTER realiza cambios y despliega versión 1.1
    THEN:
      - Versión anterior accesible en git history
      - Timestamp de última actualización cambia
      - Changelog en PR describe qué cambió
      - Legal tiene registro: "versión X cambió en fecha Y"

  Scenario: No hay datos PII en logs públicos
    GIVEN logs del servidor
    WHEN se auditan requests a /api/user/export-data
    THEN:
      - Logs no contienen JSON exportado
      - Logs solo contienen: {usuario_id, timestamp, ip, outcome}
      - Datos sensibles protegidos (no logueados)
```

---

## Non-Functional Requirements

```gherkin
Feature: Performance & Compliance

  Scenario: Página /privacy carga < 2 segundos
    GIVEN usuario en /privacy
    WHEN navega a la página
    THEN Time to Interactive < 2 segundos
    AND CLS (Cumulative Layout Shift) < 0.1

  Scenario: Export data < 3 segundos para dataset típico
    GIVEN usuario con 500 eventos
    WHEN descarga datos
    THEN response time < 3 segundos
    AND no timeout

  Scenario: Mobile accesible (WCAG 2.1 AA)
    GIVEN Privacy Policy page
    WHEN acceso via mobile (< 768px)
    THEN:
      - Text readable sin zoom (16px+)
      - Buttons clickable (44px min)
      - Color contrast >= 4.5:1
      - Sin horizontal scroll
      - Touch targets spaced adecuadamente
```

---

**Total Scenarios:** 22 escenarios cubiertos  
**Coverage Areas:** Privacy Page, Data Export, DPO Contact, Compliance

**Last Updated:** 2026-11-05
