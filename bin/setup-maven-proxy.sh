#!/bin/bash
#
# setup-maven-proxy.sh
#
# Creates ~/.m2/settings.xml with Claude Code proxy credentials from HTTPS_PROXY
# Run this script once per session when proxy credentials are refreshed
#

set -e

if [ -z "$HTTPS_PROXY" ]; then
  echo "ERROR: HTTPS_PROXY environment variable is not set" >&2
  exit 1
fi

echo "Parsing proxy credentials from HTTPS_PROXY..."

PROXY_USER=$(echo "$HTTPS_PROXY" | sed 's|http://\([^:]*\):.*|\1|')
PROXY_PASS=$(echo "$HTTPS_PROXY" | sed 's|http://[^:]*:\([^@]*\)@.*|\1|')
PROXY_HOST=$(echo "$HTTPS_PROXY" | sed 's|.*@\([^:]*\):.*|\1|')
PROXY_PORT=$(echo "$HTTPS_PROXY" | sed 's|.*:\([0-9]*\)$|\1|')

if [ -z "$PROXY_HOST" ] || [ -z "$PROXY_PORT" ] || [ -z "$PROXY_USER" ] || [ -z "$PROXY_PASS" ]; then
  echo "ERROR: Failed to parse proxy credentials from HTTPS_PROXY" >&2
  echo "Expected format: http://username:password@host:port" >&2
  exit 1
fi

echo "Creating ~/.m2/settings.xml..."
mkdir -p ~/.m2

cat > ~/.m2/settings.xml << EOF
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
      <host>${PROXY_HOST}</host>
      <port>${PROXY_PORT}</port>
      <username>${PROXY_USER}</username>
      <password>${PROXY_PASS}</password>
      <nonProxyHosts>localhost|127.0.0.1|169.254.169.254|metadata.google.internal|*.svc.cluster.local|*.local|*.googleapis.com|*.google.com</nonProxyHosts>
    </proxy>
  </proxies>
</settings>
EOF

echo "✓ Created ~/.m2/settings.xml with proxy: ${PROXY_HOST}:${PROXY_PORT}"
echo ""
echo "Next steps:"
echo "  1. Source maven-env.sh to set MAVEN_OPTS:"
echo "     source bin/maven-env.sh"
echo "  2. Run Maven commands:"
echo "     mvn clean compile"
