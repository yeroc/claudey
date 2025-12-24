package org.geekden.mcp.database.dialect;

import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.List;

/**
 * Database dialect: composition of strategies for database-specific behavior.
 * Dialects are built from providers rather than inheritance.
 */
public interface DatabaseDialect {

  /**
   * Strategy for listing schemas in a database.
   */
  interface Introspector {
    List<String> schemas(DatabaseMetaData metaData) throws SQLException;
  }

  /**
   * Strategy for adding pagination to queries.
   */
  interface Paginator {
    String paginate(String query, int offset, int limit);
  }

  boolean accepts(DatabaseMetaData metaData) throws SQLException;
  String getName();
  Introspector introspector();
  Paginator paginator();
}
