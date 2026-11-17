# Specs: T-COMPLIANCE-04 — Delete Account

**Last Updated:** 2026-11-05

---

## Feature: Delete Account

```gherkin
Feature: Usuario puede solicitar eliminación de cuenta

  Scenario: Delete button en settings
    GIVEN usuario autenticado
    WHEN navega a Settings → Seguridad
    THEN ve botón: "Eliminar Cuenta Permanentemente"
    AND botón en rojo (dangerous action)

  Scenario: Confirmación modal con countdown
    GIVEN usuario clickea "Eliminar Cuenta"
    WHEN modal abre
    THEN ve:
      - "Tu cuenta se eliminará en 30 días"
      - "Recibirás email de confirmación"
      - Botón "Confirmar Eliminación"
      - Botón "Cancelar"
    AND modal es dark (serious intent)

  Scenario: Confirmación email enviado
    GIVEN usuario confirma eliminación
    WHEN POST /api/user/request-deletion completado
    THEN email enviado con:
      - "Tu cuenta: juan@control.ttcs"
      - "Se eliminará: [fecha T+30d]"
      - Link "Cancelar eliminación" (expira 7 días)
      - Link a privacy policy
    AND respuesta: {status: "deletion_pending", deadline: "2026-12-05"}

  Scenario: Usuario puede cancelar eliminación (7 días)
    GIVEN usuario tiene 7 días desde request
    WHEN clickea link "Cancelar eliminación" en email
    THEN estado vuelve a ACTIVO
    AND email confirmación: "Eliminación cancelada"
    AND deletion_audit registra: was_recovered = true

  Scenario: No puede cancelar después 7 días
    GIVEN 8 días pasaron desde request
    WHEN intenta clickear "Cancelar"
    THEN link expirado
    AND muestra: "Link expirado. Contacta soporte."

  Scenario: Hard-delete automático T+30d
    GIVEN cuenta en estado DELETED_PENDING
    AND han pasado 30 días
    WHEN cron job `delete-account-job` corre
    THEN hard-delete:
      - DELETE FROM usuario WHERE id = X
      - Excepto: referencia en acta_electoral
      - deletion_audit.hard_deleted_at = NOW()
    AND no recovery posible

  Scenario: Datos "orphaned" en acta_electoral
    GIVEN usuario Juan (id: 123) votó en acta #5
    AND acta tiene: {voter_id: 123, vote_recorded: TRUE}
    WHEN usuario solicita delete
    THEN acta sigue existiendo:
      - voter_id = 123 (no deleted)
      - Razón: legal retención (7 años)
    AND en acta no hay nombre/email Juan (anónimo)

  Scenario: Auditoría de eliminación
    GIVEN usuario eliminación completada
    THEN deletion_audit row:
      - usuario_id: 123
      - email_at_deletion: "juan@control.ttcs" (snapshot)
      - deleted_at: NOW()
      - recovery_deadline: T+7d
      - hard_deleted_at: T+30d

  Scenario: Login rechazado post-deletion
    GIVEN usuario en estado DELETED_PENDING
    WHEN intenta login con juan@control.ttcs
    THEN rechazado: 403 "Cuenta marcada para eliminación"
    AND recomendación: "Clickea link en email para recuperar"

  Scenario: No puedo hacer nada post-deletion
    GIVEN usuario DELETED_PENDING
    WHEN intenta:
      - GET /api/user/profile
      - PUT /api/user/update-profile
      - POST /api/something
    THEN todas rechazadas: 403 "Account marked for deletion"
```

---

**Total Scenarios:** 10  
**Last Updated:** 2026-11-05
