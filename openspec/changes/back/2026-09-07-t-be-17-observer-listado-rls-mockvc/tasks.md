# Tasks: 2026-09-07-t-be-17-observer-listado-rls-mockvc

Origen: WARNING de T-BE-15 verificación  
Rama: `brydyan/sc-161/t-be-17-observer-listado-rls-mockvc`, desde `develop`

**Bloqueado por**: Nada. Puede empezar ahora.

---

## A · Línea base y preparación

- [ ] **A.1** — `git status --short` limpio. Medir línea base:

  ```bash
  cd backend/electoral-api
  ./mvnw -B clean test > /tmp/base.log 2>&1; echo "exit=$?"
  ```

  Contar desde `target/surefire-reports/*.xml`. Esperado: 291 tests (baseline T-BE-15).

- [ ] **A.2** — Crear clase test: `backend/electoral-api/src/test/java/ec/twintailcs/electoral/acta/web/ObserverControllerRlsIT.java`

  Anotaciones:
  - `@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)`
  - `@AutoConfigureMockMvc`
  - Inyectar: `@Autowired MockMvc mockMvc`, repositorios, servicios para setup

- [ ] **A.3** — Crear `@BeforeEach setup()` que:
  - Crea Provincia ("Santa Elena")
  - Crea Cantón ("La Libertad")
  - Crea Parroquia ("Central")
  - Crea 2 JRVs (id=100, id=101)
  - Crea Cargo (Prefecto, nivel PROVINCIA)
  - Crea 2 Usuarios OBSERVER (observer-a, observer-b)
  - Crea 2 `observer_jrv_asignacion`: A→JRV-1, B→JRV-2
  - Crea 2 Actas: A registra en JRV-1, B registra en JRV-2

---

## B · El test

- [ ] **B.1** — Test `observerBNoVeActasDelObserverA()`:

  ```java
  @Test
  @WithMockUser(username="observer-b-publicid", roles="OBSERVER")
  void observerBNoVeActasDelObserverA() throws Exception {
    mockMvc.perform(get("/api/v1/observer/mis-actas"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$", hasSize(1)))
      .andExpect(jsonPath("$[0].jrvId", is(101)));  // solo JRV-2
  }
  ```

  **Validación**:
  - GET retorna HTTP 200
  - Response es array con 1 acta
  - Acta tiene `jrvId = 101` (JRV de B)
  - Acta de A (jrvId=100) NO aparece

---

## C · Mutación

- [ ] **C.1** — RF-TEST-04 Scenario 1. Quitar filtro de `registradaPorId` en `ActaRepository.findWithFilters()`.

  **Ubicación**: `backend/electoral-api/src/main/java/.../acta/domain/ActaRepository.java`

  **Antes**:
  ```java
  (:registradaPorId IS NULL OR acta.registradoPor.id = :registradaPorId)
  ```

  **Después** (removida esta cláusula):
  ```java
  // solo criterios de estado, cargo, jrv (sin filtro de registradoPor)
  ```

  **Esperado**: B.1 debe caer → respuesta tendrá 2 actas (A y B), no 1.

  **Mutación** (`cp` → mutar → correr → `cp` restore → `rm .bak`, **nunca `git checkout --`**):

  ```bash
  cd backend/electoral-api
  cp src/main/java/.../ActaRepository.java src/main/java/.../ActaRepository.java.bak
  # editar, remover filtro
  ./mvnw -B test -Dtest=ObserverControllerRlsIT > /tmp/mut.log 2>&1
  # anotar si cayó
  cp src/main/java/.../ActaRepository.java.bak src/main/java/.../ActaRepository.java
  rm src/main/java/.../ActaRepository.java.bak
  ```

---

## Compuertas antes de terminar

```bash
cd backend/electoral-api
./mvnw -B clean test > /tmp/after.log 2>&1; echo "exit=$?"

    0 errores de ApplicationContext
    total = 291 + 1 = 292 tests
    0 failures
    C.1 anotada: [nombre del test que cayó]
```

---

## Qué NO hacer

- **No** tocar `ObserverController.java` — ya existe y funciona
- **No** cambiar el schema de RLS o agregaciones
- **No** usar `@BeforeEach` que modifique clase global (usar `@BeforeEach` local del test)
- **No** hardcodear IDs de usuario en setup (usar builder pattern o `@Id` autoincrementado)
