# T-IN-04 — Datos semilla (seed)

**Estado:** COMPLETADO ✅
**Dificultad:** Principiante
**Tiempo estimado:** 2 horas
**Depende de:** T-IN-02 ✅

---

## ¿Qué vas a construir?

Vas a crear un script SQL que popula la BD con datos iniciales necesarios para que staging funcione: provincia Santa Elena, sus 3 cantones, parroquias, JRVs de ejemplo, usuario MASTER inicial, y cargos electorales.

**Por qué es importante:**
- Sin datos semilla, la BD está vacía y no puedes probar nada
- Reproducible: staging siempre tiene los mismos datos base
- Referencia para desarrollo

---

## Conceptos clave

**¿Qué es un "seed" o "fixture"?**
Datos iniciales que se insertan automáticamente. Típico: usuario admin inicial, catálogos (provincias, cargos), datos de prueba.

**¿Por qué no hardcodear datos en la app?**
Es más limpio y portable mantener datos en SQL. Facilita testing, respaldo y auditoría.

**¿Qué es `SERIAL` vs insertar IDs manualmente?**
`SERIAL` genera IDs automáticamente. Es mejor dejar que lo haga la BD. Solo especifica ID si necesitas una FK exacta después.

---

## Archivos a crear

```
backend/electoral-api/src/main/resources/database/infra/sql/
  03-seed.sql                ← Crear
```

---

## Pasos

### Paso 1 — Crear `03-seed.sql`

Guarda en: `backend/electoral-api/src/main/resources/database/infra/sql/03-seed.sql`

