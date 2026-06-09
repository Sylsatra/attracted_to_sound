# CS Grenades Integration Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an optional integration between Sound Attract and the CS Grenades mod (`csgrenades`) so HE attracts via the existing sound whitelist, flashbang blinds mobs (range + LOS + per-mob FOV, linear duration falloff), smoke blocks line-of-sight volumetrically, and incendiary/molotov make eligible non-fire-immune mobs flee.

**Architecture:** Class-loading-isolated integration package at `com.example.soundattract.integration.csgrenades`. Loads only when `csgrenades` is present. Hooks into existing `StealthDetectionEvents.shouldSuppressTargeting` (for flashbang blinding) and `FovEvents.hasSmartLineOfSight` (for smoke volumetric LOS). A server-tick tracker caches active smoke/fire grenades by scanning `CSGrenadeServerAPI.entity.grenades` every N ticks. A new `FleeFromFireGoal` is attached to eligible mobs at `EntityJoinLevelEvent`.

**Tech Stack:** Forge 1.20.1, Java 17, ForgeConfigSpec, `@Mod.EventBusSubscriber`, `net.minecraftforge.event.entity.EntityJoinLevelEvent`, `net.minecraftforge.event.TickEvent.LevelTickEvent`, `club.pisquad.minecraft.csgrenades.*` (optional).

**Spec:** `docs/superpowers/specs/2026-04-25-csgrenades-integration-design.md`

**Verification model:** This codebase has no JUnit test scaffolding. Each task uses the compile-commit cadence proven across the session:

1. Make a focused change
2. Run `.\gradlew classes --no-daemon` — expect `BUILD SUCCESSFUL`
3. Commit with a scoped message
4. Manual in-game verification is batched at the end (Task 12)

Where we need confidence in pure math (`SmokeLosSuppression.blocksRay`, `FlashbangEffect.linearDuration`), the plan includes a self-check `public static void main` harness that the engineer runs once via `java -cp build/classes/java/main com.example.soundattract.integration.csgrenades.SmokeLosSuppression` (or equivalent) and removes before the final commit. These are **inline sanity checks, not permanent tests** — the workspace does not ship tests.

---

## File Structure

**New files (all under `src/main/java/com/example/soundattract/integration/csgrenades/`):**

| File | Responsibility |
|---|---|
| `CsGrenadesCompat.java` | `isLoaded()` guard; `ModList` check cached once |
| `CsGrenadesIntegration.java` | Lifecycle: called from `SoundAttractMod`; registers handler + tracker |
| `CsGrenadesEventHandler.java` | Subscribes to `GrenadeActivateEvent`; dispatches on grenade type |
| `CsGrenadesTracker.java` | Periodic scan of `CSGrenadeServerAPI.entity.grenades`; exposes `smokeBlocksRay` and `nearestActiveFire` |
| `SmokeLosSuppression.java` | Pure ray-sphere intersection math |
| `FlashbangEffect.java` | Per-mob blind-until-tick map; `apply`, `isMobBlind`, cleanup |
| `FleeFromFireGoal.java` | Vanilla AI `Goal` attached to eligible mobs |

**Modified files:**

| File | What changes |
|---|---|
| `config/separate/IntegrationConfig.java` | Add `csgrenades_integration` push-block with ~13 keys |
| `config/SoundAttractConfig.java` | Mirror accessors in `Common`; add schema v17 migration |
| `config/separate/GeneralConfig.java` | Bump `CONFIG_SCHEMA_VERSION` default from 16 → 17 |
| `SoundAttractMod.java` | Add `handleCsgrenadesIntegration()` call |
| `event/StealthDetectionEvents.java` | Prepend flashbang-blind check to `shouldSuppressTargeting(Mob)` |
| `event/FovEvents.java` | Append smoke LOS check to `hasSmartLineOfSight` |
| `event/ScentEvents.java` **or** new handler | Attach `FleeFromFireGoal` at entity join — see Task 9 for location decision |
| `src/main/resources/META-INF/mods.toml` | Add `csgrenades` as optional dependency |

---

## Chunk 1: Config Foundation and Compat Guard

### Task 1: Add 13 config keys to `IntegrationConfig.java`

**Files:**
- Modify: `src/main/java/com/example/soundattract/config/separate/IntegrationConfig.java`

- [ ] **Step 1:** Open `IntegrationConfig.java`. Locate the last `public static final ForgeConfigSpec.*` field declaration block (just before the `static { ... SPEC = BUILDER.build(); }` initializer). Add the following field declarations **immediately after the last existing integration-config field**:

```java
// CS Grenades integration
public static final ForgeConfigSpec.BooleanValue ENABLE_CSGRENADES_INTEGRATION;
public static final ForgeConfigSpec.BooleanValue ENABLE_FLASHBANG_BLINDING;
public static final ForgeConfigSpec.DoubleValue FLASHBANG_BLIND_RADIUS;
public static final ForgeConfigSpec.IntValue FLASHBANG_BLIND_DURATION_TICKS;
public static final ForgeConfigSpec.BooleanValue FLASHBANG_REQUIRE_LINE_OF_SIGHT;
public static final ForgeConfigSpec.BooleanValue FLASHBANG_REQUIRE_FOV;
public static final ForgeConfigSpec.IntValue FLASHBANG_MOB_SCAN_BUDGET;
public static final ForgeConfigSpec.BooleanValue ENABLE_SMOKE_LOS_BLOCKING;
public static final ForgeConfigSpec.DoubleValue SMOKE_CLOUD_RADIUS;
public static final ForgeConfigSpec.IntValue SMOKE_LIFETIME_MAX_TICKS;
public static final ForgeConfigSpec.BooleanValue ENABLE_FIRE_FLEE;
public static final ForgeConfigSpec.DoubleValue FIRE_FLEE_DANGER_RADIUS;
public static final ForgeConfigSpec.DoubleValue FIRE_FLEE_AWAY_DISTANCE;
public static final ForgeConfigSpec.DoubleValue FIRE_FLEE_SPEED_MODIFIER;
public static final ForgeConfigSpec.IntValue FIRE_FLEE_COOLDOWN_TICKS;
public static final ForgeConfigSpec.ConfigValue<List<? extends String>> FLEE_FROM_FIRE_ELIGIBLE_MOBS;
public static final ForgeConfigSpec.IntValue CSGRENADES_TRACKER_SCAN_INTERVAL_TICKS;
```

- [ ] **Step 2:** Inside the `static { ... }` initializer, at the end (just before `SPEC = BUILDER.build();`), add the definition block:

```java
BUILDER.comment("CS Grenades integration settings").push("csgrenades_integration");

ENABLE_CSGRENADES_INTEGRATION = BUILDER
        .comment("Master switch for all CS Grenades integration behaviors.")
        .define("enableCsgrenadesIntegration", true);

ENABLE_FLASHBANG_BLINDING = BUILDER
        .comment("Flashbang blinds mobs within range + LOS + FOV.")
        .define("enableFlashbangBlinding", true);
FLASHBANG_BLIND_RADIUS = BUILDER
        .comment("Maximum blocks from flash at which a mob can still be blinded.")
        .defineInRange("flashbangBlindRadius", 16.0, 2.0, 64.0);
FLASHBANG_BLIND_DURATION_TICKS = BUILDER
        .comment("Base blind duration; actual = base * (1 - dist/radius).")
        .defineInRange("flashbangBlindDurationTicks", 100, 1, 6000);
FLASHBANG_REQUIRE_LINE_OF_SIGHT = BUILDER
        .comment("If true, mob must have LOS to the flash to be blinded.")
        .define("flashbangRequireLineOfSight", true);
FLASHBANG_REQUIRE_FOV = BUILDER
        .comment("If true, mob must have the flash inside its FOV to be blinded.")
        .define("flashbangRequireFov", true);
FLASHBANG_MOB_SCAN_BUDGET = BUILDER
        .comment("Max mobs evaluated per flashbang activation (hard cap).")
        .defineInRange("flashbangMobScanBudget", 64, 8, 512);

ENABLE_SMOKE_LOS_BLOCKING = BUILDER
        .comment("Active smoke grenades block line-of-sight through their cloud.")
        .define("enableSmokeLosBlocking", true);
SMOKE_CLOUD_RADIUS = BUILDER
        .comment("Radius of the smoke sphere used for LOS intersection.")
        .defineInRange("smokeCloudRadius", 4.0, 1.0, 16.0);
SMOKE_LIFETIME_MAX_TICKS = BUILDER
        .comment("Safety cap: ignore smoke entities older than this many ticks since activation.")
        .defineInRange("smokeLifetimeMaxTicks", 400, 20, 6000);

ENABLE_FIRE_FLEE = BUILDER
        .comment("Eligible non-fire-immune mobs flee from active incendiary/molotov grenades.")
        .define("enableFireFlee", true);
FIRE_FLEE_DANGER_RADIUS = BUILDER
        .comment("A mob this close to an active fire grenade is considered in danger.")
        .defineInRange("fireFleeDangerRadius", 6.0, 1.0, 32.0);
FIRE_FLEE_AWAY_DISTANCE = BUILDER
        .comment("Blocks the mob tries to sprint away from fire.")
        .defineInRange("fireFleeAwayDistance", 16.0, 4.0, 64.0);
FIRE_FLEE_SPEED_MODIFIER = BUILDER
        .comment("Speed multiplier applied while fleeing fire.")
        .defineInRange("fireFleeSpeedModifier", 1.4, 0.5, 3.0);
FIRE_FLEE_COOLDOWN_TICKS = BUILDER
        .comment("Minimum ticks between re-triggering flee for the same mob.")
        .defineInRange("fireFleeCooldownTicks", 40, 1, 600);
FLEE_FROM_FIRE_ELIGIBLE_MOBS = BUILDER
        .comment("Entity IDs eligible to receive FleeFromFireGoal. mob.fireImmune()==true is always excluded.")
        .defineList("fleeFromFireEligibleMobs", Arrays.asList(
                "minecraft:zombie","minecraft:husk","minecraft:drowned","minecraft:skeleton",
                "minecraft:stray","minecraft:creeper","minecraft:spider","minecraft:cave_spider",
                "minecraft:wolf","minecraft:villager","minecraft:iron_golem","minecraft:piglin",
                "minecraft:piglin_brute","minecraft:zoglin","minecraft:hoglin","minecraft:witch",
                "minecraft:pillager","minecraft:vindicator","minecraft:evoker","minecraft:ravager"
        ), obj -> obj instanceof String);

CSGRENADES_TRACKER_SCAN_INTERVAL_TICKS = BUILDER
        .comment("How often the active-grenade cache rebuilds (smoke/fire).")
        .defineInRange("csgrenadesTrackerScanIntervalTicks", 5, 1, 40);

BUILDER.pop();
```

- [ ] **Step 2.5:** Confirm `java.util.Arrays` is already imported at the top of `IntegrationConfig.java`. If missing, add `import java.util.Arrays;`.

- [ ] **Step 3:** Compile:

```powershell
.\gradlew classes --no-daemon
```

Expected: `BUILD SUCCESSFUL`. If it fails with `cannot find symbol Arrays`, add the import.

- [ ] **Step 4:** Commit:

```powershell
git add src/main/java/com/example/soundattract/config/separate/IntegrationConfig.java
git commit -m "feat(csgrenades): add integration config keys to IntegrationConfig"
```

---

### Task 2: Mirror config accessors in `SoundAttractConfig.Common`

**Files:**
- Modify: `src/main/java/com/example/soundattract/config/SoundAttractConfig.java`

- [ ] **Step 1:** Open `SoundAttractConfig.java`. Locate the `Common` class. Find the end of the existing integration accessor block (search for `xrayChance = IntegrationConfig.XRAY_CHANCE;`). Immediately after it (and before `configSchemaVersion`), add:

```java
// CS Grenades integration
public final ForgeConfigSpec.BooleanValue enableCsgrenadesIntegration = IntegrationConfig.ENABLE_CSGRENADES_INTEGRATION;
public final ForgeConfigSpec.BooleanValue enableFlashbangBlinding = IntegrationConfig.ENABLE_FLASHBANG_BLINDING;
public final ForgeConfigSpec.DoubleValue flashbangBlindRadius = IntegrationConfig.FLASHBANG_BLIND_RADIUS;
public final ForgeConfigSpec.IntValue flashbangBlindDurationTicks = IntegrationConfig.FLASHBANG_BLIND_DURATION_TICKS;
public final ForgeConfigSpec.BooleanValue flashbangRequireLineOfSight = IntegrationConfig.FLASHBANG_REQUIRE_LINE_OF_SIGHT;
public final ForgeConfigSpec.BooleanValue flashbangRequireFov = IntegrationConfig.FLASHBANG_REQUIRE_FOV;
public final ForgeConfigSpec.IntValue flashbangMobScanBudget = IntegrationConfig.FLASHBANG_MOB_SCAN_BUDGET;
public final ForgeConfigSpec.BooleanValue enableSmokeLosBlocking = IntegrationConfig.ENABLE_SMOKE_LOS_BLOCKING;
public final ForgeConfigSpec.DoubleValue smokeCloudRadius = IntegrationConfig.SMOKE_CLOUD_RADIUS;
public final ForgeConfigSpec.IntValue smokeLifetimeMaxTicks = IntegrationConfig.SMOKE_LIFETIME_MAX_TICKS;
public final ForgeConfigSpec.BooleanValue enableFireFlee = IntegrationConfig.ENABLE_FIRE_FLEE;
public final ForgeConfigSpec.DoubleValue fireFleeDangerRadius = IntegrationConfig.FIRE_FLEE_DANGER_RADIUS;
public final ForgeConfigSpec.DoubleValue fireFleeAwayDistance = IntegrationConfig.FIRE_FLEE_AWAY_DISTANCE;
public final ForgeConfigSpec.DoubleValue fireFleeSpeedModifier = IntegrationConfig.FIRE_FLEE_SPEED_MODIFIER;
public final ForgeConfigSpec.IntValue fireFleeCooldownTicks = IntegrationConfig.FIRE_FLEE_COOLDOWN_TICKS;
public final ForgeConfigSpec.ConfigValue<List<? extends String>> fleeFromFireEligibleMobs = IntegrationConfig.FLEE_FROM_FIRE_ELIGIBLE_MOBS;
public final ForgeConfigSpec.IntValue csgrenadesTrackerScanIntervalTicks = IntegrationConfig.CSGRENADES_TRACKER_SCAN_INTERVAL_TICKS;
```

- [ ] **Step 2:** Compile:

