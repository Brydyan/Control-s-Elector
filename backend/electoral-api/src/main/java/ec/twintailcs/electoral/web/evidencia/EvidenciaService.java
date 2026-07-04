package ec.twintailcs.electoral.web.evidencia;

import ec.twintailcs.electoral.domain.evidencia.EstadoEvidencia;
import ec.twintailcs.electoral.domain.evidencia.Evidencia;
import ec.twintailcs.electoral.domain.evidencia.EvidenciaRepository;
import ec.twintailcs.electoral.security.Rol;
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

    public EvidenciaService(EvidenciaRepository repository, S3StorageService s3) {
        this.repository = repository;
        this.s3 = s3;
    }

    /**
     * Sube un archivo a S3 y persiste la metadata.
     *
     * @param file    multipart desde el controller
     * @param jrvId   FK al acta
     * @param cargoId FK al cargo
     * @param userId  OBSERVER que sube (de CurrentUser)
     * @return evidencia persistida con id
     */
    @Transactional
    public Evidencia upload(MultipartFile file, Long jrvId, Long cargoId, Long userId) {
        validate(file);
        String s3Key = buildKey(jrvId, cargoId);
        String sha256;
        long size = file.getSize();
        String contentType = file.getContentType();

        // Sube a S3 (try-with-resources garantiza cierre del stream).
        try (InputStream in = file.getInputStream()) {
            s3.upload(s3Key, in, size, contentType);
        } catch (IOException e) {
            throw new IllegalArgumentException("cannot read uploaded file: " + e.getMessage());
        }

        // Calcula el hash despues, leyendo otra vez (S3 cerro el input).
        sha256 = computeSha256(file);

        // Persiste. Si esto falla, compensamos borrando el objeto en S3.
        Evidencia evidencia = new Evidencia();
        evidencia.setJrvId(jrvId);
        evidencia.setCargoId(cargoId);
        evidencia.setUsuarioId(userId);
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
    public Evidencia get(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new EvidenciaNotFoundException(id));
    }

    /**
     * Cambia estado. @Transactional garantiza que el lock optimista
     * (version) detecta concurrencia: si dos moderadores aprueban al
     * mismo tiempo, una transaccion tira OptimisticLockException.
     */
    @Transactional
    public Evidencia cambiarEstado(Long id, EstadoEvidencia nuevo, Rol callerRol) {
        if (callerRol != Rol.MASTER && callerRol != Rol.COORDINATOR) {
            throw new EvidenciaAccessDeniedException(
                    "Solo COORDINATOR/MASTER puede aprobar/rechazar");
        }
        Evidencia e = repository.findById(id)
                .orElseThrow(() -> new EvidenciaNotFoundException(id));
        if (nuevo != EstadoEvidencia.APROBADA && nuevo != EstadoEvidencia.RECHAZADA) {
            throw new IllegalArgumentException(
                    "Solo APROBADA o RECHAZADA son aceptables, recibido: " + nuevo);
        }
        e.setEstado(nuevo);
        e.setActualizadoEn(Instant.now());
        return repository.save(e);
    }

    /**
     * Elimina. Solo OBSERVER duenho o MASTER pueden borrar.
     * Si S3 falla despues de DB OK, dejo orphan: sera limpiado por
     * un job batch (no hay job todavia, queda registrado en log).
     */
    @Transactional
    public void delete(Long id, Long callerId, Rol callerRol) {
        Evidencia e = repository.findById(id)
                .orElseThrow(() -> new EvidenciaNotFoundException(id));

        boolean duenho = callerRol == Rol.OBSERVER
                && e.getUsuarioId().equals(callerId);
        boolean admin = callerRol == Rol.MASTER;
        if (!duenho && !admin) {
            throw new EvidenciaAccessDeniedException(
                    "Solo el duenho de la evidencia o MASTER puede eliminarla");
        }

        // Borrado DB primero (rollback facil si algo sale mal).
        repository.delete(e);
        try {
            s3.delete(e.getS3Key());
        } catch (Exception s3Err) {
            log.error("S3 delete failed for evidencia {} (key {}); queda orphan",
                    id, e.getS3Key(), s3Err);
            // No relanzamos: el FE ya recibio la confirmacion de borrado.
            // El operador lo vera en logs.
        }
    }

    @Transactional(readOnly = true)
    public String presignDownload(Long id) {
        Evidencia e = repository.findById(id)
                .orElseThrow(() -> new EvidenciaNotFoundException(id));
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

    static String buildKey(Long jrvId, Long cargoId) {
        return "actas/" + jrvId + "/" + cargoId + "/"
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
