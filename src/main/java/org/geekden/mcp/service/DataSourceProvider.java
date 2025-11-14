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
 * <p>
 * All consumers just @Inject Connection and don't worry about the source.
 */
@ApplicationScoped
public class DataSourceProvider {

  private static final Logger LOG = Logger.getLogger(DataSourceProvider.class);

  @ConfigProperty(name = "quarkus.datasource.jdbc.url")
  Optional<String> configuredUrl;

  @ConfigProperty(name = "quarkus.datasource.username")
  Optional<String> username;

  @ConfigProperty(name = "quarkus.datasource.password")
  Optional<String> password;

  /**
   * CDI producer method for database connections via JDBC DriverManager.
   * <p>
   * Explicitly loads the appropriate JDBC driver based on URL prefix.
   * Uber-JARs break JDBC ServiceLoader, so we must load drivers explicitly.
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

    // TODO: Design and implement database dialect abstraction
    // Current approach uses conditional checks (code smell) which doesn't scale.
    // Need a proper pattern like Strategy or DatabaseDialect interface:
    //   - DatabaseDialect interface with methods: getDriverClassName(), etc.
    //   - SQLiteDialect, PostgreSQLDialect implementations
    //   - Factory to detect and instantiate correct dialect
    //   - Inject dialect into services that need database-specific behavior
    // This will make adding new databases cleaner and more maintainable.

    // Explicitly load the appropriate JDBC driver based on URL
    // Uber-JARs break JDBC ServiceLoader, so we must load explicitly
    try {
      if (url.startsWith("jdbc:sqlite:")) {
        Class.forName("org.sqlite.JDBC");
      } else if (url.startsWith("jdbc:postgresql:")) {
        Class.forName("org.postgresql.Driver");
      }
    } catch (ClassNotFoundException e) {
      throw new SQLException("JDBC driver not found for URL: " + url, e);
    }

    LOG.debug("Connecting to database: " + url);

    // Connect with or without credentials
    if (username.isPresent() && !username.get().isEmpty()) {
      return DriverManager.getConnection(url, username.get(), password.orElse(""));
    } else {
      return DriverManager.getConnection(url);
    }
  }
}
