# T-COMPLIANCE-01: Tasks — Privacy Policy Page

**Effort:** 3-4 puntos → 1 sprint  
**Depends on:** design.md, specs/privacy.spec.md

---

## Sprint 1: Frontend Privacy Page + Backend Export

### Task 1.1 — Privacy Policy Page (Frontend HTML)

**Estimación:** 1.5 pts

**Guarantee:** Privacy Policy `/privacy` accesible, responsive, multilidioma

- [ ] **C.1.1** — Crear componente PrivacyPolicyComponent
  - Path: `frontend/src/app/legal/privacy-policy/privacy-policy.component.ts`
  - Route: `/privacy` accesible públicamente (sin autenticación)
  - Contenido en markdown (assets/legal/privacy-policy-*.md)
  - Soporta ES + EN vía `Accept-Language`

- [ ] **C.1.1 — Mutación, ejecutada por Builder**
  - Quitá route `/privacy` de routing.module.ts
  - Corré tests: test debe caer cuando no encuentra ruta
  - Anotá en `apply-progress.md` nombre del test: `[Tu nombre aquí]`
  - Restaurá route

- [ ] **C.1.2** — Diseño responsive (mobile, tablet, desktop)
  - Desktop (> 1024px): 2 columnas (nav + content)
  - Tablet (768-1024px): 1.5 columnas
  - Mobile (< 768px): 1 columna full-width
  - Test viewport sizes: 375px, 768px, 1440px

- [ ] **C.1.2 — Mutación**
  - Quitá media query `@media (max-width: 768px)` de CSS
  - Abrí página en mobile → verifica que layout breaks
  - Test debe fallar: `responsive-mobile.spec.ts`
  - Anotá nombre del test
  - Restaurá CSS

- [ ] **C.1.3** — Tabla de retención de datos
  - Componente: RetentionTableComponent
  - Datos: JSON hardcoded (assets/legal/retention-table.json)
  - Columnas: Dato, Propósito, Retención, Eliminación
  - Sorteable por columna
  - Mobile: stackable (cards en vez de tabla)

- [ ] **C.1.3 — Mutación**
  - Quitá 1 row de tabla (ej: "Login History")
  - Corré tests: `retention-table.spec.ts` debe caer
  - Anotá test name
  - Restaurá

- [ ] **C.1.4** — Links y navegación
  - Footer global: link "Política de Privacidad" → /privacy
  - Breadcrumb: Home > Privacy Policy (si estás en /privacy)
  - Button: "Contactar Privacy Team" → abre ContactDpoModalComponent
  - Back button: vuelve a la página anterior

- [ ] **C.1.4 — Mutación**
  - Quitá link "Política de Privacidad" del footer
  - Corré tests: `footer.spec.ts` → PrivacyPolicyLinkComponent debe fallar
  - Anotá test name
  - Restaurá

- [ ] **C.1.5** — Timestamp "Última actualización"
  - Leer de environment.ts: `PRIVACY_POLICY_VERSION_DATE = "2026-11-05"`
  - Mostrar en footer: "Última actualización: 5 de noviembre de 2026"
  - Localizable (fecha en español/inglés según idioma)

- [ ] **C.1.5 — Mutación**
  - Ponél date a hardcoded "2020-01-01"
  - Corré snapshot test: `privacy-policy.spec.ts` → snapshot debe fallar
  - Anotá test name
  - Restaurá a environment.ts

---

### Task 1.2 — Data Export Endpoint (Backend)

**Estimación:** 1.5 pts

**Guarantee:** GET /api/user/export-data retorna JSON completo, auditado, rate-limited

- [ ] **C.2.1** — Crear UserDataExportUseCase
  - Path: `backend/src/main/java/com/control/electoral/auth/usecases/UserDataExportUseCase.java`
  - Input: usuarioId (desde JWT actual)
  - Output: UserExportDTO con user, login_history, audit_events
  - Filtra: solo datos del usuario actual (seguridad)

- [ ] **C.2.1 — Mutación**
  - Quitá la línea `if (usuarioId != currentUser.getId()) throw exception`
  - Corré test: test debe permitir usuario A acceder datos usuario B
  - Anotá test: `[Tu nombre aquí]`
  - Restaurá validación

