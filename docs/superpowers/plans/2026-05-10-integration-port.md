# Integration Port Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Port Sound Attract mod integrations from Forge 1.20.1 to NeoForge 1.21.1 as a 1-to-1 conversion, preserving original behavior while adapting to NeoForge API changes.

**Architecture:** Staged incremental port with compile gates. Each integration is ported independently, verified to compile, then committed before moving to the next stage. Decompilation is done on-demand when API mismatches cannot be resolved through error inspection.

**Tech Stack:** NeoForge 1.21.1, Java 21, Gradle 8.x, integration jars from libs/ folder (tacz, pointblank, immersive_melodies, plasmovoice, voicechat, enhancedai, SmartBrainLib, spore)

---

## File Structure

**Files to modify:**
- `src/main/java/com/example/soundattract/SoundAttractMod.java` - Integration registration
- `src/main/java/com/example/soundattract/integration/immersive_melodies/ImmersiveMelodiesEvents.java` - Port from Forge
- `src/main/java/com/example/soundattract/integration/tacz/TaczIntegration.java` - Port from Forge
- `src/main/java/com/example/soundattract/integration/pointblank/PointBlankIntegration.java` - Port from Forge
- `src/main/java/com/example/soundattract/integration/voicechat/` - New package for voice integrations
- `src/main/java/com/example/soundattract/integration/spore/` - Update existing
- `src/main/java/com/example/soundattract/integration/smartbrainlib/SmartBrainLibCompat.java` - Update existing
- `src/main/java/com/example/soundattract/integration/enhancedai/EnhancedAICompat.java` - Update existing

**Temporary decompilation folders:**
- `decompiled/immersive_melodies/` - For API reference
- `decompiled/tacz/` - For API reference
- `decompiled/pointblank/` - For API reference
- `decompiled/plasmovoice/` - For API reference
- `decompiled/voicechat/` - For API reference
- `decompiled/spore/` - For API reference
- `decompiled/smartbrainlib/` - For API reference
- `decompiled/enhancedai/` - For API reference

---

## Chunk 1: Stage 1 - Immersive Melodies + Config/Tag-Based

### Task 1: Decompile immersive_melodies jar (on-demand)

**Files:**
- Create: `decompiled/immersive_melodies/` (folder)

- [ ] **Step 1: Decompile immersive_melodies-neoforge jar**

Run: Use CFR or Fernflower decompiler
```
java -jar cfr.jar libs/immersive_melodies-neoforge-0.6.2+1.21.1.jar --outputdir decompiled/immersive_melodies/
```
Expected: Decompiled source files in `decompiled/immersive_melodies/`

- [ ] **Step 2: Verify decompiled API surface**

Inspect: Check key classes for event patterns and API signatures
Expected: Found relevant event classes and method signatures

### Task 2: Port ImmersiveMelodiesEvents to NeoForge

**Files:**
- Modify: `src/main/java/com/example/soundattract/integration/immersive_melodies/ImmersiveMelodiesEvents.java`
- Reference: `Forge-1.20.1/src/main/java/com/example/soundattract/integration/immersive_melodies/ImmersiveMelodiesEvents.java`

- [ ] **Step 1: Copy Forge 1.20.1 ImmersiveMelodiesEvents as starting point**

Read: `Forge-1.20.1/src/main/java/com/example/soundattract/integration/immersive_melodies/ImmersiveMelodiesEvents.java`
Expected: Have original Forge implementation

- [ ] **Step 2: Update imports to NeoForge 1.21.1 packages**

Replace:
- `net.minecraftforge.event.TickEvent` → `net.neoforged.neoforge.event.tick.ServerTickEvent`
- `net.minecraftforge.eventbus.api.SubscribeEvent` → `net.neoforged.bus.api.SubscribeEvent`
- `net.minecraftforge.registries.ForgeRegistries` → `net.minecraft.core.registries.BuiltInRegistries`
- Any other Forge-specific imports

Expected: All imports resolve to NeoForge packages

- [ ] **Step 3: Update event registration pattern**

