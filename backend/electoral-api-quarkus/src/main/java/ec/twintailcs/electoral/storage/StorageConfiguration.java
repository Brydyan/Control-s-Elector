package ec.twintailcs.electoral.storage;

import ec.twintailcs.electoral.storage.fallback.FallbackStorageService;
import ec.twintailcs.electoral.storage.s3.S3StorageAdapter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

/**
 * Configuración de inyección para StorageService.
 *
 * <p><b>Producción:</b> Inyecta {@link FallbackStorageService} como StorageService default. -
 * Primary: {@link S3StorageAdapter} (AWS S3 / RustFS / MinIO) inyectado con @S3Provider - Fallback:
 * (None, pero extensible a LocalStorageAdapter) con @FallbackProvider
 *
 * <p><b>Desarrollo / CI sin S3:</b> Configurar environment o agregar LocalStorageAdapter.
 *
 * <p><b>Decisión de diseño:</b> FallbackStorageService es el default porque: - En prod: S3 es
 * always healthy, fallback nunca se usa (costo cero) - En CI: S3 con credenciales inválidas se
 * detecta en health check, fallback da opciones - Desacoplamiento: UseCase no sabe sobre fallback,
 * transparente
 *
 * <p><b>CDI Pattern:</b> Usa @Qualifier annotations (@S3Provider, @FallbackProvider) para
 * disambiguación instead de @Alternative. Esto permite que el producer acceda explícitamente a
 * ambas implementaciones sin ambigüedad de resolución.
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
  public StorageService storageService(@FallbackProvider FallbackStorageService fallback) {
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
