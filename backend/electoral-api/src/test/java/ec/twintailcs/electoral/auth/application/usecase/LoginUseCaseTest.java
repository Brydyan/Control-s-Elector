package ec.twintailcs.electoral.auth.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ec.twintailcs.electoral.auditlog.application.AuditLogService;
import ec.twintailcs.electoral.auth.application.JwtService;
import ec.twintailcs.electoral.auth.application.PasswordHasher;
import ec.twintailcs.electoral.auth.domain.AuthException;
import ec.twintailcs.electoral.auth.domain.Rol;
import ec.twintailcs.electoral.auth.domain.RolPermisoRepository;
import ec.twintailcs.electoral.auth.domain.Sesion;
import ec.twintailcs.electoral.auth.domain.SesionRepository;
import ec.twintailcs.electoral.auth.domain.Usuario;
import ec.twintailcs.electoral.auth.domain.UsuarioRepository;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LoginUseCaseTest {

  @Mock private UsuarioRepository usuarios;
  @Mock private SesionRepository sesiones;
  @Mock private RolPermisoRepository rolPermisos;
  @Mock private JwtService jwt;
  @Mock private PasswordHasher hasher;
  @Mock private TokenHasher tokenHasher;
  @Mock private AuditLogService auditLog;

  private LoginUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase = new LoginUseCase(usuarios, sesiones, rolPermisos, jwt, hasher, tokenHasher, auditLog);
  }

  @Test
  void execute_userNotFound_throwsAuthException() {
    when(usuarios.findByEmail("nonexistent@domain.com")).thenReturn(Optional.empty());

    assertThatThrownBy(
            () -> useCase.execute("nonexistent@domain.com", "pass", "127.0.0.1", "agent"))
        .isInstanceOf(AuthException.class)
        .hasMessage("Credenciales invalidas");

    verify(auditLog).recordLoginFail("nonexistent@domain.com", "127.0.0.1");
  }

  @Test
  void execute_passwordMismatch_throwsAuthException() {
    Usuario u = new Usuario();
    u.setEmail("user@domain.com");
    u.setHabilitado(true);
    u.setPasswordHash("hashed_pass");
    when(usuarios.findByEmail("user@domain.com")).thenReturn(Optional.of(u));
    when(hasher.matches("wrong_pass", "hashed_pass")).thenReturn(false);

    assertThatThrownBy(() -> useCase.execute("user@domain.com", "wrong_pass", "127.0.0.1", "agent"))
        .isInstanceOf(AuthException.class);

    verify(auditLog).recordLoginFail("user@domain.com", "127.0.0.1");
  }

  @Test
  void execute_success_createsSessionAndIssuesTokens() {
    UUID publicId = UUID.randomUUID();
    Usuario u = new Usuario();
    u.setId(5L);
    u.setPublicId(publicId);
    u.setEmail("user@domain.com");
    u.setHabilitado(true);
    u.setPasswordHash("hashed_pass");
    Rol rol = new Rol("OPERATOR");
    u.setRol(rol);

    when(usuarios.findByEmail("user@domain.com")).thenReturn(Optional.of(u));
    when(hasher.matches("secret", "hashed_pass")).thenReturn(true);
    when(rolPermisos.findAuthorityStringsByUsuarioId(5L)).thenReturn(List.of("permiso:read"));
    JwtService.TokenPair pair =
        new JwtService.TokenPair(
            "access_jwt", "refresh_jwt", Duration.ofMinutes(15), Duration.ofDays(7), "sesion-123");
    when(jwt.issue(
            eq(publicId),
            eq("user@domain.com"),
            eq("OPERATOR"),
            eq(List.of("permiso:read")),
            anyString()))
        .thenReturn(pair);
    when(tokenHasher.hashOf("refresh_jwt")).thenReturn("refresh_hash_123");

    JwtService.TokenPair result =
        useCase.execute("user@domain.com", "secret", "127.0.0.1", "agent");

    assertThat(result).isEqualTo(pair);
    verify(sesiones, org.mockito.Mockito.atLeastOnce()).save(any(Sesion.class));
    verify(auditLog).recordLogin(5L, "127.0.0.1");
  }
}
