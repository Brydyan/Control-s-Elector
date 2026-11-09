# T-COMPLIANCE-04: Design — Delete Account

**Date:** 2026-11-05

---

## D1 — Eliminación: Soft-delete vs Hard-delete inmediato

**Decisión: Soft-delete (30d grace) → Hard-delete automático**

```
Flow:
  1. Usuario pide "Eliminar Cuenta"
  2. Email de confirmación: "Se eliminará en 30 días"
  3. Puede clickear "Cancelar eliminación" en email (7 días)
  4. Después 30 días → cron job hard-delete

Estados:
  - ACTIVO → DELETED_PENDING (soft-delete)
  - DELETED_PENDING → DELETED_PERMANENT (hard-delete, T+30d)

Ventajas:
  ✓ Recuperable si cambió de opinión
  ✓ Cumple GDPR (plazo razonable)
  ✓ Protección: accidente no borra todo inmediato
```

**Alternativa descartada — Hard-delete inmediato**

```
Usuario pide → inmediatamente DELETE FROM usuario

Desventajas:
  ❌ Irreversible: no hay "oops, recuperar"
  ❌ UX: usuario presiona botón sin saber qué pasa
  ❌ Accidentes permanentes
  ❌ GDPR: "reasonable time" puede ser disputado
```

---

## D2 — Excepciones: Qué datos se retienen post-delete

**Decisión: Actas electorales (7 años por ley) + auditoría resumida**

```
DELETE usuario.* EXCEPT:
  - Referencia en acta_electoral (user_id, vote_recorded_at)
    Retención: 7 años por requerimiento legal
  - Auditoría resumida: {user_id, action, timestamp, ONLY} (sin detalles)
    Retención: 2 años para cumplimiento

Datos PERMANENTEMENTE BORRADOS:
  - password_hash
  - email
  - name
  - login_history
  - 2FA codes
  - invitation_token
```

---

## D3 — Confirmación: 1-click vs Email confirmation vs 2-step

**Decisión: Email confirmation + 7 días cancel window**

```
1. Usuario en Settings → "Eliminar Cuenta"
2. Modal: "¿Seguro? Se eliminará en 30 días. Abriremos mail de confirmación."
3. Email enviado: "Tu cuenta se eliminará en 30 días"
4. Link en email: "Cancelar eliminación" (valid 7 días)
5. Si no cancela → T+30d hard-delete

Ventajas:
  ✓ Extra confirmación (email)
  ✓ Recovery window (7 días)
  ✓ GDPR: demostrable consentimiento
```

---

## D4 — Auditoría: Qué se registra

**Decisión: deletion_audit table con full snapshot**

```
CREATE TABLE deletion_audit (
  id BIGSERIAL PRIMARY KEY,
  usuario_id BIGINT,
  email_at_deletion VARCHAR (snapshot),
  name_at_deletion VARCHAR (snapshot),
  deleted_at TIMESTAMPTZ,
  deletion_reason VARCHAR(64), -- "user_request"
  recovery_deadline TIMESTAMPTZ (T+30d),
  was_recovered BOOLEAN DEFAULT false,
  recovered_at TIMESTAMPTZ (if applicable),
  hard_deleted_at TIMESTAMPTZ (if hard-delete completed),
  created_at TIMESTAMPTZ
);
```

---

**Next:** specs, tasks  
**Last Updated:** 2026-11-05
