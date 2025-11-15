package org.geekden.mcp.cli;

import org.geekden.mcp.AbstractDatabaseIntegrationTest;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * Integration tests for CLI introspection commands.
 * <p>
 * Uses CapturingOutput to verify both exit codes and actual output content.
 */
@QuarkusTest
class CliIntrospectionTest extends AbstractDatabaseIntegrationTest {

  @Inject
  CliCommandHandler cliHandler;

  @Inject
  CapturingOutput output;

  @BeforeEach
  void setUp() {
    output.reset();
  }

  @Test
  void testCliIntrospectAllSucceeds() {
    int exitCode = cliHandler.execute(new String[]{"introspect"});
    assertThat("Should succeed with exit code 0", exitCode, is(0));

    String stdout = output.getStdout();
    assertThat("Should contain output", stdout, not(isEmptyOrNullString()));
  }

  @Test
  void testCliIntrospectSchemaSucceeds() {
    int exitCode = cliHandler.execute(new String[]{"introspect", "main"});
    assertThat("Should succeed with exit code 0", exitCode, is(0));
  }

  @Test
  void testCliIntrospectNonExistentSchemaSucceeds() {
    int exitCode = cliHandler.execute(new String[]{"introspect", "nonexistent_schema"});
    assertThat("Should succeed with exit code 0", exitCode, is(0));
  }

  @Test
  void testCliIntrospectNonExistentTableSucceeds() {
    int exitCode = cliHandler.execute(new String[]{"introspect", "main", "nonexistent_table"});
    assertThat("Should succeed with exit code 0", exitCode, is(0));
  }

  @Test
  void testCliIntrospectTooManyArgsFails() {
    int exitCode = cliHandler.execute(new String[]{"introspect", "arg1", "arg2", "arg3"});
    assertThat("Should fail with too many arguments", exitCode, is(1));

    String stderr = output.getStderr();
    assertThat("Should show unmatched argument error", stderr, containsString("Unmatched argument"));
  }
}
