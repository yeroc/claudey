package org.geekden.mcp.journal.service;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;

import java.util.regex.Pattern;

/**
 * Service for generating embeddings from text.
 * Uses AllMiniLmL6V2 model (384 dimensions).
 * Note: Vectors do not exactly match reference implementation (JS/Xenova) due to runtime differences.
 */
@ApplicationScoped
public class EmbeddingService {

  private static final Logger LOG = Logger.getLogger(EmbeddingService.class);
  private static final Pattern FRONTMATTER_PATTERN = Pattern.compile("^---\\n.*?\\n---\\n", Pattern.DOTALL);
  private static final Pattern SECTION_HEADER_PATTERN = Pattern.compile("^## .+$", Pattern.MULTILINE);
  private static final Pattern MULTIPLE_NEWLINES = Pattern.compile("\\n{3,}");

  @Inject
  private EmbeddingModel model;

  @PostConstruct
  public void initialize() {
    LOG.info("Embedding AllMiniLmL6V2 model initialized successfully");
  }

  /**
   * Generate embedding from text.
   * 
   * @param text Text to embed
   * @return Embedding (384-dimensional vector)
   */
  public Embedding generateEmbedding(String text) {
    if (text == null || text.trim().isEmpty()) {
      throw new IllegalArgumentException("Text cannot be null or empty");
    }
    return model.embed(text).content();
  }

  /**
   * Extract searchable text from markdown entry.
   * Removes YAML frontmatter, section headers, and normalizes whitespace.
   * 
   * @param markdown Full markdown content
   * @return Cleaned text suitable for embedding
   */
  public String extractSearchableText(String markdown) {
    if (markdown == null) {
      return "";
    }

    // Remove YAML frontmatter
    String text = FRONTMATTER_PATTERN.matcher(markdown).replaceFirst("");

    // Remove section headers (## Section Name)
    text = SECTION_HEADER_PATTERN.matcher(text).replaceAll("");

    // Normalize whitespace: collapse 3+ newlines to 2
    text = MULTIPLE_NEWLINES.matcher(text).replaceAll("\n\n");

    return text.trim();
  }
}
