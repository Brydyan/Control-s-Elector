package ec.twintailcs.electoral.shared.security;

import java.util.ArrayList;
import java.util.Collection;
import javax.sql.DataSource;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionExecutionListener;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;

/** Registers the RLS listener on every Spring-managed database transaction. */
@Configuration
public class RlsTransactionConfig {

  @Bean
  RlsTransactionExecutionListener rlsTransactionExecutionListener(DataSource dataSource) {
    return new RlsTransactionExecutionListener(dataSource);
  }

  @Bean
  SmartInitializingSingleton registerRlsTransactionListener(
      Collection<PlatformTransactionManager> transactionManagers,
      RlsTransactionExecutionListener listener) {
    return () ->
        transactionManagers.forEach(
            manager -> {
              if (!(manager instanceof AbstractPlatformTransactionManager transactionManager)) {
                throw new IllegalStateException(
                    "RLS requires an AbstractPlatformTransactionManager: "
                        + manager.getClass().getName());
              }

              Collection<TransactionExecutionListener> listeners =
                  new ArrayList<>(transactionManager.getTransactionExecutionListeners());
              if (!listeners.contains(listener)) {
                listeners.add(listener);
                transactionManager.setTransactionExecutionListeners(listeners);
              }
            });
  }
}
