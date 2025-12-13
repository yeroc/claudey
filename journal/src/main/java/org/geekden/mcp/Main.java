package org.geekden.mcp;

import io.quarkiverse.mcp.server.stdio.runtime.StdioMcpMessageHandler;
import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * MCP Journal Server
 * 
 * Runs as stdio MCP server for AI agents to record and search private journal entries.
 * 
 * Usage:
 *   ./app    # MCP server mode (stdio)
 */
@QuarkusMain
public class Main implements QuarkusApplication {

  static {
    // Set LogManager before any JUL access (must be in static block)
    System.setProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager");
  }

  private static final Logger LOG = Logger.getLogger(Main.class);

  @Inject
  StdioMcpMessageHandler mcpHandler;

  @ConfigProperty(name = "quarkus.application.name")
  String appName;

  /**
   * QuarkusApplication entry point.
   * Starts the MCP stdio server.
   */
  @Override
  public int run(String... args) throws Exception {
    if (args.length > 0) {
      System.out.println("Arguments not supported in stdio mode (yet).");
      return 1;
    }

    LOG.info("Initializing MCP (stdio) server...");

    mcpHandler.initialize(System.out);

    LOG.info("MCP (stdio) server ready.");

    // Keep the application running for MCP server mode
    Quarkus.waitForExit();
    
    return 0;
  }
}
