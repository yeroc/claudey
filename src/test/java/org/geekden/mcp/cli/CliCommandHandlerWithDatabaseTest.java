package org.geekden.mcp.cli;

import org.geekden.mcp.AbstractDatabaseIntegrationTest;
import org.geekden.MainApplication;
import picocli.CommandLine;

import io.quarkus.test.junit.QuarkusTest;
import org.geekden.MainApplication;
import picocli.CommandLine;
import io.quarkus.test.junit.QuarkusTestProfile;
import org.geekden.MainApplication;
import picocli.CommandLine;
import io.quarkus.test.junit.TestProfile;
import org.geekden.MainApplication;
import picocli.CommandLine;
import jakarta.inject.Inject;
import org.geekden.MainApplication;
import picocli.CommandLine;
import org.junit.jupiter.api.BeforeEach;
import org.geekden.MainApplication;
import picocli.CommandLine;
import org.junit.jupiter.api.Test;
import org.geekden.MainApplication;
import picocli.CommandLine;

import java.util.Map;
import org.geekden.MainApplication;
import picocli.CommandLine;

import static org.hamcrest.MatcherAssert.assertThat;
import org.geekden.MainApplication;
import picocli.CommandLine;
import static org.hamcrest.Matchers.*;
import org.geekden.MainApplication;
import picocli.CommandLine;

/**
 * Test CLI command handler with SQLite database configured.
 * <p>
 * Verifies CLI commands work correctly with an in-memory SQLite database.
 */
@QuarkusTest
@TestProfile(CliCommandHandlerWithDatabaseTest.SqliteTestProfile.class)
class CliCommandHandlerWithDatabaseTest extends AbstractDatabaseIntegrationTest {

  @Inject
  MainApplication app;

  @Inject
  CommandLine.IFactory factory;

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
    int exitCode = execute(new String[]{"introspect"});
    assertThat("Should return exit code 0 with database configured", exitCode, is(0));
  }

  @Test
  void testIntrospectSchemaWithDatabaseSucceeds() {
    int exitCode = execute(new String[]{"introspect", "main"});
    assertThat("Should return exit code 0 with database configured", exitCode, is(0));
  }

  @Test
  void testIntrospectTableWithDatabaseSucceeds() {
    int exitCode = execute(new String[]{"introspect", "main", "users"});
    assertThat("Should return exit code 0 with database configured", exitCode, is(0));
  }

  @Test
  void testQueryWithDatabaseSucceeds() {
    int exitCode = execute(new String[]{"query", "SELECT 1"});
    assertThat("Should return exit code 0 with database configured", exitCode, is(0));
  }

  @Test
  void testQueryWithPageParameterSucceeds() {
    int exitCode = execute(new String[]{"query", "SELECT 1", "--page", "2"});
    assertThat("Should return exit code 0 with page parameter", exitCode, is(0));
  }
}
