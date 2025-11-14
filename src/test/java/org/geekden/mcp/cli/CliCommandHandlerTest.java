package org.geekden.mcp.cli;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * Test CLI command handler syntax validation.
 * <p>
 * Note: Output verification is not possible with MCP stdio extension active.
 * These tests verify exit codes and basic command validation logic.
 */
@QuarkusTest
class CliCommandHandlerTest {

  @Inject
  CliCommandHandler cliHandler;

  @Test
  void testCliHandlerInjection() {
    assertThat("CliCommandHandler should be injected",
        cliHandler, is(notNullValue()));
  }

  @Test
  void testInvalidCommandFails() {
    int exitCode = cliHandler.execute(new String[]{"invalid"});
    assertThat("Should return exit code 1 for invalid command", exitCode, is(1));
  }

  @Test
  void testNoArgumentsFails() {
    int exitCode = cliHandler.execute(new String[]{});
    assertThat("Should return exit code 1 when no arguments", exitCode, is(1));
  }

  @Test
  void testIntrospectTooManyArgumentsFails() {
    int exitCode = cliHandler.execute(new String[]{"introspect", "schema", "table", "extra"});
    assertThat("Should return exit code 1 for invalid introspect arguments", exitCode, is(1));
  }

  @Test
  void testQueryMissingArgumentFails() {
    int exitCode = cliHandler.execute(new String[]{"query"});
    assertThat("Should return exit code 1 when query SQL is missing", exitCode, is(1));
  }

  @Test
  void testQueryInvalidPageNumberFails() {
    int exitCode = cliHandler.execute(new String[]{"query", "SELECT 1", "--page", "invalid"});
    assertThat("Should return exit code 1 for invalid page number", exitCode, is(1));
  }
}
