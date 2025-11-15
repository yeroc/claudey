package org.geekden.mcp.cli;

import org.geekden.mcp.AbstractDatabaseIntegrationTest;
import org.geekden.MainApplication;
import picocli.CommandLine;

import io.quarkus.test.junit.QuarkusTest;
import org.geekden.MainApplication;
import picocli.CommandLine;
import jakarta.inject.Inject;
import org.geekden.MainApplication;
import picocli.CommandLine;
import org.geekden.mcp.config.DatabaseConfig;
import org.geekden.MainApplication;
import picocli.CommandLine;
import org.junit.jupiter.api.BeforeEach;
import org.geekden.MainApplication;
import picocli.CommandLine;
import org.junit.jupiter.api.Test;
import org.geekden.MainApplication;
import picocli.CommandLine;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.geekden.MainApplication;
import picocli.CommandLine;

import static org.hamcrest.MatcherAssert.assertThat;
import org.geekden.MainApplication;
import picocli.CommandLine;
import static org.hamcrest.Matchers.*;
import org.geekden.MainApplication;
import picocli.CommandLine;

/**
 * Test CLI command handler with database configured via environment variables.
 * <p>
 * These tests only run when DB_URL environment variable is set.
 */
@QuarkusTest
class CliCommandHandlerWithEnvironmentVariablesTest extends AbstractDatabaseIntegrationTest {

  @Inject
  MainApplication app;

  @Inject
  CommandLine.IFactory factory;

  @Inject
  DatabaseConfig config;

  @Inject
  CapturingOutput output;

  @BeforeEach
  void setUp() {

  private int execute(String... args) {
    CommandLine cmd = new CommandLine(app, factory);
    return cmd.execute(args);
  }
    output.reset();

  private int execute(String... args) {
    CommandLine cmd = new CommandLine(app, factory);
    return cmd.execute(args);
  }
  }

  private int execute(String... args) {
    CommandLine cmd = new CommandLine(app, factory);
    return cmd.execute(args);
  }

  @Test
  @EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
  void testIntrospectWithEnvironmentVariables() {
    assertThat("Database should be configured via environment variables",
        config.isConfigured(), is(true));

    int exitCode = execute(new String[]{"introspect"});
    assertThat("CLI introspect should succeed with configured database", exitCode, is(0));
  }

  @Test
  @EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
  void testQueryWithEnvironmentVariables() {
    assertThat("Database should be configured via environment variables",
        config.isConfigured(), is(true));

    int exitCode = execute(new String[]{"query", "SELECT 1"});
    assertThat("CLI query should succeed with configured database", exitCode, is(0));
  }

  @Test
  @EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".*postgres.*", disabledReason = "PostgreSQL-specific test")
  void testPostgreSQLConnection() {
    assertThat("Should be using PostgreSQL database",
        config.getJdbcUrl().orElse(""), containsString("postgres"));

    int exitCode = execute(new String[]{"introspect"});
    assertThat("PostgreSQL introspect should succeed", exitCode, is(0));
  }

  @Test
  @EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".*sqlite.*", disabledReason = "SQLite-specific test")
  void testSQLiteConnection() {
    assertThat("Should be using SQLite database",
        config.getJdbcUrl().orElse(""), containsString("sqlite"));

    int exitCode = execute(new String[]{"introspect"});
    assertThat("SQLite introspect should succeed", exitCode, is(0));
  }
}
