# Agent Isolation Architecture & Implementation Plan

**Date:** 2025-11-29  
**Status:** Planning Phase

## Overview

This document outlines how the Claudey Bridge agent isolation system fits into the existing multi-module Maven repository structure, with a focus on the **sandbox launcher** component architecture.

---

## Current Repository Structure

```
claudey/
├── pom.xml                    # Parent POM
├── database/                  # MCP Database Server module
│   ├── pom.xml
│   └── src/
├── journal/                   # MCP Journal module
│   ├── pom.xml
│   └── src/
├── bin/                       # Shell scripts
├── scripts/                   # Utility scripts
└── docs/                      # Documentation
```

**Key Characteristics:**
- Multi-module Maven project
- Quarkus-based MCP servers
- Java 21
- Native compilation support (GraalVM)
- Existing modules: `database`, `journal`

---

## Proposed Architecture: Three Components

The agent isolation system consists of **three distinct components** with different responsibilities:

### 1. **MCP Bridge Server** (Java/Quarkus Module)
- **Location:** `claudey/bridge/` (new Maven module)
- **Technology:** Java 21 + Quarkus + MCP Server
- **Responsibility:** Expose privileged operations as MCP tools
- **Runs:** On the host (outside sandbox) as a background service

### 2. **Sandbox Launcher** (External Dependency initially)
-   **Phase 1:** Uses **`cco`** (https://github.com/nikvdp/cco)
    -   Existing tool that handles `bubblewrap`/`sandbox-exec` isolation
    -   Configured to use host networking to reach the bridge
-   **Phase 2:** Native `cs` launcher
    -   **Location:** `claudey/sandbox/`
    -   **Technology:** Shell scripts + platform-specific tools
    -   **Responsibility:** Launch sandboxed processes without external dependencies

### 3. **Agent Wrapper** (Optional Helper Scripts)
- **Location:** `claudey/bin/` (existing directory)
- **Technology:** Shell scripts
- **Responsibility:** Convenience wrappers for launching agents
- **Runs:** User-facing commands

---

## Detailed Component Design

### Component 1: MCP Bridge Server (`bridge/`)

**Maven Module Structure:**
```
bridge/
├── pom.xml
└── src/
    ├── main/
    │   ├── java/
    │   │   └── org/geekden/mcp/bridge/
    │   │       ├── BridgeServer.java           # Main entry point
    │   │       ├── ToolDefinitionParser.java   # YAML/Markdown parser
    │   │       ├── InputValidator.java         # JSON Schema validation
    │   │       ├── CommandExecutor.java        # Execute whitelisted commands
    │   │       ├── McpToolHandler.java         # MCP protocol handler
    │   │       └── config/
    │   │           └── BridgeConfig.java       # Configuration model
    │   └── resources/
    │       ├── application.properties          # Quarkus config
    │       └── META-INF/
    │           └── native-image/               # GraalVM config
    └── test/
        └── java/
            └── org/geekden/mcp/bridge/
                ├── ToolDefinitionParserTest.java
                ├── InputValidatorTest.java
                └── CommandExecutorTest.java
```

**Key Features:**
- Quarkus application with MCP server support
- HTTP endpoint at `http://localhost:3000/mcp` (SSE transport)
- Reads tool definitions from `~/.config/claudey/tools/*.md`
- Exposes three MCP tools: `list_programs()`, `help()`, `execute()`
- Native compilation support for fast startup

**POM.xml Additions to Parent:**
```xml
<modules>
  <module>database</module>
  <module>journal</module>
  <module>bridge</module>  <!-- NEW -->
</modules>
```

**Dependencies:**
- `quarkus-mcp-server-stdio` (or HTTP variant)
- `quarkus-arc` (CDI)
- `quarkus-picocli` (CLI interface)
- YAML parser (SnakeYAML or Jackson YAML)
- JSON Schema validator

---

### Component 2: Sandbox Launcher (`sandbox/`)

**Critical Design Decision:** This is **NOT a Maven module** because:
1. Platform-specific (Linux vs macOS have different implementations)
2. Primarily shell scripts, not Java code
3. Needs to be lightweight and fast
4. May need to be installed system-wide (`/usr/local/bin`)

**Directory Structure:**
```
sandbox/
├── README.md                          # Documentation
├── linux/
│   ├── claudey-sandbox                # Main launcher script
│   ├── claudey-sandbox.conf           # Default configuration
│   └── install.sh                     # Installation script
├── macos/
│   ├── claudey-sandbox                # Main launcher script
│   ├── claudey-agent.sb               # Seatbelt profile template
│   ├── claudey-sandbox.conf           # Default configuration
│   └── install.sh                     # Installation script
└── tests/
    ├── test-linux.sh                  # Integration tests
    └── test-macos.sh                  # Integration tests
```

#### Linux Implementation (`sandbox/linux/claudey-sandbox`)

```bash
#!/bin/bash
# claudey-sandbox - Launch a sandboxed agent process
#
# Usage: claudey-sandbox [OPTIONS] -- COMMAND [ARGS...]
#
# Options:
#   --workspace DIR     Workspace directory to bind-mount (required)
#   --bridge-url URL    Bridge server URL (default: http://localhost:3000)
#   --config FILE       Configuration file
#   --verbose           Enable verbose logging

set -euo pipefail

# Default configuration
BRIDGE_URL="http://localhost:3000"
WORKSPACE=""
CONFIG_FILE="${XDG_CONFIG_HOME:-$HOME/.config}/claudey/sandbox.conf"
VERBOSE=false

# Parse arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --workspace)
            WORKSPACE="$2"
            shift 2
            ;;
        --bridge-url)
            BRIDGE_URL="$2"
            shift 2
            ;;
        --config)
            CONFIG_FILE="$2"
            shift 2
            ;;
        --verbose)
            VERBOSE=true
            shift
            ;;
        --)
            shift
            break
            ;;
        *)
            echo "Unknown option: $1" >&2
            exit 1
            ;;
    esac
done

# Validate required arguments
if [[ -z "$WORKSPACE" ]]; then
    echo "Error: --workspace is required" >&2
    exit 1
fi

if [[ ! -d "$WORKSPACE" ]]; then
    echo "Error: Workspace directory does not exist: $WORKSPACE" >&2
    exit 1
fi

# Check if bubblewrap is installed
if ! command -v bwrap &> /dev/null; then
    echo "Error: bubblewrap is not installed" >&2
    echo "Install with: sudo apt-get install bubblewrap" >&2
    exit 1
fi

# Load configuration file if it exists
if [[ -f "$CONFIG_FILE" ]]; then
    source "$CONFIG_FILE"
fi

# Resolve absolute paths
WORKSPACE=$(realpath "$WORKSPACE")

# Create temporary home directory
TEMP_HOME=$(mktemp -d)
trap "rm -rf $TEMP_HOME" EXIT

# Set up minimal home directory structure
mkdir -p "$TEMP_HOME/.cache"
mkdir -p "$TEMP_HOME/.local/share"

# Export bridge URL as environment variable
export CLAUDEY_BRIDGE_URL="$BRIDGE_URL"

[[ "$VERBOSE" == "true" ]] && echo "Launching sandbox with workspace: $WORKSPACE"

# Launch sandboxed process with bubblewrap
exec bwrap \
    --ro-bind / / \
    --dev-bind /dev /dev \
    --proc /proc \
    --tmpfs "$HOME" \
    --bind "$WORKSPACE" "$WORKSPACE" \
    --bind "$TEMP_HOME/.cache" "$HOME/.cache" \
    --bind "$TEMP_HOME/.local" "$HOME/.local" \
    --setenv HOME "$HOME" \
    --setenv CLAUDEY_BRIDGE_URL "$BRIDGE_URL" \
    --unshare-all \
    --share-net \
    --die-with-parent \
    "$@"
```

#### macOS Implementation (`sandbox/macos/claudey-sandbox`)

```bash
#!/bin/bash
# claudey-sandbox - Launch a sandboxed agent process (macOS)
#
# Usage: claudey-sandbox [OPTIONS] -- COMMAND [ARGS...]

set -euo pipefail

# Default configuration
BRIDGE_URL="http://localhost:3000"
WORKSPACE=""
SEATBELT_PROFILE="${XDG_CONFIG_HOME:-$HOME/.config}/claudey/claudey-agent.sb"
VERBOSE=false

# Parse arguments (same as Linux version)
# ... (omitted for brevity)

# Validate seatbelt profile exists
if [[ ! -f "$SEATBELT_PROFILE" ]]; then
    echo "Error: Seatbelt profile not found: $SEATBELT_PROFILE" >&2
    exit 1
fi

# Resolve absolute paths
WORKSPACE=$(realpath "$WORKSPACE")

# Export bridge URL
export CLAUDEY_BRIDGE_URL="$BRIDGE_URL"

[[ "$VERBOSE" == "true" ]] && echo "Launching sandbox with workspace: $WORKSPACE"

# Launch with sandbox-exec
exec sandbox-exec \
    -p "$SEATBELT_PROFILE" \
    -D HOME="$HOME" \
    -D WORKSPACE="$WORKSPACE" \
    "$@"
```

#### Seatbelt Profile Template (`sandbox/macos/claudey-agent.sb`)

```scheme
(version 1)

; Allow system libraries and frameworks
(allow file-read*
    (subpath "/System")
    (subpath "/usr/lib")
    (subpath "/Library/Frameworks"))

; Allow network access to localhost only
(allow network-outbound
    (remote ip "localhost:*"))

; BLOCK: Deny all access to home directory
(deny file-read* file-write*
    (subpath (param "HOME")))

; EXCEPTION: Allow workspace
(allow file-read* file-write*
    (subpath (param "WORKSPACE")))

; EXCEPTION: Allow cache directories
(allow file-read* file-write*
    (subpath (string-append (param "HOME") "/Library/Caches"))
    (subpath (string-append (param "HOME") "/.cache")))

; Block sensitive directories explicitly
(deny file-read*
    (subpath (string-append (param "HOME") "/.ssh"))
    (subpath (string-append (param "HOME") "/.aws"))
    (subpath (string-append (param "HOME") "/.config/claudey")))
```

**Why Not Java for Sandbox Launcher?**

| Aspect | Shell Script | Java |
|--------|-------------|------|
| **Startup Time** | Instant | ~50-100ms (even with native) |
| **Platform Integration** | Direct syscall access | JNI required |
| **Complexity** | Simple wrapper | Complex JNI bindings |
| **Maintenance** | Easy to debug | Harder to debug platform issues |
| **Installation** | Copy script to PATH | Requires JVM or native binary |

**Decision:** Use shell scripts for sandbox launcher, Java for bridge server.

---

### Component 3: Agent Wrapper (`bin/`)

**Purpose:** User-facing convenience commands

**Example: `bin/claude-code-sandbox`**
```bash
#!/bin/bash
# Wrapper to launch Claude Code in a sandbox

WORKSPACE="${1:-.}"

# Ensure bridge is running
if ! curl -s http://localhost:3000/mcp > /dev/null 2>&1; then
    echo "Error: Claudey Bridge is not running" >&2
    echo "Start it with: claudey-bridge serve" >&2
    exit 1
fi

# Launch Claude Code in sandbox
exec claudey-sandbox \
    --workspace "$WORKSPACE" \
    -- \
    claude-code "$@"
```

---

## Installation & Deployment

### Development Installation

```bash
# 1. Build all Maven modules
mvn clean install

# 2. Install sandbox launcher (Linux)
cd sandbox/linux
sudo ./install.sh

# 3. Install sandbox launcher (macOS)
cd sandbox/macos
./install.sh

# 4. Create tool definitions directory
mkdir -p ~/.config/claudey/tools

# 5. Add example tool definition
cat > ~/.config/claudey/tools/echo.md << 'EOF'
---
name: echo_message
description: Echo a message
command: /bin/echo
args:
  - name: message
    type: string
    pattern: "^[a-zA-Z0-9 ]+$"
---
# Echo Tool
Simple echo for testing.
EOF
```

### Production Installation

**Option 1: Homebrew (macOS/Linux)**
```bash
brew install claudey-bridge
brew install claudey-sandbox
```

**Option 2: Native Packages**
- `.deb` package for Debian/Ubuntu
- `.rpm` package for RHEL/Fedora
- `.pkg` installer for macOS

**Package Contents:**
- `/usr/local/bin/claudey-bridge` - Bridge server binary
- `/usr/local/bin/claudey-sandbox` - Sandbox launcher script
- `/usr/local/share/claudey/` - Default configurations
- `/etc/systemd/system/claudey-bridge.service` - Systemd service (Linux)
- `/Library/LaunchDaemons/org.geekden.claudey-bridge.plist` - LaunchDaemon (macOS)

---

## Build & Release Process

### Maven Build

```xml
<!-- Parent pom.xml -->
<modules>
  <module>database</module>
  <module>journal</module>
  <module>bridge</module>
</modules>
```

**Build Commands:**
```bash
# Build all modules (JVM mode)
mvn clean package

# Build native binaries
mvn clean package -Pnative

# Build specific module
mvn clean package -pl bridge
```

### Packaging

**GitHub Actions Workflow:**
```yaml
name: Release

on:
  push:
    tags:
      - 'v*'

jobs:
  build-bridge:
    strategy:
      matrix:
        os: [ubuntu-latest, macos-latest]
    runs-on: ${{ matrix.os }}
    steps:
      - uses: actions/checkout@v3
      - uses: graalvm/setup-graalvm@v1
      - name: Build native binary
        run: mvn package -Pnative -pl bridge
      - name: Upload artifact
        uses: actions/upload-artifact@v3
        with:
          name: claudey-bridge-${{ matrix.os }}
          path: bridge/target/claudey-bridge-*-runner
  
  package-sandbox:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - name: Package sandbox scripts
        run: |
          tar czf claudey-sandbox-linux.tar.gz sandbox/linux/
          tar czf claudey-sandbox-macos.tar.gz sandbox/macos/
      - uses: actions/upload-artifact@v3
        with:
          name: sandbox-scripts
          path: claudey-sandbox-*.tar.gz
```

---

## Configuration Management

### User Configuration Directory

```
~/.config/claudey/
├── bridge.yaml              # Bridge server configuration
├── sandbox.conf             # Sandbox launcher configuration
├── claudey-agent.sb         # Seatbelt profile (macOS only)
└── tools/                   # Tool definitions
    ├── deploy_prod.md
    ├── restart_db.md
    └── backup.md
```

### Bridge Configuration (`~/.config/claudey/bridge.yaml`)

```yaml
server:
  host: localhost
  port: 3000
  transport: sse

tools:
  directory: ~/.config/claudey/tools
  reload_on_change: true

security:
  max_execution_time: 300  # seconds
  allowed_commands:
    - /bin/echo
    - /usr/bin/curl
    - ~/scripts/*

logging:
  level: info
  audit_log: ~/.local/share/claudey/audit.log
```

### Sandbox Configuration (`~/.config/claudey/sandbox.conf`)

```bash
# Default bridge URL
BRIDGE_URL="http://localhost:3000"

# Additional bind mounts (Linux only)
EXTRA_MOUNTS=(
    "$HOME/.gitconfig:$HOME/.gitconfig:ro"
    "$HOME/.npmrc:$HOME/.npmrc:ro"
)

# Network restrictions
ALLOW_NETWORK=true
ALLOW_LOCALHOST_ONLY=true

# Verbose logging
VERBOSE=false
```

---

## Testing Strategy

### Unit Tests (Maven)
- Bridge server components
- Tool definition parser
- Input validator
- Command executor

**Run:** `mvn test -pl bridge`

### Integration Tests (Shell Scripts)
- Sandbox isolation verification
- Bridge-agent communication
- Platform-specific sandbox tests

**Run:** `sandbox/tests/test-linux.sh` or `sandbox/tests/test-macos.sh`

### E2E Tests (Python/Pytest)
- Complete workflows
- Real agent interactions

**Run:** `pytest tests/e2e/`

---

## Migration Path

### Phase 1: Core Infrastructure (Week 1-2)
- [ ] Create `bridge/` Maven module
- [ ] Implement tool definition parser
- [ ] Implement input validator
- [ ] Implement command executor
- [ ] Add unit tests

### Phase 2: MCP Integration (Week 3)
- [ ] Implement MCP protocol handler
- [ ] Add SSE transport support
- [ ] Implement three MCP tools
- [ ] Test with MCP inspector

### Phase 3: Sandbox Launcher (Week 4)
- [ ] Create `sandbox/` directory structure
- [ ] Implement Linux launcher (bubblewrap)
- [ ] Implement macOS launcher (sandbox-exec)
- [ ] Add integration tests

### Phase 4: Integration & Testing (Week 5)
- [ ] End-to-end testing
- [ ] Security penetration testing
- [ ] Performance benchmarking
- [ ] Documentation

### Phase 5: Packaging & Release (Week 6)
- [ ] Native compilation
- [ ] Package creation (.deb, .rpm, .pkg)
- [ ] Homebrew formula
- [ ] Release automation

---

## Open Questions & Decisions Needed

### 1. Should sandbox launcher be in the repo at all?

**Options:**
- **A)** Keep in `claudey/sandbox/` (current proposal)
  - ✅ Single repository for all components
  - ✅ Easier to version together
  - ❌ Mixes Java and shell code

