package org.geekden.mcp;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

/**
 * Base test profile that provides database isolation per test class.
 * <p>
 * Automatically derives a unique SQLite database file name from the
 * enclosing test class name (e.g., "ResultSetFormatterTest.db").
 * <p>
 * Usage:
 * <pre>
 * {@code
 * @QuarkusTest
 * @TestProfile(MyTest.Profile.class)
 * class MyTest extends AbstractDatabaseIntegrationTest {
 *   public static class Profile extends IsolatedDatabaseProfile {
 *     // Empty! Logic is in the base class
 *   }
 * }
 * }
 * </pre>
 */
public abstract class IsolatedDatabaseProfile implements QuarkusTestProfile {

  @Override
  public Map<String, String> getConfigOverrides() {
    // Dynamically derive database filename from enclosing test class
    Class<?> enclosingClass = this.getClass().getEnclosingClass();
    if (enclosingClass == null) {
      throw new IllegalStateException(
          "IsolatedDatabaseProfile must be used as an inner class of a test"
      );
    }

    String testClassName = enclosingClass.getSimpleName();
    String databaseUrl = "jdbc:sqlite:target/" + testClassName + ".db";

    return Map.of("quarkus.datasource.jdbc.url", databaseUrl);
  }
}
