package ec.twintailcs.electoral.shared.persistence;

import com.github.f4b6a3.uuid.UuidCreator;
import java.util.UUID;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.id.uuid.UuidValueGenerator;

/**
 * Generador de UUID v7 (time-ordered, RFC 9562).
 *
 * <p>Port exacto del generador del backend Spring: mismas columnas public_id, mismo algoritmo. Los
 * ids ordenados en el tiempo hacen que los indices no se fragmenten al insertar.
 */
public final class UuidV7Generator implements UuidValueGenerator {

  @Override
  public UUID generateUuid(SharedSessionContractImplementor session) {
    return UuidCreator.getTimeOrderedEpoch();
  }
}
