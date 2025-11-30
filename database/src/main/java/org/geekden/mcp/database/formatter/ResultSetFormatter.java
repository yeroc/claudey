package org.geekden.mcp.database.formatter;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Formats SQL query ResultSets as aligned text tables with Unicode separators.
 *
 * Features:
 * - Extracts column headers from ResultSetMetaData
 * - Extracts row data from ResultSet
 * - Handles NULL values as &lt;null&gt;
 * - Supports pagination metadata in footer
 * - Uses TableFormatter for consistent formatting
 */
public class ResultSetFormatter {

  /**
   * Format a ResultSet as an aligned text table.
   *
   * @param rs ResultSet to format
   * @return Formatted table as a string
   */
  public static String format(ResultSet rs) throws SQLException {
    return format(rs, null, 0);
  }

  /**
   * Format a ResultSet with pagination metadata.
   *
   * @param rs             ResultSet to format
   * @param pageNumber     Current page number (null if not paginated)
   * @param rowsToDisplay  Number of rows to display (0 for all)
   * @return Formatted table with optional pagination footer
   */
  public static String format(ResultSet rs, Integer pageNumber, int rowsToDisplay) throws SQLException {
    ResultSetMetaData metaData = rs.getMetaData();
    int columnCount = metaData.getColumnCount();

    // Extract column headers
    List<String> headers = new ArrayList<>();
    for (int i = 1; i <= columnCount; i++) {
      headers.add(metaData.getColumnLabel(i));
    }

    // Extract rows
    List<List<String>> rows = new ArrayList<>();
    int rowsFetched = 0;
    while (rs.next()) {
      rowsFetched++;

      // Stop if we've reached the display limit
      if (rowsToDisplay > 0 && rowsFetched > rowsToDisplay) {
        break;
      }

      List<String> row = new ArrayList<>();
      for (int i = 1; i <= columnCount; i++) {
        Object value = rs.getObject(i);
        row.add(TableFormatter.valueToString(value));
      }
      rows.add(row);
    }

    // Check if there was any data
    if (rows.isEmpty()) {
      return "No results.";
    }

    // Add pagination footer if specified
    if (pageNumber != null && pageNumber > 0) {
      boolean hasMore = rowsFetched > rowsToDisplay;
      String footer = formatPaginationFooter(pageNumber, hasMore);
      return TableFormatter.formatWithFooter(headers, rows, footer);
    }

    return TableFormatter.format(headers, rows);
  }

  /**
   * Format pagination metadata footer.
   *
   * @param pageNumber Current page number
   * @param hasMore    Whether more data is available
   * @return Pagination footer message
   */
  private static String formatPaginationFooter(int pageNumber, boolean hasMore) {
    if (hasMore) {
      return "Page " + pageNumber + " (more available)";
    } else {
      return "Page " + pageNumber + " (no more data)";
    }
  }

  /**
   * Format a simple message (for non-SELECT queries).
   *
   * @param message Message to format
   * @return Formatted message
   */
  public static String formatMessage(String message) {
    return message;
  }

  /**
   * Format affected row count for DML operations.
   *
   * @param rowCount Number of rows affected
   * @return Formatted message
   */
  public static String formatRowCount(int rowCount) {
    if (rowCount == 0) {
      return "No rows affected.";
    } else if (rowCount == 1) {
      return "1 row affected.";
    } else {
      return rowCount + " rows affected.";
    }
  }
}
