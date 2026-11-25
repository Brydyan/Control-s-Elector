#!/bin/bash
# test-invitations.sh — ejecuta tests de invitación con tus credenciales Resend

set -e
cd /home/andy/Documentos/PROYECTOS/CONTROL/Control-s-Elector

echo "════════════════════════════════════════════════════════════════"
echo "   Test Suite: Invitaciones Email (Resend + Mock)"
echo "════════════════════════════════════════════════════════════════"
echo ""

# Carga tu .env
if [ ! -f .env ]; then
  echo "❌ ERROR: .env no encontrado en la raíz del proyecto"
  exit 1
fi

echo "📋 Cargando .env..."
# Parse solo líneas VAR=valor (sin comentarios)
set -a
source <(grep -v '^\s*#\|^\s*$' .env | sed 's/#.*//')
set +a

# Verifica que tengas vars críticas
echo ""
echo "🔍 Verificación previa:"
echo "   SMTP_HOST: ${SMTP_HOST:-<no configurado>}"
echo "   APP_EMAIL_PROVIDER: ${APP_EMAIL_PROVIDER:-<no configurado>}"

if [ -n "$RESEND_API_KEY" ]; then
  echo "   RESEND_API_KEY: ✓ configurada"
else
  echo "   RESEND_API_KEY: ⚠ VACÍA (tests usarán mock)"
fi

# Maven: detecta ruta
BACKEND_DIR="$(pwd)/backend/electoral-api-quarkus"
if [ -f "$BACKEND_DIR/mvnw" ]; then
  echo "   Maven: ✓ wrapper encontrado"
elif command -v mvn &> /dev/null; then
  echo "   Maven: ✓ en PATH"
else
  echo "❌ Maven no encontrado en $BACKEND_DIR/mvnw ni en PATH"
  exit 1
fi

echo ""
echo "════════════════════════════════════════════════════════════════"
echo "TEST 1: Invitación básica (RecordingEmailService mock)"
echo "════════════════════════════════════════════════════════════════"
export APP_EMAIL_PROVIDER=smtp
echo "→ APP_EMAIL_PROVIDER=$APP_EMAIL_PROVIDER"
echo ""

cd "$BACKEND_DIR"
if [ -f mvnw ]; then
  ./mvnw test -Dtest=UserInvitationIT#elMasterInvitaYLaCuentaQuedaCreada -DfailIfNoTests=false
else
  mvn test -Dtest=UserInvitationIT#elMasterInvitaYLaCuentaQuedaCreada -DfailIfNoTests=false
fi
cd - > /dev/null

echo ""
echo "✓ TEST 1 pasado"
echo ""

# TEST 2: Real Resend (si tienes keys)
if [ -n "$RESEND_API_KEY" ]; then
  echo "════════════════════════════════════════════════════════════════"
  echo "TEST 2: Invitación real con Resend API"
  echo "════════════════════════════════════════════════════════════════"
  export APP_EMAIL_PROVIDER=resend
  echo "→ APP_EMAIL_PROVIDER=$APP_EMAIL_PROVIDER"
  echo "→ RESEND_API_KEY=***configurada***"
  echo ""

  cd "$BACKEND_DIR"
  if [ -f mvnw ]; then
    ./mvnw test -Dtest=UserInvitationIT#elEmailSaleAlCorreoDeContactoYLlevaElLogin -DfailIfNoTests=false
  else
    mvn test -Dtest=UserInvitationIT#elEmailSaleAlCorreoDeContactoYLlevaElLogin -DfailIfNoTests=false
  fi
  cd - > /dev/null

  echo ""
  echo "✓ TEST 2 pasado"
else
  echo "════════════════════════════════════════════════════════════════"
  echo "TEST 2: SALTADO (RESEND_API_KEY no configurada)"
  echo "════════════════════════════════════════════════════════════════"
fi

echo ""
echo "════════════════════════════════════════════════════════════════"
echo "TEST 3: Diagnóstico completo (con logs)"
echo "════════════════════════════════════════════════════════════════"
export APP_EMAIL_PROVIDER=smtp
echo "→ APP_EMAIL_PROVIDER=$APP_EMAIL_PROVIDER"
echo ""

cd "$BACKEND_DIR"
if [ -f mvnw ]; then
  ./mvnw test -Dtest=UserInvitationIT#diagnosticoResendConFallback -DfailIfNoTests=false
else
  mvn test -Dtest=UserInvitationIT#diagnosticoResendConFallback -DfailIfNoTests=false
fi
cd - > /dev/null

echo ""
echo "✓ TEST 3 pasado"
echo ""

echo "════════════════════════════════════════════════════════════════"
echo "✅ TODOS LOS TESTS COMPLETADOS"
echo "════════════════════════════════════════════════════════════════"
echo ""
echo "📊 Próximos pasos:"
echo "   1. Revisa logs arriba para errores"
echo "   2. Si RESEND_API_KEY está vacía:"
echo "      - Rellénala en .env con tu clave real"
echo "      - Verifica DNS CNAME: send.mail → send.forge.rmta.net"
echo "      - Re-ejecuta: bash test-invitations.sh"
echo "   3. Para solo TEST 2 (Resend real):"
echo "      bash test-invitations.sh 2>&1 | grep -A 50 'TEST 2'"
echo ""
