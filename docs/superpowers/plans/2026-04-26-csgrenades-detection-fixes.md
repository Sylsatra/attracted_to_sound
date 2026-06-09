# CS Grenades Detection Fixes Implementation Plan

> **For agentic workers:** Use `executing-plans` skill. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix three confirmed root causes blocking CS Grenades integration: (1) smoke grenades silently mislabeled as FLASH_BANG by the mod's `getGrenadeType()`, (2) flashbang explosion state never observed via polling, (3) blinded mobs still acquire targets because stealth range isn't reduced to zero for them.

**Architecture:**
- Detect grenade type by **entity class name** (`SimpleName`) instead of the buggy `getGrenadeType()` reflection.
- Detect flashbang explosion via the already-subscribed `EntityLeaveLevelEvent` (flash entity removal == explosion) instead of polling `isExplodedAccessor`.
- Force stealth detection range to `0` when the looker mob is blind, gated by an O(1) `BLIND_MAP.isEmpty()` fast-path to eliminate lag when no flashbangs are active.

**Tech Stack:** Java 17, Forge 1.20.1, reflection, Forge events (`EntityLeaveLevelEvent`).

**Evidence (from 2026-04-26 test logs):**
- `SmokeGrenadeEntity.getGrenadeType()` returns `'FLASH_BANG'` (confirmed via INFO log `Entity 38e3cf6d... type: 'FLASH_BANG'`). The decompiled bytecode in `mods/CounterStrikeGrenades/.../SmokeGrenadeEntity.java:122` literally passes `GrenadeType.FLASH_BANG` to `super()`.
- Flash entity leaves `TRACKED_GRENADES` ~1.6s BEFORE the actual explosion sound fires — polling window is closed before `isExploded` ever becomes true. `EntityLeaveLevelEvent` already fires for this entity and we already subscribe (`CsGrenadesEventHandler.onEntityLeaveLevel`).
- `canMobDetectLivingEntity` / `getRealisticStealthDetectionRange` never consider `FlashbangEffect.isMobBlind`, so vanilla `NearestAttackableTargetGoal` re-acquires the player every tick even after `mob.setTarget(null)`.

---

## File Structure

| File | Responsibility | Change |
|------|---------------|--------|
| `src/main/java/com/example/soundattract/integration/csgrenades/CsGrenadesTracker.java` | Periodic scan of grenades in world; classifies smoke/fire | Replace `getGrenadeType()` switch with class-name switch. Remove noisy INFO diagnostics. |
| `src/main/java/com/example/soundattract/integration/csgrenades/CsGrenadesEventHandler.java` | Forge event integration; flashbang processing | In `onEntityLeaveLevel`: if leaving entity is `FlashBangEntity`, call `processExplodedFlashbang` using entity's last position. Remove polling-based `processTrackedGrenades` flash path. Remove noisy INFO diagnostics. |
| `src/main/java/com/example/soundattract/integration/csgrenades/FlashbangEffect.java` | Blind state map + queries | Add `public static boolean hasAnyBlindMobs()` O(1) fast-path helper. |
| `src/main/java/com/example/soundattract/event/StealthDetectionEvents.java` | Stealth range calc + detection gating | In BOTH `getRealisticStealthDetectionRange(Mob,Mob,Level)` and `getRealisticStealthDetectionRange(Player,Mob,Level)` add an early-return `0.0` when looker mob is blind, gated by `FlashbangEffect.hasAnyBlindMobs()`. |

**Lag analysis (Fix C):** `getRealisticStealthDetectionRange` is called per-mob per-targeting-check, potentially thousands of times per second in dense mob farms. The fast-path is a single `ConcurrentHashMap.isEmpty()` call (≈1 ns). When the map is non-empty, one additional `ConcurrentHashMap.get(UUID)` is performed (≈50 ns). No config reads, no reflection, no polling. Net overhead when no flashbang is active: unmeasurable. When flashbang is active: a few extra ns per targeting check for up to 10 seconds (blind duration).

---

## Chunk 1: Fix A - Class-name-based grenade tracking

### Task A1: Refactor `CsGrenadesTracker.scanAndRebuild` to use class name

