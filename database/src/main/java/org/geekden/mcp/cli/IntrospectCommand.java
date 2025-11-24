package org.geekden.mcp.cli;

import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.geekden.mcp.database.config.DatabaseConfig;
import org.geekden.mcp.database.service.IntrospectionService;
import org.jboss.logging.Logger;
import picocli.CommandLine;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.util.concurrent.Callable;

/**
 * Introspect command for database schema exploration.
 *
 * Usage:
 *   introspect                    # List all schemas/tables
 *   introspect <schema>           # List tables in schema
 *   introspect <schema> <table>   # Show table structure
 */
@CommandLine.Command(
  name = "introspect",
  description = "Introspect database schema",
  mixinStandardHelpOptions = true
)
public class IntrospectCommand implements Callable<Integer> {

  private static final Logger LOG = Logger.getLogger(IntrospectCommand.class);

  @CommandLine.Parameters(
    index = "0",
    arity = "0..1",
    description = "Schema name (optional)"
  )
  String schema;

  @CommandLine.Parameters(
    index = "1",
    arity = "0..1",
    description = "Table name (optional, requires schema)"
  )
  String table;

  @Inject
  Instance<Connection> connection;

  @Inject
  DatabaseConfig config;

  @Inject
  IntrospectionService introspectionService;

  @Inject
  OutputWriter output;

  @Override
  public Integer call() {
    // Check database configuration
    if (!config.isConfigured()) {
      output.printErr("Error: Database not configured.");
      output.printErr("Set DB_URL, DB_USERNAME, and DB_PASSWORD environment variables.");
      return 1;
    }

    try (Connection conn = connection.get()) {
      DatabaseMetaData metaData = conn.getMetaData();

      String result;
      if (table != null) {
        // introspect <schema> <table>
        if (schema == null) {
          output.printErr("Error: Schema name is required when table name is specified");
          return 1;
        }
        result = introspectionService.describeTable(metaData, schema, table);
      } else if (schema != null) {
        // introspect <schema>
        result = introspectionService.listTables(metaData, schema);
      } else {
        // introspect
        result = introspectionService.listSchemas(metaData);
      }

      output.printOut(result);
      return 0;
    } catch (Exception e) {
      output.printErr("Introspection failed: " + e.getMessage());
      LOG.error("Introspection error", e);
      return 1;
    }
  }
}
