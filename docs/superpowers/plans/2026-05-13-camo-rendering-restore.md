# Camo Rendering Restore Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make layered camouflage visually render again for players and supported living entities in the NeoForge 1.21.1 port.

**Architecture:** Treat the existing camo gameplay state as authoritative and restore the missing client render attachment path first. The server already applies and syncs `CamoLayer` data via `CamouflageCapability` and `CamoSyncMessage`; client rendering should attach `CamoRenderLayer` to player renderers and `GenericMobCamoLayer` variants to supported mob renderers during `EntityRenderersEvent.AddLayers`, then rely on `CamoTextureGenerator` and `CamoRenderTypes` for overlay texture generation.

**Tech Stack:** Java 21, Minecraft 1.21.1, NeoForge client rendering events, `EntityRenderersEvent.AddLayers`, vanilla `RenderLayer`, Gradle.

---

## Findings From Brainstorming

Current pipeline pieces already exist:

- `src/main/java/com/example/soundattract/camo/CamoApplyHandler.java`
  - Applies camo materials through shift-right-click.
  - Calls `CamouflageCapability.applyMaterial(...)` for skin/entity camo.
  - Writes armor camo layers to item NBT through `CamoUtil.addLayerToStack(...)`.

- `src/main/java/com/example/soundattract/camo/CamouflageCapability.java`
  - Stores skin/body camo layers in a weak entity map.
  - Syncs layers server-to-client with `CamoSyncMessage`.
  - Exposes `getLayers()`, `getBlendedColor()`, `getVisualCamoStrength()`, display seed/climate values, and degradation.

- `src/main/java/com/example/soundattract/network/CamoSyncMessage.java`
  - Sends entity id plus serialized `CamoLayer` list.
  - On client, resolves the entity and calls `CamouflageCapability.get(targetEntity).ifPresent(camo -> camo.setLayers(msg.layers))`.

- `src/main/java/com/example/soundattract/camo/CamoRenderLayer.java`
  - Player render overlay layer already exists.
  - Uses `CamoTextureGenerator.getOrCreateMaskedSmudge(...)` and `CamoRenderTypes.camoOverlay(...)`.

- `src/main/java/com/example/soundattract/camo/GenericMobCamoLayer.java`
  - Generic humanoid, illager, and villager/wandering trader camo render layers already exist.

Likely primary root cause:

- `src/main/java/com/example/soundattract/camo/CamoClientEvents.java`
  - `onAddLayers(EntityRenderersEvent.AddLayers event)` is empty.
  - The helper `tryAddLayer(...)` exists but is never called.
  - Therefore render layers are not attached, so existing camo render classes never run.

Secondary risks to verify after layer registration:

- Player renderer registration must handle both `default` and `slim` skins.
- Entity type iteration must not assume `EntityType.PLAYER` has a normal renderer from `event.getRenderer(...)`.
- Current texture mask capture/readback path may fail for dynamic player skins or unsupported texture paths.
- Armor camo NBT exists, but the current visible overlay appears skin/entity-focused; armor visual support may require a dedicated armor render path or using rendered entity texture masks carefully.

Docs note:

- The `mcmodding` resource mention could not be read because the provided server name was empty. Direct `mcmodding` search did not surface a NeoForge-specific AddLayers page, but the codebase already imports NeoForge's `EntityRenderersEvent.AddLayers`, which is the correct event family to attach entity render layers in NeoForge.

---

## Approach Options

### Recommended: Restore `AddLayers` registration first

Use existing render layers and attach them correctly during NeoForge client layer registration.

Pros:

- Smallest change.
- Directly addresses the obvious root cause.
- Reuses existing rendering, sync, texture, and LOD code.
- Easier to verify with compile and runtime tests.

Cons:

- May reveal secondary texture-generation or armor-overlay bugs after layers start running.

### Alternative: Replace texture overlay generation with simple tint render layer

Render a simple translucent tinted entity pass without generated masked textures.

Pros:

- Avoids GPU readback and mask capture complexity.
- Easier to reason about if `CamoTextureGenerator` is brittle.

Cons:

- Lower visual quality.
- Does not use the existing smudge/mask design.
- More behavior change than needed.

### Alternative: Full armor-and-skin renderer rewrite

Add dedicated player skin, humanoid mob, and armor render layers with separate texture paths.

Pros:

- Most complete long-term rendering architecture.
- Can make armor camo visually distinct from skin/body camo.

Cons:

- Larger change.
- Higher risk.
- Not justified until the missing layer registration and texture mask path are verified.

Decision: implement the recommended path first, then only extend armor rendering if runtime testing proves skin/entity overlays work but armor overlays do not.

---

## File Structure

### Modify Java files

- `src/main/java/com/example/soundattract/camo/CamoClientEvents.java`
  - Register `CamoRenderLayer` on player renderers.
  - Register `GenericMobCamoLayer` variants on supported mob renderers.
  - Keep registration defensive so unsupported renderer/model pairs are skipped without crashing.

