# Private Journal MCP Specification

## 1. Reference Functionality (`obra/private-journal-mcp`)

The reference implementation provides a local, private journaling system for AI assistants.

### Core Features
- **Multi-section Journaling**: Entries are categorized (e.g., Feelings, Project Notes, User Context).
- **Dual Storage Strategy** (Reference Implementation):
    - **Project-specific**: `project_notes` → `.private-journal/` in current directory
    - **User-global**: `feelings`, `user_context`, `technical_insights`, `world_knowledge` → `~/.private-journal/`
- **File Format**: Markdown files with YAML frontmatter.
- **Semantic Search**: Local embeddings, in-memory vector search, automatic indexing.
- **MCP Tools**: `process_thoughts`, `search_journal`, `read_journal_entry`, `list_recent_entries`

## 2. Proposed Java Implementation

We aim to replicate this functionality within the `claudey` Quarkus MCP server.

### Architecture
- **Language**: Java 21 (Quarkus).
- **Integration**: Module within existing `claudey` server.

### Key Technical Decisions (To Be Discussed)

#### A. Storage Format
- **Proposal**: Maintain compatibility with the reference implementation.
- **Format**: Markdown + YAML Frontmatter.
- **Library**: `jackson-dataformat-yaml` for parsing frontmatter.

#### B. Embeddings & Search
- **Reference**: Uses `Xenova/all-MiniLM-L6-v2` via `@xenova/transformers`.
- **Reference Persistence**: Saves embeddings as `.embedding` JSON files alongside `.md` entries.
- **Proposal**: `LangChain4j` with `all-minilm-l6-v2`.
    - **Dependency**: `langchain4j-embeddings-all-minilm-l6-v2` (non-quantized for compatibility)
    - **Storage**: `InMemoryEmbeddingStore` for fast in-memory search
    - **Persistence**: Replicate reference strategy (store `.embedding` files) for compatibility and durability

**LangChain4j Implementation**:

*Startup - Load Existing Embeddings*:
```java
InMemoryEmbeddingStore<TextSegment> embeddingStore = new InMemoryEmbeddingStore<>();

// Scan .embedding files from both paths
for (EmbeddingData data : loadAllEmbeddingFiles()) {
    // Create Embedding from vector array
    Embedding embedding = new Embedding(data.embedding); // float[384]
    
    // Create TextSegment with metadata
    TextSegment segment = TextSegment.from(
        data.text,
        Metadata.from(Map.of(
            "path", data.path,
            "timestamp", data.timestamp,
            "sections", data.sections,
            "type", determineType(data.path) // "project" or "user"
        ))
    );
    
    // Add to in-memory store
    embeddingStore.add(embedding, segment);
}
```

*Search with Filtering*:
```java
// Generate query embedding
Embedding queryEmbedding = embeddingModel.embed(query).content();

// Build metadata filter
Filter filter = null;
if (!"both".equals(type)) {
    filter = metadataKey("type").isEqualTo(type);
}
// Additional filtering by sections if needed

// Search with cosine similarity (built-in)
List<EmbeddingMatch<TextSegment>> matches = embeddingStore.findRelevant(
    queryEmbedding,
    limit,
    0.1, // minScore
    filter
);
```

*Write New Entry*:
```java
// Generate embedding
Embedding embedding = embeddingModel.embed(cleanedText).content();

// Save .embedding JSON file (for compatibility)
saveEmbeddingJson(filePath, embedding, metadata);

// Add to in-memory store for immediate searchability
TextSegment segment = TextSegment.from(cleanedText, metadata);
embeddingStore.add(embedding, segment);
```

#### C. Directory Structure
- **Strategy**: Dual storage for compatibility with reference.
- **Paths**:
    - **Project**: `.private-journal/` in current working directory (for `project_notes`)
    - **User**: `~/.private-journal/` in home directory (for personal thoughts)
- **Fallback**: If home unavailable, uses `/tmp/.private-journal/`
- **Config**: Environment variable `JOURNAL_PATH` overrides project path.

#### D. File Organization
**Directory Structure**:
```
.private-journal/
├── 2024-11-22/
│   ├── 14-30-45-123456.md
│   ├── 14-30-45-123456.embedding
│   ├── 16-22-10-789012.md
│   └── 16-22-10-789012.embedding
└── 2024-11-23/
    └── ...
```

**Filename Convention**:
- Format: `HH-MM-SS-MMMMMM.md` (24-hour time + microseconds)
- Microseconds: `milliseconds * 1000 + random(0-999)` for uniqueness
- Directory: `YYYY-MM-DD/`

**Markdown File Structure**:
```markdown
---
title: "2:30:45 PM - November 22, 2024"
date: 2024-11-22T14:30:45.123Z
timestamp: 1732334400123
---

## Feelings

[Content here]

## Project Notes

[Content here]
```

