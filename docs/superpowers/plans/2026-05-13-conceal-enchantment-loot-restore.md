# Conceal Enchantment and Loot Restore Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restore the SoundAttract conceal enchantment and loot modifier system in the NeoForge 1.21.1 port using the known-working `1.21.1-4.1.2-neo` model.

**Architecture:** Use Minecraft 1.21.1's data-driven enchantment system instead of reintroducing the old Forge/Fabric `Enchantment` subclass model. Register only NeoForge global loot modifier serializers in Java, define the conceal enchantment and loot modifier instances in data JSON, and update stealth detection to check the `ResourceKey<Enchantment>` holder.

**Tech Stack:** Java 21, Minecraft 1.21.1, NeoForge global loot modifiers, data-driven enchantments, Gradle.

---

## Context

The current NeoForge port has deferred stubs:

- `src/main/java/com/example/soundattract/enchantment/ModEnchantments.java`
  - `CONCEAL = null`
  - empty `register(IEventBus)`

- `src/main/java/com/example/soundattract/enchantment/EnchantmentConceal.java`
  - empty shell class

- `src/main/java/com/example/soundattract/loot/ModLootModifiers.java`
  - empty `register(IEventBus)`

- `src/main/java/com/example/soundattract/SoundAttractMod.java`
  - loot modifier registration currently deferred/commented

Reference findings:

- `Forge-1.20.1` and `Fabric-1.20.1` used a custom `EnchantmentConceal extends Enchantment` class.
- `1.21.1-4.1.2-neo` moved to a 1.21.1-compatible model:
  - `ModEnchantments.CONCEAL` is a `ResourceKey<Enchantment>`.
  - `conceal.json` defines the enchantment.
  - NeoForge loot modifier serializers are registered with `DeferredRegister<MapCodec<? extends IGlobalLootModifier>>`.
  - loot modifier instances live in `data/soundattract/loot_modifiers/*.json`.
  - `data/neoforge/loot_modifiers/global_loot_modifiers.json` lists enabled global modifiers.

User scope decision: **match the 1.21.1 Neo behavior**, not the broader old Forge/Fabric eligibility.

---

## File Structure

### Modify Java files

- `src/main/java/com/example/soundattract/enchantment/ModEnchantments.java`
  - Replace stub with `ResourceKey<Enchantment> CONCEAL`.
  - Remove no-op registration behavior or keep `register(IEventBus)` as a no-op only if needed by call sites.

- `src/main/java/com/example/soundattract/enchantment/EnchantmentConceal.java`
  - Delete if unused, or leave only if no call sites require it. Prefer delete if safe.

- `src/main/java/com/example/soundattract/loot/ModLootModifiers.java`
  - Replace stub with NeoForge serializer registry from `1.21.1-4.1.2-neo`.

- `src/main/java/com/example/soundattract/loot/AddItemModifier.java`
  - Compare against `1.21.1-4.1.2-neo/src/main/java/com/example/soundattract/loot/AddItemLootModifier.java`.
  - Port to `MapCodec` and `Holder<Enchantment>` lookup if still stale.

- `src/main/java/com/example/soundattract/loot/EnchantRandomArmorModifier.java`
  - Port/reference 1.21.1 Neo behavior.

- `src/main/java/com/example/soundattract/loot/EnchantRandomToolModifier.java`
  - Port/reference 1.21.1 Neo behavior.

- `src/main/java/com/example/soundattract/loot/EnchantRandomlyModifier.java`
  - Remove if no longer used after porting to the 1.21.1 Neo model.

- `src/main/java/com/example/soundattract/event/StealthDetectionEvents.java`
  - Ensure `hasConcealmentEnchant(ItemStack)` checks `DataComponents.ENCHANTMENTS` holders against `ModEnchantments.CONCEAL`.

- `src/main/java/com/example/soundattract/SoundAttractMod.java`
  - Register `ModLootModifiers.register(modEventBus)`.
  - Do not register enchantments via Java registry.

### Create/copy resource files

- `src/main/resources/data/soundattract/enchantment/conceal.json`
- `src/main/resources/data/neoforge/loot_modifiers/global_loot_modifiers.json`
- `src/main/resources/data/soundattract/loot_modifiers/add_conceal_book.json`
- `src/main/resources/data/soundattract/loot_modifiers/enchant_conceal_armor.json`
- `src/main/resources/data/soundattract/loot_modifiers/enchant_conceal_tool.json`

