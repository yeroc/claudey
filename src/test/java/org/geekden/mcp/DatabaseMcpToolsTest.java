package org.geekden.mcp;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test MCP tools structure and basic functionality.
 */
@QuarkusTest
class DatabaseMcpToolsTest {

  @Inject
  DatabaseMcpTools mcpTools;

  @Test
  void testMcpToolsInjection() {
    assertNotNull(mcpTools, "DatabaseMcpTools should be injected");
  }

  @Test
  void testIntrospectWithoutDatabase() {
    // Without DB_URL set, should return configuration error
    String result = mcpTools.introspect(null, null);
    assertNotNull(result, "Result should not be null");
    // May contain error message or placeholder depending on config state
  }

  @Test
  void testExecuteSqlWithoutDatabase() {
    // Without DB_URL set, should return configuration error
    String result = mcpTools.executeSql("SELECT 1", 1);
    assertNotNull(result, "Result should not be null");
    // May contain error message or placeholder depending on config state
  }

  @Test
  void testExecuteSqlInvalidPage() {
    // Page number must be >= 1
    String result = mcpTools.executeSql("SELECT 1", 0);
    assertTrue(result.contains("Error") || result.contains("must be"),
        "Should return error for invalid page number");
  }
}
