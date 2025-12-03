package org.geekden.mcp.journal.model;

/**
 * Enum representing journal storage types.
 */
public enum JournalType {
  /** User's personal journal */
  USER("user"),
  
  /** Project-specific journal (currently unused) */
  PROJECT("project"),
  
  /** Both user and project journals */
  BOTH("both");

  private final String value;

  JournalType(String value) {
    this.value = value;
  }

  public String getValue() {
    return value;
  }

  /**
   * Parse a journal type from a string value.
   * 
   * @param value String value (e.g., "user", "project", "both")
   * @return Corresponding JournalType
   * @throws IllegalArgumentException if value is invalid
   */
  public static JournalType fromValue(String value) {
    java.util.Objects.requireNonNull(value, "Journal type value cannot be null");
    
    for (JournalType type : values()) {
      if (type.value.equalsIgnoreCase(value.trim())) {
        return type;
      }
    }
    
    throw new IllegalArgumentException("Unknown journal type: " + value);
  }
}
