package ec.twintailcs.electoral.storage.s3;

import java.io.IOException;
import java.io.InputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

/**
 * Wrapper sobre el {@link S3Client} singleton con operaciones de alto nivel. Los controllers y
 * services no deberian tocar S3Client directamente; siempre pasan por aca para tener un punto unico
 * de loggeo, retries y manejo de errores consistente.
 *
 * <p>Convenciones: - Las claves de objeto siguen formato `actas/<jrv_id>/<cargo_id>/<uuid>.webp`.
 * Quien arma la clave es el caller (controller); este service no decide paths. - Los streams se
 * cierran via try-with-resources en el caller (no se toman ownership).
 *
 * <p>Dependencias: solo S3Client (ya inyectado) y S3Properties.
 */
@Service
public class S3StorageService {

  private static final Logger log = LoggerFactory.getLogger(S3StorageService.class);

  private final S3Client s3;
  private final S3Presigner presigner;
  private final S3Properties props;

  public S3StorageService(S3Client s3, S3Presigner presigner, S3Properties props) {
    this.s3 = s3;
    this.presigner = presigner;
    this.props = props;
  }

  /**
   * Sube un stream como objeto en el bucket configurado.
   *
   * @param key clave S3 (path dentro del bucket)
   * @param input stream con el contenido (se cierra aca)
   * @param size tamaño en bytes (requerido por S3 PUT)
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
      log.debug("Uploaded s3://{}/{}", props.getBucket(), key);
    } catch (S3Exception e) {
      log.error("S3 upload failed for key {}: {}", key, e.getMessage());
      throw e;
    }
  }

  /** Elimina un objeto. No falla si no existe (idempotente). */
  public void delete(String key) {
    try {
      s3.deleteObject(DeleteObjectRequest.builder().bucket(props.getBucket()).key(key).build());
      log.debug("Deleted s3://{}/{}", props.getBucket(), key);
    } catch (S3Exception e) {
      log.error("S3 delete failed for key {}: {}", key, e.getMessage());
      throw e;
    }
  }

  /** True si el objeto existe en el bucket. */
  public boolean exists(String key) {
    try {
      s3.headObject(HeadObjectRequest.builder().bucket(props.getBucket()).key(key).build());
      return true;
    } catch (S3Exception e) {
      if (e.statusCode() == 404) {
        return false;
      }
      throw e;
    }
  }

  /**
   * URL prefirmada para descargar el objeto. La expiración sale de {@link
   * S3Properties#getPresignExpiry()} (default 15 min, configurable por entorno vía {@code
   * aws.s3.presign-expiry}).
   *
   * <p>El FE la usa para mostrar imagenes sin proxyar por el backend. En prod Railway se baja la
   * expiración a 5 min vía properties sin tocar código (RF-STO-07).
   */
  public String presignDownloadUrl(String key) {
    GetObjectPresignRequest req =
        GetObjectPresignRequest.builder()
            .signatureDuration(props.getPresignExpiry())
            .getObjectRequest(GetObjectRequest.builder().bucket(props.getBucket()).key(key).build())
            .build();
    return presigner.presignGetObject(req).url().toString();
  }

  public String getBucket() {
    return props.getBucket();
  }
}
