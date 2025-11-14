package org.geekden.mcp.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.spi.InjectionPoint;
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
 * For tests, automatically creates isolated databases per test class based on
 * the injection point's declaring class name.
 * <p>
 * All consumers just @Inject Connection and don't worry about the source.
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
   * <p>
   * For test classes, automatically generates isolated database files based on
   * the test class name, ensuring proper test isolation.
   *
   * @param injectionPoint The CDI injection point (used to derive test database names)
   * @return A database connection
   * @throws SQLException if connection cannot be established
   */
  @Produces
  @Dependent
  public Connection produceConnection(InjectionPoint injectionPoint) throws SQLException {
    String url = configuredUrl.orElseThrow(() ->
        new SQLException("No database URL configured (set DB_URL environment variable)")
    );

    // For test databases, create isolated database files per test class
    if (url.contains("target/test-database.db") && injectionPoint != null) {
      String testClassName = getTestClassName(injectionPoint);
      if (testClassName != null) {
        // Replace generic test-database.db with test-class-specific filename
        url = url.replace("test-database.db", testClassName + ".db");
        LOG.debug("Using isolated test database for " + testClassName + ": " + url);
      }
    }

    LOG.debug("Connecting to database: " + url);

    // Connect with or without credentials
    if (username.isPresent() && !username.get().isEmpty()) {
      return DriverManager.getConnection(url, username.get(), password.orElse(""));
    } else {
      return DriverManager.getConnection(url);
    }
  }

  /**
   * Extract the test class name from the injection point.
   * <p>
   * Returns the simple name of the class where the connection is being injected,
   * or null if not injected into a test class.
   *
   * @param injectionPoint The CDI injection point
   * @return Simple class name, or null
   */
  private String getTestClassName(InjectionPoint injectionPoint) {
    try {
      Class<?> declaringClass = injectionPoint.getMember().getDeclaringClass();
      String className = declaringClass.getSimpleName();

      // Only use class-specific databases for test classes
      if (className.endsWith("Test")) {
        return className;
      }
    } catch (Exception e) {
      LOG.debug("Could not determine test class name from injection point", e);
    }

    return null;
  }
}
