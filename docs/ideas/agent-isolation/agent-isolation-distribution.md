# Claudey Bridge: Distribution & Installation Strategy

**Date:** 2025-11-29  
**Status:** Planning Phase

## Overview

This document outlines the distribution and installation strategy for **Claudey Bridge** - a unified product that provides secure agent isolation through sandboxing and a privileged MCP bridge server.

**Key Principle:** This is **one product** with multiple components that must work together seamlessly.

**Supported Platforms:** Linux and macOS only

---

## Product Components

```
Claudey Bridge (The Product)
├── Bridge Server (Java/Quarkus native binary)
├── Sandbox Launcher (Platform-specific scripts)
├── Configuration Templates
└── Tool Definition Examples
```

All components live in the `claudey` repository and are versioned together.

---

## Repository Structure

```
claudey/
├── pom.xml                           # Parent POM
├── bridge/                           # Maven module: Bridge Server
│   ├── pom.xml
│   └── src/
├── sandbox/                          # NOT a Maven module
│   ├── linux/
│   │   ├── claudey-sandbox           # Launcher script
│   │   └── install.sh
│   ├── macos/
│   │   ├── claudey-sandbox           # Launcher script
│   │   ├── claudey-agent.sb          # Seatbelt profile
│   │   └── install.sh
│   └── common/
│       └── config-templates/         # Default configs
├── database/                         # Existing module
├── journal/                          # Existing module
├── bin/                              # User-facing wrappers
│   ├── claudey-bridge                # Symlink to bridge binary
│   └── claude-code-sandbox           # Convenience wrapper
├── packaging/                        # NEW: Distribution packaging
│   ├── homebrew/
│   │   └── claudey-bridge.rb         # Homebrew formula
│   ├── debian/
│   │   ├── control
│   │   ├── postinst
│   │   └── prerm
│   ├── rpm/
│   │   └── claudey-bridge.spec
│   └── macos/
│       └── build-pkg.sh              # .pkg builder
├── examples/                         # NEW: Example configurations
│   └── tools/
│       ├── deploy_prod.md
│       ├── restart_db.md
│       └── backup.md
└── docs/
    └── installation.md               # User-facing install guide
```

---

## Distribution Formats

### Platform-Specific Strategy

**macOS:** Homebrew (primary)  
**Linux:** Native packages - .deb (Debian/Ubuntu), .rpm (RHEL/Fedora)  
**Fallback:** GitHub Releases with tarballs (all platforms)

---

### 1. macOS: Homebrew (Primary Distribution)

**Why Homebrew for macOS?**
- ✅ De facto standard on macOS
- ✅ Handles dependencies automatically
- ✅ Easy updates (`brew upgrade`)
- ✅ Can install as service (launchd)
- ✅ Users already familiar with it

**Formula Structure:**
```ruby
# packaging/homebrew/claudey-bridge.rb
class ClaudeyBridge < Formula
  desc "Secure MCP bridge for sandboxed AI agents"
  homepage "https://github.com/yeroc/claudey"
  url "https://github.com/yeroc/claudey/archive/refs/tags/v0.1.0.tar.gz"
  sha256 "..."
  license "Apache-2.0"
  
  depends_on "openjdk@21" => :build
  depends_on "maven" => :build
  depends_on :macos
  
  def install
    # Build native binary
    system "mvn", "clean", "package", "-Pnative", "-pl", "bridge", "-DskipTests"
    
    # Install bridge binary
    bin.install "bridge/target/mcp-bridge-server-#{version}-runner" => "claudey-bridge"
    
    # Install sandbox launcher
    bin.install "sandbox/macos/claudey-sandbox"
    
    # Install Seatbelt profile
    (etc/"claudey").install "sandbox/macos/claudey-agent.sb"
    
    # Install configuration templates
    (etc/"claudey").install "sandbox/common/config-templates/bridge.yaml"
    (etc/"claudey").install "sandbox/common/config-templates/sandbox.conf"
    
    # Install example tools
    (share/"claudey/examples/tools").install Dir["examples/tools/*.md"]
    
    # Install documentation
    doc.install "docs/installation.md"
  end
  
  def post_install
    # Create user config directory
    (var/"claudey/tools").mkpath
    
    # Copy examples if user config doesn't exist
    unless (var/"claudey/tools").children.any?
      cp_r share/"claudey/examples/tools/.", var/"claudey/tools"
    end
  end
  
  service do
    run [opt_bin/"claudey-bridge", "serve"]
    keep_alive true
    log_path var/"log/claudey-bridge.log"
    error_log_path var/"log/claudey-bridge-error.log"
  end
  
  test do
    system "#{bin}/claudey-bridge", "--version"
    system "#{bin}/claudey-sandbox", "--help"
  end
end
```

