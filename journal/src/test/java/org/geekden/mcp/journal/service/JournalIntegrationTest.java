package org.geekden.mcp.journal.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import java.util.List;
import org.geekden.mcp.journal.model.SearchOptions;

@QuarkusTest
class JournalIntegrationTest {

  @Inject
  EmbeddingService embeddingService;

  @Inject
  JournalSearchService searchService;

  private static final String SAMPLE_DATA_DIR = "src/test/resources/synthetic-data/2025-01-15";

  @BeforeEach
  void setUp() {
    // Clear store before each test to ensure test isolation
    searchService.clearStore();
  }

  @Test
  void testTextExtractionMatchesSampleData() throws IOException {
    // Find all .md files in sample data
    try (Stream<Path> paths = Files.walk(Paths.get(SAMPLE_DATA_DIR))) {
      paths.filter(p -> p.toString().endsWith(".md"))
           .forEach(this::verifyTextExtraction);
    }
  }

  private void verifyTextExtraction(Path mdPath) {
    try {
      // Read markdown file
      String markdown = Files.readString(mdPath);

      // Read corresponding embedding file
      String embeddingPathStr = mdPath.toString().replace(".md", ".embedding");
      Path embeddingPath = Paths.get(embeddingPathStr);
      
      if (!Files.exists(embeddingPath)) {
        throw new RuntimeException("Missing embedding file for " + mdPath + ". Expected at: " + embeddingPath);
      }

      String embeddingJson = Files.readString(embeddingPath);
      ObjectMapper mapper = new ObjectMapper();
      JsonNode root = mapper.readTree(embeddingJson);
      String expectedText = root.get("text").asText();

      // Perform extraction
      String actualText = embeddingService.extractSearchableText(markdown);

      // Compare
      // Note: There might be slight differences in whitespace normalization or trimming
      // so we might need to be a bit lenient or adjust our logic to match exactly.
      // For now, let's check exact match and see if it fails.
      assertThat("Text extraction should match sample data for " + mdPath.getFileName(),
          actualText, equalTo(expectedText));

    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  void testSearchExcerptGeneration() {
    // Use text from one of the samples
    String text = "**Corey's Code Style Preferences:**\n- Dependencies must be in alphabetical order: grouped by scope, then by groupId, then by artifactId\n- No self-referential commit messages (no \"Generated with Claude Code\", no co-authored-by lines)\n- Remove obvious comments from code - if it's straightforward, no comment needed\n- JIRA comments: brief, factual, no unnecessary technical details when context is clear\n\n**Corey's Communication Style:**\n- Direct and matter-of-fact\n- Expects me to catch and fix mistakes without hand-holding\n- Values precision and correctness over speed\n\n**Feature Usage Tracking Pattern:**\n\nWhen deciding where to place telemetry tracking calls:\n- **Beginning of method** = captures attempts including failures (better visibility into usage patterns)\n- **After success** = only captures successful operations (incomplete picture)\n\nIn this case, recording at the beginning was the right choice because even failed export attempts indicate user intent and feature usage.\n\n**Reusing vs Creating Features:**\nWhen adding telemetry, always check if an existing Feature enum already covers the use case. Don't create new features unnecessarily. In this case, SURVEY_EXPORT already mentioned SEGP1 in its documentation, making it the obvious choice.";

    // Test 1: Query at the beginning
    String query1 = "dependencies alphabetical";
    String excerpt1 = searchService.generateExcerpt(text, query1);
    assertThat("Excerpt should contain query words", excerpt1.toLowerCase(), containsString("dependencies"));
    assertThat("Excerpt should contain query words", excerpt1.toLowerCase(), containsString("alphabetical"));
    assertThat("Excerpt should be approx 200 chars", excerpt1.length(), lessThanOrEqualTo(206)); // 200 + "..." + "..."

    // Test 2: Query in the middle
    String query2 = "telemetry tracking calls";
    String excerpt2 = searchService.generateExcerpt(text, query2);
    assertThat("Excerpt should contain query words", excerpt2.toLowerCase(), containsString("telemetry"));
    assertThat("Excerpt should contain query words", excerpt2.toLowerCase(), containsString("tracking"));
    assertThat("Excerpt start should be shifted", excerpt2, startsWith("..."));

    // Test 3: Query at the end
    String query3 = "survey export segp1";
    String excerpt3 = searchService.generateExcerpt(text, query3);
    assertThat("Excerpt should contain query words", excerpt3.toLowerCase(), containsString("survey_export"));
    assertThat("Excerpt should contain query words", excerpt3.toLowerCase(), containsString("segp1"));
    assertThat("Excerpt end should be at text end", excerpt3, not(endsWith("...")));
  }

  /**
   * NOTE: Embedding accuracy test removed because the reference implementation
   * uses a different embedding model than AllMiniLmL6V2. Text extraction is verified
   * to be identical via testTextExtractionMatchesSampleData.
   */

  @Test
  void testMetadataParsing() throws IOException {
    ObjectMapper yamlMapper = new ObjectMapper(new com.fasterxml.jackson.dataformat.yaml.YAMLFactory());
    
    try (Stream<Path> paths = Files.walk(Paths.get(SAMPLE_DATA_DIR))) {
      paths.filter(p -> p.toString().endsWith(".md"))
           .forEach(mdPath -> {
             try {
               String content = Files.readString(mdPath);
               
               // Extract frontmatter
               if (content.startsWith("---")) {
                 int end = content.indexOf("\n---", 3);
                 if (end > 0) {
                   String yaml = content.substring(3, end);
                   JsonNode frontmatter = yamlMapper.readTree(yaml);
                   
                   assertThat("Should have title", frontmatter.has("title"), is(true));
                   assertThat("Should have date", frontmatter.has("date"), is(true));
                   assertThat("Should have timestamp", frontmatter.has("timestamp"), is(true));
                   
                   // Verify timestamp matches filename (approx check or exact check if we parse filename)
                   long timestamp = frontmatter.get("timestamp").asLong();
                   assertThat("Timestamp should be valid", timestamp, greaterThan(0L));
                 }
               }
             } catch (IOException e) {
               throw new RuntimeException(e);
             }
           });
    }
  }

  @Test
  void testSearchRelevance() throws IOException {
    // Load samples into store
    try (Stream<Path> paths = Files.walk(Paths.get(SAMPLE_DATA_DIR))) {
      paths.filter(p -> p.toString().endsWith(".embedding"))
           .forEach(p -> {
             try {
               String json = Files.readString(p);
               ObjectMapper mapper = new ObjectMapper();
               JsonNode root = mapper.readTree(json);
               
               float[] vector = new float[root.get("embedding").size()];
               for (int i = 0; i < vector.length; i++) {
                 vector[i] = (float) root.get("embedding").get(i).asDouble();
               }
               
               String text = root.get("text").asText();
               String path = root.get("path").asText();
               long timestamp = root.get("timestamp").asLong();
               
               // Load sections from JSON
               java.util.List<String> sections = new java.util.ArrayList<>();
               if (root.has("sections")) {
                 JsonNode sectionsNode = root.get("sections");
                 for (JsonNode sectionNode : sectionsNode) {
                   sections.add(sectionNode.asText());
                 }
               }
               
               // Add to store
               searchService.addToStore(new dev.langchain4j.data.embedding.Embedding(vector), 
                   text, sections, timestamp, path, org.geekden.mcp.journal.model.JournalType.USER);
                   
             } catch (Exception e) {
               throw new RuntimeException(e);
             }
           });
    }

    // Search for a term in the synthetic data
    SearchOptions options = new SearchOptions("database connection pooling", 10, org.geekden.mcp.journal.model.JournalType.USER, null, null, null);
    var results = searchService.search(options);
    
    assertThat("Should find results", results, not(empty()));
    // Top result should be relevant (we can't easily test excerpt generation here)
  }

  @Test
  void testSectionFiltering() throws IOException {
    // Load sample data
    loadSampleData();
    
    // Search with section filter for "Technical Insights"
    SearchOptions options = new SearchOptions("integration tests", 10, org.geekden.mcp.journal.model.JournalType.USER, List.of(org.geekden.mcp.journal.model.JournalSection.TECHNICAL_INSIGHTS), null, null);
    var results = searchService.search(options);
    
    assertThat("Should find results", results, not(empty()));
    // All results should have "Technical Insights" section
    for (var result : results) {
      assertThat("Result should have Technical Insights section", 
          result.getEntry().getSections().keySet(), hasItem(org.geekden.mcp.journal.model.JournalSection.TECHNICAL_INSIGHTS));
    }
  }

  @Test
  void testDateFiltering() throws IOException {
    // Load sample data
    loadSampleData();
    
    // Filter for entries after a specific date (synthetic data timestamp: 1736942267000L)
    java.time.Instant after = java.time.Instant.ofEpochMilli(1736942267000L - 1000); // Just before first synthetic entry
    var results = searchService.search(new org.geekden.mcp.journal.model.SearchOptions(
        "PostgreSQL", 5, org.geekden.mcp.journal.model.JournalType.USER, null, after, null));

    assertThat("Should find results after date", results, not(empty()));

    // Filter for entries before a specific date (should find nothing)
    java.time.Instant before = java.time.Instant.ofEpochMilli(1736942267000L - 1000); // Before first synthetic entry
    var noResults = searchService.search(new org.geekden.mcp.journal.model.SearchOptions(
        "PostgreSQL", 5, org.geekden.mcp.journal.model.JournalType.USER, null, null, before));
    
    assertThat("Should find no results before date", noResults, empty());
  }

  private void loadSampleData() throws IOException {
    try (Stream<Path> paths = Files.walk(Paths.get(SAMPLE_DATA_DIR))) {
      paths.filter(p -> p.toString().endsWith(".embedding"))
           .forEach(p -> {
             try {
               String json = Files.readString(p);
               ObjectMapper mapper = new ObjectMapper();
               JsonNode root = mapper.readTree(json);
               
               float[] vector = new float[root.get("embedding").size()];
               for (int i = 0; i < vector.length; i++) {
                 vector[i] = (float) root.get("embedding").get(i).asDouble();
               }
               
               String text = root.get("text").asText();
               String path = root.get("path").asText();
               long timestamp = root.get("timestamp").asLong();
               
               // Load sections from JSON
               java.util.List<String> sections = new java.util.ArrayList<>();
               if (root.has("sections")) {
                 JsonNode sectionsNode = root.get("sections");
                 for (JsonNode sectionNode : sectionsNode) {
                   sections.add(sectionNode.asText());
                 }
               }
               
               // Add to store
               searchService.addToStore(new dev.langchain4j.data.embedding.Embedding(vector), 
                   text, sections, timestamp, path, org.geekden.mcp.journal.model.JournalType.USER);
                   
             } catch (Exception e) {
               throw new RuntimeException(e);
             }
           });
    }
  }
}
