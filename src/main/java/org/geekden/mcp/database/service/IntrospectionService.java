package org.geekden.mcp.database.service;

import jakarta.enterprise.context.ApplicationScoped;
import org.geekden.mcp.database.formatter.TableFormatter;

import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Service for database schema introspection using JDBC DatabaseMetaData.
 *
 * Provides methods to:
 * - List all schemas
 * - List tables/views in a schema
 * - Get detailed table structure (columns, types, constraints)
 */
@ApplicationScoped
public class IntrospectionService {

  /**
   * List all schemas in the database.
   *
   * @param metaData Database metadata
   * @return Formatted table of schemas
   */
  public String listSchemas(DatabaseMetaData metaData) throws SQLException {
    List<String> headers = List.of("Schema");
    List<List<String>> rows = new ArrayList<>();

    try (ResultSet rs = metaData.getSchemas()) {
      while (rs.next()) {
        String schemaName = rs.getString("TABLE_SCHEM");
        if (schemaName != null && !schemaName.isEmpty()) {
          rows.add(TableFormatter.row(schemaName));
        }
      }
    }

    // For databases like SQLite that don't have traditional schemas,
    // list catalogs instead
    if (rows.isEmpty()) {
      try (ResultSet rs = metaData.getCatalogs()) {
        while (rs.next()) {
          String catalogName = rs.getString("TABLE_CAT");
          if (catalogName != null && !catalogName.isEmpty()) {
            rows.add(TableFormatter.row(catalogName));
          }
        }
      }
    }

    // TODO: Design and implement database dialect abstraction
    // Current approach uses conditional checks (code smell) which doesn't scale.
    // Need a proper pattern like Strategy or DatabaseDialect interface:
    //   - DatabaseDialect interface with methods: getDefaultSchemas(), formatType(), etc.
    //   - SQLiteDialect, PostgreSQLDialect implementations
    //   - Factory to detect and instantiate correct dialect
    //   - Inject dialect into services that need database-specific behavior
    // This will make adding new databases cleaner and more maintainable.

    // Special handling for SQLite: always has a "main" schema
    if (rows.isEmpty()) {
      String dbProductName = metaData.getDatabaseProductName();
      if (dbProductName != null && dbProductName.toLowerCase().contains("sqlite")) {
        rows.add(TableFormatter.row("main"));
      }
    }

    if (rows.isEmpty()) {
      return "No schemas found.";
    }

    return TableFormatter.format(headers, rows);
  }

  /**
   * List all tables and views in a schema.
   *
   * @param metaData Database metadata
   * @param schema   Schema name
   * @return Formatted table of tables/views
   */
  public String listTables(DatabaseMetaData metaData, String schema) throws SQLException {
    List<String> headers = List.of("Table Name", "Type");
    List<List<String>> rows = new ArrayList<>();

    // Get both tables and views
    String[] types = {"TABLE", "VIEW"};
    try (ResultSet rs = metaData.getTables(null, schema, "%", types)) {
      while (rs.next()) {
        String tableName = rs.getString("TABLE_NAME");
        String tableType = rs.getString("TABLE_TYPE");
        rows.add(TableFormatter.row(tableName, tableType));
      }
    }

    if (rows.isEmpty()) {
      return "No tables found in schema: " + schema;
    }

    return TableFormatter.format(headers, rows);
  }

  /**
   * Get detailed table structure including columns, types, and constraints.
   *
   * @param metaData Database metadata
   * @param schema   Schema name
   * @param table    Table name
   * @return Formatted table of column details
   */
  public String describeTable(DatabaseMetaData metaData, String schema, String table) throws SQLException {
    // First check if the table exists
    boolean tableExists = false;
    try (ResultSet rs = metaData.getTables(null, schema, table, new String[]{"TABLE", "VIEW"})) {
      if (rs.next()) {
        tableExists = true;
      }
    }

    if (!tableExists) {
      // Return a friendly message instead of throwing an exception
      String tableName = (schema != null) ? schema + "." + table : table;
      return "Table not found: " + tableName;
    }

    // Get primary keys
    TreeSet<String> primaryKeys = getPrimaryKeys(metaData, schema, table);

    // Get foreign keys
    Map<String, String> foreignKeys = getForeignKeys(metaData, schema, table);

    // Get columns with constraints
    List<String> headers = List.of("Column Name", "Type", "Nullable", "Constraints");
    List<List<String>> rows = new ArrayList<>();

    try (ResultSet rs = metaData.getColumns(null, schema, table, "%")) {
      while (rs.next()) {
        String columnName = rs.getString("COLUMN_NAME");
        String columnType = rs.getString("TYPE_NAME");
        int columnSize = rs.getInt("COLUMN_SIZE");
        int nullable = rs.getInt("NULLABLE");

        // Format type with size if applicable
        String typeDisplay = columnType;
        if (columnSize > 0 && !columnType.equalsIgnoreCase("TEXT")
            && !columnType.equalsIgnoreCase("INTEGER")
            && !columnType.equalsIgnoreCase("BIGINT")) {
          typeDisplay = columnType + "(" + columnSize + ")";
        }

        // Nullable status
        String nullableDisplay = (nullable == DatabaseMetaData.columnNoNulls) ? "NOT NULL" : "NULL";

        // Constraints
        List<String> constraints = new ArrayList<>();
        if (primaryKeys.contains(columnName)) {
          constraints.add("PRIMARY KEY");
        }
        if (foreignKeys.containsKey(columnName)) {
          constraints.add("FOREIGN KEY -> " + foreignKeys.get(columnName));
        }

        String constraintsDisplay = constraints.isEmpty() ? "" : String.join(", ", constraints);

        rows.add(TableFormatter.row(columnName, typeDisplay, nullableDisplay, constraintsDisplay));
      }
    }

    if (rows.isEmpty()) {
      String tableName = (schema != null) ? schema + "." + table : table;
      return "Table not found: " + tableName;
    }

    return TableFormatter.format(headers, rows);
  }

  /**
   * Get primary keys for a table.
   */
  private TreeSet<String> getPrimaryKeys(DatabaseMetaData metaData, String schema, String table) throws SQLException {
    TreeSet<String> primaryKeys = new TreeSet<>();

    try (ResultSet rs = metaData.getPrimaryKeys(null, schema, table)) {
      while (rs.next()) {
        String columnName = rs.getString("COLUMN_NAME");
        primaryKeys.add(columnName);
      }
    }

    return primaryKeys;
  }

  /**
   * Get foreign keys for a table.
   * Returns a map of column name -> referenced table.column
   */
  private Map<String, String> getForeignKeys(DatabaseMetaData metaData, String schema, String table) throws SQLException {
    Map<String, String> foreignKeys = new LinkedHashMap<>();

    try (ResultSet rs = metaData.getImportedKeys(null, schema, table)) {
      while (rs.next()) {
        String columnName = rs.getString("FKCOLUMN_NAME");
        String referencedTable = rs.getString("PKTABLE_NAME");
        String referencedColumn = rs.getString("PKCOLUMN_NAME");

        foreignKeys.put(columnName, referencedTable + "." + referencedColumn);
      }
    }

    return foreignKeys;
  }
}
