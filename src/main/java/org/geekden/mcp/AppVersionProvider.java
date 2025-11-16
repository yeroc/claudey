package org.geekden.mcp;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import picocli.CommandLine.IVersionProvider;

/**
 * Provides dynamic version information for picocli commands.
 * Reads application name and version from Quarkus configuration.
 */
@ApplicationScoped
public class AppVersionProvider implements IVersionProvider {

  @ConfigProperty(name = "quarkus.application.name")
  String appName;

  @ConfigProperty(name = "quarkus.application.version")
  String appVersion;

  @Override
  public String[] getVersion() {
    return new String[] { appName + " " + appVersion };
  }
}
