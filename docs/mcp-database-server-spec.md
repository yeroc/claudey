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
- Results limited to 100 rows per page
- Uses LIMIT/OFFSET for pagination
- Supports all SQL operations (SELECT, INSERT, UPDATE, DELETE, DDL)

**Response Format:**
- Simple ASCII table format
- Column headers in first row
- NULL values displayed as `<null>`
- Long text fields returned as-is
- Pagination metadata:
  - Current page number
  - Has more pages (boolean)
  - Row count for current page

**Example Response:**
```
Page 1 of results (more pages available: true)

id  | name          | email
----|---------------|------------------
1   | John Doe      | john@example.com
2   | Jane Smith    | <null>
...
(100 rows)
```

### Resources

Not using MCP resources in initial version. May revisit for schema discovery UX improvements.

## Error Handling

All errors returned as user-friendly messages in tool responses:

- **SQL Syntax Errors**: "SQL syntax error: [descriptive message]"
- **Permission Denied**: "Access denied: [operation] on [object]"
- **Connection Failures**: "Database connection error: [details]"
- **Constraint Violations**: "Constraint violation: [constraint name] - [details]"

No raw stack traces exposed to clients.

## Configuration

### Required Environment Variables
- `DB_URL`: JDBC connection URL (e.g., `jdbc:postgresql://localhost:5432/mydb`)
- `DB_USERNAME`: Database username
- `DB_PASSWORD`: Database password

### Optional Environment Variables
- `DB_DRIVER`: JDBC driver class (auto-detected if not specified)
- `DB_POOL_SIZE`: Connection pool size (default: 1 for single connection)

## Token Efficiency Strategy

1. **Minimal Tool Count**: Only 2 tools vs. many specialized operations
2. **Hierarchical Parameters**: Single `introspect` tool handles multiple granularities
3. **Text Table Format**: More compact than JSON arrays of objects
4. **Fixed Pagination**: Prevents massive result sets from consuming context
5. **Metadata Caching**: Schema information cached in-memory, reducing repeated queries

## Technical Stack

- **Framework**: Quarkus 3.26.1
- **Language**: Java 21
- **MCP SDK**: Official Java SDK (modelcontextprotocol/java-sdk)
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
