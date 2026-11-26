package ec.twintailcs.electoral.storage;

import jakarta.inject.Qualifier;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * CDI Qualifier para {@link FallbackStorageService}.
 *
 * <p>Usado para distinguir la implementación de fallback de otras implementaciones de {@link StorageService}.
 * Permite que el producer method acceda explícitamente a FallbackStorageService sin ambigüedad.
 */
@Qualifier
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.FIELD, ElementType.PARAMETER})
public @interface FallbackProvider {}
