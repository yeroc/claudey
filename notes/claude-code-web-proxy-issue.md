# Claude Code on the Web - HTTP Proxy RFC Non-Compliance

**Date**: 2025-11-08
**Issue**: Container HTTP proxy returns incorrect HTTP status code for CONNECT tunnel authentication
**Impact**: Breaks any HTTP client using challenge-response authentication (expects RFC 7235 compliance)

## Executive Summary

The Claude Code on the web container environment uses an HTTP proxy (response headers indicate `server: envoy`) that **violates RFC 7235** when handling CONNECT tunnel authentication. Instead of returning `407 Proxy Authentication Required` with a `Proxy-Authenticate` header, it returns `401 Unauthorized` with a `www-authenticate` header.

This non-compliance breaks any HTTP client that uses standard challenge-response authentication, including:
- Maven 3.9.x+ (native HTTP transport)
- Modern Java HttpClient implementations expecting RFC compliance
- Any tool that waits for `407` to trigger proxy credential usage

**Only preemptive authentication works**: Tools that send credentials on the first request (curl, Maven Wagon, Apache HttpClient 4.5) succeed because they never receive the non-compliant response.

---

## RFC 7235 Specification

### Section 3.1: 401 Unauthorized (Origin Server Authentication)

> **When to use**: The origin server requires authentication for the target resource.
>
> **Required response header**: `WWW-Authenticate`
>
> **Client retry header**: `Authorization`

**Example (compliant)**:
```http
HTTP/1.1 401 Unauthorized
WWW-Authenticate: Basic realm="My Server"
```

Client should retry with:
```http
GET /resource HTTP/1.1
Authorization: Basic <credentials>
```

### Section 3.2: 407 Proxy Authentication Required (Proxy Authentication)

> **When to use**: A proxy requires authentication before forwarding the request.
>
> **Required response header**: `Proxy-Authenticate`
>
> **Client retry header**: `Proxy-Authorization`

**Example (compliant)**:
```http
HTTP/1.1 407 Proxy Authentication Required
Proxy-Authenticate: Basic realm="My Proxy"
```

Client should retry with:
```http
CONNECT target.example.com:443 HTTP/1.1
Proxy-Authorization: Basic <credentials>
```

### Key Distinction

The fundamental difference:
- **401** = Origin server needs authentication (for the target resource)
- **407** = Proxy needs authentication (to forward the request)

Each uses its corresponding header pair:
- 401 uses `WWW-Authenticate` / `Authorization`
- 407 uses `Proxy-Authenticate` / `Proxy-Authorization`

---

## Actual Proxy Behavior (Non-Compliant)

### What the Proxy Returns

When a CONNECT request is sent without credentials:

```http
CONNECT repo.maven.apache.org:443 HTTP/1.1
Host: repo.maven.apache.org
```

**Actual proxy response**:
```http
HTTP/1.1 401 Unauthorized
www-authenticate: Bearer realm=""
content-length: 14
content-type: text/plain
date: Sat, 08 Nov 2025 07:21:26 GMT
server: envoy
connection: close

Jwt is missing
```

### RFC 7235 Violations

1. **Wrong status code**: Returns `401 Unauthorized` instead of `407 Proxy Authentication Required`
   - **Impact**: Clients interpret this as the origin server requiring auth, not the proxy

2. **Wrong header name**: Uses `www-authenticate` instead of `Proxy-Authenticate`
   - **Impact**: Standard clients look for `Proxy-Authenticate` to trigger proxy credential usage

3. **Misleading auth scheme**: Advertises `Bearer realm=""` but actually accepts Basic authentication
   - **Impact**: Clients supporting only Basic/Digest/NTLM may incorrectly reject the challenge

### Evidence from strace

Captured using:
```bash
curl -x http://21.0.0.9:15004 -I https://repo.maven.apache.org/maven2/ 2>&1
```

Output confirms:
```
curl: (56) CONNECT tunnel failed, response 401
HTTP/1.1 401 Unauthorized
www-authenticate: Bearer realm=""
```

Note: Even curl reports this as a "failed" tunnel due to the 401, but curl works around it using preemptive authentication when credentials are in the proxy URL.

---

## Impact on HTTP Clients

### Challenge-Response Authentication (Broken)

**How it's supposed to work** (RFC 7235 compliant proxy):

1. Client sends CONNECT without credentials
   ```http
   CONNECT target.example.com:443 HTTP/1.1
   Host: target.example.com
   ```

