# Development Guide

This document provides comprehensive guidance for developers working on the MCP Database Server project.

## Table of Contents

- [Development Setup](#development-setup)
- [Build Instructions](#build-instructions)
- [Testing Guidelines](#testing-guidelines)
- [CI/CD Pipeline](#cicd-pipeline)
- [Release Process](#release-process)
- [Architecture Overview](#architecture-overview)
- [Code Structure](#code-structure)
- [Adding Database Support](#adding-database-support)
- [Native Compilation](#native-compilation)
- [Troubleshooting](#troubleshooting)

## Development Setup

### Prerequisites

- **Java 21+** - Required for JVM mode development
- **Maven 3.9+** - Build tool
- **GraalVM 21+** - Required for native compilation (optional)
- **Docker** - Required for running PostgreSQL in tests (optional)
- **PostgreSQL 12+** - For testing (or use Docker)
- **Git** - Version control

### Environment Setup

1. Clone the repository:
```bash
git clone https://github.com/yeroc/claudey.git
cd claudey
```

2. Configure Maven (if using Claude Code web environment):
```bash
# Option 1: Use provided scripts
./bin/setup-maven-proxy.sh
source bin/maven-env.sh

# Option 2: Manual setup (see CLAUDE.md for details)
```

3. Verify setup:
```bash
java -version    # Should show Java 21+
mvn --version    # Should show Maven 3.9+
```

### IDE Setup

#### IntelliJ IDEA
1. Import as Maven project
2. Set Project SDK to Java 21
3. Enable annotation processing
4. Install Quarkus plugin (optional but recommended)

#### VS Code
1. Install extensions:
   - Extension Pack for Java
   - Quarkus Tools
2. Open project folder
3. Java 21 will be detected automatically

#### Eclipse
1. Import as existing Maven project
2. Set Java compiler to Java 21
3. Install Quarkus tools (optional)

## Build Instructions

### Standard Build

```bash
# Clean and compile
mvn clean compile

# Run tests
mvn test

# Package (JVM mode)
mvn clean package

# Skip tests
mvn clean package -DskipTests
```

### Development Mode

Quarkus supports live reload in development mode:

```bash
mvn quarkus:dev
```

Changes to Java files will be automatically recompiled.

### Native Compilation

Build native binary with GraalVM:

```bash
# Ensure GraalVM is active
java -version  # Should show GraalVM

# Build native binary
mvn package -Dnative

# Optional: Skip tests for faster builds
mvn package -Dnative -DskipTests
```

See [Native Compilation](#native-compilation-1) section for detailed information.

## Testing Guidelines

### Test Structure

- **Unit Tests** - Test individual classes in isolation
- **Integration Tests** - Test components working together with real database
- **End-to-End Tests** - Test complete workflows (planned for Phase 6)

### Running Tests

```bash
# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=DatabaseConfigTest

# Run with PostgreSQL
export DB_URL="jdbc:postgresql://localhost:5432/testdb"
export DB_USERNAME="testuser"
export DB_PASSWORD="testpass"
mvn test

# Run with SQLite (in-memory)
export DB_URL="jdbc:sqlite::memory:"
mvn test

# Run tests with coverage
mvn test jacoco:report
# Coverage report: target/site/jacoco/index.html
```

### Test Database Setup

#### PostgreSQL (Docker)

```bash
# Start PostgreSQL container
docker run -d --name postgres-test \
  -e POSTGRES_USER=testuser \
  -e POSTGRES_PASSWORD=testpass \
  -e POSTGRES_DB=testdb \
  -p 5432:5432 \
  postgres:16

# Set environment variables
export DB_URL="jdbc:postgresql://localhost:5432/testdb"
export DB_USERNAME="testuser"
export DB_PASSWORD="testpass"

# Stop and remove container
docker stop postgres-test
docker rm postgres-test
```

#### SQLite

SQLite requires no setup - use in-memory database:

```bash
export DB_URL="jdbc:sqlite::memory:"
mvn test
```

### Writing Tests

Follow these guidelines:

1. **Use JUnit 5** - All tests should use JUnit 5 (`@Test` from `org.junit.jupiter.api`)
2. **Use Quarkus Test** - Integration tests should extend `@QuarkusTest`
3. **Clean Up** - Tests should clean up resources (connections, temp files)
4. **Isolation** - Tests should not depend on execution order
5. **Descriptive Names** - Use descriptive test method names (e.g., `shouldReturnAllSchemasWhenNoParametersProvided`)

Example test:

```java
@QuarkusTest
class DatabaseMcpToolsTest {

  @Inject
  DatabaseMcpTools tools;

  @Test
  void shouldReturnUsageWhenNoParametersProvided() {
    String result = tools.introspect(null, null);
    assertTrue(result.contains("Usage"));
  }
}
```

## CI/CD Pipeline

The project uses GitHub Actions for continuous integration and deployment.

### Workflows

#### 1. Test Workflow (`.github/workflows/test.yml`)

**Trigger**: Push to any branch, Pull requests

**Purpose**: Run automated tests with multiple databases

**Steps**:
1. Checkout code
2. Set up Java 21
3. Start PostgreSQL service container
4. Run tests with PostgreSQL
5. Run tests with SQLite
6. Generate test coverage report
7. Upload test results as artifacts

**Configuration**:
- **PostgreSQL Version**: 16
- **Java Distribution**: Temurin (Eclipse OpenJDK)
- **Maven Cache**: Enabled
- **Test Results Retention**: 7 days
- **Coverage Reports**: Uploaded as artifacts

**Environment Variables**:
```yaml
DB_URL: jdbc:postgresql://localhost:5432/testdb
DB_USERNAME: testuser
DB_PASSWORD: testpass
```

#### 2. Native Build Workflow (`.github/workflows/native-build.yml`)

**Trigger**: Push to main/master, Pull requests to main/master

**Purpose**: Build and test native binary

**Steps**:
1. Checkout code
2. Set up GraalVM 21
3. Build native binary with `mvn package -Dnative`
4. Verify binary exists and is executable
5. Test startup time and CLI interface
6. Calculate binary size
7. Upload native binary as artifact
8. Comment PR with build statistics (on PRs)

**Configuration**:
- **GraalVM Version**: 21 (Community Edition)
- **Maven Cache**: Enabled
- **Artifact Retention**: 30 days
- **Memory**: 4GB heap for native compilation

**Metrics Reported**:
- Binary size (MB)
- Startup time (ms)
- Platform (Linux x64)

#### 3. Release Workflow (`.github/workflows/release.yml`)

**Trigger**: Push of version tags (e.g., `v1.0.0`, `v0.2.1`)

**Purpose**: Build release artifacts and create GitHub release

**Jobs**:

1. **Build JVM JAR**
   - Build with `mvn package`
   - Rename artifact with version
   - Generate SHA256 checksum
   - Upload as artifact

2. **Build Native Binary (Linux x64)**
   - Build with GraalVM on Ubuntu
   - Generate native binary for Linux
   - Generate SHA256 checksum
   - Upload as artifact

3. **Build Native Binary (macOS)**
   - Build with GraalVM on macOS
   - Generate native binary for macOS
   - Generate SHA256 checksum
   - Upload as artifact

4. **Create GitHub Release**
   - Download all artifacts
   - Generate release notes
   - Create GitHub release with:
     - JVM JAR
     - Native binaries (Linux, macOS)
     - SHA256 checksums
     - Installation instructions
     - Usage examples

**Release Artifacts**:
- `mcp-database-server-{version}.jar` - JVM JAR (cross-platform)
- `mcp-database-server-{version}-linux-x64` - Native binary for Linux
- `mcp-database-server-{version}-macos-x64` - Native binary for macOS
- Corresponding `.sha256` checksum files

### Local CI Testing

You can test CI workflows locally before pushing:

```bash
# Run tests as CI would
mvn clean test

# Build native as CI would (requires GraalVM)
mvn package -Dnative -DskipTests

# Verify checksums
sha256sum target/*-runner > checksum.sha256
sha256sum -c checksum.sha256
```

### Build Badges

The README includes build status badges:

```markdown
[![Test](https://github.com/yeroc/claudey/actions/workflows/test.yml/badge.svg)](https://github.com/yeroc/claudey/actions/workflows/test.yml)
[![Native Build](https://github.com/yeroc/claudey/actions/workflows/native-build.yml/badge.svg)](https://github.com/yeroc/claudey/actions/workflows/native-build.yml)
```

These show the current status of the latest workflow runs.

## Release Process

### Creating a Release

1. **Update Version** in `pom.xml`:
```xml
<version>1.0.0</version>
```

2. **Update Documentation**:
   - Update CHANGELOG (if exists)
   - Update README with new features
   - Update implementation-phases.md status

3. **Commit Changes**:
```bash
git add pom.xml README.md docs/
git commit -m "Prepare release v1.0.0"
git push
```

4. **Create and Push Tag**:
```bash
git tag -a v1.0.0 -m "Release v1.0.0"
git push origin v1.0.0
```

5. **Monitor Release Workflow**:
   - Go to GitHub Actions
   - Watch the "Release" workflow
   - Verify all jobs complete successfully

6. **Verify Release**:
   - Check GitHub Releases page
   - Download and test artifacts
   - Verify checksums

### Semantic Versioning

This project follows [Semantic Versioning](https://semver.org/):

- **MAJOR** version (1.x.x) - Incompatible API changes
- **MINOR** version (x.1.x) - New functionality (backward compatible)
- **PATCH** version (x.x.1) - Bug fixes (backward compatible)

### Hotfix Process

For critical bugs in production:

1. Create hotfix branch from tag:
```bash
git checkout -b hotfix/v1.0.1 v1.0.0
```

2. Fix bug and test
3. Update version to 1.0.1
4. Commit and push
5. Create tag v1.0.1
6. Merge back to main

## Architecture Overview

### Framework: Quarkus

Quarkus is a Kubernetes-native Java framework optimized for GraalVM and HotSpot:

- **Supersonic Startup** - Sub-second startup times
- **Low Memory Footprint** - Especially in native mode
- **Live Reload** - Developer-friendly hot reload
- **Optimized for Containers** - Cloud-native by design

### MCP Extension

The project uses Quarkiverse MCP Server stdio extension (v1.7.0):

- **@Tool Annotations** - Simple tool registration
- **Automatic JSON Serialization** - Handles MCP protocol
- **stdio Transport** - Communication via standard input/output

### Database Layer

**Connection Pooling**: Agroal (Quarkus default)
- Managed via MicroProfile Config
- Configurable pool size, timeouts, validation
- Health checks integration

**JDBC Drivers**: Auto-loaded via JDBC 4.0+ service-loader
- PostgreSQL: `org.postgresql.Driver`
- SQLite: Quarkus JDBC SQLite extension

### Configuration

**MicroProfile Config** - Priority order:
1. Environment variables (highest priority)
2. System properties
3. `application.properties`
4. Default values in `@ConfigProperty`

### Dependency Injection

**CDI (Contexts and Dependency Injection)**:
- All services are CDI beans
- Constructor injection preferred
- Request-scoped by default

## Code Structure

```
src/main/java/org/geekden/
├── MainApplication.java           # Entry point (implements QuarkusApplication)
├── mcp/
│   ├── DatabaseMcpTools.java      # MCP tools with @Tool annotations
│   ├── cli/
│   │   └── CliCommandHandler.java # CLI command parsing
│   ├── config/
│   │   └── DatabaseConfig.java    # Configuration (MicroProfile Config)
│   └── service/
│       ├── IntrospectionService.java  # [Phase 3] Schema metadata
│       ├── SqlExecutionService.java   # [Phase 4] SQL execution
│       └── ...

src/main/resources/
├── application.properties         # Quarkus configuration
└── ...

src/test/java/org/geekden/
├── mcp/
│   ├── DatabaseMcpToolsTest.java
│   ├── config/
│   │   └── DatabaseConfigTest.java
│   └── ...
```

### Key Classes

**MainApplication** (`src/main/java/org/geekden/MainApplication.java`)
- Entry point implementing `QuarkusApplication`
- Parses command-line arguments
- Routes to CLI mode or MCP mode

**DatabaseMcpTools** (`src/main/java/org/geekden/mcp/DatabaseMcpTools.java`)
- Implements MCP tools with `@Tool` annotations
- Currently has stub implementations for introspect and execute_sql
- Will be enhanced in Phase 3 & 4

**DatabaseConfig** (`src/main/java/org/geekden/mcp/config/DatabaseConfig.java`)
- Type-safe configuration using MicroProfile Config
- Maps environment variables to config properties

**CliCommandHandler** (`src/main/java/org/geekden/mcp/cli/CliCommandHandler.java`)
- Parses CLI commands and arguments
- Delegates to appropriate tools

## Adding Database Support

To add support for a new database (e.g., MySQL, SQL Server):

### 1. Add JDBC Driver Dependency

Edit `pom.xml`:

```xml
<dependency>
  <groupId>com.mysql</groupId>
  <artifactId>mysql-connector-j</artifactId>
  <version>8.2.0</version>
</dependency>
```

### 2. Test Connection

Create integration test:

```java
@QuarkusTest
class MySqlConnectionTest {

  @ConfigProperty(name = "quarkus.datasource.jdbc.url")
  String jdbcUrl;

  @Test
  void shouldConnectToMySQL() {
    assumeTrue(jdbcUrl.contains("mysql"), "Skipping MySQL test");
    // Test connection
  }
}
```

### 3. Handle Database-Specific Quirks

Update `SqlExecutionService` (Phase 4) for database-specific SQL:

```java
private String getDatabaseType(Connection conn) throws SQLException {
  String url = conn.getMetaData().getURL();
  if (url.contains("mysql")) return "mysql";
  if (url.contains("postgresql")) return "postgresql";
  if (url.contains("sqlite")) return "sqlite";
  return "unknown";
}

private String addPagination(String query, int page, int pageSize, String dbType) {
  switch (dbType) {
    case "mysql":
    case "postgresql":
    case "sqlite":
      return query + " LIMIT " + (pageSize + 1) + " OFFSET " + ((page - 1) * pageSize);
    case "sqlserver":
      return query + " OFFSET " + ((page - 1) * pageSize) + " ROWS FETCH NEXT " + (pageSize + 1) + " ROWS ONLY";
    default:
      return query; // No pagination
  }
}
```

### 4. Update Documentation

- Add to README.md supported databases list
- Document any database-specific configuration
- Add examples for the new database

## Native Compilation

### Prerequisites

1. **Install GraalVM**:
```bash
# Using SDKMAN
sdk install java 21.0.2-graalce
sdk use java 21.0.2-graalce

# Verify
java -version  # Should show GraalVM
```

2. **Verify Native Image Tool**:
```bash
# Should already be included with GraalVM
native-image --version
```

### Building Native Binary

```bash
# Build native binary
mvn package -Dnative

# Optional: Skip tests for faster builds
mvn package -Dnative -DskipTests

# Binary location
ls -lh target/*-runner
```

### Native Build Configuration

Native compilation is configured in `pom.xml`:

```xml
<profile>
  <id>native</id>
  <properties>
    <quarkus.package.type>native</quarkus.package.type>
  </properties>
</profile>
```

### Native Image Hints

If you encounter issues with reflection, resources, or JNI in native mode, you may need to add hints:

Create `src/main/resources/META-INF/native-image/reflect-config.json`:

```json
[
  {
    "name": "org.postgresql.Driver",
    "allDeclaredConstructors": true,
    "allPublicConstructors": true,
    "allDeclaredMethods": true,
    "allPublicMethods": true
  }
]
```

**Note**: Quarkus usually handles JDBC driver registration automatically. Only add hints if you encounter specific issues.

### Testing Native Binary

```bash
# Run native binary
./target/test-app-1.0-SNAPSHOT-runner --cli

# Test startup time
time ./target/test-app-1.0-SNAPSHOT-runner --help

# Test with database
export DB_URL="jdbc:postgresql://localhost:5432/testdb"
export DB_USERNAME="testuser"
export DB_PASSWORD="testpass"
./target/test-app-1.0-SNAPSHOT-runner --cli introspect
```

### Native Build Troubleshooting

**Issue**: OutOfMemoryError during build
```bash
# Increase heap size
export MAVEN_OPTS="-Xmx4g"
mvn package -Dnative
```

**Issue**: Driver not found in native mode
- Check if driver is in dependencies
- Add reflection hints if needed
- Verify Quarkus extension for JDBC driver

**Issue**: Slow native compilation
- Normal first time (5-10 minutes)
- Use `-DskipTests` to skip tests
- Use faster machine or CI environment

## Troubleshooting

### Maven Proxy Issues (Claude Code Web)

**Symptom**: 403 errors, connection timeouts

**Solution**: Ensure BOTH `~/.m2/settings.xml` AND `MAVEN_OPTS` are configured:

```bash
./bin/setup-maven-proxy.sh
source bin/maven-env.sh
mvn clean compile
```

See CLAUDE.md for detailed proxy setup.

### Database Connection Issues

**Symptom**: Tests fail with connection errors

**Solutions**:

1. **PostgreSQL not running**:
```bash
docker ps  # Check if container is running
docker start postgres-test
```

2. **Wrong credentials**:
```bash
# Verify environment variables
echo $DB_URL
echo $DB_USERNAME
echo $DB_PASSWORD
```

3. **Port conflict**:
```bash
# Check if port 5432 is in use
lsof -i :5432
# Use different port in Docker: -p 5433:5432
```

### Native Build Issues

**Symptom**: Native build fails

**Solutions**:

1. **GraalVM not active**:
```bash
java -version  # Should show GraalVM
# If not: sdk use java 21.0.2-graalce
```

2. **Out of memory**:
```bash
export MAVEN_OPTS="-Xmx4g"
mvn package -Dnative
```

3. **Missing native-image tool**:
```bash
# Usually included with GraalVM
# If missing, install separately
gu install native-image
```

### Test Failures

**Symptom**: Tests pass locally but fail in CI

**Common Causes**:

1. **Environment variables not set** - Check CI workflow environment
2. **Database not available** - Verify service containers in workflow
3. **Timezone differences** - Use UTC in tests
4. **File path separators** - Use `File.separator` or `Paths.get()`

### Quarkus Dev Mode Issues

**Symptom**: Changes not reflected in dev mode

**Solutions**:

1. **Restart dev mode**: Ctrl+C and re-run `mvn quarkus:dev`
2. **Clear target directory**: `mvn clean quarkus:dev`
3. **Check logs** for compilation errors

## Getting Help

- **GitHub Issues**: https://github.com/yeroc/claudey/issues
- **Quarkus Documentation**: https://quarkus.io/guides/
- **MCP Specification**: https://modelcontextprotocol.io/
- **Quarkiverse MCP**: https://github.com/quarkiverse/quarkus-mcp-server

## Contributing

1. Fork the repository
2. Create feature branch: `git checkout -b feature/my-feature`
3. Make changes and add tests
4. Ensure tests pass: `mvn test`
5. Commit: `git commit -m "Add my feature"`
6. Push: `git push origin feature/my-feature`
7. Create Pull Request

All PRs must:
- Pass all tests (JVM mode)
- Pass native build workflow
- Include tests for new functionality
- Follow existing code style (2-space indents)
- Update documentation as needed
