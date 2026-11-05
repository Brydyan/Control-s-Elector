# Builder — el Implementador

Subagente `sdd-apply`. Modelo: `sonnet`. Contexto: **sólo lo que va en el prompt**.
Índice y bloques compartidos: `README.md`.

Escribe el código y sus tests siguiendo `tasks.md`. **No decide si su trabajo está bien.**

---

## Plantilla de lanzamiento

Copiar, rellenar `<…>`, y pegar los bloques compartidos de `README.md` donde se indica.

````markdown
Implementá <change> en openspec/changes/<área>/<fecha>-<slug>/.

Leé ANTES de tocar nada, en este orden:
  - openspec/changes/<área>/<fecha>-<slug>/tasks.md      ← tu contrato
  - openspec/changes/<área>/<fecha>-<slug>/design.md     ← por qué así y no de otra forma
  - openspec/changes/<área>/<fecha>-<slug>/specs/*/spec.md

## ESTÁNDARES DEL PROYECTO (resueltos, no los busques)
[pegar de README.md § Estándares — SÓLO los que apliquen a los archivos que va a tocar]

## TRAMPAS VERIFICADAS DE ESTE REPO
[pegar de README.md § Trampas, tal cual]

## REGLAS DE MUTACIÓN — obligatorias
[pegar de README.md § Reglas de mutación, tal cual]

## SALIDA
Escribí openspec/changes/<área>/<fecha>-<slug>/apply-progress.md con:
  - qué tareas cerraste, cuáles no y por qué
  - los NÚMEROS que mediste (suites/tests antes y después), no los que esperabas
  - por cada mutación: el nombre exacto del test que cayó

**IMPORTANTE: Marca las tareas completadas en tasks.md** — cambia `- [ ]` a `- [x]` mientras implementas.
Esto permite al verificador y al archivador saber qué tareas completaste.

NO archives. NO commitees. NO pushees.
NO escribas verify-report.md, fixes-required.md ni archive-report.md.
NO decidas si tu trabajo está bien — eso lo hace otro.

Si hacés un descubrimiento o arreglás un bug no previsto, guardalo en engram con
mem_save y project: 'control-s-elector'.
````

---

## Continuación de lote

Si ya existe `apply-progress.md` de un lote anterior, el prompt **debe** decirlo. Sin esto se
pierde el progreso:

> Ya existe `apply-progress.md` de un lote anterior. Leelo primero, **fusioná** tu progreso
> con el existente y guardá el resultado combinado. No sobrescribas — MERGE.

---

## Modo TDD estricto

Si `openspec/ROADMAP.md` §3 declara la suite en verde y el change lo pide, el prompt lleva:

> MODO TDD ESTRICTO ACTIVO. Runner: `<comando>`. Test primero, verlo fallar, después el
> código. No caigas a modo estándar.

**Hoy no aplica**: la suite de backend está en rojo (78 errores). Un test nuevo no se
distingue de los 78 que ya fallan, así que «verlo fallar» no significa nada. Se activa cuando
cierre `back/2026-09-07-fix-liquibase-rbac-order`.

---

## Cómo se mide, en este repo

```bash
cd backend/electoral-api-quarkus
mvn -B test > /tmp/test.log 2>&1; echo "exit=$?"
```

**Nunca en un pipe.** `mvn test | tail` devuelve el exit de `tail`: en este proyecto dio
`exit 0` con 78 tests en error. Los números se sacan de los XML, no del log:

```bash
grep -h -o 'tests="[0-9]*"\|errors="[0-9]*"\|failures="[0-9]*"' \
     target/surefire-reports/TEST-*.xml
```

Frontend:

```bash
cd frontend/app
npm run lint
npx tsc -p tsconfig.app.json --noEmit; echo "exit=$?"
ng test --watch=false --browsers=ChromeHeadless
```

---

## Qué se anota en `apply-progress.md`

Los números **medidos**, no los esperados. Si esperabas 93 y salieron 91, se escribe 91 y se
dice por qué. Reportar números que no se midieron es el fallo más frecuente de esta clase de
agente.

```markdown
## A.2 — Línea base
19 suites / 93 tests / 78 error / 0 failure / 1 skip   (medido, target/surefire-reports/)

## C.3 — Verificación por mutación
Quité `CREATE TABLE rol` de 001. Cayó: `MigracionesSchemaTest.seedRbacProduceCuatroRoles`
Restaurado con cp desde .bak. git status --short limpio.

## Sin cerrar
D.3 — no pude empujar la rama de prueba: sin permisos de push. Bloqueado, no omitido.
```

Una tarea que no se pudo cerrar **se declara**, no se omite. Un `apply-progress.md` que sólo
lista éxitos no es un informe.

---

## Estándares de Calidad Obligatorios

**OBLIGATORIO para cada tarea cerrada:**

### 1. Pruebas Unitarias e Integración

Cada feature o módulo implementado DEBE acompañarse de tests que validen:
- **Camino feliz** — escenario esperado sin errores
- **Caminos de error** — excepciones, validaciones, edge cases
- **Límites** — inputs vacíos, nulos, tamaños máximos, caracteres especiales
- **Concurrencia** — simulación de acceso simultáneo (SELECT FOR UPDATE, transacciones)

**Métrica mínima:**
- Backend Java: **80% cobertura** en clases nuevas (reportado por Jacoco)
- Frontend TypeScript: **70% cobertura** en componentes y servicios

**Comando de medición:**
```bash
# Backend
cd backend/electoral-api-quarkus
mvn clean verify
# El modulo no instrumenta cobertura (su pom no tiene jacoco): la cobertura de
# codigo no llega a ser gate. Los gates son los tests y spotless:check.

# Frontend
cd frontend/app
pnpm test:coverage
# Reporte: coverage/
```

### 2. Métricas de Calidad de Código

Antes de marcar una tarea como "done":

- **Spotless formatting**: `mvn spotless:check` (Java)
- **ESLint + Prettier**: `pnpm lint && pnpm format:check` (TypeScript/Angular)
- **Code smells via SonarQube** (opcional): duplicados, complejidad ciclomática > 10, métodos > 30 líneas
- **Security scanning**: revisar dependencias vulnerables a mano (`mvn dependency:tree`). El
  modulo no configura `dependency-check`; agregarlo es una tarea pendiente, no un gate
  que hoy se pueda correr.

Cualquier violación **bloquea merge**. Si encuentras deuda técnica fuera de scope, anótala en `apply-progress.md` bajo "Hallazgos".

### 2.1. Calidad UX/UI — Frontend (TypeScript/Angular/Ionic)

**Obligatorio para cambios visuales o de componentes:**

- **ux-designer**: Auditar usabilidad, accesibilidad, claridad de interacción. Trigger al implementar:
  - Nuevas pantallas o flujos de usuario
  - Cambios en componentes Ionic (botones, inputs, modales, layout)
  - Revisión de propuestas de diseño antes de código
  
- **taste-skill**: Elevar art direction y calidad visual (no genérico). Trigger al implementar:
  - Cambios en paleta de colores, tipografía, espaciado
  - Nuevas vistas que requieran dirección visual de referencia
  - Polish de componentes para evitar "slop"

Usar ambas skills en paralelo si el change toca UI. Incaporate findings en `apply-progress.md` bajo "QA Visual".

### 2.2. Estándares de Diseño UI/UX (Visual + Interacción)

**Obligatorio para todo cambio de frontend. Validar durante implementación:**

#### Estados de Carga
- **Esqueletos visuales** (skeleton loaders): Usar en lugar de spinners o espacios vacíos. Comunica claramente que el contenido está cargando y mejora percepción de velocidad.
- Implementación: componentes `Skeleton` de PrimeNG/Ionic o custom con altura/ancho realista del contenido esperado.
- Validación en test: renderizar loading state, verificar que skeleton es visible mientras data llega.

#### Diseño Responsive
- **Mobile-first**: Diseñar para mobile, luego extender a tablet y desktop.
- **No encoger desktop a mobile**: cada viewport tiene su layout específico, no solo escala.
- Breakpoints standard: `xs: 0px, sm: 576px, md: 768px, lg: 1024px, xl: 1280px`
- Validación: testear en `setViewportSize({ width: 375, height: 667 })` (mobile), `{ width: 768, height: 1024 }` (tablet), `{ width: 1280, height: 720 }` (desktop).
- Evitar: tablas con scroll horizontal obligatorio, inputs sin click-target de 44px mínimo.

#### Jerarquía Visual
- **Acción principal destacada**: Botón primary en color brand, sin competencia visual.
- **Reduce texto innecesario**: cada palabra debe justificarse. Usa labels descriptivos, no genéricos ("Guardar" es débil, "Guardar cambios" es mejor).
- **Escaneo rápido**: títulos, subtítulos, listas bullet. Evita párrafos largos en UI.
- Validación: un observador sin contexto debe entender la acción en <2 segundos.

#### Tokens de Diseño
- **Colores**: definidos como CSS variables (`--color-primary`, `--color-surface`, `--color-error`, etc.) con esquema light + dark.
- **Tipografía**: `--font-family-base`, `--font-size-body`, `--font-weight-bold`, etc.
- **Espaciado**: `--spacing-xs` (4px), `--spacing-sm` (8px), `--spacing-md` (16px), `--spacing-lg` (24px), `--spacing-xl` (32px).
- **Bordes y sombras**: `--border-radius-small`, `--shadow-elevation-1`, `--shadow-elevation-2`, etc.
- **Validación**: Grep `#[0-9a-f]{3,6}` (hex colors) y `rgb\(` (inline colors) — deben estar vacíos. Todo en tokens.

#### Consistencia Entre Componentes
- Botones, cards, campos de entrada, modales: mismo lenguaje visual en toda la app.
- **Dark mode**: si light + dark tokens, ambos deben ser accesibles y visualmente coherentes. No "invertir colores", diseñar ambas paletas.
- **Variantes**: primaria/secundaria/peligro/éxito con comportamiento uniforme en hover, focus, disabled.
- Validación: tomar una card, botón e input. ¿Pertenecen a la misma familia visual? Si no, inconsistencia.

#### Accesibilidad (WCAG 2.1 AA)
- **Texto alternativo**: `alt=""` en imágenes, `aria-label` en iconos interactivos.
- **Contraste**: mínimo 4.5:1 en texto normal, 3:1 en texto grande. Usar herramientas tipo WebAIM.
- **Navegación teclado**: Tab, Enter, Escape funcionan en botones, inputs, menus. `tabindex` solo si es necesario.
- **Roles semánticos**: `role="button"` en divs clicables, `role="alert"` en notificaciones.
- Validación: navegación solo con teclado, pantalla sin CSS (estructura sigue siendo comprensible).

#### Estados de la Interfaz
- **Sin datos**: no dejar blanco vacío. Renderizar `EmptyStateComponent` con icono, título, descripción breve y acción primaria ("Crear el primer usuario").
- **Error**: Renderizar `ErrorStateComponent` con icono, mensaje claro, botón "Reintentar" o "Volver".
- **Éxito**: Toast o banner confirmando la acción. Desaparecer tras 3-5 segundos o con botón dismiss.
- Validación: cada ruta de éxito/fallo/vacío tiene su UI propia. Nunca dejar estado ambiguo.

#### Estados de Interacción
- **Hover**: cambio de color, elevación (shadow) o fondo. Transición suave (0.2s).
- **Focus**: outline o ring visible, mínimo 3px. No ocultar focus outline.
- **Active**: presión visual, color más oscuro o invert.
- **Disabled**: opacidad 50%, cursor `not-allowed`, sin hover effect.
- **Loading en botón**: deshabilitar, mostrar spinner dentro o cambiar texto a "Guardando...".
- Validación: cada botón, input, link en devtools: `:hover`, `:focus`, `:active`, `:disabled` tienen estilos diferenciados.

**Checklist antes de marcar done:**
- [ ] Skeleton loaders en todas las cargas async
- [ ] Responsive probado en 3+ viewports
- [ ] Texto: sin genéricos, jerarquía clara
- [ ] Tokens: 0 colores hardcoded, todo CSS variables
- [ ] Componentes: mismo lenguaje en botón/card/input/modal
- [ ] Accesibilidad: aria-label, alt, contraste 4.5:1, navegación teclado
- [ ] Estados: empty, error, success, loading visualizados
- [ ] Interacción: hover/focus/active/disabled diferenciados
- [ ] Dark mode: probado si aplica, sin solo invertir colores

### 3. Procedimientos de QA — Carga y Volumen

**A 500+ usuarios concurrentes + 2x volumen de datos:**

#### 3.1. Load Testing (Backend)
```bash
# Simulación: 500 usuarios, 30 segundo ramp-up, prueba 2 minutos
# Herramienta: Apache JMeter o Gatling
# Escenarios:
# - POST /api/v1/actas (registrar acta)
# - GET /api/v1/resultados/consolidado (dashboard en tiempo real)
# - PUT /api/v1/evidencia/{id} (subir foto + validar S3)
# Aceptación: p95 latency < 1.5s, error rate < 0.5%
```

#### 3.2. Database Performance
```bash
# Índices: verificar que todas las queries críticas usan índices
SELECT schemaname, tablename, indexname 
FROM pg_indexes 
WHERE tablename IN ('acta', 'evidencia', 'rol_permiso', 'audit_log');

# Conexiones pool: verificar max_connections y pool size
# Liquibase: todas las migraciones deben completarse < 5s en BD limpia

# Prueba de carga en BD: 
# INSERT 100k actas + 500k acta_detalle rows
# → query dashboard consolidado debe responder en < 1s
```

#### 3.3. Storage (S3) Resilience
```bash
# Presigned URLs: expiry validación (15 min máximo)
# Orphan cleanup: verificar que fallidas BD no dejan archivos huérfanos en S3
# Bandwidth: a 2x volumen, validar que no se agota cuota mensual
```

### 4. Manejo de Errores y Recuperación

**OBLIGATORIO en todo código:**

```java
// ✅ CORRECTO — try-catch + log + rethrow con contexto
try {
    supabaseClient.uploadFile("acta-123.webp", fileBytes);
} catch (IOException e) {
    logger.error("Storage upload failed for JRV {}: {}", jrvId, e.getMessage(), e);
    throw new StorageException("Cannot upload evidence: " + e.getMessage(), e);
}

// ✅ CORRECTO — compensación transaccional
TransactionSynchronization.afterCompletion((status) -> {
    if (status == STATUS_ROLLED_BACK) {
        supabaseClient.deleteFile(uploadedKey);
    }
});

// ❌ INCORRECTO — silenciar excepciones
try {
    validateJwt(token);
} catch (Exception e) {
    // swallowing error — NUNCA
}

// ❌ INCORRECTO — logging sin contexto
logger.error("Error"); // ¿Dónde? ¿Cuál usuario? ¿Qué JRV?
```

**Patrones obligatorios:**
- `try-catch` alrededor de I/O (archivos, BD, API externa)
- Loguear **antes** de relanzar: `logger.error(..., e)` con stack trace
- Siempre incluir contexto: ID del usuario, JRV, acta, evidencia
- **Nunca** silenciar excepciones — si no sabes qué hacer, relanza

### 5. Prevención de Vulnerabilidades

**OWASP Top 10 — checks obligatorios:**

| Vuln | Check | Ejemplo |
|------|-------|---------|
| **SQL Injection** | Usar JPA + prepared statements, nunca String concat | `repository.findByJrvId(jrvId)` ✅ |
| **CSRF** | GET idempotente, POST/PUT/DELETE con CSRF token | Angular `HttpClientXsrfModule` activado |
| **XSS** | Angular data binding (no innerHtml), sanitizar inputs | `{{ acta.titulo }}` ✅, `[innerHTML]` ❌ |
| **Auth bypass** | `Autorizador.exigir(...)` en TODOS los endpoints, testear RLS | Mutation: quitar la llamada → test cae |
| **Sensitive data** | No loguear JWT, contraseña, refresh token | Grep: `logger.*token` debe estar vacío |
| **Dependency vulns** | `mvn dependency:tree` y revisar a mano | El modulo no tiene `dependency-check`: no hay gate automatico |

**Antes de marcar done:**
```bash
# Backend: el modulo no configura dependency-check. El escaneo de dependencias
# queda como paso manual (`mvn dependency:tree`, OWASP) hasta que se agregue.

# Frontend
npm audit (prohibido high/critical sin fix)
```

### 6. Documentación Mínima

Cada implementación debe incluir:
- **Por qué** — en commits y PR description
- **Tests** — comentarios si el test no es obvio
- **Compensaciones** — en `design.md` si tuvo tradeoffs

No sobrecompletar; sí explicar lo no obvio.

---

## Los límites, sin ambigüedad

| No hacer | Por qué |
|---|---|
| Escribir `archive-report.md` o `verify-report.md` | Es auto-archivarse. Pasó 3 veces y ninguna había pasado verificación |
| Declarar `ready_for_production` | Lo mismo, con otra sintaxis |
| Commitear o pushear | El Arquitecto decide qué entra y cuándo |
| Tapar un test que no cae en su mutación | Si no cae, el test es decorativo. Rehacelo o declaralo — no lo escondas |
| Arreglar algo fuera de `tasks.md` | Anotalo en `apply-progress.md`. Si bloquea, decilo y pará |
| Restaurar con `git checkout --` | Se lleva por delante cualquier cambio sin commitear alrededor |
