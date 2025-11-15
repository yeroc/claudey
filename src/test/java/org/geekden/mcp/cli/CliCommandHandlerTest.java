package org.geekden.mcp.cli;

import org.geekden.mcp.AbstractDatabaseIntegrationTest;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * Test CLI command handler syntax validation.
 * <p>
 * Uses CapturingOutput to verify both exit codes and error messages.
 */
@QuarkusTest
class CliCommandHandlerTest extends AbstractDatabaseIntegrationTest {

  @Inject
  CliCommandHandler cliHandler;

  @Inject
  CapturingOutput output;

  @BeforeEach
  void setUp() {
    output.reset();
  }

  @Test
  void testCliHandlerInjection() {
    assertThat("CliCommandHandler should be injected",
        cliHandler, is(notNullValue()));
  }

  @Test
  void testInvalidCommandFails() {
    int exitCode = cliHandler.execute(new String[]{"invalid"});
    assertThat("Should return exit code 1 for invalid command", exitCode, is(1));

    String stderr = output.getStderr();
    assertThat("Should show unmatched argument error", stderr, containsString("Unmatched argument"));
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
