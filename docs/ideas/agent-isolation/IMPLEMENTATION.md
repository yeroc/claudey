# Claudey Bridge: Implementation Plan

**Approach:** Test-Driven Development (TDD)  
**Strategy:** Validate risky/uncertain parts first, fail fast if needed

---

## Risk Assessment

### 🔴 HIGH RISK (Validate First)
1. **Sandbox isolation actually works** - Can we prevent escape on both platforms?
2. **Bridge-sandbox communication** - Can sandboxed process reach localhost bridge?
3. **Agent-specific profiles** - Can we selectively mount files (e.g., `~/.gitconfig` but not `~/.ssh/id_rsa`)?

### 🟡 MEDIUM RISK
4. **Input validation prevents injection** - Does regex/enum validation catch all attacks?
5. **MCP protocol integration** - Does Quarkus MCP server work as expected?

### 🟢 LOW RISK (Standard Engineering)
6. Tool definition parsing (YAML + Markdown)
7. Command execution
8. Packaging and distribution

---

## Phase 0: Proof of Concept (3-5 days)

**Goal:** Validate risky assumptions or abandon early

### Day 1-2: Sandbox Isolation (macOS)

**Objective:** Prove Seatbelt can create a secure sandbox

**Tests to run:**
1. ✅ Can read workspace (`$PWD`)
2. ❌ Cannot read secrets (`~/.test-secrets/`)
3. ❌ Cannot read SSH keys (`~/.ssh/id_rsa`)
4. ✅ Can selectively allow `.gitconfig` (read-only)
5. ❌ Cannot escape via symlinks

**Create:** `test-sandbox-macos.sh` with Seatbelt profile

**Success:** All tests pass  
**Failure:** Project dead on macOS, need alternative approach

**Note:** Linux (bubblewrap) tests can wait for VM/CI

---

### Day 3: Bridge Communication

**Objective:** Sandboxed process can reach localhost bridge

**Test:**
- Start simple HTTP server on host (port 3000)
- From sandbox, `curl http://localhost:3000/`
- Should succeed

**Success:** Network communication works  
**Failure:** Need to investigate Seatbelt network rules

---

### Day 4-5: End-to-End

**Objective:** Execute whitelisted command via MCP bridge

**Setup:**
1. Create minimal `bridge/` Quarkus module
2. Add one `@McpTool` method: `execute(command, args)`
3. Hardcode whitelist: `echo`, `pwd`

**Test:**
- Start bridge: `mvn quarkus:dev`
- From sandbox, call MCP tool
- Should execute and return output

**Success:** Core concept proven  
**Failure:** Reassess approach

**Decision Point:** If all pass → Phase 1. If any fail → pivot or abandon.

---

## Phase 1: Core Implementation (2-3 weeks)

**Only proceed if Phase 0 succeeds**

### Week 1: Bridge Server (Java/Quarkus)

#### Repository Structure
```
claudey/
├── bridge/                    # Maven module
│   ├── pom.xml
│   └── src/
│       ├── main/java/org/geekden/claudey/bridge/
│       │   ├── BridgeServer.java
│       │   ├── ToolDefinitionParser.java
│       │   ├── InputValidator.java
│       │   ├── CommandExecutor.java
│       │   └── McpToolHandler.java
│       └── test/java/org/geekden/claudey/bridge/
│           ├── InputValidatorTest.java  # TDD: Write first!
│           └── CommandExecutorTest.java
└── sandbox/                   # NOT a Maven module
    ├── linux/
    │   └── cs                 # Bash script
    └── macos/
        ├── cs                 # Bash script
        └── claude-code.sb     # Seatbelt profile
```

#### TDD: Input Validator (CRITICAL - Security)

**Write tests first:**
```java
// InputValidatorTest.java
@Test
void rejectsCommandInjection() {
    ToolDefinition tool = new ToolDefinition(
        "deploy",
        List.of(new ArgDef("branch", "string", "^[a-z0-9-]+$"))
    );
    
    InputValidator validator = new InputValidator(tool);
    
    // All these should be rejected
    assertThrows(ValidationException.class, 
        () -> validator.validate(Map.of("branch", "; rm -rf /")));
    assertThrows(ValidationException.class,
        () -> validator.validate(Map.of("branch", "$(whoami)")));
    assertThrows(ValidationException.class,
        () -> validator.validate(Map.of("branch", "`cat /etc/passwd`")));
}

@Test
void acceptsValidInput() {
    ToolDefinition tool = new ToolDefinition(
        "deploy",
        List.of(new ArgDef("branch", "string", "^[a-z0-9-]+$"))
    );
    
    InputValidator validator = new InputValidator(tool);
    
    // This should pass
    assertDoesNotThrow(
        () -> validator.validate(Map.of("branch", "main")));
}
```

