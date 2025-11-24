package org.geekden.mcp.database;


import io.quarkus.test.junit.QuarkusTest;


import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * Test MCP tools structure and basic functionality.
 */
@QuarkusTest
@TestProfile(DatabaseMcpToolsTest.Profile.class)
class DatabaseMcpToolsTest {

  public static class Profile extends IsolatedDatabaseProfile {
  }

  @Inject
  DatabaseMcpTools mcpTools;

  @Test
  void testMcpToolsInjection() {
    assertThat("DatabaseMcpTools should be injected",
        mcpTools, is(notNullValue()));
  }

  @Test
  void testIntrospectWithoutDatabase() {
    // Without DB_URL set, should return configuration error
    String result = mcpTools.introspect(null, null);
    assertThat("Result should not be null",
        result, is(notNullValue()));
    // May contain error message or placeholder depending on config state
  }

  @Test
  void testExecuteSqlWithoutDatabase() {
    // Without DB_URL set, should return configuration error
    String result = mcpTools.executeSql("SELECT 1", 1);
    assertThat("Result should not be null",
        result, is(notNullValue()));
    // May contain error message or placeholder depending on config state
  }

  @Test
  void testExecuteSqlInvalidPage() {
    // Page number must be >= 1
    String result = mcpTools.executeSql("SELECT 1", 0);
    assertThat("Should return error for invalid page number",
        result, anyOf(containsString("Error"), containsString("must be")));
  }
}
