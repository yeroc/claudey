package org.geekden.mcp.cli;

import io.agroal.api.AgroalDataSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.geekden.mcp.config.DatabaseConfig;
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
 */
@ApplicationScoped
public class CliCommandHandler {

  private static final Logger LOG = Logger.getLogger(CliCommandHandler.class);

  @Inject
  Instance<AgroalDataSource> dataSource;

  @Inject
  DatabaseConfig config;

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
        System.err.println("Unknown command: " + command);
        printUsage();
        return 1;
      }

      // Check database configuration
      if (!config.isConfigured()) {
        System.err.println("Error: Database not configured.");
        System.err.println("Set DB_URL, DB_USERNAME, and DB_PASSWORD environment variables.");
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
      System.err.println("Error: " + e.getMessage());
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
      System.err.println("Invalid arguments for introspect command");
      printUsage();
      return 1;
    }

    try (Connection conn = dataSource.get().getConnection()) {
      DatabaseMetaData metaData = conn.getMetaData();

      if (args.length == 1) {
        // List all schemas
        System.out.println("Listing all schemas...");
        System.out.println("(Implementation pending - Phase 3)");
      } else if (args.length == 2) {
        // List tables in schema
        String schema = args[1];
        System.out.println("Listing tables in schema: " + schema);
        System.out.println("(Implementation pending - Phase 3)");
      } else if (args.length == 3) {
        // Describe table
        String schema = args[1];
        String table = args[2];
        System.out.println("Describing table: " + schema + "." + table);
        System.out.println("(Implementation pending - Phase 3)");
      }

      return 0;
    } catch (Exception e) {
      System.err.println("Introspection failed: " + e.getMessage());
      LOG.error("Introspection error", e);
      return 1;
    }
  }

  private int handleQuery(String[] args) {
    if (args.length < 2) {
      System.err.println("Missing SQL query");
      printUsage();
      return 1;
    }

    String query = args[1];
    int page = 1;

    // Parse --page argument
    for (int i = 2; i < args.length; i++) {
      if ("--page".equals(args[i])) {
        if (i + 1 >= args.length) {
          System.err.println("Missing value for --page");
          return 1;
        }
        try {
          page = Integer.parseInt(args[i + 1]);
        } catch (NumberFormatException e) {
          System.err.println("Invalid page number: " + args[i + 1]);
          return 1;
        }
        break;
      }
    }

    System.out.println("Executing query (page " + page + "): " + query);
    System.out.println("(Implementation pending - Phase 4)");

    return 0;
  }

  private void printUsage() {
    System.err.println("Usage:");
    System.err.println("  --cli introspect                    # List all schemas/tables");
    System.err.println("  --cli introspect <schema>           # List tables in schema");
    System.err.println("  --cli introspect <schema> <table>   # Show table structure");
    System.err.println("  --cli query \"<sql>\"                 # Execute SQL query (page 1)");
    System.err.println("  --cli query \"<sql>\" --page <n>      # Execute SQL query with pagination");
  }
}
