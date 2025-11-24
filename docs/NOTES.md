# Development Notes

## GitHub Actions Issues

### hashFiles() Failure on macOS Runners (Nov 23, 2025)

**Issue**: CI workflow failing on macOS-14 runners with error:
```
The template is not valid. .github/workflows/ci.yml (Line: 141, Col: 16): 
hashFiles('**/pom.xml') failed. Fail to hash files under directory '/Users/runner/work/claudey/claudey'
```

**Root Cause**: GitHub Actions infrastructure regression introduced Nov 19-22, 2025
- Tracked in: [actions/runner-images#13341](https://github.com/actions/runner-images/issues/13341)
- Related commit: [actions/runner@7df164d](https://github.com/actions/runner/commit/7df164d2c7c2f5f2207d4a74c273c3c1d183f831)
- Affects: macOS-14, macOS-15 (both Intel and ARM64)
- Ubuntu/Windows runners unaffected

**Status**: Open issue, awaiting GitHub fix

**Workaround** (if needed):
```yaml
- name: Cache Maven packages
  if: runner.os != 'macOS'  # Temporary workaround
  uses: actions/cache@v4
  with:
    path: ~/.m2/repository
    key: maven-${{ hashFiles('**/pom.xml') }}
```