Replace: `@SubscribeEvent public static void onServerTick(TickEvent.ServerTickEvent event)`
With: `@SubscribeEvent public static void onServerTick(ServerTickEvent.Post event)`

Expected: Event registration uses NeoForge pattern

- [ ] **Step 4: Update NBT tag access for 1.21 DataComponents**

Replace: `CompoundTag tag = stack.getTag();` with DataComponents API if needed
Expected: NBT access uses 1.21 DataComponents

- [ ] **Step 5: Update config access to use NeoForge config system**

Verify: Config calls use the NeoForge config system already in place
Expected: Config access works with NeoForge config

- [ ] **Step 6: Verify compile**

Run: `cmd /c ".\gradlew.bat compileJava --console=plain > build\compile-cmd.log 2>&1"`
Expected: 0 errors

- [ ] **Step 7: If errors exist, fix API mismatches using decompiled reference**

Review: Check error messages against decompiled API
Fix: Update method signatures and patterns
Expected: All errors resolved

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/example/soundattract/integration/immersive_melodies/ImmersiveMelodiesEvents.java
git commit -m "[Stage 1] Port Immersive Melodies integration to NeoForge 1.21.1"
```

### Task 3: Register Immersive Melodies integration in SoundAttractMod

**Files:**
- Modify: `src/main/java/com/example/soundattract/SoundAttractMod.java`

- [ ] **Step 1: Add Immersive Melodies event registration to NeoForge event bus**

Add in constructor:
```java
if (ModList.get().isLoaded("immersive_melodies")) {
    NeoForge.EVENT_BUS.register(new ImmersiveMelodiesEvents());
}
```
Expected: Integration registered when mod is loaded

- [ ] **Step 2: Verify compile**

Run: `cmd /c ".\gradlew.bat compileJava --console=plain > build\compile-cmd.log 2>&1"`
Expected: 0 errors

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/example/soundattract/SoundAttractMod.java
git commit -m "[Stage 1] Register Immersive Melodies integration in SoundAttractMod"
```

### Task 4: Clean up decompiled Immersive Melodies folder (optional)

**Files:**
- Delete: `decompiled/immersive_melodies/`

- [ ] **Step 1: Remove decompiled folder**

Run: `Remove-Item -Recurse -Force decompiled/immersive_melodies/`
Expected: Folder deleted

---

## Chunk 2: Stage 2 - Gun APIs (TACZ, PointBlank)

### Task 5: Decompile tacz jar

**Files:**
- Create: `decompiled/tacz/` (folder)

- [ ] **Step 1: Decompile tacz-neoforge jar**

Run: `java -jar cfr.jar libs/tacz-neoforge-1.21.1-1.1.8-r2.jar --outputdir decompiled/tacz/`
Expected: Decompiled source files in `decompiled/tacz/`

- [ ] **Step 2: Verify event API surface**

Inspect: Check `GunShootEvent` and `GunReloadEvent` classes
Expected: Found event registration patterns and method signatures

### Task 6: Port TaczIntegration to NeoForge

**Files:**
- Modify: `src/main/java/com/example/soundattract/integration/tacz/TaczIntegration.java`
- Reference: `Forge-1.20.1/src/main/java/com/example/soundattract/integration/tacz/TaczIntegration.java`

- [ ] **Step 1: Copy Forge 1.20.1 TaczIntegration as starting point**

Read: `Forge-1.20.1/src/main/java/com/example/soundattract/integration/tacz/TaczIntegration.java`
Expected: Have original Forge implementation

- [ ] **Step 2: Update imports to NeoForge 1.21.1 packages**

Replace:
- `net.minecraftforge.eventbus.api.SubscribeEvent` → `net.neoforged.bus.api.SubscribeEvent`
- `net.minecraftforge.fml.LogicalSide` → `net.neoforged.fml.LogicalSide` (if still needed)
- Any other Forge-specific imports

Expected: All imports resolve to NeoForge packages

- [ ] **Step 3: Update event registration for GunShootEvent and GunReloadEvent**

