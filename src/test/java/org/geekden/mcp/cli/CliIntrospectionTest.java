package org.geekden.mcp.cli;

import org.geekden.mcp.AbstractDatabaseIntegrationTest;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * Integration tests for CLI introspection commands.
 * <p>
 * Note: Output verification is not possible with MCP stdio extension active.
 * These tests verify exit codes and basic execution flow.
 * Output formatting is verified manually via uber-JAR testing.
 */
@QuarkusTest
class CliIntrospectionTest extends AbstractDatabaseIntegrationTest {

  @Inject
  CliCommandHandler cliHandler;

  @Test
  void testCliIntrospectAllSucceeds() {
    int exitCode = cliHandler.execute(new String[]{"introspect"});
    assertThat("Should succeed with exit code 0", exitCode, is(0));
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
  }
}
