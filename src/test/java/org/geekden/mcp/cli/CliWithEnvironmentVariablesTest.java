package org.geekden.mcp.cli;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.geekden.mcp.config.DatabaseConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test CLI with database configured via environment variables.
 * This matches how the application is used in CI workflows.
 */
@QuarkusTest
class CliWithEnvironmentVariablesTest {

  @Inject
  CliCommandHandler cliHandler;

  @Inject
  DatabaseConfig config;

  @Test
  @EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
  void testIntrospectWithEnvironmentVariables() {
    // Print configuration for visibility in test output
    System.out.println("=== CLI Test with Environment Variables ===");
    System.out.println("DB_URL: " + System.getenv("DB_URL"));
    System.out.println("DB_USERNAME: " + (System.getenv("DB_USERNAME") != null ? "***" : "not set"));
    System.out.println("DB_PASSWORD: " + (System.getenv("DB_PASSWORD") != null ? "***" : "not set"));
    System.out.println("==========================================");

    assertTrue(config.isConfigured(), "Database should be configured via environment variables");

    ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    ByteArrayOutputStream errContent = new ByteArrayOutputStream();
    PrintStream originalOut = System.out;
    PrintStream originalErr = System.err;

    System.setOut(new PrintStream(outContent));
    System.setErr(new PrintStream(errContent));

    try {
      int exitCode = cliHandler.execute(new String[]{"introspect"});

      System.setOut(originalOut);
      System.setErr(originalErr);

      String output = outContent.toString();
      String errorOutput = errContent.toString();

      System.out.println("=== CLI Output ===");
      System.out.println(output);
      if (!errorOutput.isEmpty()) {
        System.out.println("=== CLI Error Output ===");
        System.out.println(errorOutput);
      }
      System.out.println("==================");

      assertEquals(0, exitCode, "CLI introspect should succeed with configured database");
      assertFalse(errorOutput.contains("Database not configured"),
          "Should not show database configuration error");

    } finally {
      System.setOut(originalOut);
      System.setErr(originalErr);
    }
  }

  @Test
  @EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
  void testQueryWithEnvironmentVariables() {
    assertTrue(config.isConfigured(), "Database should be configured via environment variables");

    ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    ByteArrayOutputStream errContent = new ByteArrayOutputStream();
    PrintStream originalOut = System.out;
    PrintStream originalErr = System.err;

    System.setOut(new PrintStream(outContent));
    System.setErr(new PrintStream(errContent));

    try {
      int exitCode = cliHandler.execute(new String[]{"query", "SELECT 1"});

      System.setOut(originalOut);
      System.setErr(originalErr);

      String output = outContent.toString();
      String errorOutput = errContent.toString();

      System.out.println("=== CLI Query Output ===");
      System.out.println(output);
      if (!errorOutput.isEmpty()) {
        System.out.println("=== CLI Error Output ===");
        System.out.println(errorOutput);
      }
      System.out.println("========================");

      assertEquals(0, exitCode, "CLI query should succeed with configured database");
      assertFalse(errorOutput.contains("Database not configured"),
          "Should not show database configuration error");

    } finally {
      System.setOut(originalOut);
      System.setErr(originalErr);
    }
  }

  @Test
  @EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".*postgres.*", disabledReason = "PostgreSQL-specific test")
  void testPostgreSQLConnection() {
    System.out.println("=== PostgreSQL-Specific Test ===");
    System.out.println("Verifying PostgreSQL connection via CLI");

    assertTrue(config.getJdbcUrl().orElse("").contains("postgres"),
        "Should be using PostgreSQL database");

    ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    PrintStream originalOut = System.out;
    System.setOut(new PrintStream(outContent));

    try {
      int exitCode = cliHandler.execute(new String[]{"introspect"});
      System.setOut(originalOut);

      assertEquals(0, exitCode, "PostgreSQL introspect should succeed");
      System.out.println("PostgreSQL CLI introspect succeeded");
    } finally {
      System.setOut(originalOut);
    }
  }

  @Test
  @EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".*sqlite.*", disabledReason = "SQLite-specific test")
  void testSQLiteConnection() {
    System.out.println("=== SQLite-Specific Test ===");
    System.out.println("Verifying SQLite connection via CLI");

    assertTrue(config.getJdbcUrl().orElse("").contains("sqlite"),
        "Should be using SQLite database");

    ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    PrintStream originalOut = System.out;
    System.setOut(new PrintStream(outContent));

    try {
      int exitCode = cliHandler.execute(new String[]{"introspect"});
      System.setOut(originalOut);

      assertEquals(0, exitCode, "SQLite introspect should succeed");
      System.out.println("SQLite CLI introspect succeeded");
    } finally {
      System.setOut(originalOut);
    }
  }
}