**Files:**
- Modify: `src/main/java/com/example/soundattract/integration/csgrenades/CsGrenadesTracker.java:94-131`

- [ ] **Step 1: Replace the switch on `typeName` with a switch on `entity.getClass().getSimpleName()`**

Replace the block starting at line 94 (`for (Entity entity : grenades) { ... }`) with:

```java
for (Entity entity : grenades) {
    UUID id = entity.getUUID();
    Vec3 center = entity.position();
    String simpleName = entity.getClass().getSimpleName();

    switch (simpleName) {
        case "SmokeGrenadeEntity" -> {
            double smokeRadius = com.example.soundattract.config.SoundAttractConfig.COMMON.smokeCloudRadius.get();
            ACTIVE_SMOKE.put(id, new SmokeEntry(center, smokeRadius, currentTick));
            SoundAttractMod.LOGGER.debug("[CSGrenades] Tracker: smoke grenade at {} (radius {})", center, String.format("%.2f", smokeRadius));
        }
        case "MolotovEntity", "IncendiaryEntity" -> {
            double dangerRadius = com.example.soundattract.config.SoundAttractConfig.COMMON.fireFleeDangerRadius.get();
            ACTIVE_FIRE.put(id, new FireEntry(center, dangerRadius, currentTick));
            SoundAttractMod.LOGGER.debug("[CSGrenades] Tracker: fire grenade at {} (radius {})", center, String.format("%.2f", dangerRadius));
        }
        case "FlashBangEntity", "HEGrenadeEntity", "DecoyGrenadeEntity" -> {
        }
        default -> {
            SoundAttractMod.LOGGER.debug("[CSGrenades] Tracker: unhandled grenade subclass '{}'", simpleName);
        }
    }
}
```

Rationale: `getClass().getSimpleName()` is final/virtual and always returns the concrete subclass name. We do not rely on `getGrenadeType()` at all for classification.

- [ ] **Step 2: Remove the reflection invocation in `scanAndRebuild`**

Delete lines 99-111 (the `grenadeType = GET_GRENADE_TYPE_METHOD.invoke(entity)` block and its logging) since classification no longer uses it.

Keep `GET_GRENADE_TYPE_METHOD` and `ensureReflection` for now — they're still used for sanity but may be removed in a later cleanup pass. Mark with `@SuppressWarnings("unused")` if the IDE complains.

- [ ] **Step 3: Reduce scan diagnostic log volume**

Change lines 79, 86, 89, 90, 91, 92, 138 from `LOGGER.info` to `LOGGER.debug`. These were added for diagnosis; class-name detection is now proven reliable and INFO spam must stop.

- [ ] **Step 4: Build**

Run: `.\gradlew compileJava --no-daemon`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Manual test — smoke grenade**

Throw a smoke grenade in-game. Enable debug logging temporarily to confirm.

Expected log line: `[CSGrenades] Tracker: smoke grenade at (x, y, z) (radius ...)`
Expected `scan complete` line: `1 smoke, 0 fire entries` (at DEBUG level now).

If INFO-level log noise is excessive, keep debug off and just verify behavior (smoke blocks mob LOS).

- [ ] **Step 6: Manual test — molotov/incendiary**

Throw a molotov. Expected: `1 fire entries` at DEBUG.

- [ ] **Step 7: Commit**

```powershell
git add src/main/java/com/example/soundattract/integration/csgrenades/CsGrenadesTracker.java
git commit -m "fix(csgrenades): classify grenades by class name, not buggy getGrenadeType()"
```

---

## Chunk 2: Fix B - Flashbang detection via EntityLeaveLevelEvent

### Task B1: Move flashbang trigger to entity removal

**Files:**
- Modify: `src/main/java/com/example/soundattract/integration/csgrenades/CsGrenadesEventHandler.java` (around existing `onEntityLeaveLevel` at ~line 272, and the flash dispatch in `processTrackedGrenades` at ~line 177)

- [ ] **Step 1: Read the current `onEntityLeaveLevel` and the flash dispatch site**

Skim:
- `onEntityLeaveLevel` method (currently only removes from `TRACKED_GRENADES` and `FLEE_COOLDOWNS`).
- Inside `processTrackedGrenades`: the branch that calls `processExplodedFlashbang(grenade, level)` when `"FLASH_BANG".equals(typeName)`.

