package org.geekden.mcp.database.service;

import org.geekden.mcp.database.dialect.DatabaseDialect;
import org.geekden.mcp.database.dialect.DialectFactory;

import java.sql.DatabaseMetaData;
import java.sql.SQLException;

/**
 * Handles pagination logic for SQL queries.
 *
 * Features:
 * - Detects if a query is pageable (SELECT statements)
 * - Injects LIMIT/OFFSET clauses for pagination
 * - Supports database-specific syntax via dialect abstraction
 * - Fetches PAGE_SIZE + 1 rows to detect if more data is available
 */
public class PaginationHandler {

  private final int pageSize;
  private final DialectFactory dialectFactory;

  public PaginationHandler(int pageSize, DialectFactory dialectFactory) {
    this.pageSize = pageSize;
    this.dialectFactory = dialectFactory;
  }

  /**
   * Check if a query is pageable (i.e., it's a SELECT statement).
   *
   * @param query SQL query
   * @return true if query can be paginated
   */
  public boolean isPageable(String query) {
    if (query == null || query.trim().isEmpty()) {
      return false;
    }

    String trimmed = query.trim();
    String upperCased = trimmed.toUpperCase();

    // Simple heuristic: SELECT queries without existing LIMIT clause can be paginated
    // This is a basic check - more sophisticated parsing could be added later
    return upperCased.startsWith("SELECT") && !upperCased.contains("LIMIT");
  }

  /**
   * Add pagination to a query using database-specific dialect.
   *
   * Fetches PAGE_SIZE + 1 rows to detect if more data is available.
   * Formula: LIMIT (PAGE_SIZE + 1) OFFSET (page-1)*PAGE_SIZE
   *
   * @param query    SQL query
   * @param page     Page number (1-based)
   * @param metaData Database metadata for dialect detection
   * @return Query with pagination added
   */
  public String addPagination(String query, int page, DatabaseMetaData metaData) throws SQLException {
    if (page < 1) {
      throw new IllegalArgumentException("Page number must be >= 1");
    }

    DatabaseDialect dialect = dialectFactory.getDialect(metaData);

    // Calculate offset
    int offset = (page - 1) * pageSize;

    // Fetch one extra row to detect if more data is available
    int limit = pageSize + 1;

    // Remove trailing semicolon if present
    String trimmedQuery = query.trim();
    if (trimmedQuery.endsWith(";")) {
      trimmedQuery = trimmedQuery.substring(0, trimmedQuery.length() - 1);
    }

    return dialect.paginator().paginate(trimmedQuery, offset, limit);
  }

  /**
   * Get the configured page size.
   *
   * @return Page size
   */
  public int getPageSize() {
    return pageSize;
  }

  /**
   * Check if more data is available based on the number of rows fetched.
   *
   * @param rowsFetched Number of rows fetched
   * @return true if more data is available (fetched PAGE_SIZE + 1 rows)
   */
  public boolean hasMoreData(int rowsFetched) {
    return rowsFetched > pageSize;
  }

  /**
   * Get the maximum number of rows to display.
   * When PAGE_SIZE + 1 rows are fetched, only display PAGE_SIZE rows.
   *
   * @param rowsFetched Number of rows fetched
   * @return Number of rows to display
   */
  public int getRowsToDisplay(int rowsFetched) {
    return Math.min(rowsFetched, pageSize);
  }
}
