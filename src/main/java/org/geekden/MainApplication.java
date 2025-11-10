package org.geekden;

import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import jakarta.inject.Inject;
import org.geekden.mcp.cli.CliCommandHandler;
import org.jboss.logging.Logger;

import java.util.Arrays;

/**
 * MCP Database Server - Main Application Entry Point
 *
 * Modes:
 * 1. MCP Server (default): Runs as stdio MCP server for AI agents
 * 2. CLI Mode (--cli flag): One-shot database commands for testing
 *
 * CLI Usage:
 *   ./app --cli introspect
 *   ./app --cli introspect public
 *   ./app --cli introspect public users
 *   ./app --cli query "SELECT * FROM users"
 *   ./app --cli query "SELECT * FROM users" --page 2
 */
@QuarkusMain
public class MainApplication implements QuarkusApplication {

  private static final Logger LOG = Logger.getLogger(MainApplication.class);

  @Inject
  CliCommandHandler cliHandler;

  @Override
  public int run(String... args) throws Exception {
    LOG.info("MCP Database Server starting...");
    LOG.debug("Arguments: " + Arrays.toString(args));

    // Check for CLI mode
    if (args.length > 0 && "--cli".equals(args[0])) {
      LOG.info("Running in CLI mode");
      // Remove --cli from args and pass the rest to CLI handler
      String[] cliArgs = Arrays.copyOfRange(args, 1, args.length);
      int exitCode = cliHandler.execute(cliArgs);
      // Signal Quarkus to exit with the proper exit code
      Quarkus.asyncExit(exitCode);
      return exitCode;
    }

    // Default: Run as MCP stdio server
    // The Quarkiverse MCP extension automatically handles stdio server lifecycle
    LOG.info("Running in MCP server mode (stdio)");
    LOG.info("Server ready - waiting for MCP client connections via stdio");

    // Keep the application running for MCP server mode
    Quarkus.waitForExit();
    return 0;
  }

  public static void main(String[] args) {
    Quarkus.run(MainApplication.class, (exitCode, exception) -> {
      // Ensure proper exit code propagation for CLI mode
      System.exit(exitCode);
    }, args);
  }
}
