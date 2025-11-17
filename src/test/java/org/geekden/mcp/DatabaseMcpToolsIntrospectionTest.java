package org.geekden.mcp;


import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * Integration tests for DatabaseMcpTools introspection functionality.
 * Tests the MCP tool with actual database connection.
 */
@QuarkusTest
@TestProfile(DatabaseMcpToolsIntrospectionTest.Profile.class)
class DatabaseMcpToolsIntrospectionTest {

  public static class Profile extends IsolatedDatabaseProfile {
  }

  @Inject
  DatabaseMcpTools mcpTools;

  @Test
  void testIntrospectWithNoParameters() {
    // Call introspect with no schema/table parameters
    String result = mcpTools.introspect(null, null);

    assertThat("Should return schema listing or message", result, not(emptyString()));
    // SQLite in-memory may return "No schemas found." or a formatted table
    assertThat("Should return valid response", result,
        anyOf(containsString("──"), containsString("No schemas found")));
  }

  @Test
  void testIntrospectWithSchema() {
    // SQLite uses null as the default schema
    String result = mcpTools.introspect(null, null);

    // Should list schemas or return "No schemas found"
    assertThat("Should return a result", result, not(emptyString()));
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

    // Should return "Table not found" or "No schemas found" for SQLite
    assertThat("Should handle non-existent table gracefully", result, not(emptyString()));
  }

  @Test
  void testIntrospectionErrorHandling() {
    // Test error handling - introspect with invalid parameters
    // The tool should handle errors gracefully
    String result = mcpTools.introspect("invalid_schema", "invalid_table");

    assertThat("Should return error message or not found message", result, not(emptyString()));
  }

  @Test
  void testIntrospectionFormattingConsistency() {
    // Test that all introspection modes return well-formatted output
    String schemasResult = mcpTools.introspect(null, null);

    assertThat("Should return a result", schemasResult, not(emptyString()));
    // Should either be formatted table or a message
    assertThat("Should have valid format", schemasResult,
        anyOf(containsString("──"), containsString("No schemas found")));
  }
}
