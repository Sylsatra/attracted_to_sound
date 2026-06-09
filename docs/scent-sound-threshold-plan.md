# Scent vs Sound Weight-Threshold System — Implementation Plan

## Overview

Replace the current all-or-nothing `scentOverrideSoundPriority` check with a hybrid weight-threshold system so fresh scent trails only override **low-weight** sounds, while high-weight sounds (explosions, gunshots, etc.) still distract the mob. Includes caching, NPE/race/memory-leak guards.

## Goals

1. **Correctness**: Low-weight sounds (arrow landing, door) lose to scent; high-weight sounds still win.
2. **Performance**: Cache scent query results per-mob with TTL to avoid redundant chunk scans every tick.
3. **Safety**: Null-safe throughout, thread-safe cache, bounded memory.
4. **Backward compatible**: Gate new logic behind existing `scentOverrideSoundPriority` flag.

---

## Phase 1 — Config Additions

### `ScentConfig.java` (add in `arrow_scent` push block)

```java
SCENT_OVERRIDE_SOUND_WEIGHT_THRESHOLD = BUILDER
    .comment("When scentOverrideSoundPriority=true: sounds with weight below this value always lose to fresh scent. Sounds at/above this threshold compete with scent strength.")
    .defineInRange("scentOverrideSoundWeightThreshold", 10.0, 0.0, 1000.0);

SCENT_VS_SOUND_HYBRID_MULTIPLIER = BUILDER
    .comment("Above the threshold: soundWeight is compared against (scentStrength * multiplier). Higher = scent wins more often.")
    .defineInRange("scentVsSoundHybridMultiplier", 1.0, 0.0, 100.0);

SCENT_QUERY_CACHE_TTL_TICKS = BUILDER
    .comment("How long scent-query results are cached per mob (ticks). Lower = fresher but slower.")
    .defineInRange("scentQueryCacheTtlTicks", 5, 1, 200);

SCENT_QUERY_CACHE_MAX_ENTRIES = BUILDER
    .comment("Max mobs tracked in the scent-query cache. Hard memory cap.")
    .defineInRange("scentQueryCacheMaxEntries", 4096, 128, 65536);
```

### `SoundAttractConfig.java` Common class (mirror the 4 new keys)

```java
public final ForgeConfigSpec.DoubleValue scentOverrideSoundWeightThreshold = ScentConfig.SCENT_OVERRIDE_SOUND_WEIGHT_THRESHOLD;
public final ForgeConfigSpec.DoubleValue scentVsSoundHybridMultiplier      = ScentConfig.SCENT_VS_SOUND_HYBRID_MULTIPLIER;
public final ForgeConfigSpec.IntValue    scentQueryCacheTtlTicks           = ScentConfig.SCENT_QUERY_CACHE_TTL_TICKS;
public final ForgeConfigSpec.IntValue    scentQueryCacheMaxEntries         = ScentConfig.SCENT_QUERY_CACHE_MAX_ENTRIES;
```

---

## Phase 2 — New Class: `ScentQueryCache`

**Location**: `src/main/java/com/example/soundattract/util/ScentQueryCache.java`

### Responsibility
Thread-safe, bounded, TTL-based cache for per-mob scent queries.

### Shape
```java
public final class ScentQueryCache {
    public record Result(long tickStored, boolean hasFresh, double strongestStrength) {}

    private static final ConcurrentHashMap<UUID, Result> CACHE = new ConcurrentHashMap<>();

    public static Result get(Mob mob, int maxAgeTicks, double radius);
    public static void invalidate(UUID mobId);
    public static void cleanup(long nowTick, int ttl, int maxEntries);
    public static void clear();
}
```

### Logic
- `get()`: look up `CACHE.get(mobId)`. If present and `(now - tickStored) <= ttl`, return cached.
- Otherwise compute via single-pass scan (see Phase 3), `CACHE.put()`, return.
- `cleanup()`: iterate entries; remove expired; if still over `maxEntries`, drop oldest (LRU by `tickStored`).

