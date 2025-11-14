package org.geekden.mcp.pagination;

/**
 * Handles pagination logic for SQL queries.
 *
 * Features:
 * - Detects if a query is pageable (SELECT statements)
 * - Injects LIMIT/OFFSET clauses for pagination
 * - Supports database-specific syntax (PostgreSQL, SQLite)
 * - Fetches PAGE_SIZE + 1 rows to detect if more data is available
 */
public class PaginationHandler {

  private final int pageSize;

  public PaginationHandler(int pageSize) {
    this.pageSize = pageSize;
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
   * Add pagination to a query.
   *
   * Fetches PAGE_SIZE + 1 rows to detect if more data is available.
   * Formula: LIMIT (PAGE_SIZE + 1) OFFSET (page-1)*PAGE_SIZE
   *
   * @param query SQL query
   * @param page  Page number (1-based)
   * @return Query with pagination added
   */
  public String addPagination(String query, int page) {
    if (page < 1) {
      throw new IllegalArgumentException("Page number must be >= 1");
    }

    // Calculate offset
    int offset = (page - 1) * pageSize;

    // Fetch one extra row to detect if more data is available
    int limit = pageSize + 1;

    // Add LIMIT and OFFSET (works for PostgreSQL and SQLite)
    String paginatedQuery = query.trim();
    if (paginatedQuery.endsWith(";")) {
      paginatedQuery = paginatedQuery.substring(0, paginatedQuery.length() - 1);
    }

    paginatedQuery += " LIMIT " + limit + " OFFSET " + offset;

    return paginatedQuery;
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
