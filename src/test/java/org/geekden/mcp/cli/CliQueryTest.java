package org.geekden.mcp.cli;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.Statement;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * Integration tests for CLI query commands.
 * <p>
 * Note: Output verification is not possible with MCP stdio extension active.
 * These tests verify exit codes and basic execution flow.
 * Output formatting is verified manually via uber-JAR testing.
 */
@QuarkusTest
class CliQueryTest {

  @Inject
  CliCommandHandler cliHandler;

  @Inject
  Instance<Connection> connection;

  @BeforeEach
  void setUp() throws Exception {
    // Create test table with data
    try (Connection conn = connection.get();
         Statement stmt = conn.createStatement()) {

      // Drop table if it exists (for test isolation)
      try {
        stmt.execute("DROP TABLE IF EXISTS cli_test_data");
      } catch (Exception e) {
        // Ignore if table doesn't exist
      }

      stmt.execute(
          "CREATE TABLE cli_test_data (" +
              "  id INTEGER PRIMARY KEY," +
              "  value TEXT" +
              ")"
      );

      // Insert test data
      for (int i = 1; i <= 250; i++) {
        stmt.execute(
            String.format("INSERT INTO cli_test_data (id, value) VALUES (%d, 'Value %d')", i, i)
        );
      }
    }
  }

  @AfterEach
  void tearDown() throws Exception {
    // Clean up test table
    try (Connection conn = connection.get();
         Statement stmt = conn.createStatement()) {
      stmt.execute("DROP TABLE IF EXISTS cli_test_data");
    } catch (Exception e) {
      // Ignore cleanup errors
    }
  }

  @Test
  void testCliQuerySelectSucceeds() {
    int exitCode = cliHandler.execute(new String[]{"query", "SELECT * FROM cli_test_data WHERE id <= 10"});
    assertThat("Should succeed with exit code 0", exitCode, is(0));
  }

  @Test
  void testCliQueryWithPaginationSucceeds() {
    int exitCode = cliHandler.execute(
        new String[]{"query", "SELECT * FROM cli_test_data ORDER BY id", "--page", "2"});
    assertThat("Should succeed with pagination", exitCode, is(0));
  }

  @Test
  void testCliQueryInsertSucceeds() {
    int exitCode = cliHandler.execute(
        new String[]{"query", "INSERT INTO cli_test_data (id, value) VALUES (999, 'Test')"});
    assertThat("Should succeed with INSERT", exitCode, is(0));
  }

  @Test
  void testCliQueryUpdateSucceeds() {
    int exitCode = cliHandler.execute(
        new String[]{"query", "UPDATE cli_test_data SET value = 'Updated' WHERE id = 1"});
    assertThat("Should succeed with UPDATE", exitCode, is(0));
  }

  @Test
  void testCliQueryDeleteSucceeds() {
    int exitCode = cliHandler.execute(
        new String[]{"query", "DELETE FROM cli_test_data WHERE id > 240"});
    assertThat("Should succeed with DELETE", exitCode, is(0));
  }

  @Test
  void testCliQueryDDLSucceeds() {
    int exitCode = cliHandler.execute(
        new String[]{"query", "CREATE TABLE temp_cli_test (id INTEGER)"});
    assertThat("Should succeed with DDL", exitCode, is(0));

    // Clean up
    cliHandler.execute(new String[]{"query", "DROP TABLE temp_cli_test"});
  }

  @Test
  void testCliQueryMissingQueryFails() {
    int exitCode = cliHandler.execute(new String[]{"query"});
    assertThat("Should fail when query is missing", exitCode, is(1));
  }

  @Test
  void testCliQueryInvalidPageNumberFails() {
    int exitCode = cliHandler.execute(
        new String[]{"query", "SELECT * FROM cli_test_data", "--page", "0"});
    assertThat("Should fail with invalid page number", exitCode, is(1));
  }

  @Test
  void testCliQueryNegativePageNumberFails() {
    int exitCode = cliHandler.execute(
        new String[]{"query", "SELECT * FROM cli_test_data", "--page", "-1"});
    assertThat("Should fail with negative page number", exitCode, is(1));
  }

  @Test
  void testCliQueryNonNumericPageFails() {
    int exitCode = cliHandler.execute(
        new String[]{"query", "SELECT * FROM cli_test_data", "--page", "abc"});
    assertThat("Should fail with non-numeric page", exitCode, is(1));
  }

  @Test
  void testCliQueryMissingPageValueFails() {
    int exitCode = cliHandler.execute(
        new String[]{"query", "SELECT * FROM cli_test_data", "--page"});
    assertThat("Should fail when --page value is missing", exitCode, is(1));
  }

  @Test
  void testCliQuerySqlErrorFails() {
    int exitCode = cliHandler.execute(
        new String[]{"query", "SELECT * FROM nonexistent_table"});
    assertThat("Should fail with SQL error", exitCode, is(1));
  }

  @Test
  void testCliQuerySyntaxErrorFails() {
    int exitCode = cliHandler.execute(
        new String[]{"query", "SELECT * FORM cli_test_data"});
    assertThat("Should fail with syntax error", exitCode, is(1));
  }

  @Test
  void testCliQueryComplexQuerySucceeds() {
    int exitCode = cliHandler.execute(
        new String[]{"query", "SELECT id, value FROM cli_test_data WHERE id > 10 ORDER BY id DESC"});
    assertThat("Should succeed with complex query", exitCode, is(0));
  }

  @Test
  void testCliQueryAggregationSucceeds() {
    int exitCode = cliHandler.execute(
        new String[]{"query", "SELECT COUNT(*) as total FROM cli_test_data"});
    assertThat("Should succeed with aggregation", exitCode, is(0));
  }

  @Test
  void testCliQueryEmptyResultSucceeds() {
    int exitCode = cliHandler.execute(
        new String[]{"query", "SELECT * FROM cli_test_data WHERE id > 10000"});
    assertThat("Should succeed with empty result", exitCode, is(0));
  }

  @Test
  void testCliUnknownCommandFails() {
    int exitCode = cliHandler.execute(new String[]{"unknown"});
    assertThat("Should fail with unknown command", exitCode, is(1));
  }

  @Test
  void testCliNoArgumentsFails() {
    int exitCode = cliHandler.execute(new String[]{});
    assertThat("Should fail with no arguments", exitCode, is(1));
  }
}
