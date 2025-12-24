package org.geekden.mcp.database.dialect;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.geekden.mcp.database.IsolatedDatabaseProfile;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@QuarkusTest
@TestProfile(DialectFactoryTest.Profile.class)
class DialectFactoryTest {

  public static class Profile extends IsolatedDatabaseProfile {
  }

  @Inject
  DialectFactory dialectFactory;

  @Inject
  Instance<Connection> connection;

  @Test
  void testGetDialectForCurrentDatabase() throws Exception {
    try (Connection conn = connection.get()) {
      DatabaseMetaData metaData = conn.getMetaData();
      String dbName = metaData.getDatabaseProductName().toLowerCase();
      DatabaseDialect dialect = dialectFactory.getDialect(metaData);

      if (dbName.contains("sqlite")) {
        assertThat("Should return SQLite dialect for SQLite database",
            dialect.getName(), is("SQLite"));
      } else if (dbName.contains("postgresql") || dbName.contains("postgres")) {
        assertThat("Should return Standard SQL dialect for PostgreSQL",
            dialect.getName(), is("Standard SQL"));
      } else {
        assertThat("Should return Standard SQL dialect as fallback",
            dialect.getName(), is("Standard SQL"));
      }

      assertThat("Should have introspector", dialect.introspector(), is(notNullValue()));
      assertThat("Should have paginator", dialect.paginator(), is(notNullValue()));
    }
  }

  @Test
  void testIntrospectorForCurrentDatabase() throws Exception {
    try (Connection conn = connection.get()) {
      DatabaseMetaData metaData = conn.getMetaData();
      String dbName = metaData.getDatabaseProductName().toLowerCase();
      DatabaseDialect dialect = dialectFactory.getDialect(metaData);

      List<String> schemas = dialect.introspector().schemas(metaData);

      assertThat("Should return schemas", schemas, is(notNullValue()));
      assertThat("Should return non-empty schema list", schemas, is(not(empty())));

      if (dbName.contains("sqlite")) {
        assertThat("SQLite should return 'main' schema",
            schemas, contains("main"));
      } else if (dbName.contains("postgresql") || dbName.contains("postgres")) {
        assertThat("PostgreSQL should have schemas including public or information_schema",
            schemas, anyOf(hasItem("public"), hasItem("information_schema")));
      }
    }
  }

  @Test
  void testStandardPaginator() {
    DatabaseDialect dialect = new SQLiteDialect();
    DatabaseDialect.Paginator paginator = dialect.paginator();

    String query = "SELECT * FROM users";
    String paginated = paginator.paginate(query, 0, 10);

    assertThat("Should add LIMIT and OFFSET", paginated, is("SELECT * FROM users LIMIT 10 OFFSET 0"));
  }

  @Test
  void testStandardPaginatorWithOffset() {
    DatabaseDialect dialect = new SQLiteDialect();
    DatabaseDialect.Paginator paginator = dialect.paginator();

    String query = "SELECT * FROM users";
    String paginated = paginator.paginate(query, 10, 10);

    assertThat("Should add LIMIT and OFFSET", paginated, is("SELECT * FROM users LIMIT 10 OFFSET 10"));
  }

  @Test
  void testStandardPaginatorTrimsQuery() {
    DatabaseDialect dialect = new SQLiteDialect();
    DatabaseDialect.Paginator paginator = dialect.paginator();

    String query = "  SELECT * FROM users  ";
    String paginated = paginator.paginate(query, 0, 10);

    assertThat("Should trim query before adding pagination", paginated, is("SELECT * FROM users LIMIT 10 OFFSET 0"));
  }
}