- [ ] **C.2.2** — UserExportDTO (structure)
  - Fields:
    ```java
    {
      user: {id, email, name, role, created_at, last_login},
      login_history: List<LoginHistoryDTO>,
      audit_events: List<AuditEventDTO>
    }
    ```
  - Timestamps en ISO 8601 UTC (segundos, no millis)
  - Jackson serialization: Z suffix para UTC

- [ ] **C.2.2 — Mutación**
  - Quitá `.withZone(ZoneId.of("UTC"))` de timestamp formatter
  - Corré test: timestamp validation debe fallar
  - Anotá test name
  - Restaurá

- [ ] **C.2.3** — Rate limiting (1 export / 24h per user)
  - Usar Redis: key = `export-data:${usuarioId}:${date}`
  - Si key existe → rechaza con 429
  - TTL: 86400 segundos (24h)
  - Implementar via @RateLimitingMethod o custom @Aspect

- [ ] **C.2.3 — Mutación**
  - Quitá Redis key check
  - Haz 2 requests rápido (< 1s apart)
  - Corré test: ambas deben exitosas (debe fallar porque rate limit gone)
  - Anotá test name
  - Restaurá

- [ ] **C.2.4** — Auditoría (log cada descarga)
  - Crear AuditLogEntry en invitation_audit:
    - usuario_id: current user
    - accion: "DATA_EXPORT"
    - evento: "SUCCESS"
    - datos: JSONB con {ip, file_size_kb, timestamp}
  - @Audited annotation o AfterCompletion advice

- [ ] **C.2.4 — Mutación**
  - Quitá la línea de auditoría logging
  - Corré test: `data-export-audit.spec.ts` debe fallar
  - Query: `SELECT COUNT(*) FROM invitation_audit WHERE accion = 'DATA_EXPORT'` debe ser 0
  - Anotá test name
  - Restaurá

- [ ] **C.2.5** — Endpoint REST
  - GET /api/user/export-data
  - Response: 200 OK, Content-Type: application/json
  - Header: Content-Disposition: attachment; filename="data-[userId]-[timestamp].json"
  - Authorization: requiere JWT válido
  - Max response: < 5MB (o comprimido)

- [ ] **C.2.5 — Mutación**
  - Quitá Authorization check (permitir sin JWT)
  - Corré test: endpoint debe rejetar sin token
  - Anotá test name
  - Restaurá

---

### Task 1.3 — Contact DPO Modal Component (Frontend)

**Estimación:** 0.5 pts

**Guarantee:** Modal form valida y envía email a DPO, rate-limited

- [ ] **C.3.1** — ContactDpoModalComponent
  - Path: `frontend/src/app/legal/contact-dpo-modal/contact-dpo-modal.component.ts`
  - Fields: email (prefilleado si autenticado), asunto, mensaje
  - Validaciones: email válido, asunto required, mensaje >= 20 chars
  - Botones: Enviar, Cancelar

- [ ] **C.3.1 — Mutación**
  - Quitá validator: `Validators.required` en asunto
  - Corré test: form debe permitir asunto vacío (debe fallar porque validator gone)
  - Anotá test name
  - Restaurá

- [ ] **C.3.2** — Backend: POST /api/legal/contact-dpo
  - Input: {email, asunto, mensaje}
  - Validación backend: email válido, mensaje >= 20
  - Envía email a dpo@control.ttcs vía EmailService
  - Audita en invitation_audit
  - Rate limiting: 30 segundos entre requests por IP

- [ ] **C.3.2 — Mutación**
  - Quitá rate limiting check
  - Haz 3 requests rápido (< 1s)
  - Corré test: tercera request debe rechazarse (debe fallar)
  - Anotá test name
  - Restaurá

---

### Task 1.4 — Privacy Policy Assets (Markdown)

**Estimación:** 0.5 pts

**Guarantee:** Markdown files ES + EN con contenido completo, auditable

