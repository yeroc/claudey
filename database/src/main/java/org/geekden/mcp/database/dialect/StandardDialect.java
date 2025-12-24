package org.geekden.mcp.database.dialect;

import java.sql.DatabaseMetaData;

/**
 * Standard SQL dialect: PostgreSQL, MySQL, Oracle, etc.
 */
class StandardDialect implements DatabaseDialect {

  @Override
  public boolean accepts(DatabaseMetaData metaData) {
    return true; // Catch-all dialect
  }

  @Override
  public String getName() {
    return "Standard SQL";
  }

  @Override
  public Introspector introspector() {
    return Introspectors.standard();
  }

  @Override
  public Paginator paginator() {
    return Paginators.standard();
  }
}
