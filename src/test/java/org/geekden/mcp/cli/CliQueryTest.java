package org.geekden.mcp.cli;

import org.geekden.mcp.AbstractDatabaseIntegrationTest;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.geekden.mcp.DatabaseMcpTools;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * Integration tests for CLI query commands.
 * <p>
 * Uses CapturingOutput to verify both exit codes and actual output content.
 */
@QuarkusTest
class CliQueryTest extends AbstractDatabaseIntegrationTest {

  @Inject
  CliCommandHandler cliHandler;

  @Inject
  DatabaseMcpTools mcpTools;

  @Inject
  CapturingOutput output;

  @BeforeEach
  void setUp() {
    // Reset output capture
    output.reset();

    // Create test table with data using MCP tools (ensures same connection context)
    mcpTools.executeSql("DROP TABLE IF EXISTS cli_test_data", 1);

    mcpTools.executeSql(
        "CREATE TABLE cli_test_data (" +
            "  id INTEGER PRIMARY KEY," +
            "  value TEXT" +
            ")", 1);

    // Insert test data
    for (int i = 1; i <= 250; i++) {
      mcpTools.executeSql(
          String.format("INSERT INTO cli_test_data (id, value) VALUES (%d, 'Value %d')", i, i), 1);
    }
  }

  @AfterEach
  void tearDown() {
    // Clean up test table
    mcpTools.executeSql("DROP TABLE IF EXISTS cli_test_data", 1);
  }

  @Test
  void testCliQuerySelectSucceeds() {
    int exitCode = cliHandler.execute(new String[]{"query", "SELECT * FROM cli_test_data WHERE id <= 10"});
    assertThat("Should succeed with exit code 0", exitCode, is(0));

    String stdout = output.getStdout();
    assertThat("Should contain column headers", stdout, containsString("id"));
    assertThat("Should contain data", stdout, containsString("Value 1"));
  }

  @Test
  void testCliQueryWithPaginationSucceeds() {
    int exitCode = cliHandler.execute(
        new String[]{"query", "SELECT * FROM cli_test_data ORDER BY id", "--page", "2"});
    assertThat("Should succeed with pagination", exitCode, is(0));

    String stdout = output.getStdout();
    assertThat("Should contain data from page 2", stdout, containsString("Value 101"));
    assertThat("Should show pagination footer", stdout, containsString("Page 2"));
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

    String stderr = output.getStderr();
    assertThat("Should show error message", stderr, containsString("Invalid arguments"));
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

    String stderr = output.getStderr();
    assertThat("Should show error message", stderr, containsString("Error:"));
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