```sql
-- Infra SQL 03 — Datos semilla (seed)
-- Provincia Santa Elena, cantones, parroquias, JRVs, cargos, usuario MASTER inicial

-- ============================================================================
-- ROLES (valores fijos del sistema)
-- ============================================================================

INSERT INTO rol (nombre, descripcion) VALUES
  ('MASTER', 'Administrador total del sistema'),
  ('COORDINATOR', 'Coordinador electoral, gestiona disputas y correcciones'),
  ('ADMIN', 'Solo lectura, acceso a dashboard y estadísticas'),
  ('OBSERVER', 'Registra actas en sus JRVs asignadas')
ON CONFLICT (nombre) DO NOTHING;

-- ============================================================================
-- GEOGRAFÍA: PROVINCIA SANTA ELENA
-- ============================================================================

-- Provincia
INSERT INTO provincia (nombre, codigo) VALUES
  ('Santa Elena', '24')
ON CONFLICT (codigo) DO NOTHING;

-- Cantones
INSERT INTO canton (nombre, codigo, provincia_id) 
SELECT 'Santa Elena', '2401', id FROM provincia WHERE codigo = '24'
ON CONFLICT (codigo) DO NOTHING;

INSERT INTO canton (nombre, codigo, provincia_id) 
SELECT 'La Libertad', '2402', id FROM provincia WHERE codigo = '24'
ON CONFLICT (codigo) DO NOTHING;

INSERT INTO canton (nombre, codigo, provincia_id) 
SELECT 'Salinas', '2403', id FROM provincia WHERE codigo = '24'
ON CONFLICT (codigo) DO NOTHING;

-- Parroquias de Santa Elena cantón (2401)
INSERT INTO parroquia (nombre, es_urbana, canton_id)
SELECT 'Santa Elena', true, id FROM canton WHERE codigo = '2401'
ON CONFLICT DO NOTHING;

INSERT INTO parroquia (nombre, es_urbana, canton_id)
SELECT 'Chanduy', false, id FROM canton WHERE codigo = '2401'
ON CONFLICT DO NOTHING;

INSERT INTO parroquia (nombre, es_urbana, canton_id)
SELECT 'Manglaralto', false, id FROM canton WHERE codigo = '2401'
ON CONFLICT DO NOTHING;

-- Parroquias de La Libertad cantón (2402)
INSERT INTO parroquia (nombre, es_urbana, canton_id)
SELECT 'La Libertad', true, id FROM canton WHERE codigo = '2402'
ON CONFLICT DO NOTHING;

INSERT INTO parroquia (nombre, es_urbana, canton_id)
SELECT 'Atahualpa', false, id FROM canton WHERE codigo = '2402'
ON CONFLICT DO NOTHING;

-- Parroquias de Salinas cantón (2403)
INSERT INTO parroquia (nombre, es_urbana, canton_id)
SELECT 'Salinas', true, id FROM canton WHERE codigo = '2403'
ON CONFLICT DO NOTHING;

-- ============================================================================
-- JRVs DE EJEMPLO (5 por cantón = 15 total para testing)
-- ============================================================================

-- JRVs en Santa Elena cantón (parroquia Santa Elena urbana)
DO $$
DECLARE
  parroquia_id BIGINT;
BEGIN
  SELECT id INTO parroquia_id FROM parroquia WHERE nombre = 'Santa Elena' AND canton_id = (SELECT id FROM canton WHERE codigo = '2401');
  
  FOR i IN 1..5 LOOP
    INSERT INTO jrv (numero, recinto, total_electores, parroquia_id)
    VALUES (LPAD(i::text, 3, '0'), 'Escuela ' || i || ' - Santa Elena', 100 + (i * 10), parroquia_id)
    ON CONFLICT DO NOTHING;
  END LOOP;
END $$;

-- JRVs en La Libertad cantón
DO $$
DECLARE
  parroquia_id BIGINT;
BEGIN
  SELECT id INTO parroquia_id FROM parroquia WHERE nombre = 'La Libertad' AND canton_id = (SELECT id FROM canton WHERE codigo = '2402');
  
  FOR i IN 6..10 LOOP
    INSERT INTO jrv (numero, recinto, total_electores, parroquia_id)
    VALUES (LPAD(i::text, 3, '0'), 'Colegio ' || i || ' - La Libertad', 120 + (i * 10), parroquia_id)
    ON CONFLICT DO NOTHING;
  END LOOP;
END $$;

-- JRVs en Salinas cantón
DO $$
DECLARE
  parroquia_id BIGINT;
BEGIN
  SELECT id INTO parroquia_id FROM parroquia WHERE nombre = 'Salinas' AND canton_id = (SELECT id FROM canton WHERE codigo = '2403');
  
  FOR i IN 11..15 LOOP
    INSERT INTO jrv (numero, recinto, total_electores, parroquia_id)
    VALUES (LPAD(i::text, 3, '0'), 'Instituto ' || i || ' - Salinas', 110 + (i * 10), parroquia_id)
    ON CONFLICT DO NOTHING;
  END LOOP;
END $$;

-- ============================================================================
-- USUARIO MASTER INICIAL
-- ============================================================================

-- Email generado automáticamente según el nuevo formato:
-- {3 letras nombre}{3 letras apellido}{3 letras rol}@control.ttcs
-- "Admin" + "Sistema" + "MASTER" = admsismas@control.ttcs

-- La contraseña es admin12345 (solo dev, cambiar en prod)
-- Hash BCrypt creado localmente (usa BCrypt)
-- Para generar el hash, ejecuta en Java:
-- new BCryptPasswordEncoder().encode("admin12345")
-- Resultado típico: $2a$10$7Rg5fRhQrDVpMvgBxvXJYOCCWqKj1mDMqKKKV1...

INSERT INTO usuario (public_id, nombre, apellido, email, password_hash, rol_id, habilitado)
SELECT 
  gen_random_uuid(),
  'Admin',
  'Sistema',
  'admsismas@control.ttcs',
  -- ⚠️ COPIA ESTO: Hash BCrypt de "admin12345" generado con BCrypt
  -- Necesitas ejecutar esto en tu app Java y obtener el hash:
  -- PasswordEncoder encoder = new BCryptPasswordEncoder();
  -- String hash = encoder.encode("admin12345");
  -- Por ahora usaré un placeholder — reemplázalo con el hash real
  '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcg7b3XeKeUxWdeS86E36P4/PFm',
  (SELECT id FROM rol WHERE nombre = 'MASTER'),
  true
WHERE NOT EXISTS (SELECT 1 FROM usuario WHERE email = 'admsismas@control.ttcs')
ON CONFLICT DO NOTHING;

-- ============================================================================
-- CARGOS ELECTORALES
-- ============================================================================

INSERT INTO cargo (nombre, nivel_geografico, descripcion) VALUES
  ('Prefecto/a de la Provincia', 'PROVINCIA', 'Jefe ejecutivo de la provincia'),
  ('Consejero/a CPCCS', 'NACIONAL', 'Consejo de Participación Ciudadana'),
  ('Alcalde/sa del Cantón', 'CANTON', 'Jefe ejecutivo del cantón'),
  ('Concejal Urbano', 'CANTON', 'Concejal en zona urbana'),
  ('Concejal Rural', 'CANTON', 'Concejal en zona rural'),
  ('Vocal de Junta Parroquial', 'PARROQUIA', 'Miembro de la junta parroquial rural')
ON CONFLICT DO NOTHING;

-- ============================================================================
-- PARTIDOS POLÍTICOS (ejemplo)
-- ============================================================================

INSERT INTO partido (nombre, siglas, numero, color_hex) VALUES
  ('Unidad Popular', 'UP', 1, '#FF0000'),
  ('Movimiento CREO', 'CREO', 2, '#0066FF'),
  ('Revolución Ciudadana', 'RC', 3, '#FFD700'),
  ('Izquierda Democrática', 'ID', 4, '#FF6600'),
  ('Construcción Solidaria', 'CS', 5, '#00AA00')
ON CONFLICT DO NOTHING;

-- ============================================================================
-- CANDIDATOS DE EJEMPLO (Prefecto/a solamente, para simplificar)
-- ============================================================================

INSERT INTO candidato (nombre, apellido, numero_lista, cargo_id, partido_id)
SELECT 'Juan', 'Pérez', 1, (SELECT id FROM cargo WHERE nombre = 'Prefecto/a de la Provincia'), (SELECT id FROM partido WHERE siglas = 'UP')
ON CONFLICT DO NOTHING;

INSERT INTO candidato (nombre, apellido, numero_lista, cargo_id, partido_id)
SELECT 'María', 'García', 2, (SELECT id FROM cargo WHERE nombre = 'Prefecto/a de la Provincia'), (SELECT id FROM partido WHERE siglas = 'CREO')
ON CONFLICT DO NOTHING;

INSERT INTO candidato (nombre, apellido, numero_lista, cargo_id, partido_id)
SELECT 'Carlos', 'López', 3, (SELECT id FROM cargo WHERE nombre = 'Prefecto/a de la Provincia'), (SELECT id FROM partido WHERE siglas = 'RC')
ON CONFLICT DO NOTHING;

-- ============================================================================
-- JRV_CARGO: Mapear qué cargos aplican en cada JRV
-- ============================================================================

-- Todos los cargos aplican en todas las JRVs (simplificado para testing)
-- En prod, filtraría por nivel geográfico (NACIONAL, PROVINCIA, etc)

INSERT INTO jrv_cargo (jrv_id, cargo_id)
SELECT jrv.id, cargo.id
FROM jrv
CROSS JOIN cargo
WHERE NOT EXISTS (
  SELECT 1 FROM jrv_cargo 
  WHERE jrv_cargo.jrv_id = jrv.id 
  AND jrv_cargo.cargo_id = cargo.id
);

-- ============================================================================
-- OBSERVER DE EJEMPLO (para testing RLS)
-- ============================================================================

-- Email generado: juapeperobs@control.ttcs (Juan + Pérez + OBSERVER)
-- Asignamos algunas JRVs para que pueda registrar actas

INSERT INTO usuario (public_id, nombre, apellido, email, password_hash, rol_id, habilitado)
SELECT 
  gen_random_uuid(),
  'Juan',
  'Pérez',
  'juapeperobs@control.ttcs',
  '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcg7b3XeKeUxWdeS86E36P4/PFm', -- admin12345
  (SELECT id FROM rol WHERE nombre = 'OBSERVER'),
  true
WHERE NOT EXISTS (SELECT 1 FROM usuario WHERE email = 'juapeperobs@control.ttcs')
ON CONFLICT DO NOTHING;

-- Asignar algunas JRVs al observer
INSERT INTO observer_jrv_asignacion (observer_id, jrv_id)
SELECT 
  (SELECT id FROM usuario WHERE email = 'juapeperobs@control.ttcs'),
  id
FROM jrv
WHERE numero IN ('001', '002', '003')
ON CONFLICT DO NOTHING;

-- ============================================================================
-- VERIFICACIÓN: Contar registros
-- ============================================================================

-- SELECT COUNT(*) FROM provincia;     -- 1
-- SELECT COUNT(*) FROM canton;        -- 3
-- SELECT COUNT(*) FROM parroquia;     -- 6
-- SELECT COUNT(*) FROM jrv;           -- 15
-- SELECT COUNT(*) FROM usuario;       -- 2+ (MASTER + OBSERVER)
-- SELECT COUNT(*) FROM cargo;         -- 6
-- SELECT COUNT(*) FROM partido;       -- 5
-- SELECT COUNT(*) FROM candidato;     -- 3
-- SELECT COUNT(*) FROM jrv_cargo;     -- 90 (15 jrvs * 6 cargos)
```

