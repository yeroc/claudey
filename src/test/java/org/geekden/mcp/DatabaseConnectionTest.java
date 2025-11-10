package org.geekden.mcp;

import io.agroal.api.AgroalDataSource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.geekden.mcp.config.DatabaseConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.sql.Connection;
import java.sql.DatabaseMetaData;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test database connectivity.
 * Only runs if database is configured via environment variables.
 */
@QuarkusTest
class DatabaseConnectionTest {

  @Inject
  Instance<AgroalDataSource> dataSource;

  @Inject
  DatabaseConfig config;

  @Test
  void testDataSourceInjection() {
    assertNotNull(dataSource, "DataSource instance should be injected");
  }

  @Test
  @EnabledIf("isDatabaseConfigured")
  void testDatabaseConnection() throws Exception {
    // Print configuration for visibility
    System.out.println("=== Database Configuration ===");
    System.out.println("DB_URL: " + config.getJdbcUrl().orElse("not set"));
    System.out.println("DB_USERNAME: " + config.getUsername().map(u -> u.substring(0, Math.min(u.length(), 3)) + "***").orElse("not set"));

    try (Connection conn = dataSource.get().getConnection()) {
      assertNotNull(conn, "Connection should not be null");
      assertFalse(conn.isClosed(), "Connection should be open");

      // Get database metadata
      DatabaseMetaData metaData = conn.getMetaData();
      assertNotNull(metaData, "DatabaseMetaData should not be null");

      String productName = metaData.getDatabaseProductName();
      String productVersion = metaData.getDatabaseProductVersion();
      String driverName = metaData.getDriverName();
      String driverVersion = metaData.getDriverVersion();
      String url = metaData.getURL();

      System.out.println("=== Active Database Connection ===");
      System.out.println("Product: " + productName + " " + productVersion);
      System.out.println("Driver: " + driverName + " " + driverVersion);
      System.out.println("URL: " + url);
      System.out.println("================================");

      assertNotNull(productName, "Database product name should not be null");
    }
  }

  @Test
  @EnabledIf("isDatabaseConfigured")
  void testConnectionPooling() throws Exception {
    // Test that we can get multiple connections from the pool
    try (Connection conn1 = dataSource.get().getConnection();
         Connection conn2 = dataSource.get().getConnection()) {

      assertNotNull(conn1, "First connection should not be null");
      assertNotNull(conn2, "Second connection should not be null");

      // Both connections should be valid
      assertTrue(conn1.isValid(5), "First connection should be valid");
      assertTrue(conn2.isValid(5), "Second connection should be valid");
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
