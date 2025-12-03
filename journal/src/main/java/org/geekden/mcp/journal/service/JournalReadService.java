package org.geekden.mcp.journal.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.geekden.mcp.journal.config.JournalConfig;
import org.geekden.mcp.journal.model.JournalType;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Service for reading journal entries.
 */
@ApplicationScoped
public class JournalReadService {

  private static final Logger LOG = Logger.getLogger(JournalReadService.class);
  private static final int EXCERPT_LENGTH = 150;

  @Inject
  JournalConfig config;

  @Inject
  JournalFileService fileService;

  @Inject
  JournalSearchService searchService;

  @Inject
  EmbeddingService embeddingService;

  /**
   * Read a journal entry.
   *
   * @param path Path to the entry
   * @return Entry content
   * @throws IOException if reading fails
   */
  public String readEntry(Path path) throws IOException {
    return Files.readString(path);
  }

  /**
   * List recent entries.
   *
   * @param limit Maximum number of entries
   * @param type Type filter (project, user, or both)
   * @param days Number of days to look back
   * @return List of entry info (path, date, sections, excerpt)
   * @throws IOException if scanning fails
   */
  public List<EntryInfo> listRecentEntries(int limit, JournalType type, int days) throws IOException {
    List<EntryInfo> entries = new ArrayList<>();
    Instant cutoff = Instant.now().minus(days, ChronoUnit.DAYS);

    // Scan user path
    if (type == JournalType.BOTH || type == JournalType.USER) {
      entries.addAll(scanPath(config.getUserJournalPath(), JournalType.USER, cutoff));
    }

    // Sort by timestamp descending and limit
    return entries.stream()
        .sorted(Comparator.comparing(EntryInfo::timestamp).reversed())
        .limit(limit)
        .toList();
  }

  /**
   * Scan a path for recent entries.
   *
   * @throws IOException if scanning or reading entries fails
   */
  private List<EntryInfo> scanPath(Path journalPath, JournalType type, Instant cutoff) throws IOException {
    List<EntryInfo> entries = new ArrayList<>();

    if (!Files.exists(journalPath)) {
      return entries;
    }

    try (Stream<Path> paths = Files.walk(journalPath)) {
      List<Path> mdPaths = paths.filter(p -> p.toString().endsWith(".md")).toList();
      for (Path mdPath : mdPaths) {
        long timestamp = Files.getLastModifiedTime(mdPath).toInstant().toEpochMilli();
        if (Instant.ofEpochMilli(timestamp).isAfter(cutoff)) {
          String content = fileService.readEntry(mdPath);
          String excerpt = searchService.generateSimpleExcerpt(
              embeddingService.extractSearchableText(content), EXCERPT_LENGTH);

          entries.add(new EntryInfo(
              mdPath.toAbsolutePath().toString(),
              Instant.ofEpochMilli(timestamp),
              type,
              List.of(), // TODO: Extract sections from content
              excerpt,
              timestamp
          ));
        }
      }
    }

    return entries;
  }

  /**
   * Entry information for listings.
   */
  public record EntryInfo(String path, Instant date, JournalType type,
                          List<String> sections, String excerpt, long timestamp) {
  }
}
