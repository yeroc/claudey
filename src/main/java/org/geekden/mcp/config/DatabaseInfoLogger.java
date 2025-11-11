package org.geekden.mcp.config;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.sql.Connection;
import java.sql.DatabaseMetaData;

/**
 * Logs database connection information at startup.
 * Provides visibility into which database is being used.
 */
@ApplicationScoped
public class DatabaseInfoLogger {

  private static final Logger LOG = Logger.getLogger(DatabaseInfoLogger.class);

  @Inject
  Instance<Connection> connection;

  @Inject
  DatabaseConfig config;

  void onStart(@Observes StartupEvent ev) {
    if (!config.isConfigured()) {
      LOG.info("Database not configured - running without database");
      return;
    }

    try {
      LOG.info("Database URL: " + maskPassword(config.getJdbcUrl().orElse("not set")));

      try (Connection conn = connection.get()) {
        DatabaseMetaData metaData = conn.getMetaData();
        String productName = metaData.getDatabaseProductName();
        String productVersion = metaData.getDatabaseProductVersion();
        String driverName = metaData.getDriverName();
        String driverVersion = metaData.getDriverVersion();

        LOG.info(String.format("Connected to %s %s (Driver: %s %s)",
            productName, productVersion, driverName, driverVersion));
      }
    } catch (Exception e) {
      LOG.error("Failed to get database info: " + e.getMessage(), e);
    }
  }

  /**
   * Mask password in JDBC URL for logging.
   */
  private String maskPassword(String url) {
    if (url == null) {
      return "null";
    }
    // Simple masking - replace password parameter values
    return url.replaceAll("([?&]password=)[^&]*", "$1***");
  }
}
