package org.geekden;

import io.quarkus.picocli.runtime.annotations.TopCommand;
import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import io.quarkiverse.mcp.server.stdio.runtime.StdioMcpMessageHandler;
import jakarta.inject.Inject;
import org.geekden.mcp.cli.IntrospectCommand;
import org.geekden.mcp.cli.QueryCommand;
import org.jboss.logging.Logger;
import picocli.CommandLine;

/**
 * MCP Database Server - Main Application Entry Point
 *
 * Uses Picocli for all command-line argument processing:
 * - No arguments: Runs as stdio MCP server for AI agents (default)
 * - CLI subcommands: One-shot database commands for testing
 *
 * Usage:
 *   ./app                         # MCP server mode
 *   ./app introspect              # CLI: List schemas/tables
 *   ./app introspect public       # CLI: List tables in schema
 *   ./app introspect public users # CLI: Show table structure
 *   ./app query "SELECT * FROM t" # CLI: Execute query
 *   ./app query "..." --page 2    # CLI: Execute with pagination
 *   ./app --help                  # Show help
 */
@QuarkusMain
@TopCommand
@CommandLine.Command(
  name = "mcp-database",
  mixinStandardHelpOptions = true,
  version = "1.0.0",
  description = "MCP Database Server - runs as stdio server by default, or use CLI subcommands",
  subcommands = {
    IntrospectCommand.class,
    QueryCommand.class
  }
)
public class MainApplication implements Runnable, QuarkusApplication {

  static {
    // Set LogManager before any JUL access (must be in static block)
    System.setProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager");
  }

  private static final Logger LOG = Logger.getLogger(MainApplication.class);

  @Inject
  StdioMcpMessageHandler mcpHandler;

  /**
   * Called by Picocli when no subcommand is specified.
   * Starts the MCP stdio server.
   */
  @Override
  public void run() {
    LOG.info("Running in MCP server mode (stdio)");
    LOG.info("Initializing MCP stdio server...");

    mcpHandler.initialize(System.out);

    LOG.info("Server ready - waiting for MCP client connections via stdio");

    // Keep the application running for MCP server mode
    Quarkus.waitForExit();
  }

  /**
   * QuarkusApplication entry point.
   * With @TopCommand, Picocli handles all the command execution.
   */
  @Override
  public int run(String... args) {
    // Picocli will handle command execution
    // This method exists to satisfy QuarkusApplication interface
    return 0;
  }

  public static void main(String[] args) {
    Quarkus.run(MainApplication.class, args);
  }
}
