# Hybrid Core Stabilization Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the NeoForge 1.21.1 Sound Attract core port pass `compileJava` while preserving gameplay-critical systems and deferring cosmetic, optional integration, loot, and enchantment features.

**Architecture:** Keep core gameplay systems active and convert non-critical broken Forge-era APIs into explicit no-op shims or temporarily unregistered surfaces. Use compile-driven checkpoints after each batch so each task reduces the error set without introducing broad behavioral changes.

**Tech Stack:** Java, Minecraft 1.21.1, NeoForge 21.1.228, Gradle, NeoForge event bus, NeoForge networking, Minecraft data components.

---

## Scope Check

This plan covers one bounded stabilization milestone: core compile success. It intentionally does not fully port loot modifiers, the custom `conceal` enchantment, cosmetic camo rendering, or optional integrations. Those should be separate later plans after core compile and runtime launch are stable.

## File Structure

- `src/main/java/com/example/soundattract/SoundAttractMod.java`
  - Owns active registration. Temporarily unregister deferred systems here when needed.
- `src/main/java/com/example/soundattract/loot/*`
  - Keep classes compile-safe or unregistered. Do not fully port loot behavior in this milestone.
- `src/main/java/com/example/soundattract/enchantment/*`
  - Keep compile-safe no-op shells for deferred `conceal` support.
- `src/main/java/com/example/soundattract/camo/CamoClientEvents.java`
  - Disable cosmetic render layer registration if the 1.21 renderer API blocks compile.
- `src/main/java/com/example/soundattract/camo/CamoRenderLayer.java`
  - Compile-only API adjustments or defer by removing registration.
- `src/main/java/com/example/soundattract/camo/GenericMobCamoLayer.java`
  - Compile-only API adjustments or defer by removing registration.
- `src/main/java/com/example/soundattract/camo/CamoEvents.java`
  - Preserve server-side camo state/degradation and damage degradation.
- `src/main/java/com/example/soundattract/event/FovEvents.java`
  - Preserve FOV checks and backstab damage; defer smoke LOS integration if needed.
- `src/main/java/com/example/soundattract/event/StealthDetectionEvents.java`
  - Preserve stealth checks; make `conceal` enchantment path return false during defer.
- `src/main/java/com/example/soundattract/integration/csgrenades/CsGrenadesTracker.java`
  - Add no-op shim if references remain.
- `src/main/java/com/example/soundattract/config/MobProfile2.java`
  - Simplify or defer removed `EntityPredicate` JSON codec helpers.
- `src/main/java/com/example/soundattract/config/PlayerProfile2.java`
  - Simplify or defer removed `EntityPredicate` JSON codec helpers.

## Chunk 1: Compile Baseline and Deferred Registration

### Task 1: Capture current compile baseline

**Files:**
- Read: `build/compile-cmd.log`

- [ ] **Step 1: Run compile and capture log**

Run:

```powershell
cmd /c ".\gradlew.bat compileJava --console=plain > build\compile-cmd.log 2>&1"
```

Expected: `compileJava` fails with the known remaining API errors.

- [ ] **Step 2: Count current errors**

Run:

```powershell
Select-String -Path .\build\compile-cmd.log -Pattern 'error:' | Measure-Object
```

Expected: Around 25 errors before this plan is executed.

- [ ] **Step 3: Commit only if the baseline log is intentionally tracked**

Run:

```powershell
git status --short
```

Expected: Do not commit generated build logs unless the repository already tracks them.

### Task 2: Defer loot modifier registration

**Files:**
- Modify: `src/main/java/com/example/soundattract/SoundAttractMod.java`
- Modify: `src/main/java/com/example/soundattract/loot/ModLootModifiers.java`
- Modify: `src/main/java/com/example/soundattract/loot/AddItemModifier.java`
- Modify: `src/main/java/com/example/soundattract/loot/EnchantRandomlyModifier.java`
- Modify: `src/main/java/com/example/soundattract/loot/EnchantRandomArmorModifier.java`
- Modify: `src/main/java/com/example/soundattract/loot/EnchantRandomToolModifier.java`

