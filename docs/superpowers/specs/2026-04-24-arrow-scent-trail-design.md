# Arrow Scent Trail — Design Spec

**Date:** 2026-04-24
**Status:** Approved (v3)
**Authors:** Cascade + user brainstorming
**Target version:** Minecraft 1.20.1 / Forge 47.4.x / mod 6.x

## Summary

Projectiles with a `LivingEntity` owner deposit scent nodes along their flight path and at the shooter's origin. Mobs that can follow scent (existing `FollowScentGoal`) use these trails to locate the shooter. Sound investigation and scent trails compose: near-miss and impact sounds (existing) draw mobs to the flight area; the new scent lets them trace back to the source.

## Goals

- Mobs should track shooters across long distances that simple "sound at shooter position" cannot cover.
- Both player-fired and mob-fired projectiles create scent — allowing inter-faction scent raids (e.g. a Spore mob shooting a skeleton attracts undead to the Spore).
- No new goals, new data layers, or new storage systems. Reuse `ScentNode`, `ScentManager`, `FollowScentGoal`.
- Hard-cap memory and CPU use even under worst-case dispenser spam.

## Non-Goals

- Per-projectile-type scent characteristics (snowballs vs arrows). Tag decides who emits; all emit with the same strength/duration rules.
- Multi-stage investigation behaviour ("walk to near-miss, then search"). Scent composition already produces that emergent behaviour.
- Client-side prediction of scent trails. Scent is server-authoritative; particles are pushed from server.

## Architecture

Two existing subsystems are extended:

1. **`ArrowInvestigationEvents.ARROWS`** — a `ConcurrentHashMap<Integer, ArrowState>` tracking every watched projectile from spawn to death. `ArrowState` gains `Vec3 lastScentPos` and `int scentNodesEmitted` to drive interval/cap decisions without a second tracking map.
2. **`ScentNode` + `ScentManager`** — already per-chunk, thread-safe, NBT-persistent. `ScentNode` gains a `sourceType` enum. `ScentManager.addScentNode` becomes a bounded operation that rejects emissions once a chunk reaches saturation.

A new `ArrowScentEvents` class owns the emission policy: when a projectile enters a world, it seeds an origin node; each server tick it walks flight distance and emits path nodes while respecting per-arrow caps and a global per-shooter rate.

A new `ScentQueryHelper` lets `AttractionGoal` check "does this mob have a fresh scent nearby?" so scent can preempt sound when configured.

### Component diagram

```
┌────────────────────────────────┐       ┌──────────────────────────┐
│ EntityJoinLevelEvent           │       │ TickEvent.LevelTickEvent │
│ (projectile spawn)             │       │                          │
└─────────────┬──────────────────┘       └──────────────┬───────────┘
              │                                          │
              ▼                                          ▼
┌────────────────────────────────┐       ┌──────────────────────────┐
│ ArrowInvestigationEvents       │       │ ArrowScentEvents         │
│  • put(id, ArrowState)         │       │  • iterate ARROWS        │
│                                │       │  • emit path nodes on    │
│ ArrowScentEvents               │       │    interval, honour cap  │
│  • seed ARROW_ORIGIN node      │       │    and global rate       │
└─────────────┬──────────────────┘       └──────────────┬───────────┘
              └──────────────────┬──────────────────────┘
                                 ▼
                   ┌───────────────────────────────┐
                   │ ScentManager.addScentNode     │
                   │  • reject if chunk saturated  │
                   │  • INFO log once per chunk    │
                   └───────────────┬───────────────┘
                                   ▼
                       ┌─────────────────────┐
                       │ ScentNode (stored)  │
                       │ sourceType, owner,  │
                       │ timestamp, strength │
                       └──────────┬──────────┘
                                  │
          ┌───────────────────────┼──────────────────────────┐
          ▼                       ▼                          ▼
┌──────────────────┐   ┌────────────────────┐   ┌──────────────────────┐
│ FollowScentGoal  │   │ ScentEvents        │   │ AttractionGoal       │
│  • per-type      │   │  • per-viewer      │   │  • defer if fresh    │
│    weight        │   │    visibility      │   │    scent near mob    │
│  • raid trigger  │   │    filter          │   │    (scent override)  │
└──────────────────┘   └────────────────────┘   └──────────────────────┘
```

## Data Model

### `ScentSourceType` enum

```java
public enum ScentSourceType {
    PLAYER_WALK,
    ARROW_ORIGIN,          // player-fired, at shooter position
    ARROW_PATH,            // player-fired, along flight path
    MOB_PROJECTILE_ORIGIN, // mob-fired, at shooter position
    MOB_PROJECTILE_PATH    // mob-fired, along flight path
}
```

