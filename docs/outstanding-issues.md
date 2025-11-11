# Outstanding Issues and Status

**Date**: 2025-11-11
**Branch**: `claude/implement-phase-3-011CV2qvfsb344wh1FMxbumw`

## Summary

Phase 3 implementation is **functionally complete** but has a **critical stdout redirection issue** preventing CLI mode from producing output.

## Current Status

### ✅ Completed

1. **Database Introspection Tool** - Fully implemented
   - `introspect()` - lists all schemas/catalogs
   - `introspect(schema)` - lists tables in schema
   - `introspect(schema, table)` - shows table structure with columns, types, constraints
   - Works for both PostgreSQL and SQLite

2. **Dynamic JDBC Driver Loading** - Working
   - Removed Agroal dependency (it required build-time db-kind config)
   - Simplified to use DriverManager directly via CDI `@Produces Connection`
   - Explicitly loads drivers based on URL prefix (required for uber-JARs)
   - Supports both `jdbc:postgresql:*` and `jdbc:sqlite:*` URLs

3. **Logging Configuration** - Fixed
   - JBoss LogManager initialization error resolved (static block in MainApplication)
   - ISO8601 timestamp format: `%d{yyyy-MM-dd'T'HH:mm:ss.SSSXXX} %-5p %c{3.} - %s%e%n`
   - All logs redirect to stderr (required for MCP stdio compatibility)
   - Clean log output, no more excessive DEBUG statements

4. **Tests** - All Passing
   - 57 tests passing, 6 skipped
   - All tests use Hamcrest matchers (as per project standards)
   - Tests use `tapSystemOut()` successfully to capture stdout

### ❌ Critical Issue: stdout Output Not Appearing in CLI Mode

**Problem**: When running the uber-JAR in CLI mode, `System.out.println()` produces **no output** on stdout, even though:
- Exit code: 0 (success)
- Logs show: "CLI execution complete with exit code: 0"
- Database connection succeeds
- stderr works fine (errors, usage messages appear)
- Tests work perfectly (using `tapSystemOut()`)

**Reproduction**:
```bash
# Build
mvn clean package -DskipTests

# Run CLI
DB_URL="jdbc:sqlite::memory:" java -jar target/mcp-database-server-HEAD-SNAPSHOT.jar --cli introspect 2>stderr.log >stdout.log

# Check results
echo "Exit code: $?"  # Shows: 0
cat stderr.log        # Shows logs: "CLI execution complete with exit code: 0"
cat stdout.log        # Shows: EMPTY (should contain schema listing)
```

**Expected stdout output**:
```
Schema
------
main
```

**Actual stdout**: Empty file

**Code Location**: `src/main/java/org/geekden/mcp/cli/CliCommandHandler.java:115`
```java
System.out.println(result);  // This line produces NO output in uber-JAR
```

**What We've Tried**:
1. ✗ Added `System.out.flush()` after println - no effect
2. ✗ Added `System.out.flush()` in MainApplication before return - no effect
3. ✗ Checked logging config - stderr redirect is correct, shouldn't affect stdout
4. ✗ Added debug println statements - they also produce no output

**Hypothesis**: Quarkus or one of its extensions is intercepting/redirecting stdout in production mode, possibly:
- The MCP server stdio extension
- Quarkus logging subsystem
- JBoss LogManager
- Something in uber-JAR packaging

**What Works**:
- Tests using `tapSystemOut()` successfully capture stdout
- stderr works perfectly (all error messages appear)
- Logging to stderr works (all LOG.info/error appear)

**What Doesn't Work**:
- Any `System.out.println()` in CLI mode when running uber-JAR
- Any `System.out.write()` in CLI mode when running uber-JAR

## Architecture Changes

### Removed: Agroal Datasource
**Why**: Agroal requires build-time `db-kind` configuration, incompatible with dynamic URL support.

**Before**:
- `pom.xml` included `quarkus-agroal` dependency
- `DataSourceProvider` tried Agroal first, fell back to DriverManager
- Generated warnings every time SQLite was used (since build was postgresql)

**After**:
- Removed `quarkus-agroal` from `pom.xml`
- `DataSourceProvider` uses DriverManager directly
- Simpler, cleaner, no warnings
- All consumers still use `@Inject Instance<Connection>` (no code changes)

### Files Modified

1. **src/main/java/org/geekden/mcp/service/DataSourceProvider.java**
   - Removed Agroal imports and injection
   - Uses only DriverManager
   - Explicitly loads JDBC drivers based on URL

2. **src/main/java/org/geekden/mcp/config/DatabaseInfoLogger.java**
   - Changed from `Instance<AgroalDataSource>` to `Instance<Connection>`
   - Uses CDI-produced Connection

3. **pom.xml**
   - Removed `quarkus-agroal` dependency

4. **src/main/resources/application.properties**
   - Removed Agroal pool configuration
   - Removed `db-kind` and dev services config
   - Kept only JDBC URL/username/password properties

## Next Steps

### High Priority: Fix stdout Redirection Issue

**Need to investigate**:
1. Check Quarkus application.properties for any stdout redirection settings
2. Review MCP server stdio extension behavior - does it capture stdout?
3. Check if uber-JAR build is doing something special with System.out
4. Consider using a different output mechanism for CLI mode
5. Check if there's a Quarkus shutdown hook interfering with output

**Potential Solutions**:
1. **Use logging for CLI output** - Output results via LOG.info instead of System.out
   - Pros: Might work around the redirection issue
   - Cons: Pollutes logs, wrong semantic meaning

2. **Write directly to FileDescriptor** - Bypass System.out
   ```java
   FileOutputStream fdOut = new FileOutputStream(FileDescriptor.out);
   fdOut.write(result.getBytes(StandardCharsets.UTF_8));
   fdOut.flush();
   ```

3. **Disable MCP stdio in CLI mode** - Prevent extension from capturing stdout
   - Need to research how to conditionally disable Quarkus extensions

4. **Use a different packaging mode** - Fast-jar instead of uber-jar
   - Test if the issue exists in fast-jar mode

5. **Direct JVM invocation** - Bypass Quarkus.run() for CLI mode
   - More complex, defeats purpose of Quarkus framework

### Medium Priority: Testing

Once stdout is fixed:
- Test with real PostgreSQL database (not just SQLite)
- Test with file-based SQLite (not just in-memory)
- Verify all introspection scenarios work end-to-end

### Low Priority: Documentation

- Update implementation-phases.md to reflect Phase 3 completion
- Document the stdout issue and solution (once found)
- Update README with correct CLI usage examples

## Environment

- **Java**: OpenJDK 21
- **Quarkus**: 3.27.0 LTS
- **MCP Extension**: 1.7.0
- **Package Type**: uber-jar
- **Build Command**: `mvn clean package`
- **Run Command**: `DB_URL="..." java -jar target/mcp-database-server-HEAD-SNAPSHOT.jar --cli introspect`

## Questions for User

1. Is the stdout capture by MCP stdio extension expected/documented behavior?
2. Should CLI mode use a different output mechanism than System.out?
3. Is there a Quarkus configuration to preserve stdout in CLI mode?
4. Should we switch from uber-jar to fast-jar packaging?
