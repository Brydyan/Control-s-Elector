package ec.twintailcs.electoral.storage;

import ec.twintailcs.electoral.storage.fallback.FallbackStorageService;
import ec.twintailcs.electoral.storage.s3.S3StorageAdapter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

/**
 * Configuración de inyección para StorageService.
 *
 * <p><b>Producción:</b> Inyecta {@link FallbackStorageService} como StorageService default. El
 * primary es {@link S3StorageAdapter}; actualmente no hay un proveedor de fallback registrado.
 *
 * <p><b>Decisión de diseño:</b> los casos de uso consumen la interfaz StorageService y no dependen
 * del proveedor concreto. El fallback queda opcional y no se configura en este alcance.
 *
 * <p><b>CDI Pattern:</b> usa qualifiers distintos para el decorador (@FallbackService), el primary
 * (@S3Provider) y los proveedores alternativos (@FallbackProvider).
 */
@ApplicationScoped
public class StorageConfiguration {

  /**
   * Productor de StorageService default: implementación con fallback.
   *
   * <p>Los componentes que inyecten StorageService sin qualifier reciben esta instancia. Este
   * producer es el único que debe producir StorageService sin qualifier específico.
   */
  @Produces
  @Singleton
  public StorageService storageService(@FallbackService FallbackStorageService fallback) {
    return fallback;
  }

  /**
   * Productor de fallback provider extensible (para LocalStorageAdapter en el futuro).
   *
   * <p>Actualmente None. Cuando LocalStorageAdapter esté implementado: descomentar producer
   * con @FallbackProvider qualifier. FallbackStorageService lo detecta con isResolvable() e inyecta
   * como fallback.
   */
  // @Produces
  // @Singleton
  // public StorageService localStorage(@FallbackProvider LocalStorageAdapter adapter) {
  //   return adapter;
  // }
}
