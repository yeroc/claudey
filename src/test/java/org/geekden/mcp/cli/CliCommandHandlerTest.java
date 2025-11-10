package org.geekden.mcp.cli;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;
import uk.org.webcompere.systemstubs.stream.SystemErr;

import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * Test CLI command handler functionality without database configured.
 */
@QuarkusTest
@TestProfile(CliCommandHandlerTest.NoDatabaseProfile.class)
@ExtendWith(SystemStubsExtension.class)
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

  @SystemStub
  private SystemErr systemErr;

  @Inject
  CliCommandHandler cliHandler;

  @Test
  void testCliHandlerInjection() {
    assertThat("CliCommandHandler should be injected",
        cliHandler, is(notNullValue()));
  }

  @Test
  void testIntrospectWithoutDatabaseFails() throws Exception {
    int exitCode = cliHandler.execute(new String[]{"introspect"});
    assertThat("Should return exit code 1 when database not configured",
        exitCode, is(1));

    assertThat("Should print database configuration error",
        systemErr.getText(), containsString("Database not configured"));
  }

  @Test
  void testQueryWithoutDatabaseFails() throws Exception {
    int exitCode = cliHandler.execute(new String[]{"query", "SELECT 1"});
    assertThat("Should return exit code 1 when database not configured",
        exitCode, is(1));

    assertThat("Should print database configuration error",
        systemErr.getText(), containsString("Database not configured"));
  }

  @Test
  void testInvalidCommand() throws Exception {
    int exitCode = cliHandler.execute(new String[]{"invalid"});
    assertThat("Should return exit code 1 for invalid command",
        exitCode, is(1));

    assertThat("Should print unknown command error",
        systemErr.getText(), containsString("Unknown command"));
  }

  @Test
  void testNoArgumentsShowsUsage() throws Exception {
    int exitCode = cliHandler.execute(new String[]{});
    assertThat("Should return exit code 1 when no arguments",
        exitCode, is(1));

    assertThat("Should print usage information",
        systemErr.getText(), containsString("Usage:"));
  }

  @Test
  void testIntrospectInvalidArguments() throws Exception {
    // Too many arguments for introspect (more than 3)
    int exitCode = cliHandler.execute(new String[]{"introspect", "schema", "table", "extra"});
    assertThat("Should return exit code 1 for invalid introspect arguments",
        exitCode, is(1));

    assertThat("Should print error about invalid arguments",
        systemErr.getText(), anyOf(containsString("Invalid arguments"), containsString("Usage:")));
  }

  @Test
  void testQueryMissingArgument() throws Exception {
    int exitCode = cliHandler.execute(new String[]{"query"});
    assertThat("Should return exit code 1 when query SQL is missing",
        exitCode, is(1));

    assertThat("Should print missing query error",
        systemErr.getText(), containsString("Missing SQL query"));
  }

  @Test
  void testQueryInvalidPageNumber() throws Exception {
    int exitCode = cliHandler.execute(new String[]{"query", "SELECT 1", "--page", "invalid"});
    assertThat("Should return exit code 1 for invalid page number",
        exitCode, is(1));

    assertThat("Should print invalid page number error",
        systemErr.getText(), containsString("Invalid page number"));
  }
}
