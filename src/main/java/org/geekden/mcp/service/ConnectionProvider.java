package org.geekden.mcp.service;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Optional;

/**
 * CDI producer for database connections using HikariCP connection pool.
 * <p>
 * Provides connections via HikariCP to support dynamic JDBC URLs
 * with efficient connection pooling and lifecycle management.
 * <p>
 * Uses {@link DatabaseUrlProvider} to allow URL transformation for
 * test isolation without duplicating connection pooling logic.
 */
@ApplicationScoped
public class ConnectionProvider {

  private static final Logger LOG = Logger.getLogger(ConnectionProvider.class);

  static {
    // Eagerly initialize DriverManager to trigger JDBC 4.0 ServiceLoader
    // This ensures drivers are loaded before first connection attempt
    try {
      DriverManager.getDrivers();
    } catch (Exception e) {
      LOG.warn("Failed to initialize JDBC drivers", e);
    }
  }

  @Inject
  DatabaseUrlProvider urlProvider;

  @ConfigProperty(name = "quarkus.datasource.jdbc.url")
  Optional<String> configuredUrl;

  @ConfigProperty(name = "quarkus.datasource.username")
  Optional<String> username;

  @ConfigProperty(name = "quarkus.datasource.password")
  Optional<String> password;

  @ConfigProperty(name = "hikari.maximum-pool-size", defaultValue = "2")
  int maximumPoolSize;

  @ConfigProperty(name = "hikari.pool-name", defaultValue = "DatabaseConnectionPool")
  String poolName;

  private HikariDataSource dataSource;

  /**
   * Initialize HikariCP DataSource with configuration.
   * Internal method - not exposed as CDI bean to avoid conflicts.
   */
  private synchronized void initializeDataSource() throws SQLException {
    if (dataSource == null) {
      String originalUrl = configuredUrl.orElseThrow(() ->
          new SQLException("No database URL configured (set DB_URL environment variable)")
      );

      // Transform URL using provider (no-op in production, test-specific in tests)
      String url = urlProvider.transformUrl(originalUrl);

      LOG.info("=== Initializing HikariCP connection pool ===");
      LOG.info("Original URL: " + originalUrl);
      LOG.info("Transformed URL: " + url);
      LOG.info("Pool name: " + poolName);
      LOG.info("Max pool size: " + maximumPoolSize);

      HikariConfig config = new HikariConfig();
      config.setJdbcUrl(url);

      // Set credentials if provided
      if (username.isPresent()) {
        config.setUsername(username.get());
        config.setPassword(password.orElse(""));
      }

      // Configure pool settings
      config.setMaximumPoolSize(maximumPoolSize);
      config.setPoolName(poolName);

      // Performance and reliability settings
      config.setAutoCommit(true);
      config.setConnectionTestQuery(null); // Use driver-specific test query

      dataSource = new HikariDataSource(config);
      LOG.info("HikariCP connection pool initialized successfully");
      LOG.info("==========================================");
    }
  }

  /**
   * CDI producer method for database connections from HikariCP pool.
   * <p>
   * Returns a connection from the connection pool. Connections should be
   * properly closed by the caller to return them to the pool.
   *
   * @return A database connection from the pool
   * @throws SQLException if connection cannot be obtained
   */
  @Produces
  @Dependent
  public Connection produceConnection() throws SQLException {
    initializeDataSource();
    LOG.info("Requesting connection from HikariCP pool '" + poolName + "'");
    Connection conn = dataSource.getConnection();
    LOG.info("Connection obtained successfully (hashCode=" + System.identityHashCode(conn) + ")");
    return conn;
  }

  /**
   * Cleanup method to properly close the HikariCP DataSource on shutdown.
   */
  @PreDestroy
  public void cleanup() {
    if (dataSource != null && !dataSource.isClosed()) {
      LOG.info("Closing HikariCP connection pool");
      dataSource.close();
    }
  }
}
