package org.geekden.mcp.cli;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static uk.org.webcompere.systemstubs.SystemStubs.tapSystemErr;

/**
 * Test CLI behavior when database is not configured.
 */
@QuarkusTest
@TestProfile(CliWithoutDatabaseTest.NoDatabaseProfile.class)
class CliWithoutDatabaseTest {

  /**
   * Test profile that clears database configuration.
   */
  public static class NoDatabaseProfile implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
      return Map.of(
          "DB_URL", "",
          "quarkus.datasource.jdbc.url", ""
      );
    }
  }

  @Inject
  CliCommandHandler cliHandler;

  @Test
  void testIntrospectWithoutDatabaseFails() throws Exception {
    String stderr = tapSystemErr(() -> {
      int exitCode = cliHandler.execute(new String[]{"introspect"});
      assertThat("Should return exit code 1 when database not configured",
          exitCode, is(1));
    });

    assertThat("Should print database configuration error",
        stderr, containsString("Database not configured"));
  }

  @Test
  void testQueryWithoutDatabaseFails() throws Exception {
    String stderr = tapSystemErr(() -> {
      int exitCode = cliHandler.execute(new String[]{"query", "SELECT 1"});
      assertThat("Should return exit code 1 when database not configured",
          exitCode, is(1));
    });

    assertThat("Should print database configuration error",
        stderr, containsString("Database not configured"));
  }
}
