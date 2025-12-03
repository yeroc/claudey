package org.geekden.mcp.journal;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * Test MCP tools structure and basic functionality.
 */
@QuarkusTest
class JournalMcpToolsTest {

  @Inject
  JournalMcpTools mcpTools;

  @Test
  void testMcpToolsInjection() {
    assertThat("JournalMcpTools should be injected",
        mcpTools, is(notNullValue()));
  }

  @Test
  void testProcessThoughtsWithEmptyInput() {
    // All null/empty inputs should return message about no thoughts
    String result = mcpTools.processThoughts(null, null, null, null, null);
    assertThat("Result should indicate no thoughts",
        result, containsString("No thoughts"));
  }

  @Test
  void testProcessThoughtsWithContent() {
    // Write a simple thought
    String result = mcpTools.processThoughts("Test feeling", null, null, null, null);
    assertThat("Result should indicate success",
        result, containsString("success"));
  }

  @Test
  void testSearchJournalBasic() {
    // Basic search should not throw exceptions
    String result = mcpTools.searchJournal("test query", 10, "both", null, null, null);
    assertThat("Result should not be null",
        result, is(notNullValue()));
  }

  @Test
  void testListRecentEntriesBasic() {
    // Basic listing should not throw exceptions
    String result = mcpTools.listRecentEntries(10, "both", 30);
    assertThat("Result should not be null",
        result, is(notNullValue()));
  }
}
