# MCP Database Server - Implementation Phases

This document breaks down the implementation of the MCP Database Server into manageable phases for handoff and incremental development.

## Phase 1: Foundation & Project Setup

**Goal**: Establish the base project structure with MCP server capabilities, database connectivity, and native compilation working from the start.

### Tasks
1. **Add Quarkus MCP Extension**
   - Add `io.quarkiverse.mcp:quarkus-mcp-server-stdio` dependency to pom.xml
   - Verify MCP extension is compatible with Quarkus 3.27.0 (may need to update from 3.8.1)

2. **Database Dependencies**
   - Add Quarkus JDBC extension (`quarkus-jdbc`)
   - Add Agroal connection pooling (`quarkus-agroal`)
   - Add sample JDBC drivers for testing:
     - H2 (embedded, for testing)
     - PostgreSQL (prioritize for native compatibility)

3. **Configuration Setup**
   - Create configuration properties class for database settings
   - Map environment variables to Quarkus config:
     - `DB_URL` → datasource URL
     - `DB_USERNAME` → datasource username
     - `DB_PASSWORD` → datasource password
     - `DB_DRIVER` → datasource driver (optional, auto-detect)
   - Set connection pool to size=1 by default

4. **Basic MCP Server Structure**
   - Create MCP server class (implements MCP stdio server interface)
   - Verify server starts and responds to MCP protocol handshake
   - Add basic logging configuration

5. **Native Image Baseline**
   - Configure native build profile in pom.xml
   - Add GraalVM native hints for JDBC drivers
   - Test basic native compilation
   - Document native build process
   - Establish baseline startup time and memory usage

### Acceptance Criteria
- [ ] Server starts successfully via stdio (JVM mode)
- [ ] MCP handshake completes
- [ ] Database connection established on startup
- [ ] Environment variables correctly mapped to datasource config
- [ ] Basic error handling for connection failures
- [ ] **Native binary builds successfully**
- [ ] **Native binary starts and connects to database**
- [ ] **Native startup time <50ms documented**

### Files to Create/Modify
- `pom.xml` - Add dependencies and native profile
- `src/main/java/org/geekden/mcp/DatabaseMcpServer.java` - Main MCP server class
- `src/main/java/org/geekden/mcp/config/DatabaseConfig.java` - Configuration
- `src/main/resources/application.properties` - Quarkus config
- `src/main/resources/META-INF/native-image/` - Native image hints (if needed)

---

## Phase 2: Database Introspection Tool

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
   - Format as ASCII tables for token efficiency
   - Include relevant metadata (column names, types, constraints)
   - Exclude indexes (as per spec)

5. **Native Compilation Verification**
   - Test introspection in native mode
   - Verify DatabaseMetaData works in native binary
   - Add reflection hints if needed

### Acceptance Criteria
- [ ] `introspect()` returns all schemas and tables
- [ ] `introspect(schema="public")` returns tables in "public" schema
- [ ] `introspect(schema="public", table="users")` returns detailed structure
- [ ] Primary keys displayed correctly
- [ ] Foreign keys displayed correctly
- [ ] NOT NULL constraints shown
- [ ] Response uses ASCII table format
- [ ] **Native binary builds and introspection works**

### Files to Create/Modify
- `src/main/java/org/geekden/mcp/service/IntrospectionService.java` - Metadata queries
- `src/main/java/org/geekden/mcp/tool/IntrospectTool.java` - MCP tool implementation
- `src/main/java/org/geekden/mcp/formatter/TableFormatter.java` - ASCII table formatting

---

## Phase 3: SQL Execution Tool

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
   - Inject LIMIT/OFFSET clause:
     - Page size: 100 rows
     - Formula: `LIMIT 100 OFFSET (page-1)*100`
   - Handle database-specific pagination syntax:
     - PostgreSQL/MySQL: `LIMIT ... OFFSET ...`
     - SQL Server: `OFFSET ... FETCH NEXT ...`
     - Oracle: `ROWNUM` or `FETCH FIRST`

4. **Result Formatting**
   - ASCII table format for result sets
   - Column headers in first row
   - NULL values displayed as `<null>`
   - Long text fields included as-is

5. **Pagination Metadata**
   - Current page number
   - Has more pages boolean (detect via COUNT or result size)
   - Row count for current page
   - Display format:
     ```
     Page 1 of results (more pages available: true)

     [table data]

     (100 rows)
     ```