2. Proxy returns **407** with challenge
   ```http
   HTTP/1.1 407 Proxy Authentication Required
   Proxy-Authenticate: Basic realm="proxy"
   ```

3. Client recognizes **407**, looks up proxy credentials, retries with auth
   ```http
   CONNECT target.example.com:443 HTTP/1.1
   Host: target.example.com
   Proxy-Authorization: Basic <credentials>
   ```

4. Proxy accepts, tunnel established

**What actually happens** (non-compliant proxy):

1. Client sends CONNECT without credentials
   ```http
   CONNECT target.example.com:443 HTTP/1.1
   Host: target.example.com
   ```

2. Proxy returns **401** (WRONG!)
   ```http
   HTTP/1.1 401 Unauthorized
   www-authenticate: Bearer realm=""
   ```

3. Client interprets this as **origin server authentication required**
   - Client thinks: "The target server needs credentials, not the proxy"
   - Client does NOT send proxy credentials from settings/config
   - Client fails with "401 Unauthorized"

### Preemptive Authentication (Works)

**How preemptive authentication works**:

1. Client has proxy credentials configured (from URL, settings file, etc.)

2. Client sends CONNECT **with credentials on first request**
   ```http
   CONNECT target.example.com:443 HTTP/1.1
   Host: target.example.com
   Proxy-Authorization: Basic <credentials>
   ```

3. Proxy authenticates, tunnel established
   ```http
   HTTP/1.1 200 OK
   ```

4. Client never receives the non-compliant 401 response

**Why this works around the bug**: By sending credentials immediately, the proxy never needs to send a challenge. The broken challenge-response flow is entirely bypassed.

---

## Tool Comparison

### curl (Works - Uses Preemptive Auth)

**Command**:
```bash
curl -v -I https://repo.maven.apache.org/maven2/
```

With `HTTPS_PROXY=http://user:pass@21.0.0.9:15004` set, curl extracts credentials from the URL.

**strace evidence**:
```bash
curl -v -I https://repo.maven.apache.org/maven2/ 2>&1 | grep -A5 "CONNECT\|Proxy-Authorization"
```

**Output shows**:
```
* Proxy auth using Basic with user 'container_container_...'
> CONNECT repo.maven.apache.org:443 HTTP/1.1
> Host: repo.maven.apache.org:443
> Proxy-Authorization: Basic Y29udGFpbmVyX2NvbnRhaW5lc...
> User-Agent: curl/8.5.0
> Proxy-Connection: Keep-Alive

< HTTP/1.1 200 OK
```

**Result**: ✅ Success - sends `Proxy-Authorization` on first request

### Maven 3.9.x Native Transport (Broken - Uses Challenge-Response)

**Command**:
```bash
MAVEN_OPTS="-Djdk.http.auth.tunneling.disabledSchemes=" mvn clean package
```

(With proxy credentials configured in `~/.m2/settings.xml`)

**strace evidence**:
```bash
strace -f -e trace=write -s 2000 mvn clean package 2>&1 | grep -A1 "CONNECT repo" | head -6
```

**Output shows**:
```
CONNECT repo.maven.apache.org:443 HTTP/1.1
Host: repo.maven.apache.org
User-Agent: Apache-Maven/3.9.11 (Java 21.0.8; Linux 4.4.0)

```

**Result**: ❌ Fails
- No `Proxy-Authorization` header on first request
- Receives 401 response
- Interprets as origin server auth requirement
- Never uses configured proxy credentials
- Fails with "401 Unauthorized"

### Maven Wagon Transport (Works - Uses Preemptive Auth)

**Command**:
```bash
MAVEN_OPTS="-Djdk.http.auth.tunneling.disabledSchemes= -Dmaven.resolver.transport=wagon" mvn clean package
```

(With proxy credentials configured in `~/.m2/settings.xml`)

**strace evidence**:
```bash
strace -f -e trace=write -s 2000 bash -c 'MAVEN_OPTS="-Djdk.http.auth.tunneling.disabledSchemes= -Dmaven.resolver.transport=wagon" mvn dependency:get -Dartifact=org.apache.commons:commons-lang3:3.14.0' 2>&1 | grep -A1 "CONNECT repo" | head -6
```

**Output shows**:
```
CONNECT repo.maven.apache.org:443 HTTP/1.1
Host: repo.maven.apache.org
User-Agent: Apache-HttpClient/4.5.14 (Java/21.0.8)
Proxy-Authorization: Basic Y29udGFpbmVyX2NvbnRhaW5lc...
```

