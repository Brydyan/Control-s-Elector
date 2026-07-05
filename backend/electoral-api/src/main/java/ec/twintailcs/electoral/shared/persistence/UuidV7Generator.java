package ec.twintailcs.electoral.shared.persistence;

import com.github.f4b6a3.uuid.UuidCreator;
import java.util.UUID;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.id.uuid.UuidValueGenerator;

/**
 * Genera identificadores UUIDv7 (RFC 9562) para la columna {@code public_id}
 * de cada entidad. Se apoya en uuid-creator porque Hibernate 7.1 aun no trae
 * un generador v7 nativo (llega en 7.2 como {@code UuidVersion7Strategy}).
 *
 * Punto unico de swap (ver ADR 0001): cuando el proyecto suba a Hibernate
 * >=7.2, reemplazar {@code algorithm = UuidV7Generator.class} por
 * {@code algorithm = org.hibernate.id.uuid.UuidVersion7Strategy.class} en
 * cada entidad, borrar esta clase y quitar la dependencia uuid-creator.
 */
public final class UuidV7Generator implements UuidValueGenerator {

    @Override
    public UUID generateUuid(SharedSessionContractImplementor session) {
        return UuidCreator.getTimeOrderedEpoch();
    }
}
