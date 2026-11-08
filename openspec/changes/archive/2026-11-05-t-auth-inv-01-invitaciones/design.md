# T-AUTH-INV-01: Design — Sistema de Invitaciones por Email

**Date:** 2026-11-05  
**Author:** Control Electoral Team  
**Status:** Design (decisiones + alternativas)  
**Depends on:** proposal.md  
**Next:** specs/auth/invitaciones.spec.md, tasks.md

---

## D1 — Token de activación: JWT con 24h expiry vs Token stateful en BD

**Decisión: JWT con 24h expiry**

```
Clave pública: JWT signature verificable sin DB
Token contiene: {sub: usuarioId, exp: now+24h, iat: now}
Ventajas:
  ✓ Stateless: escalable sin sesión en server
  ✓ Auto-cleanup: expira solo, no necesita limpiar BD
  ✓ Descentralizado: frontend valida localmente (quick fail)
  ✓ Mobile-friendly: no depende de session storage
```

**Alternativa descartada — Token stateful en DB**

```
Guardar token como UUID en tabla usuario.activation_token
Ventajas teóricas:
  - Revocable inmediatamente
  - Puedo trackear "cuántos intentos de validación"
Desventajas concretas:
  ❌ Requiere lookup BD en cada validación (latencia)
  ❌ Requiere cleanup periódico (cron job, complejidad)
  ❌ No escala bien con 500 users × 24h expirations
  ❌ En BD distribuida, genera hotspot en tabla usuario
  ❌ Si BD down, tokens válidos no funcionan

Descartada porque: JWT escalable, stateless, y 24h es suficiente para "cambiar de opinión"
```

**Nota de implementación — la validación es stateless**

La VALIDACIÓN del token no consulta la base: no hay lookup del usuario, ni de la fila, ni de
ninguna tabla. Se verifica firma y expiración contra la clave configurada
(`app.jwt.secret`), que es un secreto simétrico de la aplicación, no una clave pública embebida
ni un caché externo.

```
Validar token de activación:
  1. Verificar firma HMAC-SHA256 con app.jwt.secret     ← sin BD
  2. Verificar issuer                                        ← sin BD
  3. Verificar exp > NOW                                     ← sin BD
  4. Verificar typ == "activation"                           ← sin BD
  5. Extraer sub = publicId (UUID)                           ← sin BD
```

La consulta a la base aparece después, y es por necesidad del negocio, no del token: recién al
**activar** hay que leer el usuario para cambiarle la contraseña y consumir el token.

Esto mantiene la garantía de D1: escalable sin session store, y sin que un token válido deje de
funcionar porque la base esté caída.

**Quién firma.** El token se firma con **Nimbus** (`SignedJWT`, HMAC-SHA256), el mismo mecanismo y
la misma clave que usa el `JwtService` del módulo para los access y refresh tokens. No se usa
`io.smallrye.jwt`: el módulo no tiene esa dependencia, y tener dos mecanismos de firma —con dos
claves— haría que un cambio de secreto rompiera una mitad del sistema sin que ninguna prueba lo
notara. Un solo firmante, una sola clave, un solo `typ` que separa los dominios
(`access` / `refresh` / `activation`).

---

## D2 — Password setup: Usuario elige vs Temporal generado

**Decisión: Usuario elige su password**

```
MASTER invita → Usuario recibe email → Usuario digita password
Ventajas:
  ✓ Usuario "propietario" de credencial desde día 1
  ✓ Evita copiar/pegar password temporal (surface menos segura)
  ✓ UX: usuario sabe su password de inmediato
  ✓ Auditoría: usuario responsable de su setup inicial
```

**Alternativa descartada — Password temporal generado**

```
MASTER invita → Genera password temporal random (A1b2!C3d) → Usuario recibe en email
Ventajas teóricas:
  - MASTER tiene record de password temporal
  - Garantiza complejidad (random generador)
Desventajas concretas:
  ❌ Password visible en email (surface amplia, archivo histórico)
  ❌ Usuario debe cambiar en primer login (friction)
  ❌ MASTER debe gestionar "cambiar password" flow (extra UI)
  ❌ Usuarios olvidan password temporal antes de loguear (soporte)
  ❌ Auditoría: quién copió el email del usuario? (riesgo)

Descartada porque: User-chosen password es más seguro, UX mejor, y auditoría clara.
```

**El token no se consume al abrir el link, sino al activar**