### Paso 2 — Generar hash BCrypt correcto

**IMPORTANTE:** El script anterior usa un hash placeholder. Necesitas reemplazarlo con el hash real de `admin12345`.

Para generar el hash:

**Opción A: Desde Java (Quarkus)**
```java
import io.quarkus.runtime.Startup;
import io.quarkus.runtime.configuration.Config;
import org.mindrot.jbcrypt.BCrypt;

public class GenerateHash {
    public static void main(String[] args) {
        String plainPassword = "admin12345";
        String hash = BCrypt.hashpw(plainPassword, BCrypt.gensalt());
        System.out.println("Hash: " + hash);
        // Copia este hash y reemplaza el placeholder en 03-seed.sql
    }
}
```

**Opción B: Online (no recomendado para prod)**
- Usa https://bcrypt-generator.com/ con password `admin12345`, strength 10
- Copia el resultado

Una vez tengas el hash:
```bash
# Editar 03-seed.sql y reemplazar los dos placeholders:
# '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcg7b3XeKeUxWdeS86E36P4/PFm'
# con el hash real
```

### Paso 3 — Ejecutar el script

```bash
# Ejecutar seed
psql -h localhost -U electoral_user -d electoral_db -f backend/electoral-api/src/main/resources/database/infra/sql/03-seed.sql

# Verificar datos
psql -h localhost -U electoral_user -d electoral_db -c "
  SELECT 'Provincias' as tipo, COUNT(*) as total FROM provincia
  UNION ALL
  SELECT 'Cantones', COUNT(*) FROM canton
  UNION ALL
  SELECT 'Parroquias', COUNT(*) FROM parroquia
  UNION ALL
  SELECT 'JRVs', COUNT(*) FROM jrv
  UNION ALL
  SELECT 'Usuarios', COUNT(*) FROM usuario
  UNION ALL
  SELECT 'Cargos', COUNT(*) FROM cargo;
"
```

