package ec.twintailcs.electoral.storage;

import java.io.IOException;
import java.io.InputStream;

/**
 * Interfaz de abstracción para operaciones de almacenamiento de archivos.
 *
 * <p>Desacopla la lógica de dominio de la implementación específica de storage (S3, MinIO, local,
 * etc). Permite:
 * <ul>
 *   <li>Testear sin S3 real
 *   <li>Cambiar providers sin refactor de UseCase
 *   <li>Implementar fallback provider (S3 → Local)
 *   <li>Manejar errores de forma consistente
 * </ul>
 *
 * <p><b>Convención de excepciones:</b> Todos los métodos lanzan {@link StorageException}
 * (checked). Los implementadores capturan excepciones de bajo nivel (S3Exception, IOException,
 * etc) y las traducen a StorageException con contexto.
 */
public interface StorageService {

  /**
   * Sube un stream como objeto en el storage configurado.
   *
   * @param key identificador único del objeto (ej: actas/{actaId}/{uuid}.webp)
   * @param input stream con el contenido
   * @param size tamaño en bytes (requerido por algunos providers)
   * @param mimeType ej: image/webp
   * @throws StorageException si la subida falla
   * @throws IOException si hay error leyendo el stream
   */
  void upload(String key, InputStream input, long size, String mimeType)
      throws StorageException, IOException;

  /**
   * Elimina un objeto. No falla si no existe (idempotente).
   *
   * @param key identificador del objeto
   * @throws StorageException si la eliminación falla (excepto "no existe")
   */
  void delete(String key) throws StorageException;

  /**
   * Descarga el contenido del objeto como stream.
   *
   * @param key identificador del objeto
   * @return InputStream del objeto. El llamador debe cerrarlo.
   * @throws StorageException si la descarga falla
   */
  InputStream download(String key) throws StorageException;

  /**
   * Genera URL prefirmada para descargar el objeto sin autenticación.
   *
   * @param key identificador del objeto
   * @return URL prefirmada (válida según configuración de expiración)
   * @throws StorageException si la generación falla
   */
  String presignDownloadUrl(String key) throws StorageException;

  /**
   * Genera URL prefirmada para subir un objeto sin autenticación.
   *
   * @param key identificador del objeto
   * @return URL prefirmada (válida según configuración de expiración)
   * @throws StorageException si la generación falla
   */
  String presignUploadUrl(String key) throws StorageException;

  /** Verifica que el storage esté disponible (conexión, credenciales, etc). */
  boolean isHealthy();
}
