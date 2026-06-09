# Quantified Optional Performance Follow-ups Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Improve SoundAttract’s hot-path performance by using additional Quantified API capabilities when Quantified is present, while preserving identical fallback behavior when Quantified is absent.

**Architecture:** Keep Quantified strictly optional by centralizing all reflective/`MethodHandle` interop in one bridge package and exposing capability-checked helper methods to the rest of SoundAttract. Prioritize real work avoidance first by adding optional per-slice caching to Quantified-backed sound scoring, then reduce dispatch overhead by reusing a richer optional task builder bridge for `QuantifiedWorkScheduler`, `AsyncManager`, and `QuantifiedCacheCompat`.

**Tech Stack:** Java 17, Minecraft Forge 1.20.1, ForgeGradle 6, Quantified API 1.0.5 source in `mods/Quantified-API`, JUnit 5 for new unit tests, Spark for profiler verification.

---

## Chunk 1: Scope, files, and test harness

### File structure

**Existing files to modify**
- `build.gradle:111-180`
  - Add test dependencies and enable the Gradle `test` task so optional-bridge logic can be verified without launching Minecraft.
- `src/main/java/com/example/soundattract/quantified/QuantifiedWorkScheduler.java:1-270`
  - Replace remaining ad-hoc reflection with a shared optional bridge and add optional ParallelCompute slice caching for sound scoring.
- `src/main/java/com/example/soundattract/quantified/QuantifiedCacheCompat.java:1-227`
  - Reuse the shared bridge and remove repeated reflective cache invocations in hot paths.
- `src/main/java/com/example/soundattract/async/AsyncManager.java:174-229`
  - Stop duplicating Quantified task-builder reflection and route through the shared optional bridge.
- `src/main/java/com/example/soundattract/quantified/QuantifiedIntegration.java:1-39`
  - Optionally reuse the shared registration/bootstrap bridge.
- `src/main/java/com/example/soundattract/config/separate/IntegrationConfig.java:91-103`
  - Add new optional Quantified tuning toggles for slice caching and bridge behavior.
- `src/main/java/com/example/soundattract/config/SoundAttractConfig.java:338-347`
  - Expose the new config entries through `COMMON`.
- `src/main/java/com/example/soundattract/config/separate/GeneralConfig.java:91-92`
  - Bump config schema if new config keys are introduced.

**Existing files to inspect during implementation**
- `src/main/java/com/example/soundattract/worker/WorkerScheduler.java:54-152`
  - Defines `SoundScoreRequest`, `SoundCandidate`, and `SoundScoreResult`, which determine slice-cache key design and test data.
- `src/main/java/com/example/soundattract/worker/WorkerComputations.java:179-247`
  - The pure sound-scoring logic that should benefit from Quantified slice caching.
- `src/main/java/com/example/soundattract/tracking/SoundTracker.java:522-559`
  - Current Quantified cache call site for block muffling; useful for keeping cache compat behavior aligned.
- `mods/Quantified-API/core/src/main/java/org/admany/quantified/api/parallel/ParallelCompute.java:20-188`
  - Confirms `memorySliceCache`, `persistentSliceCache`, `sliceCache`, `sliceExecutor`, and `submit` signatures.
- `mods/Quantified-API/core/src/main/java/org/admany/quantified/api/model/QuantifiedTask.java:76-158`
  - Confirms builder methods like `priorityBackground`, `threadSafe(boolean)`, `timeout(Duration)`, and `batchKey(String)`.
- `mods/Quantified-API/core/src/main/java/org/admany/quantified/api/QuantifiedAPI.java:103-145`
  - Confirms `submit`, `submitParallel`, `getCached`, `getCachedAsync`, and `hybrid` entry points.

**Files to create**
- `src/main/java/com/example/soundattract/quantified/bridge/QuantifiedOptionalBridge.java`
  - Centralized capability detection and cached `MethodHandle` access for Quantified registration, task submission, parallel compute, and cache access.
- `src/test/java/com/example/soundattract/quantified/QuantifiedOptionalBridgeTest.java`
  - Unit tests for capability detection, graceful fallback, and key helper methods that do not require the actual Quantified mod.
- `src/test/java/com/example/soundattract/worker/SoundScoreCacheKeyTest.java`
  - Unit tests for stable sound-score slice-cache keys and serialization helpers.

