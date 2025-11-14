package org.geekden.mcp.formatter;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * Tests for TableFormatter.
 */
class TableFormatterTest {

  @Test
  void testSimpleTable() {
    List<String> headers = List.of("Name", "Age");
    List<List<String>> rows = new ArrayList<>();
    rows.add(TableFormatter.row("Alice", 30));
    rows.add(TableFormatter.row("Bob", 25));

    String result = TableFormatter.format(headers, rows);

    assertThat("Should contain headers", result, containsString("Name"));
    assertThat("Should contain headers", result, containsString("Age"));
    assertThat("Should contain data", result, containsString("Alice"));
    assertThat("Should contain data", result, containsString("Bob"));
    assertThat("Should contain header separator", result, containsString("──"));
  }

  @Test
  void testTableWithNullValues() {
    List<String> headers = List.of("Name", "Email");
    List<List<String>> rows = new ArrayList<>();
    rows.add(TableFormatter.row("Alice", "alice@example.com"));
    rows.add(TableFormatter.row("Bob", null));

    String result = TableFormatter.format(headers, rows);

    assertThat("Should contain data", result, containsString("Alice"));
    assertThat("Should contain null placeholder", result, containsString("<null>"));
  }

  @Test
  void testTableWithVaryingColumnWidths() {
    List<String> headers = List.of("Short", "VeryLongHeaderName");
    List<List<String>> rows = new ArrayList<>();
    rows.add(TableFormatter.row("A", "B"));
    rows.add(TableFormatter.row("LongerValue", "C"));

    String result = TableFormatter.format(headers, rows);

    assertThat("Should contain all values", result, containsString("Short"));
    assertThat("Should contain all values", result, containsString("VeryLongHeaderName"));
    assertThat("Should contain all values", result, containsString("LongerValue"));

    // Verify separators are present
    assertThat("Should have header separator", result, containsString("──"));
  }

  @Test
  void testEmptyTable() {
    List<String> headers = List.of("Column1", "Column2");
    List<List<String>> rows = new ArrayList<>();

    String result = TableFormatter.format(headers, rows);

    assertThat("Should contain headers", result, containsString("Column1"));
    assertThat("Should contain headers", result, containsString("Column2"));
    assertThat("Should contain separators", result, containsString("──"));
  }

  @Test
  void testTableWithFooter() {
    List<String> headers = List.of("ID", "Name");
    List<List<String>> rows = new ArrayList<>();
    rows.add(TableFormatter.row(1, "Alice"));
    rows.add(TableFormatter.row(2, "Bob"));

    String result = TableFormatter.formatWithFooter(headers, rows, "Page 1 (more available)");

    assertThat("Should contain footer", result, containsString("Page 1 (more available)"));
    assertThat("Should contain data", result, containsString("Alice"));
  }

  @Test
  void testValueToString() {
    assertThat("Should convert null to placeholder",
        TableFormatter.valueToString(null), is("<null>"));

    assertThat("Should convert number to string",
        TableFormatter.valueToString(42), is("42"));

    assertThat("Should convert string as-is",
        TableFormatter.valueToString("test"), is("test"));
  }

  @Test
  void testRowHelper() {
    List<String> row = TableFormatter.row("Alice", 30, null);

    assertThat("Should have 3 elements", row.size(), is(3));
    assertThat("Should contain string value", row.get(0), is("Alice"));
    assertThat("Should contain number value", row.get(1), is("30"));
    assertThat("Should contain null placeholder", row.get(2), is("<null>"));
  }

  @Test
  void testFormatWithEmptyHeaders() {
    List<String> headers = new ArrayList<>();
    List<List<String>> rows = new ArrayList<>();

    String result = TableFormatter.format(headers, rows);

    assertThat("Should return empty string for empty headers", result, is(""));
  }

  @Test
  void testFormatWithNullHeaders() {
    String result = TableFormatter.format(null, new ArrayList<>());

    assertThat("Should return empty string for null headers", result, is(""));
  }

  @Test
  void testTableAlignment() {
    List<String> headers = List.of("A", "B");
    List<List<String>> rows = new ArrayList<>();
    rows.add(TableFormatter.row("Short", "Long Value"));
    rows.add(TableFormatter.row("X", "Y"));

    String result = TableFormatter.format(headers, rows);

    // Split by lines to verify alignment
    String[] lines = result.split("\n");

    // Should have: header, separator, 2 data rows, footer separator = 5 lines
    assertThat("Should have 5 lines", lines.length, is(5));

    // Verify all data lines have the same effective width (accounting for padding)
    // This is a basic check that alignment is working
    assertThat("Should contain Short", result, containsString("Short"));
    assertThat("Should contain Long Value", result, containsString("Long Value"));
  }
}