**Result**: ✅ Success - sends `Proxy-Authorization` on first request

### Summary Table

| Tool | Auth Strategy | Proxy-Authorization Header | Result | Why |
|------|---------------|---------------------------|--------|-----|
| **curl** | Preemptive (from URL) | ✅ Sent on first request | ✅ Works | Bypasses challenge-response |
| **Maven 3.9.x native** | Challenge-response | ❌ Not sent | ❌ Fails | Waits for 407, gets 401 instead |
| **Maven Wagon** | Preemptive (from settings.xml) | ✅ Sent on first request | ✅ Works | Bypasses challenge-response |
| **Apache HttpClient 4.5** | Preemptive (configurable) | ✅ Sent on first request | ✅ Works | Bypasses challenge-response |

---

## Why Challenge-Response Fails

### Expected Flow (RFC-Compliant Proxy)

```
Client                          Proxy                    Origin Server
  |                               |                            |
  |-- CONNECT (no auth) --------->|                            |
  |                               |                            |
  |<-- 407 Proxy-Authenticate ---|                            |
  |                               |                            |
  |-- CONNECT + Proxy-Auth ------>|                            |
  |                               |                            |
  |<-- 200 OK -------------------|                            |
  |                               |                            |
  |<=== Encrypted TLS Tunnel ===>|<=== Encrypted Tunnel ====>|
```

Client receives **407**, recognizes it as a **proxy** challenge, sends proxy credentials.

### Actual Flow (Non-Compliant Proxy)

```
Client                          Proxy                    Origin Server
  |                               |                            |
  |-- CONNECT (no auth) --------->|                            |
  |                               |                            |
  |<-- 401 Unauthorized ---------|                            |
  |    www-authenticate: Bearer   |                            |
  |                               |                            |
  |                               |                            |
  X Client interprets as          |                            |
    origin server auth needed     |                            |
    (not proxy auth!)             |                            |
                                  |                            |
  X Client fails with 401         |                            |
    Never sends proxy credentials |                            |
```

Client receives **401**, interprets it as **origin server** authentication requirement, doesn't use proxy credentials.

### Preemptive Flow (Workaround)

```
Client                          Proxy                    Origin Server
  |                               |                            |
  |-- CONNECT + Proxy-Auth ------>|                            |
  |                               |                            |
  |<-- 200 OK -------------------|                            |
  |                               |                            |
  |<=== Encrypted TLS Tunnel ===>|<=== Encrypted Tunnel ====>|
```

Client sends credentials on **first request**, proxy never needs to send challenge, broken flow is never triggered.

---

## Technical Root Cause

### HTTP Client Decision Logic

Most HTTP clients use logic similar to this:

```java
// Pseudocode for standard HTTP client proxy handling
Response response = sendRequest(request);

if (response.statusCode == 407) {
    // Proxy authentication required
    String challenge = response.getHeader("Proxy-Authenticate");
    Credentials proxyCreds = lookupProxyCredentials(proxyHost, proxyPort);
    request.addHeader("Proxy-Authorization", generateAuthHeader(proxyCreds, challenge));
    response = sendRequest(request); // Retry with auth
}
else if (response.statusCode == 401) {
    // Origin server authentication required
    String challenge = response.getHeader("WWW-Authenticate");
    Credentials serverCreds = lookupServerCredentials(request.uri);
    request.addHeader("Authorization", generateAuthHeader(serverCreds, challenge));
    response = sendRequest(request); // Retry with auth
}
```

**The problem**: When proxy returns 401 instead of 407:
- Client executes the `401` branch (origin server auth)
- Looks for **server** credentials for `repo.maven.apache.org`
- Does NOT look for **proxy** credentials
- `lookupServerCredentials()` returns null (no credentials configured for repo.maven.apache.org)
- Fails with "401 Unauthorized"

### Maven 3.9.x Specific

Maven's native transport (`maven-resolver-transport-http`) uses Java's modern `java.net.http.HttpClient`, which strictly follows RFC 7235:

1. Sends CONNECT without credentials
2. Waits for authentication challenge
3. Expects **407** with `Proxy-Authenticate` header to trigger proxy auth
4. Receives **401** with `www-authenticate` header instead
5. Treats this as origin server authentication requirement
6. Never uses configured proxy credentials from `~/.m2/settings.xml`
7. Fails

### Maven Wagon Difference

Maven Wagon transport uses Apache HttpClient 4.5, which has **preemptive authentication** enabled when proxy credentials are configured:

1. Reads proxy credentials from `~/.m2/settings.xml`
2. Immediately adds `Proxy-Authorization` header to CONNECT request
3. Proxy accepts credentials, returns 200
4. Never receives the non-compliant 401 response

This is why `-Dmaven.resolver.transport=wagon` works around the issue.

---

## Solutions and Workarounds

### For Maven Users

**Use Wagon transport instead of native transport**:

1. Configure `~/.m2/settings.xml` with proxy credentials:
   ```xml
   <settings>
     <proxies>
       <proxy>
         <id>claude-code-proxy</id>
         <active>true</active>
         <protocol>https</protocol>
         <host>21.0.0.9</host>
         <port>15004</port>
         <username>container_container_...</username>
         <password>jwt_...</password>
       </proxy>
     </proxies>
   </settings>
   ```

2. Force Wagon transport:
   ```bash
   export MAVEN_OPTS="-Djdk.http.auth.tunneling.disabledSchemes= -Dmaven.resolver.transport=wagon"
   mvn clean package
   ```

### For Custom Java Applications

**Use preemptive authentication**:

```java
System.setProperty("jdk.http.auth.tunneling.disabledSchemes", "");

// Parse proxy credentials from environment
URI proxyUri = new URI(System.getenv("HTTPS_PROXY"));
String userInfo = proxyUri.getUserInfo();
String encodedAuth = Base64.getEncoder().encodeToString(userInfo.getBytes());

// Create HttpClient with proxy (but no Authenticator)
HttpClient client = HttpClient.newBuilder()
    .proxy(ProxySelector.of(new InetSocketAddress(
        proxyUri.getHost(),
        proxyUri.getPort()
    )))
    .build();

// Manually add Proxy-Authorization header to every request
HttpRequest request = HttpRequest.newBuilder()
    .uri(new URI("https://target.example.com"))
    .header("Proxy-Authorization", "Basic " + encodedAuth)
    .build();

HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
```

**Key point**: Do NOT use `Authenticator` - it won't be called because the proxy returns 401 instead of 407.

### For Infrastructure Teams

**Fix the proxy configuration** to return RFC-compliant responses:

**Before (non-compliant)**:
```http
HTTP/1.1 401 Unauthorized
www-authenticate: Bearer realm=""
```

**After (compliant)**:
```http
HTTP/1.1 407 Proxy Authentication Required
Proxy-Authenticate: Basic realm="Claude Code Proxy"
```

This would allow all standard HTTP clients to work without workarounds.

---

## Additional Java 17+ Consideration

Java 17 and later disable Basic authentication for CONNECT tunneling by default for security reasons.

**Configuration file**: `$JAVA_HOME/conf/net.properties`
```properties
jdk.http.auth.tunneling.disabledSchemes=Basic
```

**Workaround**: Set system property to empty string to enable Basic auth:
```bash
-Djdk.http.auth.tunneling.disabledSchemes=
```

**Note**: This is a **security policy**, not a bug. It must be explicitly overridden to use Basic auth with proxies (which sends credentials in base64-encoded cleartext).

---

## References

- **RFC 7235**: HTTP/1.1 Authentication
  - https://datatracker.ietf.org/doc/html/rfc7235
  - Section 3.1: 401 Unauthorized
  - Section 3.2: 407 Proxy Authentication Required

- **Maven Resolver Transport Documentation**:
  - https://maven.apache.org/guides/mini/guide-resolver-transport.html

- **Apache HttpClient Preemptive Authentication**:
  - https://hc.apache.org/httpcomponents-client-4.5.x/tutorial/html/authentication.html#d5e717

- **Java HttpClient Documentation**:
  - https://docs.oracle.com/en/java/javase/21/docs/api/java.net.http/java/net/http/HttpClient.html

---

## Diagnostic Commands Reference

See the [Appendix section in maven-deps-issue.md](./maven-deps-issue.md#appendix-diagnostic-commands) for detailed strace commands and expected outputs.

**Quick verification**:

```bash
# Test proxy returns 401 instead of 407
curl -x http://21.0.0.9:15004 -I https://repo.maven.apache.org/maven2/ 2>&1 | head -10

# Verify curl uses preemptive auth
curl -v -I https://repo.maven.apache.org/maven2/ 2>&1 | grep "Proxy-Authorization"

# Compare Maven native (fails) vs Wagon (works)
strace -f -e trace=write -s 2000 mvn clean package 2>&1 | grep -A1 "CONNECT repo"
```
