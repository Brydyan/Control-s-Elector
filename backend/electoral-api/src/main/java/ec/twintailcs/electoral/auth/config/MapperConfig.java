package ec.twintailcs.electoral.auth.config;

import ec.twintailcs.electoral.acta.domain.Acta;
import ec.twintailcs.electoral.acta.web.dto.ActaResponseDto;
import ec.twintailcs.electoral.acta.web.dto.mapper.ActaMapper;
import ec.twintailcs.electoral.auth.domain.Usuario;
import ec.twintailcs.electoral.auth.web.dto.AuthResponse.UsuarioActual;
import ec.twintailcs.electoral.auth.web.dto.mapper.UsuarioMapper;
import ec.twintailcs.electoral.electoral.domain.Candidato;
import ec.twintailcs.electoral.electoral.domain.Cargo;
import ec.twintailcs.electoral.electoral.domain.Partido;
import ec.twintailcs.electoral.electoral.web.dto.CandidatoResponse;
import ec.twintailcs.electoral.electoral.web.dto.CargoResponse;
import ec.twintailcs.electoral.electoral.web.dto.PartidoResponse;
import ec.twintailcs.electoral.electoral.web.dto.mapper.CandidatoMapper;
import ec.twintailcs.electoral.electoral.web.dto.mapper.CargoMapper;
import ec.twintailcs.electoral.electoral.web.dto.mapper.PartidoMapper;
import ec.twintailcs.electoral.evidencia.domain.Evidencia;
import ec.twintailcs.electoral.evidencia.web.dto.EvidenciaResponse;
import ec.twintailcs.electoral.evidencia.web.dto.mapper.EvidenciaMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registra mappers como beans Spring. Implementación inline para evitar problemas de generación
 * MapStruct.
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
            entity.getActualizadoEn());
      }
    };
  }

  @Bean
  public CargoMapper cargoMapper() {
    return new CargoMapper() {
      @Override
      public CargoResponse toDto(Cargo entity) {
        if (entity == null) {
          return null;
        }
        return new CargoResponse(
            entity.getId(), entity.getNombre(), entity.getNivel(), entity.getPuestos());
      }
    };
  }

  @Bean
  public PartidoMapper partidoMapper() {
    return new PartidoMapper() {
      @Override
      public PartidoResponse toDto(Partido entity) {
        if (entity == null) {
          return null;
        }
        return new PartidoResponse(
            entity.getId(), entity.getNombre(), entity.getSiglas(), entity.getNumeroLista());
      }
    };
  }

  @Bean
  public ActaMapper actaMapper() {
    return new ActaMapper() {
      @Override
      public ActaResponseDto toDto(Acta entity) {
        if (entity == null) {
          return null;
        }
        return new ActaResponseDto(
            entity.getId(),
            entity.getJrv().getId(),
            entity.getCargo().getId(),
            entity.getRegistradaPor().getId(),
            entity.getEstado(),
            entity.getStorageUrl(),
            entity.getTotalSufragantes(),
            entity.getVotosBlancos(),
            entity.getVotosNulos(),
            entity.getFechaRegistro(),
            entity.getMotivoAnulacion());
      }
    };
  }

  @Bean
  public CandidatoMapper candidatoMapper() {
    return new CandidatoMapper() {
      @Override
      public CandidatoResponse toDto(Candidato entity) {
        if (entity == null) {
          return null;
        }
        Long cargoId = entity.getCargo() != null ? entity.getCargo().getId() : null;
        Long partidoId = entity.getPartido() != null ? entity.getPartido().getId() : null;
        return new CandidatoResponse(
            entity.getId(), entity.getNombre(), entity.getOrden(), cargoId, partidoId);
      }
    };
  }
}
