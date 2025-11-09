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

If Maven fails to download dependencies with proxy errors, you need to configure the Maven proxy settings.

**Symptoms:**
- `mvn clean compile` fails with "Could not transfer artifact"
- Error shows "Unknown host repo.maven.apache.org" or proxy authentication issues

**Solution:**

settings.xml with proxy credentials is SUFFICIENT. No MAVEN_OPTS needed.

Create `~/.m2/settings.xml` by parsing the HTTPS_PROXY environment variable:

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

**Troubleshooting 503 Errors:**

If you see 503 Service Unavailable errors with `userName='null'` in the proxy info, the settings.xml file is missing or the proxy credentials have expired. Re-create settings.xml using the script above to get fresh credentials from the HTTPS_PROXY environment variable.

**Note:** The `.mvn/maven.config` file in this repository already configures Maven to use the Wagon transport with preemptive authentication, which is required for the Claude Code proxy.