**YAML Frontmatter Fields**:
- `title` (string): Human-readable timestamp
- `date` (string): ISO 8601 format
- `timestamp` (number): Unix epoch milliseconds

### Compatibility Verification
To ensure we can share the same directory without breaking the reference tool:
1.  **Content**: Markdown + Frontmatter is standard and safe.
2.  **Embeddings**: We must verify that `LangChain4j`'s `all-minilm-l6-v2` generates vectors compatible with `Xenova/all-MiniLM-L6-v2`.
    - *Risk*: If vectors differ (due to quantization or implementation), search will break when switching tools.
    - *Mitigation*: If incompatible, we will use a different extension (e.g., `.claudey.embedding`) or force re-indexing, but we will aim for exact match first.

### Embedding File Format
Each `.md` journal entry has a corresponding `.embedding` file (JSON).

**Structure**:
```json
{
  "embedding": [0.123, -0.456, ...],  // Array<number>: 384-dimensional vector
  "text": "Cleaned text content...",   // string: Text used for embedding
  "sections": ["Feelings", "Project Notes"],  // Array<string>: Section names
  "timestamp": 1732334400123,          // number: Unix epoch milliseconds
  "path": "/abs/path/to/entry.md"      // string: Absolute path to source
}
```

**Text Extraction Process**:
1. Remove YAML frontmatter (`---\n...\n---\n`)
2. Extract section names from `## Section Name` headers
3. Remove section headers from text
4. Normalize whitespace (collapse 3+ newlines to 2)
5. Trim and embed

**Embedding Model Details**:
- Model: `Xenova/all-MiniLM-L6-v2`
- Dimensions: 384
- Pooling: Mean pooling
- Normalization: Yes (L2 normalized)

**Generation**:
- Created automatically when writing entries
- Skipped if text is empty after cleaning
- Failures logged but don't block journal writing

### MCP Tools Interface

#### `process_thoughts` (Write)
- **Description**: "Your PRIVATE JOURNAL for learning and reflection..."
- **Arguments**:
    - `feelings` (string, optional)
    - `project_notes` (string, optional)
    - `user_context` (string, optional)
    - `technical_insights` (string, optional)
    - `world_knowledge` (string, optional)
- **Behavior**: Writes non-empty fields to a new journal entry.
- **Response**: Text message "Thoughts recorded successfully."

#### `search_journal`
- **Description**: "Search through your private journal entries using natural language queries..."
- **Arguments**:
    - `query` (string, required)
    - `limit` (number, default: 10)
    - `type` (string, enum: ['project', 'user', 'both'], default: 'both')
    - `sections` (array<string>, optional): Filter by section names (case-insensitive substring match)
- **Behavior**: 
    1. Generate query embedding
    2. Load all `.embedding` files from specified paths
    3. Filter by sections and date range (if specified)
    4. Calculate cosine similarity for each
    5. Filter by `minScore >= 0.1`
    6. Sort by score descending
    7. Return top N results
- **Response**: Text list of relevant entries with scores, dates, types, sections, paths, and excerpts.
    ```text
    Found N relevant entries:
    1. [Score: 0.xxx] Date (Type)
       Sections: ...
       Path: ...
       Excerpt: ...
    ```

**Excerpt Generation**:
- Max length: 200 characters
- Query-aware: Finds best 200-char window containing most query words
- Sliding window with 20-char steps
- Adds `...` prefix/suffix if truncated
- For `list_recent_entries`: Uses first 150 chars (no query awareness)

#### `read_journal_entry`
- **Description**: "Read the full content of a specific journal entry by file path."
- **Arguments**:
    - `path` (string, required): File path from search results.
- **Behavior**: Reads content of specific file.
- **Response**: The full text content of the file.

#### `list_recent_entries`
- **Description**: "Get recent journal entries in chronological order."
- **Arguments**:
    - `limit` (number, default: 10)
    - `type` (string, enum: ['project', 'user', 'both'], default: 'both')
    - `days` (number, default: 30)
- **Behavior**: Chronological list of recent entries.
- **Response**: Text list of recent entries.
    ```text
    Recent entries (last N days):
    1. Date (Type)
       Sections: ...
       Path: ...
       Excerpt: ...
    ```

### Error Handling

**Directory Creation**:
- Creates directories recursively with `mkdir -p` equivalent
- Throws error if creation fails after attempt

**Embedding Generation Failures**:
- Logged to stderr but don't block journal writing
- Missing embeddings regenerated on next startup

**File Reading Errors**:
- `ENOENT` (file not found): Return null or empty array
- Other errors: Logged and propagated

**Malformed Files**:
- Invalid JSON in `.embedding`: Logged, skipped, continue with other files
- Invalid directory names: Skipped (must match `YYYY-MM-DD` pattern)

