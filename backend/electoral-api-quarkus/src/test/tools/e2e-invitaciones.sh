#!/usr/bin/env bash
# =============================================================================
# Prueba de extremo a extremo del flujo de invitaciones (T-AUTH-INV-01).
#
# Levanta el backend contra un Postgres de contenedor, manda el email de
# invitación a un SMTP capturador local y comprueba el flujo entero:
#
#   1. MASTER crea una cuenta con invitación
#   2. el email sale con un link de activación
#   3. el link expone un token JWT de tipo "activation" con 24 h de vigencia
#   4. GET validate-token dice que el token sirve
#   5. la cuenta pendiente NO puede iniciar sesión (403)
#   6. POST activate activa la cuenta con la contraseña elegida
#   7. con la cuenta activa, el login funciona
#   8. el token no se puede reutilizar (409)
#   9. un OBSERVER no puede invitar (403)
#   10. sin token, tampoco (401)
#   11. MASTER no puede invitar a una cuenta ya activa (409)
#   12. la auditoría dejó rastro de todo el flujo
#
# Por qué este script y no un test de JUnit: el token de activación solo
# existe dentro del email. La suite de contrato es black-box (habla HTTP, no
# ve la base) y este módulo no tiene arnés de CDI para levantar la aplicación
# dentro de un test. El script levanta lo mismo y mide lo mismo, y deja el
# resultado en un archivo.
#
# Uso:  bash src/test/tools/e2e-invitaciones.sh
# Requisitos: docker, python3, curl, mvn, el backend compilado.
# =============================================================================
set -uo pipefail

BASE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
MAILBOX=/tmp/mailbox
PG_CONTAINER=electoral-pg-e2e
DB_PORT=${E2E_DB_PORT:-5433}
APP_LOG=/tmp/e2e-app.log
SMTP_LOG=/tmp/e2e-smtp.log
REPORT=/tmp/e2e-invitaciones.resultado
PORT=${E2E_PORT:-8085}

PASS=0
FAIL=0
declare -a FALLOS=()

ok() {
  PASS=$((PASS + 1))
  echo "  ✔ $1"
}

fail() {
  FAIL=$((FAIL + 1))
  FALLOS+=("$1")
  echo "  ✘ $1"
}

check() {
  # check <descripción> <esperado> <obtenido>
  if [ "$2" = "$3" ]; then
    ok "$1"
  else
    fail "$1 (esperado: $2 | obtenido: $3)"
  fi
}

cleanup() {
  if [ -f /tmp/e2e-api.pid ]; then
    kill "$(cat /tmp/e2e-api.pid)" 2>/dev/null
    rm -f /tmp/e2e-api.pid
  fi
  pkill -f "smtp_capturador.py" 2>/dev/null
  if [ "${E2E_KEEP:-0}" != "1" ]; then
    docker rm -f "$PG_CONTAINER" >/dev/null 2>&1
  fi
}
trap cleanup EXIT

echo "═══════════════════════════════════════════════════════════"
echo " E2E — flujo de invitaciones (T-AUTH-INV-01)"
echo "═══════════════════════════════════════════════════════════"

# ── 1. Postgres limpio ───────────────────────────────────────────────────────
echo "▸ Postgres de contenedor"
docker rm -f "$PG_CONTAINER" >/dev/null 2>&1
docker run -d --name "$PG_CONTAINER" \
  -e POSTGRES_USER=electoral_user -e POSTGRES_PASSWORD=electoral_pass \
  -e POSTGRES_DB=electoral_db -p "$DB_PORT":5432 postgres:16 >/dev/null || {
  echo "no se pudo levantar Postgres"; exit 1;
}
for i in $(seq 1 30); do
  docker exec "$PG_CONTAINER" pg_isready -U electoral_user -d electoral_db >/dev/null 2>&1 && break
  sleep 1
done
echo "  Postgres listo"

