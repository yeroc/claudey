package org.geekden.mcp.database.service;

import jakarta.enterprise.context.ApplicationScoped;
import org.geekden.mcp.database.formatter.ResultSetFormatter;
import org.geekden.mcp.database.service.PaginationHandler;

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

        // Check if this is a DDL statement (CREATE, DROP, ALTER, etc.)
        // DDL statements return updateCount = 0, same as DML with 0 rows affected
        // We distinguish them by checking the SQL statement type
        if (isDdlStatement(query)) {
          return "Command executed successfully.";
        } else {
          // DML operation (INSERT/UPDATE/DELETE)
          return ResultSetFormatter.formatRowCount(updateCount);
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

  /**
   * Check if a SQL statement is a DDL (Data Definition Language) statement.
   * DDL statements modify database structure rather than data.
   *
   * @param query SQL query to check
   * @return true if the query is a DDL statement
   */
  private boolean isDdlStatement(String query) {
    if (query == null) {
      return false;
    }

    String trimmed = query.trim().toUpperCase();

    // Common DDL keywords
    return trimmed.startsWith("CREATE ")
        || trimmed.startsWith("DROP ")
        || trimmed.startsWith("ALTER ")
        || trimmed.startsWith("TRUNCATE ")
        || trimmed.startsWith("RENAME ");
  }
}
