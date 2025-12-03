package org.geekden.mcp.journal.formatter;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.geekden.mcp.journal.model.JournalSection;
import org.geekden.mcp.journal.model.SearchResult;
import org.geekden.mcp.journal.service.EmbeddingService;
import org.geekden.mcp.journal.service.JournalReadService;
import org.geekden.mcp.journal.service.JournalSearchService;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Formatter for journal output.
 */
@ApplicationScoped
public class JournalFormatter {

  private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a");

  @Inject
  EmbeddingService embeddingService;

  @Inject
  JournalSearchService searchService;

  /**
   * Format search results.
   */
  public String formatSearchResults(List<SearchResult> results, String query) {
    if (results.isEmpty()) {
      return "No relevant entries found.";
    }

    StringBuilder sb = new StringBuilder();
    sb.append("Found ").append(results.size()).append(" relevant entries:\n\n");

    for (int i = 0; i < results.size(); i++) {
      SearchResult result = results.get(i);
      sb.append(i + 1).append(". [Score: ").append(String.format("%.3f", result.getScore())).append("] ");
      sb.append(formatDate(result.getEntry().getDate())).append("\n");
      
      // Format sections from entry
      if (!result.getEntry().getSections().isEmpty()) {
        String sectionNames = result.getEntry().getSections().keySet().stream()
            .map(JournalSection::getDisplayName)
            .collect(java.util.stream.Collectors.joining(", "));
        sb.append("   Sections: ").append(sectionNames).append("\n");
      }
      
      sb.append("   Path: ").append(result.getPath()).append("\n");
      
      // Generate excerpt from path by reading the file
      try {
        String content = java.nio.file.Files.readString(result.getPath());
        String cleanedText = embeddingService.extractSearchableText(content);
        String excerpt = searchService.generateExcerpt(cleanedText, query);
        sb.append("   Excerpt: ").append(excerpt).append("\n\n");
      } catch (Exception e) {
        sb.append("   Excerpt: [Unable to load]\n\n");
      }
    }

    return sb.toString();
  }

  /**
   * Format recent entries list.
   */
  public String formatRecentEntries(List<JournalReadService.EntryInfo> entries, int days) {
    if (entries.isEmpty()) {
      return "No entries found in the last " + days + " days.";
    }

    StringBuilder sb = new StringBuilder();
    sb.append("Recent entries (last ").append(days).append(" days):\n\n");

    for (int i = 0; i < entries.size(); i++) {
      JournalReadService.EntryInfo entry = entries.get(i);
      sb.append(i + 1).append(". ").append(formatDate(entry.date())).append(" (").append(entry.type().getValue()).append(")\n");
      if (!entry.sections().isEmpty()) {
        sb.append("   Sections: ").append(String.join(", ", entry.sections())).append("\n");
      }
      sb.append("   Path: ").append(entry.path()).append("\n");
      sb.append("   Excerpt: ").append(entry.excerpt()).append("\n\n");
    }

    return sb.toString();
  }

  private String formatDate(java.time.Instant instant) {
    return DATE_FORMAT.format(instant.atZone(ZoneId.systemDefault()));
  }
}
