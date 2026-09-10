package ec.twintailcs.electoral.evidencia.application;

import ec.twintailcs.electoral.acta.domain.Acta;
import ec.twintailcs.electoral.acta.domain.ActaRepository;
import ec.twintailcs.electoral.evidencia.domain.EstadoEvidencia;
import ec.twintailcs.electoral.evidencia.domain.Evidencia;
import ec.twintailcs.electoral.evidencia.domain.EvidenciaRepository;
import ec.twintailcs.electoral.evidencia.web.EvidenciaNotFoundException;
import ec.twintailcs.electoral.storage.s3.S3StorageService;
import ec.twintailcs.electoral.auth.domain.UsuarioRepository;
import ec.twintailcs.electoral.auth.domain.Usuario;
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

/**
 * Logica de negocio para subir, leer, aprobar y eliminar evidencias.
 *
 * Reglas transaccionales:
 *  1. Subir: subir a S3 PRIMERO, despes insertar en DB. Si la DB falla,
 *     borramos el objeto en S3 (compensation / cleanup).
 *  2. Borrar: borrar en DB PRIMERO, despes en S3. Si la DB falla no
 *     tocamos S3. Si S3 falla despues de DB OK, dejamos orphan y
 *     devolvemos excepcion: un batch de limpieza lo recogera despues.
 *  3. Aprobar/Rechazar: solo actualiza DB, no toca S3.
 *
 * El upload a S3 usa el cliente singleton (S3StorageService) inyectado.
 * Streams se leen una sola vez (multipart.getInputStream) por lo que
 * calculamos el SHA-256 y subimos a partir de la misma lectura cuando
 * es posible. Para no complicar esta primera version, hacemos dos
 * pasadas: hash y upload por separado. Si rendimiento lo requiere,
 * se cambia despues a un MultipartFile.getBytes() + hash incremental.
 */
@Service
public class EvidenciaService {

    private static final Logger log = LoggerFactory.getLogger(EvidenciaService.class);
    private static final long MAX_SIZE_BYTES = 10L * 1024 * 1024;        // 10 MB
    private static final String ALLOWED_MIME_PREFIX = "image/";

    private final EvidenciaRepository repository;
    private final S3StorageService s3;
    private final UsuarioRepository usuarios;
    private final ActaRepository actaRepository;

    public EvidenciaService(EvidenciaRepository repository, S3StorageService s3,
                              UsuarioRepository usuarios, ActaRepository actaRepository) {
        this.repository = repository;
        this.s3 = s3;
        this.usuarios = usuarios;
        this.actaRepository = actaRepository;
    }

