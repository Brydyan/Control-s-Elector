# T-FE-10: Spec — Coordinator: Gestión Global de Actas

## Escenarios GIVEN/WHEN/THEN

### Escenario 1: Coordinator ve tabla global de actas (todas las JRVs)

```gherkin
GIVEN un Coordinator logueado (sin restricción JRV)
  AND existen 156 actas en BD (45 ENVIADAS, 23 EN_DISPUTA, 78 APROBADAS, 10 ANULADAS)
WHEN navega a /coordinator/actas
THEN debe ver:
  - Tabla cargada en <200ms
  - Primera página (10 registros, default)
  - Estados ENVIADA + EN_DISPUTA + CORREGIDA + APROBADA (ANULADA oculta)
  - Columnas: #ACTA | JRV | CARGO | ESTADO | CHECKSUM | ACCIONES
  - Botones "Anterior" deshabilitados, "Siguiente" habilitado
  - Cada fila tiene dropdown menú ⋮ (Aprobar, Disputar, Corregir, Anular si MASTER)
```

**Mutación M1: ¿Qué pasa si mostramos ANULADA por defecto?**
```bash
# Remover en filtro frontend:
# WHERE estado != 'ANULADA'
# Test falla: tabla muestra actas anuladas (confunde Coordinator)
```

---

### Escenario 2: Coordinator filtra por estado

```gherkin
GIVEN la tabla está cargada con 145 actas (45 ENVIADAS, 23 EN_DISPUTA, 77 APROBADAS)
WHEN el usuario selecciona checkbox "En Disputa" en filtros
  AND clickea botón [Aplicar]
THEN debe:
  - GET /api/v1/coordinator/actas?page=0&size=10&estado=EN_DISPUTA
  - Tabla recarga con 10 actas (página 1 de 3)
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

### Escenario 3: Coordinator busca por número de acta o JRV

```gherkin
GIVEN la tabla contiene 10 actas en página 1
WHEN el usuario escribe "J-1" en input [Buscar acta o JRV]
THEN debe:
  - Filtrar LOCAL (no API call)
  - Tabla muestra solo actas con JRV "J-1" (3 de 10)
  - Resumen: "3 de 10 en esta página"
  - Case-insensitive: "j-1", "J-1", "J1" todos funcionan
```

**Mutación M3: ¿Si la búsqueda es case-sensitive?**
```bash
# Cambiar:
# this.actas().filter(a => a.numeroJrv.toLowerCase().includes(query.toLowerCase()))
# a:
# this.actas().filter(a => a.numeroJrv.includes(query))
# Test falla: "j-1" no encuentra "J-1" (búsqueda rota)
```

---

### Escenario 4: Coordinator aprueba acta (ENVIADA → APROBADA)

```gherkin
GIVEN un Coordinator ve acta #001 con estado ENVIADA
  AND checksum ✓ OK (250 sufragantes = 250 votos)
WHEN clickea dropdown menú ⋮ → "✓ Aprobar"
THEN debe:
  - Modal de CONFIRMACIÓN aparece:
    Acta #001 (Cargo: Presidencia)
    JRV J-1 · 15/11/2026 14:30
    Checksum: ✓ OK (250/250)
    [Cancelar] [Aprobar]
  - Coordinator clickea [Aprobar]
  - PUT /api/v1/coordinator/actas/001/aprobar
  - 200 OK: "Acta APROBADA exitosamente"
  - Tabla recarga: acta #001 estado → APROBADA (verde ✓✓)
  - GET /api/v1/coordinator/actas (reload completo)
```

**Mutación M4: ¿Si no validamos estado=ENVIADA antes de aprobar?**
```bash
# Backend omite:
# if (acta.getEstado() != ENVIADA) throw new BadStateException()
# Test falla: acta APROBADA se aprueba OTRA VEZ (idempotencia rota)
```

---

### Escenario 5: Coordinator disputa acta (ENVIADA → EN_DISPUTA + motivo)

```gherkin
GIVEN un Coordinator ve acta #005 con estado ENVIADA
  AND checksum ✗ INCORRECTO (250 sufragantes ≠ 251 votos)
WHEN clickea dropdown menú ⋮ → "⚠️ Disputar"
THEN debe:
  - Modal DISPUTA aparece:
    Acta #005 (Cargo: Alcalde)
    JRV J-3 · 15/11/2026 16:45
    Checksum: ✗ INCORRECTO (250/251) [diferencia 1 voto]
    
    Motivo (obligatorio):
    [textarea]
    
    [Cancelar] [Disputar]
  - Coordinator deja textarea vacío, clickea [Disputar]
  - Error: "El motivo es obligatorio"
  - Coordinator tipea "Diferencia de 1 voto en blancos"
  - Clickea [Disputar]
  - PUT /api/v1/coordinator/actas/005/disputar
    { "motivo": "Diferencia de 1 voto en blancos" }
  - 200 OK: "Acta puesta EN DISPUTA"
  - Tabla recarga: acta #005 estado → EN_DISPUTA (naranja ⚠️)
  - Motivo visible en tooltip
