package org.geekden.mcp.journal.service;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.geekden.mcp.journal.config.JournalConfig;
import org.geekden.mcp.journal.model.EmbeddingData;
import org.geekden.mcp.journal.model.JournalEntry;
import org.geekden.mcp.journal.model.JournalSection;
import org.geekden.mcp.journal.model.JournalType;
import org.geekden.mcp.journal.model.SearchOptions;
import org.geekden.mcp.journal.model.SearchResult;
import org.jboss.logging.Logger;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Service for searching journal entries using semantic search.
 */
@ApplicationScoped
public class JournalSearchService {

  private static final Logger LOG = Logger.getLogger(JournalSearchService.class);
  private static final double MIN_SCORE = 0.1;
  private static final int EXCERPT_LENGTH = 200;
  private static final int EXCERPT_STEP = 20;

  @Inject
  JournalConfig config;

  @Inject
  EmbeddingService embeddingService;

  @Inject
  EmbeddingFileService embeddingFileService;

  private InMemoryEmbeddingStore<TextSegment> embeddingStore;

  @PostConstruct
  public void initialize() {
    LOG.info("Initializing journal search service...");
    embeddingStore = new InMemoryEmbeddingStore<>();
    try {
      loadExistingEmbeddings();
      LOG.info("Journal search service initialized");
    } catch (IOException e) {
      LOG.errorf("Failed to load embeddings during initialization: %s", e.getMessage());
      LOG.warn("Search service initialized but embeddings could not be loaded");
    }
  }

  /**
   * Clear the embedding store.
   * Intended for testing purposes to ensure test isolation.
   * Tests can manually reload their own test data after clearing.
   */
  public void clearStore() {
    embeddingStore = new InMemoryEmbeddingStore<>();
  }

  /**
   * Load all existing embeddings from user path.
   *
   * @throws IOException if loading fails
   */
  private void loadExistingEmbeddings() throws IOException {
    int userCount = loadEmbeddingsFromPath(config.getUserJournalPath(), JournalType.USER);
    LOG.infof("Loaded %d user embeddings", userCount);
  }

  /**
   * Load embeddings from a specific path.
   *
   * @throws IOException if scanning embeddings fails
   */
  private int loadEmbeddingsFromPath(Path journalPath, JournalType type) throws IOException {
    List<EmbeddingData> embeddings = embeddingFileService.scanEmbeddings(journalPath);

    int count = 0;
    for (EmbeddingData data : embeddings) {
      try {
        Embedding embedding = new Embedding(data.getEmbedding());
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("timestamp", data.getTimestamp());
        metadata.put("type", type.getValue());
        metadata.put("sections", String.join(",", data.getSections()));
        metadata.put("text", data.getText());
        metadata.put("path", data.getPath().toString());

        TextSegment segment = TextSegment.from(data.getText(), dev.langchain4j.data.document.Metadata.from(metadata));
        embeddingStore.add(embedding, segment);
        count++;
      } catch (Exception e) {
        LOG.warnf("Failed to add embedding from %s to store: %s", data.getPath(), e.getMessage());
      }
    }

    return count;
  }

  /**
   * Search journal entries.
   * 
   * @param options Search options
   * @return List of search results
   */
  public List<SearchResult> search(SearchOptions options) {
    // Generate query embedding
    Embedding queryEmbedding = embeddingService.generateEmbedding(options.getQuery());

    // Build metadata filter (excluding sections, which we'll filter post-search)
    Filter filter = buildFilter(options);

    // Search
    EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
        .queryEmbedding(queryEmbedding)
        .maxResults(options.getLimit() * 2) // Get more results to account for post-filtering
        .minScore(MIN_SCORE)
        .filter(filter)
        .build();

    EmbeddingSearchResult<TextSegment> searchResult = embeddingStore.search(request);

    // Convert to SearchResult objects and apply section filtering
    List<SearchResult> results = searchResult.matches().stream()
        .map(match -> convertToSearchResult(match, options.getQuery()))
        .filter(result -> matchesSectionFilter(result, options.getSections()))
        .limit(options.getLimit())
        .collect(Collectors.toList());
        
    return results;
  }

