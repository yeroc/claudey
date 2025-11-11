# Maven Dependency Download Issue

**Date**: 2025-11-08T05:48:00Z
**Environment**: Claude Code sandbox/container
**Project**: org.geekden:test-app (Quarkus 3.26.1 with LangChain4j)

## Summary

Maven builds fail due to inability to resolve and download dependencies from Maven Central repository (`repo.maven.apache.org`). The root cause is a Java DNS resolution failure, while non-Java tools (curl, wget) can successfully access the same hosts.

## Problem Statement

When running `mvn clean package`, the build fails with:

```
[ERROR] Unknown host repo.maven.apache.org
```

This prevents Maven from downloading:
- `io.quarkus.platform:quarkus-maven-plugin:3.26.1`
- `io.quarkus.platform:quarkus-bom:3.26.1`
- `io.quarkus.platform:quarkus-langchain4j-bom:3.26.1`
- All transitive dependencies

## Environment Details

### System Configuration
- **Java Version**: OpenJDK 21.0.8 (Ubuntu build)
- **Maven Version**: Apache Maven 3.9.11
- **OS**: Linux 4.4.0 (container environment)
- **Platform**: linux/amd64

### Java Environment Variables
```
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
_JAVA_OPTIONS=-Djava.net.preferIPv4Stack=true
```

Note: `_JAVA_OPTIONS` is globally set to prefer IPv4 stack.

### DNS Configuration
- `/etc/resolv.conf`: **Empty file (0 bytes)** - This is unusual and may be the root cause
- `/etc/hosts`: Contains managed container entries but no Maven Central entry

## Diagnostic Tests

### Test Script Location
See `scripts/TestDNS.java` for the diagnostic utility used in testing.

### Test Results

#### 1. Java DNS Resolution (FAILED)

```bash
$ java scripts/TestDNS.java
```

**Result**:
```
DNS Resolution Failed: repo.maven.apache.org: Temporary failure in name resolution
java.net.UnknownHostException: repo.maven.apache.org: Temporary failure in name resolution
	at java.base/java.net.Inet4AddressImpl.lookupAllHostAddr(Native Method)
```

**Notes**:
- Uses `Inet4AddressImpl` (IPv4) due to `_JAVA_OPTIONS` setting
- Native method call fails at OS level
- Stack trace shows failure at `InetAddress.getByName()`

#### 2. Java DNS with Explicit IPv4 Flag (FAILED)

```bash
$ java -Djava.net.preferIPv4Stack=true scripts/TestDNS.java
```

**Result**: Same failure as above (flag is redundant due to `_JAVA_OPTIONS`)

#### 3. curl/HTTP Connectivity (SUCCESS)

```bash
$ curl -I https://repo.maven.apache.org/maven2/
```

**Result**:
```
HTTP/1.1 200 OK
date: Sat, 08 Nov 2025 05:48:05 GMT
```

**Conclusion**: Non-Java network tools can successfully resolve and connect to Maven Central.

#### 4. Maven Build with -U Flag (FAILED)

```bash
$ mvn clean package -U
```

**Result**: Same "Unknown host" error. The `-U` (force update) flag had no effect, confirming this is not a cached failure issue.

### Network Connectivity Comparison

| Tool | DNS Resolution | HTTPS Connection | Status |
|------|---------------|------------------|---------|
| Java (InetAddress) | ❌ FAILED | N/A | Cannot resolve hostname |
| Maven | ❌ FAILED | N/A | Cannot resolve hostname |
| curl | ✅ SUCCESS | ✅ SUCCESS | HTTP 200 OK |
| wget | Not tested | Not tested | N/A |

## Root Cause Analysis

### Primary Issue: Empty /etc/resolv.conf

The `/etc/resolv.conf` file is empty (0 bytes), which means:
- No DNS nameservers are configured
- Java's native DNS resolution has nowhere to send queries
- This explains why Java DNS lookups fail

