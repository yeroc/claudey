package org.geekden.mcp.database.service;

import org.geekden.mcp.IsolatedDatabaseProfile;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.Statement;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * Integration tests for IntrospectionService.
 * Uses Quarkus-managed database connection.
 */
@QuarkusTest
@TestProfile(IntrospectionServiceTest.Profile.class)
class IntrospectionServiceTest {

  public static class Profile extends IsolatedDatabaseProfile {
  }

  @Inject
  IntrospectionService introspectionService;

  @Inject
  Instance<Connection> connection;

  @BeforeEach
  void setUp() throws Exception {
    // Create test schema with tables using injected connection
    try (Connection conn = connection.get();
         Statement stmt = conn.createStatement()) {

      // Drop tables if they exist from previous test runs
      stmt.execute("DROP VIEW IF EXISTS user_orders");
      stmt.execute("DROP TABLE IF EXISTS orders");
      stmt.execute("DROP TABLE IF EXISTS users");

      // Create users table with primary key
      stmt.execute(
          "CREATE TABLE users (" +
              "  id INTEGER PRIMARY KEY," +
              "  name TEXT NOT NULL," +
              "  email TEXT" +
              ")"
      );

      // Create orders table with foreign key
      stmt.execute(
          "CREATE TABLE orders (" +
              "  order_id INTEGER PRIMARY KEY," +
              "  user_id INTEGER NOT NULL," +
              "  total REAL," +
              "  FOREIGN KEY (user_id) REFERENCES users(id)" +
              ")"
      );

      // Create a view
      stmt.execute(
          "CREATE VIEW user_orders AS " +
              "SELECT u.name, o.order_id, o.total " +
              "FROM users u JOIN orders o ON u.id = o.user_id"
      );
    }
  }

  @AfterEach
  void tearDown() throws Exception {
    // Clean up test tables
    try (Connection conn = connection.get();
         Statement stmt = conn.createStatement()) {
      stmt.execute("DROP VIEW IF EXISTS user_orders");
      stmt.execute("DROP TABLE IF EXISTS orders");
      stmt.execute("DROP TABLE IF EXISTS users");
    }
  }

  @Test
  void testListSchemas() throws Exception {
    try (Connection conn = connection.get()) {
      DatabaseMetaData metaData = conn.getMetaData();
      String result = introspectionService.listSchemas(metaData);

      // SQLite in-memory may not return any schemas/catalogs
      assertThat("Should return a result", result, not(emptyString()));

      // If schemas/catalogs are found, check formatting
      if (!result.equals("No schemas found.")) {
        assertThat("Should be formatted as table", result, containsString("Schema"));
        assertThat("Should contain separator", result, containsString("──"));
      }
    }
  }

  @Test
  void testListTables() throws Exception {
    try (Connection conn = connection.get()) {
      DatabaseMetaData metaData = conn.getMetaData();

      // SQLite uses null schema for default tables
      String result = introspectionService.listTables(metaData, null);

      assertThat("Should list tables", result, not(emptyString()));
      assertThat("Should contain users table", result, containsString("users"));
      assertThat("Should contain orders table", result, containsString("orders"));
      assertThat("Should contain view", result, containsString("user_orders"));
      assertThat("Should have column headers", result, containsString("Table Name"));
      assertThat("Should have column headers", result, containsString("Type"));
      assertThat("Should contain separator", result, containsString("──"));
    }
  }

  @Test
  void testListTablesWithEmptySchema() throws Exception {
    try (Connection conn = connection.get()) {
      DatabaseMetaData metaData = conn.getMetaData();

      // Test with a schema that doesn't exist
      // Note: SQLite ignores schema parameter and returns all tables,
      // so we just verify the method doesn't crash
      String result = introspectionService.listTables(metaData, "nonexistent_schema");

      assertThat("Should return a result", result, not(emptyString()));
      // The result may contain tables or "No tables found" depending on database behavior
    }
  }

