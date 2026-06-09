# Sound Attract 6.3.3 Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Release 6.3.3 with a Turkish-locale crash fix, a wooden floor creek ambient feature, and an arrow-triggered mob investigation feature — all configurable, all server-authoritative for attraction.

**Architecture:** Two new Forge event subscribers (`FloorCreekEvents`, `ArrowInvestigationEvents`) plus a new `ModSounds` `DeferredRegister`. Both features feed the existing `SoundTracker` pipeline; no changes to AttractionGoal pathing. All new behavior is guarded by feature flags in `SoundAttractConfig.Common`, with a schema migration (15 → 16) that appends new defaults to `soundIdWhitelist` / `soundDefaults` without clobbering user edits.

**Tech Stack:** Forge 1.20.1 (47.4.0), Minecraft 1.20.1, Mojang mappings, `net.minecraftforge.common.ForgeConfigSpec`, `net.minecraftforge.registries.DeferredRegister`, JUnit 5 (existing test harness).

**Relevant spec:** `docs/superpowers/specs/2026-04-23-sound-attract-6.3.3-design.md`

---

## File Structure

**New files:**
- `src/main/java/com/example/soundattract/registration/ModSounds.java` — `SoundEvent` registry.
- `src/main/java/com/example/soundattract/event/FloorCreekEvents.java` — per-player detection & sound emission.
- `src/main/java/com/example/soundattract/event/ArrowInvestigationEvents.java` — arrow tracking, near-miss + impact dispatch.
- `src/main/java/com/example/soundattract/util/ArrowInvestigationHelper.java` — origin resolution + target computation (pure, testable).
- `src/main/resources/assets/soundattract/sounds.json` — sound definitions JSON.
- `src/main/resources/assets/soundattract/sounds/floor/wooden_floor_creek_1.ogg` (moved).
- `src/main/resources/assets/soundattract/sounds/floor/wooden_floor_creek_2.ogg` (moved).
- `src/main/resources/assets/soundattract/sounds/floor/wooden_floor_creek_3.ogg` (moved).
- `src/main/resources/assets/soundattract/sounds/floor/wooden_floor_creek_4.ogg` (moved).
- `src/main/resources/data/soundattract/tags/blocks/floor_creek_blocks.json` — default block tag.
- `src/main/resources/data/soundattract/tags/entity_types/investigate_projectiles.json` — default entity tag.
- `src/test/java/com/example/soundattract/util/ArrowInvestigationHelperTest.java` — unit test.
- `src/test/java/com/example/soundattract/event/FloorCreekPoseProbabilityTest.java` — unit test.

**Modified files:**
- `gradle.properties` — `mod_version=6.3.3`.
- `src/main/java/com/example/soundattract/SoundAttractMod.java` — register `ModSounds`, register new event subscribers.
- `src/main/java/com/example/soundattract/config/separate/GeneralConfig.java` — bump default schema version to 16; add config keys for both features; append default ids to `SOUND_ID_WHITELIST` and `SOUND_DEFAULTS` lists.
- `src/main/java/com/example/soundattract/config/SoundAttractConfig.java` — expose new `COMMON` fields; migration block `v15 → v16`.
- `src/main/java/com/example/soundattract/client/AttractionClientEvents.java` — `Locale.ROOT` fix.
- `src/main/java/com/example/soundattract/config/PlayerStance.java` — `Locale.ROOT` fix.
- `src/main/java/com/example/soundattract/camo/CamoTextureGenerator.java` — two `Locale.ROOT` fixes.
- `src/main/java/com/example/soundattract/config/SoundAttractConfig.java` — one log-line `Locale.ROOT` fix (already listed above; same file).
- `src/main/resources/assets/soundattract/lang/en_us.json` — subtitle key.

**Note on file sizes:** `SoundAttractConfig.java` is ~78 KB; we're adding ~40 lines across config accessors + one migration block. `GeneralConfig.java` is ~60 KB; we're adding a new `BUILDER.push("floor_creek")` block, a `BUILDER.push("arrow_investigation")` block, and extending two default lists. Do not refactor unrelated sections.

---

## Chunk 1: Turkish locale bug fix + version bump

**Files:**
- Modify: `gradle.properties`
- Modify: `src/main/java/com/example/soundattract/client/AttractionClientEvents.java:85`
- Modify: `src/main/java/com/example/soundattract/config/PlayerStance.java:36`
- Modify: `src/main/java/com/example/soundattract/camo/CamoTextureGenerator.java:351,438`
- Modify: `src/main/java/com/example/soundattract/config/SoundAttractConfig.java:182`

### Task 1.1: Bump version

- [ ] **Step 1: Edit `gradle.properties`**

```
mod_version=6.3.3
```

Replace the existing `mod_version=6.3.2` line.

- [ ] **Step 2: Verify no other version string references need updating**

Run (PowerShell):
```powershell
Select-String -Path "src\main\resources\META-INF\mods.toml" -Pattern "version"
```
Expected: version is `${mod_version}` (interpolated). No edit needed.

- [ ] **Step 3: Commit**

```powershell
git add gradle.properties
git commit -m "chore: bump version to 6.3.3"
```

### Task 1.2: Fix `AttractionClientEvents.java` ResourceLocation path

This is the primary Turkish-locale crash site per the bug report.

- [ ] **Step 1: Write a regression-style assertion (integration, not a JUnit test)**

No test framework is set up for client-side Forge event classes. We rely on the manual test plan step below. Skip automated test for this specific edit.

- [ ] **Step 2: Apply the fix**

Change line 85 in `src/main/java/com/example/soundattract/client/AttractionClientEvents.java` from:

```java
ResourceLocation virtualSoundId = ResourceLocation.fromNamespaceAndPath("soundattract", "player_action." + action.toLowerCase());
```

to:

```java
ResourceLocation virtualSoundId = ResourceLocation.fromNamespaceAndPath("soundattract", "player_action." + action.toLowerCase(java.util.Locale.ROOT));
```

- [ ] **Step 3: Verify compile**

Run:
```powershell
.\gradlew.bat compileJava
```
Expected: `BUILD SUCCESSFUL`.

### Task 1.3: Fix `PlayerStance.java` map lookup

- [ ] **Step 1: Apply the fix**

Change line 36 in `src/main/java/com/example/soundattract/config/PlayerStance.java` from:

```java
return Optional.ofNullable(NAME_TO_STANCE_MAP.get(name.toLowerCase()));
```

to:

```java
return Optional.ofNullable(NAME_TO_STANCE_MAP.get(name.toLowerCase(java.util.Locale.ROOT)));
```

### Task 1.4: Fix `CamoTextureGenerator.java` ResourceLocation paths (x2)

- [ ] **Step 1: Apply both fixes**

Lines 351 and 438 of `src/main/java/com/example/soundattract/camo/CamoTextureGenerator.java`. In each occurrence replace `.toLowerCase()` with `.toLowerCase(java.util.Locale.ROOT)`.

Before (line 351):
```java
ResourceLocation loc = ResourceLocation.tryBuild("soundattract", name.toLowerCase().replaceAll("[^a-z0-9_/.]", "_"));
```
After:
```java
ResourceLocation loc = ResourceLocation.tryBuild("soundattract", name.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_/.]", "_"));
```

Apply the identical substitution at line 438.

### Task 1.5: Fix `SoundAttractConfig.java` log-line `toUpperCase`

- [ ] **Step 1: Apply the fix**

Line 182 of `src/main/java/com/example/soundattract/config/SoundAttractConfig.java`.

Before:
```java
SoundAttractMod.LOGGER.info("SoundAttractConfig: Added custom armor color: {} -> #{}", itemId, Integer.toHexString(color).toUpperCase());
```
After:
```java
SoundAttractMod.LOGGER.info("SoundAttractConfig: Added custom armor color: {} -> #{}", itemId, Integer.toHexString(color).toUpperCase(java.util.Locale.ROOT));
```

### Task 1.6: Verify + commit the locale sweep

- [ ] **Step 1: Grep for any remaining bare calls (whitelist false positives)**

Run (PowerShell):
```powershell
Select-String -Path "src\main\java\**\*.java" -Pattern "\.toLowerCase\(\)|\.toUpperCase\(\)" -SimpleMatch:$false
```
Expected: no matches in `src/main/java`. If any remain, apply the same transformation.

- [ ] **Step 2: Compile**

Run:
```powershell
.\gradlew.bat compileJava
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```powershell
git add src/main/java/com/example/soundattract/client/AttractionClientEvents.java `
        src/main/java/com/example/soundattract/config/PlayerStance.java `
        src/main/java/com/example/soundattract/camo/CamoTextureGenerator.java `
        src/main/java/com/example/soundattract/config/SoundAttractConfig.java
git commit -m "fix(i18n): use Locale.ROOT for case conversions to avoid Turkish-locale crash"
```

---

## Chunk 2: Wooden floor creek

### Task 2.1: Move audio assets into the mod resources

**Files:**
- Move: `floor_creek_sounds/wooden_floor_creek_1..4.ogg` → `src/main/resources/assets/soundattract/sounds/floor/wooden_floor_creek_1..4.ogg`

- [ ] **Step 1: Create target directory**

Run:
```powershell
New-Item -ItemType Directory -Force -Path "src\main\resources\assets\soundattract\sounds\floor"
```

- [ ] **Step 2: Move files**

```powershell
Move-Item "floor_creek_sounds\wooden_floor_creek_1.ogg" "src\main\resources\assets\soundattract\sounds\floor\"
Move-Item "floor_creek_sounds\wooden_floor_creek_2.ogg" "src\main\resources\assets\soundattract\sounds\floor\"
Move-Item "floor_creek_sounds\wooden_floor_creek_3.ogg" "src\main\resources\assets\soundattract\sounds\floor\"
Move-Item "floor_creek_sounds\wooden_floor_creek_4.ogg" "src\main\resources\assets\soundattract\sounds\floor\"
```

- [ ] **Step 3: Remove the now-empty source directory**

```powershell
Remove-Item -Path "floor_creek_sounds" -Recurse
```

### Task 2.2: Create `sounds.json`

**Files:**
- Create: `src/main/resources/assets/soundattract/sounds.json`

- [ ] **Step 1: Write the file**

```json
{
  "wooden_floor_creek": {
    "category": "block",
    "subtitle": "subtitles.soundattract.wooden_floor_creek",
    "sounds": [
      "soundattract:floor/wooden_floor_creek_1",
      "soundattract:floor/wooden_floor_creek_2",
      "soundattract:floor/wooden_floor_creek_3",
      "soundattract:floor/wooden_floor_creek_4"
    ]
  }
}
```

### Task 2.3: Add English subtitle

**Files:**
- Modify: `src/main/resources/assets/soundattract/lang/en_us.json`

- [ ] **Step 1: Add a JSON entry**

