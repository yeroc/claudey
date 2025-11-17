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
 * class MyTest {
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
    // Check if DB_URL environment variable is set
    String dbUrl = System.getenv("DB_URL");

    // If DB_URL is set and not SQLite, use it as-is (e.g., for PostgreSQL testing)
    if (dbUrl != null && !dbUrl.startsWith("jdbc:sqlite:")) {
      return Map.of("db.jdbc.url", dbUrl);
    }

    // For SQLite (default), create isolated database per test class
    Class<?> enclosingClass = this.getClass().getEnclosingClass();
    if (enclosingClass == null) {
      throw new IllegalStateException(
          "IsolatedDatabaseProfile must be used as an inner class of a test"
      );
    }

    String testClassName = enclosingClass.getSimpleName();
    String databaseUrl = "jdbc:sqlite:target/" + testClassName + ".db";

    return Map.of(
      "db.jdbc.url", databaseUrl
    );
  }
}
