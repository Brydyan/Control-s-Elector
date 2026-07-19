package ec.twintailcs.electoral.evidencia.web.dto;

import static org.assertj.core.api.Assertions.assertThat;

import ec.twintailcs.electoral.evidencia.domain.EstadoEvidencia;
import ec.twintailcs.electoral.evidencia.domain.Evidencia;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EvidenciaResponseTest {

    @Test
    void shouldMapPublicIdToIdFieldInResponseDto() {
        UUID publicId = UUID.fromString("0190afb0-3e09-7bd5-a2be-6c11e2f22c25");
        Evidencia evidencia = new Evidencia();
        evidencia.setPublicId(publicId);
        evidencia.setJrvId(10L);
        evidencia.setCargoId(20L);
        evidencia.setUsuarioId(30L);
        evidencia.setS3Key("actas/10/20/test.webp");
        evidencia.setSizeBytes(2048L);
        evidencia.setContentType("image/webp");
        evidencia.setEstado(EstadoEvidencia.APROBADA);
        evidencia.setCreadoEn(Instant.now());
        evidencia.setActualizadoEn(Instant.now());

        EvidenciaResponse response = EvidenciaResponse.from(evidencia);

        assertThat(response.id()).isEqualTo(publicId);
        assertThat(response.jrvId()).isEqualTo(10L);
        assertThat(response.cargoId()).isEqualTo(20L);
        assertThat(response.usuarioId()).isEqualTo(30L);
        assertThat(response.s3Key()).isEqualTo("actas/10/20/test.webp");
        assertThat(response.sizeBytes()).isEqualTo(2048L);
        assertThat(response.contentType()).isEqualTo("image/webp");
        assertThat(response.estado()).isEqualTo(EstadoEvidencia.APROBADA);
    }
}