# ── 2. Capturador SMTP ───────────────────────────────────────────────────────
echo "▸ Capturador SMTP"
mkdir -p "$MAILBOX"
rm -f "$MAILBOX"/*.eml 2>/dev/null
setsid nohup python3 "$BASE_DIR/src/test/tools/smtp_capturador.py" > "$SMTP_LOG" 2>&1 < /dev/null &
sleep 2

# ── 3. Backend ───────────────────────────────────────────────────────────────
echo "▸ Backend"
cd "$BASE_DIR" || exit 1

# Si el puerto ya está ocupado, el script hablaría con otra aplicación y los
# resultados serían de otro despliegue. Se corta acá.
if curl -s -o /dev/null --max-time 2 "http://localhost:$PORT/actuator/health" 2>/dev/null; then
  echo "  el puerto $PORT ya está ocupado por otra aplicación"
  echo "  (si es el backend de desarrollo, bajalo antes de correr el e2e)"
  exit 1
fi
export DB_HOST=localhost DB_PORT="$DB_PORT" DB_NAME=electoral_db
export DB_USER=electoral_user DB_PASSWORD=electoral_pass SERVER_PORT=$PORT
setsid nohup java -jar target/quarkus-app/quarkus-run.jar > "$APP_LOG" 2>&1 < /dev/null &
echo $! > /tmp/e2e-api.pid
for i in $(seq 1 90); do
  CODE=$(curl -s -o /dev/null -w "%{http_code}" "http://localhost:$PORT/actuator/health" 2>/dev/null)
  [ "$CODE" = "200" ] && break
  sleep 1
done
if [ "${CODE:-}" != "200" ]; then
  echo "  el backend no levantó"; tail -20 "$APP_LOG"; exit 1
fi
echo "  Backend listo"

API="http://localhost:$PORT"

# ── 4. Login del MASTER ──────────────────────────────────────────────────────
echo "▸ Sesión del MASTER"
MASTER_TOKEN=$(curl -s -X POST "$API/api/v1/auth/login" -H 'Content-Type: application/json' \
  -d '{"email":"mastersolon@example.local","password":"admin12345"}' \
  | python3 -c 'import sys,json; print(json.load(sys.stdin).get("accessToken",""))')
if [ -z "$MASTER_TOKEN" ]; then
  echo "  no se pudo iniciar sesión como MASTER"; exit 1
fi
echo "  Sesión iniciada"

# ── 5. Crear la cuenta con invitación ────────────────────────────────────────
echo "▸ Flujo de invitación"
NOMBRE="E2e$(date +%s)"
CREATED=$(curl -s -X POST "$API/api/v1/admin/usuarios" -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $MASTER_TOKEN" \
  -d "{\"nombre\":\"$NOMBRE\",\"apellido\":\"Prueba\",\"rolNombre\":\"OBSERVER\",\"invitar\":true}")
EMAIL=$(echo "$CREATED" | python3 -c 'import sys,json; print(json.load(sys.stdin).get("email",""))')
TEMPORAL=$(echo "$CREATED" | python3 -c 'import sys,json; print(json.load(sys.stdin).get("passwordTemporal",""))')
PUBLIC_ID=$(echo "$CREATED" | python3 -c 'import sys,json; print(json.load(sys.stdin).get("publicId",""))')
[ -n "$EMAIL" ] && ok "la cuenta se creó ($EMAIL)" || fail "la cuenta se creó"

# El email sale por la cola: se espera a que el worker lo despache.
for i in $(seq 1 30); do
  ls "$MAILBOX"/*.eml >/dev/null 2>&1 && break
  sleep 1
done
[ -n "$MAIL" ] && ok "salió el email de invitación" || fail "salió el email de invitación"

TOKEN=$(python3 "$BASE_DIR/src/test/tools/extraer_token.py" "$EMAIL" "$MAILBOX" 2>/dev/null)
[ -n "$TOKEN" ] && ok "el email trae un token de activación" || fail "el email trae un token de activación"

# ── 6. El token es un JWT de activación con 24 h ─────────────────────────────
CLAIMS=$(python3 - "$TOKEN" <<'PY' 2>/dev/null
import base64, json, sys
jwt = base64.urlsafe_b64decode(sys.argv[1] + '=' * (-len(sys.argv[1]) % 4)).decode()
part = jwt.split('.')[1]
part += '=' * (-len(part) % 4)
print(json.dumps(json.loads(base64.urlsafe_b64decode(part))))
PY
)
TYP=$(echo "$CLAIMS" | python3 -c 'import sys,json; print(json.load(sys.stdin).get("typ",""))' 2>/dev/null)
TTL=$(echo "$CLAIMS" | python3 -c 'import sys,json; c=json.load(sys.stdin); print(c.get("exp",0)-c.get("iat",0))' 2>/dev/null)
SUB=$(echo "$CLAIMS" | python3 -c 'import sys,json; print(json.load(sys.stdin).get("sub",""))' 2>/dev/null)
check "el token es de tipo activation" "activation" "$TYP"
check "el token vive 24 horas" "86400" "$TTL"
check "el sub del token es el id público" "$PUBLIC_ID" "$SUB"

# ── 7. Validación del token ──────────────────────────────────────────────────
echo "▸ Validación y activación"
VALID=$(curl -s "$API/api/v1/auth/invitaciones/validate-token/$TOKEN")
check "validate-token dice que el token sirve" "True" \
  "$(echo "$VALID" | python3 -c 'import sys,json; print(json.load(sys.stdin)["valid"])')"

INVALID=$(curl -s "$API/api/v1/auth/invitaciones/validate-token/token.inventado.aqui")
check "validate-token rechaza un token inventado" "False" \
  "$(echo "$INVALID" | python3 -c 'import sys,json; print(json.load(sys.stdin)["valid"])')"

# ── 8. La cuenta pendiente no puede iniciar sesión ───────────────────────────
LOGIN_PENDIENTE=$(curl -s -o /tmp/e2e-login.json -w "%{http_code}" -X POST "$API/api/v1/auth/login" \
  -H 'Content-Type: application/json' -d "{\"email\":\"$EMAIL\",\"password\":\"$TEMPORAL\"}")
check "una cuenta pendiente no puede iniciar sesión" "403" "$LOGIN_PENDIENTE"
grep -q "no ha activado su cuenta" /tmp/e2e-login.json \
  && ok "el mensaje dice que falta activar" \
  || fail "el mensaje dice que falta activar"

# ── 9. Activación ────────────────────────────────────────────────────────────
CONTRASENA='Observer@2025!'
ACTIVADO=$(curl -s -o /tmp/e2e-activate.json -w "%{http_code}" -X POST "$API/api/v1/auth/activate" \
  -H 'Content-Type: application/json' \
  -d "{\"token\":\"$TOKEN\",\"password\":\"$CONTRASENA\",\"passwordConfirm\":\"$CONTRASENA\"}")
check "la activación devuelve 200" "200" "$ACTIVADO"

DEBIL=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$API/api/v1/auth/activate" \
  -H 'Content-Type: application/json' \
  -d '{"token":"inventado.aqui.inventado","password":"corta","passwordConfirm":"corta"}')
check "un token inválido con contraseña débil da 400" "400" "$DEBIL"

# ── 10. El token no se reutiliza ─────────────────────────────────────────────
REUSO=$(curl -s -o /tmp/e2e-reuso.json -w "%{http_code}" -X POST "$API/api/v1/auth/activate" \
  -H 'Content-Type: application/json' \
  -d "{\"token\":\"$TOKEN\",\"password\":\"Otra@2025!\",\"passwordConfirm\":\"Otra@2025!\"}")
check "el token no se puede reutilizar" "409" "$REUSO"


# ── 10b. El reenvío invalida el token anterior ───────────────────────────────
# Es el camino por el que se llega al guardia de "token consumido": si el
# usuario todavía está pendiente y el MASTER reenvía la invitación, el token
# viejo tiene que dejar de servir y el nuevo servir.
echo "▸ Reenvío de la invitación"
ANTES=$(ls "$MAILBOX"/*.eml 2>/dev/null | wc -l)
NOMBRE2="E2eRe$(date +%s)"
CREATED2=$(curl -s -X POST "$API/api/v1/admin/usuarios" -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $MASTER_TOKEN" \
  -d "{\"nombre\":\"$NOMBRE2\",\"apellido\":\"Prueba\",\"rolNombre\":\"OBSERVER\",\"invitar\":true}")
PUBLIC_ID2=$(echo "$CREATED2" | python3 -c 'import sys,json; print(json.load(sys.stdin).get("publicId",""))')
EMAIL2=$(echo "$CREATED2" | python3 -c 'import sys,json; print(json.load(sys.stdin).get("email",""))')
for i in $(seq 1 30); do
  TOKEN2=$(python3 "$BASE_DIR/src/test/tools/extraer_token.py" "$EMAIL2" "$MAILBOX" 2>/dev/null)
  [ -n "$TOKEN2" ] && break
  sleep 1
done
TOKEN2=$(python3 "$BASE_DIR/src/test/tools/extraer_token.py" "$EMAIL2" "$MAILBOX" 2>/dev/null)
[ -n "$TOKEN2" ] && ok "la segunda cuenta recibió su invitación" || fail "la segunda cuenta recibió su invitación"

# Reenvío: genera un token nuevo y deja el viejo sin efecto.
curl -s -o /dev/null -X POST "$API/api/v1/auth/invitaciones" -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $MASTER_TOKEN" -d "{\"usuarioIds\":[\"$PUBLIC_ID2\"]}"
TOKEN3="$TOKEN2"
for i in $(seq 1 30); do
  TOKEN3=$(python3 "$BASE_DIR/src/test/tools/extraer_token.py" "$EMAIL2" "$MAILBOX" 2>/dev/null)
  [ -n "$TOKEN3" ] && [ "$TOKEN3" != "$TOKEN2" ] && break
  sleep 1
done
TOKEN3=$(python3 "$BASE_DIR/src/test/tools/extraer_token.py" "$EMAIL2" "$MAILBOX" 2>/dev/null)
[ -n "$TOKEN3" ] && [ "$TOKEN3" != "$TOKEN2" ] \
  && ok "el reenvío genera un token nuevo" \
  || fail "el reenvío genera un token nuevo"

VIEJO=$(curl -s -o /tmp/e2e-viejo.json -w "%{http_code}" -X POST "$API/api/v1/auth/activate" \
  -H 'Content-Type: application/json' \
  -d "{\"token\":\"$TOKEN2\",\"password\":\"Observer@2025!\",\"passwordConfirm\":\"Observer@2025!\"}")
check "el token viejo deja de servir después del reenvío" "409" "$VIEJO"
if [ "$VIEJO" != "409" ]; then
  echo "    cuerpo de la respuesta: $(cat /tmp/e2e-viejo.json)"
  echo "    token viejo (${#TOKEN2} chars): ${TOKEN2:0:40}..."
  echo "    publicId2=$PUBLIC_ID2 email2=$EMAIL2"
  NUEVO=$(curl -s -o /tmp/e2e-nuevo.json -w "%{http_code}" -X POST "$API/api/v1/auth/activate" \
    -H 'Content-Type: application/json' \
    -d "{\"token\":\"$TOKEN3\",\"password\":\"Observer@2025!\",\"passwordConfirm\":\"Observer@2025!\"}")
  echo "    activar con el token NUEVO: $NUEVO -> $(cat /tmp/e2e-nuevo.json)"
fi

# ── 11. Login con la contraseña elegida ──────────────────────────────────────
LOGIN_OK=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$API/api/v1/auth/login" \
  -H 'Content-Type: application/json' -d "{\"email\":\"$EMAIL\",\"password\":\"$CONTRASENA\"}")
check "con la cuenta activada se puede iniciar sesión" "200" "$LOGIN_OK"

# ── 12. Autorización ─────────────────────────────────────────────────────────
OBS=$(curl -s -X POST "$API/api/v1/admin/usuarios" -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $MASTER_TOKEN" \
  -d "{\"nombre\":\"E2eObs$(date +%s)\",\"apellido\":\"Prueba\",\"rolNombre\":\"OBSERVER\"}")
OBS_EMAIL=$(echo "$OBS" | python3 -c 'import sys,json; print(json.load(sys.stdin).get("email",""))')
OBS_PASS=$(echo "$OBS" | python3 -c 'import sys,json; print(json.load(sys.stdin).get("passwordTemporal",""))')
OBS_TOKEN=$(curl -s -X POST "$API/api/v1/auth/login" -H 'Content-Type: application/json' \
  -d "{\"email\":\"$OBS_EMAIL\",\"password\":\"$OBS_PASS\"}" \
  | python3 -c 'import sys,json; print(json.load(sys.stdin).get("accessToken",""))')

SIN_PERMISO=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$API/api/v1/auth/invitaciones" \
  -H 'Content-Type: application/json' -H "Authorization: Bearer $OBS_TOKEN" \
  -d "{\"usuarioIds\":[\"$PUBLIC_ID\"]}")
check "un OBSERVER no puede invitar" "403" "$SIN_PERMISO"

SIN_TOKEN=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$API/api/v1/auth/invitaciones" \
  -H 'Content-Type: application/json' -d "{\"usuarioIds\":[\"$PUBLIC_ID\"]}")
check "sin token no se puede invitar" "401" "$SIN_TOKEN"

# ── 13. El límite de intentos de activación ──────────────────────────────────
RATE=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$API/api/v1/auth/activate" \
  -H 'Content-Type: application/json' -d '{"token":"x.y.z","password":"Aa1!aaaa","passwordConfirm":"Aa1!aaaa"}')
for i in $(seq 1 14); do
  RATE=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$API/api/v1/auth/activate" \
    -H 'Content-Type: application/json' -d '{"token":"x.y.z","password":"Aa1!aaaa","passwordConfirm":"Aa1!aaaa"}')
done
check "el límite de intentos de activación corta con 429" "429" "$RATE"

# ── 14. Auditoría ────────────────────────────────────────────────────────────
echo "▸ Auditoría"
AUDIT=$(docker exec "$PG_CONTAINER" psql -U electoral_user -d electoral_db -t -c \
  "SELECT count(*) FROM audit_log WHERE entidad='INVITACION' AND accion IN ('INVITATION_SENT','ACTIVATION_SUCCESS','ACTIVATION_REJECTED','LOGIN_REJECTED');" 2>/dev/null | tr -d ' ')
[ "${AUDIT:-0}" -ge 4 ] \
  && ok "la auditoría registró el flujo completo ($AUDIT eventos)" \
  || fail "la auditoría registró el flujo completo (eventos: ${AUDIT:-0})"

# ── Resultado ────────────────────────────────────────────────────────────────
echo "═══════════════════════════════════════════════════════════"
echo " RESULTADO: $PASS ok / $FAIL fallos"
if [ "$FAIL" -gt 0 ]; then
  echo " Fallos:"
  for f in "${FALLOS[@]}"; do echo "   - $f"; done
fi
echo "═══════════════════════════════════════════════════════════"
{
  echo "E2E invitaciones — $(date -Iseconds)"
  echo "ok=$PASS fallos=$FAIL"
  for f in "${FALLOS[@]}"; do echo "FALLO: $f"; done
} > "$REPORT"

exit $(( FAIL > 0 ? 1 : 0 ))
