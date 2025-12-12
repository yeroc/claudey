## Installation

### Native Binary

**Linux (x64):**

```bash
wget https://github.com/{{REPO}}/releases/download/{{TAG}}/{{ARTIFACT_NAME}}-{{VERSION}}-linux-x64
chmod +x {{ARTIFACT_NAME}}-{{VERSION}}-linux-x64
./{{ARTIFACT_NAME}}-{{VERSION}}-linux-x64 --cli
```

**macOS (Apple Silicon):**

```bash
wget https://github.com/{{REPO}}/releases/download/{{TAG}}/{{ARTIFACT_NAME}}-{{VERSION}}-macos-arm64
chmod +x {{ARTIFACT_NAME}}-{{VERSION}}-macos-arm64
# Remove quarantine attribute (macOS marks downloaded executables as untrusted)
xattr -d com.apple.quarantine {{ARTIFACT_NAME}}-{{VERSION}}-macos-arm64
./{{ARTIFACT_NAME}}-{{VERSION}}-macos-arm64 --cli
```

### Bytecode (Java 21+)

```bash
wget https://github.com/{{REPO}}/releases/download/{{TAG}}/{{ARTIFACT_NAME}}-{{VERSION}}.jar
java -jar {{ARTIFACT_NAME}}-{{VERSION}}.jar --cli
```

## Checksum Verification

Download the corresponding `.sha256` file and verify:
```bash
sha256sum -c {{ARTIFACT_NAME}}-{{VERSION}}-linux-x64.sha256
```
