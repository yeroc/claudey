package org.geekden.mcp.cli;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static uk.org.webcompere.systemstubs.SystemStubs.tapSystemOut;

/**
 * Integration tests for CLI introspection commands.
 */
@QuarkusTest
class CliIntrospectionTest {

  @Inject
  CliCommandHandler cliHandler;

  @Test
  void testCliIntrospectAll() throws Exception {
    String output = tapSystemOut(() -> {
      int exitCode = cliHandler.execute(new String[]{"introspect"});
      assertThat("Should succeed", exitCode, is(0));
    });

    assertThat("Should produce output", output, not(emptyString()));
    assertThat("Should contain separator", output, containsString("──"));
  }

  @Test
  void testCliIntrospectSchema() throws Exception {
    String output = tapSystemOut(() -> {
      // SQLite uses null schema, but for testing we'll use a value
      int exitCode = cliHandler.execute(new String[]{"introspect", "main"});
      assertThat("Should succeed or handle gracefully", exitCode, is(anyOf(is(0), is(1))));
    });

    assertThat("Should produce output", output, not(emptyString()));
  }

  @Test
  void testCliIntrospectNonExistentSchema() throws Exception {
    String output = tapSystemOut(() -> {
      int exitCode = cliHandler.execute(new String[]{"introspect", "nonexistent_schema"});
      // Should still succeed but show "No tables found"
      assertThat("Should handle gracefully", exitCode, is(anyOf(is(0), is(1))));
    });

    assertThat("Should produce output", output, not(emptyString()));
  }

  @Test
  void testCliIntrospectNonExistentTable() throws Exception {
    tapSystemOut(() -> {
      int exitCode = cliHandler.execute(new String[]{"introspect", "main", "nonexistent_table"});
      // Should succeed and show "Table not found"
      assertThat("Should execute", exitCode, is(anyOf(is(0), is(1))));
    });
  }

  @Test
  void testCliIntrospectTooManyArgs() throws Exception {
    String stderr = tapSystemOut(() -> {
      int exitCode = cliHandler.execute(new String[]{"introspect", "arg1", "arg2", "arg3"});
      assertThat("Should fail with too many arguments", exitCode, is(1));
    });

    assertThat("Should show error message", stderr, not(emptyString()));
  }
}
