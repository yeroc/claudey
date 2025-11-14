package org.geekden.mcp.service;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Alternative;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.spi.InjectionPoint;
import jakarta.interceptor.Interceptor;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Optional;

/**
 * Test-specific CDI producer for database connections.
 * <p>
 * This alternative producer is active only during tests and provides
 * isolated database files per test class for proper test isolation.
 * <p>
 * Uses InjectionPoint introspection to derive test class names and
 * generate unique database filenames (e.g., "SqlExecutionServiceTest.db").
 */
@Alternative
@Priority(Interceptor.Priority.APPLICATION + 100)
@ApplicationScoped
public class TestDataSourceProvider {

  private static final Logger LOG = Logger.getLogger(TestDataSourceProvider.class);

  @ConfigProperty(name = "quarkus.datasource.jdbc.url")
  Optional<String> configuredUrl;

  @ConfigProperty(name = "quarkus.datasource.username")
  Optional<String> username;

  @ConfigProperty(name = "quarkus.datasource.password")
  Optional<String> password;

  /**
   * Produces database connections with test-class-specific isolation.
   * <p>
   * For SQLite file-based databases, automatically derives unique filenames
   * from the injection point's declaring class to ensure test isolation.
   *
   * @param injectionPoint The CDI injection point
   * @return A database connection
   * @throws SQLException if connection cannot be established
   */
  @Produces
  @Dependent
  public Connection produceConnection(InjectionPoint injectionPoint) throws SQLException {
    String url = configuredUrl.orElseThrow(() ->
        new SQLException("No database URL configured (set DB_URL environment variable)")
    );

    // For SQLite file-based databases, isolate per declaring class
    if (url.matches("jdbc:sqlite:.*\\.db")) {
      String className = injectionPoint.getMember().getDeclaringClass().getSimpleName();
      // Replace filename with class-specific name (e.g., SqlExecutionServiceTest.db)
      url = url.replaceFirst("[^/]+\\.db$", className + ".db");
      LOG.debug("Using isolated test database for " + className + ": " + url);
    }

    LOG.debug("Connecting to test database: " + url);

    // Connect with or without credentials
    if (username.isPresent() && !username.get().isEmpty()) {
      return DriverManager.getConnection(url, username.get(), password.orElse(""));
    } else {
      return DriverManager.getConnection(url);
    }
  }
}
