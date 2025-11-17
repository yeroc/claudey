package org.geekden.mcp.service;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Production implementation of DatabaseUrlProvider.
 * <p>
 * Simply returns the URL unchanged - no transformation needed in production.
 */
@ApplicationScoped
public class ProductionDatabaseUrlProvider implements DatabaseUrlProvider {

  @Override
  public String transformUrl(String originalUrl) {
    return originalUrl;
  }
}
