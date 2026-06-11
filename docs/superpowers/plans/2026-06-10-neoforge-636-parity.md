# NeoForge 6.3.6 Parity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Bring the NeoForge 1.21.1 codebase to full parity with the confirmed non-excluded Forge 6.3.6 gaps while keeping every third-party mod integration optional.

**Architecture:** Port pure shared logic directly, adapt loader-specific integrations behind `ModList` and mixin plugins, and avoid hard runtime dependencies unless the optional mod is loaded. Hot Bath uses a 1.21.1 block/fluid detection adapter, Quantified uses the v2 reflective API, CustomNPCs uses the NeoForge event bus, and Gecko uses a separate optional mixin config.

**Tech Stack:** Java 21, NeoForge 1.21.1, ModDevGradle, Sponge Mixin, optional local jars in `libs`, reflected integration for Quantified and CustomNPCs.

---

### Task 1: Baseline and Build Metadata

**Files:**
- Modify: `build.gradle`
- Modify: `src/main/templates/META-INF/neoforge.mods.toml`

- [ ] **Step 1: Verify the current compile baseline**

Run: `.\gradlew.bat compileJava`
Expected: either PASS, or existing compile failures unrelated to these parity changes are recorded before editing.

- [ ] **Step 2: Add optional compile-time jars**

Add `compileOnly files('libs/quantified api-omni-2.0.0.jar')`, `compileOnly files('libs/hotbath-1.21.1-4.0.1.jar')`, and `compileOnly files('libs/CustomNPCs-Unofficial-NeoForge-1.21.1.20241226.jar')` to `dependencies`.

- [ ] **Step 3: Add optional mixin configs**

Add `[[mixins]]` entries for `soundattract.customnpcs.mixins.json`, `soundattract.mixins.gecko.json`, and `soundattract.hotbath.mixins.json`.

- [ ] **Step 4: Declare optional dependencies**

Add optional dependency blocks for `customnpcs`, `geckolib`, `hotbath`, and `quantified`.

### Task 2: Attracted Entity Wildcards and LOS/Floor Creak Fixes

**Files:**
- Create: `src/main/java/com/example/soundattract/config/AttractedEntityIdResolver.java`
- Modify: `src/main/java/com/example/soundattract/event/SoundAttractionEvents.java`
- Modify: `src/main/java/com/example/soundattract/event/FovEvents.java`
- Modify: `src/main/java/com/example/soundattract/event/FloorCreekEvents.java`

- [ ] **Step 1: Add the resolver**

Create a resolver matching Forge behavior: `namespace:*` expands to all registered ids in that namespace, invalid namespaces are ignored, exact ids must be valid `namespace:path`, and blacklist entries remove exact ids.

- [ ] **Step 2: Use wildcard expansion in attraction cache**

In `getCachedAttractedEntityTypes`, expand wildcard entries through a new NeoForge `getEntityTypesForNamespace(String namespace)` helper backed by `BuiltInRegistries.ENTITY_TYPE`. Remove blacklisted entity types before caching or returning.

- [ ] **Step 3: Preserve blacklist cache behavior**

Keep `getCachedBlacklistedEntityTypes` exact-id based so existing blacklist semantics stay compatible with Forge 6.3.6.

- [ ] **Step 4: Fix LOS allow-list priority**

Move the `BlockTags.WALLS`/`IronBarsBlock` hard block check after the config/datapack non-blocking allow-list section.

- [ ] **Step 5: Reduce floor creak volume**

Change the floor creak `level.playSound` volume argument from `1.0f` to `0.3f`.

### Task 3: Quantified v2 Bridge

**Files:**
- Modify: `src/main/java/com/example/soundattract/quantified/bridge/QuantifiedOptionalBridge.java`
- Modify: `src/main/java/com/example/soundattract/quantified/QuantifiedIntegration.java`

- [ ] **Step 1: Replace old task API handles**

Replace `QuantifiedTask`/`register`/`submit` handle resolution with v2 `QuantifiedAPI.compute(String,String)`, `ComputeRequest`, `QuantifiedAPI.cache(String,String)`, and `CacheRequest`.

- [ ] **Step 2: Preserve scheduler-facing methods**

Keep existing public methods: `isAvailable`, `register`, `trySubmitTask`, parallel builder helpers, cache manager helpers, and cache get helpers. `register` should return `isAvailable()` for compatibility because v2 compute/cache APIs do not require the old registration call.

- [ ] **Step 3: Update logging**

Change the Quantified success message from API `1.1.0` to API `2.0`.

### Task 4: Hot Bath Optional Integration

**Files:**
- Create: `src/main/java/com/example/soundattract/integration/hotbath/HotBathConfigParser.java`
- Create: `src/main/java/com/example/soundattract/integration/hotbath/HotBathScentRules.java`
- Create: `src/main/java/com/example/soundattract/integration/hotbath/HotBathScentStateCache.java`
- Create: `src/main/java/com/example/soundattract/integration/hotbath/HotBathIntegration.java`
- Create: `src/main/java/com/example/soundattract/mixin/hotbath/HotBathMixinPlugin.java`
- Create: `src/main/resources/soundattract.hotbath.mixins.json`
- Modify: `src/main/java/com/example/soundattract/config/separate/IntegrationConfig.java`
- Modify: `src/main/java/com/example/soundattract/config/SoundAttractConfig.java`
- Modify: `src/main/java/com/example/soundattract/event/ScentEvents.java`
- Modify: `src/main/java/com/example/soundattract/camo/CamouflageCapability.java`
- Modify: `src/main/java/com/example/soundattract/SoundAttractMod.java`