Use the files from:

- `1.21.1-4.1.2-neo/src/main/resources/...`

---

## Task 1: Restore Data-Driven Conceal Enchantment Key

**Files:**

- Modify: `src/main/java/com/example/soundattract/enchantment/ModEnchantments.java`
- Possibly delete: `src/main/java/com/example/soundattract/enchantment/EnchantmentConceal.java`

- [ ] **Step 1: Replace `ModEnchantments` stub**

Change it to the 1.21.1 Neo model:

```java
package com.example.soundattract.enchantment;

import com.example.soundattract.SoundAttractMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.enchantment.Enchantment;

public class ModEnchantments {
    public static final ResourceKey<Enchantment> CONCEAL =
            ResourceKey.create(Registries.ENCHANTMENT, ResourceLocation.fromNamespaceAndPath(SoundAttractMod.MOD_ID, "conceal"));
}
```

- [ ] **Step 2: Remove or leave `EnchantmentConceal` safely**

Search for call sites:

```powershell
Select-String -Path src/main/java/**/*.java -Pattern "EnchantmentConceal" -Recurse
```

Expected:

- If no call sites remain, delete `EnchantmentConceal.java`.
- If a stale import exists, remove that import/call site instead of preserving the old model.

- [ ] **Step 3: Compile-check Java key model**

Run:

```powershell
.\gradlew compileJava
```

Expected:

```text
BUILD SUCCESSFUL
```

---

## Task 2: Restore Conceal Enchantment Data JSON

**Files:**

- Create: `src/main/resources/data/soundattract/enchantment/conceal.json`

- [ ] **Step 1: Create resource directory**

Ensure this path exists:

```text
src/main/resources/data/soundattract/enchantment/
```

- [ ] **Step 2: Copy 1.21.1 Neo conceal JSON**

Create `conceal.json` with:

```json
{
  "description": {
    "translate": "enchantment.soundattract.conceal"
  },
  "supported_items": "#minecraft:enchantable/durability",
  "primary_items": "#minecraft:enchantable/durability",
  "weight": 1,
  "max_level": 1,
  "min_cost": {
    "base": 25,
    "per_level_above_first": 0
  },
  "max_cost": {
    "base": 75,
    "per_level_above_first": 0
  },
  "anvil_cost": 8,
  "slots": [
    "any"
  ],
  "effects": {}
}
```

- [ ] **Step 3: Ensure lang key exists**

Check `src/main/resources/assets/soundattract/lang/en_us.json` for:

```json
"enchantment.soundattract.conceal": "Conceal"
```

Add it only if missing.

- [ ] **Step 4: Process resources**

Run:

```powershell
.\gradlew processResources
```

Expected:

```text
BUILD SUCCESSFUL
```

---

## Task 3: Restore NeoForge Loot Modifier Serializers

**Files:**

- Modify: `src/main/java/com/example/soundattract/loot/ModLootModifiers.java`
- Modify: `src/main/java/com/example/soundattract/SoundAttractMod.java`

- [ ] **Step 1: Replace `ModLootModifiers` stub**

Use the 1.21.1 Neo serializer registry:

```java
package com.example.soundattract.loot;

import com.example.soundattract.SoundAttractMod;
import com.mojang.serialization.MapCodec;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public class ModLootModifiers {
    public static final DeferredRegister<MapCodec<? extends IGlobalLootModifier>> LOOT_MODIFIER_SERIALIZERS =
            DeferredRegister.create(NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, SoundAttractMod.MOD_ID);

    public static final DeferredHolder<MapCodec<? extends IGlobalLootModifier>, MapCodec<AddItemModifier>> ADD_ITEM =
            LOOT_MODIFIER_SERIALIZERS.register("add_item", () -> AddItemModifier.CODEC);

    public static final DeferredHolder<MapCodec<? extends IGlobalLootModifier>, MapCodec<EnchantRandomArmorModifier>> ENCHANT_RANDOM_ARMOR =
            LOOT_MODIFIER_SERIALIZERS.register("enchant_random_armor", () -> EnchantRandomArmorModifier.CODEC);

    public static final DeferredHolder<MapCodec<? extends IGlobalLootModifier>, MapCodec<EnchantRandomToolModifier>> ENCHANT_RANDOM_TOOL =
            LOOT_MODIFIER_SERIALIZERS.register("enchant_random_tool", () -> EnchantRandomToolModifier.CODEC);

    public static void register(IEventBus eventBus) {
        LOOT_MODIFIER_SERIALIZERS.register(eventBus);
    }
}
```

