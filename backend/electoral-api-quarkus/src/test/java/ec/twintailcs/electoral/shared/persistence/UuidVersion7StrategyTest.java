package ec.twintailcs.electoral.shared.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.id.uuid.UuidVersion7Strategy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Test unitario puro del generador de UUID v7 — sin contexto de aplicacion ni base de datos.
 *
 * <p>Las entidades usan {@code @UuidGenerator(style = UuidGenerator.Style.VERSION_7)}, que delega
 * en la estrategia de Hibernate. La suite de regresion por contrato no puede afirmar nada de esto:
 * el {@code public_id} es un UUID en la respuesta HTTP, pero el contrato no promete ni la version
 * ni el orden — y de esto depende que los indices de {@code public_id} no se fragmenten al
 * insertar.
 *
 * <p>Se prueba antes de que cualquier entidad dependa del generador: si el wiring del SPI de
 * Hibernate dejara de usar esa estrategia, los ids publicos dejarian de ser v7 en silencio.
 */
class UuidVersion7StrategyTest {

  /** Cuantos ids se generan seguidos en el mismo tick para medir el orden de verdad. */
  private static final int LOTE = 2_000;

  private final UuidVersion7Strategy strategy = new UuidVersion7Strategy();

  @Test
  @DisplayName("el generador produce UUID de version 7 (RFC 9562)")
  void elGeneradorProduceUuidDeVersion7() {
    UUID resultado = strategy.generateUUID(null);

    assertThat(resultado).isNotNull();
    assertThat(resultado.version()).isEqualTo(7);
    assertThat(resultado.variant()).isEqualTo(2);
  }

  @Test
  @DisplayName("los UUID generados son monotonos: el orden del valor sigue al del tiempo")
  void losUuidGeneradosSonMonotonos() {
    // Importa porque los indices sobre public_id se ordenan por valor: si el generador
    // devolviera ids aleatorios, cada insert moveria las hojas del indice y las lecturas
    // se degradarian con el volumen de la eleccion.
    //
    // Se generan LOTE ids en un lazo cerrado porque el orden solo se pone a prueba dentro
    // del mismo milisegundo, que es donde los bits aleatorios de la parte no temporal
    // pueden romperlo. Con dos o tres ids la chance de caer en el mismo tick es baja y el
    // test pasaria sin estar midiendo nada.
    List<UUID> lote = new ArrayList<>(LOTE);
    for (int i = 0; i < LOTE; i++) {
      lote.add(strategy.generateUUID(null));
    }

    List<UUID> fueraDeOrden = new ArrayList<>();
    for (int i = 1; i < lote.size(); i++) {
      if (compararSinSigno(lote.get(i - 1), lote.get(i)) > 0) {
        fueraDeOrden.add(lote.get(i));
      }
    }

    assertThat(fueraDeOrden)
        .as(
            "de %d ids generados en el mismo tick, %d rompieron el orden ascendente; "
                + "el primero fue %s",
            LOTE, fueraDeOrden.size(), fueraDeOrden.isEmpty() ? "-" : fueraDeOrden.get(0))
        .isEmpty();
  }

  /** Compara dos UUID por sus bits, sin interpretar el bit de signo. */
  private int compararSinSigno(UUID u1, UUID u2) {
    int m = Long.compareUnsigned(u1.getMostSignificantBits(), u2.getMostSignificantBits());
    if (m != 0) {
      return m;
    }
    return Long.compareUnsigned(u1.getLeastSignificantBits(), u2.getLeastSignificantBits());
  }
}
