package org.geekden.mcp.database.service;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.geekden.mcp.database.IsolatedDatabaseProfile;
import org.geekden.mcp.database.dialect.DialectFactory;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DatabaseMetaData;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for PaginationHandler.
 */
@QuarkusTest
@TestProfile(PaginationHandlerTest.Profile.class)
class PaginationHandlerTest {

  public static class Profile extends IsolatedDatabaseProfile {
  }

  @Inject
  DialectFactory dialectFactory;

  @Inject
  Instance<Connection> connection;

  @Test
  void testIsPageable_selectQuery() {
    PaginationHandler handler = new PaginationHandler(100, dialectFactory);

    assertThat("Should detect SELECT query",
        handler.isPageable("SELECT * FROM users"), is(true));

    assertThat("Should detect SELECT with lowercase",
        handler.isPageable("select id, name from users"), is(true));

    assertThat("Should detect SELECT with mixed case",
        handler.isPageable("SeLeCt id FROM users"), is(true));

    assertThat("Should detect SELECT with leading whitespace",
        handler.isPageable("  SELECT * FROM users"), is(true));
  }

  @Test
  void testIsPageable_nonSelectQueries() {
    PaginationHandler handler = new PaginationHandler(100, dialectFactory);

    assertThat("Should not paginate INSERT",
        handler.isPageable("INSERT INTO users (name) VALUES ('test')"), is(false));

    assertThat("Should not paginate UPDATE",
        handler.isPageable("UPDATE users SET name = 'test'"), is(false));

    assertThat("Should not paginate DELETE",
        handler.isPageable("DELETE FROM users"), is(false));

    assertThat("Should not paginate CREATE",
        handler.isPageable("CREATE TABLE test (id INT)"), is(false));

    assertThat("Should not paginate DROP",
        handler.isPageable("DROP TABLE test"), is(false));
  }

  @Test
  void testIsPageable_selectWithExistingLimit() {
    PaginationHandler handler = new PaginationHandler(100, dialectFactory);

    assertThat("Should not paginate SELECT with existing LIMIT",
        handler.isPageable("SELECT * FROM users LIMIT 10"), is(false));

    assertThat("Should not paginate SELECT with LIMIT in lowercase",
        handler.isPageable("select * from users limit 10"), is(false));
  }

  @Test
  void testIsPageable_emptyOrNull() {
    PaginationHandler handler = new PaginationHandler(100, dialectFactory);

    assertThat("Should handle null query",
        handler.isPageable(null), is(false));

    assertThat("Should handle empty query",
        handler.isPageable(""), is(false));

    assertThat("Should handle whitespace only",
        handler.isPageable("   "), is(false));
  }

  @Test
  void testAddPagination_firstPage() throws Exception {
    PaginationHandler handler = new PaginationHandler(100, dialectFactory);

    try (Connection conn = connection.get()) {
      DatabaseMetaData metaData = conn.getMetaData();
      String query = "SELECT * FROM users";
      String result = handler.addPagination(query, 1, metaData);

      assertThat("Should add LIMIT 101 for first page",
          result, containsString("LIMIT 101"));

      assertThat("Should add OFFSET 0 for first page",
          result, containsString("OFFSET 0"));

      assertThat("Should preserve original query",
          result, startsWith("SELECT * FROM users"));
    }
  }

  @Test
  void testAddPagination_secondPage() throws Exception {
    PaginationHandler handler = new PaginationHandler(100, dialectFactory);

    try (Connection conn = connection.get()) {
      DatabaseMetaData metaData = conn.getMetaData();
      String query = "SELECT * FROM users";
      String result = handler.addPagination(query, 2, metaData);

      assertThat("Should add LIMIT 101 for second page",
          result, containsString("LIMIT 101"));

      assertThat("Should add OFFSET 100 for second page",
          result, containsString("OFFSET 100"));
    }
  }

  @Test
  void testAddPagination_thirdPage() throws Exception {
    PaginationHandler handler = new PaginationHandler(100, dialectFactory);

    try (Connection conn = connection.get()) {
      DatabaseMetaData metaData = conn.getMetaData();
      String query = "SELECT id, name FROM users WHERE active = true";
      String result = handler.addPagination(query, 3, metaData);

      assertThat("Should add LIMIT 101 for third page",
          result, containsString("LIMIT 101"));

      assertThat("Should add OFFSET 200 for third page",
          result, containsString("OFFSET 200"));

      assertThat("Should preserve original query",
          result, startsWith("SELECT id, name FROM users WHERE active = true"));
    }
  }

  @Test
  void testAddPagination_customPageSize() throws Exception {
    PaginationHandler handler = new PaginationHandler(50, dialectFactory);

    try (Connection conn = connection.get()) {
      DatabaseMetaData metaData = conn.getMetaData();
      String query = "SELECT * FROM users";
      String result = handler.addPagination(query, 2, metaData);

      assertThat("Should add LIMIT 51 for page size 50",
          result, containsString("LIMIT 51"));

      assertThat("Should add OFFSET 50 for second page with page size 50",
          result, containsString("OFFSET 50"));
    }
  }

  @Test
  void testAddPagination_removesTrailingSemicolon() throws Exception {
    PaginationHandler handler = new PaginationHandler(100, dialectFactory);

    try (Connection conn = connection.get()) {
      DatabaseMetaData metaData = conn.getMetaData();
      String query = "SELECT * FROM users;";
      String result = handler.addPagination(query, 1, metaData);

      assertThat("Should not have double semicolon",
          result, not(containsString(";;")));

      assertThat("Should end with OFFSET clause",
          result, endsWith("OFFSET 0"));
    }
  }

  @Test
  void testAddPagination_invalidPageNumber() throws Exception {
    PaginationHandler handler = new PaginationHandler(100, dialectFactory);

    try (Connection conn = connection.get()) {
      DatabaseMetaData metaData = conn.getMetaData();

      assertThrows(IllegalArgumentException.class, () -> {
        handler.addPagination("SELECT * FROM users", 0, metaData);
      }, "Should throw exception for page 0");

      assertThrows(IllegalArgumentException.class, () -> {
        handler.addPagination("SELECT * FROM users", -1, metaData);
      }, "Should throw exception for negative page");
    }
  }

  @Test
  void testHasMoreData() {
    PaginationHandler handler = new PaginationHandler(100, dialectFactory);

    assertThat("Should have more data when 101 rows fetched",
        handler.hasMoreData(101), is(true));

    assertThat("Should not have more data when 100 rows fetched",
        handler.hasMoreData(100), is(false));

    assertThat("Should not have more data when fewer rows fetched",
        handler.hasMoreData(50), is(false));

    assertThat("Should not have more data when no rows fetched",
        handler.hasMoreData(0), is(false));
  }

  @Test
  void testGetRowsToDisplay() {
    PaginationHandler handler = new PaginationHandler(100, dialectFactory);

    assertThat("Should display 100 rows when 101 fetched",
        handler.getRowsToDisplay(101), is(100));

    assertThat("Should display all rows when fewer than page size",
        handler.getRowsToDisplay(50), is(50));

    assertThat("Should display page size when exactly page size fetched",
        handler.getRowsToDisplay(100), is(100));

    assertThat("Should display 0 when no rows fetched",
        handler.getRowsToDisplay(0), is(0));
  }

  @Test
  void testGetPageSize() {
    PaginationHandler handler = new PaginationHandler(75, dialectFactory);

    assertThat("Should return configured page size",
        handler.getPageSize(), is(75));
  }
}
