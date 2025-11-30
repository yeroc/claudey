package org.geekden.mcp.database.formatter;

import java.util.ArrayList;
import java.util.List;

/**
 * Formats database results as aligned text tables with Unicode separators.
 *
 * Features:
 * - Auto-sizing columns based on content
 * - Unicode separators (── for header, full-width for footer)
 * - Null value handling (<null>)
 * - Clean, modern aesthetic
 */
public class TableFormatter {

  private static final String UNICODE_SEPARATOR = "──";
  private static final String NULL_VALUE = "<null>";

  /**
   * Format data as an aligned text table.
   *
   * @param headers Column headers
   * @param rows    Data rows (each row is a list of cell values)
   * @return Formatted table as a string
   */
  public static String format(List<String> headers, List<List<String>> rows) {
    if (headers == null || headers.isEmpty()) {
      return "";
    }

    // Calculate column widths
    int[] columnWidths = calculateColumnWidths(headers, rows);

    StringBuilder sb = new StringBuilder();

    // Header row
    appendRow(sb, headers, columnWidths);
    sb.append('\n');

    // Header separator
    appendHeaderSeparator(sb, columnWidths);
    sb.append('\n');

    // Data rows
    if (rows != null) {
      for (List<String> row : rows) {
        appendRow(sb, row, columnWidths);
        sb.append('\n');
      }
    }

    // Footer separator
    appendFooterSeparator(sb, columnWidths);

    return sb.toString();
  }

  /**
   * Format data with a footer message (e.g., pagination info).
   *
   * @param headers       Column headers
   * @param rows          Data rows
   * @param footerMessage Footer message to display below the table
   * @return Formatted table with footer message
   */
  public static String formatWithFooter(List<String> headers, List<List<String>> rows, String footerMessage) {
    String table = format(headers, rows);
    if (footerMessage != null && !footerMessage.isEmpty()) {
      return table + "\n" + footerMessage;
    }
    return table;
  }

  /**
   * Calculate the width of each column based on headers and data.
   */
  private static int[] calculateColumnWidths(List<String> headers, List<List<String>> rows) {
    int columnCount = headers.size();
    int[] widths = new int[columnCount];

    // Initialize with header widths
    for (int i = 0; i < columnCount; i++) {
      widths[i] = headers.get(i).length();
    }

    // Update with data widths
    if (rows != null) {
      for (List<String> row : rows) {
        for (int i = 0; i < Math.min(columnCount, row.size()); i++) {
          String value = row.get(i);
          if (value == null) {
            value = NULL_VALUE;
          }
          widths[i] = Math.max(widths[i], value.length());
        }
      }
    }

    return widths;
  }

  /**
   * Append a single row to the output.
   */
  private static void appendRow(StringBuilder sb, List<String> cells, int[] columnWidths) {
    for (int i = 0; i < columnWidths.length; i++) {
      if (i > 0) {
        sb.append("  ");  // Two spaces between columns
      }

      String value = "";
      if (i < cells.size()) {
        value = cells.get(i);
        if (value == null) {
          value = NULL_VALUE;
        }
      }

      // Left-align text, pad to column width
      sb.append(value);
      int padding = columnWidths[i] - value.length();
      for (int j = 0; j < padding; j++) {
        sb.append(' ');
      }
    }
  }

  /**
   * Append header separator (Unicode light horizontal under each column).
   */
  private static void appendHeaderSeparator(StringBuilder sb, int[] columnWidths) {
    for (int i = 0; i < columnWidths.length; i++) {
      if (i > 0) {
        sb.append("  ");  // Two spaces between columns
      }

      // Repeat Unicode separator character for column width
      int width = columnWidths[i];
      for (int j = 0; j < width; j++) {
        sb.append('─');  // Unicode light horizontal
      }
    }
  }

  /**
   * Append footer separator (full-width Unicode light horizontal).
   */
  private static void appendFooterSeparator(StringBuilder sb, int[] columnWidths) {
    // Calculate total width (columns + spacing)
    int totalWidth = 0;
    for (int i = 0; i < columnWidths.length; i++) {
      totalWidth += columnWidths[i];
      if (i > 0) {
        totalWidth += 2;  // Add spacing between columns
      }
    }

    // Draw full-width separator
    for (int i = 0; i < totalWidth; i++) {
      sb.append('─');  // Unicode light horizontal
    }
  }

  /**
   * Convert a value to a string, handling nulls.
   */
  public static String valueToString(Object value) {
    return value == null ? NULL_VALUE : value.toString();
  }

  /**
   * Create a list of string values from objects.
   */
  public static List<String> row(Object... values) {
    List<String> row = new ArrayList<>();
    for (Object value : values) {
      row.add(valueToString(value));
    }
    return row;
  }
}
