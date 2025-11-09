#!/bin/bash
#
# maven-env.sh
#
# Sets MAVEN_OPTS with proxy configuration for Claude Code web environment
# MUST be sourced, not executed: source ./maven-env.sh
#

if [ -z "$HTTPS_PROXY" ]; then
  echo "ERROR: HTTPS_PROXY environment variable is not set" >&2
  return 1 2>/dev/null || exit 1
fi

# Parse proxy from environment
PROXY_HOST=$(echo "$HTTPS_PROXY" | sed 's|.*@\([^:]*\):.*|\1|')
PROXY_PORT=$(echo "$HTTPS_PROXY" | sed 's|.*:\([0-9]*\)$|\1|')

if [ -z "$PROXY_HOST" ] || [ -z "$PROXY_PORT" ]; then
  echo "ERROR: Failed to parse proxy from HTTPS_PROXY" >&2
  echo "Expected format: http://username:password@host:port" >&2
  return 1 2>/dev/null || exit 1
fi

# Set MAVEN_OPTS for forked processes
export MAVEN_OPTS="-Djdk.http.auth.tunneling.disabledSchemes= -Dmaven.resolver.transport=wagon -Dhttp.proxyHost=${PROXY_HOST} -Dhttp.proxyPort=${PROXY_PORT} -Dhttps.proxyHost=${PROXY_HOST} -Dhttps.proxyPort=${PROXY_PORT}"

echo "✓ MAVEN_OPTS configured for proxy: ${PROXY_HOST}:${PROXY_PORT}"
echo ""
echo "Maven is now configured. Current settings:"
echo "  Proxy: ${PROXY_HOST}:${PROXY_PORT}"
echo "  Transport: wagon (preemptive auth)"
echo "  Settings: ~/.m2/settings.xml"
echo ""
echo "Run Maven commands normally:"
echo "  mvn clean compile"
echo "  mvn test"
