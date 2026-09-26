# Guía de Verificación y QA del Backend — Control Electoral 2027

Documento de auditoría y verificación ejecutable para validar la integridad del backend antes de dar por terminada la fase de API y pasar a la integración con frontend.

Basado en los estándares de **Arquitecto / QA** (`documents/agents/01-arquitecto-qa.md`) y la **Especificación Santa Elena 2027**.

---

## Nivel 1: Suite Automatizada (Compuerta Básica)

Ejecutar la suite completa sin pipes para evitar enmascarar errores con el exit code de utilidades como `tail`:

```bash
# 1. Posicionarse en el directorio del backend
cd backend/electoral-api

# 2. Correr la suite completa redirigiendo a log
./mvnw -B test > /tmp/mvn-backend-test.log 2>&1
echo "Exit code: $?"   # DEBE ser 0

# 3. Contar resultados reales directamente desde los reportes XML de Surefire:
grep -h -o 'tests="[0-9]*"\|errors="[0-9]*"\|failures="[0-9]*"\|skipped="[0-9]*"' \
  target/surefire-reports/TEST-*.xml \
  | awk -F'"' '{total[$1] += $2} END {for (k in total) print k, total[k]}'

# Criterio de Aceptación Nivel 1:
# - errors= 0
# - failures= 0
# - tests >= 281

# 4. Verificar formateo de código Spotless (Google Java Format):
./mvnw -B spotless:check
# DEBE retornar BUILD SUCCESS
```

---

## Nivel 2: Stack Local Limpio (Infraestructura)

Validar que los contenedores de base de datos, Liquibase y el storage local (RustFS S3) arranquen e inicialicen sin errores de migración:

```bash
# 1. Desde la raíz del repositorio, levantar Postgres 16 y RustFS:
docker compose --profile dev up -d

# 2. Verificar que los contenedores estén sanos:
docker compose ps

# 3. En otra terminal, iniciar el backend en el host:
cd backend/electoral-api
./mvnw spring-boot:run

# 4. Verificar salud del servicio vía Actuator:
curl -s http://localhost:8081/actuator/health
# Salida esperada: {"status":"UP"}

# 5. Verificar que OpenAPI y Swagger UI carguen correctamente:
# Abrir en navegador: http://localhost:8081/swagger-ui.html
```

---

## Nivel 3: Smoke Test del Flujo Crítico de Actas (HTTP / curl)

Flujo secuencial para verificar el ciclo de vida real de un acta en la API:

### 1. Autenticación y obtención de JWT
```bash
# Login como MASTER / Admin:
RESP_AUTH=$(curl -s -X POST http://localhost:8081/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admsismas@control.ttcs","password":"admin12345"}')

echo "$RESP_AUTH"

# Extraer el access token (requiere jq):
TOKEN=$(echo "$RESP_AUTH" | jq -r '.accessToken')
echo "Token obtenido: $TOKEN"
```

### 2. Observer: Registrar Acta
Registrar acta para la mesa JRV 1 y Cargo 1 (Alcalde), con un total de 350 sufragantes:

```bash
RESP_ACTA=$(curl -s -X POST http://localhost:8081/api/v1/observer/actas \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "jrvId": 1,
    "cargoId": 1,
    "totalSufragantes": 350,
    "votosBlancos": 10,
    "votosNulos": 20,
    "detalles": [
      {"candidatoId": 1, "votos": 200},
      {"candidatoId": 2, "votos": 120}
    ]
  }')

echo "$RESP_ACTA"
# Salida esperada: HTTP 201 Created con JSON del acta en estado BORRADOR
# Extraer el ID del acta:
ACTA_ID=$(echo "$RESP_ACTA" | jq -r '.id // .publicId')
```

### 3. Observer: Subir Foto de Acta (WebP)
Subir la evidencia física del acta (cerrando el endpoint de storage):

```bash
# Crear imagen dummy de prueba si no se dispone de una foto real:
echo "dummy-webp-binary-data" > /tmp/foto_prueba.webp

curl -i -X POST http://localhost:8081/api/v1/observer/actas/1/imagen \
  -H "Authorization: Bearer $TOKEN" \
  -F "imagen=@/tmp/foto_prueba.webp;type=image/webp"

# Salida esperada: HTTP 201 Created (NUNCA 501 NOT_IMPLEMENTED)
```

