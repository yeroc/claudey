# MCP Database Server - Implementation Phases

This document breaks down the implementation of the MCP Database Server into manageable phases for handoff and incremental development.

## Phase 1: Foundation & Project Setup

**Goal**: Establish the base project structure with MCP server capabilities and database connectivity.

### Tasks
1. **Add Quarkus MCP Extension**
   - Add `io.quarkiverse.mcp:quarkus-mcp-server-stdio` dependency to pom.xml
   - Verify MCP extension is compatible with Quarkus 3.27.0 (may need to update from 3.8.1)

2. **Database Dependencies**
   - Add Quarkus JDBC extension (`quarkus-jdbc`)
   - Add Agroal connection pooling (`quarkus-agroal`)
   - Add sample JDBC drivers for testing:
     - H2 (embedded, for testing)
     - PostgreSQL
     - MySQL

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

### Acceptance Criteria
- [ ] Server starts successfully via stdio
- [ ] MCP handshake completes
- [ ] Database connection established on startup
- [ ] Environment variables correctly mapped to datasource config
- [ ] Basic error handling for connection failures

### Files to Create/Modify
- `pom.xml` - Add dependencies
- `src/main/java/org/geekden/mcp/DatabaseMcpServer.java` - Main MCP server class
- `src/main/java/org/geekden/mcp/config/DatabaseConfig.java` - Configuration
- `src/main/resources/application.properties` - Quarkus config

---

## Phase 2: Database Introspection Tool

**Goal**: Implement the `introspect` MCP tool with hierarchical schema discovery.

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

5. **Caching Layer**
   - Implement in-memory cache for schema metadata
   - Cache key: schema + table name
   - TTL or manual invalidation strategy
   - Reduces repeated metadata queries

### Acceptance Criteria
- [ ] `introspect()` returns all schemas and tables
- [ ] `introspect(schema="public")` returns tables in "public" schema
- [ ] `introspect(schema="public", table="users")` returns detailed structure
- [ ] Primary keys displayed correctly
- [ ] Foreign keys displayed correctly
- [ ] NOT NULL constraints shown
- [ ] Response uses ASCII table format
- [ ] Schema metadata cached to reduce database queries

### Files to Create/Modify
- `src/main/java/org/geekden/mcp/service/IntrospectionService.java` - Metadata queries
- `src/main/java/org/geekden/mcp/tool/IntrospectTool.java` - MCP tool implementation
- `src/main/java/org/geekden/mcp/formatter/TableFormatter.java` - ASCII table formatting
- `src/main/java/org/geekden/mcp/cache/SchemaCache.java` - Caching layer

---

## Phase 3: SQL Execution Tool

**Goal**: Implement the `execute_sql` MCP tool with pagination and result formatting.

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

### Acceptance Criteria
- [ ] SELECT queries return paginated results (100 rows max)
- [ ] Page parameter works correctly (page=2 shows rows 101-200)
- [ ] Pagination metadata displayed correctly
- [ ] NULL values shown as `<null>`
- [ ] INSERT/UPDATE/DELETE return affected row counts
- [ ] DDL statements execute successfully
- [ ] Works across PostgreSQL, MySQL, H2 (minimum)

### Files to Create/Modify
- `src/main/java/org/geekden/mcp/service/SqlExecutionService.java` - SQL execution
- `src/main/java/org/geekden/mcp/tool/ExecuteSqlTool.java` - MCP tool implementation
- `src/main/java/org/geekden/mcp/pagination/PaginationHandler.java` - Pagination logic
- `src/main/java/org/geekden/mcp/formatter/ResultSetFormatter.java` - Result formatting

---

## Phase 4: Error Handling & Robustness

**Goal**: Implement comprehensive error handling with user-friendly messages.

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

### Acceptance Criteria
- [ ] SQL syntax errors return clean error messages
- [ ] Permission denied errors are user-friendly
- [ ] Connection failures handled gracefully
- [ ] Constraint violations show constraint name and details
- [ ] No stack traces exposed to MCP clients
- [ ] All errors logged with full details for debugging

### Files to Create/Modify
- `src/main/java/org/geekden/mcp/exception/*` - Exception classes
- `src/main/java/org/geekden/mcp/handler/ErrorHandler.java` - Error formatting
- Update all service and tool classes with error handling

---

## Phase 5: Testing & Documentation

**Goal**: Comprehensive testing across multiple databases and complete documentation.

### Tasks
1. **Unit Tests**
   - Test introspection service with H2 database
   - Test SQL execution with various query types
   - Test pagination logic
   - Test error handling scenarios
   - Test caching behavior

2. **Integration Tests**
   - Test with PostgreSQL (Testcontainers)
   - Test with MySQL (Testcontainers)
   - Test with H2 (embedded)
   - Verify cross-database compatibility

3. **MCP Protocol Tests**
   - Test MCP handshake
   - Test tool registration
   - Test tool invocation and responses
   - Test error responses in MCP format