**Installation (macOS):**
```bash
# Add tap (if not in homebrew-core)
brew tap yeroc/claudey

# Install
brew install claudey-bridge

# Start as service
brew services start claudey-bridge

# Or run manually
claudey-bridge serve
```

---

### 2. Linux: Native Packages (Primary Distribution)

**Why Native Packages for Linux?**
- ✅ Expected by Linux users
- ✅ Integrates with system package manager
- ✅ Handles dependencies (bubblewrap)
- ✅ Systemd service integration
- ✅ Automatic updates via apt/dnf

---

#### 2a. Debian/Ubuntu (.deb)

**Package Structure:**
```
claudey-bridge_0.1.0_amd64.deb
├── DEBIAN/
│   ├── control              # Package metadata
│   ├── postinst             # Post-install script
│   └── prerm                # Pre-removal script
├── usr/
│   ├── bin/
│   │   ├── claudey-bridge
│   │   └── claudey-sandbox
│   └── share/
│       ├── doc/claudey-bridge/
│       │   └── installation.md
│       └── claudey/
│           └── examples/
│               └── tools/
└── etc/
    ├── claudey/
    │   ├── bridge.yaml
    │   └── sandbox.conf
    └── systemd/system/
        └── claudey-bridge.service
```

**Control File:**
```
Package: claudey-bridge
Version: 0.1.0
Section: utils
Priority: optional
Architecture: amd64
Depends: bubblewrap
Maintainer: Your Name <your.email@example.com>
Description: Secure MCP bridge for sandboxed AI agents
 Claudey Bridge provides a secure way to give AI agents access to
 privileged operations through a sandboxed environment and MCP bridge.
```

**Systemd Service:**
```ini
# /etc/systemd/system/claudey-bridge.service
[Unit]
Description=Claudey Bridge MCP Server
After=network.target

[Service]
Type=simple
User=claudey
Group=claudey
ExecStart=/usr/bin/claudey-bridge serve
Restart=on-failure
RestartSec=5s

[Install]
WantedBy=multi-user.target
```

**Post-install Script:**
```bash
#!/bin/bash
# DEBIAN/postinst

set -e

# Create system user
if ! id -u claudey > /dev/null 2>&1; then
    useradd --system --user-group --no-create-home claudey
fi

# Create user config directory
mkdir -p /var/lib/claudey/tools
chown -R claudey:claudey /var/lib/claudey

# Copy example tools if directory is empty
if [ ! "$(ls -A /var/lib/claudey/tools)" ]; then
    cp -r /usr/share/claudey/examples/tools/* /var/lib/claudey/tools/
    chown -R claudey:claudey /var/lib/claudey/tools
fi

# Reload systemd
systemctl daemon-reload

# Enable but don't start service
systemctl enable claudey-bridge.service

echo "Claudey Bridge installed successfully!"
echo "Start with: sudo systemctl start claudey-bridge"
```

**Build Script:**
```bash
#!/bin/bash
# packaging/debian/build-deb.sh

VERSION="0.1.0"
ARCH="amd64"
PKG_NAME="claudey-bridge_${VERSION}_${ARCH}"

# Create package structure
mkdir -p "$PKG_NAME/DEBIAN"
mkdir -p "$PKG_NAME/usr/bin"
mkdir -p "$PKG_NAME/usr/share/claudey/examples/tools"
mkdir -p "$PKG_NAME/etc/claudey"
mkdir -p "$PKG_NAME/etc/systemd/system"

# Copy files
cp bridge/target/mcp-bridge-server-*-runner "$PKG_NAME/usr/bin/claudey-bridge"
cp sandbox/linux/claudey-sandbox "$PKG_NAME/usr/bin/"
cp -r examples/tools/* "$PKG_NAME/usr/share/claudey/examples/tools/"
cp sandbox/common/config-templates/* "$PKG_NAME/etc/claudey/"
cp packaging/debian/claudey-bridge.service "$PKG_NAME/etc/systemd/system/"

# Copy control files
cp packaging/debian/control "$PKG_NAME/DEBIAN/"
cp packaging/debian/postinst "$PKG_NAME/DEBIAN/"
cp packaging/debian/prerm "$PKG_NAME/DEBIAN/"

# Set permissions
chmod 755 "$PKG_NAME/DEBIAN/postinst"
chmod 755 "$PKG_NAME/DEBIAN/prerm"
chmod 755 "$PKG_NAME/usr/bin/claudey-bridge"
chmod 755 "$PKG_NAME/usr/bin/claudey-sandbox"

# Build package
dpkg-deb --build "$PKG_NAME"

echo "Package built: ${PKG_NAME}.deb"
```