Abrir el link y mirar el formulario no gasta el token. El token se consume únicamente en el POST
de activación, y sólo si la activación termina bien.

Esto es lo que permite el flujo de "el usuario abre el email, se equivoca en la contraseña, cierra
el navegador y vuelve a intentarlo al día siguiente": el token sigue vigente hasta las 24 horas.
Al revés, consumirlo en el GET obligaría al usuario a pedir una invitación nueva por un error
de dedo.

---

## D3 — Email send: Async con retry vs Síncrono

**Decisión: Async con retry (3x exponential backoff)**

```
POST /invitaciones {userIds: [1,2,3,...500]}
  → Retorna inmediatamente {status: "processing", sentCount: 500}
  → Backend publica evento a queue
  → Consumer procesa: rate limit 100/min, retry en fallo (1s, 2s, 4s)
  
Ventajas:
  ✓ Admin UI no bloquea 5+ minutos (500 emails enviándose)
  ✓ Rate limiting natural (no spam SMTP server)
  ✓ Retry automático (usuario falló, reintentamos)
  ✓ Auditoría: cada intento logged
```

**Queue elegida: cola en memoria del proceso, con worker propio**

La decisión D3 era "async con retry"; faltaba concretar la tecnología, y la concreción tiene que
poder existir el día del despliegue. La opción elegida es una cola en memoria
(`LinkedBlockingQueue` acotada) drenada por un hilo daemon propio, con rate limit de 100/min por
ventana y reintentos con backoff exponencial (1 s, 2 s, 4 s).

```
POST /api/v1/auth/invitaciones {usuarioIds: [...]}
  → genera el token, guarda su hash y registra la auditoría
  → encola el email                          ← la respuesta es 202, no espera al SMTP
  → el worker drena la cola: envía, reintenta y audita cada intento
```

**Por qué no Redis Streams.** Redis no está en el stack: la task que lo incorporaría
(T-COMPLIANCE-06) no está implementada, y atar este change a otro sin cerrar lo convierte en un
bloqueo. Y la cuestión que Redis resolvería —que un email en vuelo se pierda si el proceso
muere— ya la resuelve D5: el reenvío es manual, de MASTER, y cada intento fallido queda
auditado. Si un reinicio pierde un email, MASTER lo ve en el panel y lo reenvía.

**Alternativas descartadas**

| Opción | Por qué no |
|---|---|
| Kafka | Overkill para un MVP con 500 usuarios. Un servicio más que desplegar y monitorear |
| RabbitMQ | Infraestructura extra para el mismo problema |
| Redis Streams | Depende de un change que no está implementado. Se puede sumar después: la cola es un puerto (`InvitationEmailQueue`) y cambiar de adaptador no toca la lógica |

**Alternativa descartada — Envío síncrono**

```
POST /invitaciones {userIds: [1,2,...500]}
  → Espera a que todos 500 emails se envíen
  → Retorna {sent: 500, failed: 0}

Desventajas concretas:
  ❌ Admin aguarda 5-10 minutos (timeout probable, HTTP 504)
  ❌ No hay retry automático (si falla #200, repite todo?)
  ❌ SMTP rate limits típicos: 100/min (envío sincrónico excede)
  ❌ Si 1 email slow, toda invitación ralentiza
  ❌ No escalable para 1000+ usuarios

Descartada porque: 500 users síncrono = UX terrible, timeout, sin resilience.
```

---

## D4 — Token consumption: One-time use vs Reutilizable

**Decisión: One-time use (consumir token después de usar)**

```
POST /activate {token: JWT, password: "..."}
  → Backend valida token
  → Backend: UPDATE usuario SET activation_token = NULL
  
Garantía: Si usuario intenta /activate con mismo token otra vez
  → Token check: "activation_token IS NULL"
  → Rechaza: "Token ya fue consumido"

Ventajas:
  ✓ Previene activación múltiple accidental
  ✓ Previene replay attack (token reutilizado por tercero)
  ✓ Auditoría: una línea de token = una activación
```

**Alternativa descartada — Token reutilizable (no consumir)**

```
POST /activate {token, password}
  → Backend valida token
  → Backend NO actualiza activation_token
  → Usuario puede /activate con mismo token N veces

Desventajas concretas:
  ❌ ¿Qué pasa en segundo /activate? ¿Overwrite password?
  ❌ Race condition: 2 requests simultáneos, ambos exiten
  ❌ Seguridad: tercero puede intentar explotar mismo token
  ❌ Auditoría confusa: ¿cuántas activaciones por token?

Descartada porque: One-time use es patrón de seguridad comprobado.
```