**Startup Embedding Scan**:
- Scans both project and user paths
- Generates missing `.embedding` files for existing `.md` files
- Reports count of generated embeddings
- Failures don't prevent server startup

## 3. Implementation Notes

### Dependencies
```xml
<!-- Add to dependencyManagement section -->
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>dev.langchain4j</groupId>
            <artifactId>langchain4j-bom</artifactId>
            <version>1.8.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<!-- Add to dependencies section -->
<dependencies>
    <dependency>
        <groupId>dev.langchain4j</groupId>
        <artifactId>langchain4j-embeddings-all-minilm-l6-v2</artifactId>
        <!-- Version managed by BOM -->
    </dependency>
    <dependency>
        <groupId>dev.langchain4j</groupId>
        <artifactId>langchain4j</artifactId>
        <!-- Version managed by BOM -->
    </dependency>
    <dependency>
        <groupId>com.fasterxml.jackson.dataformat</groupId>
        <artifactId>jackson-dataformat-yaml</artifactId>
        <!-- Version managed by Quarkus BOM -->
    </dependency>
</dependencies>
```

### Key Classes
- `AllMiniLmL6V2EmbeddingModel`: Generates 384-dim embeddings
- `InMemoryEmbeddingStore<TextSegment>`: Fast in-memory vector search with cosine similarity
- `TextSegment`: Stores text + metadata (implements `Embedded` interface)
- `Metadata`: Key-value metadata storage
- `Filter`: Metadata filtering (e.g., `metadataKey("type").isEqualTo("user")`)

## 4. Integration Architecture

### Existing Claudey Structure
The current codebase follows this pattern:
```
src/main/java/org/geekden/mcp/
├── DatabaseMcpTools.java          # MCP tool definitions (@Tool methods)
├── service/
│   ├── IntrospectionService.java  # Business logic for introspection
│   └── SqlExecutionService.java   # Business logic for SQL execution
├── config/
│   └── DatabaseConfig.java        # Configuration management
└── formatter/
    └── TableFormatter.java        # Output formatting
```

**Pattern**: 
- `DatabaseMcpTools` = MCP interface layer (thin, delegates to services)
- Services = Business logic (injected via CDI)
- Config = Environment-based configuration
- Formatters = Output presentation

### Proposed Refactoring + Journal Integration

**Goal**: Consistent package structure where both database and journal are domain packages.

**Refactored Structure**:
```
src/main/java/org/geekden/
├── mcp/
│   ├── DatabaseMcpTools.java      # Existing (stays in mcp/)
│   └── JournalMcpTools.java       # NEW: Journal MCP interface
├── database/                       # REFACTORED: Move existing database code here
│   ├── service/
│   │   ├── IntrospectionService.java  # MOVED from mcp/service/
│   │   └── SqlExecutionService.java   # MOVED from mcp/service/
│   ├── config/
│   │   └── DatabaseConfig.java        # MOVED from mcp/config/
│   ├── formatter/
│   │   └── TableFormatter.java        # MOVED from mcp/formatter/
│   └── provider/
│       └── ConnectionProvider.java    # MOVED from mcp/service/
└── journal/                        # NEW: Journal domain package
    ├── service/
    │   ├── JournalWriteService.java
    │   ├── JournalSearchService.java
    │   └── EmbeddingService.java
    ├── model/
    │   ├── JournalEntry.java
    │   └── EmbeddingData.java
    ├── config/
    │   └── JournalConfig.java
    └── formatter/
        └── JournalFormatter.java
```

**Rationale**:
- `mcp/` package = **MCP interface layer only** (tools that expose functionality)
- `database/` package = **Database domain** (all database-related logic)
- `journal/` package = **Journal domain** (all journal-related logic)
- Clean separation: MCP tools delegate to domain services

**Migration Path**:
1. Create `org.geekden.database` package
2. Move existing services/config/formatters
3. Update imports in `DatabaseMcpTools`
4. Add `org.geekden.journal` package alongside
5. Both domains follow identical structure

### Component Responsibilities

#### `JournalMcpTools` (MCP Interface Layer)
```java
@ApplicationScoped
public class JournalMcpTools {
    @Inject JournalWriteService writeService;
    @Inject JournalSearchService searchService;
    @Inject JournalConfig config;
    
    @Tool(description = "Your PRIVATE JOURNAL for learning...")
    public String processThoughts(
        @ToolArg(...) String feelings,
        @ToolArg(...) String projectNotes,
        // ... other fields
    ) {
        // Validate, delegate to writeService, format response
    }
    
    @Tool(description = "Search through your private journal...")
    public String searchJournal(
        @ToolArg(...) String query,
        @ToolArg(...) int limit,
        @ToolArg(...) String type
    ) {
        // Delegate to searchService, format results
    }
    
    // ... other tools
}
```