```powershell
.\gradlew classes --no-daemon
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3:** Commit:

```powershell
git add src/main/java/com/example/soundattract/config/SoundAttractConfig.java
git commit -m "feat(csgrenades): mirror integration config accessors in Common"
```

---

### Task 3: Add eligible-mobs runtime cache and rebuild hook

The `fleeFromFireEligibleMobs` list must be parsed into a `Set<String>` for O(1) lookups. Existing pattern in this codebase keeps such caches in `SoundAttractConfig`.

**Files:**
- Modify: `src/main/java/com/example/soundattract/config/SoundAttractConfig.java`

- [ ] **Step 1:** In `SoundAttractConfig` at top-level (outside any inner class), add a `public static` field:

```java
public static java.util.Set<String> FLEE_FROM_FIRE_ELIGIBLE_SET = java.util.Collections.emptySet();
```

Place it alongside other `*_CACHE` fields (search for `POINT_BLANK_GUN_RANGE_CACHE` to find the area).

- [ ] **Step 2:** Locate `bakeConfig()` method. At the **end** of the method (just before the closing `}`), append:

```java
try {
    java.util.List<? extends String> raw = COMMON.fleeFromFireEligibleMobs.get();
    java.util.HashSet<String> set = new java.util.HashSet<>();
    for (String s : raw) { if (s != null && !s.isBlank()) set.add(s.trim()); }
    FLEE_FROM_FIRE_ELIGIBLE_SET = java.util.Collections.unmodifiableSet(set);
} catch (Throwable t) {
    SoundAttractMod.LOGGER.warn("[CsGrenadesIntegration] Failed to parse fleeFromFireEligibleMobs: {}", t.toString());
    FLEE_FROM_FIRE_ELIGIBLE_SET = java.util.Collections.emptySet();
}
```

- [ ] **Step 3:** Compile:

```powershell
.\gradlew classes --no-daemon
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4:** Commit:

```powershell
git add src/main/java/com/example/soundattract/config/SoundAttractConfig.java
git commit -m "feat(csgrenades): parse flee-eligible mobs into runtime Set cache"
```

---

### Task 4: Schema v17 migration — seed whitelist/defaults with CS sound IDs

**Files:**
- Modify: `src/main/java/com/example/soundattract/config/separate/GeneralConfig.java`
- Modify: `src/main/java/com/example/soundattract/config/SoundAttractConfig.java`

- [ ] **Step 1:** In `GeneralConfig.java`, find the `CONFIG_SCHEMA_VERSION` definition (around line 108). Change the default from `16` to `17`:

```java
CONFIG_SCHEMA_VERSION = BUILDER.comment("Internal schema version for config migrations. Do not change.")
        .defineInRange("configSchemaVersion", 17, 0, Integer.MAX_VALUE);
```

- [ ] **Step 2:** In `SoundAttractConfig.bakeConfig()`, find the **last** `if (COMMON.configSchemaVersion.get() < 16) { ... }` block. Immediately after it (and its closing `}`), append:

```java
if (COMMON.configSchemaVersion.get() < 17) {
    SoundAttractMod.LOGGER.info("Config migration: Seeding CS Grenades sound whitelist/defaults (Schema v17).");

    java.util.List<String> whitelistAdd = java.util.Arrays.asList(
            "csgrenades:hegrenade.explode",
            "csgrenades:hegrenade.detonate",
            "csgrenades:flashbang.explode",
            "csgrenades:incendiary.detonate",
            "csgrenades:molotov.detonate",
            "csgrenades:smokegrenade.emit"
    );
    java.util.List<String> defaultsAdd = java.util.Arrays.asList(
            "csgrenades:hegrenade.explode;64;20",
            "csgrenades:hegrenade.detonate;64;20",
            "csgrenades:flashbang.explode;32;8",
            "csgrenades:incendiary.detonate;24;6",
            "csgrenades:molotov.detonate;24;6",
            "csgrenades:smokegrenade.emit;12;3"
    );

    java.util.List<String> whitelist = new java.util.ArrayList<>(COMMON.soundIdWhitelist.get());
    int wAdded = 0;
    for (String id : whitelistAdd) {
        if (!whitelist.contains(id)) { whitelist.add(id); wAdded++; }
    }
    if (wAdded > 0) {
        COMMON.soundIdWhitelist.set(whitelist);
        SoundAttractMod.LOGGER.info("Added {} CS sound IDs to whitelist (v17).", wAdded);
    }

    java.util.List<String> defaults = new java.util.ArrayList<>(COMMON.soundDefaults.get());
    int dAdded = 0;
    for (String entry : defaultsAdd) {
        String prefix = entry.split(";")[0] + ";";
        boolean present = defaults.stream().anyMatch(e -> e != null && e.startsWith(prefix));
        if (!present) { defaults.add(entry); dAdded++; }
    }
    if (dAdded > 0) {
        COMMON.soundDefaults.set(defaults);
        SoundAttractMod.LOGGER.info("Added {} CS sound defaults (v17).", dAdded);
    }

    COMMON.configSchemaVersion.set(17);
}
```

- [ ] **Step 3:** Compile:

```powershell
.\gradlew classes --no-daemon
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4:** Commit:

```powershell
git add src/main/java/com/example/soundattract/config/separate/GeneralConfig.java src/main/java/com/example/soundattract/config/SoundAttractConfig.java
git commit -m "feat(csgrenades): schema v17 seeds CS sound IDs into whitelist/defaults"
```

---

### Task 5: Create `CsGrenadesCompat.java`

**Files:**
- Create: `src/main/java/com/example/soundattract/integration/csgrenades/CsGrenadesCompat.java`

- [ ] **Step 1:** Create the file with this content:

```java
package com.example.soundattract.integration.csgrenades;

import net.minecraftforge.fml.ModList;

public final class CsGrenadesCompat {
    public static final String MOD_ID = "csgrenades";

    private static Boolean cached = null;

    private CsGrenadesCompat() {}

    public static boolean isLoaded() {
        Boolean c = cached;
        if (c != null) return c;
        boolean v = ModList.get() != null && ModList.get().isLoaded(MOD_ID);
        cached = v;
        return v;
    }
}
```

- [ ] **Step 2:** Compile:

```powershell
.\gradlew classes --no-daemon
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3:** Commit:

```powershell
git add src/main/java/com/example/soundattract/integration/csgrenades/CsGrenadesCompat.java
git commit -m "feat(csgrenades): add CsGrenadesCompat.isLoaded guard"
```

---

### Task 6: Add `csgrenades` optional dependency to `mods.toml`

**Files:**
- Modify: `src/main/resources/META-INF/mods.toml`

- [ ] **Step 1:** Open `mods.toml`. After the last existing `[[dependencies.soundattract]]` block (the `relentlessundead` one near line 103-108), append:

```toml
[[dependencies.soundattract]]
    modId="csgrenades"
    mandatory=false
    versionRange="[1.5.0,)"
    ordering="NONE"
    side="BOTH"
```

- [ ] **Step 2:** Compile:

```powershell
.\gradlew classes --no-daemon
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3:** Commit:

```powershell
git add src/main/resources/META-INF/mods.toml
git commit -m "feat(csgrenades): declare optional csgrenades dependency"
```

---

## Chunk 2: Subsystems (Smoke Math, Flashbang Effect, Tracker, Fire-Flee Goal, Event Handler)

### Task 7: Create `SmokeLosSuppression.java` (pure ray-sphere math)

**Files:**
- Create: `src/main/java/com/example/soundattract/integration/csgrenades/SmokeLosSuppression.java`

- [ ] **Step 1:** Create the file:

```java
package com.example.soundattract.integration.csgrenades;