- [ ] **Step 2: Extend `onEntityLeaveLevel` to trigger flashbang processing**

Replace the current `onEntityLeaveLevel` method body with:

```java
@SubscribeEvent
public static void onEntityLeaveLevel(EntityLeaveLevelEvent event) {
    Entity entity = event.getEntity();
    TRACKED_GRENADES.remove(entity);
    if (entity instanceof Mob mob) {
        FLEE_COOLDOWNS.remove(mob);
    }

    if (!CsGrenadesCompat.isLoaded()) {
        return;
    }
    if (!SoundAttractConfig.COMMON.enableCsgrenadesIntegration.get()
            || !SoundAttractConfig.COMMON.enableFlashbangBlinding.get()) {
        return;
    }
    if (!"FlashBangEntity".equals(entity.getClass().getSimpleName())) {
        return;
    }
    if (!(event.getLevel() instanceof ServerLevel sLevel)) {
        return;
    }
    UUID id = entity.getUUID();
    if (!PROCESSED_FLASHBANGS.add(id)) {
        return;
    }
    processExplodedFlashbang(entity, sLevel);
}
```

Add any missing imports (`ServerLevel`, `UUID`, `Mob` if not already present).

Rationale: flash entities are removed from the world exactly when they explode. The entity's position (`entity.position()`) is still valid at leave-time. `PROCESSED_FLASHBANGS.add(id)` returns false if already present — cheaper than `contains`+`add`.

- [ ] **Step 3: Remove the flash path from `processTrackedGrenades`**

In `processTrackedGrenades`, delete the `case "FLASH_BANG"`/`processExplodedFlashbang` dispatch and the surrounding reflection that reads `grenadeType`. The method becomes a no-op for flash; it's no longer needed for the flashbang code path at all.

If the method is now empty-ish (only diagnostic logging remains), delete it and remove its call site in `onServerTick`.

- [ ] **Step 4: Remove polling-time diagnostic logging**

Within `processTrackedGrenades` (if it still exists), change the `processTrackedGrenades: tracked=X, alive=Y, exploded=Z` INFO log to DEBUG (or delete). Delete `Grenade ... not yet exploded` log. Delete `Processing grenade ... type=` log.

Keep `Processing flashbang - found N mobs in radius` and `Blinded mob ...` at INFO — these prove Fix B works and only fire during flashbangs.

- [ ] **Step 5: Build**

Run: `.\gradlew compileJava --no-daemon`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: Manual test — flashbang throws log**

Throw a flashbang near a zombie.

Expected log sequence:
```
[CSGrenades] Tracking grenade entity: <uuid> (type=entity.csgrenades.flashbang)
...
[CSGrenades] Processing flashbang - found N mobs in radius 16.0
[CSGrenades] Blinded mob <uuid> for <ticks> ticks
[CSGrenades] FlashbangEffect.blind: mob <uuid> blinded until tick ...
[CSGrenades] Flashbang blinded N mobs
```

The `Processing flashbang` log should fire at the moment of explosion (when you hear the flashbang sound), NOT immediately after the throw.

- [ ] **Step 7: Commit**

```powershell
git add src/main/java/com/example/soundattract/integration/csgrenades/CsGrenadesEventHandler.java
git commit -m "fix(csgrenades): trigger flashbang blind on EntityLeaveLevelEvent instead of unreliable polling"
```

---

## Chunk 3: Fix C - Blind mobs get 0 stealth detection range

### Task C1: Add O(1) fast-path to `FlashbangEffect`

**Files:**
- Modify: `src/main/java/com/example/soundattract/integration/csgrenades/FlashbangEffect.java`

- [ ] **Step 1: Add `hasAnyBlindMobs()` helper**

After the `clear()` method (around line 74), add:

```java
public static boolean hasAnyBlindMobs() {
    return !BLIND_MAP.isEmpty();
}
```

`ConcurrentHashMap.isEmpty()` is lock-free and O(1). This is the gate that prevents the blind check from ever touching HashMap lookups or config reads during normal play.

- [ ] **Step 2: Build**