Append (before the closing brace) the line:
```json
"subtitles.soundattract.wooden_floor_creek": "Floorboard creaks"
```

If `en_us.json` is an object with other keys, insert a comma before this new line as needed.

### Task 2.4: Register the SoundEvent

**Files:**
- Create: `src/main/java/com/example/soundattract/registration/ModSounds.java`
- Modify: `src/main/java/com/example/soundattract/SoundAttractMod.java`

- [ ] **Step 1: Create `ModSounds`**

```java
package com.example.soundattract.registration;

import com.example.soundattract.SoundAttractMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(Registries.SOUND_EVENT, SoundAttractMod.MOD_ID);

    public static final RegistryObject<SoundEvent> WOODEN_FLOOR_CREEK =
            SOUND_EVENTS.register("wooden_floor_creek",
                    () -> SoundEvent.createVariableRangeEvent(
                            ResourceLocation.fromNamespaceAndPath(SoundAttractMod.MOD_ID, "wooden_floor_creek")));

    public static void register(IEventBus bus) {
        SOUND_EVENTS.register(bus);
    }

    private ModSounds() {}
}
```

- [ ] **Step 2: Wire into mod constructor**

In `SoundAttractMod.java`, inside the constructor (right after `ModLootModifiers.register(modEventBus);`), add:

```java
com.example.soundattract.registration.ModSounds.register(modEventBus);
```

- [ ] **Step 3: Compile**

Run:
```powershell
.\gradlew.bat compileJava
```
Expected: `BUILD SUCCESSFUL`.

### Task 2.5: Default block tag

**Files:**
- Create: `src/main/resources/data/soundattract/tags/blocks/floor_creek_blocks.json`

- [ ] **Step 1: Write the tag JSON**

```json
{
  "replace": false,
  "values": [
    { "id": "#minecraft:planks", "required": false },
    { "id": "#minecraft:wooden_slabs", "required": false },
    { "id": "#minecraft:wooden_stairs", "required": false }
  ]
}
```

### Task 2.6: Config keys — `GeneralConfig.java`

**Files:**
- Modify: `src/main/java/com/example/soundattract/config/separate/GeneralConfig.java`

- [ ] **Step 1: Bump the default schema version**

Locate line 92 (the `CONFIG_SCHEMA_VERSION` definition). Change:
```java
.defineInRange("configSchemaVersion", 15, 0, Integer.MAX_VALUE);
```
to:
```java
.defineInRange("configSchemaVersion", 16, 0, Integer.MAX_VALUE);
```

- [ ] **Step 2: Declare floor-creek field constants**

Near the top of `GeneralConfig` (where other `public static final ForgeConfigSpec.*` constants are declared), add:

```java
public static final ForgeConfigSpec.BooleanValue ENABLE_FLOOR_CREEK;
public static final ForgeConfigSpec.DoubleValue FLOOR_CREEK_PROB_SWIMMING_CRAWLING;
public static final ForgeConfigSpec.DoubleValue FLOOR_CREEK_PROB_SNEAKING;
public static final ForgeConfigSpec.DoubleValue FLOOR_CREEK_PROB_WALKING;
public static final ForgeConfigSpec.DoubleValue FLOOR_CREEK_PROB_SPRINTING;
public static final ForgeConfigSpec.DoubleValue FLOOR_CREEK_PROB_JUMP;
public static final ForgeConfigSpec.DoubleValue FLOOR_CREEK_DISTANCE_STEP;
```

- [ ] **Step 3: Build the config block**

Inside the `static { ... }` initializer, after the existing `BUILDER.push("player_action_sounds") ... BUILDER.pop()` block, insert:

```java
BUILDER.push("floor_creek");
ENABLE_FLOOR_CREEK = BUILDER.comment("Enable wooden floor creak sounds when players move on wood flooring.")
        .define("enableFloorCreek", true);
FLOOR_CREEK_PROB_SWIMMING_CRAWLING = BUILDER.comment("Probability per step for swimming/crawling pose on wood.")
        .defineInRange("floorCreekProbSwimmingCrawling", 0.40, 0.0, 1.0);
FLOOR_CREEK_PROB_SNEAKING = BUILDER.defineInRange("floorCreekProbSneaking", 0.30, 0.0, 1.0);
FLOOR_CREEK_PROB_WALKING = BUILDER.defineInRange("floorCreekProbWalking", 0.20, 0.0, 1.0);
FLOOR_CREEK_PROB_SPRINTING = BUILDER.defineInRange("floorCreekProbSprinting", 0.80, 0.0, 1.0);
FLOOR_CREEK_PROB_JUMP = BUILDER.comment("Probability applied on both jump takeoff and landing when on wood.")
        .defineInRange("floorCreekProbJump", 0.80, 0.0, 1.0);
FLOOR_CREEK_DISTANCE_STEP = BUILDER.comment("Horizontal distance (blocks) between pose-probability rolls while moving on wood.")
        .defineInRange("floorCreekDistanceStep", 2.0, 0.1, 64.0);
BUILDER.pop();
```

- [ ] **Step 4: Append default sound id + defaults**

Locate the `SOUND_ID_WHITELIST` list definition; append `"soundattract:wooden_floor_creek"` to the end of its `Arrays.asList(...)` call.

Locate the `SOUND_DEFAULTS` list definition (starts near line 159, `"minecraft:item.crossbow.shoot;16;4"`); append `"soundattract:wooden_floor_creek;20;20"` as the last entry of its `Arrays.asList(...)`.

- [ ] **Step 5: Compile**

Run:
```powershell
.\gradlew.bat compileJava
```
Expected: `BUILD SUCCESSFUL`.

### Task 2.7: Config accessors — `SoundAttractConfig.java`

**Files:**
- Modify: `src/main/java/com/example/soundattract/config/SoundAttractConfig.java`

- [ ] **Step 1: Add `COMMON` fields**

Inside the inner `Common` class, near the other `GeneralConfig`-backed fields (around line 497 where `configSchemaVersion` is declared), add:

```java
public final ForgeConfigSpec.BooleanValue enableFloorCreek = GeneralConfig.ENABLE_FLOOR_CREEK;
public final ForgeConfigSpec.DoubleValue floorCreekProbSwimmingCrawling = GeneralConfig.FLOOR_CREEK_PROB_SWIMMING_CRAWLING;
public final ForgeConfigSpec.DoubleValue floorCreekProbSneaking = GeneralConfig.FLOOR_CREEK_PROB_SNEAKING;
public final ForgeConfigSpec.DoubleValue floorCreekProbWalking = GeneralConfig.FLOOR_CREEK_PROB_WALKING;
public final ForgeConfigSpec.DoubleValue floorCreekProbSprinting = GeneralConfig.FLOOR_CREEK_PROB_SPRINTING;
public final ForgeConfigSpec.DoubleValue floorCreekProbJump = GeneralConfig.FLOOR_CREEK_PROB_JUMP;
public final ForgeConfigSpec.DoubleValue floorCreekDistanceStep = GeneralConfig.FLOOR_CREEK_DISTANCE_STEP;
```

### Task 2.8: Pose-probability pure helper + unit test

**Files:**
- Create: `src/test/java/com/example/soundattract/event/FloorCreekPoseProbabilityTest.java`
- Create: `src/main/java/com/example/soundattract/event/FloorCreekEvents.java` (skeleton with a static pure helper)

- [ ] **Step 1: Write the failing test first**

```java
package com.example.soundattract.event;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class FloorCreekPoseProbabilityTest {

    @Test
    void swimmingOnLandHighPose() {
        double p = FloorCreekEvents.poseProbability(
                true, false, false, false,
                0.40, 0.30, 0.20, 0.80);
        assertEquals(0.40, p, 1e-9);
    }

    @Test
    void swimmingInWaterReturnsZero() {
        double p = FloorCreekEvents.poseProbability(
                true, true, false, false,
                0.40, 0.30, 0.20, 0.80);
        assertEquals(0.0, p, 1e-9);
    }

    @Test
    void crouching() {
        double p = FloorCreekEvents.poseProbability(
                false, false, true, false,
                0.40, 0.30, 0.20, 0.80);
        assertEquals(0.30, p, 1e-9);
    }

    @Test
    void sprintingOverridesWalking() {
        double p = FloorCreekEvents.poseProbability(
                false, false, false, true,
                0.40, 0.30, 0.20, 0.80);
        assertEquals(0.80, p, 1e-9);
    }

    @Test
    void defaultWalk() {
        double p = FloorCreekEvents.poseProbability(
                false, false, false, false,
                0.40, 0.30, 0.20, 0.80);
        assertEquals(0.20, p, 1e-9);
    }

    @Test
    void sprintingBeatsCrouching() {
        double p = FloorCreekEvents.poseProbability(
                false, false, true, true,
                0.40, 0.30, 0.20, 0.80);
        assertEquals(0.80, p, 1e-9);
    }
}
```

- [ ] **Step 2: Run test, verify it fails because `FloorCreekEvents` does not exist**

```powershell
.\gradlew.bat test --tests com.example.soundattract.event.FloorCreekPoseProbabilityTest
```
Expected: compilation error / test failure.

- [ ] **Step 3: Create `FloorCreekEvents` with only the pure helper to make the test pass**

```java
package com.example.soundattract.event;

public final class FloorCreekEvents {

    public static double poseProbability(boolean visuallySwimming,
                                         boolean inWater,
                                         boolean crouching,
                                         boolean sprinting,
                                         double swimCrawlProb,
                                         double sneakProb,
                                         double walkProb,
                                         double sprintProb) {
        if (visuallySwimming && inWater) return 0.0;
        if (visuallySwimming) return swimCrawlProb;
        if (sprinting) return sprintProb;
        if (crouching) return sneakProb;
        return walkProb;
    }

    private FloorCreekEvents() {}
}
```

- [ ] **Step 4: Run test, verify pass**

```powershell
.\gradlew.bat test --tests com.example.soundattract.event.FloorCreekPoseProbabilityTest
```
Expected: 6 tests pass.

- [ ] **Step 5: Commit**

```powershell
git add src/test/java/com/example/soundattract/event/FloorCreekPoseProbabilityTest.java `
        src/main/java/com/example/soundattract/event/FloorCreekEvents.java