4. **Documentation**
   - Update README with:
     - Installation instructions
     - Configuration guide
     - Usage examples
     - Supported databases
   - Create DEVELOPMENT.md with:
     - Build instructions
     - Testing guidelines
     - Architecture overview
   - Add JavaDoc comments to public APIs

5. **Example Scenarios**
   - Create sample database schemas
   - Document common AI agent workflows
   - Add example MCP client interactions

### Acceptance Criteria
- [ ] All unit tests passing
- [ ] Integration tests passing for PostgreSQL, MySQL, H2
- [ ] MCP protocol compliance verified
- [ ] README complete with usage examples
- [ ] DEVELOPMENT.md created
- [ ] JavaDoc coverage >80%

### Files to Create/Modify
- `src/test/java/org/geekden/mcp/**/*Test.java` - Test classes
- `README.md` - User documentation
- `docs/DEVELOPMENT.md` - Developer documentation
- `docs/examples/*` - Example scenarios

---

## Phase 6: Native Compilation (Stretch Goal)

**Goal**: Create GraalVM native image for faster startup and lower memory footprint.

### Tasks
1. **GraalVM Configuration**
   - Add GraalVM native profile to pom.xml
   - Configure native-image build settings
   - Test basic native compilation

2. **Reflection Configuration**
   - Identify classes requiring reflection:
     - JDBC drivers
     - MCP protocol classes
     - Jackson serialization
   - Add `reflect-config.json` if needed
   - Add `resource-config.json` for bundled resources

3. **Driver-Specific Handling**
   - Test native compilation with each JDBC driver
   - Add driver-specific native image hints
   - Document which drivers work in native mode

4. **Performance Validation**
   - Compare startup time: JVM vs native
   - Compare memory usage: JVM vs native
   - Validate functionality identical in native mode

### Acceptance Criteria
- [ ] Native binary builds successfully
- [ ] Native binary starts in <50ms
- [ ] All MCP tools work in native mode
- [ ] At least PostgreSQL and H2 drivers work natively
- [ ] Memory usage <50MB for native binary

### Files to Create/Modify
- `pom.xml` - Native profile configuration
- `src/main/resources/META-INF/native-image/reflect-config.json` - Reflection config
- `docs/NATIVE.md` - Native compilation guide

---

## Implementation Notes

### Handoff Checklist
For each phase completion:
- [ ] All acceptance criteria met
- [ ] Code committed to feature branch
- [ ] Tests passing
- [ ] Documentation updated
- [ ] Pull request created with:
  - Phase summary
  - Testing evidence
  - Known issues/limitations
  - Next phase dependencies

### Dependencies Between Phases
- Phase 2 depends on Phase 1 (foundation required)
- Phase 3 depends on Phase 1 (foundation required)
- Phase 4 can run in parallel with Phases 2-3 (enhance with error handling)
- Phase 5 depends on Phases 1-4 (testing integration)
- Phase 6 depends on Phases 1-5 (all features complete)

### Recommended Order
1. **Phase 1** → Foundation (required first)
2. **Phase 2 + Phase 3** → Can be parallel development (different developers)
3. **Phase 4** → Integrate error handling into existing code
4. **Phase 5** → Testing and documentation
5. **Phase 6** → Optional/stretch goal

### Estimated Effort
- Phase 1: 2-3 days (foundation work)
- Phase 2: 2-3 days (introspection complexity)
- Phase 3: 3-4 days (pagination and formatting)
- Phase 4: 1-2 days (error handling)
- Phase 5: 2-3 days (comprehensive testing)
- Phase 6: 2-3 days (native compilation challenges)

**Total: 12-18 days** (single developer, sequential)
**Parallel: 8-12 days** (2 developers, Phases 2+3 parallel)

---

## Success Metrics

Track these throughout implementation:

1. **Functionality**: All spec requirements implemented ✓
2. **Database Support**: PostgreSQL, MySQL, H2, SQLite tested ✓
3. **Token Efficiency**: Response sizes <50% of JSON equivalent ✓
4. **Performance**: Query latency <100ms (vs CLI) ✓
5. **Reliability**: Error rate <1% in test scenarios ✓
6. **Code Quality**: Test coverage >80% ✓

---

## Questions for Clarification

Before starting implementation, consider:

1. **MCP Extension Version**: Is `quarkus-mcp-server-stdio` stable? Check Quarkiverse docs.
2. **Quarkus Version**: Spec says 3.27.0, but pom.xml has 3.8.1. Upgrade needed?
3. **Database Priorities**: Which databases are must-have vs nice-to-have?
4. **Pagination Strategy**: Should we auto-detect pagination support per database?
5. **Cache Invalidation**: How should schema cache be invalidated? TTL? Manual?
6. **Testing Infrastructure**: Are Testcontainers acceptable for CI/CD?
