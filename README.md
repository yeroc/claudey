# MCP Database Server

[![Test](https://github.com/yeroc/claudey/actions/workflows/test.yml/badge.svg)](https://github.com/yeroc/claudey/actions/workflows/test.yml)
[![Native Build](https://github.com/yeroc/claudey/actions/workflows/native-build.yml/badge.svg)](https://github.com/yeroc/claudey/actions/workflows/native-build.yml)

A Model Context Protocol (MCP) server for database introspection and SQL execution, built with Quarkus.

## Features

- 🔍 **Database Introspection** - Browse schemas, tables, and column metadata
- 🚀 **SQL Execution** - Execute queries with automatic pagination
- 📊 **Multiple Databases** - PostgreSQL, SQLite support
- ⚡ **Native Binary** - Fast startup times with GraalVM native compilation
- 🎯 **CLI Mode** - Command-line interface for testing and one-shot queries
- 🔌 **MCP Compatible** - Works with any MCP-compatible client

## Quick Start

### Prerequisites

- Java 21+ (for JVM mode)
- GraalVM 21+ (for native compilation)
- PostgreSQL or SQLite database

### Installation

#### JVM Mode

```bash
# Build the project
mvn clean package

# Run the server
java -jar target/quarkus-app/quarkus-run.jar
```

#### Native Mode

```bash
# Build native binary
mvn package -Dnative

# Run native binary
./target/test-app-1.0-SNAPSHOT-runner
```

## Configuration

Configure database connection via environment variables:

```bash
export DB_URL="jdbc:postgresql://localhost:5432/mydb"
export DB_USERNAME="myuser"
export DB_PASSWORD="mypassword"
export DB_PAGE_SIZE="100"  # Optional, default: 100
```

### Supported Databases

- **PostgreSQL** - `jdbc:postgresql://host:port/database`
- **SQLite** - `jdbc:sqlite:/path/to/database.db` or `jdbc:sqlite::memory:`

## Usage

### MCP Mode (stdio)

Run the server in MCP mode to interact with MCP clients:

```bash
java -jar target/quarkus-app/quarkus-run.jar
```

### CLI Mode

Execute commands directly from the command line:

```bash
# Show usage
./target/test-app-1.0-SNAPSHOT-runner --cli

# Introspect all schemas
./target/test-app-1.0-SNAPSHOT-runner --cli introspect

# Introspect specific schema
./target/test-app-1.0-SNAPSHOT-runner --cli introspect public

# Introspect specific table
./target/test-app-1.0-SNAPSHOT-runner --cli introspect public users

# Execute SQL query
./target/test-app-1.0-SNAPSHOT-runner --cli query "SELECT * FROM users"

# Execute SQL query with pagination
./target/test-app-1.0-SNAPSHOT-runner --cli query "SELECT * FROM users" --page 2
```

## MCP Tools

### introspect

Browse database schemas and table structures.

**Parameters:**
- `schema` (optional) - Schema name to introspect
- `table` (optional) - Table name to describe (requires schema)

**Examples:**
```json
// List all schemas and tables
{ "name": "introspect" }

// List tables in schema
{ "name": "introspect", "arguments": { "schema": "public" } }

// Describe table structure
{ "name": "introspect", "arguments": { "schema": "public", "table": "users" } }
```

### execute_sql

Execute SQL queries with automatic pagination.

**Parameters:**
- `query` (required) - SQL query to execute
- `page` (optional) - Page number (default: 1)

**Examples:**
```json
// Execute SELECT query
{ "name": "execute_sql", "arguments": { "query": "SELECT * FROM users" } }

// Execute with pagination
{ "name": "execute_sql", "arguments": { "query": "SELECT * FROM users", "page": 2 } }

// Execute INSERT
{ "name": "execute_sql", "arguments": { "query": "INSERT INTO users (name) VALUES ('Alice')" } }
```

## Development

See [DEVELOPMENT.md](docs/DEVELOPMENT.md) for development setup, build instructions, and CI/CD documentation.

### Building

```bash
# Compile
mvn clean compile

# Run tests
mvn test

# Package (JVM)
mvn clean package

# Package (Native)
mvn package -Dnative
```

### Testing

```bash
# Run all tests
mvn test

# Run with PostgreSQL
export DB_URL="jdbc:postgresql://localhost:5432/testdb"
export DB_USERNAME="testuser"
export DB_PASSWORD="testpass"
mvn test

# Run with SQLite
export DB_URL="jdbc:sqlite::memory:"
mvn test
```

## Project Status

**Current Phase**: Phase 2 - CI/CD & Release Pipeline

See [Implementation Phases](docs/implementation-phases.md) for detailed roadmap.

### ✅ Completed
- Phase 1: Foundation & Project Setup (JVM mode)
- Phase 2: CI/CD & Release Pipeline

### 🚧 In Progress
- Phase 1: Native compilation testing

### 📋 Planned
- Phase 3: Database Introspection Tool
- Phase 4: SQL Execution Tool
- Phase 5: Error Handling & Robustness
- Phase 6: Integration Testing & Documentation
- Phase 7: Performance Optimization (Optional)

## Architecture

- **Framework**: Quarkus 3.27.0 LTS
- **Java Version**: 21
- **MCP Extension**: Quarkiverse MCP Server stdio 1.7.0
- **Connection Pooling**: Agroal
- **Configuration**: MicroProfile Config
- **Testing**: JUnit 5

## Contributing

Contributions are welcome! Please see [DEVELOPMENT.md](docs/DEVELOPMENT.md) for guidelines.

## License

[Add license information here]

## Links

- [Model Context Protocol Specification](https://modelcontextprotocol.io/)
- [Quarkus Documentation](https://quarkus.io/guides/)
- [Quarkiverse MCP Extension](https://github.com/quarkiverse/quarkus-mcp-server)
