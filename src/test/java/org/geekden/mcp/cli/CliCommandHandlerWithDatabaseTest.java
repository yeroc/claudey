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

    assertThat("Should execute introspect command successfully",
        stdout, anyOf(containsString("Listing all schemas"), containsString("pending")));
  }

  @Test
  void testIntrospectSchemaWithDatabaseSucceeds() throws Exception {
    String stdout = tapSystemOut(() -> {
      int exitCode = cliHandler.execute(new String[]{"introspect", "public"});
      assertThat("Should return exit code 0 with database configured",
          exitCode, is(0));
    });

    assertThat("Should execute introspect schema command successfully",
        stdout, anyOf(containsString("Listing tables"), containsString("pending")));
  }

  @Test
  void testIntrospectTableWithDatabaseSucceeds() throws Exception {
    String stdout = tapSystemOut(() -> {
      int exitCode = cliHandler.execute(new String[]{"introspect", "public", "users"});
      assertThat("Should return exit code 0 with database configured",
          exitCode, is(0));
    });

    assertThat("Should execute introspect table command successfully",
        stdout, anyOf(containsString("Describing table"), containsString("pending")));
  }

  @Test
  void testQueryWithDatabaseSucceeds() throws Exception {
    String stdout = tapSystemOut(() -> {
      int exitCode = cliHandler.execute(new String[]{"query", "SELECT 1"});
      assertThat("Should return exit code 0 with database configured",
          exitCode, is(0));
    });

    assertThat("Should execute query command successfully",
        stdout, anyOf(containsString("Executing query"), containsString("pending")));
  }

  @Test
  void testQueryWithPageParameterSucceeds() throws Exception {
    String stdout = tapSystemOut(() -> {
      int exitCode = cliHandler.execute(new String[]{"query", "SELECT 1", "--page", "2"});
      assertThat("Should return exit code 0 with page parameter",
          exitCode, is(0));
    });

    assertThat("Should execute query with page parameter",
        stdout, containsString("page 2"));
  }
}
