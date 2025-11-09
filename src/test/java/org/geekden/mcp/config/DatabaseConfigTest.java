package org.geekden.mcp.config;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test database configuration loading via MicroProfile Config.
 */
@QuarkusTest
class DatabaseConfigTest {

  @Inject
  DatabaseConfig config;

  @Test
  void testConfigInjection() {
    assertNotNull(config, "DatabaseConfig should be injected");
  }

  @Test
  void testPageSizeDefaultValue() {
    // Default value should be 100 if DB_PAGE_SIZE not set
    assertTrue(config.getPageSize() > 0, "Page size should be positive");
  }

  @Test
  void testIsConfigured() {
    // This test will fail if DB_URL is not set, which is expected
    // In CI, we'll set test environment variables
    assertNotNull(config.getJdbcUrl(), "JDBC URL should be configured via application.properties");
  }
}
