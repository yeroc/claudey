## Installation

### Native Binary

**Linux x64:**
```bash
wget https://github.com/REPO/releases/download/VERSION/mcp-database-server-VERSION-linux-x64
chmod +x mcp-database-server-VERSION-linux-x64
./mcp-database-server-VERSION-linux-x64 --cli
```

**macOS (Apple Silicon):**
```bash
wget https://github.com/REPO/releases/download/VERSION/mcp-database-server-VERSION-macos-arm64
chmod +x mcp-database-server-VERSION-macos-arm64
./mcp-database-server-VERSION-macos-arm64 --cli
```

### JVM Mode (Requires Java 21+)
```bash
wget https://github.com/REPO/releases/download/VERSION/mcp-database-server-VERSION.jar
java -jar mcp-database-server-VERSION.jar --cli
```

## Checksum Verification

Download the corresponding `.sha256` file and verify:
```bash
sha256sum -c mcp-database-server-VERSION-linux-x64.sha256
```
