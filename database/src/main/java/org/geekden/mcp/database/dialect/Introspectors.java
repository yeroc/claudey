package org.geekden.mcp.database.dialect;

import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Factory methods for creating database introspectors.
 */
class Introspectors {

  /**
   * Standard schema listing: PostgreSQL, MySQL, Oracle conventions.
   */
  static DatabaseDialect.Introspector standard() {
    return metaData -> {
      List<String> schemas = new ArrayList<>();
      try (ResultSet rs = metaData.getSchemas()) {
        while (rs.next()) {
          String schemaName = rs.getString("TABLE_SCHEM");
          if (schemaName != null && !schemaName.isEmpty()) {
            schemas.add(schemaName);
          }
        }
      }
      return schemas;
    };
  }

  /**
   * SQLite: No traditional schemas, uses "main".
   */
  static DatabaseDialect.Introspector sqlite() {
    return metaData -> List.of("main");
  }
}