git commit -m "feat(floor-creek): pose probability helper with tests"
```

### Task 2.9: Full `FloorCreekEvents` implementation

**Files:**
- Modify: `src/main/java/com/example/soundattract/event/FloorCreekEvents.java`

- [ ] **Step 1: Rewrite to include server-tick detection**

Replace the class with (keeping the `poseProbability` helper untouched and still `public static`):

```java
package com.example.soundattract.event;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.registration.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public final class FloorCreekEvents {

    public static final TagKey<net.minecraft.world.level.block.Block> FLOOR_CREEK_BLOCKS =
            TagKey.create(net.minecraft.core.registries.Registries.BLOCK,
                    ResourceLocation.fromNamespaceAndPath("soundattract", "floor_creek_blocks"));

    private static final Map<UUID, State> STATES = new ConcurrentHashMap<>();

    public static double poseProbability(boolean visuallySwimming,
                                         boolean inWater,
                                         boolean crouching,
                                         boolean sprinting,
                                         double swimCrawlProb,
                                         double sneakProb,
                                         double walkProb,
                                         double sprintProb) {
        if (visuallySwimming && inWater) return 0.0;
        if (visuallySwimming) return swimCrawlProb;
        if (sprinting) return sprintProb;
        if (crouching) return sneakProb;
        return walkProb;
    }

    @SubscribeEvent
    public void onPlayerLogout(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() != null) {
            STATES.remove(event.getEntity().getUUID());
        }
    }

    @SubscribeEvent
    public void onServerStopping(net.minecraftforge.event.server.ServerStoppingEvent event) {
        STATES.clear();
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.player instanceof ServerPlayer serverPlayer)) return;
        if (SoundAttractConfig.COMMON == null) return;
        if (!SoundAttractConfig.COMMON.enableFloorCreek.get()) return;
        if (!serverPlayer.isAlive() || serverPlayer.isSpectator()) return;

        UUID id = serverPlayer.getUUID();
        State state = STATES.computeIfAbsent(id, k -> new State(serverPlayer.position(), serverPlayer.onGround()));

        Vec3 curr = serverPlayer.position();
        boolean onGroundNow = serverPlayer.onGround();
        double dx = Math.hypot(curr.x - state.lastPos.x, curr.z - state.lastPos.z);

        ServerLevel level = serverPlayer.serverLevel();

        boolean onWoodNow = isOnWood(level, serverPlayer);

        double jumpProb = SoundAttractConfig.COMMON.floorCreekProbJump.get();
        if (state.wasOnGround && !onGroundNow && state.wasOnWood) {
            roll(serverPlayer, level, jumpProb);
        }
        if (!state.wasOnGround && onGroundNow && onWoodNow) {
            roll(serverPlayer, level, jumpProb);
        }

        if (onGroundNow && onWoodNow) {
            state.distanceAccumulator += dx;
            double step = SoundAttractConfig.COMMON.floorCreekDistanceStep.get();
            while (state.distanceAccumulator >= step) {
                state.distanceAccumulator -= step;
                double p = poseProbability(
                        serverPlayer.isVisuallySwimming(),
                        serverPlayer.isInWater(),
                        serverPlayer.isCrouching(),
                        serverPlayer.isSprinting(),
                        SoundAttractConfig.COMMON.floorCreekProbSwimmingCrawling.get(),
                        SoundAttractConfig.COMMON.floorCreekProbSneaking.get(),
                        SoundAttractConfig.COMMON.floorCreekProbWalking.get(),
                        SoundAttractConfig.COMMON.floorCreekProbSprinting.get());
                roll(serverPlayer, level, p);
            }
        } else {
            state.distanceAccumulator = 0.0;
        }

        state.lastPos = curr;
        state.wasOnGround = onGroundNow;
        state.wasOnWood = onWoodNow;
    }

    private static boolean isOnWood(ServerLevel level, Player player) {
        if (!player.onGround()) return false;
        BlockPos feet = BlockPos.containing(player.getX(), player.getY() - 0.05, player.getZ());
        BlockState state = level.getBlockState(feet);
        return state.is(FLOOR_CREEK_BLOCKS);
    }

    private static void roll(ServerPlayer player, ServerLevel level, double probability) {
        if (probability <= 0.0) return;
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        if (rng.nextDouble() >= probability) return;
        float pitch = 0.9f + rng.nextFloat() * 0.2f;
        level.playSound(
                null,
                player.getX(), player.getY(), player.getZ(),
                ModSounds.WOODEN_FLOOR_CREEK.get(),
                SoundSource.BLOCKS,
                1.0f,
                pitch);
        if (SoundAttractConfig.COMMON.debugLogging.get()) {
            SoundAttractMod.LOGGER.debug("[FloorCreek] {} creak at {} (p={})", player.getName().getString(), player.blockPosition(), probability);
        }
    }

    private static final class State {
        Vec3 lastPos;
        boolean wasOnGround;
        boolean wasOnWood;
        double distanceAccumulator;

        State(Vec3 lastPos, boolean wasOnGround) {
            this.lastPos = lastPos;
            this.wasOnGround = wasOnGround;
            this.wasOnWood = false;
            this.distanceAccumulator = 0.0;
        }
    }
}
```

- [ ] **Step 2: Register the subscriber in `SoundAttractMod`**

In `SoundAttractMod.java`, near the other `MinecraftForge.EVENT_BUS.register(...)` calls in the constructor, add:

```java
MinecraftForge.EVENT_BUS.register(new com.example.soundattract.event.FloorCreekEvents());
```

- [ ] **Step 3: Re-run pose unit test to confirm no regression**

```powershell
.\gradlew.bat test --tests com.example.soundattract.event.FloorCreekPoseProbabilityTest
```
Expected: 6 tests pass.

- [ ] **Step 4: Compile everything**

```powershell
.\gradlew.bat compileJava
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```powershell
git add src/main/java/com/example/soundattract/event/FloorCreekEvents.java `
        src/main/java/com/example/soundattract/SoundAttractMod.java `
        src/main/java/com/example/soundattract/registration/ModSounds.java `
        src/main/java/com/example/soundattract/config/SoundAttractConfig.java `
        src/main/java/com/example/soundattract/config/separate/GeneralConfig.java `
        src/main/resources/assets/soundattract/sounds.json `
        src/main/resources/assets/soundattract/sounds/floor/ `
        src/main/resources/assets/soundattract/lang/en_us.json `
        src/main/resources/data/soundattract/tags/blocks/floor_creek_blocks.json
git commit -m "feat(floor-creek): register wooden_floor_creek sound and server-side detection"
```

### Task 2.10: Config migration for floor-creek defaults

**Files:**
- Modify: `src/main/java/com/example/soundattract/config/SoundAttractConfig.java` (inside `bakeConfig()`)

- [ ] **Step 1: Append migration block**

At the end of the existing `v15` block (after line ≈ 635 where `COMMON.configSchemaVersion.set(15);`), insert:

```java
if (COMMON.configSchemaVersion.get() < 16) {
    SoundAttractMod.LOGGER.info("Config migration: Adding floor creek + arrow investigation defaults (Schema v16).");

    java.util.List<String> whitelist = new java.util.ArrayList<>(COMMON.soundIdWhitelist.get());
    java.util.List<String> toAddWhitelist = java.util.List.of(
            "soundattract:wooden_floor_creek",
            "soundattract:arrow_whiz",
            "soundattract:arrow_impact");
    int addedWhitelist = 0;
    for (String id : toAddWhitelist) {
        if (!whitelist.contains(id)) { whitelist.add(id); addedWhitelist++; }
    }
    if (addedWhitelist > 0) {
        COMMON.soundIdWhitelist.set(whitelist);
        SoundAttractMod.LOGGER.info("Added {} sound ids to whitelist (v16).", addedWhitelist);
    }

    java.util.List<String> defaults = new java.util.ArrayList<>(COMMON.rawSoundDefaults.get());
    java.util.List<String> toAddDefaults = java.util.List.of(
            "soundattract:wooden_floor_creek;20;20",
            "soundattract:arrow_whiz;16;10",
            "soundattract:arrow_impact;24;15");
    int addedDefaults = 0;
    for (String entry : toAddDefaults) {
        String soundId = entry.split(";")[0];
        boolean present = defaults.stream().anyMatch(e -> e.startsWith(soundId + ";"));
        if (!present) { defaults.add(entry); addedDefaults++; }
    }
    if (addedDefaults > 0) {
        COMMON.rawSoundDefaults.set(defaults);
        SoundAttractMod.LOGGER.info("Added {} sound defaults (v16).", addedDefaults);
    }

    COMMON.configSchemaVersion.set(16);
}
```

- [ ] **Step 2: Compile**

```powershell
.\gradlew.bat compileJava
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```powershell
git add src/main/java/com/example/soundattract/config/SoundAttractConfig.java
git commit -m "feat(config): schema v16 migration for floor creek + arrow sounds"
```

---

## Chunk 3: Arrow investigation

### Task 3.1: Default entity-type tag

**Files:**
- Create: `src/main/resources/data/soundattract/tags/entity_types/investigate_projectiles.json`

- [ ] **Step 1: Write the tag JSON**

```json
{
  "replace": false,
  "values": [
    "minecraft:arrow",
    "minecraft:spectral_arrow"
  ]
}
```

### Task 3.2: Config keys — `GeneralConfig.java`

**Files:**
- Modify: `src/main/java/com/example/soundattract/config/separate/GeneralConfig.java`

- [ ] **Step 1: Declare field constants**

Near the other `public static final ForgeConfigSpec.*` declarations at the top of the class, add:

```java
public static final ForgeConfigSpec.BooleanValue ENABLE_ARROW_INVESTIGATION;
public static final ForgeConfigSpec.DoubleValue ARROW_NEAR_MISS_RADIUS;
public static final ForgeConfigSpec.DoubleValue ARROW_IMPACT_RADIUS;
public static final ForgeConfigSpec.IntValue ARROW_INVESTIGATION_OFFSET;
public static final ForgeConfigSpec.IntValue ARROW_INVESTIGATION_COOLDOWN_TICKS;
public static final ForgeConfigSpec.DoubleValue ARROW_REVERSE_EXTRAPOLATE_MAX_DISTANCE;
public static final ForgeConfigSpec.BooleanValue ARROW_INVESTIGATION_RESPECT_CURRENT_TARGET;
public static final ForgeConfigSpec.ConfigValue<java.util.List<? extends String>> INVESTIGATE_PROJECTILE_IDS;
```

- [ ] **Step 2: Build the config block**

Inside the `static { ... }` initializer, after the `BUILDER.push("floor_creek") ... BUILDER.pop()` block from Task 2.6, append:

```java
BUILDER.push("arrow_investigation");
ENABLE_ARROW_INVESTIGATION = BUILDER.comment("Enable mobs investigating the origin of nearby arrows.")
        .define("enableArrowInvestigation", true);
ARROW_NEAR_MISS_RADIUS = BUILDER.comment("Blocks. Distance from arrow's flight segment that triggers near-miss detection.")
        .defineInRange("arrowNearMissRadius", 2.0, 0.0, 64.0);
ARROW_IMPACT_RADIUS = BUILDER.comment("Blocks. Radius around arrow impact point that triggers landing detection.")
        .defineInRange("arrowImpactRadius", 4.0, 0.0, 64.0);
