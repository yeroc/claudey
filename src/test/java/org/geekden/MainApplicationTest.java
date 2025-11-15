package org.geekden;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * Minimal integration test for MainApplication.
 * Verifies that Picocli is properly wired up with our commands.
 */
@QuarkusTest
class MainApplicationTest {

  @Inject
  MainApplication app;

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
}