### Task 1: Add a minimal test harness for new optional-bridge logic

**Files:**
- Modify: `build.gradle:111-180`
- Create: `src/test/java/com/example/soundattract/quantified/QuantifiedOptionalBridgeTest.java`
- Create: `src/test/java/com/example/soundattract/worker/SoundScoreCacheKeyTest.java`

- [ ] **Step 1: Add JUnit 5 support to `build.gradle`**

```groovy
dependencies {
    testImplementation 'org.junit.jupiter:junit-jupiter:5.10.2'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}

tasks.named('test', Test).configure {
    useJUnitPlatform()
}
```

- [ ] **Step 2: Write the failing bridge fallback test**

```java
package com.example.soundattract.quantified;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class QuantifiedOptionalBridgeTest {
    @Test
    void missingQuantifiedClassesShouldReportUnavailable() {
        assertFalse(false, "replace with bridge capability assertion");
    }
}
```

- [ ] **Step 3: Write the failing sound-score cache-key stability test**

```java
package com.example.soundattract.worker;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SoundScoreCacheKeyTest {
    @Test
    void equivalentRequestsShouldProduceTheSameCacheKey() {
        WorkerScheduler.SoundScoreRequest a = new WorkerScheduler.SoundScoreRequest(
            UUID.fromString("00000000-0000-0000-0000-000000000001"),
            10.0, 64.0, 10.0,
            200L,
            "minecraft:block.note_block.harp",
            List.of(new WorkerScheduler.SoundCandidate("minecraft:block.note_block.harp", 12.0, 64.0, 12.0, 198L, 16.0, 4.0, 1.0)),
            1.15,
            9.5,
            100
        );

        WorkerScheduler.SoundScoreRequest b = new WorkerScheduler.SoundScoreRequest(
            UUID.fromString("00000000-0000-0000-0000-000000000001"),
            10.0, 64.0, 10.0,
            200L,
            "minecraft:block.note_block.harp",
            List.of(new WorkerScheduler.SoundCandidate("minecraft:block.note_block.harp", 12.0, 64.0, 12.0, 198L, 16.0, 4.0, 1.0)),
            1.15,
            9.5,
            100
        );

        assertEquals("replace-with-helper(a)", "replace-with-helper(b)");
    }
}
```

- [ ] **Step 4: Run tests to verify they fail**

Run: `./gradlew.bat test --tests com.example.soundattract.quantified.QuantifiedOptionalBridgeTest --tests com.example.soundattract.worker.SoundScoreCacheKeyTest`

Expected: FAIL because the bridge/helper classes do not exist yet.

- [ ] **Step 5: Commit the harness setup**

```bash
git add build.gradle src/test/java/com/example/soundattract/quantified/QuantifiedOptionalBridgeTest.java src/test/java/com/example/soundattract/worker/SoundScoreCacheKeyTest.java
git commit -m "test: add quantified optional bridge harness"
```

## Chunk 2: Centralize optional Quantified interop

### Task 2: Introduce a shared Quantified optional bridge

**Files:**
- Create: `src/main/java/com/example/soundattract/quantified/bridge/QuantifiedOptionalBridge.java`
- Modify: `src/main/java/com/example/soundattract/async/AsyncManager.java:174-229`
- Modify: `src/main/java/com/example/soundattract/quantified/QuantifiedIntegration.java:1-39`
- Test: `src/test/java/com/example/soundattract/quantified/QuantifiedOptionalBridgeTest.java`

- [ ] **Step 1: Expand the failing test to assert fallback behavior explicitly**

```java
@Test
void submitTaskShouldReturnNullWhenQuantifiedIsUnavailable() {
    assertNull(null, "replace with QuantifiedOptionalBridge.trySubmitTask(...) assertion");
}
```

- [ ] **Step 2: Create `QuantifiedOptionalBridge.java` with capability discovery and cached handles**

```java
package com.example.soundattract.quantified.bridge;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public final class QuantifiedOptionalBridge {
    public static boolean isAvailable() { return false; }
    public static boolean register(String modId) { return false; }
    public static <T> CompletableFuture<T> trySubmitTask(String modId, String taskName, Supplier<T> work, boolean threadSafe, boolean foreground, Duration timeout, String batchKey) { return null; }
    public static Object tryCreateParallelBuilder(String modId, String taskName, long taskKey) { return null; }
}
```

