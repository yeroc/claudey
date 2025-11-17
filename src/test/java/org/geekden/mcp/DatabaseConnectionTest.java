package org.geekden.mcp;


import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.geekden.mcp.config.DatabaseConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.sql.Connection;
import java.sql.DatabaseMetaData;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * Test database connectivity with HikariCP connection pool.
 * Only runs if database is configured via environment variables.
 */
@QuarkusTest
@TestProfile(DatabaseConnectionTest.Profile.class)
class DatabaseConnectionTest {

  public static class Profile extends IsolatedDatabaseProfile {
  }

  @Inject
  Instance<Connection> connectionInstance;

  @Inject
  DatabaseConfig config;

  @Test
  void testConnectionInjection() {
    assertThat("Connection instance should be injected",
        connectionInstance, is(notNullValue()));
  }

  @Test
  @EnabledIf("isDatabaseConfigured")
  void testDatabaseConnection() throws Exception {
    // Print configuration for visibility
    System.out.println("=== Database Configuration ===");
    System.out.println("DB_URL: " + config.getJdbcUrl().orElse("not set"));
    System.out.println("DB_USERNAME: " + config.getUsername().map(u -> u.substring(0, Math.min(u.length(), 3)) + "***").orElse("not set"));

    try (Connection conn = connectionInstance.get()) {
      assertThat("Connection should not be null",
          conn, is(notNullValue()));
      assertThat("Connection should be open",
          conn.isClosed(), is(false));

      // Get database metadata
      DatabaseMetaData metaData = conn.getMetaData();
      assertThat("DatabaseMetaData should not be null",
          metaData, is(notNullValue()));

      String productName = metaData.getDatabaseProductName();
      String productVersion = metaData.getDatabaseProductVersion();
      String driverName = metaData.getDriverName();
      String driverVersion = metaData.getDriverVersion();
      String url = metaData.getURL();

      System.out.println("=== Active Database Connection (HikariCP) ===");
      System.out.println("Product: " + productName + " " + productVersion);
      System.out.println("Driver: " + driverName + " " + driverVersion);
      System.out.println("URL: " + url);
      System.out.println("================================");

      assertThat("Database product name should not be null",
          productName, is(notNullValue()));
    }
  }

  @Test
  @EnabledIf("isDatabaseConfigured")
  void testConnectionPooling() throws Exception {
    // Test that we can get multiple connections from HikariCP pool
    try (Connection conn1 = connectionInstance.get();
         Connection conn2 = connectionInstance.get()) {

      assertThat("First connection should not be null",
          conn1, is(notNullValue()));
      assertThat("Second connection should not be null",
          conn2, is(notNullValue()));

      // Both connections should be valid
      assertThat("First connection should be valid",
          conn1.isValid(5), is(true));
      assertThat("Second connection should be valid",
          conn2.isValid(5), is(true));
    }
  }

  /**
   * Condition for tests that require a configured database.
   */
  static boolean isDatabaseConfigured() {
    String dbUrl = System.getenv("DB_URL");
    return dbUrl != null && !dbUrl.isEmpty();
  }
}
