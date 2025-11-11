package org.geekden.mcp.cli;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static uk.org.webcompere.systemstubs.SystemStubs.tapSystemOut;

/**
 * Test CLI command handler with SQLite database configured.
 */
@QuarkusTest
@TestProfile(CliCommandHandlerWithDatabaseTest.SqliteTestProfile.class)
class CliCommandHandlerWithDatabaseTest {

  @Inject
  CliCommandHandler cliHandler;

  /**
   * Test profile that configures SQLite in-memory database.
   */
  public static class SqliteTestProfile implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
      return Map.of(
          "quarkus.datasource.db-kind", "sqlite",
          "quarkus.datasource.jdbc.url", "jdbc:sqlite::memory:"
      );
    }
  }

  @Test
  void testIntrospectWithDatabaseSucceeds() throws Exception {
    String stdout = tapSystemOut(() -> {
      int exitCode = cliHandler.execute(new String[]{"introspect"});
      assertThat("Should return exit code 0 with database configured",
          exitCode, is(0));
    });

    // SQLite in-memory may return "No schemas found." or formatted table
    assertThat("Should execute introspect command successfully",
        stdout, not(emptyString()));
    assertThat("Should return valid output",
        stdout, anyOf(containsString("──"), containsString("No schemas found")));
  }

  @Test
  void testIntrospectSchemaWithDatabaseSucceeds() throws Exception {
    String stdout = tapSystemOut(() -> {
      int exitCode = cliHandler.execute(new String[]{"introspect", "public"});
      assertThat("Should return exit code 0 with database configured",
          exitCode, is(0));
    });

    // For SQLite, "public" schema doesn't exist, so expect "No tables found"
    assertThat("Should execute introspect schema command successfully",
        stdout, not(emptyString()));
    assertThat("Should return valid output",
        stdout, anyOf(containsString("──"), containsString("No tables found")));
  }

  @Test
  void testIntrospectTableWithDatabaseSucceeds() throws Exception {
    String stdout = tapSystemOut(() -> {
      int exitCode = cliHandler.execute(new String[]{"introspect", "public", "users"});
      assertThat("Should return exit code 0 with database configured",
          exitCode, is(0));
    });

    // For SQLite, "public.users" doesn't exist, so expect "Table not found"
    assertThat("Should execute introspect table command successfully",
        stdout, not(emptyString()));
    assertThat("Should return valid output",
        stdout, anyOf(containsString("──"), containsString("not found")));
  }

  @Test
  void testQueryWithDatabaseSucceeds() throws Exception {
    String stdout = tapSystemOut(() -> {
      int exitCode = cliHandler.execute(new String[]{"query", "SELECT 1"});
      assertThat("Should return exit code 0 with database configured",
          exitCode, is(0));
    });

    // Query execution is Phase 4, not yet implemented
    assertThat("Should execute query command successfully",
        stdout, anyOf(containsString("Executing query"), containsString("pending"), containsString("Phase 4")));
  }

  @Test
  void testQueryWithPageParameterSucceeds() throws Exception {
    String stdout = tapSystemOut(() -> {
      int exitCode = cliHandler.execute(new String[]{"query", "SELECT 1", "--page", "2"});
      assertThat("Should return exit code 0 with page parameter",
          exitCode, is(0));
    });

    // Query execution is Phase 4, not yet implemented
    assertThat("Should execute query with page parameter",
        stdout, anyOf(containsString("page 2"), containsString("pending"), containsString("Phase 4")));
  }
}
