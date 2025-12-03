package org.geekden.mcp.journal.model;

import java.nio.file.Path;

/**
 * Represents a search result: a relevance score and the matching journal entry.
 */
public class SearchResult {

  private final double score;
  private final JournalEntry entry;
  private final Path path;

  public SearchResult(double score, JournalEntry entry, Path path) {
    this.score = score;
    this.entry = entry;
    this.path = path;
  }

  public double getScore() {
    return score;
  }

  public JournalEntry getEntry() {
    return entry;
  }

  public Path getPath() {
    return path;
  }
}
