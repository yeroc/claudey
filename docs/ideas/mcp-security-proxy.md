# MCP Security Proxy: Rule of Two Implementation

**Date:** 2025-11-27  
**Status:** Brainstorming / Design Phase

## Executive Summary

An MCP proxy server that implements the "Rule of Two" security principle to protect against prompt injection attacks. By tracking session capabilities and enforcing that no more than 2 of 3 critical properties are active simultaneously, the proxy provides automated security guardrails while maintaining usability for legitimate AI agent workflows.

## Background

### The Prompt Injection Problem

Current AI agents are vulnerable to prompt injection attacks where malicious instructions embedded in untrusted data (websites, documents, user input) can cause the agent to:
- Exfiltrate sensitive data
- Modify critical systems
- Execute unauthorized actions

### The Rule of Two (Agents Rule of Two)

From Simon Willison's research and related security papers:

> Until robustness research allows us to reliably detect and refuse prompt injection, agents must satisfy no more than two of the following three properties within a session to avoid the highest impact consequences of prompt injection:
>
> - **[A]** An agent can process untrustworthy inputs
> - **[B]** An agent can have access to sensitive systems or private data
> - **[C]** An agent can change state or communicate externally

**Key Insight:** If all three properties are necessary, the agent should not operate autonomously and requires human-in-the-loop approval.

## Core Concept

Track which "taints" (A, B, or C) have been introduced to a session based on:
1. Which MCP tools have been invoked
2. What data sources have been accessed
3. What resources are available in the working directory

When a tool call would introduce a **third taint**, either:
- **Reject** the operation (strict mode)
- **Request human approval** via web dashboard (balanced mode)
- **Log warning** but allow (development mode)

## Architecture

### High-Level Design

```
┌──────────────────┐
│  Claude Code /   │
│  Gemini CLI /    │
│  AI Client       │
└────────┬─────────┘
         │ MCP Protocol
         ▼
┌────────────────────────────────┐
│   MCP Security Proxy           │
│  ┌──────────────────────────┐  │
│  │  Taint Tracking Engine   │  │
│  │  - Session state         │  │
│  │  - Active taints [A,B,C] │  │
│  │  - Rule evaluation       │  │
│  └──────────────────────────┘  │
│  ┌──────────────────────────┐  │
│  │  Approval Server         │  │
│  │  - Web dashboard         │  │
│  │  - Manual approval UI    │  │
│  │  - Audit logs            │  │
│  └──────────────────────────┘  │
│  ┌──────────────────────────┐  │
│  │  Configuration Manager   │  │
│  │  - Server capabilities   │  │
│  │  - Tool rules            │  │
│  │  - Policies              │  │
│  └──────────────────────────┘  │
└────────┬───────────────────────┘
         │ MCP Protocol (forwarded)
         ▼
┌─────────────────────────┐
│  Actual MCP Servers     │
│  - Database             │
│  - Filesystem           │
│  - Web Scraper          │
│  - Slack, Email, etc.   │
└─────────────────────────┘
```

### Transport

- **HTTP-based MCP server** (like CCO-MCP)
- Exposes MCP endpoint at `http://localhost:PORT/mcp`
- Web dashboard at `http://localhost:PORT/`
- Real-time updates via Server-Sent Events (SSE)

### Components

#### 1. Taint Tracking Engine

**Responsibilities:**
- Maintain session state for each active conversation/context
- Track which taints [A, B, C] are active
- Evaluate whether a new tool call would violate Rule of Two
- Generate human-readable explanations for approval requests

**Session Lifecycle:**
- Session starts when first tool call arrives (identified by agent ID)
- Taints accumulate as tools are invoked
- Session can be manually reset via dashboard
- Sessions expire after configured timeout (default: 1 hour of inactivity)

#### 2. Approval Server

**Responsibilities:**
- Expose `approval_prompt` tool for Claude Code integration
- Provide web dashboard for manual approval
- Handle approval timeouts (auto-deny after N minutes)
- Maintain audit trail

**Integration Points:**
- Claude Code: `--permission-prompt-tool mcp__security-proxy__approval_prompt`
- Other clients: HTTP polling or webhook notifications

#### 3. Configuration Manager

**Responsibilities:**
- Load and validate configuration files
- Provide web UI for configuration updates
- Hot-reload configuration without restart