- `ARROW_*` vs `MOB_PROJECTILE_*` is determined at emission time by `owner instanceof Player`.
- Enum is persisted by NBT as a string name. Missing key on load → defaults to `PLAYER_WALK` for backward compatibility with pre-feature save files.

### `ScentNode` additions

```java
public class ScentNode {
    private final Vec3 position;
    private final long timestamp;
    private final float strength;
    private final UUID ownerUUID;
    private final ScentSourceType sourceType;   // NEW

    public ScentNode(Vec3 position, long timestamp, float strength,
                     UUID ownerUUID, ScentSourceType sourceType) { ... }

    public ScentSourceType getSourceType() { return sourceType; }
}
```

NBT:
```java
tag.putString("SourceType", sourceType.name());
// on load:
ScentSourceType type = tag.contains("SourceType")
    ? ScentSourceType.valueOf(tag.getString("SourceType"))
    : ScentSourceType.PLAYER_WALK;
```

Existing single-argument callers (`ScentEvents.onPlayerTick`) pass `PLAYER_WALK`.

### `ArrowState` additions (in `ArrowInvestigationEvents`)

```java
static final class ArrowState {
    Vec3 prevPos;
    final Vec3 spawnPos;
    final long spawnTick;
    final Set<UUID> notifiedMobIds = ConcurrentHashMap.newKeySet();
    // NEW fields:
    Vec3 lastScentPos;        // initialised to spawnPos
    int scentNodesEmitted;    // incremented per emit
}
```

Visibility stays package-private, but `ArrowInvestigationEvents` gains a tiny read-only static getter so `ArrowScentEvents` can iterate:

```java
static Map<Integer, ArrowState> getArrowsView() { return ARROWS; }
```

### `ScentVisibilityConfig` record

```java
public record ScentVisibilityConfig(
    boolean seePlayerScent,
    boolean seeArrowScent,          // player-fired arrows
    boolean seeMobProjectileScent   // mob-fired
) {
    public static final Codec<ScentVisibilityConfig> CODEC = RecordCodecBuilder.create(i -> i.group(
        Codec.BOOL.optionalFieldOf("see_player_scent", true).forGetter(ScentVisibilityConfig::seePlayerScent),
        Codec.BOOL.optionalFieldOf("see_arrow_scent", true).forGetter(ScentVisibilityConfig::seeArrowScent),
        Codec.BOOL.optionalFieldOf("see_mob_projectile_scent", true).forGetter(ScentVisibilityConfig::seeMobProjectileScent)
    ).apply(i, ScentVisibilityConfig::new));
}
```

Added to `PlayerProfile2` as `Optional<ScentVisibilityConfig> scentVisibility`. Missing → all-true (backward compat; current behaviour preserved).

## Emission Algorithm

### Origin node (on projectile spawn)

In `ArrowScentEvents.onEntityJoin(EntityJoinLevelEvent event)`:

```
if event.level is client → return
if !COMMON.enableArrowScentTrail → return
if entity is not Projectile → return
if projectile type not in INVESTIGATE_PROJECTILES tag → return
if owner not instanceof LivingEntity → return   (handles dispensers)

shooter = owner.position()
groundY = level.getHeight(MOTION_BLOCKING_NO_LEAVES, shooter.x, shooter.z)
origin = Vec3(shooter.x, groundY, shooter.z)

if !globalRateLimiter.tryAcquire(owner.getUUID()) → log debug + return

type = (owner instanceof Player) ? ARROW_ORIGIN : MOB_PROJECTILE_ORIGIN
strength = COMMON.arrowScentOriginStrength * biomeDurationModifier(origin)

node = new ScentNode(origin, gameTime, strength, owner.getUUID(), type)
if !scentManager.addScentNode(node) → (saturation already logged by manager)
```

### Path nodes (per server tick)

In `ArrowScentEvents.onLevelTick(TickEvent.LevelTickEvent event)`, phase == END, level instanceof ServerLevel:

```
for each (id, state) in ArrowInvestigationEvents.getArrowsView():
    if state.scentNodesEmitted >= COMMON.arrowScentMaxNodesPerArrow → continue
    projectile = level.getEntity(id)
    if projectile is null or not alive or level mismatch → continue
    if owner not instanceof LivingEntity → continue

    curr = projectile.position()
    if curr.distanceToSqr(state.lastScentPos) < intervalSq → continue
    if !globalRateLimiter.tryAcquire(owner.getUUID()) → continue

    groundY = level.getHeight(MOTION_BLOCKING_NO_LEAVES, curr.x, curr.z)
    nodePos = Vec3(curr.x, groundY, curr.z)

    type = (owner instanceof Player) ? ARROW_PATH : MOB_PROJECTILE_PATH
    strength = COMMON.arrowScentPathStrength * biomeDurationModifier(nodePos)

    node = new ScentNode(nodePos, gameTime, strength, owner.getUUID(), type)
    added = scentManager.addScentNode(node)
    if added:
        state.scentNodesEmitted++
        state.lastScentPos = curr
```