- [ ] **Step 1: Remove active loot registration from the mod entrypoint**

In `SoundAttractMod.java`, remove or disable this call:

```java
ModLootModifiers.register(modEventBus);
```

Expected: The mod no longer tries to register broken loot serializers during this milestone.

- [ ] **Step 2: Replace `ModLootModifiers` with an inert registrar**

Use this minimal structure:

```java
package com.example.soundattract.loot;

import net.neoforged.bus.api.IEventBus;

public class ModLootModifiers {
    public static void register(IEventBus eventBus) {
    }
}
```

Expected: References to `ModLootModifiers.register` remain valid if restored later.

- [ ] **Step 3: Make loot modifier classes compile-safe if still compiled**

If compile still reaches loot classes, either update them to `MapCodec` correctly or convert them to inert classes with no `LootModifier` inheritance for this milestone. Prefer inert classes if they are not used by core runtime.

Expected: No errors mentioning `IGlobalLootModifier`, `MapCodec`, or `NeoForgeRegistries.ENCHANTMENTS`.

- [ ] **Step 4: Run compile checkpoint**

Run:

```powershell
cmd /c ".\gradlew.bat compileJava --console=plain > build\compile-cmd.log 2>&1"
Select-String -Path .\build\compile-cmd.log -Pattern 'loot|IGlobalLootModifier|MapCodec|NeoForgeRegistries.ENCHANTMENTS'
```

Expected: No loot-related compile errors remain.

- [ ] **Step 5: Commit**

Run:

```powershell
git add src/main/java/com/example/soundattract/SoundAttractMod.java src/main/java/com/example/soundattract/loot
 git commit -m "chore: defer loot modifiers during core stabilization"
```

Expected: One focused commit for deferred loot registration.

## Chunk 2: Enchantment and Stealth Compile Stabilization

### Task 3: Defer `conceal` enchantment behavior

**Files:**
- Modify: `src/main/java/com/example/soundattract/enchantment/ModEnchantments.java`
- Modify: `src/main/java/com/example/soundattract/enchantment/EnchantmentConceal.java`
- Modify: `src/main/java/com/example/soundattract/event/StealthDetectionEvents.java:211-223`
- Modify: `src/main/java/com/example/soundattract/mixin/ItemStackMixin.java`

- [ ] **Step 1: Keep no-op enchantment registration**

Ensure `ModEnchantments.java` is compile-safe:

```java
package com.example.soundattract.enchantment;

import net.neoforged.bus.api.IEventBus;

public class ModEnchantments {
    public static void register(IEventBus eventBus) {
    }
}
```

Expected: No references to removed 1.21 `EnchantmentCategory`, final `Enchantment` inheritance, or invalid enchantment registries.

- [ ] **Step 2: Keep `EnchantmentConceal` as an inert marker shell**

Use this compile-safe file:

```java
package com.example.soundattract.enchantment;

public final class EnchantmentConceal {
    private EnchantmentConceal() {
    }
}
```

Expected: The class name remains reserved for a later real 1.21 port.

- [ ] **Step 3: Make stealth conceal check return false**

In `StealthDetectionEvents.java`, replace `hasConcealmentEnchant` with:

```java
private static boolean hasConcealmentEnchant(ItemStack stack) {
    return false;
}
```

Expected: Stealth detection compiles and only the custom enchantment bonus is deferred.

- [ ] **Step 4: Keep `ItemStackMixin` inert**

Ensure `ItemStackMixin.java` does not reference `ModEnchantments.CONCEAL` or `EnchantmentHelper.getEnchantments`.

Expected: No mixin compile errors related to enchantments.

- [ ] **Step 5: Run compile checkpoint**

Run:

```powershell
cmd /c ".\gradlew.bat compileJava --console=plain > build\compile-cmd.log 2>&1"
Select-String -Path .\build\compile-cmd.log -Pattern 'Enchantment|CONCEAL|getEnchantments|EnchantmentCategory'
```

Expected: No custom enchantment compile errors remain.

- [ ] **Step 6: Commit**

Run:

```powershell
git add src/main/java/com/example/soundattract/enchantment src/main/java/com/example/soundattract/event/StealthDetectionEvents.java src/main/java/com/example/soundattract/mixin/ItemStackMixin.java
 git commit -m "chore: defer conceal enchantment for core compile"
```

Expected: One focused commit for deferred enchantment behavior.

## Chunk 3: Camo Server Logic Preserved, Client Rendering Deferred

### Task 4: Preserve server camo events and defer client render layers

**Files:**
- Modify: `src/main/java/com/example/soundattract/camo/CamoClientEvents.java`
- Modify: `src/main/java/com/example/soundattract/camo/CamoEvents.java`
- Modify: `src/main/java/com/example/soundattract/camo/CamoRenderLayer.java`
- Modify: `src/main/java/com/example/soundattract/camo/GenericMobCamoLayer.java`
- Modify: `src/main/java/com/example/soundattract/camo/CamoTextureGenerator.java`

- [ ] **Step 1: Disable cosmetic render layer registration**

In `CamoClientEvents.onAddLayers`, leave the method empty:

```java
@SubscribeEvent
public static void onAddLayers(EntityRenderersEvent.AddLayers event) {
}
```

Expected: Client render layer classes can remain present, but no renderer API calls are required during core stabilization.

- [ ] **Step 2: Fix living damage event type**

In `CamoEvents.java`, change the handler to `LivingDamageEvent.Pre` and use `getNewDamage()`:

```java
@SubscribeEvent
public static void onLivingDamage(LivingDamageEvent.Pre event) {
    if (!event.getEntity().level().isClientSide) {
        CamouflageCapability.get(event.getEntity()).ifPresent(camo -> {
            camo.onDamage(event.getEntity(), event.getNewDamage());
        });
    }
}
```

Expected: Camo degradation on damage remains active.

- [ ] **Step 3: Fix start tracking target cast**

In `CamoEvents.java`, only access camo for living targets:

```java
if (event.getTarget() instanceof LivingEntity target) {
    CamouflageCapability.get(target).ifPresent(camo -> camo.sync(target));
}
```

Expected: No `Entity` to `LivingEntity` compile error.

- [ ] **Step 4: Remove broken `net.NeoForge` package reference**

In `CamoTextureGenerator.java`, replace the bad `net.NeoForge.forgespi.language.IModInfo` reference with the correct NeoForge package or remove that optional loop if it only improves diagnostics.

Expected: No `net.NeoForge` package errors.

- [ ] **Step 5: Run compile checkpoint**

Run:

```powershell
cmd /c ".\gradlew.bat compileJava --console=plain > build\compile-cmd.log 2>&1"
Select-String -Path .\build\compile-cmd.log -Pattern 'CamoClientEvents|CamoEvents|CamoRenderLayer|GenericMobCamoLayer|CamoTextureGenerator'
```

Expected: No camo compile errors remain.

- [ ] **Step 6: Commit**

Run:

```powershell
git add src/main/java/com/example/soundattract/camo
 git commit -m "chore: defer camo rendering while preserving server camo logic"
```

Expected: One focused commit for camo stabilization.

## Chunk 4: FOV, Integration Shims, and Profile Codec Deferral

### Task 5: Fix FOV visibility and CS Grenades references

**Files:**
- Modify: `src/main/java/com/example/soundattract/event/FovEvents.java`
- Create: `src/main/java/com/example/soundattract/integration/csgrenades/CsGrenadesTracker.java`

- [ ] **Step 1: Fix visibility denial API**

If `LivingVisibilityEvent` no longer uses `Event.Result`, replace the denial call with the NeoForge 1.21.1 supported method. If no direct denial exists, preserve compile by multiplying visibility down to zero using the event API available in sources.