### Why curl Works But Java Doesn't

This is the critical mystery:
- curl successfully resolves `repo.maven.apache.org` and gets HTTP 200
- Java's `InetAddress.getByName()` fails with "Temporary failure in name resolution"
- Both should use the same system DNS configuration

**Possible Explanations**:
1. curl may be using a different DNS resolution mechanism (cached, alternative resolver)
2. Container networking may route different processes differently
3. Java may be sandboxed or restricted in a way that curl is not
4. There may be container-specific DNS magic that curl benefits from but Java doesn't

## Workarounds Attempted

### 1. /etc/hosts Hack (PARTIALLY SUCCESSFUL - REVERTED)

**Action**: Added `21.0.0.125 repo.maven.apache.org` to `/etc/hosts`

**Results**:
- ✅ Java DNS resolution succeeded: resolved to `21.0.0.125`
- ❌ Java HTTPS connection failed: `java.net.SocketTimeoutException: Connect timed out`
- Maven still couldn't download dependencies

**Conclusion**: This revealed a second issue - even with DNS working, Java HTTPS connections timeout on port 443, while curl HTTPS works fine. This suggests network filtering or sandboxing specific to Java applications.

**Status**: Reverted (cleaned from `/etc/hosts`)

### 2. IPv4 Stack Preference (NO EFFECT)

**Action**: Used `-Djava.net.preferIPv4Stack=true`

**Result**: Stack trace changed from `Inet6AddressImpl` to `Inet4AddressImpl` but DNS still failed. The flag worked (IPv4 was used) but didn't solve the problem.

**Status**: Already set globally via `_JAVA_OPTIONS`

### 3. Force Update with -U (NO EFFECT)

**Action**: `mvn clean package -U` to clear cached failures

**Result**: No change in behavior

## Maven Local Repository State

```bash
$ ls -la ~/.m2/repository/io/quarkus/platform/quarkus-bom/3.26.1/
```

Contents:
- `quarkus-bom-3.26.1.pom.lastUpdated` (failure marker)
- No actual POM files downloaded

This confirms previous failed download attempts but no successful retrievals.

## Hypotheses

### Working Theory
This appears to be a **container networking configuration issue** where:

1. The container has empty `/etc/resolv.conf` (no DNS configuration)
2. Some processes (like curl) have access to container DNS magic/fallback
3. Java processes are restricted/sandboxed and cannot access that fallback
4. Even if DNS is forced via `/etc/hosts`, Java HTTPS is blocked/filtered

### Alternative Theories

1. **SELinux/AppArmor**: Java may be restricted by security policies
2. **Network Namespace**: Java may be in a different network namespace
3. **DNS Proxy**: curl may be using a DNS proxy that Java cannot access
4. **Container Runtime**: Docker/Podman DNS handling may discriminate by process type

## Next Steps for Investigation

### DNS Configuration
1. Investigate how curl resolves DNS when `/etc/resolv.conf` is empty
2. Check if there's a container DNS service: `systemctl status systemd-resolved`
3. Look for alternative resolver configuration: `/etc/nsswitch.conf`
4. Check for DNS-related environment variables: `env | grep -i dns`

### Network Filtering
1. If DNS is fixed, investigate why Java HTTPS connections timeout
2. Check iptables rules: `iptables -L -n`
3. Look for process-specific network policies
4. Test Java HTTPS with simpler domains (google.com, etc)

### Alternative Approaches
1. Configure Maven to use HTTP instead of HTTPS (security implications)
2. Pre-populate Maven local repository with all required dependencies
3. Set up a local Maven proxy/mirror within the network
4. Use a different JDK that might have different networking behavior

### Java Debugging
1. Enable Java network debugging: `-Djava.net.debug=all`
2. Check Java security policies: `$JAVA_HOME/conf/security/java.security`
3. Test with Java 11 or Java 17 instead of Java 21
4. Investigate if there are Java socket permission issues