ARROW_INVESTIGATION_OFFSET = BUILDER.comment("Blocks. Random +/- XZ offset applied to the resolved arrow origin.")
        .defineInRange("arrowInvestigationOffset", 10, 0, 128);
ARROW_INVESTIGATION_COOLDOWN_TICKS = BUILDER.comment("Ticks. Per-mob cooldown between arrow investigations.")
        .defineInRange("arrowInvestigationCooldownTicks", 40, 0, 24000);
ARROW_REVERSE_EXTRAPOLATE_MAX_DISTANCE = BUILDER.comment("Blocks. Max distance to reverse-extrapolate arrow origin when owner is unknown.")
        .defineInRange("arrowReverseExtrapolateMaxDistance", 32.0, 0.0, 256.0);
ARROW_INVESTIGATION_RESPECT_CURRENT_TARGET = BUILDER.comment("Skip investigation if mob already has a combat or sound target.")
        .define("arrowInvestigationRespectCurrentTarget", true);
INVESTIGATE_PROJECTILE_IDS = BUILDER.comment(
                "Config-level allowlist of projectile entity ids that trigger mob investigation.",
                "Unioned with the entity-type tag #soundattract:investigate_projectiles.",
                "Default covers all vanilla combat projectiles.")
        .defineList("investigateProjectileIds", java.util.Arrays.asList(
                "minecraft:arrow", "minecraft:spectral_arrow", "minecraft:trident",
                "minecraft:snowball", "minecraft:egg", "minecraft:ender_pearl",
                "minecraft:experience_bottle", "minecraft:potion",
                "minecraft:fireball", "minecraft:small_fireball", "minecraft:dragon_fireball",
                "minecraft:wither_skull", "minecraft:shulker_bullet", "minecraft:llama_spit"
        ), obj -> obj instanceof String);
BUILDER.pop();
```

### Task 3.3: Config accessors — `SoundAttractConfig.java`

**Files:**
- Modify: `src/main/java/com/example/soundattract/config/SoundAttractConfig.java`

- [ ] **Step 1: Add `COMMON` fields**

Inside `Common`, near the fields added in Task 2.7, append:

```java
public final ForgeConfigSpec.BooleanValue enableArrowInvestigation = GeneralConfig.ENABLE_ARROW_INVESTIGATION;
public final ForgeConfigSpec.DoubleValue arrowNearMissRadius = GeneralConfig.ARROW_NEAR_MISS_RADIUS;
public final ForgeConfigSpec.DoubleValue arrowImpactRadius = GeneralConfig.ARROW_IMPACT_RADIUS;
public final ForgeConfigSpec.IntValue arrowInvestigationOffset = GeneralConfig.ARROW_INVESTIGATION_OFFSET;
public final ForgeConfigSpec.IntValue arrowInvestigationCooldownTicks = GeneralConfig.ARROW_INVESTIGATION_COOLDOWN_TICKS;
public final ForgeConfigSpec.DoubleValue arrowReverseExtrapolateMaxDistance = GeneralConfig.ARROW_REVERSE_EXTRAPOLATE_MAX_DISTANCE;
public final ForgeConfigSpec.BooleanValue arrowInvestigationRespectCurrentTarget = GeneralConfig.ARROW_INVESTIGATION_RESPECT_CURRENT_TARGET;
public final ForgeConfigSpec.ConfigValue<java.util.List<? extends String>> investigateProjectileIds = GeneralConfig.INVESTIGATE_PROJECTILE_IDS;
```

- [ ] **Step 1b: Add `INVESTIGATE_PROJECTILE_TYPES_CACHE` and bake it**

Near the other `*_CACHE` static fields in `SoundAttractConfig.java` (search for `CUSTOM_WOOL_BLOCKS_CACHE` for a template), add:

```java
public static final java.util.Set<net.minecraft.world.entity.EntityType<?>> INVESTIGATE_PROJECTILE_TYPES_CACHE =
        java.util.concurrent.ConcurrentHashMap.newKeySet();
