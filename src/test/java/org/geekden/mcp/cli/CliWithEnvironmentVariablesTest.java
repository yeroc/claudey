package org.geekden.mcp.cli;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.geekden.mcp.config.DatabaseConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static uk.org.webcompere.systemstubs.SystemStubs.tapSystemErr;
import static uk.org.webcompere.systemstubs.SystemStubs.tapSystemOut;

/**
 * Test CLI with database configured via environment variables.
 * This matches how the application is used in CI workflows.
 */
@QuarkusTest
class CliWithEnvironmentVariablesTest {

  @Inject
  CliCommandHandler cliHandler;

  @Inject
  DatabaseConfig config;

  @Test
  @EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
  void testIntrospectWithEnvironmentVariables() throws Exception {
    // Print configuration for visibility in test output
    System.out.println("=== CLI Test with Environment Variables ===");
    System.out.println("DB_URL: " + System.getenv("DB_URL"));
    System.out.println("DB_USERNAME: " + (System.getenv("DB_USERNAME") != null ? "***" : "not set"));
    System.out.println("DB_PASSWORD: " + (System.getenv("DB_PASSWORD") != null ? "***" : "not set"));
    System.out.println("==========================================");

    assertThat("Database should be configured via environment variables",
        config.isConfigured(), is(true));

    String stdout = tapSystemOut(() -> {
      String stderr = tapSystemErr(() -> {
        int exitCode = cliHandler.execute(new String[]{"introspect"});

        assertThat("CLI introspect should succeed with configured database",
            exitCode, is(0));

        return stderr;
      });

      assertThat("Should not show database configuration error",
          stderr, not(containsString("Database not configured")));
    });

    System.out.println("=== CLI Output ===");
    System.out.println(stdout);
    System.out.println("==================");
  }

  @Test
  @EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
  void testQueryWithEnvironmentVariables() throws Exception {
    assertThat("Database should be configured via environment variables",
        config.isConfigured(), is(true));

    String stdout = tapSystemOut(() -> {
      String stderr = tapSystemErr(() -> {
        int exitCode = cliHandler.execute(new String[]{"query", "SELECT 1"});

        assertThat("CLI query should succeed with configured database",
            exitCode, is(0));

        return stderr;
      });

      assertThat("Should not show database configuration error",
          stderr, not(containsString("Database not configured")));
    });

    System.out.println("=== CLI Query Output ===");
    System.out.println(stdout);
    System.out.println("========================");
  }

  @Test
  @EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".*postgres.*", disabledReason = "PostgreSQL-specific test")
  void testPostgreSQLConnection() throws Exception {
    System.out.println("=== PostgreSQL-Specific Test ===");
    System.out.println("Verifying PostgreSQL connection via CLI");

    assertThat("Should be using PostgreSQL database",
        config.getJdbcUrl().orElse(""), containsString("postgres"));

    String stdout = tapSystemOut(() -> {
      int exitCode = cliHandler.execute(new String[]{"introspect"});
      assertThat("PostgreSQL introspect should succeed",
          exitCode, is(0));
    });

    System.out.println("PostgreSQL CLI introspect succeeded");
  }

  @Test
  @EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".*sqlite.*", disabledReason = "SQLite-specific test")
  void testSQLiteConnection() throws Exception {
    System.out.println("=== SQLite-Specific Test ===");
    System.out.println("Verifying SQLite connection via CLI");

    assertThat("Should be using SQLite database",
        config.getJdbcUrl().orElse(""), containsString("sqlite"));

    String stdout = tapSystemOut(() -> {
      int exitCode = cliHandler.execute(new String[]{"introspect"});
      assertThat("SQLite introspect should succeed",
          exitCode, is(0));
    });

    System.out.println("SQLite CLI introspect succeeded");
  }
}
