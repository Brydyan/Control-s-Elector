package ec.twintailcs.electoral.auth.config;

import ec.twintailcs.electoral.auth.domain.Usuario;
import ec.twintailcs.electoral.auth.web.dto.AuthResponse.UsuarioActual;
import ec.twintailcs.electoral.auth.web.dto.mapper.UsuarioMapper;
import ec.twintailcs.electoral.evidencia.domain.Evidencia;
import ec.twintailcs.electoral.evidencia.web.dto.EvidenciaResponse;
import ec.twintailcs.electoral.evidencia.web.dto.mapper.EvidenciaMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registra mappers como beans Spring.
 * Implementación inline para evitar problemas de generación MapStruct.
 */
@Configuration
public class MapperConfig {

    @Bean
    public UsuarioMapper usuarioMapper() {
        return new UsuarioMapper() {
            @Override
            public UsuarioActual toDto(Usuario entity) {
                if (entity == null) {
                    return null;
                }
                String rolNombre = entity.getRol() != null ? entity.getRol().getNombre() : null;
                return new UsuarioActual(entity.getPublicId(), entity.getEmail(), rolNombre);
            }
        };
    }

    @Bean
    public EvidenciaMapper evidenciaMapper() {
        return new EvidenciaMapper() {
            @Override
            public EvidenciaResponse toDto(Evidencia entity) {
                if (entity == null) {
                    return null;
                }
                return new EvidenciaResponse(
                    entity.getPublicId(),
                    entity.getJrvId(),
                    entity.getCargoId(),
                    entity.getUsuarioId(),
                    entity.getS3Key(),
                    entity.getSizeBytes(),
                    entity.getContentType(),
                    entity.getEstado(),
                    entity.getCreadoEn(),
                    entity.getActualizadoEn()
                );
            }
        };
    }
}
