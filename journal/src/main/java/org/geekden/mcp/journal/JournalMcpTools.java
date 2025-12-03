package org.geekden.mcp.journal;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.geekden.mcp.journal.formatter.JournalFormatter;
import org.geekden.mcp.journal.model.SearchOptions;
import org.geekden.mcp.journal.model.SearchResult;
import org.geekden.mcp.journal.service.JournalReadService;
import org.geekden.mcp.journal.service.JournalSearchService;
import org.geekden.mcp.journal.service.JournalWriteService;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.geekden.mcp.journal.model.JournalSection;
import org.geekden.mcp.journal.model.JournalType;

/**
 * MCP Tools for private journaling.
 * Provides tools for recording thoughts, searching entries, and reading journal content.
 */
@ApplicationScoped
public class JournalMcpTools {

  private static final Logger LOG = Logger.getLogger(JournalMcpTools.class);

  @Inject
  JournalWriteService writeService;

  @Inject
  JournalSearchService searchService;

  @Inject
  JournalReadService readService;

  @Inject
  JournalFormatter formatter;

  /**
   * Your PRIVATE JOURNAL for learning and reflection.
   * 
   * This is your personal space to:
   * - Record feelings and emotional states
   * - Document project progress and technical decisions
   * - Capture user context and preferences
   * - Note technical insights and learnings
   * - Store world knowledge and facts
   * 
   * All entries are stored locally in your user journal and searchable via semantic search.
   */
  @Tool(description = "Your PRIVATE JOURNAL for learning and reflection. Record thoughts across different categories: "
      + "feelings (emotional states), project_notes (project-specific work), user_context (user preferences/info), "
      + "technical_insights (learnings), world_knowledge (facts). All entries are private and searchable.")
  public String processThoughts(
      @ToolArg(description = "Your feelings and emotional state", required = false) String feelings,
      @ToolArg(description = "Project-specific notes and progress", required = false) String projectNotes,
      @ToolArg(description = "User context, preferences, and information", required = false) String userContext,
      @ToolArg(description = "Technical insights and learnings", required = false) String technicalInsights,
      @ToolArg(description = "World knowledge and facts", required = false) String worldKnowledge) {

    Map<JournalSection, String> thoughts = new LinkedHashMap<>();
    if (feelings != null) thoughts.put(JournalSection.FEELINGS, feelings);
    if (projectNotes != null) thoughts.put(JournalSection.PROJECT_NOTES, projectNotes);
    if (userContext != null) thoughts.put(JournalSection.USER_CONTEXT, userContext);
    if (technicalInsights != null) thoughts.put(JournalSection.TECHNICAL_INSIGHTS, technicalInsights);
    if (worldKnowledge != null) thoughts.put(JournalSection.WORLD_KNOWLEDGE, worldKnowledge);

    try {
      return writeService.writeThoughts(thoughts);
    } catch (IOException e) {
      LOG.error("Failed to write journal entry", e);
      return "Error: Failed to write journal entry: " + e.getMessage();
    }
  }

  /**
   * Search through your private journal entries using natural language queries.
   * Uses semantic search to find relevant entries based on meaning, not just keywords.
   */
  @Tool(description = "Search through your private journal entries using natural language queries. "
      + "Uses semantic search to find relevant entries based on meaning. "
      + "Filter by type (project/user/both), sections, date range, and limit results.")
  public String searchJournal(
      @ToolArg(description = "Natural language search query") String query,
      @ToolArg(description = "Maximum number of results to return", required = false, defaultValue = "10") int limit,
      @ToolArg(description = "Type of entries to search: 'project', 'user', or 'both'", 
               required = false, defaultValue = "both") String type,
      @ToolArg(description = "Comma-separated list of sections to search in (e.g. 'feelings,technical_insights')", required = false) String sections,
      @ToolArg(description = "Filter entries after this date (ISO-8601 format, e.g. '2025-01-01')", required = false) String after,
      @ToolArg(description = "Filter entries before this date (ISO-8601 format, e.g. '2025-12-31')", required = false) String before) {

    try {
      List<JournalSection> sectionList = null;
      if (sections != null && !sections.isBlank()) {
        sectionList = new ArrayList<>();
        for (String sectionStr : sections.split(",")) {
          try {
            sectionList.add(JournalSection.fromMcpKey(sectionStr.trim()));
          } catch (IllegalArgumentException e) {
            // Skip invalid section names
            LOG.warnf("Ignoring unknown section: %s", sectionStr);
          }
        }
        if (sectionList.isEmpty()) {
          sectionList = null; // No valid sections found
        }
      }
          
      Instant afterInstant = null;
      Instant beforeInstant = null;
      
      // Handle simple YYYY-MM-DD by appending T00:00:00Z
      if (after != null && !after.contains("T")) {
          afterInstant = LocalDate.parse(after).atStartOfDay(ZoneId.systemDefault()).toInstant();
      } else if (after != null) {
          afterInstant = Instant.parse(after);
      }
      
      if (before != null && !before.contains("T")) {
          beforeInstant = LocalDate.parse(before).atStartOfDay(ZoneId.systemDefault()).plusDays(1).minusNanos(1).toInstant();
      } else if (before != null) {
          beforeInstant = Instant.parse(before);
      }

      // Parse type to enum
      JournalType journalType = 
          JournalType.fromValue(type != null ? type : "user");

      SearchOptions options = new SearchOptions(query, limit, journalType, sectionList, afterInstant, beforeInstant);
      List<SearchResult> results = searchService.search(options);
      return formatter.formatSearchResults(results, query);
    } catch (Exception e) {
      LOG.error("Error searching journal", e);
      return "Error: Failed to search journal: " + e.getMessage();
    }
  }

  /**
   * Read the full content of a specific journal entry by file path.
   */
  @Tool(description = "Read the full content of a specific journal entry by file path. "
      + "Use the path from search results or recent entries list.")
  public String readJournalEntry(
      @ToolArg(description = "File path to the journal entry") String path) {

    try {
      Path entryPath = Path.of(path);
      return readService.readEntry(entryPath);
    } catch (IOException e) {
      LOG.errorf("Failed to read entry %s: %s", path, e.getMessage());
      return "Error: Failed to read entry: " + e.getMessage();
    }
  }

  /**
   * Get recent journal entries in chronological order.
   */
  @Tool(description = "Get recent journal entries in chronological order. "
      + "Filter by type (project/user/both) and specify how many days to look back.")
  public String listRecentEntries(
      @ToolArg(description = "Maximum number of entries to return", required = false, defaultValue = "10") int limit,
      @ToolArg(description = "Type of entries to list: 'project', 'user', or 'both'",
               required = false, defaultValue = "both") String type,
      @ToolArg(description = "Number of days to look back", required = false, defaultValue = "30") int days) {

    try {
      JournalType journalType = JournalType.fromValue(type != null ? type : "both");
      List<JournalReadService.EntryInfo> entries = readService.listRecentEntries(limit, journalType, days);
      return formatter.formatRecentEntries(entries, days);
    } catch (IOException e) {
      LOG.error("Error listing recent entries", e);
      return "Error: Failed to list recent entries: " + e.getMessage();
    } catch (IllegalArgumentException e) {
      LOG.error("Invalid journal type", e);
      return "Error: Invalid journal type '" + type + "'. Valid values are: 'user', 'project', or 'both'.";
    }
  }
}