`ArrowInvestigationEvents.onEntityLeave` and `onServerStopping` clear the `ARROWS` map; no explicit cleanup needed in `ArrowScentEvents`.

### Global rate limiter

```java
final class GlobalScentRateLimiter {
    private final Cache<UUID, WindowState> state = CacheBuilder.newBuilder()
        .expireAfterWrite(5, TimeUnit.MINUTES)
        .maximumSize(1024)
        .build();

    boolean tryAcquire(UUID shooterId) {
        int perWindow = SoundAttractConfig.COMMON.arrowScentGlobalRatePerShooter.get();
        long windowTicks = 100L;
        long now = gameTime();

        WindowState s = state.asMap().compute(shooterId, (k, v) -> {
            if (v == null || now - v.windowStart >= windowTicks) {
                return new WindowState(now, 1);
            }
            return (v.count < perWindow) ? new WindowState(v.windowStart, v.count + 1) : v;
        });

        return s.count <= perWindow && (now - s.windowStart) < windowTicks * 2;
    }

    record WindowState(long windowStart, int count) {}
}
```

Per-shooter sliding window. `compute` is atomic. Guava's TTL ensures memory stays bounded even if players disconnect without cleanup.

### Chunk saturation watchdog

In `ScentManager`:

```java
private final Map<ChunkPos, Long> lastSaturationReport = new ConcurrentHashMap<>();

public boolean addScentNode(ScentNode node) {
    ChunkPos chunkPos = new ChunkPos(node.getBlockPos());
    ConcurrentLinkedQueue<ScentNode> queue =
        scentMap.computeIfAbsent(chunkPos, k -> new ConcurrentLinkedQueue<>());

    int max = SoundAttractConfig.COMMON.maxScentNodesPerChunk.get();
    if (queue.size() >= max) {
        reportSaturation(chunkPos);
        return false;
    }
    queue.add(node);
    return true;
}

private void reportSaturation(ChunkPos chunkPos) {
    long now = level.getGameTime();
    long reportIntervalTicks = 200L;   // 10 seconds
    Long last = lastSaturationReport.get(chunkPos);
    if (last == null || now - last >= reportIntervalTicks) {
        if (lastSaturationReport.replace(chunkPos, last, now)
                || lastSaturationReport.putIfAbsent(chunkPos, now) == null) {
            SoundAttractMod.LOGGER.info(
                "[ScentManager] Chunk [x={}, z={}] saturated at {} scent nodes, dropping emissions until expiry",
                chunkPos.x, chunkPos.z,
                SoundAttractConfig.COMMON.maxScentNodesPerChunk.get());
        }
    }
}
```

INFO-level log (always on, not gated behind debug). Cleared when `ScentManager.clear()` runs (server stop) and when `cleanUpChunk` empties the chunk.

## Consumers

### `ScentEvents.onWorldTick` — per-viewer visibility filter

Current loop sends particles to every nearby player uniformly. Replace with per-viewer check:

```java
for (ServerPlayer viewer : serverLevel.players()) {
    if (viewer.position().distanceToSqr(pos) > renderDistSq) continue;
    if (!canViewerSeeScent(viewer, node.getSourceType())) continue;
    serverLevel.sendParticles(viewer, dustParticle, true,
            pos.x, pos.y + 0.3, pos.z,
            particleCount, 0.1, 0.05, 0.1, 0.0);
}
```

`canViewerSeeScent`:

```java
private static boolean canViewerSeeScent(ServerPlayer viewer, ScentSourceType type) {
    PlayerProfile2 profile = SoundAttractConfig.getMatchingPlayerProfile(viewer);
    if (profile == null) return true;
    Optional<ScentVisibilityConfig> vis = profile.scentVisibility();
    if (vis.isEmpty()) return true;
    ScentVisibilityConfig v = vis.get();
    return switch (type) {
        case PLAYER_WALK -> v.seePlayerScent();
        case ARROW_ORIGIN, ARROW_PATH -> v.seeArrowScent();
        case MOB_PROJECTILE_ORIGIN, MOB_PROJECTILE_PATH -> v.seeMobProjectileScent();
    };
}
```

### `FollowScentGoal` — per-type weight

