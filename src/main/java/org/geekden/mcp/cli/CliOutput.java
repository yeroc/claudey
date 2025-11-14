package org.geekden.mcp.cli;

import jakarta.enterprise.context.ApplicationScoped;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/**
 * Output abstraction for CLI commands.
 * <p>
 * Writes directly to FileDescriptor.out/err to bypass MCP stdio extension's
 * capture of System.out/err for JSON-RPC protocol.
 */
@ApplicationScoped
public class CliOutput {

  private final PrintStream stdout;
  private final PrintStream stderr;

  public CliOutput() {
    this.stdout = new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8);
    this.stderr = new PrintStream(new FileOutputStream(FileDescriptor.err), true, StandardCharsets.UTF_8);
  }

  public void println(String message) {
    stdout.println(message);
  }

  public void printError(String message) {
    stderr.println(message);
  }
}

