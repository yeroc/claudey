package org.geekden.mcp.database.config;

import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Optional;

/**
 * Database configuration using MicroProfile Config.
 * <p>
 * Reads from custom db.* properties, which are populated from user-facing
 * DB_URL/DB_USERNAME/DB_PASSWORD environment variables via application.properties mappings.
 */
@ApplicationScoped
@RegisterForReflection
public class DatabaseConfig {

  @ConfigProperty(name = "db.jdbc.url")
  Optional<String> jdbcUrl;

  @ConfigProperty(name = "db.username")
  Optional<String> username;

  @ConfigProperty(name = "db.password")
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
