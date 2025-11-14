package org.geekden.mcp.service;

import org.geekden.mcp.AbstractDatabaseIntegrationTest;

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
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Integration tests for SqlExecutionService.
 * Uses Quarkus-managed database connection.
 */
@QuarkusTest
class SqlExecutionServiceTest extends AbstractDatabaseIntegrationTest {

  @Inject
  SqlExecutionService sqlExecutionService;

  @Inject
  Instance<Connection> connection;

  @BeforeEach
  void setUp() throws Exception {
    // Create test table with data using injected connection
    try (Connection conn = connection.get();
         Statement stmt = conn.createStatement()) {

      // Drop table if it exists from previous test runs
      stmt.execute("DROP TABLE IF EXISTS products");

      stmt.execute(
          "CREATE TABLE products (" +
              "  id INTEGER PRIMARY KEY," +
              "  name TEXT NOT NULL," +
              "  price REAL" +
              ")"
      );

      // Insert test data
      for (int i = 1; i <= 250; i++) {
        stmt.execute(
            String.format("INSERT INTO products (id, name, price) VALUES (%d, 'Product %d', %d.99)",
                i, i, i)
        );
      }
    }
  }

  @AfterEach
  void tearDown() throws Exception {
    // Clean up test table
    try (Connection conn = connection.get();
         Statement stmt = conn.createStatement()) {
      stmt.execute("DROP TABLE IF EXISTS products");
    }
  }

  @Test
  void testExecuteSelectQuery_firstPage() throws Exception {
    String query = "SELECT * FROM products ORDER BY id";
    String result = sqlExecutionService.executeQuery(connection.get(), query, 1, 100);

    assertThat("Should contain headers",
        result, allOf(containsString("id"), containsString("name"), containsString("price")));

    assertThat("Should contain first row",
        result, containsString("Product 1"));

    assertThat("Should show pagination footer with more available",
        result, containsString("Page 1 (more available)"));
  }

  @Test
  void testExecuteSelectQuery_secondPage() throws Exception {
    String query = "SELECT * FROM products ORDER BY id";
    String result = sqlExecutionService.executeQuery(connection.get(), query, 2, 100);

    assertThat("Should contain data from second page",
        result, containsString("Product 101"));

    assertThat("Should show pagination footer",
        result, containsString("Page 2 (more available)"));
  }

  @Test
  void testExecuteSelectQuery_lastPage() throws Exception {
    String query = "SELECT * FROM products ORDER BY id";
    String result = sqlExecutionService.executeQuery(connection.get(), query, 3, 100);

    assertThat("Should contain data from last page",
        result, containsString("Product 201"));

    assertThat("Should show final page footer",
        result, containsString("Page 3 (no more data)"));
  }

  @Test
  void testExecuteSelectQuery_customPageSize() throws Exception {
    String query = "SELECT * FROM products ORDER BY id";
    String result = sqlExecutionService.executeQuery(connection.get(), query, 1, 50);

    assertThat("Should contain first row",
        result, containsString("Product 1"));

    assertThat("Should show correct page with custom page size",
        result, containsString("Page 1 (more available)"));
  }

  @Test
  void testExecuteSelectQuery_emptyResult() throws Exception {
    String query = "SELECT * FROM products WHERE id > 1000";
    String result = sqlExecutionService.executeQuery(connection.get(), query, 1, 100);

    assertThat("Should indicate no results",
        result, is("No results."));
  }

  @Test
  void testExecuteSelectQuery_nonPageable() throws Exception {
    // Query with existing LIMIT should not be paginated
    String query = "SELECT * FROM products LIMIT 5";
    String result = sqlExecutionService.executeQuery(connection.get(), query, 1, 100);

    assertThat("Should contain data",
        result, containsString("Product 1"));

    assertThat("Should not show pagination footer",
        result, not(containsString("Page 1")));
  }

  @Test
  void testExecuteInsertQuery() throws Exception {
    String query = "INSERT INTO products (id, name, price) VALUES (999, 'New Product', 99.99)";
    String result = sqlExecutionService.executeQuery(connection.get(), query, 1, 100);

    assertThat("Should show affected row count",
        result, is("1 row affected."));

    // Verify data was inserted
    String selectQuery = "SELECT * FROM products WHERE id = 999";
    String selectResult = sqlExecutionService.executeQuery(connection.get(), selectQuery, 1, 100);
    assertThat("Should find inserted row",
        selectResult, containsString("New Product"));
  }

  @Test
  void testExecuteUpdateQuery() throws Exception {
    String query = "UPDATE products SET price = 199.99 WHERE id <= 10";
    String result = sqlExecutionService.executeQuery(connection.get(), query, 1, 100);

    assertThat("Should show affected row count",
        result, is("10 rows affected."));

    // Verify data was updated
    String selectQuery = "SELECT price FROM products WHERE id = 1";
    String selectResult = sqlExecutionService.executeQuery(connection.get(), selectQuery, 1, 100);
    assertThat("Should show updated price",
        selectResult, containsString("199.99"));
  }

