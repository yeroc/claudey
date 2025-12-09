# SSH-Based Credential Isolation for Claude Code

An invisible shim that lets Claude Code run your existing scripts autonomously without giving it access to credentials.

## The Problem

You want Claude Code (CC) to run scripts autonomously that access third-party APIs and services requiring credentials (GitHub tokens, AWS keys, database passwords, etc.). However, giving CC direct access to these credentials creates security risks:

1. **Prompt Injection**: A malicious user could trick CC into exfiltrating credentials
2. **Unintended Actions**: CC could be manipulated into using credentials for unauthorized operations
3. **Credential Exposure**: CC can read files in your home directory, including where credentials are stored

Traditional solutions don't work:
- **Manual approval**: Defeats autonomous operation
- **Credential helpers**: CC can extract credentials from responses
- **Environment variables**: CC can read the environment  
- **File permissions alone**: If both CC and scripts run as same user, both can read the same files
- **setuid binaries**: Security nightmare, requires root

## The Solution: Namespace Isolation + SSH Bridge

**Core principle:** Your scripts run exactly as they do today, unchanged. CC just can't see the credentials they read.

**How it works:**
1. **Bubblewrap namespace** hides credential directories from CC
2. **SSH bridge** lets CC execute whitelisted scripts outside the namespace
3. **Scripts run normally** with full filesystem access, reading credentials as usual
4. **Optional shims** make it transparent to CC

**Key insight:** The SSH connection crosses the namespace boundary. CC connects from inside its restricted view, but scripts execute outside with normal access to credentials.

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│  BUBBLEWRAP NAMESPACE (restricted filesystem view)          │
│                                                             │
│  ┌────────────────────────────────────────────────────┐     │
│  │  Claude Code Process                               │     │
│  │  • Sees most of your home directory                │     │
│  │  • CANNOT see ~/.secrets/ (tmpfs overlay)          │     │
│  │  • CANNOT see secured-ops scripts                  │     │
│  │  • HAS access to ~/.ssh/cc-identity key            │     │
│  └──────────────────┬─────────────────────────────────┘     │
│                     │                                       │
│                     │ ssh -p 2222 -i ~/.ssh/cc-identity ... │
│                     │                                       │
└─────────────────────┼───────────────────────────────────────┘
                      │
                      │ (SSH connection crosses namespace)
                      │
┌─────────────────────▼───────────────────────────────────────┐
│  HOST NAMESPACE (full filesystem access)                    │
│                                                             │
│  ┌──────────────────────────────────────────────────────┐   │
│  │  SSH Daemon (localhost:2222)                         │   │
│  │  • Runs OUTSIDE bubblewrap                           │   │
│  │  • Validates CC identity key                         │   │
│  │  • Enforces forced command (dispatcher)              │   │
│  └──────────────────┬───────────────────────────────────┘   │
│                     │                                       │
│  ┌──────────────────▼───────────────────────────────────┐   │
│  │  Dispatcher Script                                   │   │
│  │  • Validates requested script is whitelisted         │   │
│  │  • Executes the actual script                        │   │
│  └──────────────────┬───────────────────────────────────┘   │
│                     │                                       │
│  ┌──────────────────▼───────────────────────────────────┐   │
│  │  YOUR EXISTING SCRIPTS (unchanged!)                  │   │
│  │  • Run with normal filesystem access                 │   │
│  │  • Read credentials from ~/.secrets/                 │   │
│  │  • Work exactly as they always have                  │   │
│  └──────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

## Requirements

### Required Software

- **Linux** - This approach uses Linux-specific features
- **bubblewrap** - Namespace isolation tool
  - Debian/Ubuntu: `sudo apt install bubblewrap`
  - Fedora/RHEL: `sudo dnf install bubblewrap`
  - Arch: `sudo pacman -S bubblewrap`
- **OpenSSH server** - SSH daemon (sshd)
  - Debian/Ubuntu: `sudo apt install openssh-server`
  - Fedora/RHEL: `sudo dnf install openssh-server`
  - Arch: `sudo pacman -S openssh`
- **systemd** - For socket activation (user services)
  - Included in most modern Linux distributions
  - Verify user services are enabled: `systemctl --user status`
- **bash** - For scripts and dispatcher
  - Standard on all Linux distributions

### System Configuration

- **User systemd services** must be enabled
  - Run `loginctl enable-linger $USER` to enable if needed
  - Verify with: `systemctl --user status`
- **Unprivileged port binding** (optional but recommended)
  - Allows binding to ports > 1024 without root
  - Already available by default on most systems
- **Sufficient mount namespace support**
  - Check: `cat /proc/sys/kernel/unprivileged_userns_clone` should be `1`
  - If `0`, you may need root or kernel parameter changes

