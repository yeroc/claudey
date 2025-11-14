package org.geekden.mcp.cli;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.geekden.mcp.config.DatabaseConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * Test CLI command handler with database configured via environment variables.
 * <p>
 * Note: Output verification is not possible with MCP stdio extension active.
 * These tests verify exit codes with environment-based configuration.
 */
@QuarkusTest
class CliCommandHandlerWithEnvironmentVariablesTest {

  @Inject
  CliCommandHandler cliHandler;

  @Inject
  DatabaseConfig config;

  @Test
  @EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
  void testIntrospectWithEnvironmentVariables() {
    assertThat("Database should be configured via environment variables",
        config.isConfigured(), is(true));

    int exitCode = cliHandler.execute(new String[]{"introspect"});
    assertThat("CLI introspect should succeed with configured database", exitCode, is(0));
  }

  @Test
  @EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
  void testQueryWithEnvironmentVariables() {
    assertThat("Database should be configured via environment variables",
        config.isConfigured(), is(true));

    int exitCode = cliHandler.execute(new String[]{"query", "SELECT 1"});
    assertThat("CLI query should succeed with configured database", exitCode, is(0));
  }

  @Test
  @EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".*postgres.*", disabledReason = "PostgreSQL-specific test")
  void testPostgreSQLConnection() {
    assertThat("Should be using PostgreSQL database",
        config.getJdbcUrl().orElse(""), containsString("postgres"));

    int exitCode = cliHandler.execute(new String[]{"introspect"});
    assertThat("PostgreSQL introspect should succeed", exitCode, is(0));
  }

  @Test
  @EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".*sqlite.*", disabledReason = "SQLite-specific test")
  void testSQLiteConnection() {
    assertThat("Should be using SQLite database",
        config.getJdbcUrl().orElse(""), containsString("sqlite"));

    int exitCode = cliHandler.execute(new String[]{"introspect"});
    assertThat("SQLite introspect should succeed", exitCode, is(0));
  }
}
