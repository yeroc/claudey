package org.geekden.mcp.cli;

import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.geekden.mcp.database.config.DatabaseConfig;
import org.geekden.mcp.database.service.SqlExecutionService;
import org.jboss.logging.Logger;
import picocli.CommandLine;

import java.sql.Connection;
import java.util.concurrent.Callable;

/**
 * Query command for SQL execution.
 *
 * Usage:
 *   query "<sql>"                 # Execute SQL query (page 1)
 *   query "<sql>" --page <n>      # Execute SQL query with pagination
 */
@CommandLine.Command(
  name = "query",
  description = "Execute SQL query",
  mixinStandardHelpOptions = true
)
public class QueryCommand implements Callable<Integer> {

  private static final Logger LOG = Logger.getLogger(QueryCommand.class);

  @CommandLine.Parameters(
    index = "0",
    description = "SQL query to execute"
  )
  String sql;

  @CommandLine.Option(
    names = {"--page"},
    description = "Page number for result pagination (default: ${DEFAULT-VALUE})",
    defaultValue = "1"
  )
  int page;

  @Inject
  Instance<Connection> connection;

  @Inject
  DatabaseConfig config;

  @Inject
  SqlExecutionService sqlExecutionService;

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

    // Validate page number
    if (page < 1) {
      output.printErr("Page number must be >= 1");
      return 1;
    }

    try (Connection conn = connection.get()) {
      String result = sqlExecutionService.executeQuery(conn, sql, page, config.getPageSize());
      output.printOut(result);
      return 0;
    } catch (Exception e) {
      output.printErr("Query execution failed: " + e.getMessage());
      LOG.error("Query execution error", e);
      return 1;
    }
  }
}