### Permissions

- **No root/sudo required** - Everything runs as your regular user
- **Write access to your home directory** - For creating config files
- **Ability to run bubblewrap** - Some systems restrict unprivileged namespace creation

### Optional (for performance optimization)

- **OpenSSH with "none" cipher support** - For maximum performance
  - Check: `ssh -Q cipher | grep none`
  - If not available, fallback ciphers work fine

### Compatibility Notes

**Known to work:**
- Ubuntu 20.04+
- Debian 11+
- Fedora 35+
- Arch Linux (current)
- RHEL/CentOS 8+

**May require additional configuration:**
- Systems with restricted unprivileged user namespaces
- SELinux in enforcing mode (may need policy adjustments)
- AppArmor with strict profiles

**Will NOT work:**
- macOS (no bubblewrap support)
- Windows (use WSL2 instead)
- Docker containers (nested namespaces often restricted)

## How It Works: Component Breakdown

### 1. Bubblewrap Namespace (REQUIRED)

**What it does:**
Creates a restricted filesystem view for Claude Code where credential directories simply don't exist.

**Example:**
```bash
# Launch CC in restricted namespace
bwrap \
  --ro-bind / / \
  --dev-bind /dev /dev \
  --bind /tmp /tmp \
  --bind "$HOME" "$HOME" \
  --tmpfs "$HOME/.secrets" \
  --tmpfs "$HOME/.config/sshd-agent" \
  --proc /proc \
  --unshare-all \
  --share-net \
  --die-with-parent \
  claude-code
```

