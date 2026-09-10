package ec.twintailcs.electoral.shared.security;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.transaction.TransactionExecution;
import org.springframework.transaction.TransactionExecutionListener;

/**
 * Applies the authenticated RLS context immediately after Spring opens a
 * database transaction and before service code can execute a query.
 *
 * <p>{@code set_config(..., true)} is PostgreSQL's parameter-safe equivalent
 * of {@code SET LOCAL}: it scopes both settings to the current transaction,
 * so a pooled connection cannot leak one request's identity into another.
 */
public final class RlsTransactionExecutionListener implements TransactionExecutionListener {

    private final DataSource dataSource;

    public RlsTransactionExecutionListener(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void afterBegin(TransactionExecution transaction, Throwable beginFailure) {
        if (beginFailure != null) {
            return;
        }

        RlsContext.Value value = RlsContext.get();
        if (value == null) {
            return;
        }

        Connection connection = DataSourceUtils.getConnection(dataSource);
        try {
            setLocal(connection, "app.current_user_id", Long.toString(value.userId()));
            setLocal(connection, "app.current_user_rol", value.rolName());
        } catch (SQLException ex) {
            throw new IllegalStateException("Could not establish PostgreSQL RLS context", ex);
        } finally {
            DataSourceUtils.releaseConnection(connection, dataSource);
        }
    }

    private static void setLocal(Connection connection, String setting, String value)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT set_config(?, ?, true)")) {
            statement.setString(1, setting);
            statement.setString(2, value);
            statement.execute();
        }
    }
}