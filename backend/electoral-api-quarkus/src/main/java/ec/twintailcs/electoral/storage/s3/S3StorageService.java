package ec.twintailcs.electoral.storage.s3;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import org.jboss.logging.Logger;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

/**
 * Wrapper sobre el {@link S3Client} singleton con operaciones de alto nivel. Los resources y los
 * casos de uso no deberian tocar el {@code S3Client} directamente: siempre pasan por aca para
 * tener un punto unico de loggeo y de manejo de errores.
 *
 * <p>Convenciones:
 *
 * <ul>
 *   <li>Las claves de objeto las arma el llamador; este service no decide paths.
 *   <li>Los streams se cierran via try-with-resources aca (no se toma ownership del stream del
 *       llamador mas alla de la llamada).
 * </ul>
 */
@ApplicationScoped
public class S3StorageService {

  private static final Logger log = Logger.getLogger(S3StorageService.class);

  @Inject S3Client s3;
  @Inject S3Presigner presigner;
  @Inject S3Properties props;

  /**
   * Sube un stream como objeto en el bucket configurado.
   *
   * @param key clave S3 (path dentro del bucket)
   * @param input stream con el contenido
   * @param size tamano en bytes (requerido por el PUT de S3)
   * @param mimeType ej: image/webp
   */
  public void upload(String key, InputStream input, long size, String mimeType) throws IOException {
    try (InputStream in = input) {
      PutObjectRequest req =
          PutObjectRequest.builder()
              .bucket(props.getBucket())
              .key(key)
              .contentType(mimeType)
              .contentLength(size)
              .build();
      s3.putObject(req, RequestBody.fromInputStream(in, size));
      log.debugf("Uploaded s3://%s/%s", props.getBucket(), key);
    } catch (S3Exception e) {
      log.errorf(e, "S3 upload failed for key %s: %s", key, e.getMessage());
      throw e;
    }
  }

  /** Elimina un objeto. No falla si no existe (idempotente). */
  public void delete(String key) {
    try {
      s3.deleteObject(DeleteObjectRequest.builder().bucket(props.getBucket()).key(key).build());
      log.debugf("Deleted s3://%s/%s", props.getBucket(), key);
    } catch (S3Exception e) {
      log.errorf(e, "S3 delete failed for key %s: %s", key, e.getMessage());
      throw e;
    }
  }

  /**
   * Descarga el contenido del objeto.
   *
   * <p>Lo consume el generador del expediente PDF (modulo de actas, pendiente de portar), que
   * embebe la foto de la evidencia en el PDF del expediente.
   *
   * @return InputStream del objeto. El llamador debe cerrarlo.
   */
  public InputStream download(String key) {
    return s3.getObjectAsBytes(
            GetObjectRequest.builder().bucket(props.getBucket()).key(key).build())
        .asInputStream();
  }

  /**
   * URL prefirmada para descargar el objeto. La expiracion sale de {@link
   * S3Properties#getPresignExpiry()} (default 15 min, configurable por entorno via {@code
   * aws.s3.presign-expiry}).
   *
   * <p>El frontend la usa para mostrar imagenes sin proxyar por el backend.
   */
  public String presignDownloadUrl(String key) {
    GetObjectPresignRequest req =
        GetObjectPresignRequest.builder()
            .signatureDuration(props.getPresignExpiry())
            .getObjectRequest(GetObjectRequest.builder().bucket(props.getBucket()).key(key).build())
            .build();
    return presigner.presignGetObject(req).url().toString();
  }
}