Implementation requirements:
- Cache all Quantified lookups once.
- Use `MethodHandle`, not `Method.invoke`, for hot-path entry points.
- Expose only safe helpers that return `null`/`false` on absence or failure.
- Keep Quantified optional; never reference Quantified types directly in method signatures.

- [ ] **Step 3: Replace `AsyncManager.QuantifiedAsyncBridge` with calls into the shared bridge**

```java
if (isQuantifiedAvailable()) {
    CompletableFuture<T> quantifiedFuture = QuantifiedOptionalBridge.trySubmitTask(
        SoundAttractMod.MOD_ID,
        taskName,
        supplier,
        threadSafe,
        priority == Priority.HIGH,
        null,
        taskName
    );
    if (quantifiedFuture != null) {
        return quantifiedFuture;
    }
}
```

- [ ] **Step 4: Replace `QuantifiedIntegration.bootstrap()` registration reflection with the shared bridge**

```java
if (!QuantifiedOptionalBridge.register(SoundAttractMod.MOD_ID)) {
    return;
}
```

- [ ] **Step 5: Run targeted tests**

Run: `./gradlew.bat test --tests com.example.soundattract.quantified.QuantifiedOptionalBridgeTest`

Expected: PASS.

- [ ] **Step 6: Run compile verification**

Run: `./gradlew.bat classes`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit the shared bridge**

```bash
git add src/main/java/com/example/soundattract/quantified/bridge/QuantifiedOptionalBridge.java src/main/java/com/example/soundattract/async/AsyncManager.java src/main/java/com/example/soundattract/quantified/QuantifiedIntegration.java src/test/java/com/example/soundattract/quantified/QuantifiedOptionalBridgeTest.java
git commit -m "refactor: centralize optional quantified interop"
```

## Chunk 3: Add optional slice caching for sound scoring

### Task 3: Use Quantified `ParallelCompute` slice caching for `SoundScoreRequest`

**Files:**
- Modify: `src/main/java/com/example/soundattract/quantified/QuantifiedWorkScheduler.java:67-267`
- Modify: `src/main/java/com/example/soundattract/config/separate/IntegrationConfig.java:94-103`
- Modify: `src/main/java/com/example/soundattract/config/SoundAttractConfig.java:341-347`
- Modify: `src/main/java/com/example/soundattract/config/separate/GeneralConfig.java:91-92`
- Test: `src/test/java/com/example/soundattract/worker/SoundScoreCacheKeyTest.java`

- [ ] **Step 1: Write failing tests for cache-key normalization and result serialization helpers**

```java
@Test
void differentCandidateOrderingShouldNormalizeToTheSameCacheKey() {
    assertEquals("replace-with-key-a", "replace-with-key-b");
}

@Test
void soundScoreResultShouldRoundTripThroughSerializer() {
    byte[] encoded = new byte[0];
    assertEquals("expected-sound-id", "replace-with-decoded-sound-id");
}
```

- [ ] **Step 2: Add config toggles for optional sound-score slice caching**

Add to `IntegrationConfig` and surface via `SoundAttractConfig.COMMON`:

```java
ENABLE_QUANTIFIED_SOUND_SCORE_SLICE_CACHE = BUILDER.define("enableQuantifiedSoundScoreSliceCache", true);
QUANTIFIED_SOUND_SCORE_SLICE_CACHE_PERSISTENT = BUILDER.define("quantifiedSoundScoreSliceCachePersistent", false);
QUANTIFIED_SOUND_SCORE_SLICE_CACHE_TTL_TICKS = BUILDER.defineInRange("quantifiedSoundScoreSliceCacheTtlTicks", 20, 1, 72000);
QUANTIFIED_SOUND_SCORE_SLICE_CACHE_MAX_ENTRIES = BUILDER.defineInRange("quantifiedSoundScoreSliceCacheMaxEntries", 4096, 64, 1_000_000);
```

If new keys are added, bump `configSchemaVersion` from `14` to `15` in `GeneralConfig`, and add a no-op migration block in `SoundAttractConfig.bakeConfig()` that sets `COMMON.configSchemaVersion` to `15`.

- [ ] **Step 3: Add helper methods in `QuantifiedWorkScheduler` for stable sound-score slice keys and byte serialization**

