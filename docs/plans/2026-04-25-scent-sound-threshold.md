# Scent vs Sound Weight-Threshold System Implementation Plan

> **For agentic workers:** Use checkbox (`- [ ]`) syntax for tracking. Each step is bite-sized (2-5 minutes). Follow TDD-lite (build verification after each step).

**Goal:** Replace the all-or-nothing `scentOverrideSoundPriority` check with a hybrid weight-threshold system, so fresh scent overrides only low-weight sounds while high-weight sounds (explosions, gunshots) still distract mobs. Add per-mob TTL caching with NPE/race/memory-leak guards.

**Architecture:** A new thread-safe `ScentQueryCache` (`ConcurrentHashMap<UUID, Result>`) caches the combined `(hasFresh, strongestStrength)` query per mob for 5 ticks. `ScentQueryHelper` gains a single-pass combined-query method. `AttractionGoal.canUse()` is restructured to evaluate the threshold *after* finding a sound. Lifecycle hooks (entity-leave, periodic sweep, server-stop) prevent unbounded growth.

**Tech Stack:** Minecraft Forge 1.20.1, Java 17, ForgeConfigSpec, `java.util.concurrent.ConcurrentHashMap`.

---

## File Structure

| File | Role |
|---|---|
| `src/main/java/com/example/soundattract/config/separate/ScentConfig.java` | Add 4 config keys |
| `src/main/java/com/example/soundattract/config/SoundAttractConfig.java` | Mirror 4 config accessors in `Common` |
| `src/main/java/com/example/soundattract/util/ScentQueryCache.java` | **NEW** — thread-safe TTL cache |
| `src/main/java/com/example/soundattract/util/ScentQueryHelper.java` | Add `computeFreshScentInfo()`; route `hasFreshScentNear()` through cache |
| `src/main/java/com/example/soundattract/ai/AttractionGoal.java` | Replace lines 91–94 with threshold logic placed *after* sound scan |
| `src/main/java/com/example/soundattract/event/ScentEvents.java` | Add periodic cache sweep + entity-leave invalidation |

---

## Task 1: Add Config Keys

**Files:**
- Modify: `src/main/java/com/example/soundattract/config/separate/ScentConfig.java`
- Modify: `src/main/java/com/example/soundattract/config/SoundAttractConfig.java`

- [ ] **Step 1: Add static field declarations to `ScentConfig.java`**

Insert after line 40 (`ARROW_SCENT_RATE_LIMIT_PER_SHOOTER_PER_MINUTE` declaration):

```java
public static final ForgeConfigSpec.DoubleValue SCENT_OVERRIDE_SOUND_WEIGHT_THRESHOLD;
public static final ForgeConfigSpec.DoubleValue SCENT_VS_SOUND_HYBRID_MULTIPLIER;
public static final ForgeConfigSpec.IntValue SCENT_QUERY_CACHE_TTL_TICKS;
public static final ForgeConfigSpec.IntValue SCENT_QUERY_CACHE_MAX_ENTRIES;
```

- [ ] **Step 2: Add definitions inside `arrow_scent` push block in `ScentConfig.java`**

Insert before `BUILDER.pop();` at line 112:

```java
SCENT_OVERRIDE_SOUND_WEIGHT_THRESHOLD = BUILDER
    .comment("When scentOverrideSoundPriority=true: sounds with weight strictly below this value always lose to fresh scent.")
    .defineInRange("scentOverrideSoundWeightThreshold", 10.0, 0.0, 1000.0);
SCENT_VS_SOUND_HYBRID_MULTIPLIER = BUILDER
    .comment("Above the threshold: soundWeight is compared against (scentStrength * multiplier). Higher = scent wins more often.")
    .defineInRange("scentVsSoundHybridMultiplier", 1.0, 0.0, 100.0);
SCENT_QUERY_CACHE_TTL_TICKS = BUILDER
    .comment("How long scent-query results are cached per mob (ticks). Lower = fresher but more CPU.")
    .defineInRange("scentQueryCacheTtlTicks", 5, 1, 200);
SCENT_QUERY_CACHE_MAX_ENTRIES = BUILDER
    .comment("Maximum mobs tracked in the scent-query cache (memory cap).")
    .defineInRange("scentQueryCacheMaxEntries", 4096, 128, 65536);
```