**Installation:**
```bash
# Install
sudo dpkg -i claudey-bridge_0.1.0_amd64.deb

# Install dependencies if missing
sudo apt-get install -f

# Start service
sudo systemctl start claudey-bridge
```

---

#### 2b. RHEL/Fedora (.rpm)

**Spec File:**
```spec
# packaging/rpm/claudey-bridge.spec
Name:           claudey-bridge
Version:        0.1.0
Release:        1%{?dist}
Summary:        Secure MCP bridge for sandboxed AI agents

License:        Apache-2.0
URL:            https://github.com/yeroc/claudey
Source0:        %{name}-%{version}.tar.gz

Requires:       bubblewrap

%description
Claudey Bridge provides a secure way to give AI agents access to
privileged operations through a sandboxed environment and MCP bridge.

%prep
%setup -q

%build
mvn clean package -Pnative -pl bridge -DskipTests

%install
mkdir -p %{buildroot}%{_bindir}
mkdir -p %{buildroot}%{_sysconfdir}/claudey
mkdir -p %{buildroot}%{_datadir}/claudey/examples/tools
mkdir -p %{buildroot}%{_unitdir}

install -m 755 bridge/target/mcp-bridge-server-*-runner %{buildroot}%{_bindir}/claudey-bridge
install -m 755 sandbox/linux/claudey-sandbox %{buildroot}%{_bindir}/
install -m 644 sandbox/common/config-templates/* %{buildroot}%{_sysconfdir}/claudey/
install -m 644 examples/tools/* %{buildroot}%{_datadir}/claudey/examples/tools/
install -m 644 packaging/rpm/claudey-bridge.service %{buildroot}%{_unitdir}/

%files
%{_bindir}/claudey-bridge
%{_bindir}/claudey-sandbox
%config(noreplace) %{_sysconfdir}/claudey/*
%{_datadir}/claudey/examples/tools/*
%{_unitdir}/claudey-bridge.service

%post
systemctl daemon-reload
systemctl enable claudey-bridge.service

%preun
if [ $1 -eq 0 ]; then
    systemctl stop claudey-bridge.service
    systemctl disable claudey-bridge.service
fi

%postun
systemctl daemon-reload
```

---

### 3. GitHub Releases (Universal Fallback)

**For users who prefer manual installation or need specific versions**

**Release Assets:**
```
claudey-bridge-v0.1.0-linux-amd64.tar.gz
claudey-bridge-v0.1.0-linux-arm64.tar.gz
claudey-bridge-v0.1.0-macos-amd64.tar.gz
claudey-bridge-v0.1.0-macos-arm64.tar.gz
claudey-bridge_0.1.0_amd64.deb
claudey-bridge-0.1.0-1.x86_64.rpm
```

**Tarball Contents:**
```
claudey-bridge-v0.1.0-linux-amd64/
├── bin/
│   ├── claudey-bridge
│   └── claudey-sandbox
├── etc/
│   ├── bridge.yaml
│   └── sandbox.conf
├── examples/
│   └── tools/
│       ├── deploy_prod.md
│       └── restart_db.md
├── install.sh
└── README.md
```

**Install Script:**
```bash
#!/bin/bash
# install.sh (included in tarball)

set -e

PREFIX="${PREFIX:-/usr/local}"

echo "Installing Claudey Bridge to $PREFIX"

# Copy binaries
install -m 755 bin/claudey-bridge "$PREFIX/bin/"
install -m 755 bin/claudey-sandbox "$PREFIX/bin/"

# Create config directory
mkdir -p "$HOME/.config/claudey"

# Copy config templates if they don't exist
if [ ! -f "$HOME/.config/claudey/bridge.yaml" ]; then
    cp etc/bridge.yaml "$HOME/.config/claudey/"
fi

if [ ! -f "$HOME/.config/claudey/sandbox.conf" ]; then
    cp etc/sandbox.conf "$HOME/.config/claudey/"
fi

# Copy example tools
mkdir -p "$HOME/.config/claudey/tools"
cp -n examples/tools/* "$HOME/.config/claudey/tools/" 2>/dev/null || true

echo "Installation complete!"
echo ""
echo "Next steps:"
echo "  1. Review configuration: $HOME/.config/claudey/bridge.yaml"
echo "  2. Start bridge: claudey-bridge serve"
echo "  3. Launch sandboxed agent: claudey-sandbox --workspace /path/to/workspace -- your-agent"
```

---

## Build & Release Automation