**Then implement to pass tests**

#### MCP Server Integration

```java
// McpToolHandler.java
@ApplicationScoped
public class McpToolHandler {
    
    @McpTool(description = "List available privileged operations")
    public List<ProgramInfo> list_programs() {
        // Load from ~/.config/claudey/tools/
    }
    
    @McpTool(description = "Get help for a specific program")
    public String help(@McpToolParam(description = "Program name") String program) {
        // Return Markdown documentation
    }
    
    @McpTool(description = "Execute a whitelisted program")
    public ExecutionResult execute(
        @McpToolParam(description = "Program name") String program,
        @McpToolParam(description = "Arguments") List<String> args
    ) {
        // Validate and execute
    }
}
```

**Test with MCP Inspector:**
```bash
mvn quarkus:dev -pl bridge
# In another terminal
mcp-inspector http://localhost:3000/mcp
```

### Week 2: Sandbox Launcher (`cs`)

#### Linux Implementation
```bash
#!/bin/bash
# sandbox/linux/cs

set -euo pipefail

# Load config
CONFIG_DIR="${XDG_CONFIG_HOME:-$HOME/.config}/claudey"
DEFAULT_AGENT=$(yq '.default_agent' "$CONFIG_DIR/config.yaml")

AGENT="${1:-$DEFAULT_AGENT}"
WORKSPACE="$PWD"

# Load agent profile
PROFILE="$CONFIG_DIR/profiles/${AGENT}.yaml"

if [[ ! -f "$PROFILE" ]]; then
    echo "Error: Profile not found: $PROFILE" >&2
    exit 1
fi

# Parse profile and build bwrap command
BWRAP_ARGS=(
    --ro-bind / /
    --dev-bind /dev /dev
    --proc /proc
    --tmpfs "$HOME"
    --bind "$WORKSPACE" "$WORKSPACE"
    --share-net
    --die-with-parent
)

# Add mounts from profile
while IFS= read -r mount; do
    SOURCE=$(echo "$mount" | yq '.source')
    TARGET=$(echo "$mount" | yq '.target')
    READONLY=$(echo "$mount" | yq '.readonly')
    
    if [[ "$READONLY" == "true" ]]; then
        BWRAP_ARGS+=(--ro-bind "$SOURCE" "$TARGET")
    else
        BWRAP_ARGS+=(--bind "$SOURCE" "$TARGET")
    fi
done < <(yq '.mounts[]' "$PROFILE" -o json)

# Get agent command
AGENT_CMD=$(yq ".agents.${AGENT}.command" "$CONFIG_DIR/config.yaml")

# Launch
exec bwrap "${BWRAP_ARGS[@]}" "$AGENT_CMD"
```

#### macOS Implementation
```bash
#!/bin/bash
# sandbox/macos/cs

set -euo pipefail

CONFIG_DIR="${XDG_CONFIG_HOME:-$HOME/.config}/claudey"
DEFAULT_AGENT=$(yq '.default_agent' "$CONFIG_DIR/config.yaml")

AGENT="${1:-$DEFAULT_AGENT}"
WORKSPACE="$PWD"

# Load Seatbelt profile
PROFILE="$CONFIG_DIR/profiles/${AGENT}.sb"

if [[ ! -f "$PROFILE" ]]; then
    echo "Error: Profile not found: $PROFILE" >&2
    exit 1
fi

# Get agent command
AGENT_CMD=$(yq ".agents.${AGENT}.command" "$CONFIG_DIR/config.yaml")

# Launch with sandbox-exec
exec sandbox-exec \
    -f "$PROFILE" \
    -D HOME="$HOME" \
    -D WORKSPACE="$WORKSPACE" \
    "$AGENT_CMD"
```

**Test:**
```bash
# Install cs
sudo cp sandbox/linux/cs /usr/local/bin/
chmod +x /usr/local/bin/cs

# Create minimal config
mkdir -p ~/.config/claudey/profiles
cat > ~/.config/claudey/config.yaml << EOF
default_agent: code
agents:
  code:
    command: echo
EOF

cat > ~/.config/claudey/profiles/code.yaml << EOF
mounts:
  - source: ~/.gitconfig
    target: ~/.gitconfig
    readonly: true
EOF

# Test
cs  # Should launch echo in sandbox
```

### Week 3: Integration & Polish

- End-to-end testing with real agents (Claude Code, Gemini)
- Example tool definitions
- Error handling and logging
- Documentation

