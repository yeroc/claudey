package org.geekden.mcp;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Alternative;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
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
 * Uses TestDatabaseContext to determine the current test class name,
 * which is set by AbstractDatabaseIntegrationTest. This enables parallel
 * test execution with isolated databases (e.g., "CliQueryTest.db").
 */
@Alternative
@Priority(Interceptor.Priority.APPLICATION + 100)
@ApplicationScoped
public class TestDataSourceProvider {

  private static final Logger LOG = Logger.getLogger(TestDataSourceProvider.class);

  @Inject
  TestDatabaseContext testDatabaseContext;

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
   * from the test class name registered in TestDatabaseContext. This enables
   * parallel test execution with proper database isolation.
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

    // For SQLite file-based databases, isolate per test class
    if (url.matches("jdbc:sqlite:.*\\.db")) {
      String testClassName = testDatabaseContext.getTestClass();
      if (testClassName == null) {
        throw new SQLException(
            "Test class name not set in TestDatabaseContext. " +
            "Database tests must extend AbstractDatabaseIntegrationTest."
        );
      }
      // Replace filename with test-class-specific name (e.g., CliQueryTest.db)
      url = url.replaceFirst("[^/]+\\.db$", testClassName + ".db");
      LOG.debug("Using isolated test database for " + testClassName + ": " + url);
    }

    LOG.debug("Connecting to test database: " + url);

    // Connect with or without credentials
    if (username.isPresent()) {
      return DriverManager.getConnection(url, username.get(), password.orElse(""));
    } else {
      return DriverManager.getConnection(url);
    }
  }
}
