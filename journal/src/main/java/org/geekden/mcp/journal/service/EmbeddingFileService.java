package org.geekden.mcp.journal.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.geekden.mcp.journal.model.EmbeddingData;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Service for persisting and loading .index JSON files.
 */
@ApplicationScoped
public class EmbeddingFileService {

  private static final Logger LOG = Logger.getLogger(EmbeddingFileService.class);

  @Inject
  ObjectMapper objectMapper;

  /**
   * Save embedding data to .index JSON file.
   * 
   * @param embeddingPath Path to .index file
   * @param data Embedding data to save
   */
  public void saveEmbedding(Path embeddingPath, EmbeddingData data) throws IOException {
    Files.createDirectories(embeddingPath.getParent());
    objectMapper.writeValue(embeddingPath.toFile(), data);
    LOG.debugf("Saved embedding to %s", embeddingPath);
  }

  /**
   * Load embedding data from .index JSON file.
   *
   * @param embeddingPath Path to .index file
   * @return Embedding data
   * @throws IOException if file doesn't exist or cannot be read
   */
  public EmbeddingData loadEmbedding(Path embeddingPath) throws IOException {
    if (!Files.exists(embeddingPath)) {
      throw new IOException("Embedding file not found: " + embeddingPath);
    }

    return objectMapper.readValue(embeddingPath.toFile(), EmbeddingData.class);
  }

  /**
   * Scan directory for all .index files.
   *
   * @param journalPath Root journal directory
   * @return List of embedding data
   * @throws IOException if scanning fails
   */
  public List<EmbeddingData> scanEmbeddings(Path journalPath) throws IOException {
    List<EmbeddingData> embeddings = new ArrayList<>();

    if (!Files.exists(journalPath)) {
      return embeddings;
    }

    try (Stream<Path> paths = Files.walk(journalPath)) {
      List<Path> indexPaths = paths.filter(p -> p.toString().endsWith(".index")).toList();
      for (Path embeddingPath : indexPaths) {
        try {
          embeddings.add(loadEmbedding(embeddingPath));
        } catch (IOException e) {
          LOG.warnf("Skipping malformed embedding file %s: %s", embeddingPath, e.getMessage());
        }
      }
    }

    return embeddings;
  }

  /**
   * Get the .index file path for a given .md file path.
   * 
   * @param mdPath Path to .md file
   * @return Path to corresponding .index file
   */
  public Path getEmbeddingPath(Path mdPath) {
    String mdFileName = mdPath.getFileName().toString();
    String embeddingFileName = mdFileName.replace(".md", ".index");
    return mdPath.getParent().resolve(embeddingFileName);
  }
}
