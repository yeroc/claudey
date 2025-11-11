package org.geekden.mcp;

import io.agroal.api.AgroalDataSource;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.geekden.mcp.config.DatabaseConfig;
import org.geekden.mcp.service.IntrospectionService;
import org.jboss.logging.Logger;

import java.sql.Connection;
import java.sql.DatabaseMetaData;

/**
 * MCP Tools for database access.
 * Provides introspection and SQL execution capabilities via MCP protocol.
 */
@ApplicationScoped
public class DatabaseMcpTools {

  private static final Logger LOG = Logger.getLogger(DatabaseMcpTools.class);

  @Inject
  Instance<AgroalDataSource> dataSource;

  @Inject
  DatabaseConfig config;

  @Inject
  IntrospectionService introspectionService;

  /**
   * Hierarchical schema introspection tool.
   *
   * Behavior:
   * - introspect() → all schemas, tables, views
   * - introspect(schema="public") → tables/views in schema
   * - introspect(schema="public", table="users") → detailed table structure
   */
  @Tool(description = "Introspect database schema. Call with no args for all schemas, "
      + "with schema for tables in that schema, or with schema and table for table details.")
  public String introspect(
      @ToolArg(description = "Schema name (optional)", required = false) String schema,
      @ToolArg(description = "Table name (optional, requires schema)", required = false) String table) {

    try {
      if (!config.isConfigured()) {
        return "Error: Database not configured. Set DB_URL, DB_USERNAME, and DB_PASSWORD environment variables.";
      }

      try (Connection conn = dataSource.get().getConnection()) {
        DatabaseMetaData metaData = conn.getMetaData();

        if (schema == null) {
          // List all schemas
          return listSchemas(metaData);
        } else if (table == null) {
          // List tables in schema
          return listTables(metaData, schema);
        } else {
          // Show table details
          return describeTable(metaData, schema, table);
        }
      }
    } catch (Exception e) {
      LOG.error("Error during introspection", e);
      return "Error: " + e.getMessage();
    }
  }

  /**
   * Execute SQL query with pagination.
   *
   * Supports all SQL operations (SELECT, INSERT, UPDATE, DELETE, DDL).
   * SELECT queries are paginated automatically.
   */
  @Tool(description = "Execute SQL query with automatic pagination for SELECT statements. "
      + "Supports all SQL operations (SELECT, INSERT, UPDATE, DELETE, DDL).")
  public String executeSql(
      @ToolArg(description = "SQL query to execute") String query,
      @ToolArg(description = "Page number for paginated results (default: 1)",
               required = false, defaultValue = "1") int page) {

    try {
      if (!config.isConfigured()) {
        return "Error: Database not configured. Set DB_URL, DB_USERNAME, and DB_PASSWORD environment variables.";
      }

      if (page < 1) {
        return "Error: Page number must be >= 1";
      }

      // TODO: Implement in Phase 4
      return "SQL execution not yet implemented. Query: " + query + ", Page: " + page;

    } catch (Exception e) {
      LOG.error("Error executing SQL", e);
      return "Error: " + e.getMessage();
    }
  }

  private String listSchemas(DatabaseMetaData metaData) {
    try {
      return introspectionService.listSchemas(metaData);
    } catch (Exception e) {
      LOG.error("Error listing schemas", e);
      return "Error listing schemas: " + e.getMessage();
    }
  }

  private String listTables(DatabaseMetaData metaData, String schema) {
    try {
      return introspectionService.listTables(metaData, schema);
    } catch (Exception e) {
      LOG.error("Error listing tables in schema: " + schema, e);
      return "Error listing tables in schema " + schema + ": " + e.getMessage();
    }
  }

  private String describeTable(DatabaseMetaData metaData, String schema, String table) {
    try {
      return introspectionService.describeTable(metaData, schema, table);
    } catch (Exception e) {
      LOG.error("Error describing table: " + schema + "." + table, e);
      return "Error describing table " + schema + "." + table + ": " + e.getMessage();
    }
  }
}
