package org.geekden.mcp.cli;

/**
 * Abstraction for CLI output to allow different implementations
 * for production (FileDescriptor) and testing (capturing).
 * <p>
 * Production implementation writes directly to FileDescriptor.out/err
 * to bypass MCP stdio extension's capture of System.out/err.
 * <p>
 * Test implementation captures output to StringBuilders for verification
 * and avoids corrupting Surefire's communication channel.
 */
public interface OutputWriter {

  /**
   * Write a message to standard output.
   *
   * @param message The message to write
   */
  void printOut(String message);

  /**
   * Write a message to standard error.
   *
   * @param message The message to write
   */
  void printErr(String message);
}
