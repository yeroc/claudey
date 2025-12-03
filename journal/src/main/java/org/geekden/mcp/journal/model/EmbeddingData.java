package org.geekden.mcp.journal.model;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.nio.file.Path;
import java.util.List;

/**
 * Represents the .embedding JSON file structure for compatibility
 * with the reference implementation.
 */
@RegisterForReflection
public class EmbeddingData {

  private float[] embedding;
  private String text;
  private List<String> sections;
  private long timestamp;
  private Path path;

  public EmbeddingData() {
  }

  public EmbeddingData(float[] embedding, String text, List<String> sections, long timestamp, Path path) {
    this.embedding = embedding;
    this.text = text;
    this.sections = sections;
    this.timestamp = timestamp;
    this.path = path;
  }

  public float[] getEmbedding() {
    return embedding;
  }

  public void setEmbedding(float[] embedding) {
    this.embedding = embedding;
  }

  public String getText() {
    return text;
  }

  public void setText(String text) {
    this.text = text;
  }

  public List<String> getSections() {
    return sections;
  }

  public void setSections(List<String> sections) {
    this.sections = sections;
  }

  public long getTimestamp() {
    return timestamp;
  }

  public void setTimestamp(long timestamp) {
    this.timestamp = timestamp;
  }

  public Path getPath() {
    return path;
  }

  public void setPath(Path path) {
    this.path = path;
  }
}