### Memory safety
- Hard cap via `scentQueryCacheMaxEntries`.
- `cleanup()` invoked every 200 server ticks (10 s) via `TickEvent.ServerTickEvent` in existing handler or new one.
- `invalidate()` called from entity-leave-world event.

---

## Phase 3 — Enhance `ScentQueryHelper`

### New combined method
```java
public static ScentQueryCache.Result computeFreshScentInfo(Mob mob, int maxAgeTicks, double radius);
```

Single-pass scan over chunk scent nodes. Returns:
- `hasFresh` — any node in radius and age
- `strongestStrength` — max strength among matching nodes (0.0 if none)

### Existing method
`hasFreshScentNear()` stays as a thin wrapper:
```java
return ScentQueryCache.get(mob, maxAge, radius).hasFresh();
```

### NPE guards (checklist)
- `mob == null` → return empty
- `mob.level() == null` → return empty
- `mob.level().isClientSide` → return empty
- `SoundAttractConfig.COMMON == null` → return empty
- `ScentManager` capability absent → return empty
- Each `ScentNode` null-check, position null-check
- Catch `Throwable` in cache compute, log once, return empty

---

## Phase 4 — Modify `AttractionGoal.canUse()`

### Replace lines 91–94:

```@c:\Users\haihb\Desktop\modding\attract_to_sound\1.20.1-4.1.4\src\main\java\com\example\soundattract\ai\AttractionGoal.java:91-94
if (SoundAttractConfig.COMMON.scentOverrideSoundPriority.get()
        && ScentQueryHelper.hasFreshScentNear(this.mob, 600, SoundAttractConfig.COMMON.scentDetectionRadius.get().doubleValue())) {
    return false;
}
```

### With threshold logic (applied AFTER `findInterestingSoundRecord()` so we have `newSound.weight`):

```java
if (SoundAttractConfig.COMMON.scentOverrideSoundPriority.get()) {
    int maxAge = SoundAttractConfig.COMMON.arrowScentNodeDurationTicks.get();
    double radius = SoundAttractConfig.COMMON.scentDetectionRadius.get();
    ScentQueryCache.Result scent = ScentQueryCache.get(this.mob, maxAge, radius);

    if (scent.hasFresh()) {
        double threshold = SoundAttractConfig.COMMON.scentOverrideSoundWeightThreshold.get();
        double multiplier = SoundAttractConfig.COMMON.scentVsSoundHybridMultiplier.get();

        if (newSound.weight < threshold) {
            return false;                           // scent wins (hard floor)
        }
        if (newSound.weight < scent.strongestStrength() * multiplier) {
            return false;                           // scent wins (hybrid)
        }
        // else: sound weight beats scent → fall through, use sound
    }
}
```

### Ordering caveat
The current early-return at line 91 happens **before** sound scan. New logic must run **after** `findInterestingSoundRecord()` so the sound weight is known. Move the scent check below line 121 (`newSound == null` check).

---

## Phase 5 — Cache Cleanup Lifecycle

### Periodic cleanup
Hook into existing `SoundAttractMod` server tick event or create one:

```java
@SubscribeEvent
public void onServerTickCleanup(TickEvent.ServerTickEvent e) {
    if (e.phase != TickEvent.Phase.END) return;
    long now = serverLevel.getGameTime();
    if (now % 200L != 0L) return;                   // every 10s
    ScentQueryCache.cleanup(now,
        SoundAttractConfig.COMMON.scentQueryCacheTtlTicks.get(),
        SoundAttractConfig.COMMON.scentQueryCacheMaxEntries.get());
}
```

### Entity-leave hook
```java
@SubscribeEvent
public void onEntityLeave(EntityLeaveLevelEvent e) {
    if (e.getEntity() instanceof Mob m) {
        ScentQueryCache.invalidate(m.getUUID());
    }
}
```