```

Inside `bakeConfig()`, near the end (after the v16 migration block from Task 2.10), add the parse step:

```java
INVESTIGATE_PROJECTILE_TYPES_CACHE.clear();
if (COMMON.investigateProjectileIds != null) {
    for (String idStr : COMMON.investigateProjectileIds.get()) {
        ResourceLocation loc = ResourceLocation.tryParse(idStr);
        if (loc == null) {
            SoundAttractMod.LOGGER.warn("investigateProjectileIds: invalid id '{}'", idStr);
            continue;
        }
        net.minecraft.world.entity.EntityType<?> type =
                net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getValue(loc);
        if (type == null) {
            SoundAttractMod.LOGGER.debug("investigateProjectileIds: unknown entity type '{}' (mod not loaded?)", idStr);
            continue;
        }
        INVESTIGATE_PROJECTILE_TYPES_CACHE.add(type);
    }
}
```

- [ ] **Step 2: Compile**

```powershell
.\gradlew.bat compileJava
```
Expected: `BUILD SUCCESSFUL`.

### Task 3.4: `ArrowInvestigationHelper` pure helper + unit tests

**Files:**
- Create: `src/main/java/com/example/soundattract/util/ArrowInvestigationHelper.java`
- Create: `src/test/java/com/example/soundattract/util/ArrowInvestigationHelperTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.example.soundattract.util;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class ArrowInvestigationHelperTest {

    @Test
    void segmentToPointSquaredDistance_pointOnSegment() {
        Vec3 a = new Vec3(0, 0, 0), b = new Vec3(10, 0, 0);
        double d2 = ArrowInvestigationHelper.segmentToPointSquaredDistance(a, b, new Vec3(5, 0, 0));
        assertEquals(0.0, d2, 1e-9);
    }

    @Test
    void segmentToPointSquaredDistance_pointBeforeSegmentStart() {
        Vec3 a = new Vec3(0, 0, 0), b = new Vec3(10, 0, 0);
        double d2 = ArrowInvestigationHelper.segmentToPointSquaredDistance(a, b, new Vec3(-3, 0, 4));
        assertEquals(25.0, d2, 1e-9);
    }

    @Test
    void segmentToPointSquaredDistance_pointAfterSegmentEnd() {
        Vec3 a = new Vec3(0, 0, 0), b = new Vec3(10, 0, 0);
        double d2 = ArrowInvestigationHelper.segmentToPointSquaredDistance(a, b, new Vec3(13, 0, 4));
        assertEquals(25.0, d2, 1e-9);
    }

    @Test
    void segmentToPointSquaredDistance_perpendicular() {
        Vec3 a = new Vec3(0, 0, 0), b = new Vec3(10, 0, 0);
        double d2 = ArrowInvestigationHelper.segmentToPointSquaredDistance(a, b, new Vec3(5, 3, 4));
        assertEquals(25.0, d2, 1e-9);
    }

    @Test
    void segmentToPointSquaredDistance_degenerateSegment() {
        Vec3 a = new Vec3(2, 2, 2), b = new Vec3(2, 2, 2);
        double d2 = ArrowInvestigationHelper.segmentToPointSquaredDistance(a, b, new Vec3(5, 2, 2));
        assertEquals(9.0, d2, 1e-9);
    }

    @Test
    void randomOffset_withinBounds() {
        Random rng = new Random(42);
        for (int i = 0; i < 1000; i++) {
            double off = ArrowInvestigationHelper.randomOffset(rng, 10);
            assertTrue(off >= -10.0 && off <= 10.0, "offset " + off + " out of [-10,10]");
        }
    }

    @Test
    void randomOffset_zeroOffsetProducesZero() {
        Random rng = new Random(0);
        assertEquals(0.0, ArrowInvestigationHelper.randomOffset(rng, 0), 1e-9);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```powershell
.\gradlew.bat test --tests com.example.soundattract.util.ArrowInvestigationHelperTest
```
Expected: compilation error (class missing).

- [ ] **Step 3: Create the helper**

```java
package com.example.soundattract.util;

import net.minecraft.world.phys.Vec3;

import java.util.Random;

public final class ArrowInvestigationHelper {

    public static double segmentToPointSquaredDistance(Vec3 a, Vec3 b, Vec3 p) {
        double abx = b.x - a.x, aby = b.y - a.y, abz = b.z - a.z;
        double lenSq = abx * abx + aby * aby + abz * abz;
        if (lenSq < 1e-12) {
            double dx = p.x - a.x, dy = p.y - a.y, dz = p.z - a.z;
            return dx * dx + dy * dy + dz * dz;
        }
        double apx = p.x - a.x, apy = p.y - a.y, apz = p.z - a.z;
        double t = (apx * abx + apy * aby + apz * abz) / lenSq;
        if (t < 0.0) t = 0.0;
        else if (t > 1.0) t = 1.0;
        double cx = a.x + t * abx, cy = a.y + t * aby, cz = a.z + t * abz;
        double dx = p.x - cx, dy = p.y - cy, dz = p.z - cz;
        return dx * dx + dy * dy + dz * dz;
    }

    public static double randomOffset(Random rng, int maxOffset) {
        if (maxOffset <= 0) return 0.0;
        return (rng.nextDouble() * 2.0 - 1.0) * maxOffset;
    }

    private ArrowInvestigationHelper() {}
}
```

- [ ] **Step 4: Run tests, verify pass**

```powershell
.\gradlew.bat test --tests com.example.soundattract.util.ArrowInvestigationHelperTest
```
Expected: 7 tests pass.

- [ ] **Step 5: Commit**

```powershell
git add src/main/java/com/example/soundattract/util/ArrowInvestigationHelper.java `
        src/test/java/com/example/soundattract/util/ArrowInvestigationHelperTest.java
git commit -m "feat(arrow): segment-distance + random offset helpers with tests"
```

### Task 3.5: `ArrowInvestigationEvents` implementation (hardened)

**Files:**
- Create: `src/main/java/com/example/soundattract/event/ArrowInvestigationEvents.java`
- Modify: `src/main/java/com/example/soundattract/SoundAttractMod.java`

This implementation already incorporates the hardening requirements from §4.1 of the spec:
- `ConcurrentHashMap` for both state maps.
- Per-entry `spawnTick` with age-based prune.
- Hard size cap on `ARROWS` (4096).
- `ThreadLocalRandom.current()` instead of shared `Random`.
- Null guards for `ProjectileImpactEvent.getRayTraceResult()`, `COMMON == null`, etc.
- Generalized to `Projectile` and the union of tag + config-list allowlists.
- `ServerStoppingEvent` subscriber clears all maps.

- [ ] **Step 1: Implement the event class**

```java
package com.example.soundattract.event;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.ai.AttractionGoal;
import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.tracking.SoundTracker;
import com.example.soundattract.util.ArrowInvestigationHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ArrowInvestigationEvents {

    public static final TagKey<EntityType<?>> INVESTIGATE_PROJECTILES =
            TagKey.create(net.minecraft.core.registries.Registries.ENTITY_TYPE,
                    ResourceLocation.fromNamespaceAndPath("soundattract", "investigate_projectiles"));

    private static final int MAX_TRACKED_ARROWS = 4096;
    private static final long MAX_ARROW_AGE_TICKS = 1200L;

    private static final Map<Integer, ArrowState> ARROWS = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> COOLDOWNS = new ConcurrentHashMap<>();

    @SubscribeEvent
    public void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (SoundAttractConfig.COMMON == null) return;
        Entity e = event.getEntity();
        if (!(e instanceof Projectile projectile)) return;
        if (!isWatchedProjectile(projectile)) return;
        if (ARROWS.size() >= MAX_TRACKED_ARROWS) {
            if (SoundAttractConfig.COMMON.debugLogging.get()) {
                SoundAttractMod.LOGGER.debug("[ArrowInvestigation] ARROWS cap reached; dropping new projectile {}", projectile.getId());
            }
            return;
        }
        long now = projectile.level().getGameTime();
        ARROWS.put(projectile.getId(), new ArrowState(projectile.position(), now));
    }

    @SubscribeEvent
    public void onEntityLeave(EntityLeaveLevelEvent event) {
        Entity e = event.getEntity();
        if (e != null) {
            ARROWS.remove(e.getId());
        }
    }

    @SubscribeEvent
    public void onServerStopping(net.minecraftforge.event.server.ServerStoppingEvent event) {
        ARROWS.clear();
        COOLDOWNS.clear();
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.level instanceof ServerLevel level)) return;
        if (SoundAttractConfig.COMMON == null) return;
        if (!SoundAttractConfig.COMMON.enableArrowInvestigation.get()) return;

        final double radius = SoundAttractConfig.COMMON.arrowNearMissRadius.get();
        final double radiusSq = radius * radius;
        final long now = level.getGameTime();

        ARROWS.entrySet().removeIf(entry -> {
            ArrowState state = entry.getValue();
            if (state == null) return true;
            if (now - state.spawnTick > MAX_ARROW_AGE_TICKS) return true;

            Entity e = level.getEntity(entry.getKey());
            if (!(e instanceof Projectile projectile)) return false;
            if (projectile.level() != level) return false;
            if (!projectile.isAlive()) {
                return true;
            }
            if (projectile instanceof AbstractArrow aa && aa.inGround) {
                state.prevPos = projectile.position();
                return false;
            }

            Vec3 curr = projectile.position();
            AABB segmentAabb = new AABB(state.prevPos, curr).inflate(radius + 1.0);
            List<Mob> mobs = level.getEntitiesOfClass(Mob.class, segmentAabb, m -> m.isAlive() && hasAttractionGoal(m));
            for (Mob mob : mobs) {
                UUID mobId = mob.getUUID();
                if (state.notifiedMobIds.contains(mobId)) continue;
                Vec3 mobCenter = mob.getBoundingBox().getCenter();
                double d2 = ArrowInvestigationHelper.segmentToPointSquaredDistance(state.prevPos, curr, mobCenter);
                if (d2 <= radiusSq) {
                    state.notifiedMobIds.add(mobId);
                    dispatchInvestigation(level, projectile, state.spawnPos, mob, "arrow_whiz");
                }
            }
            state.prevPos = curr;
            return false;
        });

        COOLDOWNS.entrySet().removeIf(e -> e.getValue() == null || e.getValue() < now);
    }

    @SubscribeEvent
    public void onProjectileImpact(ProjectileImpactEvent event) {
        if (SoundAttractConfig.COMMON == null) return;
        if (!SoundAttractConfig.COMMON.enableArrowInvestigation.get()) return;
        Projectile projectile = event.getProjectile();
        if (projectile == null) return;
        if (!isWatchedProjectile(projectile)) return;
        if (!(projectile.level() instanceof ServerLevel level)) return;

        HitResult rt = event.getRayTraceResult();
        if (rt == null) return;
        Vec3 impact = rt.getLocation();
        if (impact == null) return;

        double r = SoundAttractConfig.COMMON.arrowImpactRadius.get();
        AABB scan = AABB.ofSize(impact, 2 * r, 2 * r, 2 * r);
        List<Mob> mobs = level.getEntitiesOfClass(Mob.class, scan, m -> m.isAlive() && hasAttractionGoal(m));
        ArrowState state = ARROWS.get(projectile.getId());
        Vec3 spawnPos = state != null ? state.spawnPos : projectile.position();
        for (Mob mob : mobs) {
            dispatchInvestigation(level, projectile, spawnPos, mob, "arrow_impact");
        }
    }

    private void dispatchInvestigation(ServerLevel level, Projectile projectile, Vec3 spawnPos, Mob mob, String kind) {
        if (SoundAttractConfig.COMMON.arrowInvestigationRespectCurrentTarget.get()) {
            if (mob.getTarget() != null) return;
            AttractionGoal ag = findAttractionGoal(mob);
            if (ag != null && ag.getTargetSoundPos() != null) return;
        }

        long now = level.getGameTime();
        Long cdEnd = COOLDOWNS.get(mob.getUUID());
        if (cdEnd != null && cdEnd > now) return;
        int cooldown = SoundAttractConfig.COMMON.arrowInvestigationCooldownTicks.get();
        COOLDOWNS.put(mob.getUUID(), now + cooldown);

        Vec3 origin = resolveOrigin(level, projectile, spawnPos);
        int off = SoundAttractConfig.COMMON.arrowInvestigationOffset.get();
        java.util.concurrent.ThreadLocalRandom rng = java.util.concurrent.ThreadLocalRandom.current();
        double tx = origin.x + ArrowInvestigationHelper.randomOffset(rng, off);
        double tz = origin.z + ArrowInvestigationHelper.randomOffset(rng, off);
        int ty = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                (int) Math.floor(tx), (int) Math.floor(tz));
        BlockPos target = BlockPos.containing(tx, ty, tz);

        String soundId = "soundattract:" + kind;
        double range = 16.0;
        double weight = 10.0;
        ResourceLocation loc = ResourceLocation.tryParse(soundId);
        if (loc != null && SoundAttractConfig.SOUND_DEFAULT_ENTRIES_CACHE != null) {
            Double[] rw = SoundAttractConfig.SOUND_DEFAULT_ENTRIES_CACHE.get(loc);
            if (rw != null && rw.length >= 2 && rw[0] != null && rw[1] != null) {
                range = rw[0];
                weight = rw[1];
            }
        }

        UUID sourcePlayer = (projectile.getOwner() instanceof net.minecraft.world.entity.player.Player p)
                ? p.getUUID() : null;
        String dimKey = level.dimension().location().toString();
        int lifetime = SoundAttractConfig.COMMON.soundLifetimeTicks.get();
        try {
            SoundTracker.addVirtualSound(target, dimKey, range, weight, lifetime, sourcePlayer, "arrow_investigation");
        } catch (Throwable t) {
            SoundAttractMod.LOGGER.error("[ArrowInvestigation] addVirtualSound failed", t);
            return;
        }

        if (SoundAttractConfig.COMMON.debugLogging.get()) {
            Entity owner = projectile.getOwner();
            SoundAttractMod.LOGGER.debug("[ArrowInvestigation] {} -> mob={} at {} (kind={})",
                    owner != null ? owner.getName().getString() : "unknown",
                    mob.getName().getString(), target, kind);
        }
    }

    private static boolean isWatchedProjectile(Projectile projectile) {
        EntityType<?> type = projectile.getType();
        if (type.is(INVESTIGATE_PROJECTILES)) return true;
        java.util.Set<EntityType<?>> cache = SoundAttractConfig.INVESTIGATE_PROJECTILE_TYPES_CACHE;
        return cache != null && cache.contains(type);
    }

    private static Vec3 resolveOrigin(ServerLevel level, Projectile projectile, Vec3 spawnPos) {
        Entity owner = projectile.getOwner();
        if (owner != null) {
            Vec3 p = owner.position();
            if (p != null) return p;
        }
        if (spawnPos != null) return spawnPos;

        Vec3 velocity = projectile.getDeltaMovement();
        if (velocity == null || velocity.lengthSqr() < 1.0e-6) return projectile.position();
        Vec3 dir = velocity.normalize();
        double maxDist = SoundAttractConfig.COMMON.arrowReverseExtrapolateMaxDistance.get();
        Vec3 start = projectile.position();
        Vec3 end = start.subtract(dir.scale(maxDist));
        BlockHitResult hit = level.clip(new ClipContext(start, end,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, projectile));
        return hit.getType() == HitResult.Type.MISS ? end : hit.getLocation();
    }

    private static boolean hasAttractionGoal(Mob mob) {
        return findAttractionGoal(mob) != null;
    }

    private static AttractionGoal findAttractionGoal(Mob mob) {
        if (mob == null || mob.goalSelector == null) return null;
        for (WrappedGoal wg : mob.goalSelector.getAvailableGoals()) {
            Goal g = wg.getGoal();
            if (g instanceof AttractionGoal ag) return ag;
        }
        return null;
    }

    private static final class ArrowState {
        Vec3 prevPos;
        final Vec3 spawnPos;
        final long spawnTick;
        final Set<UUID> notifiedMobIds = ConcurrentHashMap.newKeySet();

        ArrowState(Vec3 spawn, long spawnTick) {
            this.prevPos = spawn;
            this.spawnPos = spawn;
            this.spawnTick = spawnTick;
        }
    }
}
```

- [ ] **Step 2: Register the subscriber**

In `SoundAttractMod.java` constructor, near `FloorCreekEvents` registration:

```java
MinecraftForge.EVENT_BUS.register(new com.example.soundattract.event.ArrowInvestigationEvents());
```

- [ ] **Step 3: Compile**

```powershell
.\gradlew.bat compileJava
```

If `AttractionGoal.getTargetSoundPos()` is not a public method in the current branch, verify by reading `src/main/java/com/example/soundattract/ai/AttractionGoal.java:566` — it returns `targetSoundPos`. If the name differs in your branch, update the call site accordingly.

If `SoundAttractConfig.SOUND_DEFAULT_ENTRIES_CACHE` has a different value type (e.g. `double[]` instead of `Double[]`), adjust the unboxing in `dispatchInvestigation` accordingly. Check the declaration near `SOUND_DEFAULT_ENTRIES_CACHE` in `SoundAttractConfig.java`.

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Run all unit tests to confirm no regression**

```powershell
.\gradlew.bat test
```
Expected: all tests pass.

- [ ] **Step 5: Commit**

```powershell
git add src/main/java/com/example/soundattract/event/ArrowInvestigationEvents.java `
        src/main/java/com/example/soundattract/SoundAttractMod.java `
        src/main/java/com/example/soundattract/config/SoundAttractConfig.java `
        src/main/java/com/example/soundattract/config/separate/GeneralConfig.java `
        src/main/resources/data/soundattract/tags/entity_types/investigate_projectiles.json
