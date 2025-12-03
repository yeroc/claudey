package org.geekden.mcp.journal.service;

import dev.langchain4j.data.embedding.Embedding;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.geekden.mcp.journal.config.JournalConfig;
import org.geekden.mcp.journal.model.EmbeddingData;
import org.geekden.mcp.journal.model.JournalEntry;
import org.geekden.mcp.journal.model.JournalSection;
import org.geekden.mcp.journal.model.JournalType;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

/**
 * Service for writing journal entries.
 */
@ApplicationScoped
public class JournalWriteService {

  private static final Logger LOG = Logger.getLogger(JournalWriteService.class);

  @Inject
  JournalConfig config;

  @Inject
  JournalFileService fileService;

  @Inject
  EmbeddingService embeddingService;

  @Inject
  EmbeddingFileService embeddingFileService;

  @Inject
  JournalSearchService searchService;

  /**
   * Write thoughts to journal.
   *
   * @param thoughts Map of JournalSection to content
   * @return Success message
   * @throws IOException if writing fails
   */
  public String writeThoughts(Map<JournalSection, String> thoughts) throws IOException {
    // Filter out empty sections
    Map<JournalSection, String> nonEmptyThoughts = new LinkedHashMap<>();
    for (Map.Entry<JournalSection, String> entry : thoughts.entrySet()) {
      if (entry.getValue() != null && !entry.getValue().trim().isEmpty()) {
        nonEmptyThoughts.put(entry.getKey(), entry.getValue());
      }
    }

    if (nonEmptyThoughts.isEmpty()) {
      return "No thoughts to record (all sections were empty).";
    }

    // Create journal entry
    JournalEntry entry = fileService.createEntry(nonEmptyThoughts);

    // Determine storage path (always user path)
    Path basePath = config.getUserJournalPath();

    // Write entry file
    Path entryPath = fileService.writeEntry(basePath, entry);

    // Generate and save embedding
    String markdown = fileService.readEntry(entryPath);
    generateAndSaveEmbedding(entryPath, markdown, nonEmptyThoughts.keySet(), entry.getTimestamp(), basePath);

    return "Thoughts recorded successfully.";
  }

  /**
   * Generate embedding and save to file.
   *
   * @throws IOException if embedding generation or saving fails
   */
  private void generateAndSaveEmbedding(Path entryPath, String markdown,
                                        Set<JournalSection> sections,
                                        long timestamp, Path basePath) throws IOException {
    // Extract searchable text
    String cleanedText = embeddingService.extractSearchableText(markdown);

    if (cleanedText.isEmpty()) {
      LOG.warn("No text to embed after cleaning, skipping embedding generation");
      return;
    }

    // Generate embedding
    Embedding embedding = embeddingService.generateEmbedding(cleanedText);

    // Determine type (always user)
    JournalType type = JournalType.USER;

    // Convert sections to display names for storage
    List<String> sectionNames = sections.stream()
        .map(JournalSection::getDisplayName)
        .collect(java.util.stream.Collectors.toList());

    // Save embedding file
    Path embeddingPath = embeddingFileService.getEmbeddingPath(entryPath);
    EmbeddingData data = new EmbeddingData(
        embedding.vector(),
        cleanedText,
        sectionNames,
        timestamp,
        entryPath.toAbsolutePath()
    );
    embeddingFileService.saveEmbedding(embeddingPath, data);

    // Add to in-memory store
    searchService.addToStore(embedding, cleanedText, sectionNames,
                             timestamp, entryPath.toAbsolutePath().toString(), type);
  }
}