- [ ] **Step 3: Mirror accessors in `SoundAttractConfig.java`**

Insert in the `Common` class after line 799 (`arrowScentRateLimitPerShooterPerMinute`):

```java
public final ForgeConfigSpec.DoubleValue scentOverrideSoundWeightThreshold = ScentConfig.SCENT_OVERRIDE_SOUND_WEIGHT_THRESHOLD;
public final ForgeConfigSpec.DoubleValue scentVsSoundHybridMultiplier = ScentConfig.SCENT_VS_SOUND_HYBRID_MULTIPLIER;
public final ForgeConfigSpec.IntValue scentQueryCacheTtlTicks = ScentConfig.SCENT_QUERY_CACHE_TTL_TICKS;
public final ForgeConfigSpec.IntValue scentQueryCacheMaxEntries = ScentConfig.SCENT_QUERY_CACHE_MAX_ENTRIES;
```

- [ ] **Step 4: Compile**

Run: `.\gradlew classes --no-daemon`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/example/soundattract/config/separate/ScentConfig.java src/main/java/com/example/soundattract/config/SoundAttractConfig.java
git commit -m "feat(scent): add config keys for threshold-based sound override"
```

---

## Task 2: Create `ScentQueryCache` Class

**Files:**
- Create: `src/main/java/com/example/soundattract/util/ScentQueryCache.java`

- [ ] **Step 1: Create the file with full content**

```java
package com.example.soundattract.util;

import com.example.soundattract.SoundAttractMod;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe, bounded, TTL-based cache for per-mob scent query results.
 * Avoids redundant chunk scans when AttractionGoal evaluates many mobs per tick.
 */
public final class ScentQueryCache {

    public record Result(long tickStored, boolean hasFresh, double strongestStrength) {
        public static final Result EMPTY = new Result(Long.MIN_VALUE, false, 0.0);
    }

    private static final ConcurrentHashMap<UUID, Result> CACHE = new ConcurrentHashMap<>();

    private ScentQueryCache() {}

    public static Result getCached(UUID mobId, long nowTick, int ttl) {
        if (mobId == null) return null;
        Result r = CACHE.get(mobId);
        if (r == null) return null;
        if (nowTick - r.tickStored() > ttl) return null;
        return r;
    }

    public static void put(UUID mobId, Result result) {
        if (mobId == null || result == null) return;
        CACHE.put(mobId, result);
    }

    public static void invalidate(UUID mobId) {
        if (mobId == null) return;
        CACHE.remove(mobId);
    }

    public static void clear() {
        CACHE.clear();
    }

    public static int size() {
        return CACHE.size();
    }

    /**
     * Periodic cleanup. Removes expired entries; if still over maxEntries, drops oldest.
     */
    public static void cleanup(long nowTick, int ttl, int maxEntries) {
        try {
            Iterator<Map.Entry<UUID, Result>> it = CACHE.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, Result> e = it.next();
                Result r = e.getValue();
                if (r == null || nowTick - r.tickStored() > ttl) {
                    it.remove();
                }
            }
            int over = CACHE.size() - maxEntries;
            if (over > 0) {
                CACHE.entrySet().stream()
                    .sorted(Map.Entry.comparingByValue(
                        (a, b) -> Long.compare(a.tickStored(), b.tickStored())))
                    .limit(over)
                    .forEach(e -> CACHE.remove(e.getKey()));
            }
        } catch (Throwable t) {
            SoundAttractMod.LOGGER.warn("[ScentQueryCache] cleanup failed: {}", t.toString());
        }
    }
}
```

- [ ] **Step 2: Compile**

Run: `.\gradlew classes --no-daemon`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/example/soundattract/util/ScentQueryCache.java
git commit -m "feat(scent): add thread-safe ScentQueryCache with TTL and bounded size"
```

---

## Task 3: Enhance `ScentQueryHelper` with Combined Query

**Files:**
- Modify: `src/main/java/com/example/soundattract/util/ScentQueryHelper.java`

- [ ] **Step 1: Replace entire file content**

