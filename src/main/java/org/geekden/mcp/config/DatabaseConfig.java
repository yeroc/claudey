package org.geekden.mcp.config;

import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Database configuration using MicroProfile Config.
 * Environment variables are mapped via application.properties.
 */
@ApplicationScoped
@RegisterForReflection
public class DatabaseConfig {

  @ConfigProperty(name = "quarkus.datasource.jdbc.url")
  String jdbcUrl;

  @ConfigProperty(name = "quarkus.datasource.username")
  String username;

  @ConfigProperty(name = "quarkus.datasource.password")
  String password;

  @ConfigProperty(name = "db.page-size", defaultValue = "100")
  int pageSize;

  public String getJdbcUrl() {
    return jdbcUrl;
  }

  public String getUsername() {
    return username;
  }

  public String getPassword() {
    return password;
  }

  public int getPageSize() {
    return pageSize;
  }

  public boolean isConfigured() {
    return jdbcUrl != null && !jdbcUrl.isEmpty();
  }
}
