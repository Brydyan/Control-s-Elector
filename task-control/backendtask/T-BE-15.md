# T-BE-15 — Tests de Integración Críticos

**Dificultad:** Intermedio
**Tiempo estimado:** 3-4 horas
**Depende de:** T-BE-11, T-BE-12

---

## ¿Qué vas a construir?

Tests que verifican que el sistema funciona correctamente de punta a punta. No son tests unitarios — prueban la integración real entre controller, service, repository y base de datos.

---

## Conceptos clave

### Test unitario vs Test de integración
- **Unitario**: prueba una función aislada, mockeando todo lo que la rodea
- **Integración**: prueba el sistema completo con BD real (o en memoria)

Para este proyecto, los tests de integración son más valiosos porque verifican:
- Que el locking pesimista realmente previene duplicados
- Que la validación de votos llega hasta el HTTP 400
- Que el flujo completo de estados funciona

### @SpringBootTest
Levanta toda la aplicación Spring para el test. Más lento pero más realista.

### @AutoConfigureMockMvc
Genera un `MockMvc` para hacer requests HTTP en los tests sin levantar un servidor real.

### H2 — base de datos en memoria para tests
H2 es una BD que vive en memoria RAM. Se crea al iniciar el test y se destruye al terminar. Perfecta para tests porque:
- No afecta la BD real
- Cada test empieza con BD limpia
- Es muy rápida

### @WithMockUser
Simula un usuario autenticado en Spring Security sin hacer login real:
```java
@WithMockUser(username = "1", roles = "OBSERVER")  // simula observer con userId=1
```

---

## Archivos a crear

```
src/test/java/ec/twintailcs/electoral/
  integration/
    ActaRegistroTest.java
    ActaFlujoTest.java
    ActaLockingTest.java
    RlsAccesoTest.java
```

Además, agregar en `pom.xml`:
```xml
<dependency>
    <groupId>com.h2database</groupId>
    <artifactId>h2</artifactId>
    <scope>test</scope>
</dependency>
```

Y crear `src/test/resources/application-test.properties`:
```properties
# BD en memoria para tests
spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1
spring.datasource.driver-class-name=org.h2.Driver
spring.datasource.username=sa
spring.datasource.password=
spring.jpa.hibernate.ddl-auto=create-drop
spring.jpa.database-platform=org.hibernate.dialect.H2Dialect
spring.jpa.open-in-view=false

# Deshabilitar Supabase en tests
supabase.url=http://mock-supabase
supabase.service-key=mock-key
supabase.bucket=test-bucket
```

---

## Pasos

### Paso 1 — Test 1: Validación de votos (HTTP 400)

```java
// src/test/java/ec/twintailcs/electoral/integration/ActaRegistroTest.java
package ec.twintailcs.electoral.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")  // Usa application-test.properties
class ActaRegistroTest {

    @Autowired
    private MockMvc mockMvc;  // Para hacer requests HTTP en los tests

    @Test
    @WithMockUser(username = "1", roles = "OBSERVER")  // Simular observer autenticado
    void cuandoVotosNoSuman_debeRetornar400() throws Exception {

        // JSON con votos que NO cuadran: 60 + 30 + 2 + 1 = 93 ≠ 100
        String actaJson = """
            {
                "jrvId": 1,
                "cargoId": 1,
                "totalSufragantes": 100,
                "votosBlancos": 2,
                "votosNulos": 1,
                "detalles": [
                    {"candidatoId": 1, "votos": 60},
                    {"candidatoId": 2, "votos": 30}
                ]
            }
            """;
        // Nota: 60 + 30 + 2 + 1 = 93, pero totalSufragantes=100 → inválido

        mockMvc.perform(post("/api/observer/actas")
                .contentType(MediaType.APPLICATION_JSON)
                .content(actaJson))
            .andExpect(status().isBadRequest());  // Esperamos HTTP 400
    }

    @Test
    @WithMockUser(username = "1", roles = "OBSERVER")
    void cuandoVotosSuman_debeRetornar201() throws Exception {

        // Primero necesitas datos de prueba en la BD (JRV, cargo, candidatos)
        // Esto se puede hacer con @Sql o con un @BeforeEach que guarda datos de prueba

        String actaJson = """
            {
                "jrvId": 1,
                "cargoId": 1,
                "totalSufragantes": 100,
                "votosBlancos": 5,
                "votosNulos": 3,
                "detalles": [
                    {"candidatoId": 1, "votos": 60},
                    {"candidatoId": 2, "votos": 32}
                ]
            }
            """;
        // 60 + 32 + 5 + 3 = 100 ✓

        mockMvc.perform(post("/api/observer/actas")
                .contentType(MediaType.APPLICATION_JSON)
                .content(actaJson))
            .andExpect(status().isCreated());  // Esperamos HTTP 201
    }
}
```

### Paso 2 — Test 2: Flujo completo de estados

