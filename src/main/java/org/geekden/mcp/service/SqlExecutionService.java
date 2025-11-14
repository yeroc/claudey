package org.geekden.mcp.service;

import jakarta.enterprise.context.ApplicationScoped;
import org.geekden.mcp.formatter.ResultSetFormatter;
import org.geekden.mcp.pagination.PaginationHandler;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Service for executing SQL queries with pagination support.
 *
 * Features:
 * - Supports all SQL types (SELECT, INSERT, UPDATE, DELETE, DDL)
 * - Automatic pagination for SELECT queries
 * - Auto-commit transaction mode
 * - Formatted results with aligned text tables
 */
@ApplicationScoped
public class SqlExecutionService {

  /**
   * Execute a SQL query with pagination support.
   *
   * @param connection Database connection
   * @param query      SQL query to execute
   * @param page       Page number (1-based, only applies to SELECT queries)
   * @param pageSize   Number of rows per page
   * @return Formatted query result
   */
  public String executeQuery(Connection connection, String query, int page, int pageSize) throws SQLException {
    if (query == null || query.trim().isEmpty()) {
      throw new IllegalArgumentException("Query cannot be empty");
    }

    if (page < 1) {
      throw new IllegalArgumentException("Page number must be >= 1");
    }

    if (pageSize < 1) {
      throw new IllegalArgumentException("Page size must be >= 1");
    }

    PaginationHandler paginationHandler = new PaginationHandler(pageSize);

    // Determine if this is a SELECT query that can be paginated
    boolean isPageable = paginationHandler.isPageable(query);

    String executedQuery = query;
    if (isPageable) {
      executedQuery = paginationHandler.addPagination(query, page);
    }

    // Execute the query
    try (Statement stmt = connection.createStatement()) {
      boolean isResultSet = stmt.execute(executedQuery);

      if (isResultSet) {
        // SELECT query - format results
        try (ResultSet rs = stmt.getResultSet()) {
          if (isPageable) {
            // Get the number of rows to display (excluding the extra row for "more data" detection)
            int rowsToDisplay = paginationHandler.getPageSize();
            return ResultSetFormatter.format(rs, page, rowsToDisplay);
          } else {
            // Non-paginated SELECT (e.g., already has LIMIT clause)
            return ResultSetFormatter.format(rs);
          }
        }
      } else {
        // INSERT/UPDATE/DELETE/DDL - return affected row count or success message
        int updateCount = stmt.getUpdateCount();
        if (updateCount >= 0) {
          // DML operation (INSERT/UPDATE/DELETE)
          return ResultSetFormatter.formatRowCount(updateCount);
        } else {
          // DDL operation (CREATE, DROP, ALTER, etc.)
          return "Command executed successfully.";
        }
      }
    }
  }

  /**
   * Execute a SQL query with default page number (1).
   *
   * @param connection Database connection
   * @param query      SQL query to execute
   * @param pageSize   Number of rows per page
   * @return Formatted query result
   */
  public String executeQuery(Connection connection, String query, int pageSize) throws SQLException {
    return executeQuery(connection, query, 1, pageSize);
  }
}
