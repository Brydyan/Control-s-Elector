package ec.twintailcs.electoral.legal.web;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Documentos legales publicos (T-LEGAL-01.2).
 *
 * <p>Verifica el contrato de {@code GET /privacy}, {@code GET /terms} y {@code GET
 * /api/v1/legal/current-version}:
 *
 * <ul>
 *   <li>responden 200 SIN token (la informacion del GDPR Art. 13/14 es de cualquiera, no solo de
 *       quien ya entro);
 *   <li>el HTML es el del idioma pedido, con el parametro por encima del idioma del navegador;
 *   <li>un idioma fuera de la lista cerrada cae al de por defecto, y no se lee ningun archivo
 *       arbitrario del classpath;
 *   <li>la version vigente aparece en el documento: el numero que el usuario lee es el mismo que el
 *       que el servidor va a pedirle que acepte.
 * </ul>
 */
@QuarkusTest
@TestProfile(LegalPublicTestProfile.class)
class LegalPublicPagesIT {

  @Test
  @DisplayName("GET /privacy responde 200 en HTML sin token")
  void privacidadEsPublica() {
    Response res = given().when().get("/privacy");

    assertThat(res.getStatusCode()).isEqualTo(200);
    assertThat(res.getContentType()).contains("text/html");
    assertThat(res.asString()).contains("Política de Privacidad");
  }

  @Test
  @DisplayName("GET /terms responde 200 en HTML sin token")
  void terminosEsPublico() {
    Response res = given().when().get("/terms");

    assertThat(res.getStatusCode()).isEqualTo(200);
    assertThat(res.getContentType()).contains("text/html");
    assertThat(res.asString()).contains("Términos de Uso");
  }

  @Test
  @DisplayName("el parametro lang manda sobre el idioma por defecto")
  void elParametroLangManda() {
    Response en = given().when().get("/privacy?lang=en");
    Response es = given().when().get("/privacy?lang=es");

    assertThat(en.asString()).contains("Privacy Policy");
    assertThat(en.asString()).doesNotContain("Política de Privacidad");
    assertThat(es.asString()).contains("Política de Privacidad");
  }

  @Test
  @DisplayName("un idioma fuera de la lista cerrada cae al español y no rompe")
  void unIdiomaFueraDeLaListaCaeAlPorDefecto() {
    Response res = given().when().get("/terms?lang=../../etc/passwd");

    assertThat(res.getStatusCode()).isEqualTo(200);
    assertThat(res.asString()).contains("Términos de Uso");
  }

  @Test
  @DisplayName("el documento muestra la version vigente y no el marcador sin sustituir")
  void elDocumentoMuestraLaVersionVigente() {
    String html = given().when().get("/privacy").asString();

    assertThat(html).contains("Versión <strong>1.0</strong>");
    assertThat(html)
        .as("el marcador de version tiene que quedar sustituido por el servidor")
        .doesNotContain("{{VERSION}}");
  }

  @Test
  @DisplayName("GET /api/v1/legal/current-version devuelve la version vigente en JSON")
  void currentVersionDevuelveLaVersion() {
    Response res = given().when().get("/api/v1/legal/current-version");

    assertThat(res.getStatusCode()).isEqualTo(200);
    assertThat(res.getContentType()).contains("application/json");
    assertThat(res.jsonPath().getString("version")).isEqualTo("1.0");
    assertThat(res.jsonPath().getString("terms_version")).isEqualTo("1.0");
    assertThat(res.jsonPath().getString("privacy_version")).isEqualTo("1.0");
  }
}
