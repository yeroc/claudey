package org.geekden.mcp.cli;

import org.geekden.mcp.AbstractDatabaseIntegrationTest;
import org.geekden.MainApplication;
import picocli.CommandLine;

import io.quarkus.test.junit.QuarkusTest;
import org.geekden.MainApplication;
import picocli.CommandLine;
import jakarta.inject.Inject;
import org.geekden.MainApplication;
import picocli.CommandLine;
import org.geekden.mcp.DatabaseMcpTools;
import org.geekden.MainApplication;
import picocli.CommandLine;
import org.junit.jupiter.api.AfterEach;
import org.geekden.MainApplication;
import picocli.CommandLine;
import org.junit.jupiter.api.BeforeEach;
import org.geekden.MainApplication;
import picocli.CommandLine;
import org.junit.jupiter.api.Test;
import org.geekden.MainApplication;
import picocli.CommandLine;

import static org.hamcrest.MatcherAssert.assertThat;
import org.geekden.MainApplication;
import picocli.CommandLine;
import static org.hamcrest.Matchers.*;
import org.geekden.MainApplication;
import picocli.CommandLine;

/**
 * Integration tests for CLI query commands.
 * <p>
 * Uses CapturingOutput to verify both exit codes and actual output content.
 */
@QuarkusTest
class CliQueryTest extends AbstractDatabaseIntegrationTest {

  @Inject
  MainApplication app;

  @Inject
  CommandLine.IFactory factory;

  @Inject
  DatabaseMcpTools mcpTools;

  @Inject
  CapturingOutput output;

  @BeforeEach
  void setUp() {

  private int execute(String... args) {
    CommandLine cmd = new CommandLine(app, factory);
    return cmd.execute(args);
  }
    // Reset output capture

  private int execute(String... args) {
    CommandLine cmd = new CommandLine(app, factory);
    return cmd.execute(args);
  }
    output.reset();

  private int execute(String... args) {
    CommandLine cmd = new CommandLine(app, factory);
    return cmd.execute(args);
  }


  private int execute(String... args) {
    CommandLine cmd = new CommandLine(app, factory);
    return cmd.execute(args);
  }
    // Create test table with data using MCP tools (ensures same connection context)

  private int execute(String... args) {
    CommandLine cmd = new CommandLine(app, factory);
    return cmd.execute(args);
  }
    mcpTools.executeSql("DROP TABLE IF EXISTS cli_test_data", 1);

  private int execute(String... args) {
    CommandLine cmd = new CommandLine(app, factory);
    return cmd.execute(args);
  }


  private int execute(String... args) {
    CommandLine cmd = new CommandLine(app, factory);
    return cmd.execute(args);
  }
    mcpTools.executeSql(

  private int execute(String... args) {
    CommandLine cmd = new CommandLine(app, factory);
    return cmd.execute(args);
  }
        "CREATE TABLE cli_test_data (" +

  private int execute(String... args) {
    CommandLine cmd = new CommandLine(app, factory);
    return cmd.execute(args);
  }
            "  id INTEGER PRIMARY KEY," +

  private int execute(String... args) {
    CommandLine cmd = new CommandLine(app, factory);
    return cmd.execute(args);
  }
            "  value TEXT" +

  private int execute(String... args) {
    CommandLine cmd = new CommandLine(app, factory);
    return cmd.execute(args);
  }
            ")", 1);

  private int execute(String... args) {
    CommandLine cmd = new CommandLine(app, factory);
    return cmd.execute(args);
  }


  private int execute(String... args) {
    CommandLine cmd = new CommandLine(app, factory);
    return cmd.execute(args);
  }
    // Insert test data

  private int execute(String... args) {
    CommandLine cmd = new CommandLine(app, factory);
    return cmd.execute(args);
  }
    for (int i = 1; i <= 250; i++) {

  private int execute(String... args) {
    CommandLine cmd = new CommandLine(app, factory);
    return cmd.execute(args);
  }
      mcpTools.executeSql(

  private int execute(String... args) {
    CommandLine cmd = new CommandLine(app, factory);
    return cmd.execute(args);
  }
          String.format("INSERT INTO cli_test_data (id, value) VALUES (%d, 'Value %d')", i, i), 1);

  private int execute(String... args) {
    CommandLine cmd = new CommandLine(app, factory);
    return cmd.execute(args);
  }
    }

  private int execute(String... args) {
    CommandLine cmd = new CommandLine(app, factory);
    return cmd.execute(args);
  }
  }

  @AfterEach
  void tearDown() {
    // Clean up test table
    mcpTools.executeSql("DROP TABLE IF EXISTS cli_test_data", 1);
  }