Verify: Event registration uses NeoForge patterns based on decompiled API
Expected: Events register correctly

- [ ] **Step 4: Update IGun API calls if changed**

Compare: Check decompiled IGun interface for method signature changes
Update: Update method calls to match 1.21.1 API
Expected: IGun API calls compile

- [ ] **Step 5: Update attachment API if changed**

Compare: Check decompiled attachment API
Update: Update attachment ID retrieval and handling
Expected: Attachment API calls compile

- [ ] **Step 6: Verify compile**

Run: `cmd /c ".\gradlew.bat compileJava --console=plain > build\compile-cmd.log 2>&1"`
Expected: 0 errors

- [ ] **Step 7: If errors exist, fix using decompiled reference**

Review: Check errors against decompiled tacz API
Fix: Update method signatures
Expected: All errors resolved

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/example/soundattract/integration/tacz/TaczIntegration.java
git commit -m "[Stage 2] Port TACZ integration to NeoForge 1.21.1"
```

### Task 7: Register TACZ integration in SoundAttractMod

**Files:**
- Modify: `src/main/java/com/example/soundattract/SoundAttractMod.java`

- [ ] **Step 1: Add TACZ event registration to NeoForge event bus**

Add in constructor:
```java
if (ModList.get().isLoaded("tacz")) {
    NeoForge.EVENT_BUS.register(new TaczIntegration());
}
```
Expected: Integration registered when mod is loaded

- [ ] **Step 2: Verify compile**

Run: `cmd /c ".\gradlew.bat compileJava --console=plain > build\compile-cmd.log 2>&1"`
Expected: 0 errors

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/example/soundattract/SoundAttractMod.java
git commit -m "[Stage 2] Register TACZ integration in SoundAttractMod"
```

### Task 8: Decompile pointblank jar

**Files:**
- Create: `decompiled/pointblank/` (folder)

- [ ] **Step 1: Decompile pointblank-neoforge jar**

Run: `java -jar cfr.jar libs/pointblank-neoforge-1.21.1-2.1.0.jar --outputdir decompiled/pointblank/`
Expected: Decompiled source files in `decompiled/pointblank/`

- [ ] **Step 2: Verify attachment API surface**

Inspect: Check attachment and event classes
Expected: Found event registration patterns and method signatures

### Task 9: Port PointBlankIntegration to NeoForge

**Files:**
- Modify: `src/main/java/com/example/soundattract/integration/pointblank/PointBlankIntegration.java`
- Reference: `Forge-1.20.1/src/main/java/com/example/soundattract/integration/pointblank/PointBlankIntegration.java`

- [ ] **Step 1: Copy Forge 1.20.1 PointBlankIntegration as starting point**

Read: `Forge-1.20.1/src/main/java/com/example/soundattract/integration/pointblank/PointBlankIntegration.java`
Expected: Have original Forge implementation

- [ ] **Step 2: Update imports to NeoForge 1.21.1 packages**

Replace Forge-specific imports with NeoForge equivalents
Expected: All imports resolve to NeoForge packages

- [ ] **Step 3: Update attachment API based on decompiled reference**

Compare: Check decompiled attachment API in pointblank
Update: Update attachment retrieval and handling methods
Expected: Attachment API calls compile

- [ ] **Step 4: Update event registration**

Verify: Event registration uses NeoForge patterns
Expected: Events register correctly

- [ ] **Step 5: Verify compile**

Run: `cmd /c ".\gradlew.bat compileJava --console=plain > build\compile-cmd.log 2>&1"`
Expected: 0 errors

- [ ] **Step 6: If errors exist, fix using decompiled reference**