- [ ] **C.4.1** — Crear privacy-policy-es.md
  - Path: `frontend/src/assets/legal/privacy-policy-es.md`
  - Secciones:
    - Intro (quiénes somos, dato de contacto)
    - Qué datos recopilamos (7-10 tipos)
    - Por qué recopilamos (propósitos)
    - Tabla de retención (ref a JSON)
    - Tus derechos (GDPR + CCPA + LGPD)
    - Contacto DPO
    - Fecha última actualización
  - ~1200-1500 palabras
  - Lenguaje claro (no legalese solo)

- [ ] **C.4.1 — Mutación**
  - Quitá sección "Tus Derechos" de markdown
  - Corré test: markdown parser debe fallar
  - Anotá test name
  - Restaurá

- [ ] **C.4.2** — Crear privacy-policy-en.md
  - Traducción de es.md
  - Misma estructura
  - Inglés claro, formal

- [ ] **C.4.2 — Mutación**
  - Quitá 1 párrafo de en.md
  - Corré test: line count debe cambiar
  - Anotá test name
  - Restaurá

- [ ] **C.4.3** — Retention table JSON
  - Path: `frontend/src/assets/legal/retention-table.json`
  - Schema:
    ```json
    [
      {
        "data_type": "Email, Nombre, Rol",
        "purpose": "Autenticación, identificación",
        "retention": "Mientras esté activo",
        "deletion": "MASTER delete o soft-delete"
      },
      ...
    ]
    ```
  - Mínimo 5 tipos de dato

- [ ] **C.4.3 — Mutación**
  - Quitá 1 row de JSON
  - Corré test: tabla debe mostrar 5 rows, ahora tiene 4 (debe fallar)
  - Anotá test name
  - Restaurá

---

## Qué NO hacer en esta fase

- ❌ Cookie banner (banner de consentimiento) → separado en T-COMPLIANCE-07
- ❌ Admin panel para editar Privacy Policy (estático MVP)
- ❌ 2FA o extra autenticación en export-data
- ❌ Anonimización de datos (futuro)
- ❌ Integración con DPO externo (manual process)
- ❌ Traducción a idiomas adicionales (solo ES + EN MVP)
- ❌ Exportar a PDF (JSON es suficiente para GDPR portability)

## Compuertas antes de dar por terminada la fase

```bash
# 1. Frontend builds sin errores
npm run build --prod

# 2. Tests pasan (todos)
npm run test -- --code-coverage

# 3. Coverage >= 80% en legal/*
# Expected: src/app/legal/privacy-policy/**/*.ts
# Expected: src/app/legal/contact-dpo-modal/**/*.ts

# 4. Backend builds
rtk cargo build

# 5. Backend tests pasan
rtk cargo test

# 6. API endpoint responde (local dev)
curl -H "Authorization: Bearer <JWT>" http://localhost:8080/api/user/export-data

# 7. Snapshot tests (privacy page)
npm run test -- --updateSnapshot  # SOLO si cambios intencionales

# 8. Accesibilidad (a11y)
npm run a11y-check src/app/legal/

# 9. Validar markdown parseable
python3 -m markdown frontend/src/assets/legal/privacy-policy-*.md

# 10. DPO email en environment.ts
grep -r "dpo@control.ttcs" frontend/src/environments/

# 11. No hay PII en logs
grep -r "export-data" backend/logs/ → verifica no contienen JSON

# 12. Rate limiting funcionando (Redis)
redis-cli keys "export-data:*" → debe haber entries después de test
```

---

## Apply Progress

- [ ] Task 1.1: Builder name: ________
- [ ] Task 1.2: Builder name: ________
- [ ] Task 1.3: Builder name: ________
- [ ] Task 1.4: Builder name: ________

**Mutations verified:**
- [ ] C.1.1: Test name: ________
- [ ] C.1.2: Test name: ________
- [ ] C.1.3: Test name: ________
- [ ] C.1.4: Test name: ________
- [ ] C.1.5: Test name: ________
- [ ] C.2.1: Test name: ________
- [ ] C.2.2: Test name: ________
- [ ] C.2.3: Test name: ________
- [ ] C.2.4: Test name: ________
- [ ] C.2.5: Test name: ________
- [ ] C.3.1: Test name: ________
- [ ] C.3.2: Test name: ________
- [ ] C.4.1: Test name: ________
- [ ] C.4.2: Test name: ________
- [ ] C.4.3: Test name: ________

**Last Updated:** 2026-11-05
