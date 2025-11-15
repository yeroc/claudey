package org.geekden.mcp.cli;

import picocli.CommandLine;

import java.util.concurrent.Callable;

/**
 * Top-level CLI command for MCP Database Server.
 *
 * When invoked with no subcommand, returns exit code 99 which MainApplication
 * interprets as "run MCP stdio server mode".
 *
 * When invoked with subcommands, provides CLI access to database operations:
 * - introspect: Database schema introspection
 * - query: SQL query execution
 */
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
public class DatabaseCliCommand implements Callable<Integer> {

  @Override
  public Integer call() {
    // If no subcommand specified, return special exit code 99
    // MainApplication will recognize this and start MCP server mode
    // (we can't call Quarkus.waitForExit() here as it would block tests)
    return 99;  // Special code for "run MCP server"
  }
}
