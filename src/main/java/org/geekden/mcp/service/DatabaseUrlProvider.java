package org.geekden.mcp.service;

/**
 * Provider for database URL transformation.
 * <p>
 * Allows different implementations to modify the JDBC URL before
 * it's used to create connections. Production implementation is a no-op,
 * while test implementation can add test-specific isolation logic.
 */
public interface DatabaseUrlProvider {

  /**
   * Transform the original JDBC URL.
   * <p>
   * Production implementation returns the URL unchanged.
   * Test implementation may modify it for test isolation (e.g., SQLite per-test-class files).
   *
   * @param originalUrl the original JDBC URL from configuration
   * @return the transformed URL to use for connections
   */
  String transformUrl(String originalUrl);
}
