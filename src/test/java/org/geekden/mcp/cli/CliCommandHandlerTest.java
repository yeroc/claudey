package org.geekden.mcp.cli;

import org.geekden.mcp.AbstractDatabaseIntegrationTest;
import org.geekden.MainApplication;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

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
  MainApplication app;

  @Inject
  CommandLine.IFactory factory;

  @Inject
  CapturingOutput output;

  @BeforeEach
  void setUp() {
    output.reset();
  }

  private int execute(String... args) {
    CommandLine cmd = new CommandLine(app, factory);
    return cmd.execute(args);
  }

  @Test
  void testInvalidCommandFails() {
    int exitCode = execute("invalid");
    assertThat("Should return exit code 2 for invalid command (Picocli usage error)", exitCode, is(2));

    String stderr = output.getStderr();
    assertThat("Should show unmatched argument error", stderr, containsString("Unmatched argument"));
  }

  @Test
  void testIntrospectTooManyArgumentsFails() {
    int exitCode = execute("introspect", "schema", "table", "extra");
    assertThat("Should return exit code 2 for invalid introspect arguments (Picocli usage error)", exitCode, is(2));
  }

  @Test
  void testQueryMissingArgumentFails() {
    int exitCode = execute("query");
    assertThat("Should return exit code 2 when query SQL is missing (Picocli usage error)", exitCode, is(2));
  }

  @Test
  void testQueryInvalidPageNumberFails() {
    int exitCode = execute("query", "SELECT 1", "--page", "invalid");
    assertThat("Should return exit code 2 for invalid page number (Picocli usage error)", exitCode, is(2));
  }
}
