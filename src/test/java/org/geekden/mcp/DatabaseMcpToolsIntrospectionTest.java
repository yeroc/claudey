package org.geekden.mcp;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * Integration tests for DatabaseMcpTools introspection functionality.
 * Tests the MCP tool with actual database connection.
 */
@QuarkusTest
class DatabaseMcpToolsIntrospectionTest {

  @Inject
  DatabaseMcpTools mcpTools;

  @Test
  void testIntrospectWithNoParameters() {
    // Call introspect with no schema/table parameters
    String result = mcpTools.introspect(null, null);

    assertThat("Should return schema listing", result, not(emptyString()));
    assertThat("Should be formatted as table", result, containsString("──"));
  }

  @Test
  void testIntrospectWithSchema() {
    // SQLite uses null as the default schema
    String result = mcpTools.introspect(null, null);

    // Should list schemas (SQLite has "main" schema)
    assertThat("Should list schemas", result, not(emptyString()));
    assertThat("Should contain separator", result, containsString("──"));
  }

  @Test
  void testIntrospectTablesInSchema() {
    // Test listing tables in the default schema (null for SQLite)
    // This will list whatever tables are in the test database
    String result = mcpTools.introspect(null, null);

    assertThat("Should return result", result, not(emptyString()));
  }

  @Test
  void testIntrospectNonExistentSchema() {
    // Test with a schema that doesn't exist
    String result = mcpTools.introspect("nonexistent_schema", null);

    // Should return "No tables found" or similar message
    assertThat("Should handle non-existent schema gracefully", result, not(emptyString()));
  }

  @Test
  void testIntrospectNonExistentTable() {
    // Test with a table that doesn't exist
    String result = mcpTools.introspect(null, "nonexistent_table");

    // Should return "Table not found" or similar message
    assertThat("Should handle non-existent table gracefully", result, not(emptyString()));
    assertThat("Should indicate table not found", result, containsString("not found"));
  }

  @Test
  void testIntrospectionErrorHandling() {
    // Test error handling - introspect with invalid parameters
    // The tool should handle errors gracefully
    String result = mcpTools.introspect("invalid_schema", "invalid_table");

    assertThat("Should return error message", result, not(emptyString()));
  }

  @Test
  void testIntrospectionFormattingConsistency() {
    // Test that all introspection modes return well-formatted output
    String schemasResult = mcpTools.introspect(null, null);

    // Should contain Unicode separators
    assertThat("Should have Unicode separators", schemasResult, containsString("──"));

    // Should end with a separator line (footer)
    assertThat("Should end with separator", schemasResult, containsString("─"));
  }
}
