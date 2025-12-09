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

## Phase 0: Bridge MVP with `cco` (1 week)

**Goal:** Prove the MCP Bridge concept using `cco` for immediate isolation.

### step 1: Bridge Server Prototype
1. Create `bridge/` Maven module (Quarkus + MCP)
2. Implement basic MCP server with `list_programs`, `help`, `execute`
3. Implement `InputValidator` (Critical Security Component)
4. Create simple tool definition: `echo_test`

### Step 2: `cco` Integration
1. Install `cco`
2. specific configuration:
   ```yaml
   # ~/.config/cco/config.yaml
   network: host  # Allow access to localhost:3000
   ```
3. Verify connection:
   - Start Bridge: `mvn quarkus:dev`
   - Start `cco`: `cco --safe`
   - Agent: `curl http://localhost:3000/mcp` should work

### Step 3: End-to-End Test
- **Scenario:** Agent in `cco` calls `execute("echo_test", ["hello"])`
- **Result:** Bridge executes `echo hello` on host and returns output

---

## Phase 1: Bridge Maturity (2 weeks)

**Goal:** Production-ready Bridge Server.

1. **Tool Definition Parser:**
   - Parse Markdown+Frontmatter definitions
   - JSON Schema generation for MCP

2. **Security Hardening:**
   - Strict input validation (Regex, Enums)
   - Path traversal prevention
   - Execution timeouts

3. **Distribution (Bridge Only):**
   - Native binary build (GraalVM)
   - Homebrew formula for `claudey-bridge`

---

## Phase 2: Native Sandbox Launcher (Long Term)

**Goal:** Remove `cco` dependency and Docker fallback. Build strictly native `cs` launcher.

1. **Linux Launcher:**
   - Implement `bwrap` logic matching `cco`'s native mode

2. **macOS Launcher:**
   - Implement `sandbox-exec` logic matching `cco`'s native mode

3. **Integration:**
   - Replace `cco` in user workflow with `cs`
   - Single installable package (`brew install claudey-bridge` includes both)

---

## Testing Strategy

### Modified for Phase 0/1
- **Isolation Testing:** Rely on `cco`'s existing guarantees (verified manually).
- **Bridge Testing:** Heavy focus on `InputValidator` and tool definitions.

### Phase 2 Testing
- **Isolation Verification:** Critical when we build our own launcher.
  - Test escape vectors
  - Test filesystem visibility

---

## Next Steps

1. **Install `cco`** and verify it works on your machine.
2. **Scaffold `bridge` module** in the repo.
3. **Write `InputValidator` tests** (TDD start).

