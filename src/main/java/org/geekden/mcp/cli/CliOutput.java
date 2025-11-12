package org.geekden.mcp.cli;

import jakarta.enterprise.context.ApplicationScoped;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/**
 * Output abstraction for CLI commands.
 * <p>
 * In production, writes directly to FileDescriptor.out/err to bypass
 * MCP stdio extension's capture of System.out/err.
 * <p>
 * In tests, can be replaced with a mock/spy for verification.
 */
@ApplicationScoped
public class CliOutput {

  // Direct access to stdout/stderr, bypassing System.out/err which MCP stdio extension captures
  private final PrintStream stdout;
  private final PrintStream stderr;

  public CliOutput() {
    // Use FileDescriptor directly to bypass MCP stdio redirection
    this.stdout = new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8);
    this.stderr = new PrintStream(new FileOutputStream(FileDescriptor.err), true, StandardCharsets.UTF_8);
  }

  /**
   * Constructor for tests to inject standard System.out/err
   */
  CliOutput(PrintStream stdout, PrintStream stderr) {
    this.stdout = stdout;
    this.stderr = stderr;
  }

  public void println(String message) {
    stdout.println(message);
  }

  public void printError(String message) {
    stderr.println(message);
  }
}