import net.minecraft.world.phys.Vec3;

public final class SmokeLosSuppression {

    private SmokeLosSuppression() {}

    /**
     * Segment [start, end] vs sphere (center, radius). Returns true iff the segment
     * intersects the sphere (touching the surface counts). Handles degenerate zero-length
     * segments by returning whether start lies inside the sphere.
     */
    public static boolean blocksRay(Vec3 start, Vec3 end, Vec3 center, double radius) {
        if (start == null || end == null || center == null || radius <= 0.0) return false;
        double dx = end.x - start.x;
        double dy = end.y - start.y;
        double dz = end.z - start.z;
        double a = dx * dx + dy * dy + dz * dz;
        double fx = start.x - center.x;
        double fy = start.y - center.y;
        double fz = start.z - center.z;
        double startDistSq = fx * fx + fy * fy + fz * fz;
        double rSq = radius * radius;
        if (a < 1.0e-9) {
            return startDistSq <= rSq;
        }
        double b = 2.0 * (fx * dx + fy * dy + fz * dz);
        double c = startDistSq - rSq;
        double disc = b * b - 4.0 * a * c;
        if (disc < 0.0) return false;
        double sq = Math.sqrt(disc);
        double t1 = (-b - sq) / (2.0 * a);
        double t2 = (-b + sq) / (2.0 * a);
        return (t1 >= 0.0 && t1 <= 1.0) || (t2 >= 0.0 && t2 <= 1.0) || (t1 < 0.0 && t2 > 1.0);
    }
}
```

The final `(t1 < 0.0 && t2 > 1.0)` clause handles the case where the segment lies entirely inside the sphere (both intersections are outside the segment range on opposite sides).

- [ ] **Step 2:** Compile:

```powershell
.\gradlew classes --no-daemon
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3 (sanity check, ephemeral):** Temporarily add a `main` method at the end of the class:

```java
public static void main(String[] args) {
    Vec3 zero = new Vec3(0, 0, 0);
    Vec3 ten = new Vec3(10, 0, 0);
    Vec3 midAbove = new Vec3(5, 3, 0);
    Vec3 farAbove = new Vec3(5, 100, 0);

    // Ray passes through sphere at origin r=1
    assert blocksRay(new Vec3(-2, 0, 0), new Vec3(2, 0, 0), zero, 1.0) : "through fail";
    // Ray entirely outside
    assert !blocksRay(new Vec3(-2, 10, 0), new Vec3(2, 10, 0), zero, 1.0) : "outside fail";
    // Ray start inside
    assert blocksRay(new Vec3(0.5, 0, 0), new Vec3(5, 0, 0), zero, 1.0) : "start-inside fail";
    // Ray end inside
    assert blocksRay(new Vec3(5, 0, 0), new Vec3(0.5, 0, 0), zero, 1.0) : "end-inside fail";
    // Ray entirely inside
    assert blocksRay(new Vec3(-0.2, 0, 0), new Vec3(0.2, 0, 0), zero, 1.0) : "fully-inside fail";
    // Zero-length, start outside
    assert !blocksRay(farAbove, farAbove, zero, 1.0) : "degenerate outside fail";
    // Zero-length, start inside
    assert blocksRay(zero, zero, zero, 1.0) : "degenerate inside fail";
    // Tangent
    assert blocksRay(new Vec3(-5, 1, 0), new Vec3(5, 1, 0), zero, 1.0) : "tangent fail";

    System.out.println("SmokeLosSuppression sanity checks passed.");
}
```

- [ ] **Step 4:** Run:

```powershell
.\gradlew classes --no-daemon
java -ea -cp "build\classes\java\main;build\resources\main" com.example.soundattract.integration.csgrenades.SmokeLosSuppression
```

(If classpath lookup fails due to missing vanilla classes, the `Vec3` import will cause a runtime ClassNotFound — in that case skip the runtime check; the math is mechanical.)

Expected: `SmokeLosSuppression sanity checks passed.` **OR** `NoClassDefFoundError: net/minecraft/world/phys/Vec3` — either outcome is acceptable; the checks are for confidence only.

- [ ] **Step 5:** **Remove** the `main` method from the file before committing:

Delete the entire `public static void main(String[] args) { ... }` block.

- [ ] **Step 6:** Compile and commit:

```powershell
.\gradlew classes --no-daemon
git add src/main/java/com/example/soundattract/integration/csgrenades/SmokeLosSuppression.java
git commit -m "feat(csgrenades): add SmokeLosSuppression ray-sphere intersection"
```

---

### Task 8: Create `FlashbangEffect.java`

**Files:**
- Create: `src/main/java/com/example/soundattract/integration/csgrenades/FlashbangEffect.java`

- [ ] **Step 1:** Create the file:

```java
package com.example.soundattract.integration.csgrenades;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.config.SoundAttractConfig;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class FlashbangEffect {

    private static final ConcurrentHashMap<UUID, Long> BLIND_UNTIL = new ConcurrentHashMap<>();

    private FlashbangEffect() {}

    public static void blind(UUID mobId, long untilTick) {
        if (mobId == null) return;
        BLIND_UNTIL.merge(mobId, untilTick, Math::max);
    }

    public static boolean isMobBlind(UUID mobId, long nowTick) {
        if (mobId == null) return false;
        Long until = BLIND_UNTIL.get(mobId);
        if (until == null) return false;
        if (nowTick >= until) {
            BLIND_UNTIL.remove(mobId, until);
            return false;
        }
        return true;
    }

    public static void invalidate(UUID mobId) {
        if (mobId == null) return;
        BLIND_UNTIL.remove(mobId);
    }

    public static void clear() {
        BLIND_UNTIL.clear();
    }

    public static int size() {
        return BLIND_UNTIL.size();
    }

    /**
     * Duration = base * (1 - dist/radius), clamped to [1, base]. Callers must ensure dist <= radius.
     */
    public static int linearDuration(double dist, double radius, int baseDurationTicks) {
        if (radius <= 0.0 || baseDurationTicks <= 0) return 0;
        double scale = 1.0 - (dist / radius);
        if (scale <= 0.0) return 1;
        int d = (int) Math.round(baseDurationTicks * scale);
        return Math.max(1, Math.min(baseDurationTicks, d));
    }

    /**
     * Periodic cleanup: drops expired entries; if still over cap, evicts oldest-expiry-first.
     */
    public static void cleanup(long nowTick) {
        int cap;
        try {
            cap = SoundAttractConfig.COMMON.flashbangMobScanBudget.get() * 16;
        } catch (Throwable t) {
            cap = 1024;
        }
        try {
            Iterator<Map.Entry<UUID, Long>> it = BLIND_UNTIL.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, Long> e = it.next();
                Long until = e.getValue();
                if (until == null || nowTick >= until) {
                    it.remove();
                }
            }
            int over = BLIND_UNTIL.size() - cap;
            if (over > 0) {
                BLIND_UNTIL.entrySet().stream()
                        .sorted(Map.Entry.comparingByValue())
                        .limit(over)
                        .forEach(e -> BLIND_UNTIL.remove(e.getKey()));
            }
        } catch (Throwable t) {
            SoundAttractMod.LOGGER.warn("[FlashbangEffect] cleanup failed: {}", t.toString());
        }
    }
}
```

- [ ] **Step 2:** Compile:

