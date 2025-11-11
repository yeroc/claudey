package org.geekden.mcp.config;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * Test database configuration loading via MicroProfile Config.
 */
@QuarkusTest
class DatabaseConfigTest {

  @Inject
  DatabaseConfig config;

  @Test
  void testConfigInjection() {
    assertThat("DatabaseConfig should be injected",
        config, is(notNullValue()));
  }

  @Test
  void testPageSizeDefaultValue() {
    // Default value should be 100 if DB_PAGE_SIZE not set
    assertThat("Page size should be positive",
        config.getPageSize(), is(greaterThan(0)));
  }

  @Test
  void testIsConfigured() {
    // This test will fail if DB_URL is not set, which is expected
    // In CI, we'll set test environment variables
    assertThat("JDBC URL should be configured via application.properties",
        config.getJdbcUrl(), is(notNullValue()));
  }
}