Run: `.\gradlew compileJava --no-daemon`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```powershell
git add src/main/java/com/example/soundattract/integration/csgrenades/FlashbangEffect.java
git commit -m "feat(csgrenades): add FlashbangEffect.hasAnyBlindMobs() fast-path helper"
```

### Task C2: Inject blind → 0-range in mob-vs-mob stealth calc

**Files:**
- Modify: `src/main/java/com/example/soundattract/event/StealthDetectionEvents.java:234-237`

- [ ] **Step 1: Add blind short-circuit at top of `getRealisticStealthDetectionRange(Mob target, Mob looker, Level level)`**

Locate the method starting near line 234. Immediately after the existing `isMobInHighWeightOverride(looker)` check (line 235-237), insert:

```java
if (com.example.soundattract.integration.csgrenades.FlashbangEffect.hasAnyBlindMobs()
        && com.example.soundattract.integration.csgrenades.FlashbangEffect.isMobBlind(looker, level.getGameTime())) {
    return 0.0;
}
```

Rationale: placed AFTER the high-weight override check (preserving existing precedence) and BEFORE all expensive light / camo / armor calculations. The `hasAnyBlindMobs()` guard means zero overhead when no flashbang is active.

- [ ] **Step 2: Build**

Run: `.\gradlew compileJava --no-daemon`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```powershell
git add src/main/java/com/example/soundattract/event/StealthDetectionEvents.java
git commit -m "fix(csgrenades): force 0 stealth range for blind mob lookers (mob-vs-mob path)"
```

### Task C3: Inject blind → 0-range in mob-vs-player stealth calc

**Files:**
- Modify: `src/main/java/com/example/soundattract/event/StealthDetectionEvents.java:1225-1228`

- [ ] **Step 1: Add identical short-circuit to `getRealisticStealthDetectionRange(Player player, Mob mob, Level level)`**

Locate the method starting near line 1225. Immediately after the `isMobInHighWeightOverride(mob)` check (line 1226-1228), insert:

```java
if (com.example.soundattract.integration.csgrenades.FlashbangEffect.hasAnyBlindMobs()
        && com.example.soundattract.integration.csgrenades.FlashbangEffect.isMobBlind(mob, level.getGameTime())) {
    return 0.0;
}
```

Note: in this overload the looker parameter is named `mob` (not `looker`).

- [ ] **Step 2: Build**

Run: `.\gradlew compileJava --no-daemon`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```powershell
git add src/main/java/com/example/soundattract/event/StealthDetectionEvents.java
git commit -m "fix(csgrenades): force 0 stealth range for blind mob lookers (mob-vs-player path)"
```

### Task C4: Remove now-redundant target-clearing polling in `FlashbangEffect.isMobBlind`

**Files:**
- Modify: `src/main/java/com/example/soundattract/integration/csgrenades/FlashbangEffect.java:48-57`

- [ ] **Step 1: Keep a single clear on `blind()`, remove repeated clears in `isMobBlind`**

Rationale: once stealth range = 0, `LivingChangeTargetEvent.canMobDetectLivingEntity()` returns false → `setCanceled(true)` → the goal cannot set a target. The per-call clearing in `isMobBlind` is no longer needed and adds overhead per detection query.

Replace lines 48-58 (the `mob.getTarget() != null` + memory-erase block + debug log) with:

```java
        return true;
    }
```

The method body becomes:

```java
public static boolean isMobBlind(Mob mob, long currentTick) {
    BlindEntry entry = BLIND_MAP.get(mob.getUUID());
    if (entry == null) {
        return false;
    }
    if (currentTick > entry.expireTick) {
        BLIND_MAP.remove(mob.getUUID());
        return false;
    }
    return true;
}
```

- [ ] **Step 2: Build**

Run: `.\gradlew compileJava --no-daemon`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```powershell
git add src/main/java/com/example/soundattract/integration/csgrenades/FlashbangEffect.java
git commit -m "refactor(csgrenades): isMobBlind is now a pure query; targeting blocked via 0-range path"
```

### Task C5: Bypass grace period for blind mobs in interval-gated path

**Files:**
- Modify: `src/main/java/com/example/soundattract/event/StealthDetectionEvents.java:1128-1156`

