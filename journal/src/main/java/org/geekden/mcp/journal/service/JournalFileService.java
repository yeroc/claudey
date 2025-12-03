package org.geekden.mcp.journal.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import jakarta.enterprise.context.ApplicationScoped;
import org.geekden.mcp.journal.model.JournalEntry;
import org.geekden.mcp.journal.model.JournalSection;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Service for reading and writing journal entry files.
 */
@ApplicationScoped
public class JournalFileService {

  private static final Logger LOG = Logger.getLogger(JournalFileService.class);
  private static final DateTimeFormatter DATE_DIR_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
  private static final DateTimeFormatter TIME_FILE_FORMAT = DateTimeFormatter.ofPattern("HH-mm-ss");
  private static final DateTimeFormatter TITLE_FORMAT = DateTimeFormatter.ofPattern("h:mm:ss a - MMMM d, yyyy");
  private static final ObjectMapper YAML_MAPPER = new ObjectMapper(
    new YAMLFactory()
      .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
  );

  /**
   * Write journal entry to file.
   * 
   * @param basePath Base journal path (project or user)
   * @param entry Journal entry to write
   * @return Path to created file
   */
  public Path writeEntry(Path basePath, JournalEntry entry) throws IOException {
    // Create date-based directory (YYYY-MM-DD)
    Instant instant = Instant.ofEpochMilli(entry.getTimestamp());
    String dateDir = DATE_DIR_FORMAT.format(instant.atZone(ZoneId.systemDefault()));
    Path datePath = basePath.resolve(dateDir);
    Files.createDirectories(datePath);

    // Generate filename (HH-MM-SS-MMMMMM.md)
    String filename = generateFilename(instant);
    Path filePath = datePath.resolve(filename);

    // Write markdown with YAML frontmatter
    String markdown = formatMarkdown(entry);
    Files.writeString(filePath, markdown);

    LOG.infof("Wrote journal entry to %s", filePath);
    return filePath;
  }

  /**
   * Read journal entry from file.
   * 
   * @param filePath Path to journal entry file
   * @return Journal entry content (raw markdown)
   */
  public String readEntry(Path filePath) throws IOException {
    if (!Files.exists(filePath)) {
      throw new IOException("File not found: " + filePath);
    }
    return Files.readString(filePath);
  }

  /**
   * Generate filename with microsecond precision.
   * Format: HH-MM-SS-MMMMMM.md
   * 
   * @param instant Timestamp
   * @return Filename
   */
  private String generateFilename(Instant instant) {
    String timePrefix = TIME_FILE_FORMAT.format(instant.atZone(ZoneId.systemDefault()));
    
    // Generate microseconds: milliseconds * 1000 + random(0-999)
    long millis = instant.toEpochMilli() % 1000;
    int randomMicros = ThreadLocalRandom.current().nextInt(1000);
    long microseconds = millis * 1000 + randomMicros;
    
    return String.format("%s-%06d.md", timePrefix, microseconds);
  }

  /**
   * Format journal entry as markdown with YAML frontmatter.
   * 
   * @param entry Journal entry
   * @return Formatted markdown
   */
  private String formatMarkdown(JournalEntry entry) throws IOException {
    StringBuilder sb = new StringBuilder();

    // YAML frontmatter
    Map<String, Object> frontmatter = new LinkedHashMap<>();
    frontmatter.put("title", entry.getTitle());
    frontmatter.put("date", entry.getDate().toString());
    frontmatter.put("timestamp", entry.getTimestamp());

    sb.append("---\n");
    sb.append(YAML_MAPPER.writeValueAsString(frontmatter));
    sb.append("---\n\n");

    // Section content
    for (Map.Entry<JournalSection, String> section : entry.getSections().entrySet()) {
      String sectionName = section.getKey().getDisplayName();
      sb.append("## ").append(sectionName).append("\n\n");
      sb.append(section.getValue().trim()).append("\n\n");
    }

    return sb.toString();
  }

  /**
   * Create a journal entry from thoughts map.
   * 
   * @param thoughts Map of JournalSection to content
   * @return Journal entry
   */
  public JournalEntry createEntry(Map<JournalSection, String> thoughts) {
    Instant now = Instant.now();
    String title = TITLE_FORMAT.format(now.atZone(ZoneId.systemDefault()));
    long timestamp = now.toEpochMilli();

    return new JournalEntry(title, now, timestamp, thoughts);
  }
}