```

**Mutación M5: ¿Si omitimos validación motivo?**
```bash
# Frontend omite:
# if (!motivo.trim()) { error = 'Obligatorio'; return; }
# Test falla: submit sin motivo (auditoría incompleta)
```

---

### Escenario 6: Coordinator corrige acta (EN_DISPUTA → CORREGIDA)

```gherkin
GIVEN una acta #005 en estado EN_DISPUTA (motivo: "Diferencia de 1 voto")
WHEN Coordinator clickea dropdown menú ⋮ → "📝 Corregir Votos"
THEN debe:
  - Modal CORRECCIÓN aparece con tabla de candidatos:
    Candidato | Votos Originales | Votos Corregidos
    ──────────────────────────────────────────────
    Perez     | 120             | [input: 121]
    Garcia    | 80              | [input: 80]
    Otros     | 49              | [input: 49]
    
    Sufragantes: [input: 250]
    Votos Blancos: [input: 0]
    Votos Nulos: [input: 0]
    
    Suma Votos Corregidos: 250 ✓ OK
    [Cancelar] [Corregir]
  - Coordinator ajusta Perez de 120 → 121
  - Suma actualiza automáticamente: 249 → 250
  - Checksum ahora muestra ✓ OK
  - Coordinator clickea [Corregir]
  - PUT /api/v1/coordinator/actas/005/corregir
    { 
      "jrvId": 3,
      "cargoId": 2,
      "totalSufragantes": 250,
      "votosBlancos": 0,
      "votosNulos": 0,
      "detalles": [
        { "candidatoId": 1, "votos": 121 },
        { "candidatoId": 2, "votos": 80 },
        { "candidatoId": 3, "votos": 49 }
      ]
    }
  - 200 OK: "Acta CORREGIDA y lista para aprobación"
  - Tabla recarga: acta #005 estado → CORREGIDA (amarillo ✓)
```

**Mutación M6: ¿Si no validamos checksum después de corrección?**
```bash
# Omitir:
# if (suma != total_sufragantes) throw ValidationException()
# Test falla: acta corregida con checksum incorrecto (inconsistencia)
```

---

### Escenario 7: MASTER anula acta (APROBADA → ANULADA + motivo)

```gherkin
GIVEN un MASTER logueado
  AND ve acta #120 en estado APROBADA
WHEN clickea dropdown menú ⋮
THEN debe:
  - Opción "✗ Anular (MASTER)" está HABILITADA (rojo, peligroso)
  - COORDINATOR ve botón DESHABILITADO (gris, no clickeable)
  
WHEN MASTER clickea "✗ Anular"
THEN debe:
  - Modal ANULACIÓN aparece:
    ⚠️ ACCIÓN IRREVERSIBLE
    ────────────────────────
    Acta #120 (Cargo: Concejalía)
    JRV J-10 · 15/11/2026 18:00
    Estado actual: APROBADA
    
    Motivo (obligatorio):
    [textarea] Ej: "Datos verificados como fraudulentos"
    
    [Cancelar] [Anular]
  - MASTER tipea motivo
  - Clickea [Anular]
  - DELETE /api/v1/coordinator/actas/120
    { "motivo": "Datos verificados como fraudulentos" }
  - 204 NO CONTENT
  - Tabla recarga: acta #120 DESAPARECE o estado → ANULADA (rojo ✗)
  - Toast: "Acta #120 ANULADA por: [motivo]"
```

**Mutación M7: ¿Si permitimos que COORDINATOR anule?**
```bash
# Omitir rol check:
# @PreAuthorize("hasRole('MASTER')")
# Test falla: COORDINATOR puede anular actas (SEGURIDAD VIOLADA)
```

---

### Escenario 8: Paginación entre páginas

```gherkin
GIVEN tabla cargada, página 1 de 5 (10 actas/página, 45 total ENVIADAS)
WHEN clickea botón [Siguiente]
THEN debe:
  - GET /api/v1/coordinator/actas?page=1&size=10&estado=ENVIADA
  - Tabla recarga con registros 11-20
  - Botón "Anterior" ahora está HABILITADO
  - Contador: "Página 2 de 5"
  - URL actualiza: /coordinator/actas?page=1