```java
private static String soundScoreCacheKey(WorkerScheduler.SoundScoreRequest req) { return ""; }
private static byte[] encodeSoundScoreResult(WorkerScheduler.SoundScoreResult result) { return new byte[0]; }
private static WorkerScheduler.SoundScoreResult decodeSoundScoreResult(byte[] bytes) { return null; }
```

Key rules:
- Include mob position, `currentTargetSoundId`, `switchRatio`, `noveltyBonus`, `noveltyTicks`, and candidate data.
- Normalize candidate ordering by sorting on stable fields such as `soundId`, `x`, `y`, `z`, `gameTime`, `range`, and `weight` before hashing/encoding.
- Bucket or normalize fields deliberately if exact tick variance would destroy cache hit rate.
- Do not include transient object identity or `UUID` randomness that makes identical work miss the cache.

- [ ] **Step 4: Wire `ParallelComputeBridge.trySubmitSoundScores()` to use Quantified slice caching when enabled**

Use either `memorySliceCache(...)` or `persistentSliceCache(...)` based on config.

```java
if (SoundAttractConfig.COMMON.enableQuantifiedSoundScoreSliceCache.get()) {
    long ttlTicks = SoundAttractConfig.COMMON.quantifiedSoundScoreSliceCacheTtlTicks.get();
    long maxEntries = SoundAttractConfig.COMMON.quantifiedSoundScoreSliceCacheMaxEntries.get();
    builder = persistent
        ? builder.persistentSliceCache("soundattract_sound_score_slice", QuantifiedWorkScheduler::soundScoreCacheKey, QuantifiedWorkScheduler::encodeSoundScoreResult, QuantifiedWorkScheduler::decodeSoundScoreResult, ttl, maxEntries, false)
        : builder.memorySliceCache("soundattract_sound_score_slice", QuantifiedWorkScheduler::soundScoreCacheKey, QuantifiedWorkScheduler::encodeSoundScoreResult, QuantifiedWorkScheduler::decodeSoundScoreResult, ttl, maxEntries);
}
```

Also set a stable batch/affinity key on Quantified task submission paths where possible, such as `"soundattract_sound_score"` and `"soundattract_group_compute"`.

- [ ] **Step 5: Run targeted tests**

Run: `./gradlew.bat test --tests com.example.soundattract.worker.SoundScoreCacheKeyTest`

Expected: PASS.

- [ ] **Step 6: Run compile verification**

Run: `./gradlew.bat classes`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit the sound-score slice cache work**

```bash
git add src/main/java/com/example/soundattract/quantified/QuantifiedWorkScheduler.java src/main/java/com/example/soundattract/config/separate/IntegrationConfig.java src/main/java/com/example/soundattract/config/SoundAttractConfig.java src/main/java/com/example/soundattract/config/separate/GeneralConfig.java src/test/java/com/example/soundattract/worker/SoundScoreCacheKeyTest.java
git commit -m "feat: add optional quantified slice cache for sound scoring"
```

## Chunk 4: Clean up cache compat and verify with profiling

### Task 4: Reuse the bridge in `QuantifiedCacheCompat` and verify profiler impact

**Files:**
- Modify: `src/main/java/com/example/soundattract/quantified/QuantifiedCacheCompat.java:1-227`
- Modify: `src/main/java/com/example/soundattract/tracking/SoundTracker.java:522-559`
- Optional Modify: `src/main/java/com/example/soundattract/quantified/QuantifiedWorkScheduler.java:119-138`
- Test: `src/test/java/com/example/soundattract/quantified/QuantifiedOptionalBridgeTest.java`

- [ ] **Step 1: Write a failing test for cache fallback behavior**

```java
@Test
void cacheLookupShouldFallBackToLoaderWhenBridgeIsUnavailable() {
    assertEquals("loader", "replace-with-cache-fallback-value");
}
```

- [ ] **Step 2: Replace `QuantifiedCacheCompat.CacheBridge` ad-hoc reflection with the shared bridge or a shared handle cache**

Implementation requirements:
- Keep `QuantifiedCacheCompat.isUsable()` semantics unchanged.
- Avoid repeated `Method.invoke` on `getCached`, `setMemoryLimitMB`, `isMemoryPressureHigh`, and `triggerMemoryPressureCleanup`.
- Preserve all fallback behavior when Quantified is missing or fails.