Expected: No `Event.Result` compile error.

- [ ] **Step 2: Add no-op CS Grenades tracker shim**

Create `CsGrenadesTracker.java`:

```java
package com.example.soundattract.integration.csgrenades;

import net.minecraft.world.phys.Vec3;

public final class CsGrenadesTracker {
    private CsGrenadesTracker() {
    }

    public static boolean smokeBlocksRay(Vec3 eye, Vec3 targetCenter, int maxAge, long currentTick) {
        return false;
    }
}
```

Expected: FOV LOS logic compiles and optional smoke blocking is inactive.

- [ ] **Step 3: Run compile checkpoint**

Run:

```powershell
cmd /c ".\gradlew.bat compileJava --console=plain > build\compile-cmd.log 2>&1"
Select-String -Path .\build\compile-cmd.log -Pattern 'FovEvents|CsGrenadesTracker|Event.Result'
```

Expected: No FOV or CS Grenades compile errors remain.

- [ ] **Step 4: Commit**

Run:

```powershell
git add src/main/java/com/example/soundattract/event/FovEvents.java src/main/java/com/example/soundattract/integration/csgrenades/CsGrenadesTracker.java
 git commit -m "chore: defer cs grenades smoke tracking for core compile"
```

Expected: One focused commit for FOV integration stabilization.

### Task 6: Defer removed entity predicate profile codecs

**Files:**
- Modify: `src/main/java/com/example/soundattract/config/MobProfile2.java`
- Modify: `src/main/java/com/example/soundattract/config/PlayerProfile2.java`

- [ ] **Step 1: Replace removed JSON helper usage**

If `EntityPredicate.fromJson` and `serializeToJson` are unused by active config loading, replace the codec with a simpler disabled placeholder that does not reference removed methods.

Expected: The classes compile without trying to serialize or deserialize `EntityPredicate` through removed helpers.

- [ ] **Step 2: Run compile checkpoint**

Run:

```powershell
cmd /c ".\gradlew.bat compileJava --console=plain > build\compile-cmd.log 2>&1"
Select-String -Path .\build\compile-cmd.log -Pattern 'MobProfile2|PlayerProfile2|EntityPredicate'
```

Expected: No profile codec compile errors remain.

- [ ] **Step 3: Commit**

Run:

```powershell
git add src/main/java/com/example/soundattract/config/MobProfile2.java src/main/java/com/example/soundattract/config/PlayerProfile2.java
 git commit -m "chore: defer entity predicate profile codecs"
```

Expected: One focused commit for profile codec stabilization.

## Chunk 5: Final Compile Gate

### Task 7: Final compile verification

**Files:**
- Read: `build/compile-cmd.log`

- [ ] **Step 1: Run final compile**

Run:

```powershell
.\gradlew.bat compileJava --console=plain
```

Expected: `BUILD SUCCESSFUL` or no Java compile errors. Warnings are acceptable.

- [ ] **Step 2: If errors remain, fix only compile blockers**

Run:

```powershell
cmd /c ".\gradlew.bat compileJava --console=plain > build\compile-cmd.log 2>&1"
Select-String -Path .\build\compile-cmd.log -Pattern 'error:' | Select-Object -First 80 | ForEach-Object { $_.Line }
```

Expected: The list should be empty. If not empty, address only the listed compile blockers and rerun this step.

- [ ] **Step 3: Check working tree**

Run:

```powershell
git status --short
```

Expected: Only intentional source/doc changes are present.

- [ ] **Step 4: Commit final stabilization if needed**

Run:

```powershell
git add src/main/java docs/superpowers
 git commit -m "chore: stabilize core neoforge compile"
```

Expected: Final compile stabilization commit exists if prior task commits were not made.

## Execution Handoff

Execute this plan with `@executing-plans` in the current session. Use compile checkpoints after each chunk and do not restore deferred systems until core compile passes.