  @Test
  void testExecuteDeleteQuery() throws Exception {
    String query = "DELETE FROM products WHERE id > 240";
    String result = sqlExecutionService.executeQuery(connection.get(), query, 1, 100);

    assertThat("Should show affected row count",
        result, is("10 rows affected."));

    // Verify data was deleted
    String selectQuery = "SELECT COUNT(*) as count FROM products";
    String selectResult = sqlExecutionService.executeQuery(connection.get(), selectQuery, 1, 100);
    assertThat("Should show reduced count",
        selectResult, containsString("240"));
  }

  @Test
  void testExecuteDeleteQuery_noRowsAffected() throws Exception {
    String query = "DELETE FROM products WHERE id > 10000";
    String result = sqlExecutionService.executeQuery(connection.get(), query, 1, 100);

    assertThat("Should indicate no rows affected",
        result, is("No rows affected."));
  }

  @Test
  void testExecuteDDL_createTable() throws Exception {
    // Clean up table if it exists from previous test run
    try (Statement stmt = connection.get().createStatement()) {
      stmt.execute("DROP TABLE IF EXISTS test_table");
    }

    String query = "CREATE TABLE test_table (id INTEGER PRIMARY KEY, value TEXT)";
    String result = sqlExecutionService.executeQuery(connection.get(), query, 1, 100);

    assertThat("Should show success message",
        result, is("Command executed successfully."));

    // Verify table was created
    String selectQuery = "SELECT name FROM sqlite_master WHERE type='table' AND name='test_table'";
    String selectResult = sqlExecutionService.executeQuery(connection.get(), selectQuery, 1, 100);
    assertThat("Should find created table",
        selectResult, containsString("test_table"));

    // Clean up
    try (Statement stmt = connection.get().createStatement()) {
      stmt.execute("DROP TABLE IF EXISTS test_table");
    }
  }

  @Test
  void testExecuteDDL_dropTable() throws Exception {
    // Create a table first
    try (Statement stmt = connection.get().createStatement()) {
      stmt.execute("CREATE TABLE temp_table (id INTEGER)");
    }

    String query = "DROP TABLE temp_table";
    String result = sqlExecutionService.executeQuery(connection.get(), query, 1, 100);

    assertThat("Should show success message",
        result, is("Command executed successfully."));
  }

  @Test
  void testExecuteQuery_nullQuery() {
    Exception exception = assertThrows(IllegalArgumentException.class, () -> {
      sqlExecutionService.executeQuery(connection.get(), null, 1, 100);
    });

    assertThat("Should throw exception for null query",
        exception.getMessage(), containsString("Query cannot be empty"));
  }

  @Test
  void testExecuteQuery_emptyQuery() {
    Exception exception = assertThrows(IllegalArgumentException.class, () -> {
      sqlExecutionService.executeQuery(connection.get(), "", 1, 100);
    });

    assertThat("Should throw exception for empty query",
        exception.getMessage(), containsString("Query cannot be empty"));
  }

  @Test
  void testExecuteQuery_whitespaceQuery() {
    Exception exception = assertThrows(IllegalArgumentException.class, () -> {
      sqlExecutionService.executeQuery(connection.get(), "   ", 1, 100);
    });

    assertThat("Should throw exception for whitespace-only query",
        exception.getMessage(), containsString("Query cannot be empty"));
  }

  @Test
  void testExecuteQuery_invalidPageNumber() {
    Exception exception = assertThrows(IllegalArgumentException.class, () -> {
      sqlExecutionService.executeQuery(connection.get(), "SELECT * FROM products", 0, 100);
    });

    assertThat("Should throw exception for page 0",
        exception.getMessage(), containsString("Page number must be >= 1"));
  }

  @Test
  void testExecuteQuery_negativePageNumber() {
    Exception exception = assertThrows(IllegalArgumentException.class, () -> {
      sqlExecutionService.executeQuery(connection.get(), "SELECT * FROM products", -1, 100);
    });

    assertThat("Should throw exception for negative page",
        exception.getMessage(), containsString("Page number must be >= 1"));
  }

  @Test
  void testExecuteQuery_invalidPageSize() {
    Exception exception = assertThrows(IllegalArgumentException.class, () -> {
      sqlExecutionService.executeQuery(connection.get(), "SELECT * FROM products", 1, 0);
    });

    assertThat("Should throw exception for page size 0",
        exception.getMessage(), containsString("Page size must be >= 1"));
  }

  @Test
  void testExecuteQuery_selectWithNulls() throws Exception {
    // Insert a row with null
    try (Statement stmt = connection.get().createStatement()) {
      stmt.execute("INSERT INTO products (id, name, price) VALUES (1000, 'No Price', NULL)");
    }

    String query = "SELECT * FROM products WHERE id = 1000";
    String result = sqlExecutionService.executeQuery(connection.get(), query, 1, 100);

    assertThat("Should show null placeholder",
        result, containsString("<null>"));
  }

  @Test
  void testExecuteQuery_defaultPageParameter() throws Exception {
    String query = "SELECT * FROM products ORDER BY id";
    String result = sqlExecutionService.executeQuery(connection.get(), query, 100);

    assertThat("Should default to page 1",
        result, containsString("Product 1"));

    assertThat("Should show pagination footer",
        result, containsString("Page 1 (more available)"));
  }
}
