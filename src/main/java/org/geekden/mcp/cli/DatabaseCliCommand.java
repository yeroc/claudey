package org.geekden.mcp.cli;

import picocli.CommandLine;

import java.util.concurrent.Callable;

/**
 * Top-level CLI command for MCP Database Server.
 *
 * Serves as the entry point for all CLI operations with subcommands:
 * - introspect: Database schema introspection
 * - query: SQL query execution
 */
@CommandLine.Command(
  name = "mcp-database",
  mixinStandardHelpOptions = true,
  version = "1.0.0",
  description = "MCP Database Server CLI",
  subcommands = {
    IntrospectCommand.class,
    QueryCommand.class
  }
)
public class DatabaseCliCommand implements Callable<Integer> {

  @Override
  public Integer call() {
    // If no subcommand specified, show usage
    CommandLine.usage(this, System.err);
    return 1;
  }
}
