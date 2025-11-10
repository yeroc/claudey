package org.geekden.mcp.cli;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static uk.org.webcompere.systemstubs.SystemStubs.tapSystemErr;

/**
 * Test CLI command handler functionality without database configured.
 */
@QuarkusTest
@TestProfile(CliCommandHandlerTest.NoDatabaseProfile.class)
class CliCommandHandlerTest {

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
  CliCommandHandler cliHandler;

  @Test
  void testCliHandlerInjection() {
    assertThat("CliCommandHandler should be injected",
        cliHandler, is(notNullValue()));
  }

  @Test
  void testIntrospectWithoutDatabaseFails() throws Exception {
    String stderr = tapSystemErr(() -> {
      int exitCode = cliHandler.execute(new String[]{"introspect"});
      assertThat("Should return exit code 1 when database not configured",
          exitCode, is(1));
    });

    assertThat("Should print database configuration error",
        stderr, containsString("Database not configured"));
  }

  @Test
  void testQueryWithoutDatabaseFails() throws Exception {
    String stderr = tapSystemErr(() -> {
      int exitCode = cliHandler.execute(new String[]{"query", "SELECT 1"});
      assertThat("Should return exit code 1 when database not configured",
          exitCode, is(1));
    });

    assertThat("Should print database configuration error",
        stderr, containsString("Database not configured"));
  }

  @Test
  void testInvalidCommand() throws Exception {
    String stderr = tapSystemErr(() -> {
      int exitCode = cliHandler.execute(new String[]{"invalid"});
      assertThat("Should return exit code 1 for invalid command",
          exitCode, is(1));
    });

    assertThat("Should print unknown command error",
        stderr, containsString("Unknown command"));
  }

  @Test
  void testNoArgumentsShowsUsage() throws Exception {
    String stderr = tapSystemErr(() -> {
      int exitCode = cliHandler.execute(new String[]{});
      assertThat("Should return exit code 1 when no arguments",
          exitCode, is(1));
    });

    assertThat("Should print usage information",
        stderr, containsString("Usage:"));
  }

  @Test
  void testIntrospectInvalidArguments() throws Exception {
    String stderr = tapSystemErr(() -> {
      // Too many arguments for introspect (more than 3)
      int exitCode = cliHandler.execute(new String[]{"introspect", "schema", "table", "extra"});
      assertThat("Should return exit code 1 for invalid introspect arguments",
          exitCode, is(1));
    });

    assertThat("Should print error about invalid arguments",
        stderr, anyOf(containsString("Invalid arguments"), containsString("Usage:")));
  }

  @Test
  void testQueryMissingArgument() throws Exception {
    String stderr = tapSystemErr(() -> {
      int exitCode = cliHandler.execute(new String[]{"query"});
      assertThat("Should return exit code 1 when query SQL is missing",
          exitCode, is(1));
    });

    assertThat("Should print missing query error",
        stderr, containsString("Missing SQL query"));
  }

  @Test
  void testQueryInvalidPageNumber() throws Exception {
    String stderr = tapSystemErr(() -> {
      int exitCode = cliHandler.execute(new String[]{"query", "SELECT 1", "--page", "invalid"});
      assertThat("Should return exit code 1 for invalid page number",
          exitCode, is(1));
    });

    assertThat("Should print invalid page number error",
        stderr, containsString("Invalid page number"));
  }
}
