package ec.twintailcs.electoral.auth.web;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

/**
 * T-FE-15 E.2: Tests para AdminCacheResource.
 *
 * <p>Verifica:
 * - Limpiar todos los caches sin parámetro
 * - Limpiar un cache específico
 * - Retornar 404 si cache no existe
 * - Response contiene lista de caches limpios
 */
@QuarkusTest
class AdminCacheResourceTest {

  @Test
  void testClearAllCaches() {
    // Limpia todos sin parámetro
    given().when().get("/api/v1/admin/cache/clear").then().statusCode(200)
        .body("success", is(true))
        .body("message", containsString("All caches cleared"))
        .body("clearedCaches", notNullValue());
  }

  @Test
  void testClearSpecificCache() {
    // Limpia cache específico (aunque esté vacío, no debe retornar error)
    given()
        .queryParam("cacheName", "partidos")
        .when()
        .get("/api/v1/admin/cache/clear")
        .then()
        .statusCode(200)
        .body("success", is(true))
        .body("message", containsString("partidos"))
        .body("message", containsString("cleared successfully"));
  }

  @Test
  void testClearNonExistentCache() {
    // Retorna 404 si cache no existe
    given()
        .queryParam("cacheName", "nonexistent-cache-xyz")
        .when()
        .get("/api/v1/admin/cache/clear")
        .then()
        .statusCode(404)
        .body("success", is(false))
        .body("message", containsString("not found"));
  }

  @Test
  void testClearCacheWithEmptyName() {
    // cacheName vacío → limpia todos (mismo que sin parámetro)
    given()
        .queryParam("cacheName", "")
        .when()
        .get("/api/v1/admin/cache/clear")
        .then()
        .statusCode(200)
        .body("success", is(true))
        .body("message", containsString("All caches cleared"));
  }
}
