package org.geekden.mcp.journal.model;

import java.time.Instant;
import java.util.List;

/**
 * Search options for journal queries.
 */
public class SearchOptions {

  private final String query;
  private final int limit;
  private final JournalType type;
  private final List<JournalSection> sections;
  private final Instant after;
  private final Instant before;

  public SearchOptions(String query, int limit, JournalType type, List<JournalSection> sections, Instant after, Instant before) {
    this.query = query;
    this.limit = limit;
    this.type = type;
    this.sections = sections;
    this.after = after;
    this.before = before;
  }

  public String getQuery() {
    return query;
  }

  public int getLimit() {
    return limit;
  }

  public JournalType getType() {
    return type;
  }

  public List<JournalSection> getSections() {
    return sections;
  }

  public Instant getAfter() {
    return after;
  }

  public Instant getBefore() {
    return before;
  }
}