6. **Non-SELECT Query Handling**
   - For INSERT/UPDATE/DELETE: return affected row count
   - For DDL: return success message
   - No pagination needed for these query types

7. **Native Compilation Verification**
   - Test SQL execution in native mode
   - Verify ResultSet handling works in native binary
   - Test pagination in native mode

### Acceptance Criteria
- [ ] SELECT queries return paginated results (100 rows max)
- [ ] Page parameter works correctly (page=2 shows rows 101-200)
- [ ] Pagination metadata displayed correctly
- [ ] NULL values shown as `<null>`
- [ ] INSERT/UPDATE/DELETE return affected row counts
- [ ] DDL statements execute successfully
- [ ] Works across PostgreSQL and H2 (minimum)
- [ ] **Native binary builds and SQL execution works**

### Files to Create/Modify
- `src/main/java/org/geekden/mcp/service/SqlExecutionService.java` - SQL execution
- `src/main/java/org/geekden/mcp/tool/ExecuteSqlTool.java` - MCP tool implementation
- `src/main/java/org/geekden/mcp/pagination/PaginationHandler.java` - Pagination logic
- `src/main/java/org/geekden/mcp/formatter/ResultSetFormatter.java` - Result formatting

---

## Phase 4: Error Handling & Robustness

**Goal**: Implement comprehensive error handling with user-friendly messages. Maintain native compilation compatibility.

### Tasks
1. **Error Classification**
   - Create exception hierarchy:
     - `SqlSyntaxException` - SQL syntax errors
     - `PermissionDeniedException` - Access denied
     - `ConnectionException` - Database connection issues
     - `ConstraintViolationException` - Constraint violations

2. **Error Message Formatting**
   - Map database exceptions to user-friendly messages:
     - SQL Syntax: "SQL syntax error: [descriptive message]"
     - Permission: "Access denied: [operation] on [object]"
     - Connection: "Database connection error: [details]"
     - Constraint: "Constraint violation: [constraint name] - [details]"
   - Strip stack traces from MCP responses
   - Log full stack traces for debugging

3. **Connection Resilience**
   - Connection validation before queries
   - Retry logic for transient failures
   - Graceful handling of connection pool exhaustion

4. **Input Validation**
   - Validate MCP tool parameters
   - Validate pagination parameters (page > 0)
   - Handle malformed SQL gracefully

5. **Native Compilation Verification**
   - Test error handling in native mode
   - Verify exception messages work correctly in native binary

### Acceptance Criteria
- [ ] SQL syntax errors return clean error messages
- [ ] Permission denied errors are user-friendly
- [ ] Connection failures handled gracefully
- [ ] Constraint violations show constraint name and details
- [ ] No stack traces exposed to MCP clients
- [ ] All errors logged with full details for debugging
- [ ] **Error handling works identically in native mode**

### Files to Create/Modify
- `src/main/java/org/geekden/mcp/exception/*` - Exception classes
- `src/main/java/org/geekden/mcp/handler/ErrorHandler.java` - Error formatting
- Update all service and tool classes with error handling

---

## Phase 5: Testing & Documentation

**Goal**: Comprehensive testing across multiple databases and complete documentation. Validate native compilation works end-to-end.

### Tasks
1. **Unit Tests**
   - Test introspection service with H2 database
   - Test SQL execution with various query types
   - Test pagination logic
   - Test error handling scenarios
   - Test ASCII table formatting edge cases

2. **Integration Tests**
   - Test with PostgreSQL (Testcontainers)
   - Test with H2 (embedded)
   - Verify cross-database compatibility
   - Test MCP protocol end-to-end

3. **Native Binary Testing**
   - Run full test suite in native mode
   - Verify startup time <50ms
   - Verify memory usage <50MB
   - Test all MCP tools in native mode
   - Document native-specific issues and workarounds

4. **MCP Protocol Tests**
   - Test MCP handshake
   - Test tool registration
   - Test tool invocation and responses
   - Test error responses in MCP format

5. **Documentation**
   - Update README with:
     - Installation instructions (JVM and native)
     - Configuration guide
     - Usage examples
     - Supported databases
     - Native compilation guide
   - Create DEVELOPMENT.md with:
     - Build instructions (JVM and native)
     - Testing guidelines
     - Architecture overview
     - Troubleshooting section
   - Add JavaDoc comments to public APIs

6. **Example Scenarios**
   - Create sample database schemas
   - Document common AI agent workflows
   - Add example MCP client interactions