Review: Check errors against decompiled pointblank API
Fix: Update method signatures
Expected: All errors resolved

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/example/soundattract/integration/pointblank/PointBlankIntegration.java
git commit -m "[Stage 2] Port PointBlank integration to NeoForge 1.21.1"
```

### Task 10: Register PointBlank integration in SoundAttractMod

**Files:**
- Modify: `src/main/java/com/example/soundattract/SoundAttractMod.java`

- [ ] **Step 1: Add PointBlank event registration to NeoForge event bus**

Add in constructor:
```java
if (ModList.get().isLoaded("pointblank")) {
    NeoForge.EVENT_BUS.register(new PointBlankIntegration());
}
```
Expected: Integration registered when mod is loaded

- [ ] **Step 2: Verify compile**

Run: `cmd /c ".\gradlew.bat compileJava --console=plain > build\compile-cmd.log 2>&1"`
Expected: 0 errors

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/example/soundattract/SoundAttractMod.java
git commit -m "[Stage 2] Register PointBlank integration in SoundAttractMod"
```

### Task 11: Clean up decompiled gun API folders (optional)

**Files:**
- Delete: `decompiled/tacz/`, `decompiled/pointblank/`

- [ ] **Step 1: Remove decompiled folders**

Run: `Remove-Item -Recurse -Force decompiled/tacz/, decompiled/pointblank/`
Expected: Folders deleted

---

## Chunk 3: Stage 3 - Voice Integrations

### Task 12: Decompile voicechat jar

**Files:**
- Create: `decompiled/voicechat/` (folder)

- [ ] **Step 1: Decompile voicechat-neoforge jar**

Run: `java -jar cfr.jar libs/voicechat-neoforge-1.21.1-2.6.17.jar --outputdir decompiled/voicechat/`
Expected: Decompiled source files in `decompiled/voicechat/`

- [ ] **Step 2: Verify voice API surface**

Inspect: Check voice event and API classes
Expected: Found event registration patterns and method signatures

### Task 13: Port Simple Voice Chat integration

**Files:**
- Create: `src/main/java/com/example/soundattract/integration/voicechat/SimpleVoiceChatIntegration.java`
- Reference: `Forge-1.20.1/src/main/java/com/example/soundattract/integration/voicechat/` (if exists)

- [ ] **Step 1: Check if Simple Voice Chat integration exists in Forge 1.20.1**

Search: `Forge-1.20.1/src/main/java/com/example/soundattract/integration/` for voicechat
Expected: Found reference implementation or need to create from scratch

- [ ] **Step 2: Create or port Simple Voice Chat integration class**

Create/Port: Implement voice event handling based on decompiled API
Expected: Integration class created with NeoForge patterns

- [ ] **Step 3: Verify compile**

Run: `cmd /c ".\gradlew.bat compileJava --console=plain > build\compile-cmd.log 2>&1"`
Expected: 0 errors

- [ ] **Step 4: If errors exist, fix using decompiled reference**

Review: Check errors against decompiled voicechat API
Fix: Update method signatures
Expected: All errors resolved

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/example/soundattract/integration/voicechat/SimpleVoiceChatIntegration.java
git commit -m "[Stage 3] Port Simple Voice Chat integration to NeoForge 1.21.1"
```

### Task 14: Register Simple Voice Chat integration in SoundAttractMod

**Files:**
- Modify: `src/main/java/com/example/soundattract/SoundAttractMod.java`

- [ ] **Step 1: Add Simple Voice Chat event registration**

Add in constructor:
```java
if (ModList.get().isLoaded("voicechat")) {
    NeoForge.EVENT_BUS.register(new SimpleVoiceChatIntegration());
}
```
Expected: Integration registered when mod is loaded

- [ ] **Step 2: Verify compile**

Run: `cmd /c ".\gradlew.bat compileJava --console=plain > build\compile-cmd.log 2>&1"`
Expected: 0 errors

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/example/soundattract/SoundAttractMod.java
git commit -m "[Stage 3] Register Simple Voice Chat integration in SoundAttractMod"
```

### Task 15: Decompile plasmovoice jar

**Files:**
- Create: `decompiled/plasmovoice/` (folder)

- [ ] **Step 1: Decompile plasmovoice-neoforge jar**

