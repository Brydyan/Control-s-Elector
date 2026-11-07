package ec.twintailcs.electoral.auth.web.dto.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import ec.twintailcs.electoral.auth.domain.EstadoUsuario;
import ec.twintailcs.electoral.auth.domain.Usuario;
import ec.twintailcs.electoral.auth.web.dto.UsuarioResponseDto;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * El panel de administracion tiene que distinguir una cuenta que espera la activacion de una que ya
 * esta activa: de eso dependen la columna de casillas (solo se puede invitar a quien esta
 * pendiente) y la accion "re-enviar invitacion".
 *
 * <p>Test unitario puro: no toca base ni contexto de aplicacion.
 */
class UsuarioMapperTest {

  private final UsuarioMapper mapper = new UsuarioMapper();

  @Test
  @DisplayName("el DTO del panel expone el estado de activacion de la cuenta")
  void elDtoExponeElEstadoDeActivacion() {
    Usuario usuario = usuarioPendiente();

    UsuarioResponseDto dto = mapper.toAdminDto(usuario);

    assertThat(dto.estadoUsuario()).isEqualTo("PENDIENTE_INVITACION");
  }

  @Test
  @DisplayName("una cuenta activa no se confunde con una pendiente")
  void unaCuentaActivaSeDistingueDeUnaPendiente() {
    Usuario usuario = usuarioPendiente();
    usuario.setEstadoUsuario(EstadoUsuario.ACTIVO);

    UsuarioResponseDto dto = mapper.toAdminDto(usuario);

    assertThat(dto.estadoUsuario()).isEqualTo("ACTIVO");
  }

  @Test
  @DisplayName("el DTO dice cuando salio la ultima invitacion")
  void elDtoDiceCuandoSalioLaUltimaInvitacion() {
    Instant envio = Instant.parse("2026-11-05T17:40:00Z");
    Usuario usuario = usuarioPendiente();
    usuario.setInvitationSentAt(envio);

    UsuarioResponseDto dto = mapper.toAdminDto(usuario);

    assertThat(dto.invitationSentAt()).isEqualTo(envio);
  }

  @Test
  @DisplayName("una cuenta nunca invitada viaja sin fecha de envio")
  void unaCuentaNuncaInvitadaViajaSinFechaDeEnvio() {
    UsuarioResponseDto dto = mapper.toAdminDto(usuarioPendiente());

    assertThat(dto.invitationSentAt()).isNull();
  }

  /** Cuenta creada y esperando la activacion: el estado en el que se puede invitar. */
  private Usuario usuarioPendiente() {
    Usuario usuario = new Usuario();
    usuario.setEmail("pendiente@cne.gob.ec");
    usuario.setNombre("Ana");
    usuario.setApellido("Perez");
    usuario.setEstadoUsuario(EstadoUsuario.PENDIENTE_INVITACION);
    return usuario;
  }
}
