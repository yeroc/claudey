package org.geekden.mcp.cli;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test CLI command handler with SQLite database configured.
 */
@QuarkusTest
@TestProfile(CliCommandHandlerWithDatabaseTest.SqliteTestProfile.class)
class CliCommandHandlerWithDatabaseTest {

  @Inject
  CliCommandHandler cliHandler;

  /**
   * Test profile that configures SQLite in-memory database.
   */
  public static class SqliteTestProfile implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
      return Map.of(
          "quarkus.datasource.db-kind", "sqlite",
          "quarkus.datasource.jdbc.url", "jdbc:sqlite::memory:"
      );
    }
  }

  @Test
  void testIntrospectWithDatabaseSucceeds() {
    ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    PrintStream originalOut = System.out;
    System.setOut(new PrintStream(outContent));

    try {
      int exitCode = cliHandler.execute(new String[]{"introspect"});
      assertEquals(0, exitCode, "Should return exit code 0 with database configured");

      String output = outContent.toString();
      assertTrue(output.contains("Listing all schemas") || output.contains("pending"),
          "Should execute introspect command successfully");
    } finally {
      System.setOut(originalOut);
    }
  }

  @Test
  void testIntrospectSchemaWithDatabaseSucceeds() {
    ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    PrintStream originalOut = System.out;
    System.setOut(new PrintStream(outContent));

    try {
      int exitCode = cliHandler.execute(new String[]{"introspect", "public"});
      assertEquals(0, exitCode, "Should return exit code 0 with database configured");

      String output = outContent.toString();
      assertTrue(output.contains("Listing tables") || output.contains("pending"),
          "Should execute introspect schema command successfully");
    } finally {
      System.setOut(originalOut);
    }
  }

  @Test
  void testIntrospectTableWithDatabaseSucceeds() {
    ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    PrintStream originalOut = System.out;
    System.setOut(new PrintStream(outContent));

    try {
      int exitCode = cliHandler.execute(new String[]{"introspect", "public", "users"});
      assertEquals(0, exitCode, "Should return exit code 0 with database configured");

      String output = outContent.toString();
      assertTrue(output.contains("Describing table") || output.contains("pending"),
          "Should execute introspect table command successfully");
    } finally {
      System.setOut(originalOut);
    }
  }

  @Test
  void testQueryWithDatabaseSucceeds() {
    ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    PrintStream originalOut = System.out;
    System.setOut(new PrintStream(outContent));

    try {
      int exitCode = cliHandler.execute(new String[]{"query", "SELECT 1"});
      assertEquals(0, exitCode, "Should return exit code 0 with database configured");

      String output = outContent.toString();
      assertTrue(output.contains("Executing query") || output.contains("pending"),
          "Should execute query command successfully");
    } finally {
      System.setOut(originalOut);
    }
  }

  @Test
  void testQueryWithPageParameterSucceeds() {
    ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    PrintStream originalOut = System.out;
    System.setOut(new PrintStream(outContent));

    try {
      int exitCode = cliHandler.execute(new String[]{"query", "SELECT 1", "--page", "2"});
      assertEquals(0, exitCode, "Should return exit code 0 with page parameter");

      String output = outContent.toString();
      assertTrue(output.contains("page 2"),
          "Should execute query with page parameter");
    } finally {
      System.setOut(originalOut);
    }
  }
}