### Server stop
Clear entire cache in `FMLServerStoppingEvent` or `ServerStoppedEvent`.

---

## Phase 6 — Concurrency Analysis

| Concern | Mitigation |
|---|---|
| Cache concurrent read/write | `ConcurrentHashMap` |
| Scent node list iteration | Existing `ScentManager` uses `ConcurrentLinkedQueue` already |
| Config reads across threads | Already safe via `ForgeConfigSpec.get()` |
| Cache compute races (two threads compute same mob) | Use `computeIfAbsent` - idempotent, safe |
| Cleanup racing with get() | Cleanup removes expired entries; if a `get()` races, it just recomputes. No corruption. |

---

## Phase 7 — Memory-Leak Prevention Checklist

- [x] Bounded cache (`scentQueryCacheMaxEntries`)
- [x] TTL expiration (`scentQueryCacheTtlTicks`)
- [x] Periodic sweep every 200 ticks
- [x] Entity-leave invalidation
- [x] Server-stop full clear
- [x] No strong references to `Mob` entities (key by UUID only)
- [x] `Result` record is immutable — no mutation races

---

## Phase 8 — NPE Guards Checklist

In `ScentQueryCache.get()` / `ScentQueryHelper.computeFreshScentInfo()`:
- [x] null mob
- [x] null level
- [x] client-side check
- [x] null `SoundAttractConfig.COMMON`
- [x] capability absent
- [x] null node list
- [x] null node position
- [x] try/catch around whole compute with warn-once logger

In `AttractionGoal.canUse()`:
- [x] null `scent` result → treat as no-scent
- [x] null `newSound` already handled

---

## Phase 9 — Testing Plan

### Unit scenarios (manual, in-game)
1. **Low-weight sound + scent** — shoot arrow at wall near scent trail → mob follows scent, not hit-point. ✓
2. **High-weight sound + scent** — TNT near scent trail → mob investigates TNT. ✓
3. **No scent** — sounds behave normally. ✓
4. **Scent but flag off** — `scentOverrideSoundPriority=false` → current behavior, sound wins. ✓
5. **Threshold edge** — sound at weight exactly = threshold → sound wins (strict `<` check). ✓
6. **Multiplier tuning** — set multiplier=0 → only hard threshold applies. ✓

### Performance verification
- Log cache hit rate via existing debug logging.
- Target: >80% hit rate at default TTL=5 with 20+ mobs.

### Memory verification
- Spawn 5000 mobs, stop server, verify cache cleared.
- Run overnight with active mobs, check cache size stays < max.

---

## Phase 10 — Rollback Plan

All changes are additive or gated behind existing `scentOverrideSoundPriority`. To roll back:
1. Set `scentOverrideSoundWeightThreshold=0` and `scentVsSoundHybridMultiplier=0` → sound always wins over scent (below threshold of 0 is impossible).
2. Or set `scentOverrideSoundPriority=false` → current behavior restored entirely.

---

## File Change Summary

| File | Change |
|---|---|
| `config/separate/ScentConfig.java` | Add 4 config keys |
| `config/SoundAttractConfig.java` | Mirror 4 keys in Common |
| `util/ScentQueryCache.java` | **NEW** class |
| `util/ScentQueryHelper.java` | Add `computeFreshScentInfo()`, refactor `hasFreshScentNear()` to use cache |
| `ai/AttractionGoal.java` | Replace lines 91-94; move logic after sound scan |
| `SoundAttractMod.java` (or existing tick handler) | Add cache cleanup tick + entity-leave hook |

---

## Implementation Order

1. Config keys (Phase 1) — compile check
2. `ScentQueryCache` class (Phase 2)
3. `ScentQueryHelper` enhancement (Phase 3)
4. Cache cleanup hooks (Phase 5)
5. `AttractionGoal` modification (Phase 4)
6. Build + smoke test
7. In-game verification (Phase 9)

Each step compiles independently; break at any point if issues surface.
