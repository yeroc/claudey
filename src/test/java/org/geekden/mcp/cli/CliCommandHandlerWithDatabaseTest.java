package org.geekden.mcp.cli;

import org.geekden.mcp.AbstractDatabaseIntegrationTest;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * Test CLI command handler with SQLite database configured.
 * <p>
 * Note: Output verification is not possible with MCP stdio extension active.
 * These tests verify exit codes with database configured.
 */
@QuarkusTest
@TestProfile(CliCommandHandlerWithDatabaseTest.SqliteTestProfile.class)
class CliCommandHandlerWithDatabaseTest extends AbstractDatabaseIntegrationTest {

  @Inject
  CliCommandHandler cliHandler;

  /**
   * Test profile that configures SQLite in-memory database.
   */
  public static class SqliteTestProfile implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
      return Map.of(
          "quarkus.datasource.db-kind", "sqlite",
          "quarkus.datasource.jdbc.url", "jdbc:sqlite::memory:"
      );
    }
  }

  @Test
  void testIntrospectWithDatabaseSucceeds() {
    int exitCode = cliHandler.execute(new String[]{"introspect"});
    assertThat("Should return exit code 0 with database configured", exitCode, is(0));
  }

  @Test
  void testIntrospectSchemaWithDatabaseSucceeds() {
    int exitCode = cliHandler.execute(new String[]{"introspect", "main"});
    assertThat("Should return exit code 0 with database configured", exitCode, is(0));
  }

  @Test
  void testIntrospectTableWithDatabaseSucceeds() {
    int exitCode = cliHandler.execute(new String[]{"introspect", "main", "users"});
    assertThat("Should return exit code 0 with database configured", exitCode, is(0));
  }

  @Test
  void testQueryWithDatabaseSucceeds() {
    int exitCode = cliHandler.execute(new String[]{"query", "SELECT 1"});
    assertThat("Should return exit code 0 with database configured", exitCode, is(0));
  }

  @Test
  void testQueryWithPageParameterSucceeds() {
    int exitCode = cliHandler.execute(new String[]{"query", "SELECT 1", "--page", "2"});
    assertThat("Should return exit code 0 with page parameter", exitCode, is(0));
  }
}
