package org.geekden.mcp.formatter;

import org.geekden.mcp.AbstractDatabaseIntegrationTest;
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
import java.sql.ResultSet;
import java.sql.Statement;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * Integration tests for ResultSetFormatter.
 * Uses Quarkus-managed database connection to create real ResultSets.
 */
@QuarkusTest
@TestProfile(ResultSetFormatterTest.Profile.class)
class ResultSetFormatterTest extends AbstractDatabaseIntegrationTest {

  public static class Profile extends IsolatedDatabaseProfile {
  }

  @Inject
  Instance<Connection> connection;

  @BeforeEach
  void setUp() throws Exception {
    // Create test table with data using injected connection
    try (Connection conn = connection.get();
         Statement stmt = conn.createStatement()) {

      // Drop table if it exists from previous test runs
      stmt.execute("DROP TABLE IF EXISTS users");

      stmt.execute(
          "CREATE TABLE users (" +
              "  id INTEGER PRIMARY KEY," +
              "  name TEXT NOT NULL," +
              "  email TEXT" +
              ")"
      );

      stmt.execute("INSERT INTO users (id, name, email) VALUES (1, 'Alice', 'alice@example.com')");
      stmt.execute("INSERT INTO users (id, name, email) VALUES (2, 'Bob', 'bob@example.com')");
      stmt.execute("INSERT INTO users (id, name, email) VALUES (3, 'Charlie', NULL)");
    }
  }

  @AfterEach
  void tearDown() throws Exception {
    // Clean up test table
    try (Connection conn = connection.get();
         Statement stmt = conn.createStatement()) {
      stmt.execute("DROP TABLE IF EXISTS users");
    }
  }

  @Test
  void testFormatSimpleResultSet() throws Exception {
    try (Connection conn = connection.get();
         Statement stmt = conn.createStatement();
         ResultSet rs = stmt.executeQuery("SELECT id, name FROM users ORDER BY id")) {

      String result = ResultSetFormatter.format(rs);

      assertThat("Should contain column headers",
          result, containsString("id"));
      assertThat("Should contain column headers",
          result, containsString("name"));

      assertThat("Should contain data",
          result, containsString("Alice"));
      assertThat("Should contain data",
          result, containsString("Bob"));
      assertThat("Should contain data",
          result, containsString("Charlie"));

      assertThat("Should have separators",
          result, containsString("──"));
    }
  }

  @Test
  void testFormatResultSetWithNulls() throws Exception {
    try (Connection conn = connection.get();
         Statement stmt = conn.createStatement();
         ResultSet rs = stmt.executeQuery("SELECT name, email FROM users WHERE id = 3")) {

      String result = ResultSetFormatter.format(rs);

      assertThat("Should contain data",
          result, containsString("Charlie"));

      assertThat("Should show null placeholder",
          result, containsString("<null>"));
    }
  }

  @Test
  void testFormatEmptyResultSet() throws Exception {
    try (Connection conn = connection.get();
         Statement stmt = conn.createStatement();
         ResultSet rs = stmt.executeQuery("SELECT * FROM users WHERE id > 1000")) {

      String result = ResultSetFormatter.format(rs);

      assertThat("Should indicate no results",
          result, is("No results."));
    }
  }

  @Test
  void testFormatWithPaginationFooter_moreAvailable() throws Exception {
    try (Connection conn = connection.get();
         Statement stmt = conn.createStatement();
         ResultSet rs = stmt.executeQuery("SELECT id, name FROM users ORDER BY id LIMIT 3")) {

      // Simulate pagination: display 2 rows, but 3 were fetched (hasMore = true)
      String result = ResultSetFormatter.format(rs, 1, 2);

      assertThat("Should contain first row",
          result, containsString("Alice"));
      assertThat("Should contain second row",
          result, containsString("Bob"));

      // Third row should not be displayed (limited to 2)
      assertThat("Should not contain third row",
          result, not(containsString("Charlie")));

      assertThat("Should show pagination footer",
          result, containsString("Page 1 (more available)"));
    }
  }

  @Test
  void testFormatWithPaginationFooter_noMoreData() throws Exception {
    try (Connection conn = connection.get();
         Statement stmt = conn.createStatement();
         ResultSet rs = stmt.executeQuery("SELECT id, name FROM users ORDER BY id LIMIT 2")) {

      // Display all 2 rows (no more data)
      String result = ResultSetFormatter.format(rs, 2, 2);

      assertThat("Should contain data",
          result, containsString("Alice"));
      assertThat("Should contain data",
          result, containsString("Bob"));

      assertThat("Should show final page footer",
          result, containsString("Page 2 (no more data)"));
    }
  }

  @Test
  void testFormatMessage() {
    String result = ResultSetFormatter.formatMessage("Operation completed successfully");

    assertThat("Should return message as-is",
        result, is("Operation completed successfully"));
  }

  @Test
  void testFormatRowCount_zero() {
    String result = ResultSetFormatter.formatRowCount(0);

    assertThat("Should format zero rows",
        result, is("No rows affected."));
  }

  @Test
  void testFormatRowCount_one() {
    String result = ResultSetFormatter.formatRowCount(1);

    assertThat("Should format one row with singular",
        result, is("1 row affected."));
  }

  @Test
  void testFormatRowCount_multiple() {
    String result = ResultSetFormatter.formatRowCount(42);

    assertThat("Should format multiple rows with plural",
        result, is("42 rows affected."));
  }

  @Test
  void testFormatAllColumns() throws Exception {
    try (Connection conn = connection.get();
         Statement stmt = conn.createStatement();
         ResultSet rs = stmt.executeQuery("SELECT * FROM users WHERE id = 1")) {

      String result = ResultSetFormatter.format(rs);

      assertThat("Should contain all columns",
          result, allOf(
              containsString("id"),
              containsString("name"),
              containsString("email")
          ));

      assertThat("Should contain row data",
          result, allOf(
              containsString("1"),
              containsString("Alice"),
              containsString("alice@example.com")
          ));
    }
  }
}
