package ec.twintailcs.electoral.auth.application;

import ec.twintailcs.electoral.auth.application.usecase.LoginUseCase;
import ec.twintailcs.electoral.auth.application.usecase.LogoutUseCase;
import ec.twintailcs.electoral.auth.application.usecase.RefreshTokenUseCase;
import org.springframework.stereotype.Service;

/**
 * Fachada para compatibilidad del módulo de autenticación. Delega en los casos de uso respectivos.
 */
@Service
public class AuthService {

  private final LoginUseCase loginUseCase;
  private final RefreshTokenUseCase refreshTokenUseCase;
  private final LogoutUseCase logoutUseCase;

  public AuthService(
      LoginUseCase loginUseCase,
      RefreshTokenUseCase refreshTokenUseCase,
      LogoutUseCase logoutUseCase) {
    this.loginUseCase = loginUseCase;
    this.refreshTokenUseCase = refreshTokenUseCase;
    this.logoutUseCase = logoutUseCase;
  }

  /** Login: valida credenciales y emite tokens. */
  public JwtService.TokenPair login(
      String email, String password, String ipCliente, String userAgent) {
    return loginUseCase.execute(email, password, ipCliente, userAgent);
  }

  /** Refresh: valida el refresh token, emite uno nuevo, rota. */
  public JwtService.TokenPair refresh(String refreshToken) {
    return refreshTokenUseCase.execute(refreshToken);
  }

  /** Logout: revoca la sesion (los refreshes futuros fallan). */
  public void logout(String refreshToken) {
    logoutUseCase.execute(refreshToken);
  }
}
