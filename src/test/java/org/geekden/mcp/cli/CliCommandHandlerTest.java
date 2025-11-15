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
    assertThat("Should return exit code 2 for invalid command (Picocli usage error)", exitCode, is(2));

    String stderr = output.getStderr();
    assertThat("Should show unmatched argument error", stderr, containsString("Unmatched argument"));
  }

  @Test
  void testIntrospectTooManyArgumentsFails() {
    int exitCode = cliHandler.execute(new String[]{"introspect", "schema", "table", "extra"});
    assertThat("Should return exit code 2 for invalid introspect arguments (Picocli usage error)", exitCode, is(2));
  }

  @Test
  void testQueryMissingArgumentFails() {
    int exitCode = cliHandler.execute(new String[]{"query"});
    assertThat("Should return exit code 2 when query SQL is missing (Picocli usage error)", exitCode, is(2));
  }

  @Test
  void testQueryInvalidPageNumberFails() {
    int exitCode = cliHandler.execute(new String[]{"query", "SELECT 1", "--page", "invalid"});
    assertThat("Should return exit code 2 for invalid page number (Picocli usage error)", exitCode, is(2));
  }
}
