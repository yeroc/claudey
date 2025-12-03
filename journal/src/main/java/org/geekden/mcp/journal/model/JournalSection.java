package org.geekden.mcp.journal.model;

import java.util.Arrays;
import java.util.Objects;

/**
 * Enumeration of journal entry sections.
 * Each section has an MCP key (used in tool arguments) and a display name (used in markdown headers).
 */
public enum JournalSection {
  FEELINGS("feelings", "Feelings"),
  PROJECT_NOTES("project_notes", "Project Notes"),
  USER_CONTEXT("user_context", "User Context"),
  TECHNICAL_INSIGHTS("technical_insights", "Technical Insights"),
  WORLD_KNOWLEDGE("world_knowledge", "World Knowledge");

  private final String mcpKey;
  private final String displayName;

  JournalSection(String mcpKey, String displayName) {
    this.mcpKey = mcpKey;
    this.displayName = displayName;
  }

  /**
   * Get the MCP key used in tool arguments (e.g., "technical_insights").
   */
  public String getMcpKey() {
    return mcpKey;
  }

  /**
   * Get the display name used in markdown headers (e.g., "Technical Insights").
   */
  public String getDisplayName() {
    return displayName;
  }

  /**
   * Parse a section from its MCP key.
   * 
   * @param mcpKey The MCP key (e.g., "technical_insights")
   * @return The corresponding JournalSection
   * @throws IllegalArgumentException if the MCP key is not recognized
   * @throws NullPointerException if mcpKey is null
   */
  public static JournalSection fromMcpKey(String mcpKey) {
    Objects.requireNonNull(mcpKey, "MCP key cannot be null");
    return Arrays.stream(values())
        .filter(section -> section.mcpKey.equalsIgnoreCase(mcpKey.trim()))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Unknown MCP key: " + mcpKey));
  }

  /**
   * Parse a section from its display name.
   * 
   * @param displayName The display name (e.g., "Technical Insights")
   * @return The corresponding JournalSection
   * @throws IllegalArgumentException if the display name is not recognized
   * @throws NullPointerException if displayName is null
   */
  public static JournalSection fromDisplayName(String displayName) {
    Objects.requireNonNull(displayName, "Display name cannot be null");
    return Arrays.stream(values())
        .filter(section -> section.displayName.equalsIgnoreCase(displayName.trim()))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Unknown display name: " + displayName));
  }
}