```powershell
.\gradlew classes --no-daemon
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3:** Commit:

```powershell
git add src/main/java/com/example/soundattract/integration/csgrenades/FlashbangEffect.java
git commit -m "feat(csgrenades): add FlashbangEffect blind map with TTL and cap"
```

---

### Task 9: Create `CsGrenadesTracker.java`

**Files:**
- Create: `src/main/java/com/example/soundattract/integration/csgrenades/CsGrenadesTracker.java`

**Upstream API surface used** (referenced only inside this class so classloader stays clean when `csgrenades` is absent):

- `club.pisquad.minecraft.csgrenades.api.CSGrenadesAPI` → `.server.entity.grenades` returning `Map<UUID, CounterStrikeGrenadeEntity>`
- `club.pisquad.minecraft.csgrenades.core.entity.CounterStrikeGrenadeEntity` with fields: `grenadeType` (enum `GrenadeType`), `isAlive()`, `level()`, `position()`, and synced data accessor `isActivatedAccessor` via `entityData.get(...)`.
- `club.pisquad.minecraft.csgrenades.GrenadeType.SMOKE_GRENADE`, `.INCENDIARY`, `.MOLOTOV`.

If the upstream exposes `isActivated()` as a method, prefer it. Otherwise read the synched accessor.

- [ ] **Step 1:** Create the file:

```java
package com.example.soundattract.integration.csgrenades;

import club.pisquad.minecraft.csgrenades.GrenadeType;
import club.pisquad.minecraft.csgrenades.api.CSGrenadesAPI;
import club.pisquad.minecraft.csgrenades.core.entity.CounterStrikeGrenadeEntity;
import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.config.SoundAttractConfig;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class CsGrenadesTracker {

    private static volatile List<CounterStrikeGrenadeEntity> smokeActive = Collections.emptyList();
    private static volatile List<CounterStrikeGrenadeEntity> fireActive = Collections.emptyList();
    private static long lastRebuildTick = Long.MIN_VALUE;

    private CsGrenadesTracker() {}

    public static void maybeRebuild(Level level, long nowTick) {
        if (!CsGrenadesCompat.isLoaded()) return;
        if (SoundAttractConfig.COMMON == null) return;
        int interval;
        try {
            interval = SoundAttractConfig.COMMON.csgrenadesTrackerScanIntervalTicks.get();
        } catch (Throwable t) {
            interval = 5;
        }
        if (nowTick - lastRebuildTick < interval) return;

        List<CounterStrikeGrenadeEntity> snapshot;
        try {
            snapshot = new ArrayList<>(CSGrenadesAPI.INSTANCE.getServer().getEntity().getGrenades().values());
        } catch (Throwable t) {
            SoundAttractMod.LOGGER.warn("[CsGrenadesTracker] snapshot failed: {}", t.toString());
            return;
        }

        long maxSmokeAge;
        try {
            maxSmokeAge = SoundAttractConfig.COMMON.smokeLifetimeMaxTicks.get();
        } catch (Throwable t) {
            maxSmokeAge = 400;
        }

        List<CounterStrikeGrenadeEntity> smoke = new ArrayList<>();
        List<CounterStrikeGrenadeEntity> fire = new ArrayList<>();
        for (CounterStrikeGrenadeEntity g : snapshot) {
            try {
                if (g == null) continue;
                if (!g.isAlive()) continue;
                if (g.level() != level) continue;
                GrenadeType type = g.getGrenadeType();
                if (type == GrenadeType.SMOKE_GRENADE) {
                    if (g.tickCount <= maxSmokeAge) smoke.add(g);
                } else if (type == GrenadeType.INCENDIARY || type == GrenadeType.MOLOTOV) {
                    fire.add(g);
                }
            } catch (Throwable t) {
                // skip
            }
        }
        smokeActive = smoke;
        fireActive = fire;
        lastRebuildTick = nowTick;
    }

    public static boolean smokeBlocksRay(Level level, Vec3 start, Vec3 end) {
        if (!CsGrenadesCompat.isLoaded()) return false;
        List<CounterStrikeGrenadeEntity> list = smokeActive;
        if (list.isEmpty()) return false;
        double radius;
        try {
            radius = SoundAttractConfig.COMMON.smokeCloudRadius.get();
        } catch (Throwable t) {
            radius = 4.0;
        }
        for (CounterStrikeGrenadeEntity g : list) {
            try {
                if (g == null || !g.isAlive() || g.level() != level) continue;
                if (SmokeLosSuppression.blocksRay(start, end, g.position(), radius)) return true;
            } catch (Throwable t) {
                // skip
            }
        }
        return false;
    }

    public static Vec3 nearestActiveFire(Level level, Vec3 pos, double maxDist) {
        if (!CsGrenadesCompat.isLoaded()) return null;
        List<CounterStrikeGrenadeEntity> list = fireActive;
        if (list.isEmpty()) return null;
        double bestSq = maxDist * maxDist;
        Vec3 best = null;
        for (CounterStrikeGrenadeEntity g : list) {
            try {
                if (g == null || !g.isAlive() || g.level() != level) continue;
                Vec3 gp = g.position();
                double d = pos.distanceToSqr(gp);
                if (d <= bestSq) {
                    bestSq = d;
                    best = gp;
                }
            } catch (Throwable t) {
                // skip
            }
        }
        return best;
    }

    public static void clear() {
        smokeActive = Collections.emptyList();
        fireActive = Collections.emptyList();
        lastRebuildTick = Long.MIN_VALUE;
    }
}
```

**IMPORTANT — upstream API accessor discovery:** The upstream `CSGrenadesAPI` is a Kotlin `object` with nested objects (`server` → `entity` → `grenades`). Kotlin objects compile to `INSTANCE` static fields and properties to `getX()` getters. The call chain `CSGrenadesAPI.INSTANCE.getServer().getEntity().getGrenades()` is the Java-side equivalent. If compilation fails with "method getServer() not found", inspect the decompiled bytecode or Kotlin metadata and adjust. Possible alternative forms seen in Kotlin objects compiled to Java:

- `CSGrenadesAPI.INSTANCE.getServer()` (Kotlin `val server = ...`)
- `CSGrenadeServerAPI.INSTANCE.getEntity().getGrenades()` (skip the top-level API wrapper)

The **fallback plan** if the nested-object chain causes problems: import `CSGrenadeServerAPI` directly (it's a Kotlin object accessible as `CSGrenadeServerAPI.INSTANCE`) and call `.getEntity().getGrenades()` on it.

Similarly `CounterStrikeGrenadeEntity.getGrenadeType()`: the Kotlin property `val grenadeType: GrenadeType` compiles to `getGrenadeType()` in Java. The `tickCount` field is inherited from vanilla `Entity` and is a plain public `int` field — accessible directly.

- [ ] **Step 2:** Compile:

```powershell
.\gradlew classes --no-daemon
```

Expected: `BUILD SUCCESSFUL`. If it fails with "cannot find symbol" on the Kotlin API chain, adjust to the fallback form above and retry. **Note:** compilation requires the `csgrenades` jar on the classpath — confirm `libs/csgrenade-1.20.1-1.5.0-prerelease.jar` is picked up by the build. Check `build.gradle` for a `compileOnly fileTree(dir: 'libs', include: '*.jar')` or similar; if missing, add it.

- [ ] **Step 3:** Commit:

```powershell
git add src/main/java/com/example/soundattract/integration/csgrenades/CsGrenadesTracker.java
git commit -m "feat(csgrenades): add CsGrenadesTracker periodic scan of active grenades"
```

---

### Task 10: Create `FleeFromFireGoal.java`

**Files:**
- Create: `src/main/java/com/example/soundattract/integration/csgrenades/FleeFromFireGoal.java`

- [ ] **Step 1:** Create the file:

```java
package com.example.soundattract.integration.csgrenades;

