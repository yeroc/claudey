package org.geekden.mcp.journal.service;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.geekden.mcp.journal.model.JournalEntry;
import org.geekden.mcp.journal.model.JournalSection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class JournalFileServiceTest {

  @Inject
  JournalFileService service;

  @Test
  void shouldNotHaveDuplicateFrontmatterDelimiters(@TempDir Path tempDir) throws Exception {
    // Given a journal entry
    Map<JournalSection, String> sections = new LinkedHashMap<>();
    sections.put(JournalSection.TECHNICAL_INSIGHTS, "Test thoughts");

    Instant now = Instant.now();
    JournalEntry entry = new JournalEntry(
      "Test Title",
      now,
      now.toEpochMilli(),
      sections
    );

    // When writing the entry
    Path writtenFile = service.writeEntry(tempDir, entry);

    // Then the file should exist
    assertThat("File should be created", Files.exists(writtenFile), is(true));

    // And the content should not have duplicate --- delimiters
    String content = Files.readString(writtenFile);

    // The content should start with exactly one ---
    assertThat("Content should start with ---", content, startsWith("---\n"));

    // The content should not start with duplicate ---
    assertThat("Content should not have duplicate --- at start",
      content, not(startsWith("---\n---\n")));

    // Count occurrences of --- (should be exactly 2: opening and closing)
    long delimiterCount = content.lines()
      .filter(line -> line.trim().equals("---"))
      .count();

    assertThat("Should have exactly 2 frontmatter delimiters (opening and closing)",
      delimiterCount, is(2L));
  }
}
