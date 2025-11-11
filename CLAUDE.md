# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is an MCP (Model Context Protocol) Database Server built with Quarkus framework. The project uses Java 21 and follows standard Maven directory structure. The main application implements QuarkusApplication interface for command-line execution.

- **Group ID**: org.geekden
- **Artifact ID**: mcp-database-server
- **Java Version**: 21
- **Framework**: Quarkus 3.27.0 LTS
- **MCP Extension**: Quarkiverse MCP Server stdio 1.7.0
- **Databases**: PostgreSQL, SQLite (via JDBC)
- **Main Class**: org.geekden.MainApplication

## Development Commands

### Build and Test
```bash
mvn clean compile          # Compile the project
mvn clean package          # Package the application
mvn package -Dnative       # Build native image
mvn test                   # Run tests
mvn quarkus:dev            # Run in development mode with hot reload
```

### Running the Application
```bash
mvn quarkus:dev            # Development mode with live reload
java -jar target/quarkus-app/quarkus-run.jar  # Run packaged application
```

## Code Style

- Always use 2 space indents, no tabs
- Follow standard Java naming conventions
- Use JUnit 5 for testing

## Testing Standards

**IMPORTANT**: All test assertions must use Hamcrest matchers. JUnit assertions (`assertEquals`, `assertTrue`, `assertFalse`, `assertNull`, etc.) are **BANNED**.

### Required Assertion Style

**Use Hamcrest `assertThat()` exclusively:**

```java
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

// Good: Hamcrest assertions
assertThat("Should return exit code 1", exitCode, is(1));
assertThat("Should not be null", object, is(notNullValue()));
assertThat("Should contain error", output, containsString("Error"));
assertThat("Should match pattern", text, anyOf(containsString("foo"), containsString("bar")));

// Bad: JUnit assertions (BANNED)
assertEquals(1, exitCode);           // NO!
assertNotNull(object);               // NO!
assertTrue(output.contains("Error")); // NO!
```

### Stream Capture

Use **system-stubs** programmatically for stdout/stderr capture (the extension conflicts with `@QuarkusTest`):

```java
import static uk.org.webcompere.systemstubs.SystemStubs.tapSystemErr;

@QuarkusTest
class MyTest {

  @Test
  void testErrorOutput() throws Exception {
    String stderr = tapSystemErr(() -> {
      // Code that writes to System.err
      int exitCode = someMethod();
      assertThat("Should succeed", exitCode, is(0));
    });

    assertThat("Should print error message",
        stderr, containsString("Error"));
  }
}
```

### Benefits

- **Better error messages**: Hamcrest shows actual vs expected values with clear descriptions
- **Readable assertions**: Natural language-like syntax
- **Consistent style**: All tests follow the same pattern
- **No more `<true> but was: <false>`**: Hamcrest provides meaningful failure output

## Dependencies

The project includes:
- Quarkiverse MCP Server stdio extension
- JDBC drivers: PostgreSQL, SQLite (Quarkiverse)
- Agroal connection pooling
- JUnit 5 for testing
- Quarkus JUnit5 for integration testing
- Hamcrest 2.2 for test assertions
- system-stubs-jupiter 2.1.8 for stream capture in tests

## Maven Proxy Configuration (Claude Code on the Web)

**REQUIRED**: Maven builds in Claude Code web environment need BOTH `~/.m2/settings.xml` AND `MAVEN_OPTS`.

The Claude Code web proxy requires preemptive authentication (non-standard). Three components must be configured:

1. `.mvn/maven.config` - Forces Wagon transport (already in repository)
2. `~/.m2/settings.xml` - Provides proxy credentials
3. `MAVEN_OPTS` - Ensures forked processes inherit proxy configuration

**Quick Setup (using provided scripts in `bin/`):**

```bash
# 1. Create ~/.m2/settings.xml with proxy credentials
./bin/setup-maven-proxy.sh

# 2. Set MAVEN_OPTS environment variables (must be sourced)
source bin/maven-env.sh

# 3. Run Maven commands
mvn clean compile
```

**Manual Setup:**

```bash
# Parse proxy from environment
PROXY_HOST=$(echo "$HTTPS_PROXY" | sed 's|.*@\([^:]*\):.*|\1|')
PROXY_PORT=$(echo "$HTTPS_PROXY" | sed 's|.*:\([0-9]*\)$|\1|')
PROXY_USER=$(echo "$HTTPS_PROXY" | sed 's|http://\([^:]*\):.*|\1|')
PROXY_PASS=$(echo "$HTTPS_PROXY" | sed 's|http://[^:]*:\([^@]*\)@.*|\1|')

# Create settings.xml with proxy credentials
mkdir -p ~/.m2
cat > ~/.m2/settings.xml << EOF
<?xml version="1.0" encoding="UTF-8"?>
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0
                              http://maven.apache.org/xsd/settings-1.0.0.xsd">
  <proxies>
    <proxy>
      <id>claude-code-proxy</id>
      <active>true</active>
      <protocol>http</protocol>
      <host>${PROXY_HOST}</host>
      <port>${PROXY_PORT}</port>
      <username>${PROXY_USER}</username>
      <password>${PROXY_PASS}</password>
      <nonProxyHosts>localhost|127.0.0.1|169.254.169.254|metadata.google.internal|*.svc.cluster.local|*.local|*.googleapis.com|*.google.com</nonProxyHosts>
    </proxy>
  </proxies>
</settings>
EOF

# Set MAVEN_OPTS for forked processes
export MAVEN_OPTS="-Djdk.http.auth.tunneling.disabledSchemes= -Dmaven.resolver.transport=wagon -Dhttp.proxyHost=${PROXY_HOST} -Dhttp.proxyPort=${PROXY_PORT} -Dhttps.proxyHost=${PROXY_HOST} -Dhttps.proxyPort=${PROXY_PORT}"

# Verify Java and run build
java -version
mvn clean compile
```

**Status:**
- ✅ **JVM builds**: Work with system OpenJDK 21
- ❌ **Native builds**: Not yet working with GraalVM CE (under investigation)

**Note:** Proxy credentials (JWT tokens) expire after ~4 hours. Re-run the setup script with fresh HTTPS_PROXY when expired.