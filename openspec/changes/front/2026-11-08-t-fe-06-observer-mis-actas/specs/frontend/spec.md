# T-FE-06: Spec — Observer: Lista de Actas Propias

## Escenarios GIVEN/WHEN/THEN

### Escenario 1: Observer ve sus actas transmitidas

```gherkin
GIVEN un Observer logueado con JRV asignada (J-1)
  AND tiene 15 actas en BD (10 transmitidas, 3 en revisión, 2 rechazadas)
WHEN navega a /observer/mis-actas
THEN debe ver:
  - Tabla cargada en <200ms
  - Primera página (10 registros)
  - Solo TRANSMITIDAS + EN_REVISION (rechazadas ocultas)
  - Columnas: #ACTA | JRV | FECHA | ESTADO | ACCIÓN
  - Botones "Anterior" deshabilitados, "Siguiente" habilitado
```

**Mutación M1: ¿Qué pasa si no filtramos rechazadas?**
```bash
# Remover en backend:
# WHERE estado != 'RECHAZADO'
# Test falla: Observer ve acta rechazada (SEGURIDAD VIOLADA)
```

---

### Escenario 2: Observer filtra por estado

```gherkin
GIVEN la tabla está cargada con 13 actas (10 transmitidas, 3 revisión)
WHEN el usuario selecciona checkbox "En revisión" en filtros
  AND clickea botón [Aplicar]
THEN debe:
  - GET /api/v1/observer/mis-actas?estado=REVISION
  - Tabla recarga con 3 actas
  - Paginación reset a página 1
  - Estado visual del checkbox persiste (visual feedback)
```

**Mutación M2: ¿Si no hacemos GET al cambiar filtro?**
```bash
# Omitir:
# this.actasService.fetchByFilter(filters).subscribe(...)
# Test falla: tabla muestra datos stale, no actualiza
```

---

### Escenario 3: Observer busca por número de acta

```gherkin
GIVEN la tabla contiene 10 actas (#001, #002, ..., #042, ...)
WHEN el usuario escribe "042" en input [Buscar acta]
THEN debe:
  - Filtrar LOCAL (no API call)
  - Tabla muestra solo #042
  - Resumen: "1 de 10"
  - Case-insensitive: "042", "042", "#042" todos funcionan
```

**Mutación M3: ¿Si la búsqueda es case-sensitive?**
```bash
# Cambiar:
# this.actas.filter(a => a.numero.toLowerCase().includes(query.toLowerCase()))
# a:
# this.actas.filter(a => a.numero.includes(query))
# Test falla: "042" no encuentra "#042" (búsqueda rota)
```

---

### Escenario 4: Master ve todas las actas (sin filtro JRV)

```gherkin
GIVEN un Master logueado (sin JRV específica)
WHEN navega a /observer/mis-actas
THEN debe:
  - GET /api/v1/observer/mis-actas (sin jrvId param)
  - Ver actas de TODAS las JRVs
  - Filtro JRV disponible y útil (dropdown muestra +1 opciones)
```

**Mutación M4: ¿Si MASTER también ve filtro JRV hardcoded?**
```bash
# Backend:
# WHERE jrv_id = :jrvId  (always aplica)
# Master:
# 403 Forbidden (no tiene jrvId)
# Test falla: Master no puede ver actas
```

---

### Escenario 5: Paginación entre páginas

```gherkin
GIVEN tabla cargada, página 1 de 5 (10 actas/página)
WHEN clickea botón [Siguiente]
THEN debe:
  - GET /api/v1/observer/mis-actas?page=1&size=10
  - Tabla recarga con registros 11-20
  - Botón "Anterior" ahora está HABILITADO
  - Contador: "Página 2 de 5"
```

**Mutación M5: ¿Si paginación offset es 0-based vs 1-based?**
```bash
# Backend retorna page=0 pero frontend interpreta como page=1
# Resultado: saltar acta 11 o mostrar duplicadas
# Test falla: datos duplicados/faltantes entre páginas
```

---

### Escenario 6: Acta sin JRV asignada → 403

```gherkin
GIVEN un Observer sin JRV asignada (null)
WHEN llama GET /api/v1/observer/mis-actas
THEN debe:
  - Backend retorna 403 Forbidden
  - Frontend redirige a /dashboard
  - Toast: "No tienes JRVs asignadas"
```

