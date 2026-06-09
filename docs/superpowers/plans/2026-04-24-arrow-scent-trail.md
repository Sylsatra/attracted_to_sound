# Arrow Scent Trail Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Projectiles with a `LivingEntity` owner deposit chunk-bounded scent trails that existing `FollowScentGoal` mobs can trace back to the shooter.

**Architecture:** Extend `ScentNode` with a `ScentSourceType` enum, bound `ScentManager.addScentNode` via a per-chunk saturation watchdog, piggy-back on `ArrowInvestigationEvents.ARROWS` tracking for emission, add a `PlayerProfile2.scentVisibility` viewer-side filter, and let `AttractionGoal` defer to fresh scent when configured.

**Tech Stack:** Minecraft 1.20.1, Forge 47.4.x, Mixin, Guava cache, existing capabilities/config/ScentManager infrastructure.

**Spec:** `docs/superpowers/specs/2026-04-24-arrow-scent-trail-design.md`

**Testing note:** This Forge mod has no unit test harness. Each chunk ends with a **compile gate** (`.\gradlew classes`) and, where applicable, in-game verification scenarios. Use frequent commits — one per task.

---

## File Structure

**New files:**
- `src/main/java/com/example/soundattract/scents/ScentSourceType.java` — 5-value enum
- `src/main/java/com/example/soundattract/config/ScentVisibilityConfig.java` — per-viewer filter record
- `src/main/java/com/example/soundattract/event/ArrowScentEvents.java` — emission event handler
- `src/main/java/com/example/soundattract/scents/GlobalScentRateLimiter.java` — per-shooter sliding window
- `src/main/java/com/example/soundattract/util/ScentQueryHelper.java` — "has fresh scent" query

**Modified files:**
- `src/main/java/com/example/soundattract/scents/ScentNode.java` — add sourceType + NBT compat
- `src/main/java/com/example/soundattract/scents/ScentManager.java` — watchdog + return-bool add
- `src/main/java/com/example/soundattract/event/ArrowInvestigationEvents.java` — expose ARROWS, extend ArrowState
- `src/main/java/com/example/soundattract/event/ScentEvents.java` — per-viewer particle filter; pass PLAYER_WALK
- `src/main/java/com/example/soundattract/config/PlayerProfile2.java` — add scentVisibility field
- `src/main/java/com/example/soundattract/config/separate/ScentConfig.java` — all new keys
- `src/main/java/com/example/soundattract/config/SoundAttractConfig.java` — expose mirrors of new keys
- `src/main/java/com/example/soundattract/ai/FollowScentGoal.java` — per-type weight multiplier
- `src/main/java/com/example/soundattract/ai/AttractionGoal.java` — scent-override early return
- `src/main/java/com/example/soundattract/SoundAttractMod.java` — register `ArrowScentEvents` on event bus

---

## Chunk 1: Data Model Foundation

### Task 1: Create `ScentSourceType` enum

**Files:**
- Create: `src/main/java/com/example/soundattract/scents/ScentSourceType.java`

- [ ] **Step 1: Create the enum file**

```java
package com.example.soundattract.scents;

public enum ScentSourceType {
    PLAYER_WALK,
    ARROW_ORIGIN,
    ARROW_PATH,
    MOB_PROJECTILE_ORIGIN,
    MOB_PROJECTILE_PATH;

    public static ScentSourceType fromNameOrDefault(String name) {
        if (name == null || name.isEmpty()) return PLAYER_WALK;
        try {
            return ScentSourceType.valueOf(name);
        } catch (IllegalArgumentException e) {
            return PLAYER_WALK;
        }
    }
}
```

- [ ] **Step 2: Verify compile**

Run: `.\gradlew classes --no-daemon`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/example/soundattract/scents/ScentSourceType.java
git commit -m "feat(scent): add ScentSourceType enum"
```

---

### Task 2: Extend `ScentNode` with sourceType

**Files:**
- Modify: `src/main/java/com/example/soundattract/scents/ScentNode.java`

- [ ] **Step 1: Add field and constructor overload**

Replace the existing class body so there's a new `sourceType` final field, a new primary constructor, and a legacy constructor that defaults to `PLAYER_WALK`:

```java
package com.example.soundattract.scents;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

public class ScentNode {
    private final Vec3 position;
    private final long timestamp;
    private final float strength;
    private final UUID ownerUUID;
    private final ScentSourceType sourceType;

    public ScentNode(Vec3 position, long timestamp, float strength,
                     UUID ownerUUID, ScentSourceType sourceType) {
        this.position = position;
        this.timestamp = timestamp;
        this.strength = strength;
        this.ownerUUID = ownerUUID;
        this.sourceType = sourceType == null ? ScentSourceType.PLAYER_WALK : sourceType;
    }

    public ScentNode(Vec3 position, long timestamp, float strength, UUID ownerUUID) {
        this(position, timestamp, strength, ownerUUID, ScentSourceType.PLAYER_WALK);
    }

    public Vec3 getPosition() { return position; }
    public BlockPos getBlockPos() { return BlockPos.containing(position); }
    public long getTimestamp() { return timestamp; }
    public float getStrength() { return strength; }
    public UUID getOwnerUUID() { return ownerUUID; }
    public ScentSourceType getSourceType() { return sourceType; }

