package org.geekden.mcp.cli;

import org.geekden.mcp.AbstractDatabaseIntegrationTest;
import org.geekden.MainApplication;
import picocli.CommandLine;

import io.quarkus.test.junit.QuarkusTest;
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

import static org.hamcrest.MatcherAssert.assertThat;
import org.geekden.MainApplication;
import picocli.CommandLine;
import static org.hamcrest.Matchers.*;
import org.geekden.MainApplication;
import picocli.CommandLine;

/**
 * Integration tests for CLI introspection commands.
 * <p>
 * Uses CapturingOutput to verify both exit codes and actual output content.
 */
@QuarkusTest
class IntrospectCommandTest extends AbstractDatabaseIntegrationTest {

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
  void testCliIntrospectAllSucceeds() {
    int exitCode = execute(new String[]{"introspect"});
    assertThat("Should succeed with exit code 0", exitCode, is(0));

    String stdout = output.getStdout();
    assertThat("Should contain output", stdout, not(isEmptyOrNullString()));
  }

  @Test
  void testCliIntrospectSchemaSucceeds() {
    int exitCode = execute(new String[]{"introspect", "main"});
    assertThat("Should succeed with exit code 0", exitCode, is(0));
  }

  @Test
  void testCliIntrospectNonExistentSchemaSucceeds() {
    int exitCode = execute(new String[]{"introspect", "nonexistent_schema"});
    assertThat("Should succeed with exit code 0", exitCode, is(0));
  }

  @Test
  void testCliIntrospectNonExistentTableSucceeds() {
    int exitCode = execute(new String[]{"introspect", "main", "nonexistent_table"});
    assertThat("Should succeed with exit code 0", exitCode, is(0));
  }

  @Test
  void testCliIntrospectTooManyArgsFails() {
    int exitCode = execute(new String[]{"introspect", "arg1", "arg2", "arg3"});
    assertThat("Should fail with too many arguments (Picocli usage error)", exitCode, is(2));

    String stderr = output.getStderr();
    assertThat("Should show unmatched argument error", stderr, containsString("Unmatched argument"));
  }
}