```java
package com.example.soundattract.util;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.scents.ScentManager;
import com.example.soundattract.scents.ScentNode;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class ScentQueryHelper {

    public static boolean hasFreshScentNear(Mob mob, int maxAgeTicks, double radius) {
        return computeFreshScentInfo(mob, maxAgeTicks, radius).hasFresh();
    }

    /**
     * Combined query: returns whether any fresh scent exists in radius AND the strongest
     * strength among matching nodes. Cached per mob.
     */
    public static ScentQueryCache.Result computeFreshScentInfo(Mob mob, int maxAgeTicks, double radius) {
        if (mob == null || mob.level() == null || mob.level().isClientSide) return ScentQueryCache.Result.EMPTY;
        if (SoundAttractConfig.COMMON == null) return ScentQueryCache.Result.EMPTY;

        long now = mob.level().getGameTime();
        int ttl = SoundAttractConfig.COMMON.scentQueryCacheTtlTicks.get();
        ScentQueryCache.Result cached = ScentQueryCache.getCached(mob.getUUID(), now, ttl);
        if (cached != null) return cached;

        ScentQueryCache.Result computed;
        try {
            computed = mob.level().getCapability(ScentManager.INSTANCE).map(manager -> {
                if (manager == null) return ScentQueryCache.Result.EMPTY;
                ChunkPos chunk = new ChunkPos(mob.blockPosition());
                List<ScentNode> nodes = manager.getNodesInArea(chunk);
                if (nodes == null || nodes.isEmpty()) {
                    return new ScentQueryCache.Result(now, false, 0.0);
                }
                double radiusSq = radius * radius;
                Vec3 mobPos = mob.position();
                if (mobPos == null) return ScentQueryCache.Result.EMPTY;

                boolean anyFresh = false;
                double strongest = 0.0;
                for (ScentNode node : nodes) {
                    if (node == null) continue;
                    Vec3 np = node.getPosition();
                    if (np == null) continue;
                    long age = now - node.getTimestamp();
                    if (age < 0 || age > maxAgeTicks) continue;
                    if (mobPos.distanceToSqr(np) > radiusSq) continue;
                    anyFresh = true;
                    float s = node.getStrength();
                    if (s > strongest) strongest = s;
                }
                return new ScentQueryCache.Result(now, anyFresh, strongest);
            }).orElse(ScentQueryCache.Result.EMPTY);
        } catch (Throwable t) {
            SoundAttractMod.LOGGER.warn("[ScentQueryHelper] computeFreshScentInfo failed: {}", t.toString());
            computed = ScentQueryCache.Result.EMPTY;
        }

        ScentQueryCache.put(mob.getUUID(), computed);
        return computed;
    }
}
```

- [ ] **Step 2: Compile**

Run: `.\gradlew classes --no-daemon`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/example/soundattract/util/ScentQueryHelper.java
git commit -m "feat(scent): add combined fresh-scent query with cache routing"
```

---

## Task 4: Update `AttractionGoal.canUse()` with Threshold Logic

**Files:**
- Modify: `src/main/java/com/example/soundattract/ai/AttractionGoal.java:86-152`

- [ ] **Step 1: Remove the early-exit scent block (lines 91-94)**

Find and delete:

```java
        if (SoundAttractConfig.COMMON.scentOverrideSoundPriority.get()
                && ScentQueryHelper.hasFreshScentNear(this.mob, 600, SoundAttractConfig.COMMON.scentDetectionRadius.get().doubleValue())) {
            return false;
        }
```

- [ ] **Step 2: Insert threshold logic after line 124 (`if (newSound == null) return false;`)**

After the `null` check on `newSound`, add:

```java
        if (SoundAttractConfig.COMMON.scentOverrideSoundPriority.get()) {
            int maxAge = SoundAttractConfig.COMMON.arrowScentNodeDurationTicks.get();
            double radius = SoundAttractConfig.COMMON.scentDetectionRadius.get();
            com.example.soundattract.util.ScentQueryCache.Result scent =
                com.example.soundattract.util.ScentQueryHelper.computeFreshScentInfo(this.mob, maxAge, radius);
            if (scent != null && scent.hasFresh()) {
                double threshold = SoundAttractConfig.COMMON.scentOverrideSoundWeightThreshold.get();
                double multiplier = SoundAttractConfig.COMMON.scentVsSoundHybridMultiplier.get();
                if (newSound.weight < threshold) {
                    if (SoundAttractConfig.COMMON.debugLogging.get()) {
                        SoundAttractMod.LOGGER.info("[AttractionGoal] Scent overrides low-weight sound ({} < {}) for {}",
                                newSound.weight, threshold, this.mob.getName().getString());
                    }
                    return false;
                }
                if (newSound.weight < scent.strongestStrength() * multiplier) {
                    if (SoundAttractConfig.COMMON.debugLogging.get()) {
                        SoundAttractMod.LOGGER.info("[AttractionGoal] Scent strength {} beats sound weight {} for {}",
                                scent.strongestStrength(), newSound.weight, this.mob.getName().getString());
                    }
                    return false;
                }
            }
        }