## Configuration Schema

### Server Capabilities

Each MCP server (or specific resource) is tagged with capabilities:

```yaml
servers:
  # Untrusted data source
  web-scraper:
    connection: "stdio"
    command: "mcp-server-puppeteer"
    capabilities: [A]
    notes: "Scrapes arbitrary URLs - untrusted input"
  
  # Sensitive data access
  customer-database:
    connection: "stdio"
    command: "mcp-server-postgres"
    args: ["--db=customers"]
    capabilities: [B, C]
    notes: "Customer DB - contains PII and can modify state"
  
  # Public read-only data
  public-docs-db:
    connection: "stdio"
    command: "mcp-server-postgres"
    args: ["--db=public_docs"]
    capabilities: [C]
    notes: "Public documentation - can modify but not sensitive"
  
  # External communication
  slack-notifier:
    connection: "stdio"
    command: "mcp-server-slack"
    capabilities: [C]
    notes: "Sends messages to external systems"

# Working directory taints
workspace:
  default_capabilities: []
  path_rules:
    - pattern: "**/customer-data/**"
      capabilities: [B]
    - pattern: "**/public/**"
      capabilities: []
```

### Tool-Specific Rules

Override or augment server-level capabilities:

```yaml
tool_rules:
  # Dangerous tools that always require approval
  - tool: "bash"
    action: "require_approval"
    reason: "Arbitrary code execution"
    bypass_taint_check: false
  
  # Safe read-only operations
  - tool: "read_file"
    server: "public-docs-db"
    action: "auto_approve"
    max_file_size_mb: 10
  
  # Conditional approval based on parameters
  - tool: "sql_query"
    server: "customer-database"
    conditions:
      - parameter: "query"
        matches: "^SELECT.*"
        action: "allow"
      - parameter: "query"
        matches: ".*(UPDATE|DELETE|INSERT).*"
        action: "require_approval"
```

### Session Policies

```yaml
policies:
  # Strict mode for production
  strict:
    max_taints: 2
    on_violation: "reject"
    message: "Operation rejected: would violate Rule of Two security policy"
  
  # Balanced mode with approval
  balanced:
    max_taints: 2
    on_violation: "require_approval"
    approval_timeout_minutes: 5
    default_on_timeout: "deny"
  
  # Development mode
  development:
    max_taints: 3
    on_violation: "warn_and_allow"
    log_level: "debug"

# Active policy
active_policy: "balanced"
```

## User Experience

### For AI Agents

**Transparent Operation:**
- Compliant tool calls pass through without delay
- Agent receives clear error messages when operations are blocked
- Approval requests include context about why approval is needed

**Example Interaction:**

```
Agent: I'll fetch that website and update the customer database...
Proxy: ⚠️  Approval Required
        
        You've already:
        • [A] Accessed untrusted data (web-scraper: read_url)
        • [B] Accessed sensitive data (customer-database: SELECT query)
        
        This operation would:
        • [C] Modify state (customer-database: UPDATE query)
        
        This violates the Rule of Two security policy.
        Awaiting human approval... (timeout in 5:00)
```

### For Human Operators

**Web Dashboard:**

**Real-time Monitoring:**
- Live feed of all tool calls
- Color-coded by taint type and status
- Session state visualization

**Approval Queue:**
- Pending approvals with context
- One-click approve/deny
- Explanation of why approval is needed
- Preview of tool parameters

**Audit Trail:**
- Searchable history
- Filter by agent, tool, server, taint type
- Export for compliance

**Configuration:**
- Visual editor for server capabilities
- Test mode to preview rule changes
- Import/export configurations

### URL Examples

```
http://localhost:8660/                  # Dashboard home
http://localhost:8660/approvals         # Pending approvals
http://localhost:8660/audit             # Audit trail
http://localhost:8660/config            # Configuration editor
http://localhost:8660/sessions          # Active sessions
http://localhost:8660/mcp               # MCP endpoint
```

## Implementation Considerations

### 1. Classification Challenges

**Ambiguous Tool Capabilities:**

Some tools don't fit neatly into categories:

| Tool | Challenge | Approach |
|------|-----------|----------|
| `grep_search` | Is workspace data "sensitive"? | Depend on path_rules configuration |
| `read_url_content` | Public vs private URLs? | Heuristics + allowlist for known-safe domains |
| `write_to_file` | State change vs communication? | If in git repo, could be [C] if pushed |