- [ ] **Step 1: Locate the grace period logic in `onServerTick`**

Find the code block starting around line 1128 (`} else {` after `canCurrentlyDetect` check). This is inside the interval-gated tick handler that runs every `stealthCheckInterval` ticks.

- [ ] **Step 2: Add blind bypass before grace period accumulation**

Replace the `} else {` block (lines 1128-1156) with:

```java
} else {
    // Bypass grace period entirely if mob is blind
    if (com.example.soundattract.integration.csgrenades.FlashbangEffect.hasAnyBlindMobs()
            && com.example.soundattract.integration.csgrenades.FlashbangEffect.isMobBlind(mob, gameTime)) {
        if (SoundAttractConfig.COMMON.debugLogging.get()) {
            SoundAttractMod.LOGGER.info(
                    "[TickCheck] Mob {} is BLIND - bypassing grace period, clearing target {}",
                    mob.getName().getString(), target.getName().getString()
            );
        }
        if (mob.getBrain().hasMemoryValue(MemoryModuleType.ANGRY_AT)) {
            mob.getBrain().eraseMemory(MemoryModuleType.ANGRY_AT);
        }
        if (mob.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)) {
            mob.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
        }
        mob.setTarget(null);
        mobOutOfRangeTicks.remove(mobId);
    } else {
        // Normal grace period logic
        int ticks = mobOutOfRangeTicks.getOrDefault(mobId, 0) + stealthCheckInterval;
        if (ticks >= SoundAttractConfig.COMMON.stealthGracePeriodTicks.get()) {
            if (SoundAttractConfig.COMMON.debugLogging.get()) {
                SoundAttractMod.LOGGER.info(
                        "[TickCheck] Mob {} lost target {} due to stealth grace period timeout.",
                        mob.getName().getString(), target.getName().getString()
                );
            }
            if (mob.getBrain().hasMemoryValue(MemoryModuleType.ANGRY_AT)) {
                mob.getBrain().eraseMemory(MemoryModuleType.ANGRY_AT);
            }
            if (mob.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)) {
                mob.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
            }
            mob.setTarget(null);
            mobOutOfRangeTicks.remove(mobId);
        } else {
            mobOutOfRangeTicks.put(mobId, ticks);
            if (SoundAttractConfig.COMMON.debugLogging.get()) {
                SoundAttractMod.LOGGER.info(
                        "[TickCheck] Mob {} cannot detect {}. In grace period ({}/{}).",
                        mob.getName().getString(), target.getName().getString(),
                        ticks, SoundAttractConfig.COMMON.stealthGracePeriodTicks.get()
                );
            }
        }
    }
}
```

Rationale: The grace period is meant for "temporarily lost sight" scenarios (ducked behind cover). Being flashbanged is total blindness - the mob should lose target immediately, not get a 100-tick grace period to keep attacking.

- [ ] **Step 3: Build**

Run: `.\gradlew compileJava --no-daemon`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```powershell
git add src/main/java/com/example/soundattract/event/StealthDetectionEvents.java
git commit -m "fix(csgrenades): bypass stealth grace period for blind mobs"
```

---

## Chunk 4: End-to-end verification

### Task D1: In-game acceptance tests

- [ ] **Step 1: Smoke test**

Set up: stand in front of a zombie, throw a smoke between you and it.

Expected:
- Smoke cloud spawns.
- Zombie stops pathing to you (LOS suppression active via `CsGrenadesTracker.smokeBlocksRay`).
- Log contains `[CSGrenades] Tracker: smoke grenade at ...` at DEBUG (enable debug logging to confirm).

- [ ] **Step 2: Molotov test**

Throw a molotov near a zombie herd.

Expected:
- Zombies flee from the fire (via `nearestActiveFire` + existing flee logic).
- Log contains `[CSGrenades] Tracker: fire grenade at ...` at DEBUG.

- [ ] **Step 3: Flashbang test — primary acceptance**

Stand in an open area. Spawn a zombie within 8 blocks. Throw a flashbang at your feet.

