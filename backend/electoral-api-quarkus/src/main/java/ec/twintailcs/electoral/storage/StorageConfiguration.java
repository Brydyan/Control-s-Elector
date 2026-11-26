package ec.twintailcs.electoral.storage;

import ec.twintailcs.electoral.storage.fallback.FallbackStorageService;
import ec.twintailcs.electoral.storage.s3.S3StorageAdapter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Configuración de inyección para StorageService.
 *
 * <p><b>Producción:</b> Inyecta {@link FallbackStorageService} como StorageService default.
 * - Primary: {@link S3StorageAdapter} (AWS S3 / RustFS / MinIO) inyectado con @S3Provider
 * - Fallback: (None, pero extensible a LocalStorageAdapter) con @FallbackProvider
 *
 * <p><b>Desarrollo / CI sin S3:</b> Configurar environment o agregar LocalStorageAdapter.
 *
 * <p><b>Decisión de diseño:</b> FallbackStorageService es el default porque:
 * - En prod: S3 es always healthy, fallback nunca se usa (costo cero)
 * - En CI: S3 con credenciales inválidas se detecta en health check, fallback da opciones
 * - Desacoplamiento: UseCase no sabe sobre fallback, transparente
 *
 * <p><b>CDI Pattern:</b> Usa @Qualifier annotations (@S3Provider, @FallbackProvider) para
 * disambiguación instead de @Alternative. Esto permite que el producer acceda explícitamente
 * a ambas implementaciones sin ambigüedad de resolución.
 */
@ApplicationScoped
public class StorageConfiguration {

  /**
   * Productor de StorageService default: implementación con fallback.
   *
   * <p>Los componentes que inyecten StorageService sin qualifier reciben esta instancia.
   * Este producer es el único que debe producir StorageService sin qualifier específico.
   */
  @Produces
  @Singleton
  public StorageService storageService(@FallbackProvider FallbackStorageService fallback) {
    return fallback;
  }

  /**
   * Productor de S3StorageAdapter con @S3Provider.
   *
   * <p>Lo inyecta FallbackStorageService como primary.
   * Nota: S3StorageAdapter ya tiene @S3Provider, este producer mantiene la singularidad
   * en scope @Singleton para toda la aplicación.
   */
  @Produces
  @Singleton
  public StorageService s3Storage(@S3Provider S3StorageAdapter adapter) {
    return adapter;
  }

  /**
   * Productor de fallback provider con @FallbackProvider.
   *
   * <p>Actualmente None (sería LocalStorageAdapter cuando esté implementado).
   * Este bean es Instance<StorageService> opcional en FallbackStorageService.
   * Si no se produce, FallbackStorageService lo detecta con isResolvable() y funciona
   * sin fallback (solo usa primary).
   */
  // @Produces
  // @Singleton
  // @FallbackProvider
  // public StorageService localStorage(@FallbackProvider LocalStorageAdapter adapter) {
  //   return adapter;
  // }
}
