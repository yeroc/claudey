package org.geekden.mcp.config;

import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Optional;

/**
 * Database configuration using MicroProfile Config.
 * Environment variables are mapped via application.properties.
 */
@ApplicationScoped
@RegisterForReflection
public class DatabaseConfig {

  @ConfigProperty(name = "DB_URL")
  Optional<String> jdbcUrl;

  @ConfigProperty(name = "DB_USERNAME")
  Optional<String> username;

  @ConfigProperty(name = "DB_PASSWORD")
  Optional<String> password;

  @ConfigProperty(name = "db.page-size", defaultValue = "100")
  int pageSize;

  public Optional<String> getJdbcUrl() {
    return jdbcUrl;
  }

  public Optional<String> getUsername() {
    return username;
  }

  public Optional<String> getPassword() {
    return password;
  }

  public int getPageSize() {
    return pageSize;
  }

  public boolean isConfigured() {
    return jdbcUrl.isPresent() && !jdbcUrl.get().isEmpty();
  }
}