Expected sequence:
1. Flashbang explodes (you hear the explosion sound).
2. INFO log: `Processing flashbang - found N mobs in radius 16.0` where N >= 1.
3. INFO log: `Blinded mob <zombie-uuid> for <~200> ticks`.
4. INFO log: `FlashbangEffect.blind: mob <uuid> blinded until tick ...`.
5. The zombie **stops moving toward you** for the blind duration.
6. The zombie does NOT hit you while blind.

- [ ] **Step 4: Flashbang test — range verification**

Throw a flashbang. While zombie is blind, run up to melee range (1-2 blocks).

Expected:
- Zombie still does not attack you.
- If `debugLogging` is enabled, you should see log lines showing stealth range computed as `0.00` for blind mobs (from `[GRSDR_Update]` debug lines, or a fresh `[CanDetect]` OUT_OF_RANGE log).

- [ ] **Step 5: Flashbang test — grace period bypass**

Spawn a zombie, let it acquire you as target (it starts chasing/hitting). Throw a flashbang at your feet while it's actively attacking.

Expected:
- Within 1-2 ticks of explosion: `[TickCheck] Mob {} is BLIND - bypassing grace period, clearing target {}`
- Zombie **immediately** stops attacking (no 100-tick grace period)
- Confirm via debug log that you do NOT see: `[TickCheck] Mob {} cannot detect {}. In grace period ({}/{}).`

- [ ] **Step 6: Flashbang test — expiration**

Wait for the blind duration to expire (~10 seconds at defaults).

Expected:
- Zombie resumes targeting you.
- No more blind-related logs.

- [ ] **Step 7: Lag sanity check — no flashbangs active**

Spawn ~50 zombies around you with no flashbang thrown. Observe TPS via F3 or `/forge tps`.

Expected:
- TPS unchanged from baseline (the `hasAnyBlindMobs()` fast-path returns false, zero added cost).

- [ ] **Step 8: Commit any verification-only changes (if any)**

If no code changes during verification, skip commit. Otherwise:

```powershell
git add <changed files>
git commit -m "test(csgrenades): verification pass for three-fix integration"
```

---

## Failure Recovery

**If flashbang still does not blind mobs after Chunk 2:**
- Verify `EntityLeaveLevelEvent` fires for flash entities: add a one-shot DEBUG log at the top of `onEntityLeaveLevel` printing `entity.getClass().getSimpleName()`.
- Confirm `PROCESSED_FLASHBANGS` is not pre-polluted. Inspect `onServerStopping`: it should clear that set.
- If entity is leaving level because of chunk unload (not explosion), position may be stale; in that case gate on `event.getLevel().getGameTime() - entity.tickCount > <min-arming-ticks>` or check `entity.isRemoved()` vs chunk boundary. This is an edge case — validate only if the primary test fails.

**If blind mobs still attack after Chunk 3:**
- Confirm `LivingChangeTargetEvent` is firing for the vanilla goal: existing `onMobAttemptTarget` handler should log CANCELED at DEBUG. Temporarily enable `debugLogging`.
- Verify `canMobDetectLivingEntity` is called via that event for zombies. If not, the vanilla `NearestAttackableTargetGoal` may not fire the event for all mob types (unlikely in 1.20.1, but worth confirming).
- Fallback: add blind check directly at top of `canMobDetectLivingEntity` (line 764) as well.

**If TPS regresses:**
- Confirm `hasAnyBlindMobs()` is called before `isMobBlind` (the `&&` short-circuits in Java).
- Profile with a sampler. The only added path when flashbang is active is one `ConcurrentHashMap.get` per targeting check.

---

## Out of Scope

- Removing the now-unused `GET_GRENADE_TYPE_METHOD` reflection and `ensureReflection` machinery from `CsGrenadesTracker`. Keep as-is; can be removed in a later cleanup. The `final` `getGrenadeType` accessor is not wrong per se — it's just unreliable for smoke due to the upstream mod's bytecode.
- Fixing the upstream CS Grenades mod bug (SmokeGrenadeEntity passing FLASH_BANG to super). That's the mod author's problem.
- Changing the flashbang radius/duration defaults — existing config is fine; user has not complained about tuning.
- Adding new config options — the existing `enableFlashbangBlinding` toggle is sufficient.
