package org.geekden.mcp.cli;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test CLI command handler functionality without database configured.
 */
@QuarkusTest
@TestProfile(CliCommandHandlerTest.NoDatabaseProfile.class)
class CliCommandHandlerTest {

  /**
   * Test profile that clears database configuration.
   */
  public static class NoDatabaseProfile implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
      return Map.of(
          "DB_URL", "",
          "quarkus.datasource.jdbc.url", ""
      );
    }
  }

  @Inject
  CliCommandHandler cliHandler;

  @Test
  void testCliHandlerInjection() {
    assertNotNull(cliHandler, "CliCommandHandler should be injected");
  }

  @Test
  void testIntrospectWithoutDatabaseFails() {
    // Capture stderr to check error message
    ByteArrayOutputStream errContent = new ByteArrayOutputStream();
    PrintStream originalErr = System.err;
    System.setErr(new PrintStream(errContent));

    try {
      int exitCode = cliHandler.execute(new String[]{"introspect"});
      assertEquals(1, exitCode, "Should return exit code 1 when database not configured");

      String errorOutput = errContent.toString();
      assertThat("Should print database configuration error",
          errorOutput, containsString("Database not configured"));
    } finally {
      System.setErr(originalErr);
    }
  }

  @Test
  void testQueryWithoutDatabaseFails() {
    ByteArrayOutputStream errContent = new ByteArrayOutputStream();
    PrintStream originalErr = System.err;
    System.setErr(new PrintStream(errContent));

    try {
      int exitCode = cliHandler.execute(new String[]{"query", "SELECT 1"});
      assertEquals(1, exitCode, "Should return exit code 1 when database not configured");

      String errorOutput = errContent.toString();
      assertThat("Should print database configuration error",
          errorOutput, containsString("Database not configured"));
    } finally {
      System.setErr(originalErr);
    }
  }

  @Test
  void testInvalidCommand() {
    ByteArrayOutputStream errContent = new ByteArrayOutputStream();
    PrintStream originalErr = System.err;
    System.setErr(new PrintStream(errContent));

    try {
      int exitCode = cliHandler.execute(new String[]{"invalid"});
      assertEquals(1, exitCode, "Should return exit code 1 for invalid command");

      String errorOutput = errContent.toString();
      assertThat("Should print unknown command error",
          errorOutput, containsString("Unknown command"));
    } finally {
      System.setErr(originalErr);
    }
  }

  @Test
  void testNoArgumentsShowsUsage() {
    ByteArrayOutputStream errContent = new ByteArrayOutputStream();
    PrintStream originalErr = System.err;
    System.setErr(new PrintStream(errContent));

    try {
      int exitCode = cliHandler.execute(new String[]{});
      assertEquals(1, exitCode, "Should return exit code 1 when no arguments");

      String errorOutput = errContent.toString();
      assertThat("Should print usage information",
          errorOutput, containsString("Usage:"));
    } finally {
      System.setErr(originalErr);
    }
  }

  @Test
  void testIntrospectInvalidArguments() {
    ByteArrayOutputStream errContent = new ByteArrayOutputStream();
    PrintStream originalErr = System.err;
    System.setErr(new PrintStream(errContent));

    try {
      // Too many arguments for introspect (more than 3)
      int exitCode = cliHandler.execute(new String[]{"introspect", "schema", "table", "extra"});
      assertEquals(1, exitCode, "Should return exit code 1 for invalid introspect arguments");

      String errorOutput = errContent.toString();
      assertThat("Should print error about invalid arguments",
          errorOutput, anyOf(containsString("Invalid arguments"), containsString("Usage:")));
    } finally {
      System.setErr(originalErr);
    }
  }

  @Test
  void testQueryMissingArgument() {
    ByteArrayOutputStream errContent = new ByteArrayOutputStream();
    PrintStream originalErr = System.err;
    System.setErr(new PrintStream(errContent));

    try {
      int exitCode = cliHandler.execute(new String[]{"query"});
      assertEquals(1, exitCode, "Should return exit code 1 when query SQL is missing");

      String errorOutput = errContent.toString();
      assertThat("Should print missing query error",
          errorOutput, containsString("Missing SQL query"));
    } finally {
      System.setErr(originalErr);
    }
  }

  @Test
  void testQueryInvalidPageNumber() {
    ByteArrayOutputStream errContent = new ByteArrayOutputStream();
    PrintStream originalErr = System.err;
    System.setErr(new PrintStream(errContent));

    try {
      int exitCode = cliHandler.execute(new String[]{"query", "SELECT 1", "--page", "invalid"});
      assertEquals(1, exitCode, "Should return exit code 1 for invalid page number");

      String errorOutput = errContent.toString();
      assertThat("Should print invalid page number error",
          errorOutput, containsString("Invalid page number"));
    } finally {
      System.setErr(originalErr);
    }
  }
}