    public static ScentNode load(CompoundTag tag) {
        double x = tag.getDouble("X");
        double y = tag.getDouble("Y");
        double z = tag.getDouble("Z");
        long ts = tag.getLong("Timestamp");
        float str = tag.getFloat("Strength");
        UUID owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        ScentSourceType type = tag.contains("SourceType")
                ? ScentSourceType.fromNameOrDefault(tag.getString("SourceType"))
                : ScentSourceType.PLAYER_WALK;
        return new ScentNode(new Vec3(x, y, z), ts, str, owner, type);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putDouble("X", position.x);
        tag.putDouble("Y", position.y);
        tag.putDouble("Z", position.z);
        tag.putLong("Timestamp", timestamp);
        tag.putFloat("Strength", strength);
        if (ownerUUID != null) {
            tag.putUUID("Owner", ownerUUID);
        }
        tag.putString("SourceType", sourceType.name());
        return tag;
    }
}
```

- [ ] **Step 2: Verify compile**

Run: `.\gradlew classes --no-daemon`
Expected: `BUILD SUCCESSFUL` (existing callsites still work via legacy constructor)

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/example/soundattract/scents/ScentNode.java
git commit -m "feat(scent): add sourceType to ScentNode with NBT backward-compat"
```

---

### Task 3: Update `ScentEvents.onPlayerTick` to use explicit type

**Files:**
- Modify: `src/main/java/com/example/soundattract/event/ScentEvents.java`

- [ ] **Step 1: Replace the ScentNode construction to pass PLAYER_WALK**

Find the line that constructs `new ScentNode(currentPos, currentTime, baseStrength, playerId)` and update it:

```java
ScentNode node = new ScentNode(currentPos, currentTime, baseStrength,
        playerId, com.example.soundattract.scents.ScentSourceType.PLAYER_WALK);
```

- [ ] **Step 2: Verify compile**

Run: `.\gradlew classes --no-daemon`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/example/soundattract/event/ScentEvents.java
git commit -m "refactor(scent): pass explicit PLAYER_WALK source type"
```

### Review gate for Chunk 1

- [ ] **Step 4: Run full build (including resources)**

Run: `.\gradlew build -x test --no-daemon`
Expected: `BUILD SUCCESSFUL`. Open an existing world in dev if available, confirm no NBT crash on load (old worlds should load cleanly via the missing-key fallback).

---

## Chunk 2: Config Keys and `ScentManager` Watchdog

### Task 4: Add config keys to `ScentConfig`

**Files:**
- Modify: `src/main/java/com/example/soundattract/config/separate/ScentConfig.java`

- [ ] **Step 1: Add new static final fields and builder statements**

At the top of the class with other `public static final ForgeConfigSpec.*` declarations, add:

```java
public static final ForgeConfigSpec.BooleanValue ENABLE_ARROW_SCENT_TRAIL;
public static final ForgeConfigSpec.DoubleValue  ARROW_SCENT_PATH_INTERVAL;
public static final ForgeConfigSpec.IntValue     ARROW_SCENT_MAX_NODES_PER_ARROW;
public static final ForgeConfigSpec.IntValue     ARROW_SCENT_GLOBAL_RATE_PER_SHOOTER;
public static final ForgeConfigSpec.DoubleValue  ARROW_SCENT_PATH_STRENGTH;
public static final ForgeConfigSpec.DoubleValue  ARROW_SCENT_ORIGIN_STRENGTH;
public static final ForgeConfigSpec.IntValue     ARROW_SCENT_DURATION_TICKS;
public static final ForgeConfigSpec.DoubleValue  ARROW_PATH_SCENT_WEIGHT_MULT;
public static final ForgeConfigSpec.DoubleValue  ARROW_ORIGIN_SCENT_WEIGHT_MULT;
public static final ForgeConfigSpec.IntValue     MAX_SCENT_NODES_PER_CHUNK;
public static final ForgeConfigSpec.BooleanValue SCENT_OVERRIDES_SOUND;
public static final ForgeConfigSpec.IntValue     SCENT_OVERRIDE_MAX_AGE_TICKS;
public static final ForgeConfigSpec.BooleanValue SHOW_ARROW_SCENT_PARTICLES;
```

In the `static { ... BUILDER.push("..."); ... BUILDER.pop(); }` block, append a new section (choose a location after existing scent keys):

```java
BUILDER.push("arrow_trail");
ENABLE_ARROW_SCENT_TRAIL = BUILDER.comment("Enable arrow/projectile scent trails.")
        .define("enableArrowScentTrail", true);
ARROW_SCENT_PATH_INTERVAL = BUILDER.comment("Min flight distance (blocks) between path node emits.")
        .defineInRange("arrowScentPathInterval", 8.0, 1.0, 64.0);
ARROW_SCENT_MAX_NODES_PER_ARROW = BUILDER.comment("Hard cap on path nodes emitted per projectile.")
        .defineInRange("arrowScentMaxNodesPerArrow", 6, 1, 64);
ARROW_SCENT_GLOBAL_RATE_PER_SHOOTER = BUILDER.comment("Max scent nodes per shooter per 100-tick window.")
        .defineInRange("arrowScentGlobalRatePerShooter", 60, 1, 1000);
ARROW_SCENT_PATH_STRENGTH = BUILDER.comment("Base strength for ARROW_PATH / MOB_PROJECTILE_PATH nodes.")
        .defineInRange("arrowScentPathStrength", 0.4, 0.05, 1.0);
ARROW_SCENT_ORIGIN_STRENGTH = BUILDER.comment("Base strength for ARROW_ORIGIN / MOB_PROJECTILE_ORIGIN nodes.")
        .defineInRange("arrowScentOriginStrength", 0.7, 0.05, 1.0);
