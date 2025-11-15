package org.geekden.mcp.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Optional;

/**
 * CDI producer for database connections using JDBC DriverManager.
 * <p>
 * Provides connections directly via DriverManager to support dynamic JDBC URLs
 * without requiring build-time configuration. This approach trades connection
 * pooling for simplicity and flexibility.
 */
@ApplicationScoped
public class DataSourceProvider {

  private static final Logger LOG = Logger.getLogger(DataSourceProvider.class);

  static {
    // Eagerly initialize DriverManager to trigger JDBC 4.0 ServiceLoader
    // This ensures drivers are loaded before first connection attempt
    try {
      DriverManager.getDrivers();
    } catch (Exception e) {
      LOG.warn("Failed to initialize JDBC drivers", e);
    }
  }

  @ConfigProperty(name = "quarkus.datasource.jdbc.url")
  Optional<String> configuredUrl;

  @ConfigProperty(name = "quarkus.datasource.username")
  Optional<String> username;

  @ConfigProperty(name = "quarkus.datasource.password")
  Optional<String> password;

  /**
   * CDI producer method for database connections via JDBC DriverManager.
   * <p>
   * Relies on JDBC 4.0 automatic driver loading via ServiceLoader.
   * Quarkus uber-JAR properly merges META-INF/services/java.sql.Driver files
   * from all JDBC driver JARs, enabling automatic driver registration.
   *
   * @return A database connection
   * @throws SQLException if connection cannot be established
   */
  @Produces
  @Dependent
  public Connection produceConnection() throws SQLException {
    String url = configuredUrl.orElseThrow(() ->
        new SQLException("No database URL configured (set DB_URL environment variable)")
    );

    LOG.debug("Connecting to database: " + url);

    // Connect with or without credentials
    if (username.isPresent()) {
      return DriverManager.getConnection(url, username.get(), password.orElse(""));
    } else {
      return DriverManager.getConnection(url);
    }
  }
}
