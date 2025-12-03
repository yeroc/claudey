package org.geekden.mcp.journal.config;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

/**
 * Configuration for journal storage paths.
 * <p>
 * User journal is stored in ~/.private-journal/ by default, or a configured path.
 * Can be namespaced by agent name (e.g., ~/.private-journal/claude/).
 */
@ApplicationScoped
public class JournalConfig {

  @ConfigProperty(name = "journal.path.user")
  Optional<String> userPath;

  @ConfigProperty(name = "journal.agent.name")
  Optional<String> agentName;

  /**
   * Get the user journal path.
   * 
   * @return Path to user journal directory
   * @throws IOException if home directory is not accessible or writable
   */
  public Path getUserJournalPath() throws IOException {
    Path basePath = resolveBasePath();
    
    // Append agent name if configured
    if (agentName.isPresent()) {
      return basePath.resolve(agentName.get());
    }
    return basePath;
  }

  private Path resolveBasePath() throws IOException {
    if (userPath.isPresent()) {
      return resolveConfiguredPath(userPath.get());
    }
    return resolveDefaultPath();
  }

  private Path resolveConfiguredPath(String configuredPath) throws IOException {
    Path path = Paths.get(configuredPath);
    if (!path.isAbsolute()) {
      Path home = getHomeDirectory();
      path = home.resolve(configuredPath);
    }
    return path;
  }

  private Path resolveDefaultPath() throws IOException {
    Path home = getHomeDirectory();
    return home.resolve(".private-journal");
  }

  private Path getHomeDirectory() throws IOException {
    String homeProperty = System.getProperty("user.home");
    if (homeProperty == null || homeProperty.isEmpty()) {
      throw new IOException("user.home system property is not set");
    }
    
    Path home = Paths.get(homeProperty);
    if (!Files.exists(home)) {
      throw new IOException("Home directory does not exist: " + home);
    }
    if (!Files.isWritable(home)) {
      throw new IOException("Home directory is not writable: " + home);
    }
    
    return home;
  }

  /**
   * Determine which journal path to use based on section name.
   * 
   * @param section Section name
   * @return Path to use for this section
   * @throws IOException if path cannot be determined
   */
  public Path getPathForSection(String section) throws IOException {
    return getUserJournalPath();
  }
}
