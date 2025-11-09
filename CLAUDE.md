# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is an MCP (Model Context Protocol) Database Server built with Quarkus framework. The project uses Java 21 and follows standard Maven directory structure. The main application implements QuarkusApplication interface for command-line execution.

- **Group ID**: org.geekden
- **Artifact ID**: test-app
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

## Dependencies

The project includes:
- Quarkiverse MCP Server stdio extension
- JDBC drivers: PostgreSQL, SQLite (Quarkiverse)
- Agroal connection pooling
- JUnit 5 for testing
- Quarkus JUnit5 for integration testing

## Maven Proxy Configuration (Claude Code on the Web)

If Maven fails to download dependencies with proxy errors, you need to configure BOTH the Maven settings.xml AND MAVEN_OPTS environment variables.

**Symptoms:**
- `mvn clean compile` fails with "Could not transfer artifact"
- Error shows "Unknown host repo.maven.apache.org" or proxy authentication issues
- Certificate errors (PKIX path building failed)

**Solution (BOTH steps required):**

**Step 1:** Create `~/.m2/settings.xml` with proxy credentials:

```bash
# Create settings.xml with proxy credentials from HTTPS_PROXY
# Format: http://username:password@host:port
mkdir -p ~/.m2

PROXY_USER=$(echo "$HTTPS_PROXY" | sed 's|http://\([^:]*\):.*|\1|')
PROXY_PASS=$(echo "$HTTPS_PROXY" | sed 's|http://[^:]*:\([^@]*\)@.*|\1|')
PROXY_HOST=$(echo "$HTTPS_PROXY" | sed 's|.*@\([^:]*\):.*|\1|')
PROXY_PORT=$(echo "$HTTPS_PROXY" | sed 's|.*:\([0-9]*\)$|\1|')

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
```

**Step 2:** Set MAVEN_OPTS environment variable (extract from HTTPS_PROXY):

```bash
# Parse proxy details from HTTPS_PROXY
PROXY_HOST=$(echo "$HTTPS_PROXY" | sed 's|.*@\([^:]*\):.*|\1|')
PROXY_PORT=$(echo "$HTTPS_PROXY" | sed 's|.*:\([0-9]*\)$|\1|')

# Set MAVEN_OPTS with proxy configuration
export MAVEN_OPTS="-Djdk.http.auth.tunneling.disabledSchemes= -Dmaven.resolver.transport=wagon -Dhttp.proxyHost=${PROXY_HOST} -Dhttp.proxyPort=${PROXY_PORT} -Dhttps.proxyHost=${PROXY_HOST} -Dhttps.proxyPort=${PROXY_PORT}"

# Now run Maven commands
mvn clean compile
```

**Why both are required:**

Maven forks child processes during the build. The `.mvn/maven.config` file configures the main Maven process to use Wagon transport, but forked processes inherit environment variables (MAVEN_OPTS) while not automatically inheriting all settings.xml configuration. Both configurations are needed to ensure all processes can reach the proxy.

**Troubleshooting Proxy Errors:**

The Claude Code proxy can be unreliable. Common issues:

1. **503 Service Unavailable**: The Claude Code proxy is overloaded or experiencing issues. Wait and retry.

2. **Certificate errors (PKIX)**: Java cannot validate SSL certificates through the proxy. This happens with newer GraalVM versions (25+). Use system OpenJDK 21 instead, or set MAVEN_OPTS as shown above.

3. **Unknown host errors**: Proxy credentials may have expired. The HTTPS_PROXY JWT tokens expire after ~4 hours. Re-run both setup steps above to get fresh credentials.

4. **Intermittent failures**: The proxy may work for some downloads but not others. This is environmental - retry the build.

**Note:** The `.mvn/maven.config` file in this repository configures Maven to use the Wagon transport with preemptive authentication, which is the foundation for proxy compatibility, but MAVEN_OPTS must also be set for forked processes.