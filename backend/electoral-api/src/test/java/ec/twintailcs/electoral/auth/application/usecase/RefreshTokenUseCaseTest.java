package ec.twintailcs.electoral.auth.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nimbusds.jwt.JWTClaimsSet;
import ec.twintailcs.electoral.auditlog.application.AuditLogService;
import ec.twintailcs.electoral.auth.application.JwtService;
import ec.twintailcs.electoral.auth.domain.AuthException;
import ec.twintailcs.electoral.auth.domain.Rol;
import ec.twintailcs.electoral.auth.domain.RolPermisoRepository;
import ec.twintailcs.electoral.auth.domain.Sesion;
import ec.twintailcs.electoral.auth.domain.SesionRepository;
import ec.twintailcs.electoral.auth.domain.Usuario;
import ec.twintailcs.electoral.auth.domain.UsuarioRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RefreshTokenUseCaseTest {

  @Mock private UsuarioRepository usuarios;
  @Mock private SesionRepository sesiones;
  @Mock private RolPermisoRepository rolPermisos;
  @Mock private JwtService jwt;
  @Mock private TokenHasher tokenHasher;
  @Mock private AuditLogService auditLog;

  private RefreshTokenUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase = new RefreshTokenUseCase(usuarios, sesiones, rolPermisos, jwt, tokenHasher, auditLog);
  }

  @Test
  void execute_invalidJwt_throwsAuthException() {
    when(jwt.parseRefresh("invalid_token"))
        .thenThrow(new JwtService.InvalidJwtException("bad token"));

    assertThatThrownBy(() -> useCase.execute("invalid_token")).isInstanceOf(AuthException.class);
    verify(auditLog).recordRefreshFail(null);
  }

  @Test
  void execute_sessionNotFound_throwsAuthException() {
    UUID publicId = UUID.randomUUID();
    JWTClaimsSet claims =
        new JWTClaimsSet.Builder()
            .subject(publicId.toString())
            .claim("email", "user@domain.com")
            .claim("rol", "OPERATOR")
            .claim("sid", "sesion-123")
            .build();
    JwtService.ParsedJwt parsed = new JwtService.ParsedJwt(claims);
    when(jwt.parseRefresh("valid_token")).thenReturn(parsed);
    when(tokenHasher.hashOf("valid_token")).thenReturn("token_hash");
    when(sesiones.findByRefreshHash("token_hash")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.execute("valid_token"))
        .isInstanceOf(AuthException.class)
        .hasMessage("Refresh token invalido o ya usado");
    verify(auditLog).recordRefreshFail(null);
  }

  @Test
  void execute_success_rotatesAndIssuesNewPair() {
    UUID publicId = UUID.randomUUID();
    JWTClaimsSet claims =
        new JWTClaimsSet.Builder()
            .subject(publicId.toString())
            .claim("email", "user@domain.com")
            .claim("rol", "OPERATOR")
            .claim("sid", "sesion-123")
            .build();
    JwtService.ParsedJwt parsed = new JwtService.ParsedJwt(claims);
    when(jwt.parseRefresh("valid_token")).thenReturn(parsed);
    when(tokenHasher.hashOf("valid_token")).thenReturn("token_hash");

    Sesion sesion = new Sesion();
    sesion.setUsuarioId(10L);
    sesion.setSesionId("sesion-123");
    sesion.setExpiraEn(Instant.now().plusSeconds(3600));
    sesion.setRevocado(false);
    when(sesiones.findByRefreshHash("token_hash")).thenReturn(Optional.of(sesion));

    Usuario u = new Usuario();
    u.setId(10L);
    u.setPublicId(publicId);
    u.setEmail("user@domain.com");
    u.setHabilitado(true);
    Rol rol = new Rol("OPERATOR");
    u.setRol(rol);
    when(usuarios.findByPublicId(publicId)).thenReturn(Optional.of(u));
    when(rolPermisos.findAuthorityStringsByUsuarioId(10L)).thenReturn(List.of("permiso:read"));

    JwtService.TokenPair newPair =
        new JwtService.TokenPair(
            "new_access", "new_refresh", Duration.ofMinutes(15), Duration.ofDays(7), "sesion-123");
    when(jwt.issue(
            eq(publicId),
            eq("user@domain.com"),
            eq("OPERATOR"),
            eq(List.of("permiso:read")),
            eq("sesion-123")))
        .thenReturn(newPair);
    when(tokenHasher.hashOf("new_refresh")).thenReturn("new_refresh_hash");

    JwtService.TokenPair res = useCase.execute("valid_token");

    assertThat(res).isEqualTo(newPair);
    verify(sesiones).save(sesion);
    verify(auditLog).recordRefresh(10L);
  }
}
