package ec.twintailcs.electoral.storage;

import jakarta.inject.Qualifier;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * CDI Qualifier para proveedores alternativos de almacenamiento.
 *
 * <p>El decorador de fallback usa este qualifier para descubrir adaptadores opcionales. No debe
 * asignarse a {@code FallbackStorageService} para evitar que se inyecte a sí mismo.
 */
@Qualifier
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.FIELD, ElementType.PARAMETER})
public @interface FallbackProvider {}