import com.example.soundattract.config.SoundAttractConfig;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public class FleeFromFireGoal extends Goal {

    private final PathfinderMob mob;
    private long lastFleeTick = Long.MIN_VALUE;
    private double wantedX, wantedY, wantedZ;

    public FleeFromFireGoal(PathfinderMob mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (SoundAttractConfig.COMMON == null) return false;
        if (!SoundAttractConfig.COMMON.enableCsgrenadesIntegration.get()) return false;
        if (!SoundAttractConfig.COMMON.enableFireFlee.get()) return false;
        if (!CsGrenadesCompat.isLoaded()) return false;
        if (!mob.isAlive() || mob.fireImmune()) return false;

        long now = mob.level().getGameTime();
        int cooldown = SoundAttractConfig.COMMON.fireFleeCooldownTicks.get();
        if (now - lastFleeTick < cooldown) return false;

        double danger = SoundAttractConfig.COMMON.fireFleeDangerRadius.get();
        Vec3 fire = CsGrenadesTracker.nearestActiveFire(mob.level(), mob.position(), danger);
        if (fire == null) return false;

        int away = (int) Math.round(SoundAttractConfig.COMMON.fireFleeAwayDistance.get());
        Vec3 target = DefaultRandomPos.getPosAway(mob, away, 7, fire);
        if (target == null) return false;

        this.wantedX = target.x;
        this.wantedY = target.y;
        this.wantedZ = target.z;
        return true;
    }

    @Override
    public void start() {
        double speed = SoundAttractConfig.COMMON.fireFleeSpeedModifier.get();
        this.mob.getNavigation().moveTo(wantedX, wantedY, wantedZ, speed);
        this.lastFleeTick = mob.level().getGameTime();
    }

    @Override
    public boolean canContinueToUse() {
        return !this.mob.getNavigation().isDone();
    }

    @Override
    public void stop() {
        this.mob.getNavigation().stop();
    }
}
```

- [ ] **Step 2:** Compile:

```powershell
.\gradlew classes --no-daemon
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3:** Commit:

```powershell
git add src/main/java/com/example/soundattract/integration/csgrenades/FleeFromFireGoal.java
git commit -m "feat(csgrenades): add FleeFromFireGoal for eligible mobs"
```

---

### Task 11: Create `CsGrenadesEventHandler.java` (flashbang activation + tracker tick + lifecycle cleanup)

**Files:**
- Create: `src/main/java/com/example/soundattract/integration/csgrenades/CsGrenadesEventHandler.java`

- [ ] **Step 1:** Create the file:

```java
package com.example.soundattract.integration.csgrenades;

import club.pisquad.minecraft.csgrenades.GrenadeType;
import club.pisquad.minecraft.csgrenades.core.entity.CounterStrikeGrenadeEntity;
import club.pisquad.minecraft.csgrenades.event.GrenadeActivateEvent;
import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.event.FovEvents;
import com.example.soundattract.los.OptimizedLOS;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;

public class CsGrenadesEventHandler {

    @SubscribeEvent
    public void onGrenadeActivate(GrenadeActivateEvent event) {
        try {
            if (SoundAttractConfig.COMMON == null) return;
            if (!SoundAttractConfig.COMMON.enableCsgrenadesIntegration.get()) return;
            if (event.getGrenadeType() != GrenadeType.FLASH_BANG) return;
            if (!SoundAttractConfig.COMMON.enableFlashbangBlinding.get()) return;

            CounterStrikeGrenadeEntity grenade = (CounterStrikeGrenadeEntity) event.getEntity();
            if (grenade == null) return;
            if (!(grenade.level() instanceof ServerLevel level)) return;

            Vec3 center = grenade.position();
            double radius = SoundAttractConfig.COMMON.flashbangBlindRadius.get();
            int base = SoundAttractConfig.COMMON.flashbangBlindDurationTicks.get();
            boolean requireLos = SoundAttractConfig.COMMON.flashbangRequireLineOfSight.get();
            boolean requireFov = SoundAttractConfig.COMMON.flashbangRequireFov.get();
            int budget = SoundAttractConfig.COMMON.flashbangMobScanBudget.get();
            long now = level.getGameTime();

            AABB aabb = new AABB(center, center).inflate(radius);
            List<Mob> mobs = level.getEntitiesOfClass(Mob.class, aabb, Mob::isAlive);
            int count = Math.min(mobs.size(), budget);

            for (int i = 0; i < count; i++) {
                Mob mob = mobs.get(i);
                Vec3 eye = mob.getEyePosition();
                double d = eye.distanceTo(center);
                if (d > radius) continue;
                if (requireLos && !OptimizedLOS.hasLineOfSight(level, eye, center)) continue;
                if (requireFov && !FovEvents.isTargetInFov(mob, grenade, false)) continue;
                int dur = FlashbangEffect.linearDuration(d, radius, base);
                FlashbangEffect.blind(mob.getUUID(), now + dur);
            }

            if (SoundAttractConfig.COMMON.debugLogging.get()) {
                SoundAttractMod.LOGGER.info("[CsGrenadesEventHandler] Flashbang blinded up to {} mobs at {}", count, center);
            }
        } catch (Throwable t) {
            SoundAttractMod.LOGGER.warn("[CsGrenadesEventHandler] onGrenadeActivate failed: {}", t.toString());
        }
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.level instanceof ServerLevel level)) return;
        if (SoundAttractConfig.COMMON == null) return;
        if (!SoundAttractConfig.COMMON.enableCsgrenadesIntegration.get()) return;

        long now = level.getGameTime();
        try {
            CsGrenadesTracker.maybeRebuild(level, now);
        } catch (Throwable t) {
            SoundAttractMod.LOGGER.warn("[CsGrenadesEventHandler] tracker rebuild failed: {}", t.toString());
        }
        if (now % 200L == 0L) {
            FlashbangEffect.cleanup(now);
        }
    }

    @SubscribeEvent
    public void onEntityJoin(EntityJoinLevelEvent event) {
        try {
            if (event.getLevel().isClientSide) return;
            if (SoundAttractConfig.COMMON == null) return;
            if (!SoundAttractConfig.COMMON.enableCsgrenadesIntegration.get()) return;
            if (!SoundAttractConfig.COMMON.enableFireFlee.get()) return;
            if (!(event.getEntity() instanceof PathfinderMob mob)) return;
            if (mob.fireImmune()) return;
            var key = ForgeRegistries.ENTITY_TYPES.getKey(mob.getType());
            if (key == null) return;
            if (!SoundAttractConfig.FLEE_FROM_FIRE_ELIGIBLE_SET.contains(key.toString())) return;
            mob.goalSelector.addGoal(1, new FleeFromFireGoal(mob));
        } catch (Throwable t) {
            SoundAttractMod.LOGGER.warn("[CsGrenadesEventHandler] onEntityJoin failed: {}", t.toString());
        }
    }

    @SubscribeEvent
    public void onEntityLeave(EntityLeaveLevelEvent event) {
        try {
            if (event.getEntity() instanceof Mob m) FlashbangEffect.invalidate(m.getUUID());
        } catch (Throwable ignored) { }
    }

    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        FlashbangEffect.clear();
        CsGrenadesTracker.clear();
    }
}
```