Where the goal scores candidate nodes, multiply strength by a type-dependent factor:

```java
private float weightForType(ScentSourceType type) {
    return switch (type) {
        case PLAYER_WALK -> 1.0f;
        case ARROW_ORIGIN, MOB_PROJECTILE_ORIGIN -> 0.9f;
        case ARROW_PATH, MOB_PROJECTILE_PATH -> 0.6f;
    };
}
```

Exact multipliers read from config (`arrowOriginScentWeightMultiplier`, `arrowPathScentWeightMultiplier`; `mobProjectile*` reuse the arrow values unless `arrowAndMobProjectileUseSameWeights` is true — default true for simplicity).

### `AttractionGoal` — scent override

Early return at the top of `canUse()`:

```java
if (SoundAttractConfig.COMMON.scentOverridesSound.get()
        && ScentQueryHelper.hasFreshScentNear(mob,
               SoundAttractConfig.COMMON.scentOverrideMaxAgeTicks.get())) {
    return false;
}
```

`ScentQueryHelper`:

```java
public static boolean hasFreshScentNear(Mob mob, int maxAgeTicks) {
    ServerLevel level = (ServerLevel) mob.level();
    ScentManager mgr = level.getCapability(ScentManager.INSTANCE).orElse(null);
    if (mgr == null) return false;
    long now = level.getGameTime();
    ChunkPos center = new ChunkPos(mob.blockPosition());
    for (ScentNode node : mgr.getNodesInArea(center)) {
        if (now - node.getTimestamp() <= maxAgeTicks) return true;
    }
    return false;
}
```

3×3 chunk scan reuses existing method. Called from `canUse()` which already runs at 10 % frequency (`nextInt(10) != 0`), so cost is negligible.

## Configuration

New keys in `SoundAttractConfig.COMMON` (section: `scent.arrow_trail`):

| Key | Type | Default | Notes |
|---|---|---|---|
| `enableArrowScentTrail` | bool | `true` | master toggle |
| `arrowScentPathInterval` | double (blocks) | `8.0` | min flight distance between path emits |
| `arrowScentMaxNodesPerArrow` | int | `6` | hard cap per projectile |
| `arrowScentGlobalRatePerShooter` | int | `60` | nodes allowed per 100-tick window per shooter |
| `arrowScentPathStrength` | double | `0.4` | base strength for path nodes (vs 1.0 for walk) |
| `arrowScentOriginStrength` | double | `0.7` | base strength for origin nodes |
| `arrowScentDurationTicks` | int | `half of scentNodeDurationTicks` | see Duration note |
| `arrowPathScentWeightMultiplier` | double | `0.6` | applied in `FollowScentGoal` |
| `arrowOriginScentWeightMultiplier` | double | `0.9` | applied in `FollowScentGoal` |

In section `scent.watchdog`:

| Key | Type | Default | Notes |
|---|---|---|---|
| `maxScentNodesPerChunk` | int | `128` | bound across all scent types |

In section `scent.priority`:

| Key | Type | Default | Notes |
|---|---|---|---|
| `scentOverridesSound` | bool | `true` | applies to all scent, not just arrow |
| `scentOverrideMaxAgeTicks` | int | `200` | scent must be within this age to preempt sound |

In section `scent.particles`:

| Key | Type | Default | Notes |
|---|---|---|---|
| `showArrowScentParticles` | bool | `false` | global toggle; profile-level filter still applies |

### Duration note

Current `ScentManager.cleanUpChunk` takes a single `maxDuration`. To support per-type durations we either (a) per-node max-duration check (cleanest) or (b) run cleanup twice with different durations. Choosing (a): add `node.getEffectiveMaxDurationTicks()` which returns `PLAYER_WALK` → `scentNodeDurationTicks` and arrow types → `arrowScentDurationTicks`. Update cleanup callsites to use per-node.

## Concurrency

| Object | Access pattern | Safety |
|---|---|---|
| `ARROWS` map | Read-many by ArrowScent tick, write-one by join/leave handlers | Already `ConcurrentHashMap` |
| `ArrowState` mutable fields | Written only on server tick thread | Safe by Minecraft threading invariant |
| `ScentManager.scentMap` | compute/add/remove concurrent | Already `ConcurrentHashMap<ChunkPos, ConcurrentLinkedQueue>` |
| `lastSaturationReport` | compute only | `ConcurrentHashMap` + `compute`/`putIfAbsent` |
| `GlobalScentRateLimiter.state` | compute only | Guava cache; `compute` atomic |
| Player profile lookups | Read-only config | Safe |

No new synchronization primitives needed.

## Memory Bounds

