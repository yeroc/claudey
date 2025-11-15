package org.geekden;

import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import io.quarkiverse.mcp.server.stdio.runtime.StdioMcpMessageHandler;
import jakarta.inject.Inject;
import org.geekden.mcp.cli.DatabaseCliCommand;
import org.jboss.logging.Logger;
import picocli.CommandLine;

import java.util.Arrays;

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
public class MainApplication implements QuarkusApplication {

  static {
    // Set LogManager before any JUL access (must be in static block)
    System.setProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager");
  }

  private static final Logger LOG = Logger.getLogger(MainApplication.class);

  @Inject
  CommandLine.IFactory factory;

  @Inject
  StdioMcpMessageHandler mcpHandler;

  @Override
  public int run(String... args) throws Exception {
    LOG.info("MCP Database Server starting...");
    LOG.debug("Arguments: " + Arrays.toString(args));

    // Always use Picocli for command parsing
    // - No args: DatabaseCliCommand.call() returns 99 -> MCP server mode
    // - Subcommands (introspect, query): Execute CLI operation and return exit code
    CommandLine cmd = new CommandLine(DatabaseCliCommand.class, factory);
    int exitCode = cmd.execute(args);

    // Exit code 99 is special: indicates we should run MCP server mode
    if (exitCode == 99) {
      LOG.info("Running in MCP server mode (stdio)");
      LOG.info("Initializing MCP stdio server...");

      mcpHandler.initialize(System.out);

      LOG.info("Server ready - waiting for MCP client connections via stdio");

      // Keep the application running for MCP server mode
      Quarkus.waitForExit();
      return 0;
    }

    LOG.info("Application complete with exit code: " + exitCode);
    return exitCode;
  }

  public static void main(String[] args) {
    Quarkus.run(MainApplication.class, (exitCode, exception) -> {
      // Ensure proper exit code propagation for CLI mode
      System.exit(exitCode);
    }, args);
  }
}
