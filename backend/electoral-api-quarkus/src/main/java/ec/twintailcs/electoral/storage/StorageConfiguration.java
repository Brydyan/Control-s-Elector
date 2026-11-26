package ec.twintailcs.electoral.storage;

import ec.twintailcs.electoral.storage.fallback.FallbackStorageService;
import ec.twintailcs.electoral.storage.s3.S3StorageAdapter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

/**
 * Configuración de inyección para StorageService.
 *
 * <p><b>Producción:</b> Inyecta {@link FallbackStorageService} como StorageService default.
 * - Primary: {@link S3StorageAdapter} (AWS S3 / RustFS / MinIO)
 * - Fallback: (None, pero extensible a LocalStorageAdapter)
 *
 * <p><b>Desarrollo / CI sin S3:</b> Configurar environment o agregar LocalStorageAdapter.
 *
 * <p><b>Decisión de diseño:</b> FallbackStorageService es el default porque:
 * - En prod: S3 es always healthy, fallback nunca se usa (costo cero)
 * - En CI: S3 con credenciales inválidas se detecta en health check, fallback da opciones
 * - Desacoplamiento: UseCase no sabe sobre fallback, transparente
 */
@ApplicationScoped
public class StorageConfiguration {

  /**
   * Productor de StorageService default: implementación con fallback.
   *
   * <p>Los componentes que inyecten StorageService sin @Named reciben esta instancia.
   * Este producer es el único que debe producir StorageService sin qualifier específico.
   */
  @Produces
  @Singleton
  public StorageService storageService(FallbackStorageService fallback) {
    return fallback;
  }

  /**
   * Productor de S3StorageAdapter con @Named("s3").
   *
   * <p>Lo inyecta FallbackStorageService como primary.
   * Nota: S3StorageAdapter ya tiene @Named("s3"), este producer es redundante
   * pero se mantiene para explicititud en la configuración de DI.
   */
  @Produces
  @Singleton
  @Named("s3")
  public StorageService s3Storage(@Named("s3") S3StorageAdapter adapter) {
    return adapter;
  }

  /**
   * Productor de fallback provider con @Named("fallback").
   *
   * <p>Actualmente None (sería LocalStorageAdapter cuando esté implementado).
   * Este bean es Instance<StorageService> opcional en FallbackStorageService.
   * Si no se produce, FallbackStorageService lo detecta con isResolvable() y funciona
   * sin fallback (solo usa primary).
   */
  // @Produces
  // @Singleton
  // @Named("fallback")
  // public StorageService localStorage(LocalStorageAdapter adapter) {
  //   return adapter;
  // }
}
