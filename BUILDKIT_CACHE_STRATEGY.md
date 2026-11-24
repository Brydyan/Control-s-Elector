# BuildKit Cache Invalidation Strategy

## Problem Solved

Docker BuildKit on Railway caches all layers, including Maven builds. When dependencies or code change, the cache prevents Maven from re-executing, causing stale JARs to be deployed.

## Solution: ARG BUILD_ID

Both `Dockerfile` and `Dockerfile.native` now declare:

```dockerfile
ARG BUILD_ID=unknown
RUN echo "Build ID: ${BUILD_ID}" && \
    # Maven build with 429 backoff logic
```

**Why this works:**
- BuildKit includes ARG values in the layer cache key (official Docker design)
- When `BUILD_ID` changes, the cache key changes, forcing layer rebuild
- All subsequent layers cascade rebuild automatically
- Minimal overhead (one echo statement)

## Configuration: Railway Dashboard

1. **Navigate to:** Railway App → Service Settings → Variables
2. **Add variable:** `BUILD_ID`
3. **Set value:** Any unique string
   - Recommended: `$(date +%s%N)` or `$(date -u +%Y%m%d-%H%M%S)`
   - Or manual: `rebuild-20261124-1700`

4. **Deploy:** Next build will skip Maven cache

## When to Invalidate

- **After 429 errors:** Maven Central rate limiting
- **Security updates:** Dependencies with CVEs
- **Scheduled builds:** Nightly/weekly full rebuilds
- **Manual intervention:** User suspects cache corruption
- **Post-incident:** After production errors traced to stale JAR

## Implementation Details

**Dockerfile (JVM build):**
```dockerfile
ARG BUILD_ID=unknown
RUN echo "Build ID: ${BUILD_ID}" && \
    attempt=1; \
    for mirror in google aliyun central google aliyun; do \
      # Maven with 429 backoff...
```

**Dockerfile.native (native image build):**
```dockerfile
ARG BUILD_ID=unknown
RUN echo "Build ID: ${BUILD_ID}" && \
    attempt=1; \
    for mirror in google aliyun central google aliyun; do \
      # Maven native build with 429 backoff...
```

## Best Practices Alignment

✅ **Official Docker Recommendation:** ARG values are cache keys (BuildKit design)
✅ **No side effects:** Doesn't affect other layers or optimization
✅ **Explicit & auditable:** Build logs show "Build ID: <value>"
✅ **Railway native:** Full support for `--build-arg` without extra config
✅ **Future-proof:** Works with Buildx, local builds, CI/CD

## Why Not Other Approaches?

| Approach | Why Not |
|----------|---------|
| Reorder COPY | Violates Docker principle "less changed to more changed"; causes expensive Maven rebuild on every src change |
| `.builddate` file | Docker excludes file mtime from cache key; file-based caching doesn't work |
| Clear S3 cache | Railway has no selective clearing UI; `NO_CACHE=1` is all-or-nothing |

## Troubleshooting

**Q: I set BUILD_ID but build still cached?**
A: Verify in Railway Build Logs that "Build ID: <value>" appears with new value. If it shows old value, Railway may not have redeployed. Try "Redeploy" button.

**Q: How often should I change BUILD_ID?**
A: Only when necessary (429 errors, security updates, manual override). Frequent changes defeat the purpose of caching.

**Q: Can I automate this?**
A: Yes, with Railway's API or CI/CD integration, but requires separate automation. For now, manual variable update is simplest.

---

**Commit:** 2ae1221  
**Research basis:** Official Docker BuildKit documentation, Railway platform features, industry best practices