git commit -m "feat(arrow): mob investigation on arrow near-miss and impact"
```

---

## Chunk 4: Full-build verification + manual smoke tests

### Task 4.1: Full build

- [ ] **Step 1: Clean build**

```powershell
.\gradlew.bat clean build
```
Expected: `BUILD SUCCESSFUL`, jar in `build/libs/`.

### Task 4.2: Turkish locale smoke test

- [ ] **Step 1: Launch client with Turkish JVM locale**

Add `-Duser.language=tr -Duser.country=TR` to run-client JVM args (IDE run config or `gradle.properties`).

- [ ] **Step 2: Walk on grass (generates step sound with `action=WALKING`)**

Expected: no crash, player_action virtual sound packet sent normally.

### Task 4.3: Floor creek in-game test

- [ ] **Step 1: Place a 20-block plank bridge, a 20-block oak-slab bridge, and a 20-block stone bridge**

- [ ] **Step 2: Walk each surface at each pose (walk, sneak, sprint, crawl under a slab, jump)**

Expected:
- Plank + slab: creaks fire at approximately configured probabilities (subjective; verify in log with `debugLogging=true`).
- Stone: no creak.
- Jump on plank: up to two creaks (takeoff + landing) at 80% each.

### Task 4.4: Arrow investigation in-game test

- [ ] **Step 1: Spawn an idle zombie. Stand 15 blocks away and shoot an arrow past it (within 2 blocks)**

Expected: zombie begins walking toward shooter's approximate area (within ±10 blocks).

- [ ] **Step 2: Shoot a volley of 5 arrows quickly**

Expected: only one investigation per zombie per 2-second window.

- [ ] **Step 3: Set up a dispenser firing arrows past a villager**

Expected: villager investigates reverse-extrapolated origin (the dispenser side).

- [ ] **Step 4: Engage a zombie in combat; fire an arrow past it**

Expected: investigation skipped (combat target takes priority).

### Task 4.5: Tag: Config repair smoke test

- [ ] **Step 1: With an old v15 config file on disk (remove new entries and set schema to 15), start the game**

Expected: `bakeConfig` logs `Schema v16` migration, appends three sound ids to whitelist and three defaults.

### Task 4.6: Final commit + tag

- [ ] **Step 1: Verify changelog**

If `CHANGELOG.md` exists at repo root, append a 6.3.3 section. Otherwise skip.

- [ ] **Step 2: Commit any outstanding file**

```powershell
git status
git add -A
git commit -m "chore: 6.3.3 finalization" --allow-empty
```

- [ ] **Step 3: Tag**

```powershell
git tag v6.3.3
```

---

## Risks & Notes

- **`AttractionGoal.getTargetSoundPos()` visibility.** The current code declares `public BlockPos getTargetSoundPos()` at `AttractionGoal.java:566`. If the name differs in the worktree you're executing from, adjust the single call site in `ArrowInvestigationEvents.dispatchInvestigation`.
- **`SoundTracker.addVirtualSound` signature.** Confirmed at `SoundTracker.java:397`: `addVirtualSound(BlockPos, String, double, double, int, UUID, String)`. If the signature changes, update the call.
- **`ProjectileImpactEvent` caveat.** This event fires on both block and entity impacts. The implementation does not distinguish; both should trigger investigation per spec. Do not cancel the event.
- **`ARROWS` cleanup.** Arrows that disappear without `EntityLeaveLevelEvent` (chunk unload edge cases) are cleaned on next tick by the `level.getEntity(id)` null check via the `removeIf` body (entries where `e` isn't an arrow or is dead stay in the map until leave event fires). If memory growth is a concern on long sessions, add a size-based eviction. Not required for 6.3.3.
- **Unit test harness.** The repository does not appear to have pre-existing JUnit tests. If `./gradlew test` fails because the test source set isn't configured, add `testImplementation 'org.junit.jupiter:junit-jupiter:5.10.2'` (or the version already on classpath) to `build.gradle` and ensure `test { useJUnitPlatform() }` is set. This is a minor `build.gradle` addition; gate on whether existing test infrastructure is present before inventing new config.
- **`SoundAttractConfig.SOUND_DEFAULT_ENTRIES_CACHE` type.** Verify the declared type before using `Double[]` vs `double[]` in `ArrowInvestigationEvents.dispatchInvestigation`. If it's a `Map<ResourceLocation, double[]>`, unbox accordingly.

---

## Part 5 — Addendum (2026-04-23): Arrow-goal fix + server-authoritative config migration

### 5.1 Context

Three post-implementation items surfaced after the initial 6.3.3 tasks were completed:

1. **Arrow investigation coverage gap.** `ArrowInvestigationEvents.findAttractionGoal()` only recognizes `AttractionGoal`. Mobs running `LeaderAttractionGoal` or `FollowerEdgeRelayGoal` (enabled whenever `edgeMobSmartBehavior` is ON, or when group-role assignment promotes a mob) are filtered out of the near-miss scan and impact scan. Result: arrows flying past or landing near such mobs produce no investigation. User reproduction: default config, idle zombie, arrow past within ~1 block → no reaction.
2. **Invisibility render override.** The mod currently hides armor, held items, and camo layers when `entity.isInvisible()` via four mixin injection sites. Users want a server-authoritative toggle so admins can force "render gear through invisibility" regardless of client preference.
3. **Client-authoritative config holes.** Several gameplay-critical keys currently live in `COMMON` configs but are read by client-only code (`AttractionClientEvents`, `PointBlankGunItemMixin` client paths). In multiplayer a client can set them in their local file and bypass server rules — effectively a silent cheat surface.

   Specifically:
   - `enablePlayerActionSounds`, `playerActionRanges`, `playerActionWeights`, `playerActionCheckRadius` — read on client in `AttractionClientEvents` to decide whether/how to emit footstep/action sound messages to the server. Client with `enablePlayerActionSounds=false` walks silently regardless of server config.
   - `enablePointBlankIntegration`, `enableTaczIntegration`, `enableVoiceChatIntegration` — gameplay integration toggles that must match on both sides.

Standard Forge SERVER config behavior (auto-synced to clients on login and reload) fixes all three without a custom packet.

### 5.2 Goals

- Arrow fix applies to all sound-pursuit goal types without regressing the existing path.
- A single new SERVER-type spec, written to `<world>/serverconfig/soundattract/server-rules.toml` (dedicated server: `<server>/world/serverconfig/...`), containing:
  - `[rendering] hideWornGearWhileInvisible` — default `true` (preserves current hiding behavior).
  - `[player_action_sounds]` — `enablePlayerActionSounds`, `playerActionRanges`, `playerActionWeights`, `playerActionCheckRadius` — defaults match current COMMON defaults.
  - `[integrations]` — `enablePointBlankIntegration`, `enableTaczIntegration`, `enableVoiceChatIntegration` — defaults match current COMMON defaults.
- Reading code reads only the SERVER spec going forward. No dual reads.
- One-time migration on server start: if the SERVER spec values equal their defaults **and** the corresponding COMMON values are non-default, copy COMMON → SERVER so existing user customizations survive. Log each migrated key. Idempotent after first run.
- Old COMMON keys remain in spec (to avoid breaking users' COMMON toml parsing) but are marked deprecated in comments and are no longer read by runtime code.

### 5.3 Non-goals

- No changes to `SoundMessage` wire protocol. The client-supplied `range` / `weight` trust issue (client can send `range=0`) is a separate, larger redesign and is not addressed here.
- No new rendering layers, no camo detection changes, no vanilla invisibility particle/opacity changes.
- Stealth, LOS, AI, and scent configs stay in COMMON — they are read only on the server, so COMMON is sufficient and their file locations should not move (would break many users' configs).

---

### Task 5.1: Arrow goal coverage fix

Touch one file: `src/main/java/com/example/soundattract/event/ArrowInvestigationEvents.java` and add one getter to `FollowerEdgeRelayGoal`.

- [ ] **Step 1: Add `getTargetSoundPos()` to `FollowerEdgeRelayGoal`**

File: `src/main/java/com/example/soundattract/ai/FollowerEdgeRelayGoal.java`

Append a public getter near the end of the class (next to other public state accessors; `LeaderAttractionGoal.getTargetSoundPos()` at line 440 is the reference pattern):

```java
public BlockPos getTargetSoundPos() {
    return this.targetSoundPos;
}
```

Rationale: parity with `AttractionGoal` and `LeaderAttractionGoal`, avoids reflection in `ArrowInvestigationEvents`.

- [ ] **Step 2: Generalize goal lookup in `ArrowInvestigationEvents`**

Replace `findAttractionGoal` / `hasAttractionGoal` with a pair of helpers that cover all three sound-pursuit goal types.

File: `src/main/java/com/example/soundattract/event/ArrowInvestigationEvents.java`

Add imports:

```java
import com.example.soundattract.ai.LeaderAttractionGoal;
import com.example.soundattract.ai.FollowerEdgeRelayGoal;
```

Replace the existing `hasAttractionGoal` / `findAttractionGoal` methods with:

```java
private static boolean hasSoundPursuitGoal(Mob mob) {
    if (mob == null || mob.goalSelector == null) return false;
    for (WrappedGoal wg : mob.goalSelector.getAvailableGoals()) {
        Goal g = wg.getGoal();
        if (g instanceof AttractionGoal
                || g instanceof LeaderAttractionGoal
                || g instanceof FollowerEdgeRelayGoal) {
            return true;
        }
    }
    return false;
}

private static BlockPos getMobSoundTargetPos(Mob mob) {
    if (mob == null || mob.goalSelector == null) return null;
    for (WrappedGoal wg : mob.goalSelector.getAvailableGoals()) {
        Goal g = wg.getGoal();
        if (g instanceof AttractionGoal ag) {
            return ag.getTargetSoundPos();
        }
        if (g instanceof LeaderAttractionGoal lag) {
            return lag.getTargetSoundPos();
        }
        if (g instanceof FollowerEdgeRelayGoal feg) {
            return feg.getTargetSoundPos();
        }
    }
    return null;
}
```

- [ ] **Step 3: Update call sites**

In `onServerTick`:

```java
List<Mob> mobs = level.getEntitiesOfClass(Mob.class, segmentAabb, m -> m.isAlive() && hasSoundPursuitGoal(m));
```

In `onProjectileImpact`:

```java
List<Mob> mobs = level.getEntitiesOfClass(Mob.class, scan, m -> m.isAlive() && hasSoundPursuitGoal(m));
```

In `dispatchInvestigation`, replace the `findAttractionGoal(mob)` / `ag.getTargetSoundPos()` block with:

```java
if (SoundAttractConfig.COMMON.arrowInvestigationRespectCurrentTarget.get()) {
    if (mob.getTarget() != null) return;
    BlockPos existing = getMobSoundTargetPos(mob);
    if (existing != null) return;
}
```

- [ ] **Step 4: Delete the now-unused `AttractionGoal` import reference if the compiler warns**

`AttractionGoal` is still referenced inside `hasSoundPursuitGoal` / `getMobSoundTargetPos`, so the import stays. No action needed unless your IDE prunes on save.

- [ ] **Step 5: Build**

```powershell
./gradlew compileJava
```

Expected: clean compile.

---

### Task 5.2: New SERVER-type spec `Server` with all migrated keys

- [ ] **Step 1: Create the `Server` inner class in `SoundAttractConfig`**

File: `src/main/java/com/example/soundattract/config/SoundAttractConfig.java`

Near the existing `COMMON` / `COMMON_SPEC` declarations (around line 37-44), add an inner `Server` class and a paired spec. Field order matches file section order to keep generated TOML readable.

```java
public static class Server {
    // [rendering]
    public final ForgeConfigSpec.BooleanValue hideWornGearWhileInvisible;