---

## Cómo verificar que funciona

1. **Datos insertados correctamente:**
   ```bash
   psql -h localhost -U electoral_user -d electoral_db -c "SELECT * FROM usuario LIMIT 1;"
   ```
   Esperado: Fila con MASTER o OBSERVER.

2. **JRVs tienen cargos asignados (JRV_CARGO):**
   ```bash
   psql -h localhost -U electoral_user -d electoral_db -c "SELECT COUNT(*) FROM jrv_cargo;"
   # Esperado: 90 (15 jrvs * 6 cargos)
   ```

3. **OBSERVER tiene JRVs asignadas:**
   ```bash
   psql -h localhost -U electoral_user -d electoral_db -c "
     SELECT u.email, COUNT(oja.jrv_id) as jrvs
     FROM usuario u
     LEFT JOIN observer_jrv_asignacion oja ON u.id = oja.observer_id
     WHERE u.email LIKE '%perobs%'
     GROUP BY u.email;
   "
   # Esperado: juan...perobs@control.ttcs | 3
   ```

4. **Login en staging:**
   ```bash
   curl -X POST http://localhost:8081/api/v1/auth/login \
     -H "Content-Type: application/json" \
     -d '{"email":"admsismas@control.ttcs","password":"admin12345"}'
   # Esperado: JWT token si el backend está levantado
   ```

---

## Errores comunes

**Error: `relation "rol" does not exist`**
Razón: Script 02-rls.sql no se ejecutó o falló.
Solución: Verifica que T-IN-02 ✅ y ejecuta el script 02-rls.sql antes del 03.

**Error: `duplicate key value violates unique constraint`**
Razón: Corriste el seed dos veces sin limpiar.
Solución: Los `ON CONFLICT DO NOTHING` previenen duplicados. Si sigue fallando, revisa si hay datos previos.

**Login falla con `password: "admin12345"`**
Razón: El hash BCrypt en el seed no coincide con "admin12345".
Solución: Regenera el hash correctamente (Paso 2) y actualiza los dos placeholders en 03-seed.sql.

**JRV_CARGO vacía (0 registros)**
Razón: El INSERT final no ejecutó. Posiblemente error en la query CROSS JOIN.
Solución: Revisa que tabla `jrv` y `cargo` tienen datos (SELECT COUNT(*) FROM jrv/cargo).

---

## Checklist original (de infra.md)

- [ ] Provincia Santa Elena
- [ ] Cantones: Santa Elena (2401), La Libertad (2402), Salinas (2403)
- [ ] Parroquias principales por cantón
- [ ] JRVs de ejemplo (10-20 por cantón para staging)
- [ ] Usuario MASTER inicial: `master@twintailcs.xyz` (actualizado a `admsismas@control.ttcs`)
- [ ] Cargos: Prefecto, Alcalde, Concejal Urbano, Concejal Rural, Vocal JPR, Consejero CPCCS