---

## D5 — Re-invitación: MASTER manual vs Auto-reenvío

**Decisión: MASTER re-envía manualmente desde admin panel**

```
Usuario no activa en 24h → Token expira
Usuario ve error: "Link expirado, contacta al administrador"
MASTER abre admin panel → Usuarios → Busca usuario → "Re-enviar Invitación"
  → Genera NUEVO token
  → Envía NUEVO email

Ventajas:
  ✓ MASTER tiene control (no envío masivo accidental)
  ✓ Auditoría clara: quién reenvió, cuándo
  ✓ Oportunidad de validar: ¿usuario sigue activo en el sistema?
  ✓ Simple (no auto-reenvío complejo)
```

**Alternativa descartada — Auto-reenvío a los 12h**

```
Usuario no activa en 24h:
  → A las 12h: Sistema automáticamente reenvía
  → A las 20h: Si sigue sin activar, reenvío #2
  → A las 24h: Token expira permanentemente

Desventajas concretas:
  ❌ Usuarios reciben 2-3 emails (spam perception)
  ❌ Complicada lógica de horarios (UTC, timezone)
  ❌ Auditoría: ¿quién decidió reenviar? (automático no da clarity)
  ❌ Si usuario dice "no quiero más emails", no hay forma de optout
  ❌ En votaciones reales, timing impredecible (no sabemos cuándo activa)

Descartada porque: MASTER control > auto-reenvío. Es elección consciente por admin.
```

**Nota de implementación (ronda 2 — Task 3.3)**

El panel de usuarios no tenía el estado de las cuentas: el DTO de administración no llevaba
`estado_usuario`, así que la pantalla no podía distinguir a quién se podía invitar. Ahora
`UsuarioResponseDto` lleva `estado_usuario` e `invitation_sent_at`:

- `estado_usuario` decide la casilla de selección y la acción "Re-enviar Invitación": solo
  aparecen en cuentas `PENDIENTE_INVITACION`.
- `invitation_sent_at` distingue "nunca se le invitó" de "se le invitó y no abrió el correo"; el
  backend usa la misma señal para elegir la plantilla de reenvío.

El envío en lote es **atómico**: si una sola cuenta del lote ya está activa, el backend responde
409 y no sale ningún email. Por eso la pantalla solo deja marcar cuentas pendientes. No es un
control de seguridad — el control real es `usuario:create` + MASTER en el backend — es lo que evita
que el operador mande un lote que ya va a ser rechazado.

---

## D6 — Estado del usuario: PENDIENTE_INVITACION → ACTIVO vs Otra transición

**Decisión: Estado explícito PENDIENTE_INVITACION**

```
Máquina de estados:
  CREADO → PENDIENTE_INVITACION (al crear desde admin)
           ↓ (al activar link)
           ACTIVO (puede loguear)

En LoginUseCase:
  if usuario.estado_usuario != ACTIVO:
    throw BusinessException("Usuario aún no ha activado su cuenta")
```

**Dónde lo verifica exactamente, y en qué orden**

El chequeo vive en `LoginUseCase.execute()`, **después** del de la contraseña, no antes:

```
LoginUseCase.execute(email, password, ip, userAgent):
  Usuario u = usuarioRepository.findByEmail(email)

  if u == null || !u.habilitado || !passwordHasher.matches(password, u.passwordHash):
    auditar LOGIN_FAIL
    throw AuthException("Credenciales invalidas")          ← 401

  if u.estadoUsuario != ACTIVO:
    auditar LOGIN_REJECTED("usuario_pendiente_activacion")
    throw AccesoNoPermitidoException(
      "Usuario aún no ha activado su cuenta. Revisa tu email.")  ← 403

  emitir JWT
```

**Por qué el estado se comprueba después de la contraseña.** Al revés, el mensaje "no activaste
tu cuenta" confirmaría que ese email existe, aunque la contraseña sea incorrecta: cualquiera
podría usar el login para enumerar las cuentas que están esperando activación. Con el orden de
arriba, el 403 solo lo recibe quien ya conoce la contraseña; para el resto la respuesta es la
misma de siempre.

El 403 (y no el 401) es lo que corresponde: las credenciales eran correctas, lo que falta es un
paso de activación. Es lo que el `spec.md` pide y lo que el frontend muestra como "revisá tu
email".