### 4. Observer: Enviar Acta para Revisión
```bash
curl -i -X POST http://localhost:8081/api/v1/observer/actas/1/enviar \
  -H "Authorization: Bearer $TOKEN"

# Salida esperada: HTTP 200 OK con estado = "ENVIADA"
```

### 5. Coordinator: Listar y Aprobar Acta
```bash
# Listar actas en revisión:
curl -s -X GET "http://localhost:8081/api/v1/coordinator/actas?estado=ENVIADA" \
  -H "Authorization: Bearer $TOKEN" | jq .

# Aprobar el acta:
curl -i -X POST http://localhost:8081/api/v1/coordinator/actas/1/aprobar \
  -H "Authorization: Bearer $TOKEN"

# Salida esperada: HTTP 200 OK con estado = "APROBADA"
```

### 6. Dashboard: Verificar Consolidación
```bash
# Resumen global de actas:
curl -s http://localhost:8081/api/v1/dashboard/resumen \
  -H "Authorization: Bearer $TOKEN" | jq .
# Debe reflejar "aprobadas": 1, "totalActivas": 1

# Resultados agregados por candidato:
curl -s http://localhost:8081/api/v1/dashboard/resultados \
  -H "Authorization: Bearer $TOKEN" | jq .
```

---

## Nivel 4: Pruebas de Fuego (Las Garantías Críticas Electorales)

Estas pruebas validan los invariantes duros del sistema ante intentos de fraude o fallos de concurrencia:

### 1. Invariante Matemática (Rechazo de Cuadre Inválido)
El backend **debe rechazar** cualquier acta donde $\sum \text{votos} + \text{blancos} + \text{nulos} \neq \text{totalSufragantes}$:

```bash
curl -i -X POST http://localhost:8081/api/v1/observer/actas \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "jrvId": 2,
    "cargoId": 1,
    "totalSufragantes": 350,
    "votosBlancos": 10,
    "votosNulos": 20,
    "detalles": [
      {"candidatoId": 1, "votos": 200},
      {"candidatoId": 2, "votos": 125}
    ]
  }'
# Suma: 200 + 125 + 10 + 20 = 355 (Mayor a 350)
# Salida esperada: HTTP 400 Bad Request (Error de validación matemática)
```

### 2. Concurrencia y Bloqueo Pesimista (`SELECT FOR UPDATE NOWAIT`)
Intentar registrar la misma mesa y dignidad dos veces:

```bash
# Si el acta JRV 1 y Cargo 1 ya existe, un segundo registro debe fallar:
curl -i -X POST http://localhost:8081/api/v1/observer/actas \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "jrvId": 1,
    "cargoId": 1,
    "totalSufragantes": 350,
    "votosBlancos": 10,
    "votosNulos": 20,
    "detalles": [
      {"candidatoId": 1, "votos": 200},
      {"candidatoId": 2, "votos": 120}
    ]
  }'
# Salida esperada: HTTP 409 Conflict (Acta ya registrada para esta JRV y Cargo)
```

### 3. Row Level Security (RLS en PostgreSQL)
- Un usuario con rol `OBSERVER` no asignado a la JRV 999 intenta consultar o mutar actas de dicha mesa.
- **Salida esperada**: PostgreSQL no retorna filas (`HTTP 404 / 403`), impidiendo fuga o manipulación de actas ajenas en base de datos.

### 4. Auditoría Forense (`audit_log`)
Verificar que cada mutación crítica haya dejado rastro inmutable en la tabla `audit_log`:

```sql
SELECT id, accion, entidad, entidad_id, registrado_por, ip_address, timestamp 
FROM audit_log 
ORDER BY id DESC 
LIMIT 10;
```
- Debe contener registros para: `ACTA_REGISTRADA`, `EVIDENCIA_SUBIDA`, `ACTA_APROBADA`.

---

## Checklist de Aprobación Final del Backend

- [ ] **Nivel 1**: Suite completa en VERDE (281+ tests, 0 errores, 0 fallos) + Spotless check limpio.
- [ ] **Nivel 2**: Docker compose levanta PostgreSQL y RustFS; Liquibase aplica todos los changelogs sin error.
- [ ] **Nivel 3**: Flujo completo probado con curl (`login` ➔ `registrar` ➔ `subir foto` ➔ `enviar` ➔ `aprobar` ➔ `dashboard`).
- [ ] **Nivel 4**: Las 3 garantías de negocio (cuadre matemático, locking pesimista y RLS) verificadas con respuestas de rechazo esperadas.
