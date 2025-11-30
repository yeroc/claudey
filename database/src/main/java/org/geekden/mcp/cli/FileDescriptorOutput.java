package org.geekden.mcp.cli;

import jakarta.enterprise.context.ApplicationScoped;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/**
 * Production implementation of OutputWriter for CLI commands.
 * <p>
 * Writes directly to FileDescriptor.out/err to bypass MCP stdio extension's
 * capture of System.out/err for JSON-RPC protocol.
 * <p>
 * During tests, this implementation is automatically replaced by CapturingOutput
 * via CDI @Alternative mechanism to prevent corrupting Surefire's communication channel.
 */
@ApplicationScoped
public class FileDescriptorOutput implements OutputWriter {

  private final PrintStream stdout;
  private final PrintStream stderr;

  public FileDescriptorOutput() {
    this.stdout = new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8);
    this.stderr = new PrintStream(new FileOutputStream(FileDescriptor.err), true, StandardCharsets.UTF_8);
  }

  @Override
  public void printOut(String message) {
    stdout.println(message);
  }

  @Override
  public void printErr(String message) {
    stderr.println(message);
  }
}

