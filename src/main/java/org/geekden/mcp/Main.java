package org.geekden;

import io.quarkiverse.mcp.server.stdio.runtime.StdioMcpMessageHandler;
import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.geekden.mcp.cli.IntrospectCommand;
import org.geekden.mcp.cli.QueryCommand;
import org.jboss.logging.Logger;
import picocli.CommandLine;

/**
 * MCP Database Server
 *
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
@CommandLine.Command(
  mixinStandardHelpOptions = true,
  versionProvider = AppVersionProvider.class,
  description = "MCP Database Server - runs as stdio server by default, or use CLI subcommands",
  subcommands = { IntrospectCommand.class, QueryCommand.class }
)
public class Main implements Runnable, QuarkusApplication {

  static {
    // Set LogManager before any JUL access (must be in static block)
    System.setProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager");
  }

  private static final Logger LOG = Logger.getLogger(Main.class);

  @Inject
  CommandLine.IFactory factory;

  @Inject
  StdioMcpMessageHandler mcpHandler;

  @ConfigProperty(name = "quarkus.application.name")
  String appName;

  /**
   * Called by Picocli when no subcommand is specified.
   * Starts the MCP stdio server.
   */
  @Override
  public void run() {
    LOG.info("Initializing MCP (stdio) server...");

    mcpHandler.initialize(System.out);

    LOG.info("MCP (stdio) server ready.");

    // Keep the application running for MCP server mode
    Quarkus.waitForExit();
  }

  /**
   * QuarkusApplication entry point.
   * Creates CommandLine and delegates to Picocli for execution.
   */
  @Override
  public int run(String... args) throws Exception {
    return new CommandLine(this, factory).setCommandName(appName).execute(args);
  }
}
