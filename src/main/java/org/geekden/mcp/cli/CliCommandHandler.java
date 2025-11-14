package org.geekden.mcp.cli;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.geekden.mcp.config.DatabaseConfig;
import org.geekden.mcp.service.IntrospectionService;
import org.geekden.mcp.service.SqlExecutionService;
import org.jboss.logging.Logger;

import java.sql.Connection;
import java.sql.DatabaseMetaData;

/**
 * CLI command handler for one-shot database operations.
 *
 * Usage:
 *   --cli introspect                    # List all schemas/tables
 *   --cli introspect <schema>           # List tables in schema
 *   --cli introspect <schema> <table>   # Show table structure
 *   --cli query "<sql>"                 # Execute SQL query (page 1)
 *   --cli query "<sql>" --page <n>      # Execute SQL query with pagination
 *
 * Note: Uses CliOutput to write to FileDescriptor.out directly to bypass MCP stdio extension's stdout capture
 */
@ApplicationScoped
public class CliCommandHandler {

  private static final Logger LOG = Logger.getLogger(CliCommandHandler.class);

  @Inject
  Instance<Connection> connection;

  @Inject
  DatabaseConfig config;

  @Inject
  IntrospectionService introspectionService;

  @Inject
  SqlExecutionService sqlExecutionService;

  @Inject
  OutputWriter output;

  /**
   * Execute CLI command and return exit code.
   *
   * @param args Command line arguments (excluding --cli)
   * @return Exit code (0 for success, 1 for error)
   */
  public int execute(String[] args) {
    try {
      // Check for arguments
      if (args.length == 0) {
        printUsage();
        return 1;
      }

      String command = args[0];

      // Check command is recognized
      if (!isValidCommand(command)) {
        output.printErr("Unknown command: " + command);
        printUsage();
        return 1;
      }

      // Check database configuration
      if (!config.isConfigured()) {
        output.printErr("Error: Database not configured.");
        output.printErr("Set DB_URL, DB_USERNAME, and DB_PASSWORD environment variables.");
        return 1;
      }

      // Execute command
      switch (command) {
        case "introspect":
          return handleIntrospect(args);
        case "query":
          return handleQuery(args);
        default:
          // Should never reach here
          return 1;
      }
    } catch (Exception e) {
      output.printErr("Error: " + e.getMessage());
      LOG.error("CLI command failed", e);
      return 1;
    }
  }

  private boolean isValidCommand(String command) {
    return "introspect".equals(command) || "query".equals(command);
  }

  private int handleIntrospect(String[] args) {
    // Validate arguments
    if (args.length > 3) {
      output.printErr("Invalid arguments for introspect command");
      printUsage();
      return 1;
    }

    try (Connection conn = connection.get()) {
      DatabaseMetaData metaData = conn.getMetaData();

      String result;
      if (args.length == 1) {
        result = introspectionService.listSchemas(metaData);
      } else if (args.length == 2) {
        String schema = args[1];
        result = introspectionService.listTables(metaData, schema);
      } else if (args.length == 3) {
        String schema = args[1];
        String table = args[2];
        result = introspectionService.describeTable(metaData, schema, table);
      } else {
        output.printErr("Invalid arguments for introspect command");
        printUsage();
        return 1;
      }

      output.printOut(result);
      return 0;
    } catch (Exception e) {
      output.printErr("Introspection failed: " + e.getMessage());
      LOG.error("Introspection error", e);
      return 1;
    }
  }

  private int handleQuery(String[] args) {
    if (args.length < 2) {
      output.printErr("Missing SQL query");
      printUsage();
      return 1;
    }

    String query = args[1];
    int page = 1;

    // Parse --page argument
    for (int i = 2; i < args.length; i++) {
      if ("--page".equals(args[i])) {
        if (i + 1 >= args.length) {
          output.printErr("Missing value for --page");
          return 1;
        }
        try {
          page = Integer.parseInt(args[i + 1]);
        } catch (NumberFormatException e) {
          output.printErr("Invalid page number: " + args[i + 1]);
          return 1;
        }
        break;
      }
    }

    // Validate page number
    if (page < 1) {
      output.printErr("Page number must be >= 1");
      return 1;
    }

    try (Connection conn = connection.get()) {
      String result = sqlExecutionService.executeQuery(conn, query, page, config.getPageSize());
      output.printOut(result);
      return 0;
    } catch (Exception e) {
      output.printErr("Query execution failed: " + e.getMessage());
      LOG.error("Query execution error", e);
      return 1;
    }
  }

  private void printUsage() {
    output.printErr("Usage:");
    output.printErr("  --cli introspect                    # List all schemas/tables");
    output.printErr("  --cli introspect <schema>           # List tables in schema");
    output.printErr("  --cli introspect <schema> <table>   # Show table structure");
    output.printErr("  --cli query \"<sql>\"                 # Execute SQL query (page 1)");
    output.printErr("  --cli query \"<sql>\" --page <n>      # Execute SQL query with pagination");
  }
}