```

**Mutación M8: ¿Si paginación offset es 0-based vs 1-based?**
```bash
# Frontend envía page=1 pero backend interpreta como page=0
# Resultado: saltar acta 11 o mostrar duplicadas
# Test falla: datos duplicados/faltantes entre páginas
```

---

### Escenario 9: 409 Conflict — dos Coordinators aprueban concurrentemente

```gherkin
GIVEN dos Coordinators (A y B) ven la misma acta #050 en ENVIADA
WHEN Coordinator A clickea [Aprobar] y envía PUT /coordinator/actas/050/aprobar
  AND Coordinator B clickea [Aprobar] simultáneamente y envía PUT /coordinator/actas/050/aprobar
THEN debe:
  - Coordinator A obtiene 200 OK: estado → APROBADA (gana)
  - Coordinator B obtiene 409 CONFLICT: "Acta ya aprobada por otro usuario"
  - Toast: "⚠️ Conflicto: otro Coordinator cambió esta acta"
  - Tabla Coordinator B recarga automáticamente
  - Acta #050 ahora muestra estado APROBADA (reflejando realidad)
  - Ambas transiciones auditadas con timestamps exactos
```

**Mutación M9: ¿Si omitimos reload en 409?**
```bash
# Omitir:
# if (err.status === 409) cargarActas();
# Test falla: Coordinator B ve acta #050 en ENVIADA (state mismatch)
```

---

### Escenario 10: Acceso denegado (no COORDINATOR / MASTER)

```gherkin
GIVEN un OBSERVER logueado (no tiene rol COORDINATOR)
WHEN intenta navegar a /coordinator/actas
THEN debe:
  - roleGuard rechaza acceso: 403 Forbidden
  - Redirige a /dashboard
  - Toast: "No tienes permiso para ver este contenido"
```

**Mutación M10: ¿Si omitimos roleGuard en ruta?**
```bash
# Remover en app.routes.ts:
# canActivate: [roleGuard]
# data: { roles: ['COORDINATOR', 'MASTER'] }
# Test falla: OBSERVER puede ver tabla (SEGURIDAD VIOLADA)
```

---

## Mutaciones — Validar Test Cae

| ID | Descripción | Implementación | Test Falla |
|----|-----------|--------------------|------------|
| M1 | Mostrar ANULADA por defecto | Remover filtro WHERE estado != ANULADA | Tabla muestra anuladas (confunde) |
| M2 | Omitir GET al filtrar | Remover service.fetchByFilter() | Tabla stale, no actualiza |
| M3 | Búsqueda case-sensitive | filter(a => a.numeroJrv.includes(q)) | "j-1" no encuentra "J-1" |
| M4 | No validar estado=ENVIADA | Omitir state check en backend | Acta APROBADA se aprueba 2x |
| M5 | Motivo DISPUTA opcional | Remover validación motivo | Submit sin motivo (auditoría rota) |
| M6 | No validar checksum post-corregir | Omitir suma=total check | Acta corregida con checksum roto |
| M7 | COORDINATOR puede anular | Remover @PreAuthorize("MASTER") | COORDINATOR anula actas (security) |
| M8 | Offset 0-based vs 1-based | Backend interpreta page=1 como page=0 | Duplicados/faltantes entre páginas |
| M9 | No reload en 409 Conflict | Omitir cargarActas() en error 409 | State mismatch (tabla stale) |
| M10 | Omitir roleGuard | Remover canActivate: [roleGuard] | OBSERVER ve tabla (security) |

---

## Criterios de Cobertura

**Cobertura mínima:** 70% (unit tests)

- ActasTableComponent.renderRows: 8 tests (estados, checksum, acciones)
- ActasFiltersComponent: 6 tests (cambiar estado, cargo, JRV, fecha)
- ModalAprobarComponent: 4 tests (mostrar, validar, submit, error)
- ModalDisputarComponent: 5 tests (motivo obligatorio, submit, error)
- ModalCorregirComponent: 5 tests (suma checksum, validar, submit)
- ModalAnularComponent: 3 tests (MASTER-only, submit, error)
- ActasListaPage: 5 tests (carga inicial, filtros, paginación, 409 conflict)

**E2E:** 5 journeys críticos
1. Tabla carga + filtra + pagina
2. Aprobar acta ENVIADA
3. Disputar acta + corregir + aprobar
4. Anular acta (MASTER-only)
5. 409 Conflict handling

**Backend:** Integration tests (CoordinatorActasContractIT.java)
1. GET /coordinator/actas devuelve página con filtros
2. PUT /aprobar valida estado, registra audit
3. PUT /disputar valida motivo
4. PUT /corregir valida checksum
5. DELETE /anular (MASTER-only)
6. 409 Conflict en race conditions