**Inside the namespace:**
- CC sees `~/.secrets/` as an empty directory
- CC cannot read credential files (they don't exist in its view)
- CC sees everything else normally

**Outside the namespace:**
- Your normal shell sees real `~/.secrets/` with credentials
- Scripts executed via SSH see real filesystem
- No changes to your existing setup

### 2. SSH Daemon (Execution Bridge)

**What it does:**
Provides a way for CC to execute scripts outside the namespace.

**Key properties:**
- Runs on localhost:2222 (unprivileged port)
- Runs OUTSIDE bubblewrap (has full filesystem access)
- Uses systemd socket activation (starts on-demand)
- Only accepts CC identity key
- Forces all connections through dispatcher script

**Configuration:**
```ini
# ~/.config/sshd-agent/sshd_config
Port 2222
ListenAddress 127.0.0.1
HostKey /home/you/.config/sshd-agent/ssh_host_ed25519_key
AuthorizedKeysFile /home/you/.config/sshd-agent/authorized_keys
PubkeyAuthentication yes
PasswordAuthentication no
```

**Authorized keys with forced command:**
```bash
# ~/.config/sshd-agent/authorized_keys
command="/home/you/.config/sshd-agent/secured-ops/dispatcher",no-pty,no-port-forwarding,no-X11-forwarding,no-agent-forwarding ssh-ed25519 AAAA...cc-identity-public-key...
```

**What "forced command" means:**
- Regardless of what command CC requests via SSH
- SSH ALWAYS executes the dispatcher script
- CC's requested command is passed in `$SSH_ORIGINAL_COMMAND`
- Dispatcher decides whether to honor the request

### 3. Dispatcher Script (Whitelist Enforcer)

**What it does:**
Validates that requested scripts are whitelisted, then executes them.

**Simple implementation:**
```bash
#!/bin/bash
set -euo pipefail

# Parse command safely (no shell evaluation)
read -ra ARGS <<< "$SSH_ORIGINAL_COMMAND"
SCRIPT_PATH="${ARGS[0]:-}"
SCRIPT_ARGS=("${ARGS[@]:1}")

# Whitelist of allowed scripts
ALLOWED_SCRIPTS=(
    "/home/you/scripts/deploy.sh"
    "/home/you/scripts/backup.sh"
    "/home/you/scripts/db-migrate.sh"
    "/home/you/bin/run-tests.sh"
)

# Validate script is whitelisted
if [[ ! " ${ALLOWED_SCRIPTS[*]} " =~ " ${SCRIPT_PATH} " ]]; then
    echo "ERROR: Script not whitelisted: $SCRIPT_PATH" >&2
    exit 1
fi

# Verify script exists and is executable
if [[ ! -x "$SCRIPT_PATH" ]]; then
    echo "ERROR: Script not found or not executable: $SCRIPT_PATH" >&2
    exit 1
fi

# Log and execute
echo "$(date -Iseconds) - Executing: $SCRIPT_PATH ${SCRIPT_ARGS[*]}" >> ~/.config/sshd-agent/operations.log
exec "$SCRIPT_PATH" "${SCRIPT_ARGS[@]}"
```

**Security note:**
This implementation assumes your whitelisted scripts don't do dangerous things with their arguments (no `eval`, no `bash -c` with user input, etc.). If you need more complex argument validation, consider using existing tools like `rrsh` (Restricted Rsync Shell), `rbash`, or study how `gitolite` restricts SSH commands.

### 4. Your Existing Scripts (Unchanged!)

**This is the whole point:** Your scripts don't change at all.

**Example script that works as-is:**
```bash
#!/bin/bash
# ~/scripts/deploy.sh

ENVIRONMENT="$1"

# Read credentials exactly as you do today
GITHUB_TOKEN=$(cat ~/.secrets/github_token)
AWS_ACCESS_KEY=$(cat ~/.secrets/aws_access_key)

# Do your work
echo "Deploying to $ENVIRONMENT..."
git push "https://${GITHUB_TOKEN}@github.com/myorg/myrepo.git" main
aws s3 sync ./build s3://my-bucket/

echo "Deployment complete!"
```

**When CC runs this:**
- CC calls: `ssh -p 2222 -i ~/.ssh/cc-identity localhost /home/you/scripts/deploy.sh prod`
- SSH daemon (outside namespace) receives connection
- Dispatcher validates script is whitelisted
- Script executes outside namespace with normal filesystem access
- Script reads credentials from `~/.secrets/` (which exists outside namespace)
- Script works exactly as it always has

### 5. CC Identity Key (CC's Only Credential)

**What it is:**
A single SSH private key that CC has access to.

**Security properties:**
- CC can read this key (needs it to authenticate to SSH daemon)
- Having the key doesn't give credential access
- The key can only trigger whitelisted operations
- If compromised, attacker can only run whitelisted scripts

**Why this is safe:**
Unlike a GitHub token or AWS key, this SSH key is inherently capability-limited. Its power is defined by:
1. What the forced command (dispatcher) allows
2. Which scripts are whitelisted
3. What those scripts do

Even if CC is manipulated into exfiltrating this key, an attacker gains nothing beyond what CC could already do.

## Execution Flow (Detailed Example)

Let's trace what happens when CC wants to deploy code:

**Step 1: CC's perspective (inside bubblewrap)**
```bash
# CC runs this command:
ssh -p 2222 -i ~/.ssh/cc-identity localhost /home/you/scripts/deploy.sh prod

# CC can see:
# - Most of home directory
# - The deploy.sh script
# - The SSH identity key

# CC CANNOT see:
# - ~/.secrets/ (appears empty due to tmpfs overlay)
# - ~/.config/sshd-agent/ (hidden from CC)
```

**Step 2: SSH connection crosses namespace boundary**
```
CC (inside namespace) → SSH client → localhost:2222 → SSH daemon (outside namespace)
```

**Step 3: SSH daemon authentication**
- Daemon receives connection from CC
- Validates `~/.ssh/cc-identity` public key against authorized_keys
- Finds matching entry with forced command

**Step 4: Forced command execution**
```bash
# CC requested: /home/you/scripts/deploy.sh prod
# SSH ignores this and executes the forced command:
/home/you/.config/sshd-agent/secured-ops/dispatcher

# The original request is available in environment variable:
SSH_ORIGINAL_COMMAND="/home/you/scripts/deploy.sh prod"
```

**Step 5: Dispatcher validation**
```bash
# Dispatcher parses SSH_ORIGINAL_COMMAND
SCRIPT_PATH="/home/you/scripts/deploy.sh"
SCRIPT_ARGS=("prod")

# Checks whitelist
# ✓ /home/you/scripts/deploy.sh is in ALLOWED_SCRIPTS

# Executes:
exec /home/you/scripts/deploy.sh prod
```

**Step 6: Script execution (outside namespace)**
```bash
# deploy.sh runs with normal filesystem access
# Reads: GITHUB_TOKEN=$(cat ~/.secrets/github_token)
# This works because script runs OUTSIDE bubblewrap
# ~/.secrets/ is real and contains actual credentials

# Performs deployment
git push https://${GITHUB_TOKEN}@github.com/...

# Returns: "Deployment complete!"
```

**Step 7: Response to CC**
- Output from deploy.sh is sent back through SSH
- CC receives: "Deployment complete!"
- CC never sees the GITHUB_TOKEN
- CC has no way to extract it

## Why This Is Secure

### Attack: CC tries to read credentials directly

```bash
# Inside bubblewrap, CC tries:
cat ~/.secrets/github_token

# Result: File is empty or doesn't exist (tmpfs overlay)
```

### Attack: CC tries to bypass via SSH

```bash
# CC tries:
ssh -p 2222 -i ~/.ssh/cc-identity localhost "cat ~/.secrets/github_token"

# What happens:
# 1. SSH daemon executes forced command (dispatcher)
# 2. Dispatcher receives SSH_ORIGINAL_COMMAND="cat ~/.secrets/github_token"
# 3. Dispatcher checks whitelist
# 4. "cat" is not in ALLOWED_SCRIPTS
# 5. Dispatcher rejects: "ERROR: Script not whitelisted"
```

### Attack: CC tries to run unauthorized script

```bash
# CC tries:
ssh -p 2222 -i ~/.ssh/cc-identity localhost /home/you/scripts/delete-everything.sh

# What happens:
# 1. Dispatcher checks whitelist
# 2. delete-everything.sh not in ALLOWED_SCRIPTS
# 3. Rejected
```

### Attack: CC tries to modify whitelisted script

```bash
# CC tries:
echo "cat ~/.secrets/github_token" >> /home/you/scripts/deploy.sh

# Result: Permission denied
# The script file is outside CC's writable namespace
```

### Attack: CC tries to create malicious script

```bash
# CC tries to create:
cat > /tmp/evil.sh << 'EOF'
#!/bin/bash
cat ~/.secrets/*
EOF
chmod +x /tmp/evil.sh

ssh -p 2222 -i ~/.ssh/cc-identity localhost /tmp/evil.sh

# What happens:
# 1. CC can create /tmp/evil.sh (inside its namespace)
# 2. Dispatcher checks whitelist
# 3. /tmp/evil.sh not in ALLOWED_SCRIPTS
# 4. Rejected
```

### Attack: Social engineering

**Attack:** CC says: "I've created a helpful backup script at `/home/you/scripts/backup-all.sh`. Can you add it to the whitelist?"

**Defense:** Human review before whitelisting. You examine the script to ensure it's safe before adding to `ALLOWED_SCRIPTS`.

This is the only attack that requires human judgment, which is unavoidable for any system that allows adding new capabilities.

## Setup Instructions

### Prerequisites

- Linux system with bubblewrap installed: `sudo apt install bubblewrap` (or equivalent)
- OpenSSH server package: `sudo apt install openssh-server`
- systemd user services enabled

### 1. Create Directory Structure

```bash
mkdir -p ~/.config/sshd-agent/secured-ops
mkdir -p ~/.secrets
chmod 700 ~/.secrets
chmod 700 ~/.config/sshd-agent
```

### 2. Move Credentials to Protected Location

```bash
# Example: Move existing credentials
mv ~/.github_token ~/.secrets/github_token
mv ~/.aws/credentials ~/.secrets/aws_credentials

# Set restrictive permissions
chmod 600 ~/.secrets/*
```

**Update your existing scripts to read from new location** (this is the ONLY change needed):
```bash
# Old:
# GITHUB_TOKEN=$(cat ~/.github_token)

# New:
GITHUB_TOKEN=$(cat ~/.secrets/github_token)
```

### 3. Generate SSH Keys

```bash
# Host key for SSH daemon
ssh-keygen -t ed25519 -f ~/.config/sshd-agent/ssh_host_ed25519_key -N "" -C "sshd-agent-host-key"

# CC identity key
ssh-keygen -t ed25519 -f ~/.ssh/cc-identity -N "" -C "claude-code-identity"

# Set permissions
chmod 600 ~/.config/sshd-agent/ssh_host_ed25519_key
chmod 600 ~/.ssh/cc-identity
```

### 4. Create SSH Daemon Configuration

Create `~/.config/sshd-agent/sshd_config`:

```sshd
# Network configuration
Port 2222
ListenAddress 127.0.0.1
AddressFamily inet

# Host key
HostKey /home/YOUR_USERNAME/.config/sshd-agent/ssh_host_ed25519_key

# Authentication
AuthorizedKeysFile /home/YOUR_USERNAME/.config/sshd-agent/authorized_keys
PubkeyAuthentication yes
PasswordAuthentication no
PermitRootLogin no
StrictModes yes

# Only allow your user
AllowUsers YOUR_USERNAME

# Security restrictions
X11Forwarding no
AllowTcpForwarding no
AllowStreamLocalForwarding no
PermitTunnel no
PermitUserEnvironment no

# Logging
SyslogFacility AUTH
LogLevel INFO

# Disable unnecessary features
PrintMotd no
PrintLastLog no
UsePAM no
```

**Replace `YOUR_USERNAME` with your actual username.**

### 5. Create Authorized Keys with Forced Command

Create `~/.config/sshd-agent/authorized_keys`:

```bash
command="/home/YOUR_USERNAME/.config/sshd-agent/secured-ops/dispatcher",no-pty,no-port-forwarding,no-X11-forwarding,no-agent-forwarding ssh-ed25519 AAAA...PASTE_CC_IDENTITY_PUBLIC_KEY_HERE... claude-code-identity
```

Get your CC identity public key:
```bash
cat ~/.ssh/cc-identity.pub
```

Paste the output (starts with `ssh-ed25519 AAAA...`) into the authorized_keys file.

Set permissions:
```bash
chmod 600 ~/.config/sshd-agent/authorized_keys
```

### 6. Create Dispatcher Script

Create `~/.config/sshd-agent/secured-ops/dispatcher`:

```bash
#!/bin/bash
set -euo pipefail

# Parse command safely (no shell evaluation)
read -ra ARGS <<< "$SSH_ORIGINAL_COMMAND"
SCRIPT_PATH="${ARGS[0]:-}"
SCRIPT_ARGS=("${ARGS[@]:1}")

# Whitelist of allowed scripts - CUSTOMIZE THIS LIST
ALLOWED_SCRIPTS=(
    "/home/YOUR_USERNAME/scripts/deploy.sh"
    "/home/YOUR_USERNAME/scripts/backup.sh"
    "/home/YOUR_USERNAME/scripts/db-migrate.sh"
)

# Validate script is whitelisted
if [[ ! " ${ALLOWED_SCRIPTS[*]} " =~ " ${SCRIPT_PATH} " ]]; then
    echo "ERROR: Script not whitelisted: $SCRIPT_PATH" >&2
    echo "Allowed scripts:" >&2
    printf '  %s\n' "${ALLOWED_SCRIPTS[@]}" >&2
    exit 1
fi

# Verify script exists and is executable
if [[ ! -x "$SCRIPT_PATH" ]]; then
    echo "ERROR: Script not found or not executable: $SCRIPT_PATH" >&2
    exit 1
fi

# Log the operation
echo "$(date -Iseconds) - Executing: $SCRIPT_PATH ${SCRIPT_ARGS[*]}" >> ~/.config/sshd-agent/operations.log

# Execute the script
exec "$SCRIPT_PATH" "${SCRIPT_ARGS[@]}"
```

Make it executable:
```bash
chmod 700 ~/.config/sshd-agent/secured-ops/dispatcher
```

**Important:** Update the `ALLOWED_SCRIPTS` array with paths to your actual scripts.

### 7. Set Up Systemd Socket Activation

Create `~/.config/systemd/user/sshd-agent.socket`:

```ini
[Unit]
Description=SSH Agent Socket for Secured Operations
Documentation=man:sshd(8)

[Socket]
ListenStream=127.0.0.1:2222
Accept=no

[Install]
WantedBy=sockets.target
```

Create `~/.config/systemd/user/sshd-agent.service`:

```ini
[Unit]
Description=SSH Agent for Secured Operations
Documentation=man:sshd(8)
Requires=sshd-agent.socket

[Service]
Type=notify
ExecStart=/usr/sbin/sshd -D -e -f %h/.config/sshd-agent/sshd_config
StandardInput=socket
StandardOutput=socket
StandardError=journal

# Security hardening
PrivateTmp=yes
ProtectSystem=strict
ProtectHome=read-only
ReadWritePaths=%h/.config/sshd-agent

[Install]
WantedBy=default.target
```

Enable and start:
```bash
systemctl --user daemon-reload
systemctl --user enable sshd-agent.socket
systemctl --user start sshd-agent.socket
```

Verify it's listening:
```bash
systemctl --user status sshd-agent.socket
ss -tlnp | grep 2222
```

### 8. Test Without Bubblewrap First

Before adding bubblewrap complexity, test that SSH bridge works:

```bash
# Test SSH connection
ssh -p 2222 -i ~/.ssh/cc-identity localhost /home/YOUR_USERNAME/scripts/deploy.sh test-arg

# Should execute your script with the argument
```

If this works, the SSH bridge is configured correctly.

### 9. Create Bubblewrap Launch Script

Create `~/bin/cc-isolated` (ensure `~/bin` is in your PATH):

```bash
#!/bin/bash
# Launch Claude Code in isolated namespace

# Directories/files to expose from $HOME (read-only unless specified)
# Only add paths that CC needs and that don't contain credentials
HOME_MOUNTS_RO=(
    ".bashrc"
    ".bash_profile"
    ".profile"
    ".gitconfig"
    ".config/git"
    ".ssh/cc-identity"
    ".ssh/cc-identity.pub"
    # Add more as needed when CC complains about missing files:
    # ".vimrc"
    # ".tmux.conf"
    # ".config/nvim"
)

# Directories to expose read-write (for caches, temp files, etc.)
HOME_MOUNTS_RW=(
    ".cache"
    ".local"
    # Add more as needed:
    # ".config/Code"  # If CC needs to persist editor settings
)

# NEVER expose these (examples of what to avoid):
# ".secrets"
# ".ssh" (except cc-identity specifically)
# ".aws"
# ".azure"
# ".gcloud"
# ".ansible"
# ".docker/config.json"
# ".netrc"
# ".pgpass"
# Anything with "credential", "password", "token", "secret" in the name

# Build mount arguments
MOUNT_ARGS=()
for path in "${HOME_MOUNTS_RO[@]}"; do
    full_path="$HOME/$path"
    if [ -e "$full_path" ]; then
        MOUNT_ARGS+=(--ro-bind "$full_path" "$full_path")
    fi
done

for path in "${HOME_MOUNTS_RW[@]}"; do
    full_path="$HOME/$path"
    if [ -e "$full_path" ]; then
        MOUNT_ARGS+=(--bind "$full_path" "$full_path")
    fi
done

exec bwrap \
  --ro-bind / / \
  --dev-bind /dev /dev \
  --bind /tmp /tmp \
  --tmpfs "$HOME" \
  --bind "/space/$USER" "/space/$USER" \
  "${MOUNT_ARGS[@]}" \
  --proc /proc \
  --unshare-all \
  --share-net \
  --die-with-parent \
  claude-code "$@"
```

Make it executable:
```bash
chmod +x ~/bin/cc-isolated
```

**Important notes:**
- The script creates an empty `$HOME` with `--tmpfs "$HOME"`, then selectively mounts only allowed paths
- Adjust `/space/$USER` to match your actual workspace location
- The `[ -e "$full_path" ]` check prevents errors if a path doesn't exist
- Add paths to the arrays as needed when CC complains about missing files
- **Never** add directories containing credentials to these arrays

**Iterative approach:**
Start with the minimal list above. When CC breaks with "file not found" errors, add that specific path to the appropriate array. This way you only expose what's actually needed.

### 10. Launch Claude Code in Isolated Mode

```bash
# Instead of running:
# claude-code

# Run:
cc-isolated
```

Now CC runs in a namespace where it cannot see credentials.

### 11. Test End-to-End

Inside CC's isolated environment, test that everything works:

```bash
# Launch CC in isolated mode
cc-isolated

# Inside CC, test that credentials are hidden:
ls ~/.secrets/  # Should be empty or not exist
cat ~/.secrets/github_token  # Should fail

# Test that secure_run works:
secure_run /home/you/scripts/deploy.sh test-arg
# Should execute the script successfully

# Test that whitelisting works:
secure_run /bin/cat ~/.secrets/github_token
# Should be rejected by dispatcher (not whitelisted)
```

If the script works via `secure_run` but credentials are hidden, you're good to go!

## Making It Easy: The secure_run Helper

To make calling scripts through the SSH bridge simpler, add a helper function to your regular `.bashrc`:

```bash
# Add to ~/.bashrc
# Secure execution helper for credential-requiring scripts
secure_run() {
    ssh -p 2222 -i ~/.ssh/cc-identity -o LogLevel=ERROR -o StrictHostKeyChecking=accept-new localhost "$@"
}

# Optional shorter alias
alias srun='secure_run'
```

Now both you and CC can use the same simple pattern:

```bash
# Instead of:
ssh -p 2222 -i ~/.ssh/cc-identity localhost /home/you/scripts/deploy.sh prod

# Just use:
secure_run /home/you/scripts/deploy.sh prod

# Or with alias:
srun /home/you/scripts/deploy.sh prod
```

**Benefits:**
- Same environment for CC and your normal shell
- Easy to test commands manually
- Only 11 extra characters per invocation
- Explicit about which operations cross the security boundary
- Easy to grep logs to see which operations touched credentials

**For Claude Code:**
Include in CC's instructions or system prompt:
> When executing scripts that require credentials (API tokens, passwords, etc.), use the `secure_run` helper:
> ```bash
> secure_run /path/to/script.sh arg1 arg2
> ```

## Monitoring and Maintenance

### View Operation Logs

```bash
tail -f ~/.config/sshd-agent/operations.log
```

### View SSH Daemon Logs

```bash
journalctl --user -u sshd-agent.service -f
```

### Check Socket Status

```bash
systemctl --user status sshd-agent.socket
```

### Adding New Scripts to Whitelist

1. Verify the script is safe (doesn't eval user input, etc.)
2. Add full path to `ALLOWED_SCRIPTS` array in dispatcher
3. Test: `ssh -p 2222 -i ~/.ssh/cc-identity localhost /path/to/new-script.sh`

### Rotating CC Identity Key

```bash
# Generate new key
ssh-keygen -t ed25519 -f ~/.ssh/cc-identity-new -N ""

# Update authorized_keys with new public key
# Test with new key
ssh -p 2222 -i ~/.ssh/cc-identity-new localhost /home/you/scripts/deploy.sh

# If working, replace old key
mv ~/.ssh/cc-identity-new ~/.ssh/cc-identity
mv ~/.ssh/cc-identity-new.pub ~/.ssh/cc-identity.pub
```

## Troubleshooting

### SSH Connection Refused

```bash
# Check socket is listening
systemctl --user status sshd-agent.socket

# Check logs
journalctl --user -u sshd-agent.service

# Try verbose SSH
ssh -vvv -p 2222 -i ~/.ssh/cc-identity localhost
```

### Permission Denied

```bash
# Check key permissions
ls -la ~/.ssh/cc-identity  # Should be 600

# Check authorized_keys
cat ~/.config/sshd-agent/authorized_keys

# Verify key fingerprints match
ssh-keygen -lf ~/.ssh/cc-identity.pub
```

### Script Not Whitelisted

```bash
# Check dispatcher logs
tail ~/.config/sshd-agent/operations.log

# Verify script path matches exactly what's in ALLOWED_SCRIPTS
# Paths must be absolute
```

### Bubblewrap Issues

```bash
# Test bubblewrap in isolation
bwrap --ro-bind / / --tmpfs /tmp bash -c "echo test"

# If that works, test with home directory hiding
bwrap --ro-bind / / --tmpfs "$HOME" bash -c "ls $HOME"
# Should be empty

# Check what CC can actually see
cc-isolated bash -c "ls -la ~"
cc-isolated bash -c "ls -la ~/.secrets"

# Test individual mounts
bwrap --ro-bind / / --tmpfs "$HOME" --ro-bind "$HOME/.bashrc" "$HOME/.bashrc" bash -c "cat ~/.bashrc"
```

### Missing Files in CC Environment

If CC complains about missing files:

1. Identify the exact path from the error message
2. Verify it's safe to expose (doesn't contain credentials)
3. Add to appropriate array in `cc-isolated` script:
   - `HOME_MOUNTS_RO` for read-only config files
   - `HOME_MOUNTS_RW` for caches/temp directories
4. Test again

Example:
```bash
# CC error: "cannot find ~/.npmrc"
# Add to HOME_MOUNTS_RO:
HOME_MOUNTS_RO=(
    ...
    ".npmrc"
)
```

### Too Many Mounts

If you hit argument length limits (unlikely with <100 mounts):

**Option 1:** Mount parent directories instead of individual files:
```bash
# Instead of:
".config/git"
".config/nvim"
".config/tmux"

# Use:
".config"  # But be careful - might expose too much
```

**Option 2:** Use symlinks to consolidate:
```bash
mkdir ~/safe-config
ln -s ~/.bashrc ~/safe-config/bashrc
ln -s ~/.gitconfig ~/safe-config/gitconfig
# Then mount just ~/safe-config
```

## Security Considerations

### What This Protects Against

1. **Credential exfiltration**: CC cannot read credential files
2. **Unauthorized operations**: Only whitelisted scripts can execute
3. **Credential misuse**: CC never sees credentials, even when scripts use them
4. **Prompt injection**: Even if manipulated, CC is constrained to whitelisted operations

### What This Does NOT Protect Against

1. **Whitelisted script abuse**: If a script is too broad in scope, CC could misuse it within allowed parameters
2. **SSH key exfiltration**: CC can read its own identity key (but the key is capability-limited)
3. **Social engineering**: CC could convince you to whitelist a malicious script
4. **Logic bugs**: Vulnerabilities in your whitelisted scripts

### Best Practices

1. **Whitelist conservatively**: Only add scripts you've reviewed and trust
2. **Keep scripts focused**: Each script should do one specific thing
3. **Validate inputs**: Scripts should validate arguments even if whitelisted
4. **Log everything**: The dispatcher logs all operations automatically
5. **Review logs periodically**: Watch for unusual patterns
6. **Audit scripts**: If a script changes, review it before next use
7. **Principle of least privilege**: Don't give scripts broader access than needed

### Advanced Security: Argument Validation

The simple dispatcher trusts that whitelisted scripts handle their arguments safely. If you need more sophisticated argument validation, consider:

- **rrsh** (Restricted Rsync Shell) - Built for restricting rsync but adaptable
- **rbash** (Restricted Bash) - Limits shell capabilities
- **gitolite** - Study how it restricts git operations via SSH
- **Custom validation** - Add pattern matching to dispatcher for specific scripts

For most use cases, trusting well-written scripts is sufficient. Add complexity only when needed.

## How This Compares to Alternatives

### vs. Manual Approval
- **This solution**: Autonomous operation with security boundaries
- **Manual approval**: Secure but defeats autonomous operation

### vs. Credential Helpers
- **This solution**: Scripts execute in different security context, credentials never exposed to CC
- **Credential helpers**: CC calls helper, can extract/misuse credentials from response

### vs. Service Accounts / OAuth
- **This solution**: Works with any credential type, local control
- **Service accounts**: Only works for services that support them, cloud-dependent

### vs. Setuid Binaries
- **This solution**: Unprivileged, standard tools, auditable
- **Setuid**: Requires root, high risk, hard to audit

### vs. VM/Container Service
- **This solution**: Lightweight, single machine, low overhead
- **VM/Container**: More isolation but much higher complexity and resource usage

## Performance Optimization

Since SSH connections are localhost-only and already protected by the namespace boundary, you can reduce overhead significantly:

### Optimize SSH Client Options

Update the `secure_run` helper in `.bashrc`:

```bash
secure_run() {
    ssh -p 2222 \
        -i ~/.ssh/cc-identity \
        -o LogLevel=ERROR \
        -o StrictHostKeyChecking=accept-new \
        -o Compression=no \
        -o Ciphers=none \
        -o HostKeyAlgorithms=ssh-ed25519 \
        localhost "$@"
}
```

**Optimizations explained:**
- `Ciphers=none` - Disables encryption (not needed for localhost)
- `Compression=no` - Disables compression overhead
- `HostKeyAlgorithms=ssh-ed25519` - Uses fastest key type only

### Optimize SSH Daemon Configuration

Update `~/.config/sshd-agent/sshd_config`:

```sshd
# Performance optimizations for localhost-only connections
Ciphers none
MACs none

# Reduce protocol overhead
TCPKeepAlive no
UseDNS no

# Existing config...
Port 2222
ListenAddress 127.0.0.1
# ... rest of config
```

**Note:** The `Ciphers none` and `MACs none` options require OpenSSH to be compiled with the "none" cipher support. If these cause errors, you can use the fastest available cipher instead:

```sshd
# Fallback if "none" isn't supported
Ciphers aes128-gcm@openssh.com
MACs umac-128@openssh.com
```

### Connection Multiplexing (Advanced)

For even better performance with many rapid calls, use SSH connection multiplexing:

```bash
# In ~/.bashrc or ~/.ssh/config for your user
mkdir -p ~/.ssh/control

# Update secure_run to use multiplexing:
secure_run() {
    ssh -p 2222 \
        -i ~/.ssh/cc-identity \
        -o LogLevel=ERROR \
        -o StrictHostKeyChecking=accept-new \
        -o ControlMaster=auto \
        -o ControlPath=~/.ssh/control/%r@%h:%p \
        -o ControlPersist=10m \
        localhost "$@"
}
```

This reuses a single connection for multiple commands, eliminating handshake overhead on subsequent calls.

### Performance Testing

Test the overhead:

```bash
# Without optimization
time ssh -p 2222 -i ~/.ssh/cc-identity localhost /bin/true

# With optimization
time secure_run /bin/true

# With multiplexing (second call)
time secure_run /bin/true  # Should be much faster
```

Expect 5-20ms per call without multiplexing, <1ms with multiplexing for subsequent calls.

## Related Projects

**[cco (Claude Code Orchestrator)](https://github.com/nikvdp/cco)** - A project exploring similar security concerns around Claude Code execution. May provide additional inspiration for isolation approaches and credential management patterns.

**[ClaudeCage](https://github.com/PACHAKUTlQ/ClaudeCage)** - Takes a different approach to isolating Claude Code using containerization and security boundaries. Worth exploring for alternative perspectives on the same problem.

**[mcp_command_server](https://github.com/copyleftdev/mcp_command_server)** - An MCP server implementation for executing shell commands. Provides a practical example of exposing command execution capabilities through the Model Context Protocol.

**[claude-code-sandbox](https://github.com/textcortex/claude-code-sandbox)** - Runs Claude Code in local Docker containers to avoid constant permission approvals. Features automatic credential discovery, sandboxed execution, and execution monitoring.

## Conclusion

This approach provides a practical balance for autonomous Claude Code operation:

✅ **Your scripts run unchanged** - Just update credential paths
✅ **Low ceremony** - Standard tools (SSH, bubblewrap, systemd)
✅ **No root required** - Everything runs as your user
✅ **Auditable** - All operations logged, whitelist is explicit
✅ **Flexible** - Easy to add new scripts to whitelist
✅ **Secure** - CC cannot access credentials even if compromised

The key insight is using namespace isolation (bubblewrap) to hide credentials from CC, while using SSH as a bridge for CC to execute whitelisted scripts outside the namespace where credentials are accessible. Your scripts work exactly as they always have - CC just can't see what they read.