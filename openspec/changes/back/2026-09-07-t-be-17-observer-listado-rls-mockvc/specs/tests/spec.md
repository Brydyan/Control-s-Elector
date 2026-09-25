# Spec: T-BE-17 — Prueba MockMvc RLS listado OBSERVER

---

## RF-TEST-04-H1: OBSERVER B no ve actas de OBSERVER A en listado

**Requisito**: Cuando OBSERVER B consulta `GET /api/v1/observer/mis-actas`, el resultado contiene solo actas registradas por B (RLS filtrada en HTTP).

### Scenario

```gherkin
Given:
  - Provincia "Santa Elena" existe
  - JRV-1 existe en canton A
  - JRV-2 existe en canton A
  - Cargo "Prefecto" (nivel PROVINCIA) asignado a ambas JRVs
  - OBSERVER-A: usuario ID=1001, asignado a JRV-1
  - OBSERVER-B: usuario ID=1002, asignado a JRV-2
  - Acta de Prefecto registrada por A en JRV-1
  - Acta de Prefecto registrada por B en JRV-2

When:
  - OBSERVER-B se autentica (JWT o @WithMockUser)
  - OBSERVER-B consulta GET /api/v1/observer/mis-actas (sin estado)

Then:
  - HTTP 200 OK
  - Response: List con 1 acta (la de JRV-2, registrada por B)
  - Acta de A (JRV-1, registrada por A) NO aparece
  - Assert: response.size() == 1 AND response[0].jrvId == 101 (JRV-2)
```

### Entrada

```http
GET /api/v1/observer/mis-actas
Authorization: Bearer <JWT de OBSERVER-B>
Content-Type: application/json
```

### Salida esperada

```json
[
  {
    "id": 2,
    "publicId": "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
    "jrvId": 101,
    "cargoId": 1,
    "estado": "ENVIADA",
    "registradoPor": {
      "id": 1002,
      "nombre": "observer-b"
    },
    ...
  }
]
```

### Notas

- La consulta se ejecuta con `registradaPorId = 1002` (id de B)
- JPQL filter: `(:registradaPorId IS NULL OR acta.registradoPor.id = :registradaPorId)`
- Fila de acta de A tiene `registradoPor.id = 1001`, no pasa filtro
- Resultado: solo acta de B

---

## RNF-TEST-01: Test usa MockMvc + Spring Security

**Requisito**: El test simula una HTTP request real con SecurityContext propagado (no mock de repository).

### Criterios

- ✅ Test clase: `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `@AutoConfigureMockMvc`
- ✅ `@WithMockUser` o `@WithMockJWT` propaga SecurityContext
- ✅ `mockMvc.perform(get(...))` vs `repository.findWithFilters()` (test HTTP layer, no repository layer)
- ✅ Assert en respuesta JSON (HTTP serialization)

---

## RNF-TEST-02: Datos de prueba aislados y deterministas

**Requisito**: Cada test empieza limpio y puede ejecutarse N veces sin falla.

### Criterios

- ✅ `@BeforeEach` setup crea datos nuevos (no hardcoded IDs)
- ✅ Datos se limpian después (transacción de test rollback automático)
- ✅ IDs de usuarios son únicos (randomizados o incrementales)
- ✅ JRVs y cargos específicos para el test, sin reutilizar datos globales

---

## RNF-TEST-03: Mutación verifica el filtro

**Requisito**: Remover la guardia de `registradaPorId` debe hacer que el test falle.

### Criterios

- ✅ Archivo: `ActaRepository.findWithFilters()`
- ✅ JPQL: `(:registradaPorId IS NULL OR acta.registradoPor.id = :registradaPorId)`
- ✅ Acción: remover esta cláusula OR
- ✅ Esperado: test cae porque OBSERVER-B ahora ve actas de A (response.size() == 2, no 1)
- ✅ Resultado medido: nombre exacto del test que cayó anotado en `apply-progress.md`

---

## Entrega

- `backend/electoral-api/src/test/java/.../acta/web/ObserverControllerRlsIT.java`
  - 1 test: `observerBNoVeActasDelObserverA()`
  - 1 mutación: C.1 (remove registradaPorId guard)
  - ~80 líneas

- `apply-progress.md`
  - Gate 0: 291 tests (baseline desde T-BE-15)
  - Gate 1: 292 tests (+1)
  - Mutación C.1: "observerBNoVeActasDelObserverA cayó cuando quité `:registradaPorId` filter"