**Mutación M6: ¿Si el backend no valida jrvId?**
```bash
# Omitir en service:
# if (usuario.jrvId == null) throw new ForbiddenException()
# Test falla: Observer sin JRV ve actas (SEGURIDAD VIOLADA)
```

---

### Escenario 7: Fecha en formato correcto (timezone)

```gherkin
GIVEN una acta transmitida: 2026-11-08T15:30:00Z (UTC)
WHEN se muestra en tabla (usuario en ECT = -5:00)
THEN debe:
  - Mostrar: "15:30 -05:00" o "15:30 ECT" (NOT UTC)
  - Formato: DD/MM/YYYY HH:mm
  - Ejemplo: "08/11/2026 15:30"
```

**Mutación M7: ¿Si no convertimos timezone?**
```bash
# Mostrar UTC directo: "20:30 UTC"
# Test falla: user ve hora incorrecta (+5h diferencia)
```

---

### Escenario 8: Tabla vacía (user sin actas)

```gherkin
GIVEN un Observer sin actas transmitidas aún
WHEN navega a /observer/mis-actas
THEN debe:
  - Tabla vacía con mensaje: "No tienes actas aún"
  - EmptyStateComponent muestra icono document-outline
  - Botón "Volver" disponible
```

**Mutación M8: ¿Si no manejamos resultado vacío?**
```bash
# Omitir: if (actas.length === 0) showEmptyState()
# Test falla: tabla con headers pero 0 rows (confusa para user)
```

---

### Escenario 9: Accesibilidad: navegación con teclado

```gherkin
GIVEN tabla renderizada con 3 actas
WHEN user presiona TAB repetidamente
THEN debe:
  - Focus visible en filtros → tabla → botones
  - Enter/Space: activar filtro o botón acción
  - Arrow Down: navegar filas (si implementado)
  - Screen reader anuncia: "Tabla de actas, 3 filas, columnas: #Acta, JRV, Fecha, Estado, Acción"
```

**Mutación M9: ¿Si tabla sin ARIA roles?**
```bash
# Omitir:
# <table role="table">
# <thead role="rowgroup">
# <tbody role="rowgroup">
# <tr role="row">
# Test falla: screen reader no entiende estructura (WCAG AA violation)
```

---

### Escenario 10: Performance bajo carga (100 actas)

```gherkin
GIVEN backend pagina: max 50 actas por request
WHEN user navega 100 actas (2 páginas)
  AND carga cada página
THEN debe:
  - Primera carga: <200ms
  - Cambio página: <100ms
  - Tabla es scrolleable, no scrolls document (sticky header)
```

**Mutación M10: ¿Si cargamos todas las actas (100) de una vez?**
```bash
# Backend: size=100 (sin paginar)
# Frontend: <500 rows en tabla
# Resultado: Rendered ~100 DOM nodes → render lag 500ms+
# Test falla: performance test timeout
```

---

## Cobertura de Mutaciones

| # | Descripción | Test Expected | Type |
|----|------------|--------------|------|
| M1 | Mostrar actas rechazadas | debe fallar | security |
| M2 | Omitir GET al filtrar | debe fallar | functional |
| M3 | Búsqueda case-sensitive | debe fallar | functional |
| M4 | MASTER filtro JRV hardcoded | debe fallar | security |
| M5 | Paginación offset error | debe fallar | functional |
| M6 | Sin validar jrvId | debe fallar | security |
| M7 | Timezone no convertir | debe fallar | functional |
| M8 | Sin empty state | debe fallar | UX |
| M9 | Sin ARIA roles | debe fallar | accessibility |
| M10 | Cargar 100 items | debe fallar | performance |

---

## Criterios de Éxito

Todos los escenarios PASS + todas las mutaciones FALL.

```
PASS: Escenarios 1-10 ejecutan sin errores
FALL: Mutaciones M1-M10 causan test failures esperadas
✅ SDD #506 COMPLETA cuando todos los tests pasan
```

---

## Notas de Implementación

- Usar Vitest para unit tests (componentes, service)
- Usar Playwright para E2E (flujo completo)
- Mocks de API: msw (Mock Service Worker) para simular backend
- Fixtures: usuario.observer.role + 15 actas en estado TRANSMITIDO/REVISION
