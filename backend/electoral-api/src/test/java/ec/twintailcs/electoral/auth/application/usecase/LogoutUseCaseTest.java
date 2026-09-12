package ec.twintailcs.electoral.auth.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ec.twintailcs.electoral.auditlog.application.AuditLogService;
import ec.twintailcs.electoral.auth.domain.Sesion;
import ec.twintailcs.electoral.auth.domain.SesionRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LogoutUseCaseTest {

  @Mock private SesionRepository sesiones;
  @Mock private TokenHasher tokenHasher;
  @Mock private AuditLogService auditLog;

  private LogoutUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase = new LogoutUseCase(sesiones, tokenHasher, auditLog);
  }

  @Test
  void execute_sessionNotFound_doesNothing() {
    when(tokenHasher.hashOf("refresh_tok")).thenReturn("hash_val");
    when(sesiones.findByRefreshHash("hash_val")).thenReturn(Optional.empty());

    useCase.execute("refresh_tok");

    verify(sesiones, never()).save(org.mockito.ArgumentMatchers.any());
    verify(auditLog, never()).recordLogout(org.mockito.ArgumentMatchers.anyLong());
  }

  @Test
  void execute_sessionFound_revokesSession() {
    when(tokenHasher.hashOf("refresh_tok")).thenReturn("hash_val");
    Sesion sesion = new Sesion();
    sesion.setUsuarioId(12L);
    sesion.setRevocado(false);
    when(sesiones.findByRefreshHash("hash_val")).thenReturn(Optional.of(sesion));

    useCase.execute("refresh_tok");

    assertThat(sesion.isRevocado()).isTrue();
    verify(sesiones).save(sesion);
    verify(auditLog).recordLogout(12L);
  }
}
