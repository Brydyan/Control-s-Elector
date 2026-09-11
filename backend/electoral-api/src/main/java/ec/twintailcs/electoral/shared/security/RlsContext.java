package ec.twintailcs.electoral.shared.security;

/**
 * Holder en ThreadLocal para el contexto de RLS del request actual.
 *
 * <p>Lo puebla {@link RlsAuthenticationBridge} después de que el filtro JWT identificó al usuario,
 * y lo consume {@link RlsTransactionExecutionListener} al abrir una transacción.
 *
 * <p>{@link RlsAuthenticationBridge} siempre llama a {@link #clear()} en el bloque finally, así que
 * no hay riesgo de leak entre requests que comparten hilo del pool de Tomcat.
 */
public final class RlsContext {

  private static final ThreadLocal<Value> CURRENT = new ThreadLocal<>();

  private RlsContext() {
    // utility class
  }

  public static void set(Long userId, String rolName) {
    CURRENT.set(new Value(userId, rolName));
  }

  public static Value get() {
    return CURRENT.get();
  }

  public static void clear() {
    CURRENT.remove();
  }

  /**
   * Snapshot inmutable del usuario autenticado, suficiente para inyectar en la conexión Postgres
   * vía SET LOCAL.
   */
  public record Value(Long userId, String rolName) {}
}
