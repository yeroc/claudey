package org.geekden.mcp.journal.model;

import java.time.Instant;
import java.util.Map;

/**
 * Represents a journal entry with YAML frontmatter and section content.
 */
public class JournalEntry {

  private final String title;
  private final Instant date;
  private final long timestamp;
  private final Map<JournalSection, String> sections;

  public JournalEntry(String title, Instant date, long timestamp, Map<JournalSection, String> sections) {
    this.title = title;
    this.date = date;
    this.timestamp = timestamp;
    this.sections = sections;
  }

  public String getTitle() {
    return title;
  }

  public Instant getDate() {
    return date;
  }

  public long getTimestamp() {
    return timestamp;
  }

  public Map<JournalSection, String> getSections() {
    return sections;
  }
}