Ventajas:
  ✓ Explicit en código (no ambiguo)
  ✓ Query rápida: SELECT * FROM usuario WHERE estado_usuario = 'PENDIENTE_INVITACION'
  ✓ Admin puede filtrar: "Mostrar usuarios pendientes de activar"
  ✓ Auditoría: estado visible en BD

**Alternativa descartada — activation_token NOT NULL como "indicador"**

```
Sin campo estado_usuario.
Lógica de "está pendiente activación":
  if usuario.activation_token IS NOT NULL: → pendiente

Desventajas concretas:
  ❌ Frágil: ¿qué si activation_token = NULL por otra razón?
  ❌ Query compleja: WHERE activation_token IS NOT NULL (slow scan)
  ❌ Admin UI: no hay estado visible, confusión
  ❌ Auditoría: borrar token = cambio de estado implícito (hard to trace)
  ❌ Future: si qiero 2+ transiciones, no funciona

Descartada porque: Estado explícito es más robusto y auditable.
```

---

## D7 — Email service: Interfaz abstracta vs Implementación directa

**Decisión: Interfaz EmailService abstracta**

```
public interface EmailService {
  void sendInvitationEmail(String email, String name, String link, String token);
  void sendActivationConfirmation(String email, String name);
}

Implementaciones:
  - SmtpEmailProvider (SMTP directo, para dev/staging)
  - SendGridEmailProvider (SendGrid, para producción escalada)
  
En application.properties:
  app.email.provider=smtp  (o sendgrid)

Ventajas:
  ✓ Swappeable: puedo cambiar provider sin tocar código
  ✓ Testeable: mock fácil
  ✓ Evolutivo: agregar WhatsApp/SMS después
```

**Alternativa descartada — Implementación directa SMTP**

```
@Service
public class EmailService {
  @Inject
  Mailer mailer; // Quarkus mailer directo
  
  void sendInvitationEmail(...) {
    mailer.send(...);
  }
}

Desventajas concretas:
  ❌ Acoplado a Quarkus mailer
  ❌ Cambiar a SendGrid = refactor grande
  ❌ Test: difícil mockear SMTP
  ❌ No hay fallback a otro provider

Descartada porque: Abstracción costo mínimo, beneficio máximo.
```

---

## D8 — Password requirements: Nivel de complejidad

**Decisión: 8+ chars, 1 mayúscula, 1 número, 1 carácter especial**

```
const MIN_LENGTH = 8;
const HAS_UPPERCASE = /[A-Z]/;
const HAS_NUMBER = /[0-9]/;
const HAS_SPECIAL = /[!@#$%^&*]/;

Justificación:
  - 8+ chars: OWASP recommendation
  - 1 mayúscula: previene "password" (todo lowercase)
  - 1 número: previene "passwords" (solo letters)
  - 1 especial: previene common patterns

Ejemplos válidos: "Abc123!x", "Observer@2025", "My$Pass01"
Ejemplos inválidos: "password" (no mayús, no special), "Pass123" (no special)
```

**Alternativa descartada — Solo 8+ caracteres**

```
No requerir mayúscula, número, especial.
"password" sería válido.

Desventajas:
  ❌ Muy débil (fácil de crackear con wordlist)
  ❌ OWASP no recomienda
  ❌ Para votaciones, seguridad crítica

Descartada porque: Seguridad electoral no negotiable.
```

---

## D9 — Rate limiting de email: 100/min vs Otra velocidad

**Decisión: 100 emails/minuto máximo**

```
Límite: 100 emails / 60 segundos = 1.67 emails/seg

Cálculo:
  - SMTP típico: 100-300 req/min
  - Seguridad: 100 es conservador
  - 500 usuarios: 5 minutos de envío continuado
  - Evita que SMTP provider throttle/bloquee

En EmailQueueConsumer:
  if (emailsSentThisMinute >= 100):
    sleep(60000)
```

**Alternativa descartada — Sin límite (fire-and-forget)**

```
Enviar todos 500 emails tan rápido como SMTP permita.

Desventajas:
  ❌ SMTP server rechaza (throttling)
  ❌ Algunos emails no llegan
  ❌ Spike anormal en logs del provider
  ❌ Risk: IP flagged como spam

Descartada porque: Rate limiting es defensive best practice.
```

---

## D10 — Auditoría: Tabla separada invitation_audit vs Agregar columnas a usuario