ARROW_SCENT_DURATION_TICKS = BUILDER.comment("Lifetime for arrow/projectile scent nodes.")
        .defineInRange("arrowScentDurationTicks", 3000, 20, 24000);
ARROW_PATH_SCENT_WEIGHT_MULT = BUILDER.comment("Weight multiplier for ARROW_PATH nodes in FollowScentGoal.")
        .defineInRange("arrowPathScentWeightMultiplier", 0.6, 0.0, 4.0);
ARROW_ORIGIN_SCENT_WEIGHT_MULT = BUILDER.comment("Weight multiplier for ARROW_ORIGIN nodes in FollowScentGoal.")
        .defineInRange("arrowOriginScentWeightMultiplier", 0.9, 0.0, 4.0);
BUILDER.pop();

BUILDER.push("watchdog");
MAX_SCENT_NODES_PER_CHUNK = BUILDER.comment(
        "Hard cap on scent nodes stored per chunk. When reached, new emissions are rejected ",
        "and an INFO-level log is emitted once per 10 seconds per saturated chunk.")
        .defineInRange("maxScentNodesPerChunk", 128, 16, 4096);
BUILDER.pop();

BUILDER.push("priority");
SCENT_OVERRIDES_SOUND = BUILDER.comment(
        "When true, mobs with a fresh scent nearby ignore sound attraction and follow scent instead.")
        .define("scentOverridesSound", true);
SCENT_OVERRIDE_MAX_AGE_TICKS = BUILDER.comment("Max age (ticks) for scent to preempt sound.")
        .defineInRange("scentOverrideMaxAgeTicks", 200, 20, 24000);
BUILDER.pop();

BUILDER.push("particles");
SHOW_ARROW_SCENT_PARTICLES = BUILDER.comment(
        "Global toggle for arrow/projectile scent particles. Per-player profile filter still applies.")
        .define("showArrowScentParticles", false);
BUILDER.pop();
```

Match the order/section naming convention used elsewhere in the file.

- [ ] **Step 2: Verify compile**

Run: `.\gradlew classes --no-daemon`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/example/soundattract/config/separate/ScentConfig.java
git commit -m "feat(config): add arrow scent + watchdog + priority + particles keys"
```

---

### Task 5: Mirror config keys into `SoundAttractConfig.COMMON`

**Files:**
- Modify: `src/main/java/com/example/soundattract/config/SoundAttractConfig.java`

- [ ] **Step 1: Add mirror field declarations in the `Common` class**

Locate the existing mirror block (many `public final ForgeConfigSpec.XValue xxx = ScentConfig.XXX;` lines) and append:

```java
public final ForgeConfigSpec.BooleanValue enableArrowScentTrail           = ScentConfig.ENABLE_ARROW_SCENT_TRAIL;
public final ForgeConfigSpec.DoubleValue  arrowScentPathInterval          = ScentConfig.ARROW_SCENT_PATH_INTERVAL;
public final ForgeConfigSpec.IntValue     arrowScentMaxNodesPerArrow      = ScentConfig.ARROW_SCENT_MAX_NODES_PER_ARROW;
public final ForgeConfigSpec.IntValue     arrowScentGlobalRatePerShooter  = ScentConfig.ARROW_SCENT_GLOBAL_RATE_PER_SHOOTER;
public final ForgeConfigSpec.DoubleValue  arrowScentPathStrength          = ScentConfig.ARROW_SCENT_PATH_STRENGTH;
public final ForgeConfigSpec.DoubleValue  arrowScentOriginStrength        = ScentConfig.ARROW_SCENT_ORIGIN_STRENGTH;
public final ForgeConfigSpec.IntValue     arrowScentDurationTicks         = ScentConfig.ARROW_SCENT_DURATION_TICKS;
public final ForgeConfigSpec.DoubleValue  arrowPathScentWeightMultiplier  = ScentConfig.ARROW_PATH_SCENT_WEIGHT_MULT;
public final ForgeConfigSpec.DoubleValue  arrowOriginScentWeightMultiplier= ScentConfig.ARROW_ORIGIN_SCENT_WEIGHT_MULT;
public final ForgeConfigSpec.IntValue     maxScentNodesPerChunk           = ScentConfig.MAX_SCENT_NODES_PER_CHUNK;
public final ForgeConfigSpec.BooleanValue scentOverridesSound             = ScentConfig.SCENT_OVERRIDES_SOUND;
public final ForgeConfigSpec.IntValue     scentOverrideMaxAgeTicks        = ScentConfig.SCENT_OVERRIDE_MAX_AGE_TICKS;
public final ForgeConfigSpec.BooleanValue showArrowScentParticles         = ScentConfig.SHOW_ARROW_SCENT_PARTICLES;
```

- [ ] **Step 2: Verify compile**

