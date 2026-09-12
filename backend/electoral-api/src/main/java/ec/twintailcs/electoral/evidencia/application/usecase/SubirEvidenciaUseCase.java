package ec.twintailcs.electoral.evidencia.application.usecase;

import ec.twintailcs.electoral.acta.domain.Acta;
import ec.twintailcs.electoral.acta.domain.ActaRepository;
import ec.twintailcs.electoral.acta.domain.EstadoActa;
import ec.twintailcs.electoral.auth.domain.Usuario;
import ec.twintailcs.electoral.auth.domain.UsuarioRepository;
import ec.twintailcs.electoral.evidencia.domain.EstadoEvidencia;
import ec.twintailcs.electoral.evidencia.domain.Evidencia;
import ec.twintailcs.electoral.evidencia.domain.EvidenciaInvalidaException;
import ec.twintailcs.electoral.evidencia.domain.EvidenciaRepository;
import ec.twintailcs.electoral.storage.s3.S3StorageService;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class SubirEvidenciaUseCase {

  private static final Logger log = LoggerFactory.getLogger(SubirEvidenciaUseCase.class);
  private static final long MAX_SIZE_BYTES = 10L * 1024 * 1024; // 10 MB
  private static final String ALLOWED_MIME_PREFIX = "image/";

  private final EvidenciaRepository repository;
  private final S3StorageService s3;
  private final UsuarioRepository usuarios;
  private final ActaRepository actaRepository;

  public SubirEvidenciaUseCase(
      EvidenciaRepository repository,
      S3StorageService s3,
      UsuarioRepository usuarios,
      ActaRepository actaRepository) {
    this.repository = repository;
    this.s3 = s3;
    this.usuarios = usuarios;
    this.actaRepository = actaRepository;
  }

  @Transactional
  public Evidencia execute(MultipartFile file, Long actaId, UUID callerPublicId) {
    validate(file);

    Usuario u =
        usuarios
            .findByPublicId(callerPublicId)
            .orElseThrow(() -> new EvidenciaInvalidaException("Usuario no existe"));

    Acta acta =
        actaRepository
            .findById(actaId)
            .orElseThrow(() -> new EvidenciaInvalidaException("Acta no encontrada: " + actaId));

    if (acta.getEstado() == EstadoActa.ANULADA) {
      throw new EvidenciaInvalidaException("No se pueden subir evidencias a un acta ANULADA");
    }

    String rol = u.getRolNombre();
    boolean esRegistrador =
        acta.getRegistradaPor() != null && acta.getRegistradaPor().getId().equals(u.getId());
    boolean esPrivilegiado = "COORDINATOR".equals(rol) || "MASTER".equals(rol);
    if (!esRegistrador && !esPrivilegiado) {
      throw new EvidenciaInvalidaException(
          "Solo el registrador del acta (o COORDINATOR/MASTER) puede subir fotos");
    }

    String s3Key = buildKey(actaId);
    long size = file.getSize();
    String contentType = file.getContentType();
    String sha256 = computeSha256(file);

    try (InputStream in = file.getInputStream()) {
      s3.upload(s3Key, in, size, contentType);
    } catch (IOException e) {
      throw new EvidenciaInvalidaException("cannot read uploaded file: " + e.getMessage());
    }

    Evidencia evidencia = new Evidencia();
    evidencia.setActa(acta);
    evidencia.setJrvId(acta.getJrv().getId());
    evidencia.setCargoId(acta.getCargo().getId());
    evidencia.setUsuarioId(u.getId());
    evidencia.setS3Key(s3Key);
    evidencia.setSizeBytes(size);
    evidencia.setContentType(contentType);
    evidencia.setSha256(sha256);
    evidencia.setEstado(EstadoEvidencia.EN_REVISION);
    evidencia.setCreadoEn(Instant.now());
    evidencia.setActualizadoEn(Instant.now());
    evidencia.validarDatosObligatorios();

    try {
      return repository.save(evidencia);
    } catch (RuntimeException dbError) {
      log.error("DB insert failed after S3 upload, cleaning up S3 object {}", s3Key, dbError);
      try {
        s3.delete(s3Key);
      } catch (Exception s3Cleanup) {
        log.error("S3 cleanup failed after DB failure for {}", s3Key, s3Cleanup);
      }
      throw dbError;
    }
  }

  private void validate(MultipartFile file) {
    if (file == null || file.isEmpty()) {
      throw new EvidenciaInvalidaException("archivo vacio");
    }
    if (file.getSize() > MAX_SIZE_BYTES) {
      throw new EvidenciaInvalidaException(
          "archivo excede tamano maximo (" + MAX_SIZE_BYTES + " bytes)");
    }
    String ct = file.getContentType();
    if (ct == null || !ct.startsWith(ALLOWED_MIME_PREFIX)) {
      throw new EvidenciaInvalidaException("Content-Type debe ser image/*, recibido: " + ct);
    }
  }

  static String buildKey(Long actaId) {
    return "actas/" + actaId + "/" + UUID.randomUUID() + ".webp";
  }

  private static String computeSha256(MultipartFile file) {
    try (InputStream in = file.getInputStream()) {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] buf = new byte[8192];
      int r;
      while ((r = in.read(buf)) > 0) {
        md.update(buf, 0, r);
      }
      byte[] digest = md.digest();
      StringBuilder sb = new StringBuilder(digest.length * 2);
      for (byte b : digest) {
        sb.append(String.format("%02x", b));
      }
      return sb.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    } catch (IOException e) {
      throw new EvidenciaInvalidaException("cannot read file for hashing");
    }
  }
}