**Decisión: Tabla separada invitation_audit**

```
CREATE TABLE invitation_audit (
  id BIGSERIAL PRIMARY KEY,
  usuario_id BIGINT REFERENCES usuario(id),
  accion VARCHAR(64),          -- INVITATION_SENT, ACTIVATION_SUCCESS, etc
  evento VARCHAR(32),           -- SUCCESS, FAILED, EXPIRED, etc
  datos JSONB,                  -- {email, error, retry_count, ...}
  created_at TIMESTAMPTZ
);

Ventajas:
  ✓ Escalable: invitation_audit crece, usuario table no polluted
  ✓ Auditoría: 100% traceable, no mutación
  ✓ Query: SELECT * FROM invitation_audit WHERE usuario_id=5 ORDER BY created_at DESC
  ✓ Analytics: COUNT(*) por evento, trends
  ✓ Retention: puedo archivar vieja auditoría sin tocar usuarios
```

**Alternativa descartada — Columnas adicionales en usuario**

```
ALTER TABLE usuario ADD COLUMN (
  invitation_sent_at TIMESTAMPTZ,
  invitation_sent_count INT,
  last_invitation_error TEXT,
  activation_attempts INT
);

Desventajas concretas:
  ❌ usuario table crece sin control
  ❌ History perdida (overwrite cada vez)
  ❌ No puedo saber "cuál fue el error en reintento #2"
  ❌ Query compleja: "dame todos los eventos de usuario X"

Descartada porque: Auditoría separada es patrón establecido.
```

**Nota de implementación — la tabla `invitation_audit` no se creó; se usa `audit_log`**

El razonamiento de esta decisión se mantiene íntegro: la auditoría del flujo de invitaciones **no**
va mezclada en la tabla `usuario`, y el detalle estructurado va en JSONB. Lo que cambia es dónde
vive.

`audit_log` ya tiene, desde el changeset 010 y el 024, las cuatro columnas que la tabla propuesta
iba a aportar:

```
audit_log (ya existe)
  entidad        VARCHAR(64)   ← 'INVITACION'
  entidad_id     BIGINT        ← usuario afectado
  accion         VARCHAR(64)   ← INVITATION_SENT, ACTIVATION_SUCCESS, ...
  detalle        JSONB         ← {email, token_exp, error, ...}
  timestamp      TIMESTAMPTZ
  registrado_por BIGINT        ← quién hizo la acción
```

Y hay una razón de casa: el estándar del proyecto es que **la auditoría se escribe siempre por
`AuditLogService` → JPA → Postgres**, nunca desde el servicio que produce el evento. Una tabla
propia escribida desde el flujo de invitaciones sería un segundo camino de escritura sobre
auditoría, que es exactamente la forma de que el rastro se desincronice del estado real.

**Consecuencia práctica:** la consulta del rastro del flujo es

```sql
SELECT accion, detalle, timestamp
FROM audit_log
WHERE entidad = 'INVITACION' AND entidad_id = :usuario_id
ORDER BY timestamp DESC;
```

La tabla `invitation_audit` del planteo original **no se crea**. Si más adelante el volumen de
auditoría justifica separarla, se puede migrar sin perder nada: los datos están todos ahí, con la
misma forma.

Ver `apply-progress.md` §D.5, donde está declarado como desviación.

---

## Summary de Decisiones

| Decision | Elegido | Descartado | Razón |
|----------|---------|-----------|-------|
| D1 | JWT 24h expiry | Stateful en BD | Stateless, escalable |
| D2 | User-chosen password | Temporal generado | Security, ownership |
| D3 | Async + retry | Sincrónico | UX, resilience, rate limiting |
| D4 | One-time use | Reutilizable | Security (no replay) |
| D5 | MASTER manual reenvío | Auto-reenvío | Control, clarity |
| D6 | Estado PENDIENTE_INVITACION | Token IS NOT NULL | Explicit, auditable |
| D7 | Interfaz abstracta | Implementación directa | Testable, swappeable |
| D8 | 8+/mayús/num/special | Solo 8+ chars | OWASP, seguridad electoral |
| D9 | 100 emails/min rate limit | Sin límite | Defensive, no spam flagging |
| D10 | Tabla invitation_audit | Columnas en usuario | Auditoría escalable |

---

**Next:** 
- specs/auth/invitaciones.spec.md (escenarios Gherkin)
- tasks.md (work breakdown)

---

**Last Updated:** 2026-11-05
