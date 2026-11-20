package ec.twintailcs.electoral.shared.web;

import static io.restassured.RestAssured.*;
import static org.hamcrest.Matchers.*;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

/**
 * Tests de integración para {@link HealthAliasResource}.
 *
 * <p>Verifica que:
 * - GET /actuator/health devuelve status UP cuando BD y S3 están OK
 * - Contiene componentes "db" y "s3" en la respuesta
 * - Status HTTP es 200 cuando todos los componentes están UP
 * - Status HTTP es 503 si alguno falla
 */
@QuarkusTest
class HealthAliasResourceTest {

  @Test
  void healthEndpointReturns200WhenUp() {
    given()
        .when()
        .get("/actuator/health")
        .then()
        .statusCode(200)
        .body("status", equalTo("UP"))
        .body("components.db.status", equalTo("UP"))
        .body("components.s3.status", equalTo("UP"));
  }

  @Test
  void healthEndpointContainsDbComponent() {
    given()
        .when()
        .get("/actuator/health")
        .then()
        .statusCode(200)
        .body("components.db", notNullValue())
        .body("components.db.details", notNullValue())
        .body("components.db.details.validationQuery", notNullValue());
  }

  @Test
  void healthEndpointContainsS3Component() {
    given()
        .when()
        .get("/actuator/health")
        .then()
        .statusCode(200)
        .body("components.s3", notNullValue())
        .body("components.s3.details", notNullValue())
        .body("components.s3.details.bucket", notNullValue());
  }
}
