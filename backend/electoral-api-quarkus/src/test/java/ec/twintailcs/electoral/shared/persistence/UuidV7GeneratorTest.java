package ec.twintailcs.electoral.shared.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Test unitario puro del generador de UUID v7 — sin contexto de aplicacion ni base de datos.
 *
 * <p>Port exacto del {@code UuidV7GeneratorTest} del backend Spring. La suite de regresion por
 * contrato no puede afirmar esto: el {@code public_id} es un UUID en la respuesta HTTP, pero el
 * contrato no promete ni la version ni el orden — y de eso depende que los indices de {@code
 * public_id} no se fragmenten al insertar.
 *
 * <p>Se prueba antes de que cualquier entidad dependa del generador: si el wiring del SPI de
 * Hibernate dejara de usar esta clase, los ids publicos dejarian de ser v7 en silencio.
 */
class UuidV7GeneratorTest {

  private final UuidV7Generator generator = new UuidV7Generator();

  @Test
  @DisplayName("el generador produce UUID de version 7 (RFC 9562)")
  void elGeneradorProduceUuidDeVersion7() {
    UUID resultado = generator.generateUuid(null);

    assertThat(resultado).isNotNull();
    assertThat(resultado.version()).isEqualTo(7);
  }

  @Test
  @DisplayName("los UUID generados son monótonos: el orden del valor sigue al del tiempo")
  void losUuidGeneradosSonMonotonos() {
    // Importa porque los indices sobre public_id se ordenan por valor: si el generador
    // devolviera ids aleatorios, cada insert moveria las hojas del indice y las lecturas
    // se degradarian con el volumen de la eleccion.
    UUID primero = generator.generateUuid(null);
    UUID segundo = generator.generateUuid(null);
    UUID tercero = generator.generateUuid(null);

    assertThat(compararSinSigno(primero, segundo)).isLessThanOrEqualTo(0);
    assertThat(compararSinSigno(segundo, tercero)).isLessThanOrEqualTo(0);
  }

  /** Compara dos UUID por su bits, sin interpretar el bit de signo. */
  private int compararSinSigno(UUID u1, UUID u2) {
    int m = Long.compareUnsigned(u1.getMostSignificantBits(), u2.getMostSignificantBits());
    if (m != 0) {
      return m;
    }
    return Long.compareUnsigned(u1.getLeastSignificantBits(), u2.getLeastSignificantBits());
  }
}
