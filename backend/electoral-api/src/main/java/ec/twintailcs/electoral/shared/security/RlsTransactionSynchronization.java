package ec.twintailcs.electoral.shared.security;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.ConnectionHolder;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Synchronization que aplica el contexto RLS al inicio de la transacción
 * activa. Se registra desde {@link RlsInterceptor} y Spring lo invoca
 * dentro del ciclo de vida transaccional, sobre la MISMA conexión
 * usada por Hibernate durante la transacción.
 *
 * <p>Si no hay un {@link ConnectionHolder} bindeado a la transacción
 * (caso raro, fuera de un datasource transaccional), se loguea como
 * warning y se continúa — la query subsecuente abrirá su propia conexión
 * sin RLS, y las policies de Postgres la van a rechazar.
 */
public final class RlsTransactionSynchronization implements TransactionSynchronization {

    private final DataSource dataSource;
    private final RlsContext.Value value;

    public RlsTransactionSynchronization(DataSource dataSource, RlsContext.Value value) {
        this.dataSource = dataSource;
        this.value = value;
    }

    @Override
    public void afterBegin() {
        Object resource = TransactionSynchronizationManager.getResource(dataSource);
        if (!(resource instanceof ConnectionHolder holder)) {
            // No estamos dentro de un datasource transaccional; no podemos
            // aplicar SET LOCAL. Logueamos y dejamos que las policies
            // de Postgres rechacen las queries si corresponde.
            return;
        }

        try {
            apply(holder, "SET LOCAL app.current_user_id = ?", ps -> ps.setLong(1, value.userId()));
            apply(holder, "SET LOCAL app.current_user_rol = ?", ps -> ps.setString(1, value.rolName()));
        } catch (SQLException e) {
            throw new IllegalStateException(
                    "No se pudo establecer contexto RLS en la transacción", e);
        }
    }

    private void apply(ConnectionHolder holder,
                       String sql,
                       SqlBinder binder) throws SQLException {
        try (PreparedStatement ps = holder.getConnection().prepareStatement(sql)) {
            binder.bind(ps);
            ps.execute();
        }
    }

    @FunctionalInterface
    private interface SqlBinder {
        void bind(PreparedStatement ps) throws SQLException;
    }
}