Run: `.\gradlew classes --no-daemon`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/example/soundattract/config/SoundAttractConfig.java
git commit -m "feat(config): mirror arrow scent keys on COMMON spec"
```

---

### Task 6: Add saturation watchdog to `ScentManager`

**Files:**
- Modify: `src/main/java/com/example/soundattract/scents/ScentManager.java`

- [ ] **Step 1: Add the watchdog state field**

Inside the class, alongside `scentMap`:

```java
private final Map<ChunkPos, Long> lastSaturationReport = new ConcurrentHashMap<>();
```

- [ ] **Step 2: Change `addScentNode` to return boolean and enforce cap**

Replace the existing `addScentNode`:

```java
public boolean addScentNode(ScentNode node) {
    ChunkPos chunkPos = new ChunkPos(node.getBlockPos());
    ConcurrentLinkedQueue<ScentNode> queue =
            scentMap.computeIfAbsent(chunkPos, k -> new ConcurrentLinkedQueue<>());

    int max = SoundAttractConfig.COMMON.maxScentNodesPerChunk.get();
    if (queue.size() >= max) {
        reportSaturation(chunkPos, max);
        return false;
    }
    queue.add(node);
    return true;
}

private void reportSaturation(ChunkPos chunkPos, int cap) {
    long now = level.getGameTime();
    long interval = 200L;
    Long last = lastSaturationReport.get(chunkPos);
    boolean shouldLog;
    if (last == null) {
        shouldLog = lastSaturationReport.putIfAbsent(chunkPos, now) == null;
    } else if (now - last >= interval) {
        shouldLog = lastSaturationReport.replace(chunkPos, last, now);
    } else {
        shouldLog = false;
    }
    if (shouldLog) {
        com.example.soundattract.SoundAttractMod.LOGGER.info(
                "[ScentManager] Chunk [x={}, z={}] saturated at {} scent nodes, dropping emissions until expiry",
                chunkPos.x, chunkPos.z, cap);
    }
}
```

- [ ] **Step 3: Clear watchdog state in `clear()`**

Inside the existing `clear()`:

```java
public void clear() {
    scentMap.clear();
    lastSaturationReport.clear();
}
```

- [ ] **Step 4: Also clear the report entry when a chunk empties**

Inside `cleanUpChunk`, after `if (nodes.isEmpty()) { scentMap.remove(chunkPos); }` add:

```java
if (nodes.isEmpty()) {
    scentMap.remove(chunkPos);
    lastSaturationReport.remove(chunkPos);
}
```

(Replace the existing `if (nodes.isEmpty())` block.)

- [ ] **Step 5: Verify compile**

Run: `.\gradlew classes --no-daemon`
Expected: `BUILD SUCCESSFUL`. Existing callers that discard the boolean still compile because returning `boolean` is not a source-incompatible change when the return value is unused.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/example/soundattract/scents/ScentManager.java
git commit -m "feat(scent): add chunk saturation watchdog with rate-limited INFO log"
```

### Review gate for Chunk 2

- [ ] **Step 7: Run full build**

Run: `.\gradlew build -x test --no-daemon`
Expected: `BUILD SUCCESSFUL`

---

## Chunk 3: Extend `ArrowInvestigationEvents`

### Task 7: Extend `ArrowState` with scent tracking fields

**Files:**
- Modify: `src/main/java/com/example/soundattract/event/ArrowInvestigationEvents.java`

- [ ] **Step 1: Add fields to the private inner class**

In the `ArrowState` class at the bottom of the file:

```java
private static final class ArrowState {
    Vec3 prevPos;
    final Vec3 spawnPos;
    final long spawnTick;
    final Set<UUID> notifiedMobIds = ConcurrentHashMap.newKeySet();
    // NEW scent-emission state
    Vec3 lastScentPos;
    int scentNodesEmitted;

    ArrowState(Vec3 spawn, long spawnTick) {
        this.prevPos = spawn;
        this.spawnPos = spawn;
        this.spawnTick = spawnTick;
        this.lastScentPos = spawn;
        this.scentNodesEmitted = 0;
    }
}
```

- [ ] **Step 2: Expose read-only view of ARROWS**

Add a package-private static getter at class level (near the `ARROWS` field):

```java
static java.util.Map<Integer, ArrowState> getArrowsView() {
    return ARROWS;
}
```

Change the `ARROWS` field visibility from `private` to package-private if it is currently `private`:

```java
static final Map<Integer, ArrowState> ARROWS = new ConcurrentHashMap<>();
```

- [ ] **Step 3: Make `ArrowState` package-private**

Change `private static final class ArrowState` to `static final class ArrowState` so `ArrowScentEvents` (same package) can access its fields.

- [ ] **Step 4: Verify compile**

Run: `.\gradlew classes --no-daemon`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/example/soundattract/event/ArrowInvestigationEvents.java
git commit -m "feat(arrow): expose ARROWS view and extend ArrowState with scent fields"
```

---

## Chunk 4: Rate Limiter + `ArrowScentEvents`

### Task 8: Create `GlobalScentRateLimiter`

**Files:**
- Create: `src/main/java/com/example/soundattract/scents/GlobalScentRateLimiter.java`

- [ ] **Step 1: Write the class**

```java
package com.example.soundattract.scents;

import com.example.soundattract.config.SoundAttractConfig;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

public final class GlobalScentRateLimiter {
    private static final long WINDOW_TICKS = 100L;

    private final Cache<UUID, WindowState> state = CacheBuilder.newBuilder()
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .maximumSize(1024)
            .concurrencyLevel(4)
            .build();

    public boolean tryAcquire(UUID shooterId, long gameTime) {
        if (shooterId == null) return false;
        int perWindow = SoundAttractConfig.COMMON.arrowScentGlobalRatePerShooter.get();

        WindowState updated = state.asMap().compute(shooterId, (k, v) -> {
            if (v == null || gameTime - v.windowStart >= WINDOW_TICKS) {
                return new WindowState(gameTime, 1);
            }
            if (v.count >= perWindow) {
                return v;
            }
            return new WindowState(v.windowStart, v.count + 1);
        });

        return updated.count <= perWindow
                && (gameTime - updated.windowStart) < WINDOW_TICKS;
    }