    /**
     * Sube un archivo a S3 y persiste la metadata vinculada al acta.
     *
     * <p>T-BE-14 cerrado: cada evidencia ahora pertenece a un acta específico
     * (FK NOT NULL definida en 013-t-be-14-16.sql). El service valida que
     * el acta exista, no esté ANULADA, y que el usuario sea el registrador
     * del acta (o COORDINATOR/MASTER, que pueden agregar fotos de revisión).
     *
     * @param file          multipart desde el controller
     * @param actaId        FK al acta
     * @param callerPublicId OBSERVER que sube (de CurrentUser)
     * @return evidencia persistida
     */
    @Transactional
    public Evidencia upload(MultipartFile file, Long actaId, UUID callerPublicId) {
        validate(file);

        Usuario u = usuarios.findByPublicId(callerPublicId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no existe"));

        Acta acta = actaRepository.findById(actaId)
                .orElseThrow(() -> new IllegalArgumentException("Acta no encontrada: " + actaId));
        if (acta.getEstado() == ec.twintailcs.electoral.acta.domain.EstadoActa.ANULADA) {
            throw new IllegalArgumentException("No se pueden subir evidencias a un acta ANULADA");
        }
        // El registrador del acta puede subir fotos. COORDINATOR/MASTER también.
        String rol = u.getRolNombre();
        boolean esRegistrador = acta.getRegistradaPor().getId().equals(u.getId());
        boolean esPrivilegiado = "COORDINATOR".equals(rol) || "MASTER".equals(rol);
        if (!esRegistrador && !esPrivilegiado) {
            throw new IllegalArgumentException(
                    "Solo el registrador del acta (o COORDINATOR/MASTER) puede subir fotos");
        }

        String s3Key = buildKey(actaId);

        long size = file.getSize();
        String contentType = file.getContentType();
        String sha256 = computeSha256(file);

        // Sube a S3 primero; si la DB falla, compensamos.
        try (InputStream in = file.getInputStream()) {
            s3.upload(s3Key, in, size, contentType);
        } catch (IOException e) {
            throw new IllegalArgumentException("cannot read uploaded file: " + e.getMessage());
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

        try {
            return repository.save(evidencia);
        } catch (RuntimeException dbError) {
            log.error("DB insert failed after S3 upload, cleaning up S3 object {}",
                    s3Key, dbError);
            try {
                s3.delete(s3Key);
            } catch (Exception s3Cleanup) {
                log.error("S3 cleanup failed after DB failure for {}", s3Key, s3Cleanup);
            }
            throw dbError;
        }
    }

    @Transactional(readOnly = true)
    public Evidencia get(UUID publicId) {
        return repository.findByPublicId(publicId)
                .orElseThrow(() -> new EvidenciaNotFoundException(publicId));
    }

    /**
     * Cambia estado. La autorización la hace @PreAuthorize en el controller
     * (requiere acta:aprobar o acta:rechazar — COORDINATOR/MASTER).
     * El lock optimista (version) detecta concurrencia.
     */
    @Transactional
    public Evidencia cambiarEstado(UUID publicId, EstadoEvidencia nuevo) {
        Evidencia e = repository.findByPublicId(publicId)
                .orElseThrow(() -> new EvidenciaNotFoundException(publicId));
        if (nuevo != EstadoEvidencia.APROBADA && nuevo != EstadoEvidencia.RECHAZADA) {
            throw new IllegalArgumentException(
                    "Solo APROBADA o RECHAZADA son aceptables, recibido: " + nuevo);
        }
        e.setEstado(nuevo);
        e.setActualizadoEn(Instant.now());
        return repository.save(e);
    }

    /**
     * Elimina. @PreAuthorize en el controller garantiza que solo quien
     * tenga {@code evidencia:delete} (MASTER) puede borrar. El callerId
     * es solo para log/registro, no para control de acceso (los permisos
     * estan en la DB, no hardcodeados).
     */
    @Transactional
    public void delete(UUID publicId, UUID callerPublicId) {
        Evidencia e = repository.findByPublicId(publicId)
                .orElseThrow(() -> new EvidenciaNotFoundException(publicId));

        // Borrado DB primero (rollback facil si algo sale mal).
        repository.delete(e);
        try {
            s3.delete(e.getS3Key());
        } catch (Exception s3Err) {
            log.error("S3 delete failed for evidencia with public ID {} (key {}); queda orphan",
                    publicId, e.getS3Key(), s3Err);
            // No relanzamos: el FE ya recibio la confirmacion de borrado.
            // El operador lo vera en logs.
        }
    }

    @Transactional(readOnly = true)
    public String presignDownload(UUID publicId) {
        Evidencia e = repository.findByPublicId(publicId)
                .orElseThrow(() -> new EvidenciaNotFoundException(publicId));
        return s3.presignDownloadUrl(e.getS3Key());
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("archivo vacio");
        }
        if (file.getSize() > MAX_SIZE_BYTES) {
            throw new IllegalArgumentException(
                    "archivo excede tamano maximo (" + MAX_SIZE_BYTES + " bytes)");
        }
        String ct = file.getContentType();
        if (ct == null || !ct.startsWith(ALLOWED_MIME_PREFIX)) {
            throw new IllegalArgumentException(
                    "Content-Type debe ser image/*, recibido: " + ct);
        }
    }

    static String buildKey(Long actaId) {
        return "actas/" + actaId + "/"
                + UUID.randomUUID() + ".webp";
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
            throw new IllegalArgumentException("cannot read file for hashing", e);
        }
    }
}