**Solutions:**
- **Conservative defaults:** When ambiguous, assume higher risk
- **Context-aware tagging:** Same tool, different taints based on parameters
- **User override:** Let operators tag specific invocations in audit trail

### 2. Taint Propagation

**When does taint spread?**

```yaml
taint_propagation:
  # Does reading a tainted file propagate taint?
  file_read_propagates: true
  
  # Does processing tainted data in memory propagate?
  computation_propagates: true
  
  # Does taint decay over time or tokens?
  decay_enabled: false
  decay_half_life_minutes: 30
```

**Example:**
```
1. Agent reads untrusted URL [A]
2. Agent parses data, extracts email addresses
   → Still tainted [A]
3. Agent reads customer DB to check if emails exist [B]
   → Session now has [A, B]
4. Agent wants to write results to file
   → If file is in git repo, this could be [C]
   → Trigger approval
```

### 3. Session Boundaries

**When should sessions reset?**

Options:
- **Per conversation:** New conversation = new session
- **Per task:** Using `task_boundary` calls to segment
- **Time-based:** Session expires after N minutes no activity
- **Manual:** User explicitly resets via dashboard
- **Taint-based:** Reset after high-risk operation completes

**Recommendation:** Hybrid approach
- Default: conversation-scoped
- Allow clients to signal session boundaries via MCP metadata
- Manual reset always available

### 4. Client Compatibility

**Different clients have different approval mechanisms:**

| Client | Approval Mechanism | Integration |
|--------|-------------------|-------------|
| Claude Code CLI | `--permission-prompt-tool` flag | Expose `approval_prompt` tool |
| Claude Desktop | No built-in approval | Interactive web dashboard |
| Gemini CLI | Unknown | Need to research |
| Custom clients | Varies | Webhook notifications? |

**Proxy must support multiple modes:**
- **Tool-based approval** (for Claude Code)
- **Webhook notifications** (for custom integrations)
- **Web-based approval** (universal fallback)
- **Pre-approval** (authorized operations)

### 5. Performance

**Latency considerations:**
- Tool calls must be intercepted and evaluated
- Taint checking should be <10ms
- Approval UI must be responsive
- Audit logging shouldn't block tool execution

