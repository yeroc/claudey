package org.geekden.mcp.cli;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.interceptor.Interceptor;

/**
 * Test-specific implementation of OutputWriter that captures output to StringBuilders.
 * <p>
 * This alternative implementation is active only during tests and prevents output
 * from being written to FileDescriptor.out/err, which would corrupt Surefire's
 * communication channel with the forked JVM.
 * <p>
 * Tests can inject this concrete type to access captured output via getStdout()
 * and getStderr() methods for verification.
 */
@Alternative
@Priority(Interceptor.Priority.APPLICATION + 100)
@ApplicationScoped
public class CapturingOutput implements OutputWriter {

  private final StringBuilder stdout = new StringBuilder();
  private final StringBuilder stderr = new StringBuilder();

  @Override
  public void printOut(String message) {
    stdout.append(message).append('\n');
  }

  @Override
  public void printErr(String message) {
    stderr.append(message).append('\n');
  }

  /**
   * Get all output written to stdout.
   *
   * @return Captured stdout content
   */
  public String getStdout() {
    return stdout.toString();
  }

  /**
   * Get all output written to stderr.
   *
   * @return Captured stderr content
   */
  public String getStderr() {
    return stderr.toString();
  }

  /**
   * Clear all captured output.
   * Should be called in @BeforeEach to ensure test isolation.
   */
  public void reset() {
    stdout.setLength(0);
    stderr.setLength(0);
  }
}
