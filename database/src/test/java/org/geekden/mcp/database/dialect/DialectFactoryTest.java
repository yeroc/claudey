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
  void testGetDialectForSQLite() throws Exception {
    try (Connection conn = connection.get()) {
      DatabaseMetaData metaData = conn.getMetaData();
      DatabaseDialect dialect = dialectFactory.getDialect(metaData);

      assertThat("Should return SQLite dialect", dialect.getName(), is("SQLite"));
      assertThat("Should have introspector", dialect.introspector(), is(notNullValue()));
      assertThat("Should have paginator", dialect.paginator(), is(notNullValue()));
    }
  }

  @Test
  void testSQLiteIntrospector() throws Exception {
    try (Connection conn = connection.get()) {
      DatabaseMetaData metaData = conn.getMetaData();
      DatabaseDialect dialect = dialectFactory.getDialect(metaData);

      List<String> schemas = dialect.introspector().schemas(metaData);

      assertThat("Should return schemas", schemas, is(notNullValue()));
      assertThat("Should contain 'main' schema", schemas, contains("main"));
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
