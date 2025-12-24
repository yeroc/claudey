package org.geekden.mcp.database.dialect;

import java.sql.DatabaseMetaData;
import java.sql.SQLException;

/**
 * SQLite dialect: Custom schema provider, standard pagination.
 */
class SQLiteDialect implements DatabaseDialect {

  @Override
  public boolean accepts(DatabaseMetaData metaData) throws SQLException {
    String dbName = metaData.getDatabaseProductName().toLowerCase();
    return dbName.contains("sqlite");
  }

  @Override
  public String getName() {
    return "SQLite";
  }

  @Override
  public Introspector introspector() {
    return Introspectors.sqlite();
  }

  @Override
  public Paginator paginator() {
    return Paginators.standard();
  }
}
