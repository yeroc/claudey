package org.geekden.mcp;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * Integration tests for DatabaseMcpTools SQL execution functionality.
 * Tests the executeSql MCP tool with actual database connection.
 */
@QuarkusTest
class DatabaseMcpToolsExecuteSqlTest {

  @Inject
  DatabaseMcpTools mcpTools;

  @BeforeEach
  void setUp() {
    // Create test table with data using MCP tools (ensures same connection context)
    mcpTools.executeSql("DROP TABLE IF EXISTS test_products", 1);

    mcpTools.executeSql(
        "CREATE TABLE test_products (" +
            "  id INTEGER PRIMARY KEY," +
            "  name TEXT NOT NULL," +
            "  price REAL" +
            ")", 1);

    // Insert test data for pagination tests
    for (int i = 1; i <= 150; i++) {
      mcpTools.executeSql(
          String.format("INSERT INTO test_products (id, name, price) VALUES (%d, 'Product %d', %d.99)",
              i, i, i), 1);
    }
  }

  @AfterEach
  void tearDown() {
    // Clean up test table
    mcpTools.executeSql("DROP TABLE IF EXISTS test_products", 1);
  }

  @Test
  void testExecuteSelectQuery() {
    String result = mcpTools.executeSql("SELECT * FROM test_products WHERE id <= 5 ORDER BY id", 1);

    assertThat("Should contain column headers",
        result, allOf(containsString("id"), containsString("name"), containsString("price")));

    assertThat("Should contain data",
        result, allOf(containsString("Product 1"), containsString("Product 2")));

    assertThat("Should have table separators",
        result, containsString("──"));
  }

  @Test
  void testExecuteSelectQuery_pagination() {
    String result = mcpTools.executeSql("SELECT * FROM test_products ORDER BY id", 1);

    assertThat("Should contain data from first page",
        result, containsString("Product 1"));

    assertThat("Should show pagination footer",
        result, containsString("Page 1 (more available)"));
  }

  @Test
  void testExecuteSelectQuery_secondPage() {
    String result = mcpTools.executeSql("SELECT * FROM test_products ORDER BY id", 2);

    assertThat("Should contain data from second page",
        result, containsString("Product 101"));

    assertThat("Should show pagination footer",
        result, containsString("Page 2"));
  }

  @Test
  void testExecuteSelectQuery_emptyResult() {
    String result = mcpTools.executeSql("SELECT * FROM test_products WHERE id > 10000", 1);

    assertThat("Should indicate no results",
        result, is("No results."));
  }

  @Test
  void testExecuteInsertQuery() {
    String result = mcpTools.executeSql(
        "INSERT INTO test_products (id, name, price) VALUES (999, 'New Product', 99.99)", 1);

    assertThat("Should show affected row count",
        result, is("1 row affected."));

    // Verify data was inserted
    String selectResult = mcpTools.executeSql("SELECT * FROM test_products WHERE id = 999", 1);
    assertThat("Should find inserted row",
        selectResult, containsString("New Product"));
  }

  @Test
  void testExecuteUpdateQuery() {
    String result = mcpTools.executeSql(
        "UPDATE test_products SET price = 199.99 WHERE id <= 5", 1);

    assertThat("Should show affected row count",
        result, is("5 rows affected."));

    // Verify data was updated
    String selectResult = mcpTools.executeSql("SELECT price FROM test_products WHERE id = 1", 1);
    assertThat("Should show updated price",
        selectResult, containsString("199.99"));
  }

  @Test
  void testExecuteDeleteQuery() {
    String result = mcpTools.executeSql(
        "DELETE FROM test_products WHERE id > 140", 1);

    assertThat("Should show affected row count",
        result, is("10 rows affected."));
  }

  @Test
  void testExecuteDeleteQuery_noRowsAffected() {
    String result = mcpTools.executeSql(
        "DELETE FROM test_products WHERE id > 10000", 1);

    assertThat("Should indicate no rows affected",
        result, is("No rows affected."));
  }

  @Test
  void testExecuteDDL_createTable() {
    String result = mcpTools.executeSql(
        "CREATE TABLE test_temp (id INTEGER PRIMARY KEY, value TEXT)", 1);

    assertThat("Should show success message",
        result, is("Command executed successfully."));

    // Clean up
    mcpTools.executeSql("DROP TABLE test_temp", 1);
  }

  @Test
  void testExecuteDDL_dropTable() {
    // Create a table first
    mcpTools.executeSql("CREATE TABLE test_drop (id INTEGER)", 1);

    String result = mcpTools.executeSql("DROP TABLE test_drop", 1);

    assertThat("Should show success message",
        result, is("Command executed successfully."));
  }

  @Test
  void testExecuteQuery_invalidPageNumber() {
    String result = mcpTools.executeSql("SELECT * FROM test_products", 0);

    assertThat("Should return error for invalid page number",
        result, containsString("Error: Page number must be >= 1"));
  }

  @Test
  void testExecuteQuery_withNulls() {
    // Insert a row with null
    mcpTools.executeSql("INSERT INTO test_products (id, name, price) VALUES (1000, 'No Price', NULL)", 1);

    String result = mcpTools.executeSql("SELECT * FROM test_products WHERE id = 1000", 1);

    assertThat("Should show null placeholder",
        result, containsString("<null>"));
  }

  @Test
  void testExecuteQuery_sqlError() {
    String result = mcpTools.executeSql("SELECT * FROM nonexistent_table", 1);

    assertThat("Should return error message",
        result, containsString("Error:"));
  }

  @Test
  void testExecuteQuery_syntaxError() {
    String result = mcpTools.executeSql("SELECT * FORM test_products", 1);

    assertThat("Should return error message for syntax error",
        result, containsString("Error:"));
  }

  @Test
  void testExecuteSelectQuery_complexQuery() {
    String result = mcpTools.executeSql(
        "SELECT id, name, ROUND(price, 2) as rounded_price FROM test_products WHERE price > 50.00 ORDER BY price DESC", 1);

    assertThat("Should execute complex query",
        result, allOf(
            containsString("id"),
            containsString("name"),
            containsString("rounded_price")
        ));
  }

  @Test
  void testExecuteSelectQuery_withAggregation() {
    String result = mcpTools.executeSql(
        "SELECT COUNT(*) as total_count FROM test_products", 1);

    assertThat("Should execute aggregation query",
        result, allOf(
            containsString("total_count"),
            containsString("150")
        ));
  }

  @Test
  void testExecuteQuery_pagination_lastPage() {
    String result = mcpTools.executeSql("SELECT * FROM test_products ORDER BY id", 2);

    assertThat("Should show final page footer",
        result, containsString("Page 2 (no more data)"));
  }
}
