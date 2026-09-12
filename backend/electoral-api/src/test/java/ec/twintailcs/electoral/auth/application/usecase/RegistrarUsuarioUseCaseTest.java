package ec.twintailcs.electoral.auth.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ec.twintailcs.electoral.auth.application.EmailGenerator;
import ec.twintailcs.electoral.auth.application.PasswordHasher;
import ec.twintailcs.electoral.auth.domain.Rol;
import ec.twintailcs.electoral.auth.domain.RolRepository;
import ec.twintailcs.electoral.auth.domain.Usuario;
import ec.twintailcs.electoral.auth.domain.UsuarioInvalidoException;
import ec.twintailcs.electoral.auth.domain.UsuarioRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RegistrarUsuarioUseCaseTest {

  @Mock private UsuarioRepository usuarios;
  @Mock private RolRepository roles;
  @Mock private PasswordHasher hasher;
  @Mock private EmailGenerator emailGenerator;

  private RegistrarUsuarioUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase = new RegistrarUsuarioUseCase(usuarios, roles, hasher, emailGenerator);
  }

  @Test
  void execute_emptyPassword_throwsUsuarioInvalido() {
    assertThatThrownBy(() -> useCase.execute("Juan", "Perez", "  ", "OPERATOR"))
        .isInstanceOf(UsuarioInvalidoException.class);
  }

  @Test
  void execute_roleNotFound_throwsUsuarioInvalido() {
    when(roles.findByNombre("UNKNOWN")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.execute("Juan", "Perez", "password123", "UNKNOWN"))
        .isInstanceOf(UsuarioInvalidoException.class);
  }

  @Test
  void execute_success_hashesPasswordAndSaves() {
    Rol rol = new Rol("OPERATOR");
    when(roles.findByNombre("OPERATOR")).thenReturn(Optional.of(rol));
    when(emailGenerator.generar("Juan", "Perez", "OPERATOR")).thenReturn("juaperope@control.ttcs");
    when(hasher.hash("password123")).thenReturn("hashed_password_123");
    when(usuarios.save(any(Usuario.class))).thenAnswer(i -> i.getArgument(0));

    Usuario u = useCase.execute("Juan", "Perez", "password123", "OPERATOR");

    assertThat(u.getEmail()).isEqualTo("juaperope@control.ttcs");
    assertThat(u.getPasswordHash()).isEqualTo("hashed_password_123");
    assertThat(u.getRol()).isEqualTo(rol);
    assertThat(u.isHabilitado()).isTrue();
    verify(usuarios).save(any(Usuario.class));
  }
}