- [ ] **Step 3: Reduce repeated hot-path setup in `SoundTracker.applyBlockMuffling()` if safe**

Examples:
- Keep the current `QuantifiedCacheCompat.isUsable()` guard, but avoid rebuilding work that can be shared between Quantified and non-Quantified paths.
- Consider extracting the cache-key builder into a helper so the same key logic can be unit-tested and reused.

- [ ] **Step 4: Run all tests and compile checks**

Run: `./gradlew.bat test`

Expected: PASS.

Run: `./gradlew.bat classes`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Run a manual profiling verification pass**

Run the mod in the same scenario you used for Spark and verify:
- `IllegalStateException.<init>` / `Throwable.fillInStackTrace()` is absent from the `AttractionGoal` hot path.
- `java.lang.reflect.Method.invoke()` is no longer prominent in `QuantifiedWorkScheduler$ParallelComputeBridge.trySubmitSoundScores()`.
- `java.lang.reflect.Method.invoke()` is no longer prominent in `QuantifiedCacheCompat` and `AsyncManager$QuantifiedAsyncBridge` hot paths.
- Slice-cache hit rate is high enough to matter; if not, adjust cache-key granularity before merging.

Suggested commands:
- Build: `./gradlew.bat classes`
- Launch your normal profiling setup.
- Capture a new Spark profile under identical mob-load conditions.

Expected: lower self/total time in sound scoring and Quantified cache bridge paths, with no functional regression when Quantified is disabled.

- [ ] **Step 6: Commit the cache compat cleanup**

```bash
git add src/main/java/com/example/soundattract/quantified/QuantifiedCacheCompat.java src/main/java/com/example/soundattract/tracking/SoundTracker.java src/main/java/com/example/soundattract/quantified/QuantifiedWorkScheduler.java src/test/java/com/example/soundattract/quantified/QuantifiedOptionalBridgeTest.java
git commit -m "perf: reduce quantified cache bridge overhead"
```

## Chunk 5: Post-implementation acceptance checklist

### Task 5: Validate optionality and regression safety

**Files:**
- Modify if needed: `src/main/java/com/example/soundattract/worker/WorkSchedulerManager.java:32-49`
- Modify if needed: `src/main/java/com/example/soundattract/async/AsyncManager.java:47-68`
- Modify if needed: `src/main/java/com/example/soundattract/quantified/QuantifiedIntegration.java:16-37`

- [ ] **Step 1: Test with Quantified enabled**

Run: `./gradlew.bat classes`

Expected: BUILD SUCCESSFUL and Quantified-backed scheduler still initializes.

- [ ] **Step 2: Test with Quantified disabled in config**

Set `integration.enableQuantifiedIntegration = false` and run the same workload.

Expected:
- No crashes.
- `WorkSchedulerManager` falls back to `LocalWorkScheduler`.
- `AsyncManager` falls back to `FALLBACK_EXECUTOR`.
- `QuantifiedCacheCompat` falls back to executing the loader directly.

- [ ] **Step 3: Test with Quantified mod absent from the runtime**

Expected:
- No classloading failures.
- All optional bridges report unavailable and return `null`/`false` cleanly.

- [ ] **Step 4: Final commit or follow-up fixes**

```bash
git add .
git commit -m "perf: complete optional quantified performance follow-ups"
```

## Recommended execution order

1. Add test support.
2. Centralize Quantified interop.
3. Add sound-score slice caching.
4. Clean up `QuantifiedCacheCompat`.
5. Re-profile with Spark before any further optimization work.

## Notes from local source review

The local Quantified API source shows the following optional capabilities that are worth exploiting, in this order:
- `ParallelCompute.Builder.memorySliceCache(...)` / `persistentSliceCache(...)` for deterministic per-slice reuse.
- `QuantifiedTask.Builder.batchKey(...)` plus thread-safety/priority metadata for better scheduler affinity.
- `QuantifiedAPI.getCached(...)` / `getCachedAsync(...)` behind a cheaper bridge.
- `QuantifiedAPI.hybrid(...)` for future work if a stable expensive computation with a natural cache key appears.

The best next optimization is **slice caching in sound scoring**, because it removes repeated work rather than only reducing dispatch overhead.

Plan complete and saved to `docs/superpowers/plans/2026-04-06-quantified-performance-followups.md`. Ready to execute?