- [ ] **Step 2: Re-enable registration in `SoundAttractMod`**

Find the deferred line:

```java
// Deferred: ModLootModifiers.register(modEventBus);
```

Replace with:

```java
ModLootModifiers.register(modEventBus);
```

Keep existing import if already present.

- [ ] **Step 3: Compile-check serializer registration**

Run:

```powershell
.\gradlew compileJava
```

Expected:

```text
BUILD SUCCESSFUL
```

---

## Task 4: Port Loot Modifier Implementations to 1.21.1 Neo Model

**Files:**

- Modify: `src/main/java/com/example/soundattract/loot/AddItemModifier.java`
- Modify: `src/main/java/com/example/soundattract/loot/EnchantRandomArmorModifier.java`
- Modify: `src/main/java/com/example/soundattract/loot/EnchantRandomToolModifier.java`
- Delete if unused: `src/main/java/com/example/soundattract/loot/EnchantRandomlyModifier.java`

- [ ] **Step 1: Inspect current modifier files**

Read all four current files and compare with:

```text
1.21.1-4.1.2-neo/src/main/java/com/example/soundattract/loot/AddItemLootModifier.java
1.21.1-4.1.2-neo/src/main/java/com/example/soundattract/loot/EnchantRandomArmorModifier.java
1.21.1-4.1.2-neo/src/main/java/com/example/soundattract/loot/EnchantRandomToolModifier.java
```

- [ ] **Step 2: Port `AddItemModifier`**

Ensure it uses:

```java
public static final MapCodec<AddItemModifier> CODEC =
        RecordCodecBuilder.mapCodec(inst -> codecStart(inst).apply(inst, AddItemModifier::new));
```

Ensure `doApply`:

- gets the enchantment registry via `context.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT)`
- gets `ModEnchantments.CONCEAL`
- creates `EnchantmentInstance`
- adds an enchanted book to `generatedLoot`

Do not keep unconditional info logging from the reference unless already consistent with this port's logging style.

- [ ] **Step 3: Port `EnchantRandomArmorModifier`**

Ensure it:

- uses `MapCodec<EnchantRandomArmorModifier>`
- gets `Holder<Enchantment>` from `ModEnchantments.CONCEAL`
- applies only to `ArmorItem`
- checks `concealHolder.value().canEnchant(stack)`
- checks `EnchantmentHelper.getItemEnchantmentLevel(concealHolder, stack) == 0`
- enchants level `1`
- returns after enchanting one stack

- [ ] **Step 4: Port `EnchantRandomToolModifier`**

Match 1.21.1 Neo behavior exactly:

- eligible items are `DiggerItem` or `SwordItem`
- same holder/canEnchant/existing-level checks as armor
- enchant one stack and return

- [ ] **Step 5: Remove obsolete abstract base**

If `EnchantRandomlyModifier` has no call sites after porting, delete it.

Search:

```powershell
Select-String -Path src/main/java/**/*.java -Pattern "EnchantRandomlyModifier" -Recurse
```

Expected:

- No results after deletion.

- [ ] **Step 6: Compile-check modifiers**

Run:

```powershell
.\gradlew compileJava
```

Expected:

```text
BUILD SUCCESSFUL
```

---

## Task 5: Restore Loot Modifier Data JSON

**Files:**

- Create: `src/main/resources/data/neoforge/loot_modifiers/global_loot_modifiers.json`
- Create: `src/main/resources/data/soundattract/loot_modifiers/add_conceal_book.json`
- Create: `src/main/resources/data/soundattract/loot_modifiers/enchant_conceal_armor.json`
- Create: `src/main/resources/data/soundattract/loot_modifiers/enchant_conceal_tool.json`

- [ ] **Step 1: Copy global loot modifier list**

Create `global_loot_modifiers.json`:

```json
{
  "replace": false,
  "entries": [
    "soundattract:add_conceal_book",
    "soundattract:enchant_conceal_armor",
    "soundattract:enchant_conceal_tool"
  ]
}
```