#### `JournalWriteService` (Business Logic)
```java
@ApplicationScoped
public class JournalWriteService {
    @Inject JournalConfig config;
    @Inject EmbeddingService embeddingService;
    @Inject JournalSearchService searchService; // To update in-memory store
    
    public void writeThoughts(Map<String, String> thoughts) {
        // 1. Determine paths (project vs user)
        // 2. Format markdown with YAML frontmatter
        // 3. Write .md file
        // 4. Generate embedding
        // 5. Save .embedding JSON
        // 6. Update in-memory store
    }
}
```

#### `JournalSearchService` (Search Logic)
```java
@ApplicationScoped
public class JournalSearchService {
    private InMemoryEmbeddingStore<TextSegment> embeddingStore;
    @Inject EmbeddingService embeddingService;
    @Inject JournalConfig config;
    
    @PostConstruct
    public void initialize() {
        // Load all .embedding files into memory
        embeddingStore = new InMemoryEmbeddingStore<>();
        loadExistingEmbeddings();
    }
    
    public List<SearchResult> search(String query, SearchOptions options) {
        // 1. Generate query embedding
        // 2. Build metadata filter
        // 3. Call embeddingStore.findRelevant()
        // 4. Format results
    }
    
    public void addToStore(Embedding embedding, TextSegment segment) {
        embeddingStore.add(embedding, segment);
    }
}
```

#### `EmbeddingService` (Model Wrapper)
```java
@ApplicationScoped
public class EmbeddingService {
    private AllMiniLmL6V2EmbeddingModel model;
    
    @PostConstruct
    public void initialize() {
        model = new AllMiniLmL6V2EmbeddingModel();
    }
    
    public Embedding generateEmbedding(String text) {
        return model.embed(text).content();
    }
    
    public String extractSearchableText(String markdown) {
        // Remove frontmatter, headers, normalize whitespace
    }
}
```

### Startup Sequence
1. **Quarkus CDI** initializes all `@ApplicationScoped` beans
2. **`JournalSearchService.@PostConstruct`** runs:
   - Scans `.private-journal/` directories
   - Loads all `.embedding` JSON files
   - Populates `InMemoryEmbeddingStore`
3. **`JournalMcpTools`** registered with MCP server (via `@Tool` annotations)
4. Server ready to accept journal tool calls

### Configuration
```java
@ApplicationScoped
public class JournalConfig {
    @ConfigProperty(name = "journal.path.project", defaultValue = ".private-journal")
    String projectPath;
    
    @ConfigProperty(name = "journal.path.user")
    Optional<String> userPath; // Falls back to ~/.private-journal
    
    public String getProjectJournalPath() {
        return resolveProjectPath();
    }
    
    public String getUserJournalPath() {
        return userPath.orElse(resolveUserHomePath());
    }
}
```

### Why This Architecture?
1. **Consistency**: Mirrors existing `DatabaseMcpTools` + services pattern
2. **Separation of Concerns**: MCP layer, business logic, configuration are separate
3. **Testability**: Services can be unit tested independently
4. **CDI Integration**: Leverages Quarkus dependency injection
5. **Maintainability**: Clear boundaries between components

## 5. Pending Verification
1.  **Embedding Compatibility**: Verify if Java `all-minilm-l6-v2` produces identical 384-dim vectors to `Xenova/all-MiniLM-L6-v2`
    - Both use ONNX runtime, should be compatible
    - Test with sample text and compare vectors
2.  **Binary Size**: Confirm model adds acceptable overhead (~23MB)
3.  **Dual Storage**: Implement dual storage (project + user paths) as per reference

---

## Appendix: Architecture Consistency Checklist

When adding new functionality to an existing codebase, **always** complete this checklist:

### 1. Map Existing Structure
- List all current packages and their purposes
- Identify the organizational pattern (layered, domain-driven, feature-based, etc.)
- Note any existing inconsistencies or technical debt

### 2. Identify Architectural Patterns
- What pattern does the existing code follow? (MVC, hexagonal, domain-driven, etc.)
- Are there established conventions? (e.g., `service/`, `config/`, `formatter/` packages)
- How are cross-cutting concerns handled?

### 3. Consistency Check
- Would the proposed changes follow the **same pattern** as existing code?
- If creating a new package structure, should existing code be refactored to match?
- Would this create "two ways of doing things"?

### 4. Refactoring Decision
- If inconsistency detected: Propose refactoring existing code **first**
- Document the "before" and "after" structure clearly
- Explain the migration path

### 5. Future-Proofing
- If a third similar feature were added, would the pattern still make sense?
- Does this create a scalable organizational structure?