### Acceptance Criteria
- [ ] All unit tests passing (JVM and native)
- [ ] Integration tests passing for PostgreSQL and H2
- [ ] MCP protocol compliance verified
- [ ] Native binary startup time <50ms
- [ ] Native binary memory usage <50MB
- [ ] README complete with usage examples
- [ ] DEVELOPMENT.md created
- [ ] JavaDoc coverage >80%
- [ ] Native compilation documented

### Files to Create/Modify
- `src/test/java/org/geekden/mcp/**/*Test.java` - Test classes
- `README.md` - User documentation
- `docs/DEVELOPMENT.md` - Developer documentation
- `docs/NATIVE.md` - Native compilation guide
- `docs/examples/*` - Example scenarios

---

## Phase 6: Performance Optimization (Optional)

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
   - Add and test MySQL driver
   - Add and test SQLite driver
   - Document database-specific quirks
   - Update pagination logic for SQL Server and Oracle

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
- [ ] MySQL and SQLite drivers tested and working
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
- [ ] Code committed to feature branch
- [ ] Tests passing (JVM and native modes)
- [ ] Native binary builds successfully
- [ ] Documentation updated
- [ ] Pull request created with:
  - Phase summary
  - Testing evidence (JVM and native)
  - Native-specific issues encountered
  - Known issues/limitations
  - Next phase dependencies

### Native Compilation Strategy
- **Phase 1**: Establish native compilation baseline
- **Phases 2-4**: Verify each feature works in native mode as it's added
- **Phase 5**: Comprehensive native testing and documentation
- **Benefit**: Issues traced to specific features, not debugging at the end

### Dependencies Between Phases
- Phase 2 depends on Phase 1 (foundation + native baseline required)
- Phase 3 depends on Phase 1 (foundation + native baseline required)
- Phase 4 can run in parallel with Phases 2-3 (enhance with error handling)
- Phase 5 depends on Phases 1-4 (testing integration)
- Phase 6 depends on Phases 1-5 (all features complete before optimization)

### Recommended Order
1. **Phase 1** → Foundation + Native Baseline (required first)
2. **Phase 2 + Phase 3** → Can be parallel development (different developers)
3. **Phase 4** → Integrate error handling into existing code
4. **Phase 5** → Testing, documentation, and native validation
5. **Phase 6** → Optional performance optimization (caching, etc.)

### Estimated Effort
- Phase 1: 3-4 days (foundation + native setup)
- Phase 2: 2-3 days (introspection + native verification)
- Phase 3: 3-4 days (pagination + native verification)
- Phase 4: 1-2 days (error handling + native verification)
- Phase 5: 3-4 days (comprehensive testing + native docs)
- Phase 6: 2-3 days (optimization - optional)

**Total Core (Phases 1-5): 12-17 days** (single developer, sequential)
**Parallel Core: 9-13 days** (2 developers, Phases 2+3 parallel)
**With Optimization (Phase 6): 14-20 days total**

---

## Success Metrics

Track these throughout implementation:

### Core Requirements (Phases 1-5)
1. **Functionality**: All spec requirements implemented ✓
2. **Database Support**: PostgreSQL and H2 tested and working ✓
3. **Token Efficiency**: Response sizes <50% of JSON equivalent ✓
4. **Performance**: Query latency <100ms (without caching) ✓
5. **Reliability**: Error rate <1% in test scenarios ✓
6. **Code Quality**: Test coverage >80% ✓
7. **Native Compilation**: Binary builds, starts <50ms, uses <50MB ✓

### Optional Enhancements (Phase 6)
1. **Caching**: Schema metadata cached, >70% hit ratio ✓
2. **Additional DBs**: MySQL and SQLite tested ✓
3. **Optimized Performance**: Query latency <50ms with caching ✓

---

## Questions for Clarification

Before starting implementation, consider:

1. **MCP Extension Version**: Is `quarkus-mcp-server-stdio` stable? Check Quarkiverse docs.
2. **Quarkus Version**: Spec says 3.27.0, but pom.xml has 3.8.1. Upgrade needed?
3. **Database Priorities**: PostgreSQL + H2 are core. MySQL/SQLite in Phase 6?
4. **Pagination Strategy**: Should we auto-detect pagination support per database?
5. **Native Drivers**: Which JDBC drivers have best GraalVM native support?
6. **Testing Infrastructure**: Are Testcontainers acceptable for CI/CD?
7. **Phase 6 Scope**: Is performance optimization needed, or stop after Phase 5?