  /**
   * Check if a result matches the section filter.
   */
  private boolean matchesSectionFilter(SearchResult result, List<JournalSection> requestedSections) {
    if (requestedSections == null || requestedSections.isEmpty()) {
      return true; // No filter, all results match
    }

    // Check if any requested section appears in the entry's sections
    for (JournalSection requested : requestedSections) {
      if (result.getEntry().getSections().containsKey(requested)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Add embedding to in-memory store.
   */
  public void addToStore(Embedding embedding, String text, List<String> sections, long timestamp, String path, JournalType type) {
    Map<String, Object> metadata = new HashMap<>();
    metadata.put("path", path);
    metadata.put("timestamp", timestamp);
    metadata.put("sections", String.join(",", sections));
    metadata.put("type", type.getValue());
    metadata.put("text", text);

    TextSegment segment = TextSegment.from(text, dev.langchain4j.data.document.Metadata.from(metadata));
    embeddingStore.add(embedding, segment);
  }

  /**
   * Build metadata filter from search options.
   * Note: Section filtering is done post-search for better flexibility.
   */
  private Filter buildFilter(SearchOptions options) {
    Filter filter = null;

    // Filter by type (project/user/both)
    if (options.getType() != JournalType.BOTH) {
      filter = metadataKey("type").isEqualTo(options.getType().getValue());
    }

    // Filter by date range (if specified)
    if (options.getAfter() != null) {
      Filter afterFilter = metadataKey("timestamp").isGreaterThanOrEqualTo(options.getAfter().toEpochMilli());
      if (filter == null) {
        filter = afterFilter;
      } else {
        filter = filter.and(afterFilter);
      }
    }

    if (options.getBefore() != null) {
      Filter beforeFilter = metadataKey("timestamp").isLessThanOrEqualTo(options.getBefore().toEpochMilli());
      if (filter == null) {
        filter = beforeFilter;
      } else {
        filter = filter.and(beforeFilter);
      }
    }

    return filter;
  }

  /**
   * Convert EmbeddingMatch to SearchResult.
   */
  private SearchResult convertToSearchResult(EmbeddingMatch<TextSegment> match, String query) {
    TextSegment segment = match.embedded();
    dev.langchain4j.data.document.Metadata metadata = segment.metadata();

    String path = metadata.getString("path");
    long timestamp = metadata.getLong("timestamp");
    String sectionsStr = metadata.getString("sections");

    // Parse section display names to create JournalEntry
    Map<JournalSection, String> sections = new LinkedHashMap<>();
    if (sectionsStr != null && !sectionsStr.isEmpty()) {
      for (String displayName : sectionsStr.split(",")) {
        String trimmed = displayName.trim();
        // We don't have the content, just mark sections as present with empty content
        JournalSection section = JournalSection.fromDisplayName(trimmed);
        sections.put(section, "");
      }
    }

    // Create a minimal JournalEntry for the search result
    Instant date = Instant.ofEpochMilli(timestamp);
    String title = ""; // We don't store title in metadata
    JournalEntry entry = new JournalEntry(title, date, timestamp, sections);

    return new SearchResult(match.score(), entry, Path.of(path));
  }

  /**
   * Generate query-aware excerpt from text.
   */
  public String generateExcerpt(String text, String query) {
    if (text == null || text.isEmpty()) {
      return "";
    }

    if (text.length() <= EXCERPT_LENGTH) {
      return text;
    }

    // Simple implementation: find first occurrence of any query word
    String[] queryWords = query.toLowerCase().split("\\s+");
    int bestStart = 0;
    int maxMatches = 0;

    // Sliding window
    for (int start = 0; start <= text.length() - EXCERPT_LENGTH; start += EXCERPT_STEP) {
      int end = Math.min(start + EXCERPT_LENGTH, text.length());
      String window = text.substring(start, end).toLowerCase();
      
      int matches = 0;
      for (String word : queryWords) {
        if (window.contains(word)) {
          matches++;
        }
      }

      if (matches > maxMatches) {
        maxMatches = matches;
        bestStart = start;
      }
    }

    // Check the very last window to ensure we don't miss the end
    if (text.length() > EXCERPT_LENGTH) {
      int lastStart = text.length() - EXCERPT_LENGTH;
      String window = text.substring(lastStart).toLowerCase();
      int matches = 0;
      for (String word : queryWords) {
        if (window.contains(word)) {
          matches++;
        }
      }
      if (matches >= maxMatches) {
        maxMatches = matches;
        bestStart = lastStart;
      }
    }

    int end = Math.min(bestStart + EXCERPT_LENGTH, text.length());
    String excerpt = text.substring(bestStart, end);

    // Add ellipsis if truncated
    if (bestStart > 0) {
      excerpt = "..." + excerpt;
    }
    if (end < text.length()) {
      excerpt = excerpt + "...";
    }

    return excerpt.trim();
  }

  /**
   * Generate simple excerpt (first N chars) for listings.
   */
  public String generateSimpleExcerpt(String text, int length) {
    if (text == null || text.isEmpty()) {
      return "";
    }

    if (text.length() <= length) {
      return text;
    }

    return text.substring(0, length) + "...";
  }
}