Run: `java -jar cfr.jar libs/plasmovoice-neoforge-1.21.1-2.1.9.jar --outputdir decompiled/plasmovoice/`
Expected: Decompiled source files in `decompiled/plasmovoice/`

- [ ] **Step 2: Verify Plasmo Voice API surface**

Inspect: Check Plasmo Voice event and API classes
Expected: Found event registration patterns and method signatures

### Task 16: Port Plasmo Voice integration

**Files:**
- Create: `src/main/java/com/example/soundattract/integration/voicechat/PlasmoVoiceIntegration.java`
- Reference: `Forge-1.20.1/src/main/java/com/example/soundattract/integration/voicechat/` (if exists)

- [ ] **Step 1: Check if Plasmo Voice integration exists in Forge 1.20.1**

Search: `Forge-1.20.1/src/main/java/com/example/soundattract/integration/` for plasmovoice
Expected: Found reference implementation or need to create from scratch

- [ ] **Step 2: Create or port Plasmo Voice integration class**

Create/Port: Implement Plasmo Voice event handling based on decompiled API
Expected: Integration class created with NeoForge patterns

- [ ] **Step 3: Verify compile**

Run: `cmd /c ".\gradlew.bat compileJava --console=plain > build\compile-cmd.log 2>&1"`
Expected: 0 errors

- [ ] **Step 4: If errors exist, fix using decompiled reference**

Review: Check errors against decompiled plasmovoice API
Fix: Update method signatures
Expected: All errors resolved

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/example/soundattract/integration/voicechat/PlasmoVoiceIntegration.java
git commit -m "[Stage 3] Port Plasmo Voice integration to NeoForge 1.21.1"
```

### Task 17: Register Plasmo Voice integration in SoundAttractMod

**Files:**
- Modify: `src/main/java/com/example/soundattract/SoundAttractMod.java`

- [ ] **Step 1: Add Plasmo Voice event registration**

Add in constructor:
```java
if (ModList.get().isLoaded("plasmovoice")) {
    NeoForge.EVENT_BUS.register(new PlasmoVoiceIntegration());
}
```
Expected: Integration registered when mod is loaded

- [ ] **Step 2: Verify compile**

Run: `cmd /c ".\gradlew.bat compileJava --console=plain > build\compile-cmd.log 2>&1"`
Expected: 0 errors

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/example/soundattract/SoundAttractMod.java
git commit -m "[Stage 3] Register Plasmo Voice integration in SoundAttractMod"
```

### Task 18: Clean up decompiled voice folders (optional)

**Files:**
- Delete: `decompiled/voicechat/`, `decompiled/plasmovoice/`

- [ ] **Step 1: Remove decompiled folders**

Run: `Remove-Item -Recurse -Force decompiled/voicechat/, decompiled/plasmovoice/`
Expected: Folders deleted

---

## Chunk 4: Stage 4 - Spore/AI Integrations

### Task 19: Decompile spore jar

**Files:**
- Create: `decompiled/spore/` (folder)

- [ ] **Step 1: Decompile spore jar**

Run: `java -jar cfr.jar libs/spore_2.2.0f_1.21.1_neo.jar --outputdir decompiled/spore/`
Expected: Decompiled source files in `decompiled/spore/`

- [ ] **Step 2: Verify Spore API surface**

Inspect: Check Spore AI goal injection and event classes
Expected: Found AI goal patterns and event signatures

### Task 20: Update SporeIntegration to NeoForge

**Files:**
- Modify: `src/main/java/com/example/soundattract/integration/spore/SporeIntegration.java`
- Modify: `src/main/java/com/example/soundattract/integration/spore/BiomassSoundSystem.java`
- Modify: `src/main/java/com/example/soundattract/integration/spore/SporeGoalInjectorProxy.java`

- [ ] **Step 1: Update SporeIntegration imports and event registration**

Replace: Forge-specific imports with NeoForge equivalents
Update: Event registration to NeoForge patterns
Expected: Imports resolve and events register correctly

- [ ] **Step 2: Update BiomassSoundSystem for NeoForge**

Update: Any Forge-specific event handling
Expected: Class compiles

