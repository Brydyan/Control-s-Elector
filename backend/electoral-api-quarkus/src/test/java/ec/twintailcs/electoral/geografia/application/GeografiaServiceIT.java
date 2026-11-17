package ec.twintailcs.electoral.geografia.application;

import static org.assertj.core.api.Assertions.assertThat;

import ec.twintailcs.electoral.geografia.web.GeografiaTestProfile;
import ec.twintailcs.electoral.geografia.web.dto.GeografiaTreeDto;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Arbol geografico contra una base real.
 *
 * <p>El seed de referencia deja 1 provincia, 2 cantones, 4 parroquias y 5 JRVs, asi que el test
 * puede afirmar la forma completa del arbol con los cuatro niveles poblados.
 */
@QuarkusTest
@TestProfile(GeografiaTestProfile.class)
class GeografiaServiceIT {

  @Inject GeografiaService service;

  @Test
  @DisplayName("el arbol tiene provincia, canton, parroquia y JRV en los cuatro niveles")
  void getArbolCompleto() {
    GeografiaTreeDto arbol = service.listarArbol();

    assertThat(arbol.provincias()).isNotEmpty();

    // El seed deja 1 provincia (Santa Elena, codigo 24) con 2 cantones. Se busca por nombre y
    // no por posicion: otros tests del modulo comparten la base y pueden dejar provincias de
    // su cuenta, y el orden del arbol es alfabetico.
    GeografiaTreeDto.ProvinciaNode provincia =
        arbol.provincias().stream()
            .filter(p -> "Santa Elena".equals(p.nombre()))
            .findFirst()
            .orElseThrow(
                () -> new AssertionError("no hay provincia Santa Elena: " + arbol.provincias()));
    assertThat(provincia.codigo()).isEqualTo("24");
    // El seed deja 2 cantones en esa provincia; el test no puede exigir el numero exacto
    // porque otros tests del modulo comparten la base y pueden dejar cantones de su cuenta.
    assertThat(provincia.cantones())
        .extracting(GeografiaTreeDto.CantonNode::nombre)
        .contains("Santa Elena", "La Libertad");

    // El orden es alfabetico por nombre: "La Libertad" va antes que "Santa Elena".
    // El test busca por nombre y no por posicion, para que el orden no sea el sujeto del test.
    GeografiaTreeDto.CantonNode canton =
        provincia.cantones().stream()
            .filter(c -> "Santa Elena".equals(c.nombre()))
            .findFirst()
            .orElseThrow(
                () -> new AssertionError("no hay canton Santa Elena: " + provincia.cantones()));
    assertThat(canton.codigo()).isEqualTo("2401");
    assertThat(canton.parroquias())
        .extracting(GeografiaTreeDto.ParroquiaNode::nombre)
        .contains("Santa Elena", "Ballenita");

    // Santa Elena (urbana) con 2 JRVs; Ballenita (rural) con 1.
    GeografiaTreeDto.ParroquiaNode parroquia =
        canton.parroquias().stream()
            .filter(p -> "Santa Elena".equals(p.nombre()))
            .findFirst()
            .orElseThrow(
                () -> new AssertionError("no hay parroquia Santa Elena: " + canton.parroquias()));
    assertThat(parroquia.esUrbana()).isTrue();
    assertThat(parroquia.jrvs()).hasSizeGreaterThanOrEqualTo(2);

    GeografiaTreeDto.JrvNode jrv = parroquia.jrvs().get(0);
    assertThat(jrv.numero()).isNotBlank();
    assertThat(jrv.recinto()).isNotBlank();
    assertThat(jrv.totalElectores()).isPositive();

    // La parroquia rural del mismo canton, con su unico JRV.
    GeografiaTreeDto.ParroquiaNode rural =
        canton.parroquias().stream()
            .filter(p -> "Ballenita".equals(p.nombre()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("no hay parroquia Ballenita"));
    assertThat(rural.esUrbana()).isFalse();
    assertThat(rural.jrvs()).hasSize(1);
  }

  @Test
  @DisplayName(
      "cada JRV cuelga de una parroquia que cuelga de un canton que cuelga de una provincia")
  void losNivelesEstanEncadenados() {
    GeografiaTreeDto arbol = service.listarArbol();

    for (GeografiaTreeDto.ProvinciaNode provincia : arbol.provincias()) {
      for (GeografiaTreeDto.CantonNode canton : provincia.cantones()) {
        for (GeografiaTreeDto.ParroquiaNode parroquia : canton.parroquias()) {
          for (GeografiaTreeDto.JrvNode jrv : parroquia.jrvs()) {
            assertThat(jrv.id()).isNotNull();
            assertThat(jrv.numero()).isNotBlank();
          }
        }
      }
    }
  }
}
