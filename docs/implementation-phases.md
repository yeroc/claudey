# MCP Database Server - Implementation Phases

This document breaks down the implementation of the MCP Database Server into manageable phases for handoff and incremental development.

## Implementation Principles

### Platform-First Development

**IMPORTANT**: Always research and leverage platform capabilities before implementing custom solutions.

- **Consult canonical documentation first**: Quarkus, MicroProfile, JDBC specifications
- **Don't reinvent the wheel**: Use built-in features for:
  - Configuration management (MicroProfile Config)
  - Connection pooling (Agroal)
  - Dependency injection (CDI)
  - JDBC driver loading (service-loader)
  - Logging (Quarkus Logging)
  - Native compilation (Quarkus GraalVM integration)
- **Research before coding**: Each phase should begin with reviewing relevant platform documentation
- **Examples of what NOT to implement**:
  - Custom config file parsers (use MicroProfile Config)
  - Manual connection pool management (use Agroal)
  - Custom retry logic for connections (configure Agroal properly)
  - Manual class loading for JDBC drivers (JDBC 4.0+ service-loader)

**Key Documentation Resources**:
- Quarkus guides: https://quarkus.io/guides/
- MicroProfile Config: https://quarkus.io/guides/config-reference
- Quarkus Datasource/Agroal: https://quarkus.io/guides/datasource
- Quarkus Native: https://quarkus.io/guides/building-native-image

---

## Phase 1: Foundation & Project Setup

**Goal**: Establish the base project structure with MCP server capabilities, database connectivity, and native compilation working from the start.

### Tasks
1. **Add Quarkus MCP Extension**
   - Use `mvn quarkus:add-extension -Dextensions="io.quarkiverse.mcp:quarkus-mcp-server-stdio"`
   - Verify MCP extension is compatible with Quarkus 3.27.0 (may need to update from 3.8.1)

2. **Database Dependencies**
   - Use `mvn quarkus:add-extension -Dextensions="quarkus-jdbc-postgresql"` for PostgreSQL
   - Use `mvn quarkus:add-extension -Dextensions="quarkus-jdbc-sqlite"` for SQLite
   - Use `mvn quarkus:add-extension -Dextensions="quarkus-agroal"` for connection pooling
   - Note: Quarkus will automatically configure drivers and pooling

3. **Configuration Setup**
   - **Research Quarkus configuration system first**:
     - Read Quarkus Config documentation: https://quarkus.io/guides/config-reference
     - Understand MicroProfile Config (handles env vars, files, priority automatically)
     - Review datasource configuration: https://quarkus.io/guides/datasource
   - **Use Quarkus's built-in config system** (don't implement custom config loading):
     - Leverage MicroProfile Config for environment variables and config files
     - Use `@ConfigProperty` or `@ConfigMapping` for type-safe config
     - Configure Quarkus datasource properties:
       - `quarkus.datasource.jdbc.url` (from DB_URL env var or config file)
       - `quarkus.datasource.username` (from DB_USERNAME)
       - `quarkus.datasource.password` (from DB_PASSWORD)
       - `quarkus.datasource.jdbc.max-size` (from DB_POOL_SIZE, default: 1)
     - Add custom config for page size (DB_PAGE_SIZE, default: 100)
   - **Config priority** (handled by Quarkus automatically):
     - Environment variables (highest)
     - System properties
     - `application.properties` or custom config file
   - Note: JDBC drivers auto-detected via service-loader (JDBC 4.0+)

