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

If Maven fails to download dependencies with errors like "Unknown host repo.maven.apache.org", you need to configure the Maven proxy settings.

**Symptoms:**
- `mvn clean compile` fails with "Could not transfer artifact"
- Error mentions "Unknown host repo.maven.apache.org"

**Solution:**

Create or update `~/.m2/settings.xml` with proxy credentials from environment variables:

```bash
mkdir -p ~/.m2
cat > ~/.m2/settings.xml << 'EOF'
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
      <host>21.0.0.17</host>
      <port>15004</port>
      <username>PROXY_USERNAME_FROM_ENV</username>
      <password>PROXY_PASSWORD_FROM_ENV</password>
      <nonProxyHosts>localhost|127.0.0.1|169.254.169.254|metadata.google.internal|*.svc.cluster.local|*.local|*.googleapis.com|*.google.com</nonProxyHosts>
    </proxy>
  </proxies>
</settings>
EOF
```

**To extract credentials from environment:**

```bash
# Extract username and password from HTTPS_PROXY environment variable
# Format: http://username:password@host:port
env | grep -i https_proxy
```

The username and password are embedded in the `HTTPS_PROXY` environment variable.

**Note:** The `.mvn/maven.config` file in this repository already configures Maven to use the Wagon transport with preemptive authentication, which is required for the Claude Code proxy.