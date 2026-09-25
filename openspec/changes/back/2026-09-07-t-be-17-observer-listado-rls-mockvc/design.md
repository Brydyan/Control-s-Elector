# Design: T-BE-17 — Prueba MockMvc RLS listado OBSERVER

---

## D1: Por qué MockMvc y no Testcontainers

**Opción A**: Testcontainers (como T-BE-15)
- Prueba RLS a nivel de BD + transacción
- No prueba la **propagación de SecurityContext** a través del request HTTP
- No verifica que `observerId()` helper extrae el usuario correcto

**Opción B**: MockMvc (elegida)
- Prueba HTTP stack completo: `@PreAuthorize("isAuthenticated()")` + `observerId()` + query con filtro
- Verifica que Spring Security propaga `@WithMockUser` a través de `CurrentUser`
- Simula dos requests separados con dos identidades distintas

**Decisión**: MockMvc. RF-TEST-04 Scenario 1 es un requisito HTTP: "OBSERVER B consulta GET /...". Testcontainers no prueba eso.

---

## D2: Setup de datos

**Escenario**:
```
Provincia: Santa Elena (id=1)
  Canton: La Libertad (id=2)
    Parroquia: Central (id=3)
      JRV-1 (id=100)
      JRV-2 (id=101)
  
  OBSERVER-A:
    usuario.id = 1001
    observer_jrv_asignacion: [JRV-1]
    Acta: JRV-1 + Prefecto (registrada por A)
  
  OBSERVER-B:
    usuario.id = 1002
    observer_jrv_asignacion: [JRV-2]
    Acta: JRV-2 + Prefecto (registrada por B)
```

**Cuando B consulta GET /observer/mis-actas**:
- Query: `findWithFilters(estado=null, ..., observerId=1002)`
- RLS filter en JPQL: `(:registradaPorId IS NULL OR acta.registradaPor.id = :registradaPorId)`
  - Parámetro: `:registradaPorId = 1002`
  - Acta de A (registradaPor.id = 1001) no pasa el filtro
  - **Resultado**: acta de A ausente en respuesta de B

---

## D3: Estructura del test

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class ObserverControllerRlsIT {
  
  @Autowired MockMvc mockMvc;
  @Autowired ActaRepository actaRepository;
  @Autowired UsuarioRepository usuarioRepository;
  
  @BeforeEach
  void setup() {
    // 1. Crear provincia, cantón, parroquia, 2 JRVs
    // 2. Crear cargo (Prefecto)
    // 3. Crear 2 usuarios OBSERVER + asignaciones
    // 4. OBSERVER-A registra acta en JRV-1
    // 5. OBSERVER-B registra acta en JRV-2
  }
  
  @Test
  @WithMockUser(username="observer-b-publicid", roles="OBSERVER")
  void observerBNoVeActasDelObserverA() {
    mockMvc.perform(get("/api/v1/observer/mis-actas"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$", hasSize(1)))  // solo 1 acta (propia)
      .andExpect(jsonPath("$[0].jrvId", is(101)))  // JRV de B
  }
}
```

---

## D4: Mutaciones

Una mutación: remover el guard de `registradaPorId` en `ActaRepository.findWithFilters()`.

**Hipótesis**: Si quito `(:registradaPorId IS NULL OR acta.registradaPor.id = :registradaPorId)`, OBSERVER-B verá actas de OBSERVER-A.

**Verificación**: Test debe caer cuando ambos OBSERVERs ven acta del otro.

---

## D5: Integración con Spring Security

**Flujo**:
1. `@WithMockUser(username="observer-b", roles="OBSERVER")` crea un `SecurityContext` en el thread de prueba
2. MockMvc propaga el context al request
3. `@PreAuthorize("isAuthenticated()")` valida el context
4. `CurrentUser.id()` retorna el username del mock ("observer-b")
5. `observerId()` helper usa username para buscar en BD el `Usuario` real
6. Query se ejecuta con `observerId` correcto

**Consideración**: `CurrentUser` usa `SecurityContextHolder.getContext().getAuthentication()`. MockMvc propaga esto automáticamente.

---

## D6: Por qué es una línea de prueba y no refactorización

Esta prueba **cierra el WARNING de T-BE-15**, no arregla nada en producción. Los datos ya están correctos:
- RLS está activo (probado en RF-TEST-05)
- Filtro en JPQL es correcto (probado en RF-TEST-06)
- HTTP endpoint existe (testeable ahora)

Esta prueba es **cobertura adicional** que valida el comportamiento end-to-end.

---

## Resumen

| Aspecto | Decisión |
|---------|----------|
| Framework de test | MockMvc (SpringBootTest) |
| Contexto | Real Spring ApplicationContext, 2 usuarios reales |
| Aislamiento de datos | `@BeforeEach` setup + transacción test |
| Mutación | Remover `:registradaPorId` guard |
| Complejidad | Media (~ 80 líneas) |
| Riesgo | Bajo (puro test, no cambios de código) |