    // [player_action_sounds]
    public final ForgeConfigSpec.BooleanValue enablePlayerActionSounds;
    public final ForgeConfigSpec.ConfigValue<List<? extends String>> playerActionRanges;
    public final ForgeConfigSpec.ConfigValue<List<? extends String>> playerActionWeights;
    public final ForgeConfigSpec.DoubleValue playerActionCheckRadius;

    // [integrations]
    public final ForgeConfigSpec.BooleanValue enablePointBlankIntegration;
    public final ForgeConfigSpec.BooleanValue enableTaczIntegration;
    public final ForgeConfigSpec.BooleanValue enableVoiceChatIntegration;

    Server(ForgeConfigSpec.Builder b) {
        b.push("rendering");
        hideWornGearWhileInvisible = b
                .comment("If true, mob/player armor, held items, and Sound Attract camo layers are hidden while the entity has invisibility.",
                        "If false, those layers render normally through invisibility.",
                        "Server-authoritative: connected clients use this value regardless of their local setting.")
                .define("hideWornGearWhileInvisible", true);
        b.pop();

        b.push("player_action_sounds");
        enablePlayerActionSounds = b
                .comment("Enable/disable virtual sound generation from player movements.",
                        "Read on the client before sending footstep messages; moved to SERVER so admins control it.")
                .define("enablePlayerActionSounds", true);
        playerActionRanges = b
                .comment("Base detection ranges for player actions. Format: 'ACTION;range'")
                .defineList("playerActionRanges", java.util.Arrays.asList(
                        "CRAWLING;3", "SNEAKING;5", "WALKING;10", "SPRINTING;16", "SPRINT_JUMPING;20"
                ), obj -> obj instanceof String && ((String) obj).split(";").length == 2);
        playerActionWeights = b
                .comment("Weights for player action sounds. Format: 'ACTION;weight'")
                .defineList("playerActionWeights", java.util.Arrays.asList(
                        "CRAWLING;1.0", "SNEAKING;1.0", "WALKING;1.0", "SPRINTING;1.0", "SPRINT_JUMPING;1.0"
                ), obj -> obj instanceof String && ((String) obj).split(";").length == 2);
        playerActionCheckRadius = b
                .comment("Radius within which to check for players to generate sounds.")
                .defineInRange("playerActionCheckRadius", 24.0, 0.0, 512.0);
        b.pop();

        b.push("integrations");
        enablePointBlankIntegration = b
                .comment("Enable Point Blank gun integration.")
                .define("enablePointBlankIntegration", true);
        enableTaczIntegration = b
                .comment("Enable Tacz gun integration.")
                .define("enableTaczIntegration", true);
        enableVoiceChatIntegration = b
                .comment("Enable Simple Voice Chat integration.")
                .define("enableVoiceChatIntegration", true);
        b.pop();
    }
}

public static final Server SERVER;
public static final ForgeConfigSpec SERVER_SPEC;

static {
    final org.apache.commons.lang3.tuple.Pair<Server, ForgeConfigSpec> srvPair =
            new ForgeConfigSpec.Builder().configure(Server::new);
    SERVER = srvPair.getLeft();
    SERVER_SPEC = srvPair.getRight();
}
```

Add safe accessors (client render code may run before the SERVER config is synced; fall back to the legacy default):

```java
public static boolean shouldHideWornGearWhileInvisible() {
    try {
        if (SERVER_SPEC != null && SERVER_SPEC.isLoaded()) {
            return SERVER.hideWornGearWhileInvisible.get();
        }
    } catch (Throwable ignored) {
    }
    return true;
}

public static boolean serverReady() {
    return SERVER_SPEC != null && SERVER_SPEC.isLoaded();
}
```

- [ ] **Step 2: Register the SERVER spec**

File: `src/main/java/com/example/soundattract/config/ConfigHelper.java`

Inside `register(ModLoadingContext context)`, after the existing `registerConfig` calls, add:

```java
context.registerConfig(ModConfig.Type.SERVER, SoundAttractConfig.SERVER_SPEC, "soundattract/server-rules.toml");
```

- [ ] **Step 3: Deprecate (but keep) the old COMMON keys**

Do NOT remove the COMMON declarations in `GeneralConfig.java` (`ENABLE_PLAYER_ACTION_SOUNDS`, `PLAYER_ACTION_RANGES`, `PLAYER_ACTION_WEIGHTS`, `PLAYER_ACTION_CHECK_RADIUS`), `GunsConfig.java` (`ENABLE_POINT_BLANK_INTEGRATION`, `ENABLE_TACZ_INTEGRATION`), or `VoiceConfig.java` (`ENABLE_VOICE_CHAT_INTEGRATION`). Removing them would invalidate existing `common`/`guns`/`voice` toml files and trigger Forge "unknown entry" warnings.

Instead prepend a `[DEPRECATED]` marker to each key's comment string. Example in `GeneralConfig.java`:

```java
ENABLE_PLAYER_ACTION_SOUNDS = BUILDER.comment(
        "[DEPRECATED as of 6.3.3] Moved to soundattract/server-rules.toml (SERVER config).",
        "The value here is no longer read at runtime; kept only so existing config files do not error.")
        .define("enablePlayerActionSounds", true);
```

Do the same for the other six keys. Keep defaults unchanged so migration logic can reliably distinguish "user customized" from "still default".

- [ ] **Step 4: Confirm build**

```powershell
./gradlew compileJava
```

---

### Task 5.3: Swap reader code from COMMON → SERVER

- [ ] **Step 1: `AttractionClientEvents`**

File: `src/main/java/com/example/soundattract/client/AttractionClientEvents.java`

Replace:

```java
if (FEAR_OF_SOUND_LOADED || !SoundAttractConfig.PLAYER_ACTION_SOUNDS_ENABLED_CACHE) {
    return;
}
```

with:

```java
if (FEAR_OF_SOUND_LOADED) return;
if (!SoundAttractConfig.serverReady()) return;
if (!SoundAttractConfig.SERVER.enablePlayerActionSounds.get()) return;
```

Replace:

```java
double checkRadius = com.example.soundattract.config.SoundAttractConfig.COMMON.playerActionCheckRadius.get();
```

with:

```java
double checkRadius = SoundAttractConfig.SERVER.playerActionCheckRadius.get();
```

The `PLAYER_ACTION_RANGES_CACHE` / `PLAYER_ACTION_WEIGHTS_CACHE` maps are populated in `parseAndCachePlayerActionConfig()` — update that method (see next step) to read from SERVER.

- [ ] **Step 2: `SoundAttractConfig.parseAndCachePlayerActionConfig()`**

File: `src/main/java/com/example/soundattract/config/SoundAttractConfig.java`

Replace the three reads:

```java
PLAYER_ACTION_SOUNDS_ENABLED_CACHE = COMMON.enablePlayerActionSounds.get();
...
List<? extends String> ranges = COMMON.playerActionRanges.get();
...
List<? extends String> weights = COMMON.playerActionWeights.get();
```

with the SERVER equivalents, guarded so it runs only after the SERVER spec is loaded:

```java
if (!serverReady()) return;
PLAYER_ACTION_SOUNDS_ENABLED_CACHE = SERVER.enablePlayerActionSounds.get();
PLAYER_ACTION_RANGES_CACHE.clear();
PLAYER_ACTION_WEIGHTS_CACHE.clear();

List<? extends String> ranges = SERVER.playerActionRanges.get();
... (rest unchanged)

List<? extends String> weights = SERVER.playerActionWeights.get();
... (rest unchanged)
```

Ensure `parseAndCachePlayerActionConfig()` is called on both SERVER config loading/reloading (not only COMMON) — find the existing `@SubscribeEvent` hook for `ModConfigEvent.Loading`/`Reloading` and gate the call on the spec being the SERVER spec as well.

- [ ] **Step 3: Integration toggle read sites**

In `SoundAttractConfig.java` `bakeConfig` (lines ~822 and ~910):

```java
TACZ_ENABLED_CACHE = ModList.get().isLoaded("tacz") && COMMON.enableTaczIntegration.get();
...
POINT_BLANK_ENABLED_CACHE = ModList.get().isLoaded("pointblank") && COMMON.enablePointBlankIntegration.get();
```

Replace with:

```java
TACZ_ENABLED_CACHE = ModList.get().isLoaded("tacz") && serverReady() && SERVER.enableTaczIntegration.get();
...
POINT_BLANK_ENABLED_CACHE = ModList.get().isLoaded("pointblank") && serverReady() && SERVER.enablePointBlankIntegration.get();
```

Grep for any other `COMMON.enablePointBlankIntegration` / `COMMON.enableTaczIntegration` / `COMMON.enableVoiceChatIntegration` read sites and swap each to the SERVER equivalent with a `serverReady()` short-circuit that returns `false` when the SERVER spec isn't yet loaded. Do NOT swap sites that are only accessed from server-side event handlers after server start — those are unreachable until SERVER is loaded — but the `serverReady()` guard is cheap insurance.

Search command:

```powershell
rg "COMMON\.(enablePointBlankIntegration|enableTaczIntegration|enableVoiceChatIntegration|enablePlayerActionSounds|playerActionRanges|playerActionWeights|playerActionCheckRadius)" src/main
```

Expected: only the declaration in `SoundAttractConfig.Common` should remain.

- [ ] **Step 4: Confirm build**

```powershell
./gradlew compileJava
```

---

### Task 5.4: One-time migration: COMMON → SERVER

On SERVER spec loading, if the SERVER value equals its default and the corresponding COMMON value is non-default, copy COMMON → SERVER. Log each migration. Idempotent: once a SERVER value is customized (no longer default), it is never overwritten.

- [ ] **Step 1: Add migration routine in `SoundAttractConfig`**

```java
private static boolean serverMigrationDone = false;