- [ ] **Step 3: Update SporeGoalInjectorProxy based on decompiled API**

Compare: Check decompiled Spore AI goal API
Update: Update goal injection methods
Expected: Goal injection compiles

- [ ] **Step 4: Verify compile**

Run: `cmd /c ".\gradlew.bat compileJava --console=plain > build\compile-cmd.log 2>&1"`
Expected: 0 errors

- [ ] **Step 5: If errors exist, fix using decompiled reference**

Review: Check errors against decompiled spore API
Fix: Update method signatures
Expected: All errors resolved

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/example/soundattract/integration/spore/
git commit -m "[Stage 4] Update Spore integration to NeoForge 1.21.1"
```

### Task 21: Register Spore integration in SoundAttractMod

**Files:**
- Modify: `src/main/java/com/example/soundattract/SoundAttractMod.java`

- [ ] **Step 1: Add Spore event registration**

Add in constructor:
```java
if (ModList.get().isLoaded("spore")) {
    NeoForge.EVENT_BUS.register(new SporeIntegration());
}
```
Expected: Integration registered when mod is loaded

- [ ] **Step 2: Verify compile**

Run: `cmd /c ".\gradlew.bat compileJava --console=plain > build\compile-cmd.log 2>&1"`
Expected: 0 errors

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/example/soundattract/SoundAttractMod.java
git commit -m "[Stage 4] Register Spore integration in SoundAttractMod"
```

### Task 22: Decompile SmartBrainLib jar

**Files:**
- Create: `decompiled/smartbrainlib/` (folder)

- [ ] **Step 1: Decompile SmartBrainLib-neoforge jar**

Run: `java -jar cfr.jar libs/SmartBrainLib-neoforge-1.21.1-1.16.11.jar --outputdir decompiled/smartbrainlib/`
Expected: Decompiled source files in `decompiled/smartbrainlib/`

- [ ] **Step 2: Verify SmartBrainLib API surface**

Inspect: Check SmartBrainLib AI behavior hooks
Expected: Found AI behavior patterns

### Task 23: Update SmartBrainLibCompat to NeoForge

**Files:**
- Modify: `src/main/java/com/example/soundattract/integration/smartbrainlib/SmartBrainLibCompat.java`

- [ ] **Step 1: Update imports and AI behavior hooks**

Replace: Forge-specific imports with NeoForge equivalents
Update: AI behavior registration based on decompiled API
Expected: Imports resolve and behaviors register correctly

- [ ] **Step 2: Verify compile**

Run: `cmd /c ".\gradlew.bat compileJava --console=plain > build\compile-cmd.log 2>&1"`
Expected: 0 errors

- [ ] **Step 3: If errors exist, fix using decompiled reference**

Review: Check errors against decompiled SmartBrainLib API
Fix: Update method signatures
Expected: All errors resolved

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/example/soundattract/integration/smartbrainlib/SmartBrainLibCompat.java
git commit -m "[Stage 4] Update SmartBrainLib integration to NeoForge 1.21.1"
```

### Task 24: Register SmartBrainLib integration in SoundAttractMod

**Files:**
- Modify: `src/main/java/com/example/soundattract/SoundAttractMod.java`

- [ ] **Step 1: Add SmartBrainLib event registration**

Add in constructor:
```java
if (ModList.get().isLoaded("smartbrainlib")) {
    NeoForge.EVENT_BUS.register(new SmartBrainLibCompat());
}
```
Expected: Integration registered when mod is loaded

- [ ] **Step 2: Verify compile**

Run: `cmd /c ".\gradlew.bat compileJava --console=plain > build\compile-cmd.log 2>&1"`
Expected: 0 errors

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/example/soundattract/SoundAttractMod.java
git commit -m "[Stage 4] Register SmartBrainLib integration in SoundAttractMod"
```

### Task 25: Decompile enhancedai jar

**Files:**
- Create: `decompiled/enhancedai/` (folder)

- [ ] **Step 1: Decompile enhancedai jar**

