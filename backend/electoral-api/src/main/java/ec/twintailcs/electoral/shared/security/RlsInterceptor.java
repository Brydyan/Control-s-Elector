package ec.twintailcs.electoral.shared.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;

/**
 * HandlerInterceptor que enlaza el contexto RLS al ciclo de vida de la
 * transacción JPA/Spring del request actual.
 *
 * <p>Flujo correcto (antes era incorrecto):
 * <ol>
 *   <li>El {@link RlsAuthenticationBridge} corre justo después del JWT y
 *       guarda (userId, rolName) en {@link RlsContext} (ThreadLocal).</li>
 *   <li>Cuando arranca el handler, este interceptor mira si ya hay una
 *       transacción activa; si la hay, registra un
 *       {@link RlsTransactionSynchronization} que Spring invocará en
 *       {@code afterBegin()} sobre la MISMA conexión transaccional.</li>
 *   <li>Si no hay transacción activa (rara vez), aplica SET LOCAL directo
 *       sobre la conexión ligada al request vía
 *       {@link DataSourceUtils#getConnection(DataSource)}.</li>
 * </ol>
 *
 * <p>Notas:
 * <ul>
 *   <li>NO llamamos {@code sync.afterBegin()} manualmente: Spring ya lo hace
 *       cuando registra el synchronization dentro de una transacción activa.
 *       Llamarlo dos veces duplica el SET LOCAL y deja la conexión en estado
 *       ambiguo.</li>
 *   <li>Si el SET LOCAL falla, lanzamos — el request no debe continuar con
 *       RLS desincronizado. Antes el interceptor original logueaba y seguía.</li>
 * </ul>
 */
@Component
public class RlsInterceptor implements HandlerInterceptor {

    private final DataSource dataSource;

    public RlsInterceptor(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws IOException {
        RlsContext.Value value = RlsContext.get();
        if (value == null) {
            // No hay usuario autenticado en este request. La cadena de filtros
            // de Spring Security rechazará el endpoint si requiere auth, así
            // que acá solo dejamos pasar.
            return true;
        }

        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            // Registramos el sync; Spring invocará afterBegin() cuando la
            // transacción actual termine de arrancar, sobre la MISMA conexión.
            TransactionSynchronizationManager.registerSynchronization(
                    new RlsTransactionSynchronization(dataSource, value));
        } else {
            // Fallback: no hay transacción (raro). Aplicamos SET LOCAL sobre
            // la conexión ligada al request y la devolvemos al pool al final.
            applyLocalDirectly(value);
        }

        return true;
    }

    private void applyLocalDirectly(RlsContext.Value value) {
        Connection connection = DataSourceUtils.getConnection(dataSource);
        try {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SET LOCAL app.current_user_id = ?")) {
                ps.setLong(1, value.userId());
                ps.execute();
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "SET LOCAL app.current_user_rol = ?")) {
                ps.setString(1, value.rolName());
                ps.execute();
            }
        } catch (Exception e) {
            throw new IllegalStateException(
                    "No se pudo aplicar contexto RLS al request", e);
        } finally {
            DataSourceUtils.releaseConnection(connection, dataSource);
        }
    }
}