## Files and References

- Test utility: `scripts/TestDNS.java`
- Project POM: `pom.xml` (Quarkus 3.26.1)
- Claude guide: `CLAUDE.md`

## SOLUTION FOUND (2025-11-08T06:57:00Z)

### Root Cause Identified

The issue was **NOT** DNS resolution as initially suspected. The real problems were:

1. **HTTP Proxy Non-Compliance**: The container proxy at `21.0.0.77:15004` returns `401 Unauthorized` instead of the RFC-compliant `407 Proxy Authentication Required`
2. **Basic Auth Disabled by Default**: Java disables Basic authentication for CONNECT tunneling by default (`jdk.http.auth.tunneling.disabledSchemes=Basic` in `net.properties`)
3. **HTTP Proxy Authentication Required**: Proxy credentials must be provided (configured via `HTTPS_PROXY` environment variable)

**Note**: JDK-8306745 (Authenticator bug) only affected diagnostic test script development, not the core Maven issue. The Authenticator was never invoked because the proxy returned 401 instead of 407.

### Why DNS Tests Failed

Java's `InetAddress.getByName()` performs direct DNS resolution and doesn't use HTTP proxies. Since the container has no direct DNS access (empty `/etc/resolv.conf`), DNS tests always failed. However, curl worked because it routes through the HTTP proxy, which handles DNS resolution on the proxy side.

### The Working Solution for Custom Java HTTP Clients

**For testing/diagnostic purposes only** (Maven solution is different - see "Applying to Maven" section below):

1. **Enable Basic auth for CONNECT tunneling**:
   ```bash
   export JAVA_OPTS="-Djdk.http.auth.tunneling.disabledSchemes="
   ```

2. **Use preemptive authentication by manually setting the `Proxy-Authorization` header**:
   ```java
   System.setProperty("jdk.http.auth.tunneling.disabledSchemes", "");

   URI proxyUri = new URI(System.getenv("HTTPS_PROXY"));
   String userInfo = proxyUri.getUserInfo(); // "username:jwt_token"
   String encodedAuth = Base64.getEncoder().encodeToString(userInfo.getBytes());

   HttpClient client = HttpClient.newBuilder()
     .proxy(ProxySelector.of(new InetSocketAddress(proxyUri.getHost(), proxyUri.getPort())))
     // Note: Do NOT use .authenticator() - won't be called due to 401 vs 407 issue
     .build();

   HttpRequest request = HttpRequest.newBuilder()
     .header("Proxy-Authorization", "Basic " + encodedAuth)
     .uri(targetUri)
     .build();
   ```

**Why this works**: By sending credentials preemptively (on the first request), we bypass the broken challenge-response flow entirely. The proxy receives valid credentials immediately and never sends the non-compliant 401 response.

### Verification

The test script `scripts/TestHttps.java` now successfully connects through the proxy and returns HTTP 200:

```bash
$ java scripts/TestHttps.java
Proxy: 21.0.0.77:15004
Response code: 200
Success!
```

### Proxy Non-Compliance with RFC 7235

**Critical Discovery**: The container HTTP proxy at `21.0.0.77:15004` (response headers show `server: envoy`) is **not HTTP-compliant** for CONNECT tunnel authentication.

#### RFC 7235 Specification

Per RFC 7235 (HTTP/1.1 Authentication):

- **401 Unauthorized** (Section 3.1): "The origin server MUST send a WWW-Authenticate header field containing at least one challenge applicable to the target resource."
  - Use case: The **origin server** requires authentication
  - Required header: `WWW-Authenticate`

- **407 Proxy Authentication Required** (Section 3.2): "The proxy MUST send a Proxy-Authenticate header field containing a challenge applicable to that proxy for the target resource."
  - Use case: The **proxy** requires authentication
  - Required header: `Proxy-Authenticate`

