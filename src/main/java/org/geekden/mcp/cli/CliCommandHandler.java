package org.geekden.mcp.cli;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import picocli.CommandLine;

import java.io.PrintWriter;
import java.io.Writer;
import java.io.IOException;

/**
 * CLI command handler that delegates to Picocli commands.
 *
 * This class provides a compatibility layer for tests while using
 * Picocli for actual command parsing and execution.
 *
 * Usage:
 *   introspect                    # List all schemas/tables
 *   introspect <schema>           # List tables in schema
 *   introspect <schema> <table>   # Show table structure
 *   query "<sql>"                 # Execute SQL query (page 1)
 *   query "<sql>" --page <n>      # Execute SQL query with pagination
 */
@ApplicationScoped
public class CliCommandHandler {

  @Inject
  CommandLine.IFactory factory;

  @Inject
  OutputWriter output;

  /**
   * Execute CLI command using Picocli and return exit code.
   *
   * @param args Command line arguments (subcommand and options)
   * @return Exit code (0 for success, 1 for error)
   */
  public int execute(String[] args) {
    CommandLine cmd = new CommandLine(DatabaseCliCommand.class, factory);

    // Configure Picocli to use our custom OutputWriter
    PrintWriter out = new PrintWriter(new OutputStreamWriter(output, false), true);
    PrintWriter err = new PrintWriter(new OutputStreamWriter(output, true), true);

    cmd.setOut(out);
    cmd.setErr(err);

    int exitCode = cmd.execute(args);

    // Flush to ensure all output is written
    out.flush();
    err.flush();

    // Normalize Picocli exit codes to maintain backward compatibility:
    // - Exit code 0: success
    // - Exit code 99: MCP server mode (treat as error in test context)
    // - All other codes: normalize to 1 for error
    if (exitCode == 0) {
      return 0;
    }
    return 1;  // All errors (including exit code 99) become 1
  }

  /**
   * Custom Writer that delegates to OutputWriter.
   */
  private static class OutputStreamWriter extends Writer {
    private final OutputWriter output;
    private final boolean isError;
    private final StringBuilder buffer = new StringBuilder();

    OutputStreamWriter(OutputWriter output, boolean isError) {
      this.output = output;
      this.isError = isError;
    }

    @Override
    public void write(char[] cbuf, int off, int len) throws IOException {
      buffer.append(cbuf, off, len);
    }

    @Override
    public void flush() throws IOException {
      if (buffer.length() > 0) {
        String content = buffer.toString();
        // Remove trailing newline if present, as printOut/printErr add their own
        if (content.endsWith("\n")) {
          content = content.substring(0, content.length() - 1);
        }
        if (!content.isEmpty()) {
          if (isError) {
            output.printErr(content);
          } else {
            output.printOut(content);
          }
        }
        buffer.setLength(0);
      }
    }

    @Override
    public void close() throws IOException {
      flush();
    }
  }
}