- `src/main/java/com/example/soundattract/camo/CamoRenderLayer.java`
  - Only modify if runtime testing shows model part visibility is not restored safely or overlay masks fail.

- `src/main/java/com/example/soundattract/camo/GenericMobCamoLayer.java`
  - Only modify if generic mob registration exposes type/model cast issues.

- `src/main/java/com/example/soundattract/camo/CamoTextureGenerator.java`
  - Only modify if generated texture creation returns `null` during runtime after render layers are attached.

### No new files initially

This plan should start by wiring the existing render classes before adding new abstractions.

---

## Task 1: Confirm Client Event Registration Is the Missing Link

**Files:**

- Read: `src/main/java/com/example/soundattract/camo/CamoClientEvents.java`
- Read: `src/main/resources/META-INF/neoforge.mods.toml` or template if relevant
- Read: `src/main/resources/soundattract.mixins.json`

- [ ] **Step 1: Confirm `CamoClientEvents` is auto-subscribed**

Check that the class has:

```java
@EventBusSubscriber(modid = SoundAttractMod.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
```

Expected:

- It is subscribed on the MOD bus and client dist.
- No manual registration is needed in `SoundAttractMod`.

- [ ] **Step 2: Confirm `onAddLayers` is empty**

Expected current state:

```java
@SubscribeEvent
public static void onAddLayers(EntityRenderersEvent.AddLayers event) {
}
```

- [ ] **Step 3: Confirm render layer classes compile before change**

Run:

```powershell
.\gradlew compileJava
```

Expected:

```text
BUILD SUCCESSFUL
```

---

## Task 2: Register Player Camo Render Layers

**Files:**

- Modify: `src/main/java/com/example/soundattract/camo/CamoClientEvents.java:31-33`

- [ ] **Step 1: Add default and slim player renderer registration**

Inside `onAddLayers`, retrieve both skin renderers:

```java
PlayerRenderer defaultRenderer = event.getSkin("default");
if (defaultRenderer != null) {
    defaultRenderer.addLayer(new CamoRenderLayer(defaultRenderer));
}

PlayerRenderer slimRenderer = event.getSkin("slim");
if (slimRenderer != null) {
    slimRenderer.addLayer(new CamoRenderLayer(slimRenderer));
}
```

Expected:

- Both player model variants receive `CamoRenderLayer`.
- No `EntityType.PLAYER` path is used for player renderers.

- [ ] **Step 2: Compile-check player layer registration**

Run:

```powershell
.\gradlew compileJava
```

Expected:

```text
BUILD SUCCESSFUL
```

If method names differ for NeoForge 1.21.1, inspect the local NeoForge mappings/decompiled sources and adjust to the available AddLayers API.

---

## Task 3: Register Supported Mob Camo Render Layers

**Files:**

- Modify: `src/main/java/com/example/soundattract/camo/CamoClientEvents.java:31-58`

- [ ] **Step 1: Add a supported entity list**

In `onAddLayers`, call `tryAddLayer` for humanoid, illager, villager, and wandering trader entities likely supported by current model handling:

```java
tryAddLayer(event, EntityType.ZOMBIE);
tryAddLayer(event, EntityType.HUSK);
tryAddLayer(event, EntityType.DROWNED);
tryAddLayer(event, EntityType.SKELETON);
tryAddLayer(event, EntityType.STRAY);
tryAddLayer(event, EntityType.WITHER_SKELETON);
tryAddLayer(event, EntityType.PIGLIN);
tryAddLayer(event, EntityType.PIGLIN_BRUTE);
tryAddLayer(event, EntityType.ZOMBIFIED_PIGLIN);
tryAddLayer(event, EntityType.PILLAGER);
tryAddLayer(event, EntityType.VINDICATOR);
tryAddLayer(event, EntityType.EVOKER);
tryAddLayer(event, EntityType.ILLUSIONER);
tryAddLayer(event, EntityType.VILLAGER);
tryAddLayer(event, EntityType.WANDERING_TRADER);
```

Keep the helper's try/catch behavior so missing renderers or incompatible models are skipped.

- [ ] **Step 2: Remove unused imports after wiring**

`CamoClientEvents.java` currently has unused imports. After registering layers, clean only imports that are still unused by the final file.

- [ ] **Step 3: Compile-check mob layer registration**

Run:

```powershell
.\gradlew compileJava
```

Expected:

```text
BUILD SUCCESSFUL
```

---

## Task 4: Add Minimal Debug Verification for Render Execution If Needed

**Files:**

- Modify only if runtime test is ambiguous:
  - `src/main/java/com/example/soundattract/camo/CamoRenderLayer.java`
  - `src/main/java/com/example/soundattract/camo/GenericMobCamoLayer.java`