    public void clear() { state.invalidateAll(); }

    private record WindowState(long windowStart, int count) {}
}
```

- [ ] **Step 2: Verify compile**

Run: `.\gradlew classes --no-daemon`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/example/soundattract/scents/GlobalScentRateLimiter.java
git commit -m "feat(scent): add GlobalScentRateLimiter with per-shooter sliding window"
```

---

### Task 9: Create `ArrowScentEvents`

**Files:**
- Create: `src/main/java/com/example/soundattract/event/ArrowScentEvents.java`

- [ ] **Step 1: Write the event subscriber**

```java
package com.example.soundattract.event;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.scents.GlobalScentRateLimiter;
import com.example.soundattract.scents.ScentManager;
import com.example.soundattract.scents.ScentNode;
import com.example.soundattract.scents.ScentSourceType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Map;
import java.util.UUID;

public final class ArrowScentEvents {

    private static final GlobalScentRateLimiter RATE_LIMITER = new GlobalScentRateLimiter();

    @SubscribeEvent
    public void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (SoundAttractConfig.COMMON == null) return;
        if (!SoundAttractConfig.COMMON.enableArrowScentTrail.get()) return;

        Entity entity = event.getEntity();
        if (!(entity instanceof Projectile projectile)) return;
        if (!entity.getType().is(ArrowInvestigationEvents.INVESTIGATE_PROJECTILES)) return;
        if (!(projectile.getOwner() instanceof LivingEntity owner)) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        emitNode(level, projectile, owner, owner.position(), true);
    }

    @SubscribeEvent
    public void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.level instanceof ServerLevel level)) return;
        if (SoundAttractConfig.COMMON == null) return;
        if (!SoundAttractConfig.COMMON.enableArrowScentTrail.get()) return;

        double interval = SoundAttractConfig.COMMON.arrowScentPathInterval.get();
        double intervalSq = interval * interval;
        int perArrowCap = SoundAttractConfig.COMMON.arrowScentMaxNodesPerArrow.get();

        Map<Integer, ArrowInvestigationEvents.ArrowState> arrows =
                ArrowInvestigationEvents.getArrowsView();

        for (Map.Entry<Integer, ArrowInvestigationEvents.ArrowState> entry : arrows.entrySet()) {
            ArrowInvestigationEvents.ArrowState state = entry.getValue();
            if (state == null) continue;
            if (state.scentNodesEmitted >= perArrowCap) continue;

            Entity e = level.getEntity(entry.getKey());
            if (!(e instanceof Projectile projectile)) continue;
            if (projectile.level() != level) continue;
            if (!projectile.isAlive()) continue;
            if (!(projectile.getOwner() instanceof LivingEntity owner)) continue;

            Vec3 curr = projectile.position();
            Vec3 last = state.lastScentPos != null ? state.lastScentPos : state.spawnPos;
            if (curr.distanceToSqr(last) < intervalSq) continue;

            if (emitNode(level, projectile, owner, curr, false)) {
                state.lastScentPos = curr;
                state.scentNodesEmitted++;
            }
        }
    }

    /**
     * @param useOriginStrength true for origin/seed nodes, false for path nodes
     * @return true if a node was actually stored (addScentNode returned true and rate limiter allowed it)
     */
    private static boolean emitNode(ServerLevel level, Projectile projectile,
                                    LivingEntity owner, Vec3 rawPos, boolean useOriginStrength) {
        UUID ownerId = owner.getUUID();
        long now = level.getGameTime();

        if (!RATE_LIMITER.tryAcquire(ownerId, now)) {
            if (SoundAttractConfig.COMMON.debugLogging.get()) {
                SoundAttractMod.LOGGER.debug("[ArrowScent] Rate limited: owner={}", ownerId);
            }
            return false;
        }

        int groundY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                (int) Math.floor(rawPos.x), (int) Math.floor(rawPos.z));
        Vec3 nodePos = new Vec3(rawPos.x, groundY, rawPos.z);

        boolean isPlayer = owner instanceof Player;
        ScentSourceType type;
        if (useOriginStrength) {
            type = isPlayer ? ScentSourceType.ARROW_ORIGIN : ScentSourceType.MOB_PROJECTILE_ORIGIN;
        } else {
            type = isPlayer ? ScentSourceType.ARROW_PATH : ScentSourceType.MOB_PROJECTILE_PATH;
        }

        double strength = useOriginStrength
                ? SoundAttractConfig.COMMON.arrowScentOriginStrength.get()
                : SoundAttractConfig.COMMON.arrowScentPathStrength.get();

        ScentNode node = new ScentNode(nodePos, now, (float) strength, ownerId, type);

        boolean[] added = new boolean[1];
        level.getCapability(ScentManager.INSTANCE).ifPresent(mgr -> added[0] = mgr.addScentNode(node));

        if (added[0] && SoundAttractConfig.COMMON.debugLogging.get()) {
            SoundAttractMod.LOGGER.debug(
                    "[ArrowScent] {} emitted for owner={} at {} strength={} (arrowId={})",
                    type, ownerId, nodePos, strength, projectile.getId());
        }
        return added[0];
    }
}
```

- [ ] **Step 2: Verify compile**

