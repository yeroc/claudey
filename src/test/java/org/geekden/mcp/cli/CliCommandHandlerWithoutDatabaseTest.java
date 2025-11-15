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
 * Test CLI command handler behavior when database is not configured.
 * <p>
 * Verifies that appropriate error messages are shown when database URL is missing.
 */
@QuarkusTest
@TestProfile(CliCommandHandlerWithoutDatabaseTest.NoDatabaseProfile.class)
class CliCommandHandlerWithoutDatabaseTest extends AbstractDatabaseIntegrationTest {

  /**
   * Test profile that clears database configuration.
   */
  public static class NoDatabaseProfile implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
      return Map.of(
          "DB_URL", "",
          "quarkus.datasource.jdbc.url", ""
      );
    }
  }

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

  @Test
  void testIntrospectWithoutDatabaseFails() {
    int exitCode = execute(new String[]{"introspect"});
    assertThat("Should return exit code 1 when database not configured", exitCode, is(1));

    String stderr = output.getStderr();
    assertThat("Should show database not configured error", stderr, containsString("Database not configured"));
  }

  @Test
  void testQueryWithoutDatabaseFails() {
    int exitCode = execute(new String[]{"query", "SELECT 1"});
    assertThat("Should return exit code 1 when database not configured", exitCode, is(1));

    String stderr = output.getStderr();
    assertThat("Should show database not configured error", stderr, containsString("Database not configured"));
  }
}
