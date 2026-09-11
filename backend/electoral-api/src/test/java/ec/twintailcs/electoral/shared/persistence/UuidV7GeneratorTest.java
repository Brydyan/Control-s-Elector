package ec.twintailcs.electoral.shared.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Pure unit test — no Spring context, no DB. Proves the uuid-creator SPI wiring produces RFC 9562
 * v7 UUIDs before any entity depends on it.
 */
class UuidV7GeneratorTest {

  private final UuidV7Generator generator = new UuidV7Generator();

  @Test
  void generateUuid_returnsVersion7Uuid() {
    UUID result = generator.generateUuid(null);

    assertThat(result).isNotNull();
    assertThat(result.version()).isEqualTo(7);
  }

  @Test
  void generateUuid_isMonotonicallyOrderedAcrossSequentialCalls() {
    UUID first = generator.generateUuid(null);
    UUID second = generator.generateUuid(null);
    UUID third = generator.generateUuid(null);

    assertThat(compareUnsigned(first, second)).isLessThanOrEqualTo(0);
    assertThat(compareUnsigned(second, third)).isLessThanOrEqualTo(0);
  }

  private int compareUnsigned(UUID u1, UUID u2) {
    int m = Long.compareUnsigned(u1.getMostSignificantBits(), u2.getMostSignificantBits());
    if (m != 0) {
      return m;
    }
    return Long.compareUnsigned(u1.getLeastSignificantBits(), u2.getLeastSignificantBits());
  }
}