4. **Basic MCP Server Structure**
   - **Research Quarkiverse MCP extension first**: https://github.com/quarkiverse/quarkus-mcp-server
   - Review extension documentation for stdio server implementation
   - Create MCP server class using extension's interfaces/annotations
   - Verify server starts and responds to MCP protocol handshake
   - Use Quarkus Logging (don't add custom logging framework)

5. **CLI Interface for Testing**
   - Add `--cli` flag for one-shot CLI commands
   - Stateless execution - each command reads DB config from env vars
   - Commands:
     - `--cli introspect` - List all schemas/tables
     - `--cli introspect <schema>` - List tables in schema
     - `--cli introspect <schema> <table>` - Show table structure
     - `--cli query "<sql>"` - Execute SQL query (page 1)
     - `--cli query "<sql>" --page <n>` - Execute SQL query with pagination
   - Same table formatting as MCP tools
   - Usage: `./app --cli introspect public` or `./app-native --cli query "SELECT * FROM users"`
   - Uses same configuration as MCP mode (DB_URL, DB_USERNAME, DB_PASSWORD)

6. **Native Image Baseline**
   - Configure native build profile in pom.xml
   - Follow Quarkus native build guide: https://quarkus.io/guides/building-native-image
   - Add GraalVM native hints for JDBC drivers (if needed)
   - Test basic native compilation
   - Document native build process

7. **Basic Tests**
   - Unit test for configuration loading (env vars, config file)
   - Integration test for MCP server startup
   - Integration test for database connection (PostgreSQL and SQLite)
   - Verify tests pass in both JVM and native modes

### Acceptance Criteria
- [ ] Server starts successfully via stdio (JVM mode)
- [ ] MCP handshake completes
- [ ] Database connection established on startup
- [ ] Environment variables correctly mapped to datasource config
- [ ] Basic error handling for connection failures
- [ ] CLI interface works in JVM mode (`--cli` flag)
- [ ] All CLI commands functional (introspect, query with pagination)
- [ ] CLI uses same table formatting as MCP tools
- [ ] Native binary builds successfully
- [ ] Native binary starts and connects to database
- [ ] CLI interface works in native mode
- [ ] All tests pass in JVM mode
- [ ] All tests pass in native mode

### Files to Create/Modify
- `pom.xml` - Will be updated automatically by `mvn quarkus:add-extension`
- Native build profile configuration in pom.xml (manual)
- `src/main/java/org/geekden/mcp/DatabaseMcpServer.java` - Main MCP server class
- `src/main/java/org/geekden/mcp/config/DatabaseConfig.java` - Configuration
- `src/main/java/org/geekden/mcp/cli/CliCommandHandler.java` - CLI command parser and executor
- `src/main/java/org/geekden/MainApplication.java` - Update to handle `--cli` flag and route to MCP or CLI mode
- `src/main/resources/application.properties` - Quarkus config
- `src/main/resources/META-INF/native-image/` - Native image hints (if needed)

---

## Phase 2: CI/CD & Release Pipeline

**Goal**: Establish GitHub Actions workflows for automated testing, native builds, and release management. Ensures all subsequent phases can leverage CI/CD.

### Tasks
1. **GitHub Actions: Test Workflow**
   - Create `.github/workflows/test.yml`
   - Run on push to all branches and pull requests
   - Execute Maven tests in JVM mode
   - Run with PostgreSQL via service containers
   - Run with SQLite (embedded)
   - Report test coverage
   - Fail build on test failures

2. **GitHub Actions: Native Build Workflow**
   - Create `.github/workflows/native-build.yml`
   - Run on push to main and pull requests
   - Setup GraalVM environment
   - Execute native compilation: `mvn package -Dnative`
   - Verify native binary starts (<50ms startup)
   - Test basic MCP handshake in native mode
   - Archive native binary as artifact

3. **GitHub Actions: Release Workflow**
   - Create `.github/workflows/release.yml`
   - Trigger on git tags (e.g., `v1.0.0`)
   - Build both JVM JAR and native binaries
   - Create multi-platform native builds (Linux x64, optional: macOS, Windows)
   - Create GitHub release with:
     - Release notes
     - JVM JAR artifact
     - Native binary artifacts (per platform)
     - Checksums (SHA256)
   - Tag with semantic version

4. **Build Optimization**
   - Configure Maven caching in workflows
   - Optimize build times (parallel builds if applicable)
   - Add build badges to README (build status, test coverage)

5. **Documentation**
   - Document CI/CD pipeline in DEVELOPMENT.md
   - Document release process

### Acceptance Criteria
- [ ] Test workflow runs on every push/PR
- [ ] All tests pass in CI (PostgreSQL and SQLite)
- [ ] Native build workflow completes successfully
- [ ] Native binary starts successfully in CI
- [ ] Release workflow creates GitHub release on tags
- [ ] JAR and native binaries published as release artifacts
- [ ] Build badges visible in README
- [ ] CI/CD documented in DEVELOPMENT.md

### Files to Create/Modify
- `.github/workflows/test.yml` - Test automation
- `.github/workflows/native-build.yml` - Native compilation
- `.github/workflows/release.yml` - Release automation
- `README.md` - Add build badges
- `docs/DEVELOPMENT.md` - CI/CD documentation

---

## Phase 3: Database Introspection Tool

**Goal**: Implement the `introspect` MCP tool with hierarchical schema discovery. Maintain native compilation compatibility.

### Tasks
1. **Create Introspection Service**
   - Service class to query JDBC metadata
   - Methods for:
     - List all schemas
     - List tables/views in a schema
     - Get detailed table structure (columns, types, constraints)

2. **Implement MCP Tool: `introspect`**
   - Register tool with MCP server
   - Define parameters:
     - `schema` (optional string)
     - `table` (optional string)
   - Implement three behavior modes:
     - No params → all schemas, tables, views
     - `schema` only → tables/views in schema
     - `schema` + `table` → detailed table structure

3. **Schema Metadata Extraction**
   - Use `DatabaseMetaData` interface:
     - `getSchemas()` - list schemas
     - `getTables()` - list tables/views
     - `getColumns()` - column details
     - `getPrimaryKeys()` - PK constraints
     - `getImportedKeys()` - FK constraints
   - Parse NOT NULL constraints from column metadata

4. **Response Formatting**
   - Format as aligned text tables with Unicode separators (modern aesthetic)
   - Header separator: Unicode light horizontal (──) below column names
   - Footer separator: full-width Unicode light horizontal
   - Include relevant metadata (column names, types, constraints)
   - Exclude indexes (as per spec)

5. **Tests for Introspection**
   - Unit tests for IntrospectionService with test database
   - Test all three modes: all schemas, schema tables, table details
   - Test primary key extraction
   - Test foreign key extraction
   - Test NOT NULL constraint detection
   - Test table formatting (alignment, Unicode separators)
   - Integration tests with PostgreSQL and SQLite

6. **Native Compilation Verification**
   - Verify introspection tests pass in native mode
   - Verify DatabaseMetaData works in native binary
   - Add reflection hints if needed

### Acceptance Criteria
- [ ] `introspect()` returns all schemas and tables
- [ ] `introspect(schema="public")` returns tables in "public" schema
- [ ] `introspect(schema="public", table="users")` returns detailed structure
- [ ] Primary keys displayed correctly
- [ ] Foreign keys displayed correctly
- [ ] NOT NULL constraints shown
- [ ] Response uses aligned text table format with Unicode separators
- [ ] All introspection tests pass in JVM mode
- [ ] All introspection tests pass in native mode
- [ ] Native binary builds and introspection works

### Files to Create/Modify
- `src/main/java/org/geekden/mcp/service/IntrospectionService.java` - Metadata queries
- `src/main/java/org/geekden/mcp/tool/IntrospectTool.java` - MCP tool implementation
- `src/main/java/org/geekden/mcp/formatter/TableFormatter.java` - ASCII table formatting

---

## Phase 4: SQL Execution Tool

**Goal**: Implement the `execute_sql` MCP tool with pagination and result formatting. Maintain native compilation compatibility.

### Tasks
1. **Create SQL Execution Service**
   - Service class to execute arbitrary SQL
   - Support for all SQL types (SELECT, INSERT, UPDATE, DELETE, DDL)
   - Auto-commit transaction mode

2. **Implement MCP Tool: `execute_sql`**
   - Register tool with MCP server
   - Define parameters:
     - `query` (required string)
     - `page` (optional integer, default=1)

3. **Pagination Logic**
   - Detect if query is pageable (SELECT statements)
   - Use configurable page size from `DB_PAGE_SIZE` config (default: 100)
   - Inject LIMIT/OFFSET clause:
     - Fetch `PAGE_SIZE + 1` rows to detect more data
     - Formula: `LIMIT (PAGE_SIZE + 1) OFFSET (page-1)*PAGE_SIZE`
     - If `PAGE_SIZE + 1` rows returned → show first `PAGE_SIZE`, mark "more available"
     - If ≤ `PAGE_SIZE` rows returned → show all rows, mark "no more data"
   - Handle database-specific pagination syntax:
     - PostgreSQL/SQLite: `LIMIT ... OFFSET ...`
     - Future: SQL Server (`OFFSET ... FETCH NEXT ...`), Oracle (`FETCH FIRST`)

4. **Result Formatting**
   - Aligned text table format with Unicode separators (modern aesthetic)
   - Header separator: Unicode light horizontal (──) below column names
   - Footer separator: full-width Unicode light horizontal
   - Column headers in first row
   - NULL values displayed as `<null>`
   - Long text fields included as-is

5. **Pagination Metadata**
   - Display below footer separator
   - Format when more data available: "Page {n} (more available)"
   - Format for final page: "Page {n} (no more data)"
   - Example with more pages:
     ```
     id  name          email
     ──  ────────────  ──────────────────
      1  John Doe      john@example.com
      2  Jane Smith    <null>
     ... (98 more rows)
     ──────────────────────────────────────
     Page 1 (more available)
     ```
   - Example final page:
     ```
     id  name          email
     ──  ────────────  ──────────────────
     201  Alice Wong    alice@example.com
     ──────────────────────────────────────
     Page 3 (no more data)
     ```

6. **Non-SELECT Query Handling**
   - For INSERT/UPDATE/DELETE: return affected row count
   - For DDL: return success message
   - No pagination needed for these query types

7. **Tests for SQL Execution**
   - Unit tests for SqlExecutionService
   - Test SELECT query pagination (page 1, page 2, final page)
   - Test pagination metadata ("more available" vs "no more data")
   - Test INSERT/UPDATE/DELETE (affected row counts)
   - Test DDL statements
   - Test NULL value formatting
   - Test result set formatting (alignment, Unicode separators)
   - Test configurable page size (DB_PAGE_SIZE)
   - Integration tests with PostgreSQL and SQLite

8. **Native Compilation Verification**
   - Verify SQL execution tests pass in native mode
   - Verify ResultSet handling works in native binary
   - Verify pagination works in native mode

### Acceptance Criteria
- [ ] SELECT queries return paginated results (default: 100 rows per page)
- [ ] Page parameter works correctly (page=2 shows rows 101-200)
- [ ] Pagination metadata displayed correctly ("more available" vs "no more data")
- [ ] Response uses aligned text table format with Unicode separators
- [ ] NULL values shown as `<null>`
- [ ] INSERT/UPDATE/DELETE return affected row counts
- [ ] DDL statements execute successfully
- [ ] Configurable page size works (DB_PAGE_SIZE)
- [ ] All SQL execution tests pass in JVM mode
- [ ] All SQL execution tests pass in native mode
- [ ] Works with both PostgreSQL and SQLite

### Files to Create/Modify
- `src/main/java/org/geekden/mcp/service/SqlExecutionService.java` - SQL execution
- `src/main/java/org/geekden/mcp/tool/ExecuteSqlTool.java` - MCP tool implementation
- `src/main/java/org/geekden/mcp/pagination/PaginationHandler.java` - Pagination logic
- `src/main/java/org/geekden/mcp/formatter/ResultSetFormatter.java` - Result formatting

---

## Phase 5: Error Handling & Robustness

**Goal**: Implement proper error handling that passes through database errors to LLMs while ensuring clean MCP responses. Maintain native compilation compatibility.

### Tasks
1. **Database Error Handling**
   - Pass through raw database error messages (LLMs understand database errors well)
   - Catch and wrap SQLExceptions appropriately
   - Return database error messages in MCP error responses
   - No need to sanitize or simplify - preserve original error context
   - Log full exception details for debugging

2. **Connection Pool Configuration**
   - **Research Agroal documentation first**: https://quarkus.io/guides/datasource#agroal
   - **Configure Agroal via Quarkus properties** (don't implement custom pooling):
     - `quarkus.datasource.jdbc.max-size` - Pool size (default: 1)
     - `quarkus.datasource.jdbc.min-size` - Minimum connections
     - `quarkus.datasource.jdbc.validation-query-sql` - Connection validation
     - `quarkus.datasource.jdbc.acquisition-timeout` - Connection timeout
     - `quarkus.datasource.jdbc.idle-removal-interval` - Idle timeout
     - `quarkus.datasource.jdbc.max-lifetime` - Max connection lifetime
   - Let Agroal handle connection resilience (no manual retry logic)
   - Review health check configuration in Quarkus docs

3. **Input Validation**
   - Validate MCP tool parameters
   - Validate pagination parameters (page > 0)
   - Validate schema/table names for introspection
   - Return clear validation error messages

4. **MCP Error Response Format**
   - Return errors in proper MCP error format
   - Include error message from database (unmodified)
   - No stack traces in MCP responses
   - Log full stack traces server-side for debugging

5. **Tests for Error Handling**
   - Test SQL syntax error handling (raw error message preserved)
   - Test permission denied errors
   - Test constraint violation errors
   - Test connection failures
   - Test input validation (invalid page numbers, etc.)
   - Verify no stack traces in MCP responses
   - Verify errors logged with full details
   - Test Agroal pool configuration behaviors

6. **Native Compilation Verification**
   - Verify error handling tests pass in native mode
   - Verify exception messages work correctly in native binary
   - Ensure database error propagation works in native mode

### Acceptance Criteria
- [ ] Database errors returned in MCP error format with original error messages
- [ ] SQL syntax errors include raw database error (e.g., PostgreSQL/SQLite syntax errors)
- [ ] Connection failures handled by Agroal pool configuration
- [ ] Input validation errors return clear messages
- [ ] No stack traces in MCP responses (logged server-side only)
- [ ] All errors logged with full details for debugging
- [ ] Agroal connection pool properly configured (validation, timeouts, health checks)
- [ ] All error handling tests pass in JVM mode
- [ ] All error handling tests pass in native mode

### Files to Create/Modify
- `src/main/java/org/geekden/mcp/handler/McpErrorHandler.java` - MCP error response formatting
- `src/main/resources/application.properties` - Agroal pool configuration
- Update all service and tool classes with error handling

---

## Phase 6: Integration Testing & Documentation

**Goal**: End-to-end integration testing across databases and complete user/developer documentation.

### Tasks
1. **End-to-End Integration Tests**
   - Full MCP protocol workflow tests (handshake → tool calls → responses)
   - Real-world scenario tests:
     - Discover schema → query data → handle errors
     - Multi-page result navigation
     - DDL operations followed by introspection
   - Cross-database compatibility tests (PostgreSQL and SQLite)
   - CLI interface integration tests (all commands)
   - Test with Testcontainers for PostgreSQL
   - Test with embedded SQLite

2. **Native Binary Validation**
   - Run complete test suite in native mode
   - Verify all functionality works in native binary
   - Document any native-specific issues or workarounds
   - Performance baseline (startup time, memory usage)

3. **User Documentation**
   - Update README with:
     - Quick start guide
     - Installation instructions (JVM and native)
     - Configuration guide (env vars, config files)
     - Usage examples (MCP mode and CLI mode)
     - Supported databases
     - Troubleshooting common issues
   - Create user-facing examples:
     - Sample database schemas
     - Common AI agent workflows
     - Example MCP client configurations

4. **Developer Documentation**
   - Create DEVELOPMENT.md:
     - Build instructions (JVM and native)
     - Testing guidelines
     - Architecture overview
     - Code structure
     - Adding new database support
   - Add JavaDoc to public APIs
   - Document Agroal pool configuration options
   - Document native compilation process and requirements

### Acceptance Criteria
- [ ] All integration tests pass in JVM mode
- [ ] All integration tests pass in native mode
- [ ] End-to-end MCP workflows verified
- [ ] Cross-database compatibility verified (PostgreSQL and SQLite)
- [ ] CLI integration tests pass
- [ ] README complete with quick start and examples
- [ ] DEVELOPMENT.md complete
- [ ] JavaDoc added to public APIs
- [ ] Native compilation guide documented
- [ ] All CI/CD workflows passing

### Files to Create/Modify
- `src/test/java/org/geekden/mcp/integration/**/*IntegrationTest.java` - E2E tests
- `README.md` - User documentation
- `docs/DEVELOPMENT.md` - Developer documentation
- `docs/examples/*` - Example scenarios and schemas

---

## Phase 7: Performance Optimization (Optional)

**Goal**: Add caching, query optimization, and performance monitoring to reduce latency and token usage.

### Tasks
1. **Schema Metadata Caching**
   - Implement in-memory cache for schema metadata
   - Cache key: schema + table name
   - Cache introspection results (schemas, tables, columns, constraints)
   - TTL-based or manual invalidation strategy
   - Reduces repeated DatabaseMetaData queries

2. **Query Result Caching**
   - Cache SELECT query results
   - Cache key: query hash + page number
   - Configurable TTL (default: 60 seconds)
   - Only cache SELECT queries, not DML/DDL
   - Add MCP tool parameter to bypass cache

3. **Connection Pool Optimization**
   - Tune connection pool settings
   - Add connection health checks
   - Monitor connection usage patterns
   - Add metrics for connection acquisition time

4. **Performance Monitoring**
   - Add query execution time logging
   - Track cache hit/miss ratios
   - Monitor memory usage over time
   - Log slow queries (>1 second threshold)

5. **Additional Database Support**
   - Add and test MySQL driver (optional)
   - Document database-specific quirks
   - Update pagination logic for SQL Server and Oracle (optional)

6. **Native Binary Optimization**
   - Profile native binary performance
   - Optimize memory allocation
   - Reduce binary size if possible
   - Test with multiple JDBC drivers in native mode

### Acceptance Criteria
- [ ] Schema metadata cached and reduces query time by >50%
- [ ] Query result cache working with configurable TTL
- [ ] Cache hit ratio >70% in typical workflows
- [ ] Performance metrics logged correctly
- [ ] Additional database drivers tested (if implemented)
- [ ] Native binary size documented
- [ ] Memory usage stable over 1000+ queries

### Files to Create/Modify
- `src/main/java/org/geekden/mcp/cache/SchemaCache.java` - Schema caching
- `src/main/java/org/geekden/mcp/cache/QueryResultCache.java` - Query result caching
- `src/main/java/org/geekden/mcp/metrics/PerformanceMonitor.java` - Metrics
- `src/main/java/org/geekden/mcp/config/CacheConfig.java` - Cache configuration
- Update `application.properties` with cache settings
- `docs/PERFORMANCE.md` - Performance tuning guide

---

## Implementation Notes

### Handoff Checklist
For each phase completion:
- [ ] All acceptance criteria met (including native compilation)
- [ ] Platform capabilities researched and leveraged (no reinventing the wheel)
- [ ] Quarkus/platform documentation consulted for built-in features
- [ ] Code committed to feature branch
- [ ] Tests passing (JVM and native modes)
- [ ] Native binary builds successfully
- [ ] Documentation updated
- [ ] Pull request created with:
  - Phase summary
  - Platform features leveraged (e.g., "Used MicroProfile Config instead of custom config")
  - Testing evidence (JVM and native)
  - Native-specific issues encountered
  - Known issues/limitations
  - Next phase dependencies

### Testing Strategy (TDD Approach)
- **Each Phase**: Write tests alongside implementation (not after)
- **Phase 1**: Basic tests for configuration and connectivity
- **Phases 3-5**: Feature-specific unit and integration tests
- **Phase 6**: End-to-end integration tests and documentation
- **Benefit**: Catch bugs early, tests guide design, better code quality

### Native Compilation Strategy
- **Phase 1**: Establish native compilation baseline
- **Phase 2**: Set up CI/CD to verify native builds continuously
- **Phases 3-5**: Verify each feature works in native mode as it's added (tests pass in native)
- **Phase 6**: Comprehensive end-to-end native validation
- **Benefit**: Issues traced to specific features via CI, not debugging at the end

### Dependencies Between Phases
- Phase 2 depends on Phase 1 (foundation + native baseline required for CI/CD)
- Phase 3 depends on Phases 1-2 (foundation + CI/CD required)
- Phase 4 depends on Phases 1-2 (foundation + CI/CD required)
- Phase 5 can run in parallel with Phases 3-4 (enhance with error handling)
- Phase 6 depends on Phases 1-5 (all features complete for end-to-end testing)
- Phase 7 depends on Phases 1-6 (all features complete before optimization)

### Recommended Order
1. **Phase 1** → Foundation + Native Baseline (required first)
2. **Phase 2** → CI/CD & Release Pipeline (enables continuous verification)
3. **Phase 3 + Phase 4** → Can be parallel development (different developers)
4. **Phase 5** → Integrate error handling into existing code
5. **Phase 6** → End-to-end integration testing and documentation
6. **Phase 7** → Optional performance optimization (caching, etc.)

### Estimated Effort
- Phase 1: 3-4 days (foundation + native setup)
- Phase 2: 1-2 days (CI/CD setup)
- Phase 3: 2-3 days (introspection + native verification)
- Phase 4: 3-4 days (pagination + native verification)
- Phase 5: 1-2 days (error handling + native verification)
- Phase 6: 3-4 days (comprehensive testing + native docs)
- Phase 7: 2-3 days (optimization - optional)

**Total Core (Phases 1-6): 13-19 days** (single developer, sequential)
**Parallel Core: 10-15 days** (2 developers, Phases 3+4 parallel)
**With Optimization (Phase 7): 15-22 days total**

---

## Success Metrics

Track these throughout implementation:

### Core Requirements (Phases 1-6)
1. **Functionality**: All spec requirements implemented ✓
2. **Database Support**: PostgreSQL and SQLite tested and working ✓
3. **Token Efficiency**: Aligned text tables more compact than JSON ✓
4. **Performance**: Fast query execution and reasonable startup time ✓
5. **Reliability**: Tests pass consistently across databases ✓
6. **Code Quality**: Good test coverage with tests written per phase ✓
7. **Native Compilation**: Binary builds and runs successfully ✓
8. **CI/CD**: Automated tests, native builds, and releases ✓

### Optional Enhancements (Phase 7)
1. **Caching**: Schema metadata cached with good hit ratio ✓
2. **Additional DBs**: MySQL, SQL Server, Oracle (optional) ✓
3. **Optimized Performance**: Improved query latency with caching ✓

---

## Questions for Clarification

Before starting implementation, consider:

1. **MCP Extension Version**: Is `quarkus-mcp-server-stdio` stable? Check Quarkiverse docs.
2. **Quarkus Version**: Spec says 3.27.0, but pom.xml has 3.8.1. Upgrade needed?
3. **Database Priorities**: ✓ PostgreSQL + SQLite are core (confirmed)
4. **Extension Installation**: ✓ Use `mvn quarkus:add-extension` for all extensions (confirmed)
5. **CI/CD Setup**: ✓ GitHub Actions for tests, native builds, and releases (Phase 2)
6. **Pagination Strategy**: Should we auto-detect pagination support per database?
7. **Native Drivers**: PostgreSQL and SQLite drivers work well with GraalVM
8. **Testing Infrastructure**: ✓ Testcontainers for PostgreSQL in CI (confirmed)
9. **Phase 7 Scope**: Is performance optimization needed, or stop after Phase 6?

---

## Reference Documentation

Key resources for implementation:

- **Quarkus Native Build Guide**: https://quarkus.io/guides/building-native-image
- **Quarkus JDBC**: https://quarkus.io/guides/datasource
- **Quarkus Agroal**: https://quarkus.io/guides/datasource#agroal
- **MCP Protocol Specification**: https://modelcontextprotocol.io/
- **Quarkiverse MCP Extension**: https://github.com/quarkiverse/quarkus-mcp-server