- [ ] **Step 1: Prefer no logging initially**

Do not add logs before the first runtime test. The layer registration fix should be visible if everything else works.

- [ ] **Step 2: If no visual overlay appears, add config-gated debug logging**

Only if runtime testing still shows no render, add debug logs guarded by existing debug config to report:

- render layer invoked
- entity id/type
- layer count
- blended color present/missing
- generated texture `ResourceLocation` present/missing

- [ ] **Step 3: Remove or keep only useful config-gated logs**

Do not leave noisy unconditional logs.

---

## Task 5: Validate Texture Generation Path

**Files:**

- Inspect/modify only if runtime test shows layer runs but texture is null:
  - `src/main/java/com/example/soundattract/camo/CamoTextureGenerator.java`
  - `src/main/java/com/example/soundattract/mixin/SimpleTextureMixin.java`
  - `src/main/java/com/example/soundattract/mixin/HttpTextureMixin.java`
  - `src/main/java/com/example/soundattract/mixin/SimpleTextureTextureImageMixin.java`

- [ ] **Step 1: Verify mixins are included in main mixin config**

Check `src/main/resources/soundattract.mixins.json` contains:

```json
"SimpleTextureMixin",
"HttpTextureMixin",
"SimpleTextureTextureImageMixin"
```

Expected:

- Texture capture mixins are present.

- [ ] **Step 2: Verify mask extraction behavior in runtime logs/debugger**

During runtime, confirm one of these succeeds for player/entity texture:

- `CAPTURED_MASKS.get(loc)` returns an image.
- resource manager can load `loc`.
- render-thread GPU readback returns valid dimensions.

- [ ] **Step 3: Add fallback only if needed**

If all masked paths fail, add a controlled fallback in render layers:

```java
ResourceLocation smudge = CamoTextureGenerator.getOrCreateMaskedSmudge(...);
if (smudge == null) {
    smudge = CamoTextureGenerator.getOrCreateSmudge(seed, color, strength, lod.resolution(), erosion, humidity, temp);
}
```

Expected:

- Camo still renders as an unmasked overlay instead of disappearing completely.
- This should be treated as fallback behavior, not the first change.

---

## Task 6: Runtime Smoke Test

**Files:**

- No code changes unless failures are found.

- [ ] **Step 1: Launch client**

Run:

```powershell
.\gradlew runClient
```

Expected:

- Game reaches main menu.
- No client class loading error involving `CamoClientEvents`, `CamoRenderLayer`, `GenericMobCamoLayer`, `CamoTextureGenerator`, or texture mixins.

- [ ] **Step 2: Apply camo to player skin**

In a test world:

1. Enable layered camouflage config if disabled.
2. Shift-right-click with a configured camo material item.
3. Remove armor from at least one body region so skin camo can apply.
4. Confirm a visible camo overlay appears on player body/limbs.

Expected:

- `CamouflageCapability` receives at least one layer.
- `CamoSyncMessage` syncs to client.
- `CamoRenderLayer.render(...)` runs.
- Player overlay is visible.

- [ ] **Step 3: Apply camo to supported mob**

Shift-right-click a supported living entity with a configured camo material.

Expected:

- Supported humanoid/illager/villager mob receives layer data.
- Generic mob camo layer renders a visible overlay.

- [ ] **Step 4: Test third-person and observer visibility**

Use either another client or camera perspective to confirm:

- local player sees own camo in third-person
- other players/tracking clients receive camo sync for the target entity

---

## Task 7: Final Verification

**Files:**

- All changed files.

- [ ] **Step 1: Run build verification**

Run:

```powershell
.\gradlew compileJava processResources
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 2: Search for empty render hook**

Run:

```powershell
Select-String -Path src/main/java/**/*.java -Pattern "onAddLayers\(EntityRenderersEvent.AddLayers event\) \{\s*\}" -Recurse
```

Expected:

- No empty `onAddLayers` implementation remains.

- [ ] **Step 3: Search for unconditional camo debug logs**

Run:

```powershell
Select-String -Path src/main/java/com/example/soundattract/camo/**/*.java -Pattern "LOGGER\.(info|warn|error)" -Recurse
```

Expected:

- Any newly added logs are config-gated or intentionally retained.

---

## Acceptance Criteria

- `CamoClientEvents.onAddLayers(...)` attaches `CamoRenderLayer` to both `default` and `slim` player renderers.
- Supported humanoid, illager, villager, and wandering trader renderers receive a compatible camo render layer.
- `compileJava processResources` succeeds.
- Runtime client launch has no render-layer registration crash.
- Applying camo produces visible overlay on player skin in third-person.
- Applying camo to supported mobs produces visible overlay.
- If masked texture generation fails, the implementation either fixes the mask path or adds a non-crashing fallback so camo is visible instead of silently absent.