Run: `.\gradlew classes --no-daemon`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/example/soundattract/event/ArrowScentEvents.java
git commit -m "feat(scent): add ArrowScentEvents emission handler"
```

---

### Task 10: Register `ArrowScentEvents` on the Forge event bus

**Files:**
- Modify: `src/main/java/com/example/soundattract/SoundAttractMod.java`

- [ ] **Step 1: Register the handler**

Find where `ArrowInvestigationEvents` is registered (search for `new ArrowInvestigationEvents()` or a `MinecraftForge.EVENT_BUS.register(...)` call for arrow events). Add a sibling line:

```java
net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new com.example.soundattract.event.ArrowScentEvents());
```

Keep existing registration order; place immediately after the `ArrowInvestigationEvents` registration so ordering is predictable.

- [ ] **Step 2: Verify compile**

Run: `.\gradlew classes --no-daemon`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/example/soundattract/SoundAttractMod.java
git commit -m "feat(scent): register ArrowScentEvents on event bus"
```

### Review gate for Chunk 4

- [ ] **Step 4: In-game smoke test (dev run, if available)**

- Start Minecraft in dev.
- Open a creative world, enable `debugLogging=true`.
- Shoot an arrow ~20 blocks in a straight line.
- Expected log lines: one `ARROW_ORIGIN emitted` + ~2 `ARROW_PATH emitted` for a short shot.
- Shoot 100 arrows rapidly.
- Expected: some rate-limited debug entries; no crash; no log spam from watchdog unless same chunk saturates.

- [ ] **Step 5: Commit the plan progress marker**

```bash
git commit --allow-empty -m "milestone: chunk 4 arrow scent emission live"
```

---

## Chunk 5: Per-Viewer Visibility

### Task 11: Create `ScentVisibilityConfig` record

**Files:**
- Create: `src/main/java/com/example/soundattract/config/ScentVisibilityConfig.java`

- [ ] **Step 1: Write the record**

```java
package com.example.soundattract.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record ScentVisibilityConfig(
        boolean seePlayerScent,
        boolean seeArrowScent,
        boolean seeMobProjectileScent
) {
    public static final Codec<ScentVisibilityConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.optionalFieldOf("see_player_scent", true).forGetter(ScentVisibilityConfig::seePlayerScent),
            Codec.BOOL.optionalFieldOf("see_arrow_scent", true).forGetter(ScentVisibilityConfig::seeArrowScent),
            Codec.BOOL.optionalFieldOf("see_mob_projectile_scent", true).forGetter(ScentVisibilityConfig::seeMobProjectileScent)
    ).apply(instance, ScentVisibilityConfig::new));

    public static final ScentVisibilityConfig DEFAULT_ALL_VISIBLE =
            new ScentVisibilityConfig(true, true, true);
}
```

- [ ] **Step 2: Verify compile**

Run: `.\gradlew classes --no-daemon`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/example/soundattract/config/ScentVisibilityConfig.java
git commit -m "feat(profile): add ScentVisibilityConfig record"
```

---

### Task 12: Add `scentVisibility` to `PlayerProfile2`

**Files:**
- Modify: `src/main/java/com/example/soundattract/config/PlayerProfile2.java`

- [ ] **Step 1: Add the field to the record and codec**

Update the record header:

```java
public record PlayerProfile2(
    String id,
    Optional<EntityPredicate> condition,
    Map<PlayerStance, Double> detectionOverrides,
    Optional<ScentEmissionConfig> scentEmission,
    Optional<ScentVisibilityConfig> scentVisibility
) { ... }
```

In the `Codec` builder, add as the last field (keeps order stable):

```java
public static final Codec<PlayerProfile2> CODEC = RecordCodecBuilder.create(instance -> instance.group(
    Codec.STRING.optionalFieldOf("id", "unknown").forGetter(PlayerProfile2::id),
    ENTITY_PREDICATE_CODEC.optionalFieldOf("condition").forGetter(PlayerProfile2::condition),
    Codec.unboundedMap(PlayerStance.CODEC, Codec.DOUBLE).optionalFieldOf("detection_overrides", Map.of()).forGetter(PlayerProfile2::detectionOverrides),
    ScentEmissionConfig.CODEC.optionalFieldOf("scent_emission").forGetter(PlayerProfile2::scentEmission),
    ScentVisibilityConfig.CODEC.optionalFieldOf("scent_visibility").forGetter(PlayerProfile2::scentVisibility)
).apply(instance, PlayerProfile2::new));
```

Update `withId` and any other constructor-invoking helpers:

```java
public PlayerProfile2 withId(String newId) {
    return new PlayerProfile2(newId, condition, detectionOverrides, scentEmission, scentVisibility);
}
```

- [ ] **Step 2: Verify compile**

Run: `.\gradlew classes --no-daemon`
Expected: `BUILD SUCCESSFUL`. If callsites that construct `PlayerProfile2` directly fail to compile, update them to pass `Optional.empty()` for `scentVisibility`.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/example/soundattract/config/PlayerProfile2.java
git commit -m "feat(profile): add optional scentVisibility to PlayerProfile2"
```

---

### Task 13: Apply per-viewer filter in `ScentEvents.onWorldTick`

**Files:**
- Modify: `src/main/java/com/example/soundattract/event/ScentEvents.java`

- [ ] **Step 1: Add the `canViewerSeeScent` helper**

Add near the top of the class (private static):

