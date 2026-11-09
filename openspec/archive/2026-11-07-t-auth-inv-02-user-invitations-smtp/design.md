# Design — T-AUTH-INV-02: User Invitations + SMTP

**Date:** 2026-11-07

---

## D1 — Username Generation Strategy

**Decision:** Deterministic format: `{3name}{3lastname}{3role}@control.ttcs`

**Example:**
- Nombre: Juan, Apellido: García, Rol: OBSERVER
- Username: **juagar.obs@control.ttcs**

**Algorithm:**
```java
String first3Name = nombre.substring(0, Math.min(3, nombre.length())).toLowerCase();
String first3Last = apellido.substring(0, Math.min(3, apellido.length())).toLowerCase();
String first3Role = rol.substring(0, Math.min(3, rol.length())).toLowerCase();
String username = first3Name + first3Last + "." + first3Role + "@control.ttcs";
```

**Why:** 
- Deterministic (no UUIDs)
- Human-readable (easy to remember)
- Collision prevention: unique index on DB
- Secure: no sensitive info in username

**Alternative descartada — UUID:**
- Would be unguessable, but unmemorable
- Users need to copy from email anyway
- Adds complexity

---

## D2 — Temporary Password Generation

**Decision:** 12 characters, 4 entropy classes (upper+lower+digit+special)

**Format:**
```
Upper: A-Z (2 chars)
Lower: a-z (3 chars)
Digit: 0-9 (3 chars)
Special: !@#$%^&* (4 chars)
```

**Example:** `Kh7@mP9#qLx4`

**Algorithm:**
```java
SecureRandom random = new SecureRandom();
String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*";
StringBuilder sb = new StringBuilder();
for (int i = 0; i < 12; i++) {
  sb.append(chars.charAt(random.nextInt(chars.length())));
}
```

**Why:**
- 12 chars = ~79 bits entropy (sufficient for temp password)
- Force user to change on first login
- Never stored in plaintext (hash immediately)

**Alternative descartada — Passphrase:**
- Longer, harder to type in email
- Less entropy per character
- Users forget easier

---

## D3 — SMTP Template & Localization

**Decision:** HTML email template, ES only (EN later)

**Template Structure:**
```html
<h1>Bienvenido a Control Electoral</h1>
<p>Tu cuenta ha sido creada exitosamente.</p>

<h2>Credenciales Temporales:</h2>
<ul>
  <li><strong>Usuario:</strong> {{username}}</li>
  <li><strong>Contraseña:</strong> {{temporaryPassword}}</li>
</ul>

<h3>Próximos pasos:</h3>
<ol>
  <li>Inicia sesión en https://control.twintailcs.xyz</li>
  <li>Cambia tu contraseña en "Mi Perfil" → "Seguridad"</li>
  <li>Guarda tus nuevas credenciales en lugar seguro</li>
</ol>

<p><strong>Nota:</strong> Esta contraseña expira en 30 días.</p>
<footer>Control Electoral CNE | 2026</footer>
```

**Why:**
- HTML renders well on all email clients
- Brand colors + logo
- Clear CTA (login link)
- Disclaimer (temp password expiry)

**Alternative descartada — Plain text:**
- Boring, doesn't look professional
- No branding

---

## D4 — Activation Token (if email verification needed)

**Decision:** Delay email verification to separate SDD (T-AUTH-03)

**For now:** User is active immediately after invite. No email verification required.

**Why:**
- MASTER already trusts email (entered manually)
- Simplifies scope of T-AUTH-INV-02
- Verification can be added later as post-login requirement

---

## D5 — Rate Limiting

**Decision:** Per-IP, 1 invite per 5 seconds (prevents spam)

**Implementation:**
```java
@RateLimit(requests = 1, windowSec = 5, identifier = "ip")
public Response inviteUser(CreateUserRequest req, @Context HttpServletRequest http)
```

**Why:**
- Prevents bulk automated user creation
- 1/5s = 12 per minute (reasonable for manual)
- IP-based (accounts for proxies)

---

## D6 — Auditoría & Logging

**Decision:** Table `user_invitations` + audit_log (existing)

**Columns:**
```sql
user_invitations (
  id BIGINT PRIMARY KEY,
  usuario_id BIGINT FK,
  email_personal VARCHAR(255),
  invited_at TIMESTAMP,
  invited_by BIGINT FK (usuario_id de MASTER),
  status VARCHAR(20),  -- 'INVITED', 'ACTIVATED', 'EXPIRED'
  ip_address VARCHAR(45),
  user_agent VARCHAR(500),
  activated_at TIMESTAMP NULL,
  expiry_at TIMESTAMP
)
```

**Also log in `audit_log`:**
- Action: USER_INVITED
- Usuario: invited user
- Metadata: email_personal, invited_by

**Why:**
- Full audit trail (when, who, from where)
- Can query "who invited this user?"
- Can check if invitation expired

---

**Status:** Design complete, ready for tasks.md