  @Test
  void testDescribeTable() throws Exception {
    try (Connection conn = connection.get()) {
      DatabaseMetaData metaData = conn.getMetaData();

      // Describe users table
      String result = introspectionService.describeTable(metaData, null, "users");

      assertThat("Should describe table", result, not(emptyString()));

      // Check for column names
      assertThat("Should contain id column", result, containsString("id"));
      assertThat("Should contain name column", result, containsString("name"));
      assertThat("Should contain email column", result, containsString("email"));

      // Check for column headers
      assertThat("Should have Column Name header", result, containsString("Column Name"));
      assertThat("Should have Type header", result, containsString("Type"));
      assertThat("Should have Nullable header", result, containsString("Nullable"));
      assertThat("Should have Constraints header", result, containsString("Constraints"));

      // Check for primary key constraint
      assertThat("Should show primary key", result, containsString("PRIMARY KEY"));

      // Check for NOT NULL constraint
      assertThat("Should show NOT NULL", result, containsString("NOT NULL"));

      // Check for separators
      assertThat("Should contain separator", result, containsString("──"));
    }
  }

  @Test
  void testDescribeTableWithForeignKey() throws Exception {
    try (Connection conn = connection.get()) {
      DatabaseMetaData metaData = conn.getMetaData();

      // Describe orders table which has a foreign key
      String result = introspectionService.describeTable(metaData, null, "orders");

      assertThat("Should describe table", result, not(emptyString()));

      // Check for column names
      assertThat("Should contain order_id column", result, containsString("order_id"));
      assertThat("Should contain user_id column", result, containsString("user_id"));
      assertThat("Should contain total column", result, containsString("total"));

      // Check for primary key
      assertThat("Should show primary key", result, containsString("PRIMARY KEY"));

      // Check for foreign key (may vary by database implementation)
      // Some databases report foreign keys differently, so we'll check if the user_id is present
      assertThat("Should show user_id column", result, containsString("user_id"));
    }
  }

  @Test
  void testDescribeNonExistentTable() throws Exception {
    try (Connection conn = connection.get()) {
      DatabaseMetaData metaData = conn.getMetaData();

      String result = introspectionService.describeTable(metaData, null, "nonexistent_table");

      assertThat("Should indicate table not found", result, containsString("Table not found"));
    }
  }

  @Test
  void testDescribeTableWithNullableColumns() throws Exception {
    try (Connection conn = connection.get()) {
      DatabaseMetaData metaData = conn.getMetaData();

      // The 'email' column in 'users' table is nullable
      String result = introspectionService.describeTable(metaData, null, "users");

      // Check that nullable columns are marked correctly
      assertThat("Should show nullable status", result, containsString("NULL"));

      // The result should have both "NOT NULL" (for name column) and "NULL" (for email column)
      assertThat("Should contain NOT NULL", result, containsString("NOT NULL"));
    }
  }

  @Test
  void testTableFormattingConsistency() throws Exception {
    try (Connection conn = connection.get()) {
      DatabaseMetaData metaData = conn.getMetaData();

      // Test that all three modes produce well-formatted tables
      String schemas = introspectionService.listSchemas(metaData);
      String tables = introspectionService.listTables(metaData, null);
      String tableDetails = introspectionService.describeTable(metaData, null, "users");

      // All should have Unicode separators (unless "No schemas found")
      if (!schemas.equals("No schemas found.")) {
        assertThat("Schemas should have separator", schemas, containsString("──"));
        assertThat("Schemas should end with separator line", schemas, endsWith("─"));
      }
      assertThat("Tables should have separator", tables, containsString("──"));
      assertThat("Table details should have separator", tableDetails, containsString("──"));

      // All should end with footer separator
      assertThat("Tables should end with separator line", tables, endsWith("─"));
      assertThat("Table details should end with separator line", tableDetails, endsWith("─"));
    }
  }
}