```java
private static boolean canViewerSeeScent(ServerPlayer viewer,
                                         com.example.soundattract.scents.ScentSourceType type) {
    com.example.soundattract.config.PlayerProfile2 profile =
            SoundAttractConfig.getMatchingPlayerProfile(viewer);
    if (profile == null || profile.scentVisibility().isEmpty()) {
        com.example.soundattract.scents.ScentSourceType t = type;
        if ((t == com.example.soundattract.scents.ScentSourceType.ARROW_ORIGIN
                || t == com.example.soundattract.scents.ScentSourceType.ARROW_PATH
                || t == com.example.soundattract.scents.ScentSourceType.MOB_PROJECTILE_ORIGIN
                || t == com.example.soundattract.scents.ScentSourceType.MOB_PROJECTILE_PATH)
                && !SoundAttractConfig.COMMON.showArrowScentParticles.get()) {
            return false;
        }
        return true;
    }
    com.example.soundattract.config.ScentVisibilityConfig v = profile.scentVisibility().get();
    return switch (type) {
        case PLAYER_WALK -> v.seePlayerScent();
        case ARROW_ORIGIN, ARROW_PATH -> v.seeArrowScent()
                && SoundAttractConfig.COMMON.showArrowScentParticles.get();
        case MOB_PROJECTILE_ORIGIN, MOB_PROJECTILE_PATH -> v.seeMobProjectileScent()
                && SoundAttractConfig.COMMON.showArrowScentParticles.get();
    };
}
```

- [ ] **Step 2: Apply filter in the particle loop**

In `onWorldTick`, inside the `for (ServerPlayer nearbyPlayer : serverLevel.players())` block, add a guard before `sendParticles`:

```java
for (ServerPlayer nearbyPlayer : serverLevel.players()) {
    if (nearbyPlayer.position().distanceToSqr(pos) > renderDistSq) continue;
    if (!canViewerSeeScent(nearbyPlayer, node.getSourceType())) continue;
    serverLevel.sendParticles(nearbyPlayer, dustParticle, true,
            pos.x, pos.y + 0.3, pos.z,
            particleCount, 0.1, 0.05, 0.1, 0.0);
}
```

- [ ] **Step 3: Verify compile**

Run: `.\gradlew classes --no-daemon`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/example/soundattract/event/ScentEvents.java
git commit -m "feat(scent): per-viewer visibility filter for particles"
```

### Review gate for Chunk 5

- [ ] **Step 5: In-game test**

- Spawn arrow trail in world with `enableGameplayScentParticles=true` and `showArrowScentParticles=false`.
- Expected: no arrow-trail particles visible; player walk particles still visible.
- Set `showArrowScentParticles=true`. Reload config.
- Expected: arrow-trail particles now visible to all players.
- Add a datapack PlayerProfile2 with `scent_visibility.see_arrow_scent=false` matching a specific player.
- Expected: that player alone sees no arrow particles.

---

## Chunk 6: Goal Integration (Scent Override + Per-Type Weight)

### Task 14: Create `ScentQueryHelper`

**Files:**
- Create: `src/main/java/com/example/soundattract/util/ScentQueryHelper.java`

- [ ] **Step 1: Write the helper**

```java
package com.example.soundattract.util;

import com.example.soundattract.scents.ScentManager;
import com.example.soundattract.scents.ScentNode;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ChunkPos;

public final class ScentQueryHelper {
    private ScentQueryHelper() {}

    public static boolean hasFreshScentNear(Mob mob, int maxAgeTicks) {
        if (!(mob.level() instanceof ServerLevel level)) return false;
        ScentManager mgr = level.getCapability(ScentManager.INSTANCE).orElse(null);
        if (mgr == null) return false;

        long now = level.getGameTime();
        ChunkPos center = new ChunkPos(mob.blockPosition());

        for (ScentNode node : mgr.getNodesInArea(center)) {
            if (now - node.getTimestamp() <= maxAgeTicks) {
                return true;
            }
        }
        return false;
    }
}
```

- [ ] **Step 2: Verify compile**

Run: `.\gradlew classes --no-daemon`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/example/soundattract/util/ScentQueryHelper.java
git commit -m "feat(util): add ScentQueryHelper.hasFreshScentNear"
```

---

### Task 15: Wire scent override into `AttractionGoal.canUse`

**Files:**
- Modify: `src/main/java/com/example/soundattract/ai/AttractionGoal.java`

- [ ] **Step 1: Add early-return at top of `canUse()`**

Locate the `public boolean canUse()` method. As the FIRST statement inside it:

```java
if (com.example.soundattract.config.SoundAttractConfig.COMMON != null
        && com.example.soundattract.config.SoundAttractConfig.COMMON.scentOverridesSound.get()
        && com.example.soundattract.util.ScentQueryHelper.hasFreshScentNear(
                this.mob,
                com.example.soundattract.config.SoundAttractConfig.COMMON.scentOverrideMaxAgeTicks.get())) {
    return false;
}
```

If the class uses a field named differently than `this.mob` (e.g. `this.entity`), match the existing naming.

- [ ] **Step 2: Repeat for `LeaderAttractionGoal.canUse()`**

Same early-return in `src/main/java/com/example/soundattract/ai/LeaderAttractionGoal.java`.

- [ ] **Step 3: Repeat for `FollowerEdgeRelayGoal.canUse()` if applicable**

If that goal is also considered "sound-attraction" in this codebase (check the class), add the same guard. If it serves a different purpose, skip it.

- [ ] **Step 4: Verify compile**