```java
// src/test/java/ec/twintailcs/electoral/integration/ActaFlujoTest.java
package ec.twintailcs.electoral.integration;

import ec.twintailcs.electoral.domain.*;
import ec.twintailcs.electoral.repository.ActaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@org.springframework.transaction.annotation.Transactional  // Rollback al terminar cada test
class ActaFlujoTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ActaRepository actaRepository;

    @Test
    void flujoCompleto_ENVIADA_a_APROBADA() throws Exception {

        // PASO 1: Observer registra el acta → ENVIADA
        MvcResult result = mockMvc.perform(
            post("/api/observer/actas")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"jrvId":1,"cargoId":1,"totalSufragantes":100,
                     "votosBlancos":5,"votosNulos":5,
                     "detalles":[{"candidatoId":1,"votos":90}]}
                    """)
                .with(org.springframework.security.test.web.servlet.request
                    .SecurityMockMvcRequestPostProcessors.user("1").roles("OBSERVER"))
        ).andExpect(status().isCreated()).andReturn();

        // Extraer el ID del acta del response
        String responseBody = result.getResponse().getContentAsString();
        // (parsear el JSON para obtener el id — usando Jackson u otra forma)
        Long actaId = 1L; // En un test real, parsearías el JSON

        // Verificar que el estado es ENVIADA
        Acta acta = actaRepository.findById(actaId).orElseThrow();
        assertThat(acta.getEstado()).isEqualTo(EstadoActa.ENVIADA);

        // PASO 2: Coordinator marca EN_DISPUTA
        mockMvc.perform(
            put("/api/coordinator/actas/" + actaId + "/disputar")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"motivo\":\"Votos no cuadran\"}")
                .with(org.springframework.security.test.web.servlet.request
                    .SecurityMockMvcRequestPostProcessors.user("2").roles("COORDINATOR"))
        ).andExpect(status().isOk());

        acta = actaRepository.findById(actaId).orElseThrow();
        assertThat(acta.getEstado()).isEqualTo(EstadoActa.EN_DISPUTA);

        // PASO 3: Observer corrige
        mockMvc.perform(
            put("/api/observer/actas/" + actaId + "/corregir")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"jrvId":1,"cargoId":1,"totalSufragantes":100,
                     "votosBlancos":5,"votosNulos":5,
                     "detalles":[{"candidatoId":1,"votos":90}]}
                    """)
                .with(org.springframework.security.test.web.servlet.request
                    .SecurityMockMvcRequestPostProcessors.user("1").roles("OBSERVER"))
        ).andExpect(status().isOk());

        acta = actaRepository.findById(actaId).orElseThrow();
        assertThat(acta.getEstado()).isEqualTo(EstadoActa.CORREGIDA);

        // PASO 4: Coordinator aprueba
        mockMvc.perform(
            put("/api/coordinator/actas/" + actaId + "/aprobar")
                .with(org.springframework.security.test.web.servlet.request
                    .SecurityMockMvcRequestPostProcessors.user("2").roles("COORDINATOR"))
        ).andExpect(status().isOk());

        acta = actaRepository.findById(actaId).orElseThrow();
        assertThat(acta.getEstado()).isEqualTo(EstadoActa.APROBADA);
    }
}
```

### Paso 3 — Test 3: Observer no puede ver JRVs de otros

```java
// src/test/java/ec/twintailcs/electoral/integration/RlsAccesoTest.java
package ec.twintailcs.electoral.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RlsAccesoTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithMockUser(username = "99", roles = "OBSERVER")  // Observer que NO tiene asignada la JRV 1
    void observer_sinAsignacion_noDebePoderRegistrarActa() throws Exception {

        String actaJson = """
            {
                "jrvId": 1,
                "cargoId": 1,
                "totalSufragantes": 100,
                "votosBlancos": 5,
                "votosNulos": 5,
                "detalles": [{"candidatoId": 1, "votos": 90}]
            }
            """;

        // Observer 99 no tiene asignada JRV 1, debe recibir 403
        mockMvc.perform(post("/api/observer/actas")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(actaJson))
            .andExpect(status().isForbidden());  // HTTP 403
    }
}
```

---

## Cómo ejecutar los tests

```bash
# Ejecutar todos los tests
./mvnw test

# Ejecutar solo los tests de integración
./mvnw test -Dtest="*Test"

# Ejecutar un test específico
./mvnw test -Dtest="ActaRegistroTest"
```

Al final verás algo como:
```
Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

---

## Errores comunes

| Error | Causa | Solución |
|-------|-------|----------|
| `Could not create datasource` | H2 no está en el classpath | Agrega la dependencia H2 con `scope=test` en pom.xml |
| Tests pasan pero luego la BD tiene datos sucios | Falta `@Transactional` | Agrega en la clase de test para hacer rollback automático |
| `BeanCreationException` al iniciar | Supabase config no existe | Crea `application-test.properties` con valores mock |
| `403` cuando esperas `400` | Auth falla antes que la validación | Verifica que `@WithMockUser` está configurado correctamente |
| Tests no se aíslan entre sí | Sin rollback, datos de test 1 afectan test 2 | Usa `@Transactional` o `@DirtiesContext` en la clase |
