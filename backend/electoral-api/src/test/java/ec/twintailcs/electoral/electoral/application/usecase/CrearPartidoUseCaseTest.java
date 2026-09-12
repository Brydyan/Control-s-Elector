package ec.twintailcs.electoral.electoral.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ec.twintailcs.electoral.auditlog.application.AuditLogService;
import ec.twintailcs.electoral.electoral.domain.ConfiguracionConflictException;
import ec.twintailcs.electoral.electoral.domain.Partido;
import ec.twintailcs.electoral.electoral.domain.PartidoRepository;
import ec.twintailcs.electoral.electoral.web.dto.PartidoRequest;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CrearPartidoUseCaseTest {

  @Mock private PartidoRepository partidoRepository;
  @Mock private AuditLogService auditLogService;

  private CrearPartidoUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase = new CrearPartidoUseCase(partidoRepository, auditLogService);
  }

  @Test
  void execute_withDuplicateNumeroLista_throwsConflict() {
    Partido existente = new Partido("Alianza PAIS", "AP", 35);
    existente.setId(2L);
    when(partidoRepository.findByNumeroLista(35)).thenReturn(Optional.of(existente));
    PartidoRequest dto = new PartidoRequest("Otro Partido", "OP", 35);

    assertThatThrownBy(() -> useCase.execute(dto, 99L, null))
        .isInstanceOf(ConfiguracionConflictException.class)
        .hasMessageContaining("Ya existe un partido con numeroLista 35");

    verify(partidoRepository, never()).save(any());
    verify(auditLogService, never()).registrar(any(), any(), any(), any(), any(), any());
  }

  @Test
  void execute_savesAndAudits() {
    when(partidoRepository.findByNumeroLista(6)).thenReturn(Optional.empty());
    PartidoRequest dto = new PartidoRequest("PSC", "PSC", 6);
    Partido guardado = new Partido(dto.nombre(), dto.siglas(), dto.numeroLista());
    guardado.setId(3L);
    when(partidoRepository.save(any(Partido.class))).thenReturn(guardado);

    Partido resultado = useCase.execute(dto, 99L, null);

    assertThat(resultado).isNotNull();
    assertThat(resultado.getId()).isEqualTo(3L);
    assertThat(resultado.getNombre()).isEqualTo("PSC");
    assertThat(resultado.getSiglas()).isEqualTo("PSC");
    assertThat(resultado.getNumeroLista()).isEqualTo(6);

    verify(partidoRepository).save(any(Partido.class));
    verify(auditLogService)
        .registrarConIp(
            eq(99L),
            eq("CREAR_PARTIDO"),
            eq("partido"),
            eq(3L),
            eq("Partido creado: PSC"),
            eq(null));
  }
}