- [ ] **Step 1: Add Hot Bath config values**

Add the Forge 6.3.6 Hot Bath config block with NeoForge `ModConfigSpec` types and expose the fields in `SoundAttractConfig.Common`.

- [ ] **Step 2: Add parser/rules/cache**

Port the parser, rules, and state cache from Forge unchanged except imports where needed.

- [ ] **Step 3: Add configurable camo wash**

Add `CamouflageCapability.applyCamoWash(LivingEntity,float,float,boolean)` using NeoForge attachment state and existing `CamoUtil.washStack`.

- [ ] **Step 4: Add 1.21.1 Hot Bath adapter**

Implement `HotBathIntegration` with no direct Hot Bath imports. Detect current bath fluid by block/fluid registry ids such as `hotbath:hot_water_block` and `hotbath:hot_water_fluid`, map them to configured fluid ids, poll players over ticks, apply scent/camo wash, and clear state on logout/server stop.

- [ ] **Step 5: Hook scent multiplier and registration**

Multiply player scent by `HotBathScentStateCache.scentMultiplier(player, currentTime)`, rebuild Hot Bath rules during config bake, and register the integration only when `hotbath` is loaded and config enables it.

- [ ] **Step 6: Add optional Hot Bath mixin metadata**

Create a Hot Bath mixin plugin and empty optional mixin config for now, because 1.21.1 Hot Bath no longer has the Forge splash projectile classes. Splash-bottle parity is represented by bucket/fluid bathing behavior until a 1.21.1 splash class exists.

### Task 5: CustomNPCs Target Bridge

**Files:**
- Create: `src/main/java/com/example/soundattract/integration/customnpcs/CustomNpcsStealthTargetBridge.java`
- Modify: `src/main/java/com/example/soundattract/SoundAttractMod.java`

- [ ] **Step 1: Port event bridge**

Use `net.neoforged.bus.api.Event`, `EventPriority`, and `IEventBus`, reflect `noppes.npcs.api.NpcAPI.Instance().events()`, and add a listener for `noppes.npcs.api.event.NpcEvent$TargetEvent`.

- [ ] **Step 2: Cancel hidden targets**

Extract `npc` and `entity` wrappers, call `getMCEntity`, and cancel the event when `StealthDetectionEvents.canMobDetectLivingEntity(npc, target)` returns false.

- [ ] **Step 3: Register after config bake**

Call `CustomNpcsStealthTargetBridge.registerIfPresent()` from common setup after config is ready.

### Task 6: Gecko Camo Rendering

**Files:**
- Create: `src/main/java/com/example/soundattract/gecko/CamoGeoLayer.java`
- Create: `src/main/java/com/example/soundattract/mixin/GeckoMixinPlugin.java`
- Create: `src/main/java/com/example/soundattract/mixin/gecko/MixinGeoEntityRenderer.java`
- Create: `src/main/java/com/example/soundattract/mixin/gecko/MixinGeoArmorRenderer.java`
- Create: `src/main/resources/soundattract.mixins.gecko.json`

- [ ] **Step 1: Add NeoForge Gecko layer**

Adapt Forge `CamoGeoLayer` to GeckoLib 4.8 package names: `software.bernie.geckolib.animatable.GeoAnimatable`, NeoForge `OnlyIn`, and NeoForge attachments via `living.getData(CamoAttachments.CAMOUFLAGE)`.

- [ ] **Step 2: Add entity renderer mixin**

Inject into `GeoEntityRenderer` constructors and add the camo layer.

- [ ] **Step 3: Add armor renderer mixin**

Adapt Forge armor overlay mixin to GeckoLib 4.8 signatures, `GeoItem`, 1.21.1 `ItemStack` component-compatible camo utility calls, and NeoForge imports.

- [ ] **Step 4: Gate mixins on GeckoLib**

Use a NeoForge loading-list mixin plugin that only applies Gecko mixins when `geckolib` is present.

### Task 7: Final Verification

**Files:**
- All touched files.

- [ ] **Step 1: Run focused source checks**

Run: `rg -n "QuantifiedTask|org.admany.quantified.api.model|net.minecraftforge" src/main/java/com/example/soundattract`
Expected: no stale Quantified v1 references and no accidental Forge imports in NeoForge source.

- [ ] **Step 2: Run compile**

Run: `.\gradlew.bat compileJava`
Expected: PASS.

- [ ] **Step 3: Review diff**

Run: `git diff -- src/main/java src/main/resources src/main/templates build.gradle docs/superpowers/plans/2026-06-10-neoforge-636-parity.md`
Expected: only parity-plan and parity implementation changes.
