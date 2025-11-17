package org.geekden.mcp;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.interceptor.Interceptor;
import org.geekden.mcp.service.DatabaseUrlProvider;

/**
 * Test-specific implementation of DatabaseUrlProvider.
 * <p>
 * With @TestProfile, each test class runs in its own Quarkus instance,
 * so we no longer need dynamic URL transformation. This is just a no-op
 * pass-through that exists to satisfy the Alternative priority system.
 */
@Alternative
@Priority(Interceptor.Priority.APPLICATION + 100)
@ApplicationScoped
public class TestDatabaseUrlProvider implements DatabaseUrlProvider {

  @Override
  public String transformUrl(String originalUrl) {
    // No transformation needed - @TestProfile provides isolation
    return originalUrl;
  }
}