| Structure | Worst-case size | Bound |
|---|---|---|
| `ARROWS` | 4096 entries | existing `MAX_TRACKED_ARROWS` |
| `ScentManager.scentMap` per chunk | 128 nodes | `maxScentNodesPerChunk` |
| `lastSaturationReport` | # active chunks | Bounded by loaded chunks; cleared on server stop |
| `GlobalScentRateLimiter.state` | 1024 shooters | Guava maxSize + 5min TTL |

Everything has a hard cap. No unbounded growth path.

## Failure Modes

| Failure | Detection | Recovery |
|---|---|---|
| NBT load with unknown `SourceType` string | `IllegalArgumentException` in `valueOf` | Catch → `PLAYER_WALK` default |
| Null `ScentManager` capability (unusual levels) | `orElse(null)` | Early return, skip emission |
| Projectile crosses dimensions mid-flight | `projectile.level() != level` check | Skip this tick for that arrow; state persists until entity leaves |
| Config access before load | `COMMON == null` guard | Already standard pattern in codebase |
| `ScentManager.addScentNode` under saturation | Returns `false` | Caller ignores; state.lastScentPos NOT updated so next tick retries cleanly |

## Testing Strategy

No unit test infrastructure in this codebase. Verification is manual with structured logging.

### Debug logging to add

At `INFO` level (always on):
- `ScentManager` saturation report (in spec above)

At `DEBUG` level (behind `debugLogging`):
- `[ArrowScent] Origin emitted for {owner} at {pos} type={type} strength={s}`
- `[ArrowScent] Path node {n}/{cap} for arrow {id} at {pos}`
- `[ArrowScent] Rate limited: {owner} exceeded {rate}/100 ticks`
- `[AttractionGoal] Deferring to scent: mob={name} has fresh scent age={ticks}`

### In-game scenarios

1. **Straight shot, 50 blocks**
   - Expected: 1 origin + ~5 path nodes (min(cap=6, 50/8=6))
   - Verify: mob picks trail and paths toward shooter

2. **Shot into canopy (jungle)**
   - Expected: nodes snap to ground below leaves (not stuck on foliage)
   - Verify: mobs can reach each node

3. **Dispenser spam**
   - Expected: fires arrows with `owner == null` → no emissions
   - Verify: no scent nodes appear near dispenser; no rate-limiter noise

4. **Modded dispenser that sets fake owner**
   - Expected: chunk saturates at 128 → INFO log every 10 s
   - Verify: no memory growth; other chunks unaffected

5. **Mob-shot arrow (skeleton fires at player)**
   - Expected: scent with `MOB_PROJECTILE_*` type; owner = skeleton UUID
   - Verify: undead allies potentially follow toward the source (emergent)

6. **Player profile with `see_arrow_scent: false`**
   - Expected: that player sees no red arrow-trail particles; still sees own walk scent
   - Verify: other players unaffected

7. **Sound + scent composition (headline scenario)**
   - Player shoots from 80 blocks away; arrow near-misses zombie
   - Zombie hears virtual sound → walks to flight area
   - Zombie enters 3×3 chunk scent radius → picks arrow scent
   - Zombie paths back toward shooter
   - Verify: raid triggers at trail end if `enableScentRaid=true`

8. **World reload**
   - Save world mid-trail, quit, reload
   - Expected: nodes persist with their types intact
   - Verify: existing trail still followable; new emissions resume

## Migration

- Existing save files: `ScentNode` NBT without `SourceType` loads as `PLAYER_WALK`. No data loss.
- Existing `PlayerProfile2` JSON without `scent_visibility`: treated as all-true. No behaviour change until explicitly configured.
- `ScentEvents.onPlayerTick` passes `ScentSourceType.PLAYER_WALK` explicitly — constructor must be updated across all callsites, but the change is mechanical.

## Open Questions Resolved

- **Why not require player owner?** Watchdog + rate limiter already cover abuse. Allowing mob owners enables inter-faction raids (user's Spore-vs-Undead scenario).
- **Will airborne nodes work?** Heightmap `MOTION_BLOCKING_NO_LEAVES` snaps to ground below canopy; no tree-leaf false positives.
- **Do we need DDA LOS?** Not for emission. Reserved for future "verify mob can path here" optimisation, which would use existing `OptimizedLOS.hasLineOfSight`.
- **Should arrow scent override player scent?** No — both use `FollowScentGoal`, which picks the highest-scoring node regardless of source. Type weight multipliers tune balance.

## Rollout

Single feature flag `enableArrowScentTrail` (default true). Flipping it off disables emission; existing nodes expire normally. All dependent changes (watchdog, scent override, per-type weights, visibility filter) have their own independent flags and default to safe values.
