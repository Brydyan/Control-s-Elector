package ec.twintailcs.electoral.evidencia.application.usecase;

import ec.twintailcs.electoral.evidencia.application.ArchivoEvidencia;
import ec.twintailcs.electoral.evidencia.application.AuditoriaEvidencia;
import ec.twintailcs.electoral.evidencia.domain.ActaEvidencia;
import ec.twintailcs.electoral.evidencia.domain.ActaEvidenciaRepository;
import ec.twintailcs.electoral.evidencia.domain.EstadoEvidencia;
import ec.twintailcs.electoral.evidencia.domain.Evidencia;
import ec.twintailcs.electoral.evidencia.domain.EvidenciaInvalidaException;
import ec.twintailcs.electoral.storage.StorageException;
import ec.twintailcs.electoral.storage.StorageService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Status;
import jakarta.transaction.Synchronization;
import jakarta.transaction.Transaction;
import jakarta.transaction.TransactionManager;
import jakarta.transaction.Transactional;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.UUID;
import org.jboss.logging.Logger;

/**
 * Sube una evidencia fotografica y la deja registrada en la base.
 *
 * <p>Port exacto del {@code SubirEvidenciaUseCase} del backend Spring: mismas validaciones, misma
 * clave ({@code actas/{actaId}/{uuid}.webp}), mismo SHA-256 y misma compensacion (si la
 * transaccion falla, el objeto se borra del storage).
 *
 * <p><b>Desacoplamiento:</b> Usa {@link StorageService} en lugar de S3StorageService directamente,
 * permitiendo:
 * <ul>
 *   <li>Cambiar providers (S3 → MinIO → Local) sin refactor
 *   <li>Implementar fallback automático (S3 → Local)
 *   <li>Testear con storage mock
 * </ul>
 */
@ApplicationScoped
public class SubirEvidenciaUseCase {

  private static final Logger log = Logger.getLogger(SubirEvidenciaUseCase.class);

  /** Tamano maximo del archivo (10 MB), el mismo limite del backend Spring. */
  private static final long MAX_SIZE_BYTES = 10L * 1024 * 1024;

  /** Prefijo de content type admitido. */
  private static final String ALLOWED_MIME_PREFIX = "image/";

  @Inject StorageService storage;
  @Inject ActaEvidenciaRepository actaRepository;
  @Inject AuditoriaEvidencia auditoria;
  @Inject TransactionManager transactionManager;

  /**
   * Sube el archivo y registra la evidencia.
   *
   * @param archivo archivo multipart ya leido
   * @param actaId id interno del acta a la que se asocia
   * @param usuarioId id interno del usuario que sube
   * @param rolNombre rol del usuario que sube
   * @param ip direccion IP del cliente
   */
  @Transactional
  public Evidencia execute(
      ArchivoEvidencia archivo, Long actaId, Long usuarioId, String rolNombre, String ip) {
    validar(archivo);

    ActaEvidencia acta =
        actaRepository
            .buscarPorId(actaId)
            .orElseThrow(() -> new EvidenciaInvalidaException("Acta no encontrada: " + actaId));

    if (acta.anulada()) {
      throw new EvidenciaInvalidaException("No se pueden subir evidencias a un acta ANULADA");
    }

    boolean esRegistrador = acta.registradaPor() != null && acta.registradaPor().equals(usuarioId);
    boolean esPrivilegiado = "COORDINATOR".equals(rolNombre) || "MASTER".equals(rolNombre);
    if (!esRegistrador && !esPrivilegiado) {
      throw new EvidenciaInvalidaException(
          "Solo el registrador del acta (o COORDINATOR/MASTER) puede subir fotos");
    }

    String s3Key = buildKey(actaId);
    long size = archivo.size();
    String contentType = archivo.contentType();
    String sha256 = computeSha256(archivo.contenido());

    try {
      storage.upload(s3Key, new ByteArrayInputStream(archivo.contenido()), size, contentType);
    } catch (IOException e) {
      throw new EvidenciaInvalidaException("cannot read uploaded file: " + e.getMessage());
    } catch (StorageException e) {
      // StorageService.upload() lanza StorageException si las credenciales fallan,
      // conectividad se pierde, etc. Se traduce a 400 "evidencia inválida" en lugar
      // de 500 "internal error" para que la suite de contrato pueda diferenciar entre
      // problemas de configuracion y problemas de entrada del cliente.
      // Incluir tipo de error para debugging.
      throw new EvidenciaInvalidaException(
          String.format("Storage upload failed [%s]: %s", e.getErrorType(), e.getMessage()));
    }

    // La compensacion de storage se engancha al final de la transaccion, no al persist().
    // Si la transaccion ROLLBACKea (commit fallido o excepcion temprana), el objeto de storage se
    // limpia: si no, queda huerfano y el usuario cree que subio la foto.
    programarLimpiezaSiHayRollback(s3Key);

    Evidencia evidencia = new Evidencia();
    evidencia.setActaId(acta.id());
    evidencia.setJrvId(acta.jrvId());
    evidencia.setCargoId(acta.cargoId());
    evidencia.setUsuarioId(usuarioId);
    evidencia.setS3Key(s3Key);
    evidencia.setSizeBytes(size);
    evidencia.setContentType(contentType);
    evidencia.setSha256(sha256);
    evidencia.setEstado(EstadoEvidencia.EN_REVISION);
    evidencia.setCreadoEn(Instant.now());
    evidencia.setActualizadoEn(Instant.now());
    evidencia.validarDatosObligatorios();
    evidencia.persist();

    // La auditoria va DENTRO de la transaccion: si algo falla despues, el audit hace rollback
    // junto con la evidencia.
    auditoria.registrar(
        usuarioId, "EVIDENCIA_SUBIDA", evidencia.getId(), "Evidencia subida s3Key=" + s3Key, ip);

    return evidencia;
  }

