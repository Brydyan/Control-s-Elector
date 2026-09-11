package ec.twintailcs.electoral.electoral.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ec.twintailcs.electoral.auditlog.application.AuditLogService;
import ec.twintailcs.electoral.electoral.domain.Partido;
import ec.twintailcs.electoral.electoral.domain.PartidoRepository;
import ec.twintailcs.electoral.electoral.web.ConfiguracionConflictException;
import ec.twintailcs.electoral.electoral.web.ConfiguracionNotFoundException;
import ec.twintailcs.electoral.electoral.web.dto.PartidoRequest;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ActualizarPartidoUseCaseTest {

  @Mock private PartidoRepository partidoRepository;
  @Mock private AuditLogService auditLogService;

  private ActualizarPartidoUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase = new ActualizarPartidoUseCase(partidoRepository, auditLogService);
  }

  @Test
  void execute_whenNotFound_throws() {
    when(partidoRepository.findById(1L)).thenReturn(Optional.empty());
    PartidoRequest dto = new PartidoRequest("PSC", "PSC", 6);

    assertThatThrownBy(() -> useCase.execute(1L, dto, 99L, null))
        .isInstanceOf(ConfiguracionNotFoundException.class)
        .hasMessageContaining("Partido no encontrado: 1");

    verify(partidoRepository, never()).save(any());
    verify(auditLogService, never()).registrar(any(), any(), any(), any(), any(), any());
  }

  @Test
  void execute_whenDuplicateNumeroLista_throwsConflict() {
    Partido existente = new Partido("PSC", "PSC", 6);
    existente.setId(1L);
    when(partidoRepository.findById(1L)).thenReturn(Optional.of(existente));

    Partido otro = new Partido("ID", "ID", 12);
    otro.setId(2L);
    when(partidoRepository.findByNumeroLista(12)).thenReturn(Optional.of(otro));

    PartidoRequest dto = new PartidoRequest("PSC Modificado", "PSC", 12);

    assertThatThrownBy(() -> useCase.execute(1L, dto, 99L, null))
        .isInstanceOf(ConfiguracionConflictException.class)
        .hasMessageContaining("Ya existe un partido con numeroLista 12");

    verify(partidoRepository, never()).save(any());
    verify(auditLogService, never()).registrar(any(), any(), any(), any(), any(), any());
  }

  @Test
  void execute_success_updatesAndAudits() {
    Partido existente = new Partido("PSC", "PSC", 6);
    existente.setId(1L);
    when(partidoRepository.findById(1L)).thenReturn(Optional.of(existente));
    when(partidoRepository.findByNumeroLista(6)).thenReturn(Optional.of(existente));
    when(partidoRepository.save(any(Partido.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    PartidoRequest dto = new PartidoRequest("Partido Social Cristiano", "PSC", 6);
    Partido resultado = useCase.execute(1L, dto, 99L, null);

    assertThat(resultado).isNotNull();
    assertThat(resultado.getId()).isEqualTo(1L);
    assertThat(resultado.getNombre()).isEqualTo("Partido Social Cristiano");

    verify(partidoRepository).save(existente);
    verify(auditLogService)
        .registrar(
            eq(99L),
            eq("ACTUALIZAR_PARTIDO"),
            eq("partido"),
            eq(1L),
            eq("Partido actualizado: PSC"),
            eq(null));
  }
}
