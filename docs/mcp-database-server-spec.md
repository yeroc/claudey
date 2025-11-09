# MCP Database Server - Product Requirements Document

## Overview

A Model Context Protocol (MCP) stdio server that exposes database access in a token-efficient manner for AI agents. Primary use case is development and testing workflows.

## Goals

- Provide AI agents with structured database access via MCP protocol
- Minimize token usage in responses
- Leverage connection pooling and caching for improved latency
- Support database-agnostic connectivity via JDBC
- Enable natural language to SQL workflows

## Non-Goals

- Production data access (initially)
- Multi-database connection management
- Complex transaction orchestration
- Query optimization tooling (indexes, explain plans, etc.)

## Architecture

### Transport
- **Protocol**: Model Context Protocol (MCP) over stdio
- **Message Format**: JSON-RPC 2.0, newline-delimited
- **Connection Model**: 1:1 relationship between MCP server instance and database connection

### Database Connectivity
- **Driver**: JDBC (database-agnostic)
- **Connection**: Single persistent connection per server instance
- **Configuration**: Environment variables (with Quarkus config support for CLI/file overrides)
- **Permissions**: Rely on database-level access control

### Transaction Model
- Each SQL query executes in its own transaction
- No support for multi-statement transactions
- Auto-commit mode

## MCP Capabilities

### Tools

#### 1. `introspect`
Hierarchical schema introspection with optional scope narrowing.

**Parameters:**
- `schema` (optional string): Database schema name
- `table` (optional string): Table name (requires schema parameter)

**Behavior:**
- `introspect()` → Full database schema (all schemas, tables, views)
- `introspect(schema="public")` → Tables/views in specified schema
- `introspect(schema="public", table="users")` → Detailed table structure

**Response includes:**
- Table and view names
- Column names and data types
- Primary keys
- Foreign keys
- NOT NULL constraints

**Excludes:**
- Indexes (users can query system tables if needed)

#### 2. `execute_sql`
Execute arbitrary SQL queries with automatic pagination.

**Parameters:**
- `query` (required string): SQL query to execute
- `page` (optional integer, default=1): Page number (1-indexed)

**Behavior:**
- Results limited to configurable page size (default: 100 rows, set via `DB_PAGE_SIZE`)
- Uses LIMIT/OFFSET for pagination:
  - Fetches `PAGE_SIZE + 1` rows to detect if more data exists
  - Shows first `PAGE_SIZE` rows if all `PAGE_SIZE + 1` were returned
  - Shows all rows if ≤ `PAGE_SIZE` were returned
- Supports all SQL operations (SELECT, INSERT, UPDATE, DELETE, DDL)

**Response Format:**
- Aligned text table format with Unicode separators
- Column headers in first row
- NULL values displayed as `<null>`
- Long text fields returned as-is
- Pagination metadata (below footer separator):
  - Format with more pages: "Page {n} (more available)"
  - Format for final page: "Page {n} (no more data)"

**Example Response (with more pages):**
```
id  name          email
──  ────────────  ──────────────────
 1  John Doe      john@example.com
 2  Jane Smith    <null>
... (98 more rows)
──────────────────────────────────────
Page 1 (more available)
```

**Example Response (final page):**
```
id  name          email
──  ────────────  ──────────────────
201  Alice Wong    alice@example.com
──────────────────────────────────────
Page 3 (no more data)
```

### Resources

Not using MCP resources in initial version. May revisit for schema discovery UX improvements.

## Error Handling

Database errors are passed through to AI agents with original error messages:

- **SQL Syntax Errors**: Raw database error messages (e.g., PostgreSQL/SQLite syntax errors)
- **Permission Denied**: Raw database permission errors
- **Connection Failures**: Connection error details from database/pool
- **Constraint Violations**: Raw constraint violation messages from database

Rationale: LLMs are trained on database documentation and error messages, so raw errors provide better context than sanitized versions.

No stack traces exposed to clients (logged server-side only).

Connection resilience handled by Agroal connection pool configuration.

## Configuration

Configuration can be provided via multiple sources (in priority order):
1. Environment variables
2. Runtime config file (`database-server.properties` or `mcp-db.properties`)
3. Default `application.properties`

### Required Configuration
- `DB_URL`: JDBC connection URL (e.g., `jdbc:postgresql://localhost:5432/mydb`, `jdbc:sqlite:./test.db`)
- `DB_USERNAME`: Database username
- `DB_PASSWORD`: Database password

### Optional Configuration
- `DB_POOL_SIZE`: Connection pool size (default: 1 for single connection)
- `DB_PAGE_SIZE`: Page size for query results (default: 100 rows, compile-time config)

### Driver Auto-Detection
- JDBC 4.0+ drivers are auto-detected via service-loader mechanism
- No need to specify driver class name
- Driver determined from JDBC URL prefix (e.g., `jdbc:postgresql:` → PostgreSQL driver)

## Token Efficiency Strategy

1. **Minimal Tool Count**: Only 2 tools vs. many specialized operations
2. **Hierarchical Parameters**: Single `introspect` tool handles multiple granularities
3. **Aligned Text Table Format**: More compact than JSON arrays of objects
   - Column names stated once (not repeated per row)
   - Minimal Unicode separators for modern aesthetic
   - Footer separator delimits data from pagination metadata
4. **Fixed Pagination**: Prevents massive result sets from consuming context
5. **Metadata Caching**: Schema information cached in-memory, reducing repeated queries

## Technical Stack

- **Framework**: Quarkus 3.27.0 (LTS)
- **Language**: Java 21
- **MCP Extension**: Quarkus MCP Server Stdio (io.quarkiverse.mcp/quarkus-mcp-server-stdio)
- **Database**: JDBC-compatible databases
- **Build**: Maven
- **Native Compilation**: GraalVM (stretch goal)

## Success Criteria

1. AI agents can discover database schema via introspection
2. AI agents can execute SQL queries with results under 100 rows per page
3. Pagination allows access to large result sets without token overflow
4. Connection pooling reduces query latency vs. CLI-based approaches
5. Works with major databases (PostgreSQL, MySQL, SQLite, H2, Oracle, SQL Server)

## Future Considerations

- Schema change notifications via MCP resources
- Query result caching
- Read-only mode toggle
- Multiple database connection support
- Index introspection for optimization scenarios
- Native binary packaging with GraalVM
- Streaming support for very large text fields