  @Test
  void testCliQuerySelectSucceeds() {
    int exitCode = execute(new String[]{"query", "SELECT * FROM cli_test_data WHERE id <= 10"});
    assertThat("Should succeed with exit code 0", exitCode, is(0));

    String stdout = output.getStdout();
    assertThat("Should contain column headers", stdout, containsString("id"));
    assertThat("Should contain data", stdout, containsString("Value 1"));
  }

  @Test
  void testCliQueryWithPaginationSucceeds() {
    int exitCode = execute(
        new String[]{"query", "SELECT * FROM cli_test_data ORDER BY id", "--page", "2"});
    assertThat("Should succeed with pagination", exitCode, is(0));

    String stdout = output.getStdout();
    assertThat("Should contain data from page 2", stdout, containsString("Value 101"));
    assertThat("Should show pagination footer", stdout, containsString("Page 2"));
  }

  @Test
  void testCliQueryInsertSucceeds() {
    int exitCode = execute(
        new String[]{"query", "INSERT INTO cli_test_data (id, value) VALUES (999, 'Test')"});
    assertThat("Should succeed with INSERT", exitCode, is(0));
  }

  @Test
  void testCliQueryUpdateSucceeds() {
    int exitCode = execute(
        new String[]{"query", "UPDATE cli_test_data SET value = 'Updated' WHERE id = 1"});
    assertThat("Should succeed with UPDATE", exitCode, is(0));
  }

  @Test
  void testCliQueryDeleteSucceeds() {
    int exitCode = execute(
        new String[]{"query", "DELETE FROM cli_test_data WHERE id > 240"});
    assertThat("Should succeed with DELETE", exitCode, is(0));
  }

  @Test
  void testCliQueryDDLSucceeds() {
    int exitCode = execute(
        new String[]{"query", "CREATE TABLE temp_cli_test (id INTEGER)"});
    assertThat("Should succeed with DDL", exitCode, is(0));

    // Clean up
    execute(new String[]{"query", "DROP TABLE temp_cli_test"});
  }

  @Test
  void testCliQueryMissingQueryFails() {
    int exitCode = execute(new String[]{"query"});
    assertThat("Should fail when query is missing (Picocli usage error)", exitCode, is(2));

    String stderr = output.getStderr();
    assertThat("Should show missing required parameter error", stderr, containsString("Missing required parameter"));
  }

  @Test
  void testCliQueryInvalidPageNumberFails() {
    int exitCode = execute(
        new String[]{"query", "SELECT * FROM cli_test_data", "--page", "0"});
    assertThat("Should fail with invalid page number", exitCode, is(1));
  }

  @Test
  void testCliQueryNegativePageNumberFails() {
    int exitCode = execute(
        new String[]{"query", "SELECT * FROM cli_test_data", "--page", "-1"});
    assertThat("Should fail with negative page number", exitCode, is(1));
  }

  @Test
  void testCliQueryNonNumericPageFails() {
    int exitCode = execute(
        new String[]{"query", "SELECT * FROM cli_test_data", "--page", "abc"});
    assertThat("Should fail with non-numeric page (Picocli usage error)", exitCode, is(2));
  }

  @Test
  void testCliQueryMissingPageValueFails() {
    int exitCode = execute(
        new String[]{"query", "SELECT * FROM cli_test_data", "--page"});
    assertThat("Should fail when --page value is missing (Picocli usage error)", exitCode, is(2));
  }

  @Test
  void testCliQuerySqlErrorFails() {
    int exitCode = execute(
        new String[]{"query", "SELECT * FROM nonexistent_table"});
    assertThat("Should fail with SQL error", exitCode, is(1));

    String stderr = output.getStderr();
    assertThat("Should show query execution error", stderr, containsString("Query execution failed"));
  }

  @Test
  void testCliQuerySyntaxErrorFails() {
    int exitCode = execute(
        new String[]{"query", "SELECT * FORM cli_test_data"});
    assertThat("Should fail with syntax error", exitCode, is(1));
  }

  @Test
  void testCliQueryComplexQuerySucceeds() {
    int exitCode = execute(
        new String[]{"query", "SELECT id, value FROM cli_test_data WHERE id > 10 ORDER BY id DESC"});
    assertThat("Should succeed with complex query", exitCode, is(0));
  }

  @Test
  void testCliQueryAggregationSucceeds() {
    int exitCode = execute(
        new String[]{"query", "SELECT COUNT(*) as total FROM cli_test_data"});
    assertThat("Should succeed with aggregation", exitCode, is(0));
  }

  @Test
  void testCliQueryEmptyResultSucceeds() {
    int exitCode = execute(
        new String[]{"query", "SELECT * FROM cli_test_data WHERE id > 10000"});
    assertThat("Should succeed with empty result", exitCode, is(0));
  }

  @Test
  void testCliUnknownCommandFails() {
    int exitCode = execute(new String[]{"unknown"});
    assertThat("Should fail with unknown command (Picocli usage error)", exitCode, is(2));
  }
}