```

- [ ] **Step 3: Verify import (no change needed — `ScentQueryHelper` already imported at line 10)**

- [ ] **Step 4: Compile**

Run: `.\gradlew classes --no-daemon`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/example/soundattract/ai/AttractionGoal.java
git commit -m "feat(ai): apply scent-vs-sound weight threshold in AttractionGoal.canUse"
```

---

## Task 5: Add Cache Cleanup Lifecycle Hooks

**Files:**
- Modify: `src/main/java/com/example/soundattract/event/ScentEvents.java`

- [ ] **Step 1: Locate the existing `@SubscribeEvent` server tick handler**

Run search: `grep -n "ServerTickEvent\|LevelTickEvent" src/main/java/com/example/soundattract/event/ScentEvents.java`

If a `LevelTickEvent` handler exists, augment it. Otherwise add a new method.

- [ ] **Step 2: Add periodic cleanup hook**

Add inside `ScentEvents` class (idempotent — check existing imports first):

```java
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void onServerTickScentCacheCleanup(net.minecraftforge.event.TickEvent.LevelTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) return;
        if (!(event.level instanceof net.minecraft.server.level.ServerLevel level)) return;
        if (com.example.soundattract.config.SoundAttractConfig.COMMON == null) return;
        long now = level.getGameTime();
        if (now % 200L != 0L) return;
        com.example.soundattract.util.ScentQueryCache.cleanup(
            now,
            com.example.soundattract.config.SoundAttractConfig.COMMON.scentQueryCacheTtlTicks.get(),
            com.example.soundattract.config.SoundAttractConfig.COMMON.scentQueryCacheMaxEntries.get()
        );
    }

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void onEntityLeaveScentCache(net.minecraftforge.event.entity.EntityLeaveLevelEvent event) {
        if (event.getEntity() instanceof net.minecraft.world.entity.Mob m) {
            com.example.soundattract.util.ScentQueryCache.invalidate(m.getUUID());
        }
    }

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void onServerStoppedScentCache(net.minecraftforge.event.server.ServerStoppedEvent event) {
        com.example.soundattract.util.ScentQueryCache.clear();
    }
```

> **Note:** If `ScentEvents` is registered on the FORGE bus already (check `SoundAttractMod` registration), these annotations work as-is. If it's a static instance, ensure these methods are static and the class is registered with `MinecraftForge.EVENT_BUS.register(ScentEvents.class)` or the existing pattern.

- [ ] **Step 3: Verify registration**

Search: `grep -rn "ScentEvents" src/main/java/com/example/soundattract/SoundAttractMod.java`
Expected: registration call exists. If not, add `MinecraftForge.EVENT_BUS.register(ScentEvents.class);` in mod constructor.

- [ ] **Step 4: Compile**