**Optimization strategies:**
- In-memory session state (with persistence)
- Async audit logging
- Compiled rule evaluation (don't parse YAML per-call)
- SSE keeps browser connections alive

## Security Considerations

### Proxy as Security Boundary

The proxy is a **critical security component**. If compromised:
- Attacker could disable taint checking
- Approval rules could be bypassed
- Audit logs could be falsified

**Mitigations:**
- Proxy runs locally on user's machine (not cloud service)
- Configuration files have restrictive permissions
- Audit logs are append-only
- Optional: cryptographically signed audit trail
- Optional: integration with system authentication (OS keyring)

### URL Safety Detection

Determining if a URL is "untrusted" is hard:

**Heuristic approaches:**
- **Domain allowlist:** `docs.anthropic.com`, `wikipedia.org` → trusted
- **URL structure:** UUIDs/tokens in path → likely private
- **Response headers:** `X-Robots-Tag: noindex` → private content
- **Authentication required:** If fetch returns 401/403 → sensitive

**Conservative default:** All URLs are [A] unless explicitly allowlisted

### Sensitive Data Detection

How to know if a file/database contains sensitive data?

**Explicit tagging:**
- Path-based rules (e.g., `/customer-data/*`)
- Database connection strings (e.g., DB_NAME contains "customer")
- MCP server configuration

**Do NOT:** Try to detect PII via content scanning
- Too slow
- Too error-prone
- Privacy concerns about proxy scanning content

## Comparison with CCO-MCP

| Feature | CCO-MCP | This Proxy |
|---------|---------|------------|
| **Security Model** | Tool-based allowlist/denylist | Capability-based taint tracking |
| **Statefulness** | Stateless (per-tool evaluation) | Stateful (session-based taints) |
| **Composability** | Manual rules for each tool | Automatic risk from capability combinations |
| **Explanation** | "Tool X requires approval" | "You've done A+B, can't do C without approval" |
| **Configuration** | Tool rules + priorities | Server capabilities + taint policies |
| **Scope** | Claude Code specific | Generic MCP clients |

**Can We Combine Both?**

Yes! Use tool-level rules (like CCO-MCP) as a baseline, plus taint tracking:

```yaml
# Static tool rules (CCO-MCP style)
tool_rules:
  - tool: "bash"
    action: "require_approval"

# Dynamic capability tracking (our innovation)
servers:
  customer-db:
    capabilities: [B, C]

# Both are enforced
enforcement:
  - Check tool-level rules first
  - If approved, check taint rules
  - Both must pass for auto-approval
```

## Future Enhancements

### 1. Risk Scoring

Instead of binary taint flags, use risk scores:

```yaml
servers:
  customer-db:
    risk_score:
      untrusted_input: 0.0      # [A]
      sensitive_data: 0.9        # [B]
      state_change: 0.7          # [C]

policy:
  max_combined_risk: 1.5
```

### 2. Taint Decay

Taint reduces over time or after processing:

```yaml
taint_decay:
  enabled: true
  half_life_tokens: 10000
  # After 10k tokens, [A] reduces from 1.0 to 0.5
```

### 3. Sub-session Isolation

Create isolated sub-sessions for risky operations:

```
Agent: I need to check this URL and update the DB
Proxy: I'll create an isolated sub-session for the URL check
       → Sub-session has [A]
       → Main session remains clean
       → Result is sanitized before returning
```

### 4. Machine Learning

Learn from approval patterns:

```
"User always approves fetch + DB read + write for workflow X"
→ Create auto-approval rule for this pattern
```

### 5. Integration with Other Security Tools

- **Git hooks:** Prevent committing sensitive data
- **Secret scanners:** Detect credentials in tool parameters
- **Network proxies:** Block certain external communications
- **IDE integration:** Show taint status in editor

## Open Questions

1. **Should taint be per-tool-type or per-invocation?**
   - E.g., is `read_url_content` always [A], or only for untrusted domains?

2. **How granular should path rules be?**
   - File-level? Directory-level? Git repository boundaries?

3. **What's the right default timeout for approval?**
   - 5 minutes? 30 minutes? Configurable?

4. **Should we support "reason" annotations from agents?**
   - Agent says "I need to do X because Y" → helps human decide

5. **How to handle legitimate workflows that need all 3 properties?**
   - Always require approval?
   - Allow "blessed" workflows?
   - Temporary elevation of privileges?

6. **Taint decay: good idea or security theater?**
   - Is there a principled way to decay taint?
   - Or is this just giving false confidence?

## Next Steps

### Phase 1: Proof of Concept
- [ ] Implement basic MCP proxy (forward-only)
- [ ] Add simple taint tracking (hardcoded rules)
- [ ] Build minimal web dashboard
- [ ] Test with Claude Code CLI

### Phase 2: Core Features
- [ ] YAML configuration system
- [ ] Session state management
- [ ] Approval workflow (web + tool-based)
- [ ] Audit logging

### Phase 3: Polish
- [ ] Real-time dashboard with SSE
- [ ] Configuration editor UI
- [ ] Documentation & examples
- [ ] Docker deployment

### Phase 4: Advanced Features
- [ ] Risk scoring
- [ ] Taint propagation rules
- [ ] ML-based rule suggestions
- [ ] Integration testing with multiple clients

## References

- [Simon Willison's "Lethal Trifecta"](https://simonwillison.net/2024/Oct/21/claude-artifacts/)
- [CCO-MCP - Real-time audit and approval system](https://github.com/toolprint/cco-mcp)
- [Minimal MCP Approval Demo](https://github.com/mmarcen/test_permission-prompt-tool)
- [MCP Specification](https://spec.modelcontextprotocol.io/)
- [Claude Code Permission Prompt Tool](https://docs.anthropic.com/en/docs/claude-code/sdk#custom-permission-prompt-tool)
- [Agent Security: The Rule of Two](https://www.anthropic.com/index/prompt-injection-and-the-rule-of-two)

## License Considerations

If building this as OSS:
- Likely MIT or Apache 2.0 for maximum adoption
- Clear documentation that this is a security tool, not a guarantee
- Disclaimer about prompt injection still being an open problem

---

**Contributors Welcome!**

This is an early-stage design. Feedback, suggestions, and collaboration are highly encouraged.
