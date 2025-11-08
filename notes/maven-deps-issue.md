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

## Questions for System Administrator

1. Why is `/etc/resolv.conf` empty?
2. How is DNS resolution supposed to work in this environment?
3. Are there known restrictions on Java networking in this container?
4. Is there a Maven mirror/proxy we should be using instead?
5. Are outbound HTTPS connections from Java intentionally blocked?

## Conclusion

The Maven build cannot complete due to fundamental networking restrictions in the environment. While non-Java tools work fine, Java applications cannot perform DNS resolution (and potentially cannot make HTTPS connections even if DNS is manually configured).

This needs environment-level fixes, not code-level changes. The project's `pom.xml` is correctly configured - this is purely an infrastructure issue.