**Note on `OptimizedLOS.hasLineOfSight` signature:** The existing class likely exposes `hasLineOfSight(Level level, Vec3 start, Vec3 end)` (based on earlier memory). Before committing, open `com.example.soundattract.los.OptimizedLOS` and confirm. If the actual signature takes `(Vec3 start, Vec3 end, Level level)` or similar, adjust the call site accordingly.

**Note on `FovEvents.isTargetInFov`:** Confirmed signature is `(Mob looker, Entity target, boolean checkObstructions)`. `grenade` is an `Entity` subclass (CSGrenade entity extends Minecraft `Entity`), so the call is valid.

- [ ] **Step 2:** Compile:

```powershell
.\gradlew classes --no-daemon
```

If `OptimizedLOS.hasLineOfSight` signature mismatches, adjust. Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3:** Commit:

```powershell
git add src/main/java/com/example/soundattract/integration/csgrenades/CsGrenadesEventHandler.java
git commit -m "feat(csgrenades): add event handler (flash activation, tracker tick, lifecycle)"
```

---

### Task 12: Create `CsGrenadesIntegration.java` (lifecycle bootstrap)

**Files:**
- Create: `src/main/java/com/example/soundattract/integration/csgrenades/CsGrenadesIntegration.java`

- [ ] **Step 1:** Create the file:

```java
package com.example.soundattract.integration.csgrenades;

import com.example.soundattract.SoundAttractMod;
import net.minecraftforge.common.MinecraftForge;

public final class CsGrenadesIntegration {

    private static boolean registered = false;

    private CsGrenadesIntegration() {}

    public static void register() {
        if (registered) return;
        if (!CsGrenadesCompat.isLoaded()) {
            SoundAttractMod.LOGGER.info("[CsGrenadesIntegration] csgrenades not loaded; skipping integration.");
            return;
        }
        MinecraftForge.EVENT_BUS.register(new CsGrenadesEventHandler());
        registered = true;
        SoundAttractMod.LOGGER.info("[CsGrenadesIntegration] Registered CS Grenades event handler.");
    }
}
```

- [ ] **Step 2:** Compile and commit:

```powershell
.\gradlew classes --no-daemon
git add src/main/java/com/example/soundattract/integration/csgrenades/CsGrenadesIntegration.java
git commit -m "feat(csgrenades): add CsGrenadesIntegration.register bootstrap"
```

---

## Chunk 3: Wiring, Hooks, Verification

### Task 13: Wire `CsGrenadesIntegration.register()` into `SoundAttractMod`

**Files:**
- Modify: `src/main/java/com/example/soundattract/SoundAttractMod.java`

- [ ] **Step 1:** Open `SoundAttractMod.java`. Locate an existing `handle<Mod>Integration` method (e.g. `handlePointBlankIntegration` around line 130-159 per the session memory). Identify where these are invoked (likely in `FMLCommonSetupEvent` handler or mod constructor).

- [ ] **Step 2:** Add a new method alongside the existing ones:

```java
private void handleCsgrenadesIntegration() {
    try {
        com.example.soundattract.integration.csgrenades.CsGrenadesIntegration.register();
    } catch (Throwable t) {
        LOGGER.warn("[SoundAttractMod] CS Grenades integration register failed: {}", t.toString());
    }
}
```

- [ ] **Step 3:** Find the site that calls the other `handle*Integration()` methods and add a call to `handleCsgrenadesIntegration()` there. Example: if `handlePointBlankIntegration()` is called inside `onCommonSetup(FMLCommonSetupEvent event)`, add `handleCsgrenadesIntegration();` on the next line.

- [ ] **Step 4:** Compile:

```powershell
.\gradlew classes --no-daemon
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5:** Commit:

```powershell
git add src/main/java/com/example/soundattract/SoundAttractMod.java
git commit -m "feat(csgrenades): register CS Grenades integration on common setup"
```

---

### Task 14: Hook flashbang blind-check into `StealthDetectionEvents.shouldSuppressTargeting(Mob)`

**Files:**
- Modify: `src/main/java/com/example/soundattract/event/StealthDetectionEvents.java`

- [ ] **Step 1:** Open the file and find the method:

```java
public static boolean shouldSuppressTargeting(Mob mob) {
    if (!SoundAttractConfig.COMMON.enableStealthMechanics.get()) {
        return false;
    }
    ...
}
```

(Around line 948 per the earlier grep.)

- [ ] **Step 2:** Prepend (at the very top of the method body, before the `enableStealthMechanics` check) the flashbang check:

```java
try {
    if (SoundAttractConfig.COMMON != null
            && SoundAttractConfig.COMMON.enableCsgrenadesIntegration.get()
            && SoundAttractConfig.COMMON.enableFlashbangBlinding.get()
            && mob != null && mob.level() != null
            && com.example.soundattract.integration.csgrenades.FlashbangEffect.isMobBlind(
                    mob.getUUID(), mob.level().getGameTime())) {
        return true;
    }
} catch (Throwable t) {
    // fall through to normal checks
}
```

This sits **before** the `enableStealthMechanics` gate because flashbang blinding should work even if the stealth-mechanics master flag is off (flashbang is a separate, focused feature with its own master flag).

- [ ] **Step 3:** Compile:

```powershell
.\gradlew classes --no-daemon
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4:** Commit:

```powershell
git add src/main/java/com/example/soundattract/event/StealthDetectionEvents.java
git commit -m "feat(csgrenades): hook flashbang blind-check into shouldSuppressTargeting"
```

---

### Task 15: Hook smoke LOS-blocking into `FovEvents.hasSmartLineOfSight`

**Files:**
- Modify: `src/main/java/com/example/soundattract/event/FovEvents.java`

- [ ] **Step 1:** Open `FovEvents.java` and locate the method `hasSmartLineOfSight(Mob looker, Entity target)`. It returns `true`/`false`.

- [ ] **Step 2:** **At the end** of the method, **immediately before the final `return true;`** (or whatever terminal positive return exists), insert:

```java
try {
    if (SoundAttractConfig.COMMON != null
            && SoundAttractConfig.COMMON.enableCsgrenadesIntegration.get()
            && SoundAttractConfig.COMMON.enableSmokeLosBlocking.get()
            && com.example.soundattract.integration.csgrenades.CsGrenadesTracker.smokeBlocksRay(
                    looker.level(),
                    looker.getEyePosition(),
                    target.position().add(0, target.getEyeHeight() / 2.0, 0))) {
        return false;
    }
} catch (Throwable t) {
    // fall through; don't affect vanilla LOS behavior on error
}
```

**Placement detail:** Open the actual method first and confirm it doesn't have multiple return-true sites. If it does, add the block right before the *positive* final return. If the method flow makes early-exit safer, the check can go at the method entry — but only **after** any existing cache lookup (we want cache hits to skip the smoke check so cached-LOS-true doesn't get flipped by transient smoke). The simplest safe placement is: right before the last positive return.

- [ ] **Step 3:** Compile:

```powershell
.\gradlew classes --no-daemon
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4:** Commit:

```powershell
git add src/main/java/com/example/soundattract/event/FovEvents.java
git commit -m "feat(csgrenades): hook smoke LOS blocking into hasSmartLineOfSight"
```

---

### Task 16: Sanity-verify `bakeConfig` is called on config load/reload

**Files:**
- Review only: `src/main/java/com/example/soundattract/config/SoundAttractConfig.java`

- [ ] **Step 1:** Confirm `bakeConfig()` is called from a `ModConfigEvent.Loading` / `ModConfigEvent.Reloading` handler so the new `FLEE_FROM_FIRE_ELIGIBLE_SET` is populated. Search:

```powershell
Select-String -Path "src\main\java\com\example\soundattract\**\*.java" -Pattern "bakeConfig\(\)"
```

Expected: at least one call from a `ModConfigEvent` subscriber. If not present, add one in the same pattern as any existing sibling mod-config handler.

- [ ] **Step 2:** If a change was needed, compile and commit:

```powershell
.\gradlew classes --no-daemon
git add -A
git commit -m "chore(csgrenades): ensure bakeConfig runs on config load/reload"
```

Otherwise, no commit needed.

---

### Task 17: Compile, package, smoke-test in-game

**Files:** none modified.

- [ ] **Step 1:** Full build:

```powershell
.\gradlew build --no-daemon -x test
```

Expected: `BUILD SUCCESSFUL`. Jar lands in `build/libs/`.

- [ ] **Step 2:** Copy built jar + `csgrenades` jar into a dev Minecraft 1.20.1 Forge instance's `mods/` folder. Launch a single-player world (creative, peaceful).

- [ ] **Step 3:** Enable debug logging in `config/soundattract-common.toml`: set `debugLogging = true`. Restart world.

- [ ] **Step 4: HE attract verification**

Commands:
```
/summon minecraft:zombie ~ ~ ~10
/give @p csgrenades:hegrenade 4
```

Throw HE near the zombie. Expected: zombie paths toward explosion position; log includes `[AttractionGoal]` sound-acquisition entry referencing `csgrenades:hegrenade.explode`.

- [ ] **Step 5: Flashbang blind verification**

Commands:
```
/kill @e[type=!player]
/summon minecraft:zombie ~ ~ ~8
/give @p csgrenades:flashbang 4
```

Aggro the zombie (hit it once), then throw flashbang **in front of it**. Expected: zombie's pursuit stops (targeting suppressed) for ~50 ticks scaled by distance. Log: `[CsGrenadesEventHandler] Flashbang blinded up to N mobs`.

Repeat with zombie **facing away** (spawn behind it, don't aggro) → no blind.

Repeat with zombie **behind a 2-block wall** → no blind.

- [ ] **Step 6: Smoke LOS verification**

```
/kill @e[type=!player]
/summon minecraft:zombie ~ ~ ~16
/give @p csgrenades:smokegrenade 4
```

Let the zombie aggro you, then drop smoke between you and the zombie. Expected: zombie loses sight while smoke is alive; regains targeting after smoke expires.

- [ ] **Step 7: Fire flee verification**

```
/kill @e[type=!player]
/summon minecraft:zombie ~ ~ ~5
/give @p csgrenades:incendiary 4
```

Throw incendiary at the zombie's feet. Expected: after activation, zombie pathing reverses and it sprints away from the fire point. Re-evaluates after ~40 tick cooldown if still in danger.

Summon a Blaze (`/summon minecraft:blaze ~ ~ ~5`) and repeat — Blaze must not flee (fire-immune).

- [ ] **Step 8: Disable flag verification**

Set `enableCsgrenadesIntegration = false` in `config/soundattract-common.toml`, reload with `/reload` or world restart. Repeat Steps 4-7. Expected: whitelist HE attract still works (it's data-seeded, not gated). Flashbang / smoke / fire-flee behaviors all inert.

- [ ] **Step 9: Absence verification**

Remove `csgrenades` jar from `mods/`. Launch. Expected: server starts cleanly, no `ClassNotFoundException`, log prints `[CsGrenadesIntegration] csgrenades not loaded; skipping integration.` Sound Attract runs normally for all other features.

- [ ] **Step 10: Commit verification evidence (optional)**

Take screenshots / log snippets from each verification. Commit them to `docs/verification/2026-04-25-csgrenades/` if desired:

```powershell
git add docs/verification/2026-04-25-csgrenades/
git commit -m "docs(csgrenades): in-game verification evidence"
```

---

## Chunk 4: Final Polish and Close-out

### Task 18: Schema migration idempotency check

**Files:** none modified (verification only).

- [ ] **Step 1:** With the new jar installed, launch the world once. Note the `configSchemaVersion` in `config/soundattract-common.toml` is now `17`.

- [ ] **Step 2:** Stop the server, launch again. Expected: no `Added N CS sound IDs to whitelist (v17)` log line this time (migration is guarded by `< 17`). Check the file once more: `csgrenades:hegrenade.explode` appears exactly once in each list.

- [ ] **Step 3:** (Manual) Remove one seeded entry (e.g. `csgrenades:hegrenade.detonate`) from `soundIdWhitelist`, keep `configSchemaVersion = 17`. Restart. Expected: the entry is **not** re-added (migration is one-shot by version; that's the intended contract). Document this behavior — if users want to re-seed, they can set `configSchemaVersion` back to `16`.

### Task 19: Update spec with any discovered deltas

**Files:**
- Modify: `docs/superpowers/specs/2026-04-25-csgrenades-integration-design.md` (if any design drift)

- [ ] **Step 1:** Review the final implementation against the spec. If any signature / field / behavior diverged (e.g. the `CSGrenadesAPI.INSTANCE.getServer()...` fallback chain had to be used), add a brief "Implementation Notes" section at the bottom of the spec documenting actual decisions.

- [ ] **Step 2:** Commit:

```powershell
git add docs/superpowers/specs/2026-04-25-csgrenades-integration-design.md
git commit -m "docs(csgrenades): align spec with implementation deltas"
```

### Task 20: Final close-out commit and PR-ready summary

- [ ] **Step 1:** Verify `git log --oneline` shows roughly 15-20 focused commits since the spec was written.

- [ ] **Step 2:** If running on a feature branch, push and open a PR referencing the spec:

```powershell
git push -u origin <branch>
```

PR title: `feat: CS Grenades optional integration (HE / flashbang / smoke / fire-flee)`
PR body: link to `docs/superpowers/specs/2026-04-25-csgrenades-integration-design.md` and `docs/superpowers/plans/2026-04-25-csgrenades-integration.md`.

---

## Reference: Commit Message Conventions

All commits use scoped conventional-commit style matching the repo's existing style (per recent commits in this session):

- `feat(csgrenades): <what>` — new feature
- `docs(csgrenades): <what>` — docs / spec changes
- `chore(csgrenades): <what>` — config / tooling
- `fix(csgrenades): <what>` — bug fix on the integration

## Reference: Abort Conditions

Stop the plan and surface to user if any of these occur:

1. `CSGrenadesAPI` / `CounterStrikeGrenadeEntity` / `GrenadeActivateEvent` Kotlin-to-Java interop fails and cannot be resolved in 2 attempts (Task 9 or 11)
2. `OptimizedLOS.hasLineOfSight` signature changes unexpectedly (Task 11)
3. `FovEvents.hasSmartLineOfSight` has multiple termination paths making hook placement ambiguous (Task 15)
4. `bakeConfig` is not invoked anywhere on config load (Task 16) — indicates a broader bug to raise separately
5. Any in-game verification step (Task 17) fails twice after one targeted fix