### GitHub Actions Workflow

```yaml
# .github/workflows/release.yml
name: Release

on:
  push:
    tags:
      - 'v*'

jobs:
  build-native:
    strategy:
      matrix:
        include:
          - os: ubuntu-latest
            platform: linux
            arch: amd64
          - os: macos-latest
            platform: macos
            arch: amd64
          - os: macos-latest-xlarge  # Apple Silicon
            platform: macos
            arch: arm64
    
    runs-on: ${{ matrix.os }}
    
    steps:
      - uses: actions/checkout@v3
      
      - uses: graalvm/setup-graalvm@v1
        with:
          java-version: '21'
          distribution: 'graalvm-community'
          github-token: ${{ secrets.GITHUB_TOKEN }}
      
      - name: Build native binary
        run: mvn clean package -Pnative -pl bridge -DskipTests
      
      - name: Create tarball
        run: |
          VERSION=${GITHUB_REF#refs/tags/v}
          TARBALL="claudey-bridge-v${VERSION}-${{ matrix.platform }}-${{ matrix.arch }}.tar.gz"
          
          mkdir -p dist/bin dist/etc dist/examples/tools
          
          cp bridge/target/mcp-bridge-server-*-runner dist/bin/claudey-bridge
          cp sandbox/${{ matrix.platform }}/claudey-sandbox dist/bin/
          cp sandbox/common/config-templates/* dist/etc/
          cp examples/tools/* dist/examples/tools/
          cp packaging/install.sh dist/
          
          tar czf "$TARBALL" -C dist .
          
          echo "TARBALL=$TARBALL" >> $GITHUB_ENV
      
      - name: Upload artifact
        uses: actions/upload-artifact@v3
        with:
          name: ${{ env.TARBALL }}
          path: ${{ env.TARBALL }}
  
  build-packages:
    needs: build-native
    runs-on: ubuntu-latest
    
    steps:
      - uses: actions/checkout@v3
      
      - name: Download Linux binary
        uses: actions/download-artifact@v3
        with:
          name: claudey-bridge-v*-linux-amd64.tar.gz
      
      - name: Build .deb package
        run: |
          tar xzf claudey-bridge-v*-linux-amd64.tar.gz
          bash packaging/debian/build-deb.sh
      
      - name: Build .rpm package
        run: |
          bash packaging/rpm/build-rpm.sh
      
      - name: Upload packages
        uses: actions/upload-artifact@v3
        with:
          name: packages
          path: |
            *.deb
            *.rpm
  
  create-release:
    needs: [build-native, build-packages]
    runs-on: ubuntu-latest
    
    steps:
      - uses: actions/checkout@v3
      
      - name: Download all artifacts
        uses: actions/download-artifact@v3
      
      - name: Create GitHub Release
        uses: softprops/action-gh-release@v1
        with:
          files: |
            **/*.tar.gz
            **/*.deb
            **/*.rpm
            **/*.pkg
          body: |
            ## Installation
            
            ### Homebrew (Recommended)
            ```bash
            brew tap yeroc/claudey
            brew install claudey-bridge
            ```
            
            ### Debian/Ubuntu
            ```bash
            wget https://github.com/yeroc/claudey/releases/download/${{ github.ref_name }}/claudey-bridge_*_amd64.deb
            sudo dpkg -i claudey-bridge_*_amd64.deb
            ```
            
            ### RHEL/Fedora
            ```bash
            wget https://github.com/yeroc/claudey/releases/download/${{ github.ref_name }}/claudey-bridge-*.x86_64.rpm
            sudo rpm -i claudey-bridge-*.x86_64.rpm
            ```
            
            ### Manual Installation
            ```bash
            wget https://github.com/yeroc/claudey/releases/download/${{ github.ref_name }}/claudey-bridge-${{ github.ref_name }}-linux-amd64.tar.gz
            tar xzf claudey-bridge-*.tar.gz
            cd claudey-bridge-*
            sudo ./install.sh
            ```
```

---

## User Installation Guide

### macOS (Recommended: Homebrew)

```bash
# Install
brew tap yeroc/claudey
brew install claudey-bridge

# Start as service
brew services start claudey-bridge

# Verify
curl http://localhost:3000/mcp
```

### Linux - Debian/Ubuntu (Recommended: .deb package)

```bash
# Download latest release
wget https://github.com/yeroc/claudey/releases/latest/download/claudey-bridge_0.1.0_amd64.deb

# Install
sudo dpkg -i claudey-bridge_0.1.0_amd64.deb

# Install dependencies if missing
sudo apt-get install -f

# Start service
sudo systemctl start claudey-bridge

# Enable on boot
sudo systemctl enable claudey-bridge

# Verify
curl http://localhost:3000/mcp
```