  /** Valida el archivo antes de tocar S3 o la base. */
  private void validar(ArchivoEvidencia archivo) {
    if (archivo == null || archivo.size() <= 0) {
      throw new EvidenciaInvalidaException("archivo vacio");
    }
    if (archivo.size() > MAX_SIZE_BYTES) {
      throw new EvidenciaInvalidaException(
          "archivo excede tamano maximo (" + MAX_SIZE_BYTES + " bytes)");
    }
    String contentType = archivo.contentType();
    if (contentType == null || !contentType.startsWith(ALLOWED_MIME_PREFIX)) {
      throw new EvidenciaInvalidaException(
          "Content-Type debe ser image/*, recibido: " + contentType);
    }
  }

  /** Clave del objeto en el bucket. La impone el backend, no el cliente. */
  static String buildKey(Long actaId) {
    return "actas/" + actaId + "/" + UUID.randomUUID() + ".webp";
  }

  /** SHA-256 en hexadecimal del contenido subido. */
  private String computeSha256(byte[] contenido) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] digest = md.digest(contenido);
      StringBuilder sb = new StringBuilder(digest.length * 2);
      for (byte b : digest) {
        sb.append(String.format("%02x", b));
      }
      return sb.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }

  /**
   * Registra la limpieza del objeto de storage si la transaccion termina en rollback.
   *
   * <p>Si no hay transaccion activa, no se registra nada (mismo guard que el backend Spring).
   */
  private void programarLimpiezaSiHayRollback(String s3Key) {
    try {
      Transaction transaccion = transactionManager.getTransaction();
      if (transaccion == null
          || transaccion.getStatus() == Status.STATUS_NO_TRANSACTION
          || transaccion.getStatus() != Status.STATUS_ACTIVE) {
        return;
      }
      transaccion.registerSynchronization(
          new Synchronization() {
            @Override
            public void beforeCompletion() {
              // nada que hacer antes del commit
            }

            @Override
            public void afterCompletion(int status) {
              if (status == Status.STATUS_ROLLEDBACK) {
                try {
                  storage.delete(s3Key);
                  log.infof(
                      "Storage cleanup after rollback for key %s (evidencia NOT persisted)", s3Key);
                } catch (StorageException cleanupFailed) {
                  log.errorf(
                      cleanupFailed,
                      "Storage cleanup FAILED after rollback for key %s; orphan remains",
                      s3Key);
                }
              }
            }
          });
    } catch (Exception e) {
      log.warnf(e, "No se pudo registrar la limpieza de storage para la clave %s", s3Key);
    }
  }
}