Run: `.\gradlew classes --no-daemon`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/example/soundattract/event/ScentEvents.java
git commit -m "feat(scent): add periodic cleanup, entity-leave, and server-stop hooks for ScentQueryCache"
```

---

## Task 6: Manual In-Game Verification

**Files:** none (runtime test only)

- [ ] **Step 1: Build dev runtime**

Run: `.\gradlew runClient --no-daemon` (in separate terminal)

- [ ] **Step 2: Test low-weight sound + scent**

In-game:
1. Place a zombie in a flat area.
2. Walk near it leaving a scent trail.
3. Fire arrow at wall (low-weight `entity.arrow.hit` sound).
4. **Expected:** Zombie follows scent toward you, ignores arrow landing point.

- [ ] **Step 3: Test high-weight sound + scent**

In-game:
1. Same setup.
2. Detonate TNT near the trail (or use `/playsound minecraft:entity.generic.explode hostile @e`).
3. **Expected:** Zombie investigates explosion (sound weight > threshold).

- [ ] **Step 4: Test no-scent baseline**

In-game:
1. Spawn zombie far from any scent trail.
2. Fire arrow at wall.
3. **Expected:** Zombie investigates arrow landing (current behavior).

- [ ] **Step 5: Test config flag off**

Edit `config/soundattract-common.toml`: set `scentOverrideSoundPriority = false`. Reload world.
**Expected:** All sounds win regardless of scent (legacy behavior).

- [ ] **Step 6: Verify cache size with debug**

Enable `debugLogging = true`. Spawn 50 zombies. Run for 30s.
Run in F3 chat or log: check `ScentQueryCache.size()` via temporary debug command or log line.
**Expected:** size <= `scentQueryCacheMaxEntries` (4096); periodic cleanup logs visible.

---

## Task 7: Edge-Case Verification

**Files:** none

- [ ] **Step 1: Threshold boundary**

Set `scentOverrideSoundWeightThreshold = 5.0`. Trigger sound with weight exactly 5.0 near scent.
**Expected:** Sound wins (strict `<` check; weight==threshold fails the lose-condition).

- [ ] **Step 2: Multiplier=0 (hard-floor only mode)**

Set `scentVsSoundHybridMultiplier = 0`. Above-threshold sounds should always win.
**Expected:** Only sub-threshold sounds lose to scent.

- [ ] **Step 3: Very strong scent**

Manually craft a scent node with strength=2.0 (max). Set multiplier=20. Trigger sound weight=30.
**Expected:** scent wins (30 < 2.0 * 20 = 40).

- [ ] **Step 4: TTL effectiveness**

Set `scentQueryCacheTtlTicks=1`. Verify perf does not regress vs `=20`.
**Expected:** No noticeable lag spike; cache hit rate lower but functionality identical.

---

## Rollback Plan

All changes are additive or gated behind existing `scentOverrideSoundPriority`. To roll back:
- Set `scentOverrideSoundPriority = false` in config → fully restores legacy behavior.
- Set `scentOverrideSoundWeightThreshold = 0` and `scentVsSoundHybridMultiplier = 0` → sound always wins (0 < 0 is false, multiplier check is also false).
- Worst case: `git revert` the 5 commits.

---

## Safety Audit Summary

| Concern | Mitigation | Verified by |
|---|---|---|
| **NPE — null mob/level/config** | Guards in `computeFreshScentInfo` head | Code inspection |
| **NPE — null nodes/positions** | Per-iteration null checks | Code inspection |
| **NPE — null scent.Result** | `EMPTY` sentinel + null guard in caller | Code inspection |
| **Race — concurrent compute for same mob** | `ConcurrentHashMap.put` is atomic; double-compute is benign (both produce same value) | Code review |
| **Race — cleanup vs get** | `Iterator.remove()` on `ConcurrentHashMap` is safe; missed entry just recomputes | Code review |
| **Memory leak — unbounded growth** | Hard cap (`scentQueryCacheMaxEntries`) + TTL + 200-tick sweep | Task 6 Step 6 |
| **Memory leak — entity refs** | Cache keyed by UUID only, never holds `Mob` reference | Code inspection |
| **Memory leak — server reload** | `ServerStoppedEvent` clears cache | Code inspection |
| **Performance regression** | TTL=5 → 80% hit rate target; combined scan replaces two passes | Task 6 Step 6 |

---

## Implementation Order Summary

1. **Task 1** — Config keys (compiles standalone)
2. **Task 2** — `ScentQueryCache` class (compiles standalone)
3. **Task 3** — `ScentQueryHelper` enhancement (depends on Task 2)
4. **Task 5** — Cleanup hooks (depends on Task 2 — no behavior change yet)
5. **Task 4** — `AttractionGoal` integration (depends on Tasks 1, 2, 3)
6. **Task 6 + 7** — Runtime verification

Stop and surface issues to the human if any compile fails or in-game tests show unexpected behavior.