#### Actual Proxy Behavior (Non-Compliant)

When authentication is missing, the proxy returns:

```
HTTP/1.1 401 Unauthorized
www-authenticate: Bearer realm=""
content-length: 14
content-type: text/plain

Jwt is missing
```

**Violations**:
1. **Wrong status code**: Returns `401` instead of `407` for proxy authentication
2. **Wrong header name**: Uses `www-authenticate` (lowercase, for origin servers) instead of `Proxy-Authenticate` (for proxies)
3. **Misleading auth scheme**: Advertises `Bearer` authentication but actually accepts Basic auth

This non-compliance causes standard HTTP clients (including Maven's new transport) to misinterpret the challenge as coming from the origin server rather than the proxy.

### Applying to Maven

#### Working Solution: Use Wagon Transport

Maven 3.9.x changed from Wagon (Apache HttpClient 4.5) to a new native HTTP transport (maven-resolver-transport-http). The new transport **fails** with this non-compliant proxy because it interprets the 401 as an origin server challenge, not a proxy challenge.

**Solution**: Force Maven to use the legacy Wagon transport, which uses **preemptive authentication**:

```bash
export MAVEN_OPTS="-Djdk.http.auth.tunneling.disabledSchemes= -Dmaven.resolver.transport=wagon"
mvn clean package
```

**Why this works**:
1. **Wagon uses preemptive proxy authentication**: Apache HttpClient 4.5.14 reads credentials from `~/.m2/settings.xml` and sends `Proxy-Authorization` header on the **first** CONNECT request
2. **No challenge-response needed**: Since credentials are sent immediately (like curl), Wagon never receives the non-compliant 401 response
3. **Basic auth enabled**: The `jdk.http.auth.tunneling.disabledSchemes=""` property allows Basic auth for CONNECT tunneling

#### Why Native Transport Fails

Maven's native HTTP transport (maven-resolver-transport-http) uses **challenge-response authentication**:
1. Send CONNECT without credentials
2. Receive proxy challenge (expects **407** with `Proxy-Authenticate`)
3. Retry CONNECT with `Proxy-Authorization` header

When the proxy returns 401 instead of 407, Maven's native transport treats it as an **origin server** authentication challenge and doesn't use the configured proxy credentials from `settings.xml`.

#### Verification (strace evidence)

**Wagon (works)**:
```
CONNECT repo.maven.apache.org:443 HTTP/1.1
Host: repo.maven.apache.org
User-Agent: Apache-HttpClient/4.5.14 (Java/21.0.8)
Proxy-Authorization: Basic <credentials>
```
→ Sends credentials on first request (preemptive)

**Native transport (fails)**:
```
CONNECT repo.maven.apache.org:443 HTTP/1.1
Host: repo.maven.apache.org
User-Agent: Apache-Maven/3.9.11 (Java 21.0.8; Linux 4.4.0)
```
→ No `Proxy-Authorization` header, receives 401, interprets as server challenge, fails

### Key Learnings

1. **Empty `/etc/resolv.conf` is not the problem** - container networking uses HTTP proxy for all external access
2. **The proxy is HTTP non-compliant** - returns 401 instead of 407, violating RFC 7235 Section 3.2
3. **Maven 3.9.x changed transports** - new native transport uses challenge-response auth (expects 407), old Wagon uses preemptive auth
4. **Preemptive authentication works around non-compliant proxies** - curl and Wagon send credentials immediately, bypassing the challenge-response flow
5. **Basic auth for tunneling is disabled by default** in Java 17+ for security reasons (credentials sent in cleartext) - must be explicitly enabled via `jdk.http.auth.tunneling.disabledSchemes=""`
6. **JDK-8306745 is a red herring** - this Authenticator bug only affected our diagnostic test scripts, not the Maven build itself (Authenticator was never called due to 401 vs 407 issue)
7. **DNS resolution tests are misleading** in proxy environments - they test direct DNS, not proxy-routed connections

### References

- **RFC 7235**: HTTP/1.1 Authentication - https://datatracker.ietf.org/doc/html/rfc7235
  - Section 3.1: 401 Unauthorized (origin server authentication)
  - Section 3.2: 407 Proxy Authentication Required (proxy authentication)
- **JDK-8306745**: HttpClient silently drops Authorization headers with authenticated proxies
- **Test Scripts**: `scripts/TestHttps.java`, `scripts/TestHttpsWithAuthenticator.java`, `scripts/TestDNS.java`
- **Configuration**: `$JAVA_HOME/conf/net.properties` (contains `jdk.http.auth.tunneling.disabledSchemes=Basic`)
- **Maven Settings**: `~/.m2/settings.xml` (proxy configuration)

## Questions for System Administrator

1. ~~Why is `/etc/resolv.conf` empty?~~ **ANSWERED**: Container uses HTTP proxy for all external access, no direct DNS needed
2. ~~How is DNS resolution supposed to work in this environment?~~ **ANSWERED**: Through the HTTP proxy at `21.0.0.77:15004`
3. ~~Are there known restrictions on Java networking in this container?~~ **ANSWERED**: Yes, Java 17+ disables Basic auth for proxy tunneling by default
4. ~~Is there a Maven mirror/proxy we should be using instead?~~ **ANSWERED**: The HTTP proxy is correctly configured via `HTTPS_PROXY`
5. ~~Are outbound HTTPS connections from Java intentionally blocked?~~ **ANSWERED**: No, but they require proxy authentication
6. **NEW**: Can the HTTP proxy be fixed to return `407 Proxy Authentication Required` with `Proxy-Authenticate` header instead of the non-compliant `401 Unauthorized` with `www-authenticate`?

## Conclusion

~~The Maven build cannot complete due to fundamental networking restrictions in the environment. While non-Java tools work fine, Java applications cannot perform DNS resolution (and potentially cannot make HTTPS connections even if DNS is manually configured).~~

~~This needs environment-level fixes, not code-level changes. The project's `pom.xml` is correctly configured - this is purely an infrastructure issue.~~

**FINAL SOLUTION**: Maven builds work successfully! The root causes were:

1. **Container HTTP proxy non-compliance**: The HTTP proxy returns `401 Unauthorized` (RFC 7235 violation) instead of `407 Proxy Authentication Required` for CONNECT tunnel authentication
2. **Maven 3.9.x transport change**: New native HTTP transport uses challenge-response authentication and fails when receiving non-compliant 401 responses
3. **Java 17+ security restriction**: Basic authentication is disabled by default for CONNECT tunneling

**Working Configuration**:

1. **Create `~/.m2/settings.xml`** with proxy credentials:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0
                              http://maven.apache.org/xsd/settings-1.0.0.xsd">
  <proxies>
    <proxy>
      <id>container-proxy</id>
      <active>true</active>
      <protocol>https</protocol>
      <host>21.0.0.9</host>
      <port>15004</port>
      <username>container_container_011CUurpgkapzArANsfBvYER--claude_code_remote--steep-gloomy-hasty-hatch</username>
      <password>jwt_eyJ0eXAi...</password>
    </proxy>
  </proxies>
</settings>
```

Extract credentials from `HTTPS_PROXY` environment variable (format: `http://user:password@host:port`).

2. **Run Maven** with Wagon transport and Basic auth enabled:

```bash
export MAVEN_OPTS="-Djdk.http.auth.tunneling.disabledSchemes= -Dmaven.resolver.transport=wagon"
mvn clean package
```

This forces Maven to use the legacy Wagon transport (Apache HttpClient 4.5), which uses **preemptive proxy authentication** (reading credentials from settings.xml and sending them on the first CONNECT request). Combined with enabling Basic auth for tunneling, Maven successfully downloads all dependencies and builds the project.

The project's `pom.xml` is correctly configured. The issue is entirely related to the non-compliant proxy and incompatibility between Maven's new HTTP transport and the proxy's authentication behavior.

---

## Appendix: Diagnostic Commands

### Testing Proxy Response Without Credentials

To capture the 401 response from the proxy:

```bash
# Extract proxy host/port without credentials
python3 -c "
import urllib.parse
import os
proxy = os.getenv('HTTPS_PROXY')
parsed = urllib.parse.urlparse(proxy)
print(f'{parsed.scheme}://{parsed.hostname}:{parsed.port}')
"

# Test with curl (shows 401 with Bearer challenge)
curl -x http://21.0.0.9:15004 -I https://repo.maven.apache.org/maven2/ 2>&1
```

Expected output:
```
curl: (56) CONNECT tunnel failed, response 401
HTTP/1.1 401 Unauthorized
www-authenticate: Bearer realm=""
content-length: 14
content-type: text/plain
date: Sat, 08 Nov 2025 07:21:26 GMT
server: envoy
connection: close
```

### Capturing Curl's Preemptive Authentication

To verify curl sends Proxy-Authorization on first request:

```bash
curl -v -I https://repo.maven.apache.org/maven2/ 2>&1 | grep -A5 "CONNECT\|Proxy-Authorization"
```

Look for:
```
> CONNECT repo.maven.apache.org:443 HTTP/1.1
> Host: repo.maven.apache.org:443
> Proxy-Authorization: Basic <base64-encoded-credentials>
```

### Capturing Maven Native Transport Behavior (Fails)

To see Maven's new transport sending CONNECT without credentials:

```bash
strace -f -e trace=write -s 2000 mvn clean package 2>&1 | grep -A1 "CONNECT repo" | head -6
```

Look for:
```
CONNECT repo.maven.apache.org:443 HTTP/1.1
Host: repo.maven.apache.org
User-Agent: Apache-Maven/3.9.11 (Java 21.0.8; Linux 4.4.0)
```

Note: **No `Proxy-Authorization` header** - Maven native transport expects 407 challenge.

### Capturing Maven Wagon Transport Behavior (Works)

To verify Wagon sends credentials preemptively:

```bash
strace -f -e trace=write -s 2000 bash -c 'MAVEN_OPTS="-Djdk.http.auth.tunneling.disabledSchemes= -Dmaven.resolver.transport=wagon" mvn dependency:get -Dartifact=org.apache.commons:commons-lang3:3.14.0' 2>&1 | grep -A1 "CONNECT repo" | head -6
```

Look for:
```
CONNECT repo.maven.apache.org:443 HTTP/1.1
Host: repo.maven.apache.org
User-Agent: Apache-HttpClient/4.5.14 (Java/21.0.8)
Proxy-Authorization: Basic <base64-encoded-credentials>
```

Note: **Includes `Proxy-Authorization` header** on first request (preemptive auth).

### Capturing Proxy 401 Response Headers

To see the exact headers returned by the proxy:

```bash
strace -f -e trace=read,write -s 2000 mvn clean package 2>&1 | grep -A10 "HTTP/1.1 401" | head -50
```

Look for:
```
HTTP/1.1 401 Unauthorized
www-authenticate: Bearer realm=""
content-length: 14
content-type: text/plain
date: Sat, 08 Nov 2025 07:19:37 GMT
server: envoy
connection: close

Jwt is missing
```

### Testing Java HttpClient Manually

Run the test scripts to verify Java networking:

```bash
# Test with manual Proxy-Authorization header (works)
cd scripts
javac TestHttps.java
java -Djdk.http.auth.tunneling.disabledSchemes= TestHttps

# Test with Authenticator (fails due to JDK-8306745)
javac TestHttpsWithAuthenticator.java
java -Djdk.http.auth.tunneling.disabledSchemes= TestHttpsWithAuthenticator
```