public static void migrateCommonToServerIfNeeded() {
    if (serverMigrationDone) return;
    if (!serverReady()) return;
    if (COMMON_SPEC == null || !COMMON_SPEC.isLoaded()) return;

    int migrated = 0;
    migrated += migrateBool("enablePlayerActionSounds",
            COMMON.enablePlayerActionSounds, SERVER.enablePlayerActionSounds);
    migrated += migrateDouble("playerActionCheckRadius",
            COMMON.playerActionCheckRadius, SERVER.playerActionCheckRadius);
    migrated += migrateStringList("playerActionRanges",
            COMMON.playerActionRanges, SERVER.playerActionRanges);
    migrated += migrateStringList("playerActionWeights",
            COMMON.playerActionWeights, SERVER.playerActionWeights);
    migrated += migrateBool("enablePointBlankIntegration",
            COMMON.enablePointBlankIntegration, SERVER.enablePointBlankIntegration);
    migrated += migrateBool("enableTaczIntegration",
            COMMON.enableTaczIntegration, SERVER.enableTaczIntegration);
    migrated += migrateBool("enableVoiceChatIntegration",
            COMMON.enableVoiceChatIntegration, SERVER.enableVoiceChatIntegration);

    if (migrated > 0) {
        SoundAttractMod.LOGGER.info(
            "[SoundAttract] Migrated {} config keys from COMMON (deprecated) to SERVER spec.",
            migrated);
        SERVER_SPEC.save();
    }
    serverMigrationDone = true;
}

private static int migrateBool(String name,
                               ForgeConfigSpec.BooleanValue src,
                               ForgeConfigSpec.BooleanValue dst) {
    boolean srvDefault = (Boolean) ((ForgeConfigSpec.ValueSpec)
            SERVER_SPEC.get(dst.getPath())).getDefault();
    boolean srvCurr = dst.get();
    if (srvCurr != srvDefault) return 0; // already customized
    boolean comCurr = src.get();
    if (comCurr == srvDefault) return 0;   // COMMON still default
    dst.set(comCurr);
    SoundAttractMod.LOGGER.info("[SoundAttract] migrate {}: {} -> SERVER", name, comCurr);
    return 1;
}

private static int migrateDouble(String name,
                                 ForgeConfigSpec.DoubleValue src,
                                 ForgeConfigSpec.DoubleValue dst) { /* analogous */ }

private static int migrateStringList(String name,
                                     ForgeConfigSpec.ConfigValue<List<? extends String>> src,
                                     ForgeConfigSpec.ConfigValue<List<? extends String>> dst) { /* analogous */ }
```

Implement `migrateDouble` and `migrateStringList` analogously — compare current vs default, copy if SERVER is still default and COMMON diverges. For the list case, use `new java.util.ArrayList<>(src.get())` when calling `dst.set(...)`.

- [ ] **Step 2: Invoke on server start**

Hook into Forge's `ServerStartingEvent` (or the existing `ServerAboutToStartEvent` listener if one exists):

```java
@SubscribeEvent
public static void onServerAboutToStart(ServerAboutToStartEvent event) {
    SoundAttractConfig.migrateCommonToServerIfNeeded();
}
```

Place this in an existing forge-bus subscriber class (e.g., `SoundAttractMod` or `SoundAttractionEvents`) or create a tiny new subscriber for it. Migration runs once per server lifecycle; `serverMigrationDone` prevents re-runs.

- [ ] **Step 3: Confirm build**

```powershell
./gradlew compileJava
```

---

### Task 5.5: Invisibility render override — mixin gates

Gate all four existing `isInvisible()`-based cancel sites behind `SoundAttractConfig.shouldHideWornGearWhileInvisible()`. When the helper returns `false`, the mixin must fall through (no cancel) so vanilla / mod rendering proceeds.

- [ ] **Step 1: `HumanoidArmorLayerMixin` — HEAD cancel**

File: `src/main/java/com/example/soundattract/mixin/HumanoidArmorLayerMixin.java`

Replace:

```java
if (entity.isInvisible()) {
    ci.cancel();
    return;
}
```

with:

```java
if (entity.isInvisible() && SoundAttractConfig.shouldHideWornGearWhileInvisible()) {
    ci.cancel();
    return;
}
```

Add the import `import com.example.soundattract.config.SoundAttractConfig;` if missing.

- [ ] **Step 2: `HumanoidArmorLayerMixin` — TAIL camo guard**

In the `soundattract$tintArmorWithCamo` method, replace:

```java
if (entity.isInvisible()) return;
```

with:

```java
if (entity.isInvisible() && SoundAttractConfig.shouldHideWornGearWhileInvisible()) return;
```

- [ ] **Step 3: `ItemInHandLayerMixin`**

File: `src/main/java/com/example/soundattract/mixin/ItemInHandLayerMixin.java`

Replace:

```java
if (entity.isInvisible()) {
    ci.cancel();
}
```

with:

```java
if (entity.isInvisible() && com.example.soundattract.config.SoundAttractConfig.shouldHideWornGearWhileInvisible()) {
    ci.cancel();
}
```

- [ ] **Step 4: `MixinGeoArmorRenderer`**

File: `src/main/java/com/example/soundattract/mixin/gecko/MixinGeoArmorRenderer.java`

Replace:

```java
if (living.isInvisible()) return;
```

with:

```java
if (living.isInvisible() && com.example.soundattract.config.SoundAttractConfig.shouldHideWornGearWhileInvisible()) return;
```

- [ ] **Step 5: Build**

```powershell
./gradlew build
```

Expected: clean build, no mixin refmap warnings for the touched classes.

---

### Task 5.6: Verification

- [ ] **Step 1: Arrow fix regression test (edgeMobSmartBehavior = OFF)**

Default config. Spawn idle zombie, shoot arrow within 2 blocks. Expected: zombie investigates (same as 6.3.3 Part 4).

- [ ] **Step 2: Arrow fix with edgeMobSmartBehavior = ON**

Enable `edgeMobSmartBehavior=true`. Spawn a group of 4+ zombies so the group manager promotes a leader and edge mobs. Shoot an arrow past an edge mob. Expected: the edge mob investigates (previously silent).

- [ ] **Step 3: Arrow fix near-miss with leader goal**

Same group as Step 2. Shoot past the leader (non-edge). Expected: leader investigates.

- [ ] **Step 4: Invisibility render override (default)**

Fresh install. Drink invisibility potion wearing full iron armor holding a sword. Expected: armor, item, camo all hidden (legacy behavior preserved).

- [ ] **Step 5: Invisibility render override (flipped)**

Set `hideWornGearWhileInvisible=false` in `config/soundattract/rendering.toml` on the server (or singleplayer world's serverconfig dir). Reconnect (or `/reload`). Drink invisibility again.
Expected: armor, held item, and camo overlay render through invisibility.

- [ ] **Step 6: Server-authority check**

Dedicated server: set `hideWornGearWhileInvisible=true` on the server; set a client's local copy to `false`. Connect the client. Expected: client reads the server's value (`true`), gear is hidden. Invert on server, client sees gear.

- [ ] **Step 7: Early render safety**

Restart client; observe main menu and panorama (no active world). Expected: no crashes, no NPEs from `shouldHideWornGearWhileInvisible()` when no world/server is loaded. The default `true` fallback must cover this path.

- [ ] **Step 8: Player-action server authority**

Dedicated server with default config. Client sets local `enablePlayerActionSounds=false` in their COMMON file (old location). Connect client. Expected: the client's walk still generates virtual sound messages on the server (because the client reads SERVER now). Server log confirms sound arrival.

- [ ] **Step 9: Migration sanity**

On a pre-6.3.3 world with customized `playerActionRanges` in COMMON, launch 6.3.3. Expected: server log contains `migrate playerActionRanges: [...] -> SERVER` at startup; `server-rules.toml` now holds the migrated values. Subsequent launches do NOT log a migration (idempotent).

- [ ] **Step 10: Integration toggle authority**

Client disables `enablePointBlankIntegration` locally (old COMMON). Server has it enabled. Expected: Point Blank gun sounds still trigger mob attraction (server-side logic reads SERVER value).

---

### 5.7 Risks & Notes

- **`FollowerEdgeRelayGoal.targetSoundPos` mutation timing.** The field is written under the main server thread; `ArrowInvestigationEvents` reads it on the server tick END phase (same thread). No synchronization needed, but do not call the new getter from an async worker.
- **SERVER config load order.** Forge fires `ModConfigEvent.Loading` for SERVER specs only after the server starts. Client render code must tolerate "not loaded yet" — the `shouldHideWornGearWhileInvisible()` helper does exactly this with the `isLoaded()` guard.
- **Client disconnects and returns.** After a client leaves a server, Forge unloads the server config; the helper returns the default `true` again until the next connection syncs a new value. Acceptable.
- **Non-humanoid mobs with armor (modded).** The mixin targets `HumanoidArmorLayer` only. Mobs that render armor through bespoke layers are not affected by this toggle; that is out of scope for 6.3.3.
- **GeckoLib optional.** `MixinGeoArmorRenderer` is only active when GeckoLib is loaded (guarded by `GeckoMixinPlugin`). No conditional change needed in the mixin itself.
- **Per-world SERVER configs in singleplayer.** SERVER configs live at `<world>/serverconfig/soundattract/server-rules.toml`. A singleplayer user who previously customized `playerActionRanges` in their global COMMON file will see the migration copy those values into each world's serverconfig on first entry. World-specific overrides work thereafter. Users creating brand-new worlds after 6.3.3 and who never touched COMMON get defaults.
- **Migration irreversibility.** Once migrated, SERVER holds the authoritative value. If a user later edits the old COMMON key, that edit is ignored. This is intentional — COMMON is deprecated. The deprecation comment in the TOML makes this discoverable.
- **`SoundMessage` range/weight trust.** NOT fixed by this migration. A malicious client can still send `range=0` in `SoundMessage` to suppress attraction. Track as a separate follow-up: drop range/weight from the wire format and resolve server-side from `SOUND_DEFAULT_ENTRIES_CACHE` keyed on `soundId`.
- **Config sync during `/reload`.** Forge re-sends SERVER configs on `/reload`. The `ModConfigEvent.Reloading` listener in `SoundAttractConfig` must handle the SERVER spec path to rebuild `PLAYER_ACTION_RANGES_CACHE`/`PLAYER_ACTION_WEIGHTS_CACHE`. Verify the existing listener covers SERVER spec identity, not only COMMON.
- **`serverReady()` on client during login handshake.** There is a brief window between login and SERVER config sync completion where `serverReady()` returns false. `AttractionClientEvents` short-circuits during that window — players walking in the first ~100 ms after login emit nothing. Acceptable.

---

**End of plan.**