- [ ] **Step 2: Copy `add_conceal_book.json` from 1.21.1 Neo**

Use the reference file exactly from:

```text
1.21.1-4.1.2-neo/src/main/resources/data/soundattract/loot_modifiers/add_conceal_book.json
```

- [ ] **Step 3: Copy `enchant_conceal_armor.json` from 1.21.1 Neo**

Use the reference file exactly.

- [ ] **Step 4: Copy `enchant_conceal_tool.json` from 1.21.1 Neo**

Use the reference file exactly.

- [ ] **Step 5: Process resources**

Run:

```powershell
.\gradlew processResources
```

Expected:

```text
BUILD SUCCESSFUL
```

---

## Task 6: Reconnect Stealth Detection to Conceal Enchantment

**Files:**

- Modify: `src/main/java/com/example/soundattract/event/StealthDetectionEvents.java`

- [ ] **Step 1: Inspect current `hasConcealmentEnchant`**

Find:

```java
private static boolean hasConcealmentEnchant(ItemStack stack)
```

- [ ] **Step 2: Match 1.21.1 holder-based implementation**

Ensure it uses this logic:

```java
private static boolean hasConcealmentEnchant(ItemStack stack) {
    if (stack == null || stack.isEmpty()) {
        return false;
    }

    ItemEnchantments enchantments = stack.get(DataComponents.ENCHANTMENTS);
    if (enchantments == null) {
        return false;
    }
    return enchantments.keySet().stream().anyMatch(holder -> holder.is(ModEnchantments.CONCEAL));
}
```

Imports required:

```java
import com.example.soundattract.enchantment.ModEnchantments;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.enchantment.ItemEnchantments;
```

- [ ] **Step 3: Compile-check stealth integration**

Run:

```powershell
.\gradlew compileJava
```

Expected:

```text
BUILD SUCCESSFUL
```

---

## Task 7: Final Verification

**Files:**

- All changed files.

- [ ] **Step 1: Run Java/resource build**

Run:

```powershell
.\gradlew compileJava processResources
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 2: Check for leftover stubs**

Run searches:

```powershell
Select-String -Path src/main/java/**/*.java -Pattern "CONCEAL = null|Deferred: ModLootModifiers|register\(IEventBus eventBus\) \{\s*\}|EnchantmentConceal" -Recurse
```

Expected:

- No `CONCEAL = null`.
- No deferred loot registration.
- No empty loot/enchantment registration stubs.
- No `EnchantmentConceal` references if the class was deleted.

- [ ] **Step 3: Data file presence check**

Confirm these files exist:

```text
src/main/resources/data/soundattract/enchantment/conceal.json
src/main/resources/data/neoforge/loot_modifiers/global_loot_modifiers.json
src/main/resources/data/soundattract/loot_modifiers/add_conceal_book.json
src/main/resources/data/soundattract/loot_modifiers/enchant_conceal_armor.json
src/main/resources/data/soundattract/loot_modifiers/enchant_conceal_tool.json
```

- [ ] **Step 4: Runtime smoke test**

Run:

```powershell
.\gradlew runClient
```

Expected:

- Game reaches main menu.
- No datapack parse error for `soundattract:conceal`.
- No global loot modifier codec error for:
  - `soundattract:add_item`
  - `soundattract:enchant_random_armor`
  - `soundattract:enchant_random_tool`

- [ ] **Step 5: In-game validation**

In a test world:

1. Use `/enchant @s soundattract:conceal 1` on a damageable item.
2. Confirm the item receives the enchantment.
3. Trigger stealth detection behavior wearing/holding conceal gear.
4. Open loot tables listed in the JSON conditions or use loot commands to generate them.
5. Confirm conceal books or enchanted armor/tools can appear at the configured chance.

---

## Acceptance Criteria

- `ModEnchantments.CONCEAL` is a `ResourceKey<Enchantment>`, not `null`.
- Conceal enchantment is defined by data JSON.
- NeoForge global loot modifier serializers are registered.
- Global loot modifier JSON entries exist and reference registered serializer names.
- Loot modifiers can add conceal books and enchant eligible armor/tools using the 1.21.1 Neo behavior.
- Stealth detection recognizes conceal via `holder.is(ModEnchantments.CONCEAL)`.
- `compileJava processResources` succeeds.
- No leftover empty stubs or deferred registration comments remain.
