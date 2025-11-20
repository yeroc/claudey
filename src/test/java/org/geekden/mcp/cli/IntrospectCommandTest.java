package org.geekden.mcp.cli;

import org.geekden.mcp.IsolatedDatabaseProfile;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * Integration tests for CLI introspection commands.
 * <p>
 * Uses CapturingOutput to verify both exit codes and actual output content.
 */
@QuarkusTest
@TestProfile(IntrospectCommandTest.Profile.class)
class IntrospectCommandTest {

  public static class Profile extends IsolatedDatabaseProfile {
  }

  @Inject
  IntrospectCommand command;

  @Inject
  CommandLine.IFactory factory;

  @Inject
  CapturingOutput output;

  @BeforeEach
  void setUp() {
    output.reset();
  }

  private int execute(String... args) {
    CommandLine cmd = new CommandLine(command, factory);
    return cmd.execute(args);
  }

  @Test
  void testCliIntrospectAllSucceeds() {
    int exitCode = execute();
    assertThat("Should succeed with exit code 0", exitCode, is(0));

    String stdout = output.getStdout();
    assertThat("Should contain output", stdout, not(emptyOrNullString()));
  }

  @Test
  void testCliIntrospectSchemaSucceeds() {
    int exitCode = execute("main");
    assertThat("Should succeed with exit code 0", exitCode, is(0));
  }

  @Test
  void testCliIntrospectNonExistentSchemaSucceeds() {
    int exitCode = execute("nonexistent_schema");
    assertThat("Should succeed with exit code 0", exitCode, is(0));
  }

  @Test
  void testCliIntrospectNonExistentTableSucceeds() {
    int exitCode = execute("main", "nonexistent_table");
    assertThat("Should succeed with exit code 0", exitCode, is(0));
  }
}
