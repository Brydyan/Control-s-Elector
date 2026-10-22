package ec.twintailcs.electoral.shared.persistence;

import com.github.f4b6a3.uuid.UuidCreator;
import io.quarkus.runtime.annotations.RegisterForReflection;
import java.util.UUID;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.id.uuid.UuidValueGenerator;

/**
 * Generador de UUID v7 (time-ordered, RFC 9562).
 *
 * <p>Port exacto del generador del backend Spring: mismas columnas public_id, mismo algoritmo. Los
 * ids ordenados en el tiempo hacen que los indices no se fragmenten al insertar.
 *
 * <p>El registro para reflexion no es opcional. Las entidades la referencian desde
 * `@UuidGenerator(algorithm = UuidV7Generator.class)`, y Hibernate la instancia con
 * `UuidGenerator.instantiateCustomGenerator`, que hace `getDeclaredConstructor()` sobre la clase.
 * En una imagen nativa eso solo funciona si la clase esta registrada: sin el
 * `@RegisterForReflection` el binario arranca y muere con
 *
 * <p>java.lang.NoSuchMethodException: ...UuidV7Generator.<init>()
 *
 * <p>El camino JVM no lo detecta —ni `clean verify` ni los tests de contrato lo ven— porque en la
 * JVM la reflexion no necesita registro previo. Solo se reproduce construyendo y arrancando el
 * binario nativo.
 */
@RegisterForReflection
public final class UuidV7Generator implements UuidValueGenerator {

  @Override
  public UUID generateUuid(SharedSessionContractImplementor session) {
    return UuidCreator.getTimeOrderedEpoch();
  }
}