---

## Phase 2: Distribution (1 week)

### Build Process

```bash
# Build native binaries
mvn clean package -Pnative -pl bridge

# Package for each platform
./packaging/debian/build-deb.sh
./packaging/rpm/build-rpm.sh
./packaging/homebrew/update-formula.sh
```

### GitHub Actions

```yaml
# .github/workflows/release.yml
name: Release

on:
  push:
    tags: ['v*']

jobs:
  build-native:
    strategy:
      matrix:
        include:
          - os: ubuntu-latest
            platform: linux
          - os: macos-latest
            platform: macos
    
    runs-on: ${{ matrix.os }}
    
    steps:
      - uses: actions/checkout@v3
      - uses: graalvm/setup-graalvm@v1
      
      - name: Build native binary
        run: mvn package -Pnative -pl bridge
      
      - name: Create tarball
        run: |
          mkdir -p dist/bin
          cp bridge/target/claudey-bridge-*-runner dist/bin/claudey-bridge
          cp sandbox/${{ matrix.platform }}/cs dist/bin/
          tar czf claudey-bridge-${{ matrix.platform }}.tar.gz -C dist .
      
      - uses: actions/upload-artifact@v3
        with:
          name: claudey-bridge-${{ matrix.platform }}
          path: claudey-bridge-${{ matrix.platform }}.tar.gz
```

### Distribution Channels

**macOS:**
```bash
brew tap yeroc/claudey
brew install claudey-bridge
```

**Linux (Debian/Ubuntu):**
```bash
wget https://github.com/yeroc/claudey/releases/latest/download/claudey-bridge_0.1.0_amd64.deb
sudo dpkg -i claudey-bridge_0.1.0_amd64.deb
```

**Linux (RHEL/Fedora):**
```bash
wget https://github.com/yeroc/claudey/releases/latest/download/claudey-bridge-0.1.0-1.x86_64.rpm
sudo rpm -i claudey-bridge-0.1.0-1.x86_64.rpm
```

---

## Testing Strategy

### Critical Tests (Security)

1. **Input Validation** - Prevent command injection
   - Test all known injection vectors
   - Regex validation works
   - Enum validation works

2. **Sandbox Isolation** - Prevent escape
   - Cannot read SSH keys
   - Cannot read cloud credentials
   - Cannot escape via symlinks
   - Cannot access /proc/1/root

3. **Bridge Authorization** - Only whitelisted operations
   - Unknown tools rejected
   - Invalid arguments rejected
   - Path traversal blocked

### Integration Tests

4. **End-to-End Workflows**
   - Launch agent with `cs`
   - Agent calls bridge tool
   - Tool executes successfully
   - Agent receives response

5. **Agent Profiles**
   - Claude Code profile works
   - Gemini profile works
   - Minimal profile works

### Standard Tests

6. **Unit Tests** - Normal TDD for non-security code
   - Tool definition parsing
   - Command execution
   - MCP protocol handling

---

## Timeline

**Phase 0 (PoC):** 3-5 days  
**Phase 1 (Core):** 2-3 weeks  
**Phase 2 (Distribution):** 1 week  

**Total:** ~4-5 weeks to MVP

---

## Success Criteria

### Phase 0
- [ ] Sandbox prevents reading SSH keys
- [ ] Sandbox allows workspace access
- [ ] Sandbox allows selective file mounting
- [ ] Sandboxed process can reach localhost bridge
- [ ] End-to-end command execution works

### Phase 1
- [ ] Bridge server runs and exposes MCP tools
- [ ] Input validation blocks all injection attacks
- [ ] `cs` launches agents in sandbox
- [ ] Agent profiles work for Claude Code and Gemini
- [ ] End-to-end workflow tested with real agents

### Phase 2
- [ ] Native binaries built for Linux and macOS
- [ ] Packages created (.deb, .rpm, Homebrew)
- [ ] Installation tested on fresh systems
- [ ] Documentation complete

---

## Open Questions

1. **Seatbelt file-level mounting:** Can macOS Seatbelt selectively allow individual files like `~/.gitconfig`?
   - **Mitigation:** If not, copy whitelisted files to temp directory

2. **Agent compatibility:** Do all agents work when sandboxed?
   - **Mitigation:** Test early with Claude Code and Gemini

3. **Performance:** Is sandbox overhead acceptable?
   - **Mitigation:** Benchmark in Phase 0

---

## Next Steps

1. **Review this plan** - Confirm approach
2. **Start Phase 0** - Run PoC tests
3. **Decision point** - Proceed or pivot based on results
