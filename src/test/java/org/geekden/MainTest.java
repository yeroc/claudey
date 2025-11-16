package org.geekden;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * Minimal integration test for Main.
 * Verifies that Picocli is properly wired up with our commands.
 */
@QuarkusTest
class MainTest {

  @Inject
  Main app;

  @Inject
  CommandLine.IFactory factory;

  @Test
  void testHelpCommand() {
    CommandLine cmd = new CommandLine(app, factory);
    int exitCode = cmd.execute("--help");
    assertThat("Help should succeed", exitCode, is(0));
  }

  @Test
  void testVersionCommand() {
    CommandLine cmd = new CommandLine(app, factory);
    int exitCode = cmd.execute("--version");
    assertThat("Version should succeed", exitCode, is(0));
  }

  @Test
  void testInvalidCommandFails() {
    CommandLine cmd = new CommandLine(app, factory);
    int exitCode = cmd.execute("nonexistent");
    assertThat("Invalid command should return usage error", exitCode, is(2));
  }

  // Tests for Picocli parameter validation through the full command-line flow

  @Test
  void testQueryCommandMissingRequiredParameterFails() {
    CommandLine cmd = new CommandLine(app, factory);
    int exitCode = cmd.execute("query");
    assertThat("Query without SQL should fail with usage error", exitCode, is(2));
  }

  @Test
  void testQueryCommandNonNumericPageFails() {
    CommandLine cmd = new CommandLine(app, factory);
    int exitCode = cmd.execute("query", "SELECT 1", "--page", "abc");
    assertThat("Non-numeric page value should fail with usage error", exitCode, is(2));
  }

  @Test
  void testQueryCommandMissingPageValueFails() {
    CommandLine cmd = new CommandLine(app, factory);
    int exitCode = cmd.execute("query", "SELECT 1", "--page");
    assertThat("Missing --page value should fail with usage error", exitCode, is(2));
  }

  @Test
  void testIntrospectCommandTooManyArgumentsFails() {
    CommandLine cmd = new CommandLine(app, factory);
    int exitCode = cmd.execute("introspect", "arg1", "arg2", "arg3");
    assertThat("Too many arguments should fail with usage error", exitCode, is(2));
  }
}