- **B)** Separate repository (`claudey-sandbox`)
  - ✅ Clean separation of concerns
  - ✅ Independent versioning
  - ❌ Harder to coordinate releases

**Recommendation:** Option A - Keep in same repo for now, can split later if needed.

### 2. Should we support Windows?

**Challenges:**
- Windows has no equivalent to bubblewrap or sandbox-exec
- AppContainer is complex and poorly documented
- WSL2 could be an option but adds complexity

**Recommendation:** Start with Linux/macOS only, revisit Windows later.

### 3. How should bridge server be started?

**Options:**
- **A)** Manual: User runs `claudey-bridge serve`
- **B)** Systemd/LaunchDaemon: Auto-start on boot
- **C)** On-demand: Sandbox launcher starts bridge if not running

**Recommendation:** Support all three, default to manual for development.

### 4. Should tool definitions be in YAML or Markdown+Frontmatter?

**Current proposal:** Markdown with YAML frontmatter

**Pros:**
- Rich documentation alongside definition
- Human-readable
- Familiar format

**Cons:**
- More complex parsing
- YAML frontmatter is less common in Java ecosystem

**Alternative:** Pure YAML with separate docs

**Recommendation:** Stick with Markdown+Frontmatter for better UX.

### 5. How to handle tool definition validation?

**Options:**
- **A)** Validate at startup (fail fast)
- **B)** Validate on-demand (when tool is called)
- **C)** Both (validate at startup, re-validate on call)

**Recommendation:** Option C - Validate at startup for quick feedback, re-validate on call for security.

---

## Next Steps

1. **Review this architecture** - Confirm the three-component design
2. **Decide on open questions** - Especially repo structure and Windows support
3. **Create implementation plan** - Detailed task breakdown
4. **Set up bridge module** - Create Maven module structure
5. **Implement parser** - Start with tool definition parsing

---

## References

- [Quarkus MCP Server Extension](https://github.com/quarkiverse/quarkus-mcp-server)
- [Bubblewrap Documentation](https://github.com/containers/bubblewrap)
- [macOS Sandbox Guide](https://reverse.put.as/wp-content/uploads/2011/09/Apple-Sandbox-Guide-v1.0.pdf)
- [JSON Schema Specification](https://json-schema.org/)
- [MCP Specification](https://spec.modelcontextprotocol.io/)