Run: `.\gradlew classes --no-daemon`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/example/soundattract/ai/AttractionGoal.java \
        src/main/java/com/example/soundattract/ai/LeaderAttractionGoal.java \
        src/main/java/com/example/soundattract/ai/FollowerEdgeRelayGoal.java
git commit -m "feat(ai): sound goals defer to fresh scent when scentOverridesSound is on"
```

---

### Task 16: Per-type weight multiplier in `FollowScentGoal`

**Files:**
- Modify: `src/main/java/com/example/soundattract/ai/FollowScentGoal.java`

- [ ] **Step 1: Add helper method**

Add near the top of the class (private):

```java
private static float weightForType(com.example.soundattract.scents.ScentSourceType type) {
    var cfg = com.example.soundattract.config.SoundAttractConfig.COMMON;
    return switch (type) {
        case PLAYER_WALK -> 1.0f;
        case ARROW_ORIGIN, MOB_PROJECTILE_ORIGIN ->
                cfg.arrowOriginScentWeightMultiplier.get().floatValue();
        case ARROW_PATH, MOB_PROJECTILE_PATH ->
                cfg.arrowPathScentWeightMultiplier.get().floatValue();
    };
}
```

- [ ] **Step 2: Apply multiplier when selecting a node**

In the node-selection logic (search for where `node.getStrength()` is used when comparing candidates), multiply by `weightForType(node.getSourceType())`. Example:

```java
float effective = node.getStrength() * weightForType(node.getSourceType());
```

Use `effective` in the comparator (`Comparator.comparingDouble(...)`) or the manual max loop instead of raw `getStrength()`.

- [ ] **Step 3: Verify compile**

Run: `.\gradlew classes --no-daemon`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/example/soundattract/ai/FollowScentGoal.java
git commit -m "feat(ai): apply per-type weight multiplier in scent node selection"
```

### Review gate for Chunk 6

- [ ] **Step 5: In-game test**

- Shoot an arrow past a zombie that has both `AttractionGoal` and `FollowScentGoal`.
- With `scentOverridesSound=true`: zombie ignores arrow whiz sound and follows scent back to shooter.
- With `scentOverridesSound=false`: zombie reacts to sound first (old behaviour).
- Trigger a scent raid by leaving an extended trail: `enableScentRaid=true` + mob reaches trail end → raid scheduled.

---

## Chunk 7: Final Verification

### Task 17: Full build + manifest check

- [ ] **Step 1: Clean build**

Run: `.\gradlew clean build -x test --no-daemon`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 2: Inspect generated config file (first run of dev world)**

After starting dev once and creating a world, open `<world>/serverconfig/soundattract-server.toml` (or the relevant config file) and confirm:

- Section `[scent.arrow_trail]` present with all 9 new keys.
- Section `[scent.watchdog]` present with `maxScentNodesPerChunk`.
- Section `[scent.priority]` present with `scentOverridesSound` and `scentOverrideMaxAgeTicks`.
- Section `[scent.particles]` present with `showArrowScentParticles`.

- [ ] **Step 3: Load an existing pre-feature world**

Expected: no NBT crashes. Any saved `ScentNode` entries load with `sourceType=PLAYER_WALK`.

- [ ] **Step 4: Run the headline scenario**

1. Enable `debugLogging=true`.
2. Build an open field. Stand 80 blocks from a zombie.
3. Fire a bow shot that passes within 3 blocks of the zombie (near-miss).
4. Confirm log order:
   - `[ArrowInvestigation] ARROW_ORIGIN emitted for owner=<you>`
   - A few `ARROW_PATH emitted` entries as the arrow flies.
   - `[ArrowInvestigation] ... -> mob=Zombie at ... (kind=arrow_whiz)` (existing behaviour).
   - Zombie starts moving toward near-miss area (from sound).
   - Zombie reaches near-miss area, picks up scent, paths toward you.
   - With `enableScentRaid=true`: `[FollowScentGoal] ... triggered scent raid ...` at trail end.

- [ ] **Step 5: Chunk saturation test**

1. Place a dispenser rigged to fire arrows every tick into one chunk.
2. Within a few seconds observe the INFO log: `[ScentManager] Chunk [x=..., z=...] saturated at 128 scent nodes, dropping emissions until expiry`.
3. Confirm log repeats at most once every 10 seconds while saturation persists.
4. Disable the dispenser. After `arrowScentDurationTicks` the chunk drains and the log stops.

- [ ] **Step 6: Final commit**

```bash
git commit --allow-empty -m "milestone: arrow scent trail feature complete"
```

---

## Rollback Procedure

Every task is one commit. To rollback a specific chunk:

```bash
git log --oneline docs/superpowers/plans/2026-04-24-arrow-scent-trail.md
git revert <first-bad-commit>..<last-bad-commit>
```

The feature can also be disabled at runtime by setting `enableArrowScentTrail=false`. No data migration needed — existing nodes simply expire.

## Reference

- Spec: `docs/superpowers/specs/2026-04-24-arrow-scent-trail-design.md`
- Existing scent system: `@src/main/java/com/example/soundattract/scents/ScentManager.java`, `@src/main/java/com/example/soundattract/event/ScentEvents.java`, `@src/main/java/com/example/soundattract/ai/FollowScentGoal.java`
- Arrow investigation: `@src/main/java/com/example/soundattract/event/ArrowInvestigationEvents.java`
- DDA LOS (reserved for future reachability check): `@src/main/java/com/example/soundattract/los/OptimizedLOS.java:247`
