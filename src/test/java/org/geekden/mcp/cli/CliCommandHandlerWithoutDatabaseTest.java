package org.geekden.mcp.cli;

import org.geekden.mcp.AbstractDatabaseIntegrationTest;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * Test CLI command handler behavior when database is not configured.
 * <p>
 * Verifies that appropriate error messages are shown when database URL is missing.
 */
@QuarkusTest
@TestProfile(CliCommandHandlerWithoutDatabaseTest.NoDatabaseProfile.class)
class CliCommandHandlerWithoutDatabaseTest extends AbstractDatabaseIntegrationTest {

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

  @Inject
  CapturingOutput output;

  @BeforeEach
  void setUp() {
    output.reset();
  }

  @Test
  void testIntrospectWithoutDatabaseFails() {
    int exitCode = cliHandler.execute(new String[]{"introspect"});
    assertThat("Should return exit code 1 when database not configured", exitCode, is(1));

    String stderr = output.getStderr();
    assertThat("Should show database not configured error", stderr, containsString("Database not configured"));
  }

  @Test
  void testQueryWithoutDatabaseFails() {
    int exitCode = cliHandler.execute(new String[]{"query", "SELECT 1"});
    assertThat("Should return exit code 1 when database not configured", exitCode, is(1));

    String stderr = output.getStderr();
    assertThat("Should show database not configured error", stderr, containsString("Database not configured"));
  }
}