### Linux - RHEL/Fedora (Recommended: .rpm package)

```bash
# Download latest release
wget https://github.com/yeroc/claudey/releases/latest/download/claudey-bridge-0.1.0-1.x86_64.rpm

# Install
sudo rpm -i claudey-bridge-0.1.0-1.x86_64.rpm

# Start service
sudo systemctl start claudey-bridge

# Enable on boot
sudo systemctl enable claudey-bridge

# Verify
curl http://localhost:3000/mcp
```

### Manual Installation (All Platforms)

```bash
# Download latest release
wget https://github.com/yeroc/claudey/releases/latest/download/claudey-bridge-v0.1.0-linux-amd64.tar.gz

# Extract
tar xzf claudey-bridge-v0.1.0-linux-amd64.tar.gz
cd claudey-bridge-v0.1.0-linux-amd64

# Install
sudo ./install.sh

# Configure
vim ~/.config/claudey/bridge.yaml

# Start
claudey-bridge serve
```

### Configuration

```bash
# Edit bridge configuration
vim ~/.config/claudey/bridge.yaml

# Edit sandbox configuration
vim ~/.config/claudey/sandbox.conf

# Add custom tools
vim ~/.config/claudey/tools/my_tool.md
```

---

## Version Management

### Semantic Versioning

- **Major (1.0.0):** Breaking changes to configuration or API
- **Minor (0.1.0):** New features, backward compatible
- **Patch (0.0.1):** Bug fixes

### Release Cadence

- **Alpha:** Weekly (v0.1.0-alpha.1, v0.1.0-alpha.2, ...)
- **Beta:** Bi-weekly (v0.1.0-beta.1, v0.1.0-beta.2, ...)
- **Stable:** Monthly (v0.1.0, v0.2.0, ...)

### Upgrade Path

**macOS (Homebrew):**
```bash
brew upgrade claudey-bridge
brew services restart claudey-bridge
```

**Linux (Debian/Ubuntu):**
```bash
# Download new version
wget https://github.com/yeroc/claudey/releases/latest/download/claudey-bridge_0.2.0_amd64.deb

# Upgrade
sudo dpkg -i claudey-bridge_0.2.0_amd64.deb

# Restart service
sudo systemctl restart claudey-bridge
```

**Linux (RHEL/Fedora):**
```bash
# Download new version
wget https://github.com/yeroc/claudey/releases/latest/download/claudey-bridge-0.2.0-1.x86_64.rpm

# Upgrade
sudo rpm -U claudey-bridge-0.2.0-1.x86_64.rpm

# Restart service
sudo systemctl restart claudey-bridge
```

**Manual Installation:**
wget https://github.com/yeroc/claudey/releases/latest/download/claudey-bridge-latest-linux-amd64.tar.gz
tar xzf claudey-bridge-latest-linux-amd64.tar.gz
cd claudey-bridge-*
sudo ./install.sh
```

---

## Distribution Checklist

### Pre-Release
- [ ] All tests passing
- [ ] Documentation updated
- [ ] CHANGELOG.md updated
- [ ] Version bumped in pom.xml
- [ ] Git tag created

### Build
- [ ] Native binaries built for all platforms
- [ ] Tarballs created
- [ ] .deb package built and tested
- [ ] .rpm package built and tested
- [ ] .pkg package built and tested (macOS)

### Distribution
- [ ] GitHub Release created
- [ ] Homebrew formula updated
- [ ] Release notes published
- [ ] Documentation site updated

### Post-Release
- [ ] Announce on social media
- [ ] Update installation guide
- [ ] Monitor for issues

---

## Future Enhancements

### Package Managers
- [ ] Add to Homebrew core (after stability)
- [ ] Add to Debian/Ubuntu official repos
- [ ] Add to Fedora/RHEL repos
- [ ] Snap package (Linux)
- [ ] Flatpak (Linux)

### Distribution Channels
- [ ] Docker images
- [ ] Kubernetes Helm chart
- [ ] Nix package

### Automation
- [ ] Automated testing of packages
- [ ] Automated Homebrew PR creation
- [ ] Automated changelog generation

---

## Summary

**Distribution Strategy:**

- **macOS:** Homebrew (primary), GitHub Releases (fallback)
- **Linux:** Native packages - .deb/.rpm (primary), GitHub Releases (fallback)
- **All Platforms:** GitHub Releases with tarballs (manual installation)

**All components live in one repository** and are versioned together as a unified product.

**Supported Platforms:** Linux and macOS only (no Windows)
