package ec.twintailcs.electoral.auth.application;

import ec.twintailcs.electoral.common.application.ratelimit.RateLimitDecision;
import ec.twintailcs.electoral.common.application.ratelimit.RateLimitExceededException;
import ec.twintailcs.electoral.common.application.ratelimit.RateLimitPolicies;
import ec.twintailcs.electoral.common.application.ratelimit.RateLimitPolicy;
import ec.twintailcs.electoral.common.application.ratelimit.RateLimiter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Locale;
import java.util.Optional;

/**
 * Limite de intentos FALLIDOS de login por cuenta.
 *
 * <p>Dos politicas, para frenar a quien adivina contrasenas sin darle a un atacante la forma de
 * dejar afuera al observador real (los emails de los observadores no son secretos):
 *
 * <ul>
 *   <li>{@code login-account-ip} (email + IP, estricta): el atacante agota la suya, no la del
 *       observador que entra desde otra IP. Solo se aplica con IP confiable.
 *   <li>{@code login-account} (solo email, holgada): frena un ataque distribuido contra una cuenta;
 *       para bloquearla hace falta mucho volumen.
 * </ul>
 *
 * <p>{@link #checkAllowed} va ANTES de verificar la contrasena: un intento bloqueado no gasta
 * BCrypt. {@link #recordFailure} va solo si la contrasena fallo: entrar bien no consume intentos.
 * Un intento bloqueado responde 429 aunque la contrasena sea correcta.
 */
@ApplicationScoped
public class LoginAttemptGuard {

  static final String ACCOUNT_IP = "login-account-ip";
  static final String ACCOUNT = "login-account";

  @Inject RateLimiter rateLimiter;
  @Inject RateLimitPolicies policies;

  /**
   * Rechaza el intento si la cuenta ya agoto sus intentos fallidos.
   *
   * @param email email tal como llego (se normaliza)
   * @param trustedIp IP confiable del cliente, si existe
   * @throws RateLimitExceededException si alguna politica esta agotada
   */
  public void checkAllowed(String email, Optional<String> trustedIp) {
    String account = normalize(email);
    if (account.isEmpty()) {
      return;
    }
    if (trustedIp.isPresent()) {
      ensure(ACCOUNT_IP, rateLimiter.check(policy(ACCOUNT_IP), account + "|" + trustedIp.get()));
    }
    ensure(ACCOUNT, rateLimiter.check(policy(ACCOUNT), account));
  }

  /** Registra un intento fallido en las politicas que correspondan. */
  public void recordFailure(String email, Optional<String> trustedIp) {
    String account = normalize(email);
    if (account.isEmpty()) {
      return;
    }
    trustedIp.ifPresent(ip -> rateLimiter.consume(policy(ACCOUNT_IP), account + "|" + ip));
    rateLimiter.consume(policy(ACCOUNT), account);
  }

  private RateLimitPolicy policy(String name) {
    return policies.get(name);
  }

  private static void ensure(String policy, RateLimitDecision decision) {
    if (!decision.allowed()) {
      throw new RateLimitExceededException(policy, decision);
    }
  }

  private static String normalize(String email) {
    return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
  }
}