Run: `java -jar cfr.jar libs/enhancedai-4.1.0.0.jar --outputdir decompiled/enhancedai/`
Expected: Decompiled source files in `decompiled/enhancedai/`

- [ ] **Step 2: Verify EnhancedAI API surface**

Inspect: Check EnhancedAI config/tag bridge API
Expected: Found config and tag access patterns

### Task 26: Update EnhancedAICompat to NeoForge

**Files:**
- Modify: `src/main/java/com/example/soundattract/integration/enhancedai/EnhancedAICompat.java`

- [ ] **Step 1: Update imports and proxy methods**

Replace: Forge-specific imports with NeoForge equivalents
Update: Proxy method calls based on decompiled API
Expected: Imports resolve and proxy methods compile

- [ ] **Step 2: Verify config/tag bridge still works**

Verify: Config access and tag lookup methods compile
Expected: Config/tag bridge compiles

- [ ] **Step 3: Verify compile**

Run: `cmd /c ".\gradlew.bat compileJava --console=plain > build\compile-cmd.log 2>&1"`
Expected: 0 errors

- [ ] **Step 4: If errors exist, fix using decompiled reference**

Review: Check errors against decompiled enhancedai API
Fix: Update method signatures
Expected: All errors resolved

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/example/soundattract/integration/enhancedai/EnhancedAICompat.java
git commit -m "[Stage 4] Update EnhancedAI integration to NeoForge 1.21.1"
```

### Task 27: Register EnhancedAI integration in SoundAttractMod

**Files:**
- Modify: `src/main/java/com/example/soundattract/SoundAttractMod.java`

- [ ] **Step 1: Add EnhancedAI event registration**

Add in constructor:
```java
if (ModList.get().isLoaded("enhancedai")) {
    NeoForge.EVENT_BUS.register(new EnhancedAICompat());
}
```
Expected: Integration registered when mod is loaded

- [ ] **Step 2: Verify compile**

Run: `cmd /c ".\gradlew.bat compileJava --console=plain > build\compile-cmd.log 2>&1"`
Expected: 0 errors

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/example/soundattract/SoundAttractMod.java
git commit -m "[Stage 4] Register EnhancedAI integration in SoundAttractMod"
```

### Task 28: Clean up decompiled AI folders (optional)

**Files:**
- Delete: `decompiled/spore/`, `decompiled/smartbrainlib/`, `decompiled/enhancedai/`

- [ ] **Step 1: Remove decompiled folders**

Run: `Remove-Item -Recurse -Force decompiled/spore/, decompiled/smartbrainlib/, decompiled/enhancedai/`
Expected: Folders deleted

---

## Chunk 5: Final Verification

### Task 29: Final compile verification

**Files:**
- None (verification only)

- [ ] **Step 1: Run full compile**

Run: `cmd /c ".\gradlew.bat compileJava --console=plain > build\compile-cmd.log 2>&1"`
Expected: 0 errors

- [ ] **Step 2: Count errors**

Run: `Select-String -Path .\build\compile-cmd.log -Pattern 'error:' | Measure-Object`
Expected: Count = 0

- [ ] **Step 3: If errors exist, review and fix**

Review: Check error messages
Fix: Resolve any remaining API mismatches
Expected: All errors resolved

- [ ] **Step 4: Run build**

Run: `cmd /c ".\gradlew.bat build --console=plain > build\build-cmd.log 2>&1"`
Expected: Build succeeds

- [ ] **Step 5: Commit final verification**

```bash
git commit -m "[Final] All integrations ported to NeoForge 1.21.1 - compile verified"
```

### Task 30: Optional runtime verification

**Files:**
- None (runtime testing only)

- [ ] **Step 1: Run in test environment**

Run: Launch game with target mods loaded
Expected: Game launches without errors

- [ ] **Step 2: Test each integration**

Test: Verify each integration works in-game
Expected: All integrations function correctly

- [ ] **Step 3: Document any runtime issues**

Document: Note any behavioral differences or issues
Expected: Issues documented if found
