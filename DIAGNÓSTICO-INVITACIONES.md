# Diagnóstico: Invitaciones no se envían

## 🔴 Síntomas

- POST `/api/v1/users/invite` responde 200 pero email no llega
- `user_invitations` table tiene registros pero sin `invited_at`
- Resend dashboard muestra 0 requests

## ✅ Checklist: Configuración

### 1. Variables de entorno

```bash
# Terminal
cat .env | grep -i "email\|resend"

# Esperado (con tus valores reales):
# SMTP_HOST=smtp.resend.com
# SMTP_PORT=587
# SMTP_USERNAME=resend
# SMTP_PASSWORD=<tu-api-key>
# RESEND_API_KEY=<tu-api-key>
# MAIL_FROM=controlsoporte@mail.twintailcs.xyz
```

**Si falta algo:**
```bash
# Edita .env
nano .env

# Recarga
export $(cat .env | xargs)
```

### 2. Provider activo

```bash
# ¿Cuál provider está configurado?
export $(cat .env | xargs)
echo "Provider actual: $APP_EMAIL_PROVIDER"

# Debe ser "resend" o "smtp"
# Default en application.properties: "smtp"
```

**Si está en smtp y quieres Resend:**
```bash
export APP_EMAIL_PROVIDER=resend
# O en .env: APP_EMAIL_PROVIDER=resend
```

### 3. Queue de invitaciones

La cola **vive en memoria del proceso**:
- Encola → worker thread → retry backoff → Resend API
- Si el proceso muere = emails se pierden

**Ver cola pendiente (en logs):**
```bash
# Terminal 1: start backend
cd backend/electoral-api-quarkus
cargo run

# Terminal 2: check logs
tail -f target/app.log | grep -i "invitacion\|email"
```

---

## 🧪 Test rápido (5 min)

### Opción A: Mock local (sin Resend)

```bash
cd /home/andy/Documentos/PROYECTOS/CONTROL/Control-s-Elector

export $(cat .env | xargs)
export APP_EMAIL_PROVIDER=smtp

# Test: invitación → RecordingEmailService captura en memoria
rtk cargo test -p electoral-api-quarkus UserInvitationIT::elEmailSaleAlCorreoDeContactoYLlevaElLogin -- --nocapture
```

**Esperado:**
```
✓ email.destinatario() = <test-email>
✓ email.username() = jua..@control.ttcs
✓ email.activationLink() contains /auth/activate?token=
```

**Si falla:**
- Revisa logs: `... FAILED ...` explica por qué
- Stack trace: ¿RuntimeException en ResendEmailProvider? ¿Config inválida?

---

### Opción B: Real Resend (con tu API key)

```bash
export $(cat .env | xargs)
export APP_EMAIL_PROVIDER=resend

# Verifica que la key está cargada
echo "RESEND_API_KEY=${RESEND_API_KEY:0:10}..." 

# Test
rtk cargo test -p electoral-api-quarkus UserInvitationIT::elEmailSaleAlCorreoDeContactoYLlevaElLogin -- --nocapture
```

**Esperado:**
```
✓ HTTP POST a api.resend.com/emails
✓ Status 200
✓ Email en Resend dashboard dentro de 10s
```

**Si status ≠ 200:**
- 401: `RESEND_API_KEY` inválida o vacía
- 429: Rate limit excedido (espera 60s)
- 500: Error interno de Resend

---

## 🔧 Verificación en BD

Luego de un test, consulta:

```sql
-- ¿Se creó la invitación?
SELECT 
  id, email_personal, status, invited_at, 
  expiry_at, ip_address, invited_by
FROM user_invitations
ORDER BY id DESC
LIMIT 5;

-- ¿Se registraron fallos?
SELECT 
  id, usuario_id, email, reason, created_at
FROM invitation_audit
WHERE reason NOT LIKE '%éxito%'
ORDER BY id DESC
LIMIT 5;

-- ¿El usuario existe?
SELECT 
  id, email, email_personal, estado_usuario, 
  habilitado, rol_id
FROM usuario
WHERE email_personal LIKE '%test%'
ORDER BY id DESC
LIMIT 1;
```

---

## 📊 Logs útiles

### Con DEBUG de email

```bash
export APP_LOG_LEVEL=DEBUG
export QUARKUS_LOG_CATEGORY_EC_TWINTAILCS_ELECTORAL=DEBUG

rtk cargo test -p electoral-api-quarkus UserInvitationIT::elEmailSaleAlCorreoDeContactoYLlevaElLogin \
  -- --nocapture 2>&1 | tee test.log

# Busca:
grep -i "resend\|email\|invita" test.log
```

### Líneas clave de logs

| Log | Significa |
|-----|-----------|
| `Resend email sent successfully to ...` | ✓ Envío OK |
| `Failed to send email via Resend: 401 Unauthorized` | ❌ API key inválida |
| `Cola de invitaciones llena` | ❌ Queue capacity excedido |
| `Falló el envío del email ... (intento 1 de 3)` | ⚠ Reintentando (backoff: 1s, 2s) |

---

## 🚀 Flujo completo: desde cero

```bash
#!/bin/bash

cd /home/andy/Documentos/PROYECTOS/CONTROL/Control-s-Elector

# 1. Carga vars
export $(cat .env | xargs)

# 2. Verifica provider
echo "Provider: $APP_EMAIL_PROVIDER"

# 3. TEST (elige uno)

# OPCIÓN A: Mock (rápido)
echo "→ TEST CON MOCK"
export APP_EMAIL_PROVIDER=smtp
rtk cargo test -p electoral-api-quarkus \
  UserInvitationIT::elEmailSaleAlCorreoDeContactoYLlevaElLogin \
  -- --nocapture

# OPCIÓN B: Real Resend (con tu key)
# echo "→ TEST CON RESEND REAL"
# export APP_EMAIL_PROVIDER=resend
# rtk cargo test -p electoral-api-quarkus \
#   UserInvitationIT::elEmailSaleAlCorreoDeContactoYLlevaElLogin \
#   -- --nocapture
```

---

## 🎯 Próximos pasos

| Si... | Haz... |
|-------|--------|
| Mock test pasa | Resend está integrado. Llena RESEND_API_KEY en .env |
| Mock test falla | Revisa logs de ResendEmailProvider. ¿Config cargada? |
| Resend test timeout | Queue worker no procesa. ¿Thread vivo? Ver app logs |
| Resend responde 401 | API key vacía o inválida. Verifica .env |
| Email no llega en 10s | Resend encolado pero backend sin respuesta. Revisa dashboard Resend |

---

## 📞 Resend Dashboard

https://resend.com/dashboard

**Para verificar:**
- Activity tab → últimos requests
- Emails tab → inbox / bounce / spam
- API key → está en Settings

**Si no ves requests:**
- Backend no llamó a Resend (revisar APP_EMAIL_PROVIDER)
- O llamó pero 401/5xx (revisar logs)

