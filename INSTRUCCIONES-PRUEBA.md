# 🚀 Pruebas de Invitaciones: Instrucciones Paso a Paso

## 1️⃣ Preparación (5 min)

### A. Verifica tu `.env`

```bash
cd /home/andy/Documentos/PROYECTOS/CONTROL/Control-s-Elector

# Verifica que tiene credenciales
cat .env | grep -i "email\|resend"
```

**Esperado:**
```
SMTP_HOST=smtp.resend.com
SMTP_PORT=587
SMTP_USERNAME=resend
SMTP_PASSWORD=<tu-api-key>
RESEND_API_KEY=<tu-api-key>
MAIL_FROM=controlsoporte@mail.twintailcs.xyz
```

### B. Carga variables en la terminal actual

```bash
export $(cat .env | xargs)

# Verifica que cargó
echo "RESEND_API_KEY=${RESEND_API_KEY:0:10}..."
echo "SMTP_HOST=$SMTP_HOST"
```

---

## 2️⃣ Ejecuta script automático (3 min)

```bash
bash test-invitations.sh
```

**Qué hace:**
- TEST 1: Invitación con mock local (RecordingEmailService)
- TEST 2: Invitación real con Resend (si RESEND_API_KEY está llena)
- TEST 3: Reenvío de invitación

**Esperado:**
```
✅ TODOS LOS TESTS COMPLETADOS
```

---

## 3️⃣ Diagnóstico (si TEST 1 ó TEST 2 fallan)

### A. Ver logs detallados

```bash
export $(cat .env | xargs)
export APP_LOG_LEVEL=DEBUG
export APP_EMAIL_PROVIDER=resend  # o "smtp"

rtk cargo test -p electoral-api-quarkus UserInvitationIT::diagnosticoResendConFallback \
  -- --nocapture 2>&1 | tee test-diag.log

# Busca errores
grep -i "error\|failed\|401\|resend" test-diag.log
```

### B. Revisa BD directamente

```bash
# Conecta a PostgreSQL local
psql electoral_db electoral_user

-- ¿Se creó la invitación?
SELECT email_personal, status, invited_at, ip_address 
FROM user_invitations 
ORDER BY id DESC 
LIMIT 3;

-- ¿Hay fallos registrados?
SELECT usuario_id, email, reason 
FROM invitation_audit 
WHERE reason NOT LIKE '%éxito%' 
ORDER BY id DESC 
LIMIT 5;

-- ¿El usuario existe?
SELECT email, email_personal, estado_usuario 
FROM usuario 
WHERE email_personal LIKE '%diag%' 
ORDER BY id DESC 
LIMIT 1;
```

### C. Dashboard Resend

Si usas Resend real:

1. Abre: https://resend.com/dashboard
2. Ve a **Activity** tab
3. Busca últimos requests (últimos 10 min)
4. Si ves `401 Unauthorized` → RESEND_API_KEY inválida
5. Si ves `200 OK` → Email encolado en Resend

---

## 4️⃣ Manual: Paso a paso

Si prefieres correr cada test individualmente:

### Test 1: Mock local (sin Resend)

```bash
export $(cat .env | xargs)
export APP_EMAIL_PROVIDER=smtp

rtk cargo test -p electoral-api-quarkus \
  UserInvitationIT::elEmailSaleAlCorreoDeContactoYLlevaElLogin \
  -- --nocapture
```

**Qué verás:**
```
✓ email.destinatario() = ...@example.com
✓ email.username() = jua..@control.ttcs
✓ email.activationLink() contains /auth/activate?token=
```

### Test 2: Real Resend

```bash
export $(cat .env | xargs)
export APP_EMAIL_PROVIDER=resend

# Verifica que API key está llena
if [ -z "$RESEND_API_KEY" ]; then
  echo "❌ RESEND_API_KEY está vacía en .env"
  exit 1
fi

rtk cargo test -p electoral-api-quarkus \
  UserInvitationIT::elEmailSaleAlCorreoDeContactoYLlevaElLogin \
  -- --nocapture
```

### Test 3: Diagnóstico completo

```bash
export $(cat .env | xargs)
export APP_LOG_LEVEL=DEBUG

rtk cargo test -p electoral-api-quarkus \
  UserInvitationIT::diagnosticoResendConFallback \
  -- --nocapture 2>&1 | tee diag.log

# Revisa output
grep "✓\|❌\|ERROR" diag.log
```

---

## 5️⃣ Troubleshooting

| Síntoma | Causa | Solución |
|---------|-------|----------|
| TEST 1 falla con `RuntimeException` | Config no cargada | `export $(cat .env \| xargs)` |
| TEST 2 responde `401` | RESEND_API_KEY inválida | Verifica en Resend dashboard Settings |
| TEST 2 timeout (10s) | Resend API no responde | Espera 1 min, reintenta. Resend puede estar caído |
| `invitation_audit` muestra `cola_llena` | Queue capacity excedido | Reduce otros tests concurrentes |
| Email llega al SPAM | DNS CNAME no propagado | Resend dashboard: Settings → Domains |
| SQL: table "invitation_audit" does not exist | BD no migrada | `cargo run -p electoral-api-quarkus` primero |

---

## 6️⃣ Resumen: 3 comandos clave

```bash
# 1. Prep
export $(cat .env | xargs)

# 2. Test rápido (mock)
export APP_EMAIL_PROVIDER=smtp && \
rtk cargo test -p electoral-api-quarkus UserInvitationIT::elEmailSaleAlCorreoDeContactoYLlevaElLogin

# 3. Test real (si RESEND_API_KEY está llena)
export APP_EMAIL_PROVIDER=resend && \
rtk cargo test -p electoral-api-quarkus UserInvitationIT::diagnosticoResendConFallback
```

---

## 📊 Qué esperar

| Test | Tiempo | Resultado |
|------|--------|-----------|
| Mock (TEST 1) | 10-15s | `✓ PASSED` (RecordingEmailService captura email) |
| Real Resend (TEST 2) | 5-10s | `✓ PASSED` (si API key válida) ó `❌ FAILED 401` |
| Diagnóstico (TEST 3) | 15-20s | Muestra: invitación creada + BD + logs |

---

## 🎯 Próximo paso

Una vez que TEST 1 ✓ pase:
1. Llena `RESEND_API_KEY` en `.env` con tu clave real
2. Verifica DNS CNAME: `send.mail → send.forge.rmta.net`
3. Re-ejecuta TEST 2
4. Revisa Resend dashboard → Activity

¿Listo? ¡Ejecuta!

```bash
bash test-invitations.sh
```

