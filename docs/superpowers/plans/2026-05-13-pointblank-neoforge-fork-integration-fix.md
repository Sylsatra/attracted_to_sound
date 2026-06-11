# PointBlank NeoForge Fork Integration Fix Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restore SoundAttract PointBlank integration for the NeoForge 1.21.1 `mod.pbj` fork by hooking the fork's real server-authoritative fire/reload paths.

**Architecture:** Replace old Forge/Fabric-derived hook points with mixins into PointBlank's packet-driven server handlers. Keep SoundAttract's integration logic server-local via `PointBlankIntegration`, and update attachment reflection to match the fork's recursive attachment API.

**Tech Stack:** Java 21, NeoForge 1.21.1, Sponge Mixin, PointBlank `mod.pbj` fork, Gradle.

---

## Context

The NeoForge 1.21.1 PointBlank build is not just a package rename from the old Forge/Fabric versions. Decompiled evidence shows it uses:

- `@Mod("pointblank")`
- `mod.pbj.*` packages
- `mod.pbj.Platform` service-loader abstraction
- `mod.pbj.platform.neoforge.NeoforgeNetworkService`
- packet-driven fire/reload requests

The current SoundAttract mixin hooks are stale:

- `hitScanTarget(...)` appears defined but not called by the decompiled 1.21.1 `GunItem` fire flow.
- `tryReload(Player, ItemStack)` starts client-side reload state and is not the authoritative server reload point.

Correct server paths from the decompiled fork:

```text
GunClientState.actionFire
→ GunItem.requestFireFromServer
→ HitScanFireRequestPacket or ProjectileFireRequestPacket
→ GunItem.handleClientHitScanFireRequest or GunItem.handleClientProjectileFireRequest
```

```text
GunClientState.actionReload
→ GunItem.requestReloadFromServer
→ ReloadRequestPacket
→ GunItem.handleClientReloadRequest
```

## File Structure

**Modify:**

- `src/main/java/com/example/soundattract/mixin/PointBlankGunItemMixin.java`
  - Remove stale `hitScanTarget(...)` and `tryReload(...)` hooks.
  - Add hooks for `handleClientHitScanFireRequest(...)`, `handleClientProjectileFireRequest(...)`, and `handleClientReloadRequest(...)`.

- `src/main/java/com/example/soundattract/integration/pointblank/PointBlankIntegration.java`
  - Update attachment reflection to use `Attachments.getAttachments(ItemStack, boolean)` recursively.
  - Use `AttachmentCategory.getName()` for muzzle category matching.
  - Preserve existing server/config guards and `SoundTracker.addSound(...)` calls.

- `src/main/templates/META-INF/neoforge.mods.toml`
  - Ensure `soundattract.pointblank.mixins.json` is declared as a mixin config.

- `src/main/resources/soundattract.pointblank.mixins.json`
  - Verify it still points to `PointBlankGunItemMixin` and uses the plugin.

**No new runtime classes are required.**

---

## Task 1: Fix PointBlank Mixin Hook Points

**Files:**

- Modify: `src/main/java/com/example/soundattract/mixin/PointBlankGunItemMixin.java`

- [ ] **Step 1: Replace stale imports**

Remove imports that are only needed by old hooks:

```java
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.List;
```

Add imports required by the fork's server handler signatures:

```java
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.UUID;
```

Keep:

```java
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
```

- [ ] **Step 2: Remove old hook methods**

Delete these methods from `PointBlankGunItemMixin`:

```java
soundattract$onFire(... hitScanTarget ...)
soundattract$onReloadRequest(... tryReload ...)
```

- [ ] **Step 3: Add hit-scan server fire hook**

Add an injection into:

```text
handleClientHitScanFireRequest(Lnet/minecraft/server/level/ServerPlayer;Lmod/pbj/item/FireModeInstance;Ljava/util/UUID;IIZJ)V
```

Use `@At("RETURN")` rather than `HEAD` so SoundAttract runs after PointBlank has accepted and processed the request. The handler should call:

```java
PointBlankIntegration.onGunShoot(player, player.getInventory().getItem(slotIndex));
```

Guard it with the existing config checks:

```java
if (!SoundAttractConfig.serverReady() || !SoundAttractConfig.SERVER.enablePointBlankIntegration.get()) return;
```

- [ ] **Step 4: Add projectile server fire hook**

Add an injection into:

```text
handleClientProjectileFireRequest(Lnet/minecraft/server/level/ServerPlayer;Lmod/pbj/item/FireModeInstance;Ljava/util/UUID;IIZDDDDDDIJ)V
```

Use `@At("RETURN")` and call:

```java
PointBlankIntegration.onGunShoot(player, player.getInventory().getItem(slotIndex));
```

Use the same config guard as the hit-scan hook.

- [ ] **Step 5: Add server reload hook**

Add an injection into:

```text
handleClientReloadRequest(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/item/ItemStack;Ljava/util/UUID;ILmod/pbj/item/FireModeInstance;)V
```

Use `@At("RETURN")` and call:

```java
PointBlankIntegration.onGunReload(player, itemStack);
```

Use the same config guard.

- [ ] **Step 6: Compile-check the descriptors**

Run:

```powershell
.\gradlew compileJava
```

Expected:

```text
BUILD SUCCESSFUL
```

If Mixin reports a descriptor mismatch, inspect the exact decompiled method signature in:

```text
decompiled/pointblank/mod/pbj/item/GunItem.java
```

and correct only the descriptor that failed.

---

## Task 2: Update PointBlank Attachment Reflection

**Files:**

- Modify: `src/main/java/com/example/soundattract/integration/pointblank/PointBlankIntegration.java`

- [ ] **Step 1: Add reflection handles for fork methods**

Inside `PointBlankIntegration`, cache these methods/fields after class loading:

```java
attachmentsClass.getMethod("getAttachments", ItemStack.class, boolean.class)
attachmentClass.getMethod("getCategory")
attachmentCategoryClass.getMethod("getName")
attachmentCategoryClass.getField("MUZZLE")
```

Use existing static fields or add private static fields for these reflection objects.

- [ ] **Step 2: Create a private helper for recursive attachments**

Add a private helper:

```java
private static java.util.Collection<ItemStack> getAttachmentStacks(ItemStack gunStack)
```

It should invoke:

```java
Attachments.getAttachments(gunStack, true)
```

The returned object is a `java.util.NavigableMap<?, ItemStack>`. Return its `.values()`.

If reflection fails, return `java.util.List.of()`.

- [ ] **Step 3: Use recursive attachments in shoot range calculation**

In `calculateShootRangeWeight`, replace direct reflection call to:

```java
getAttachments(ItemStack.class)
```

with the new helper.

This ensures nested suppressor/muzzle attachments are included.

- [ ] **Step 4: Use name-based muzzle matching**

In `onGunShoot`, replace direct object identity comparison:

```java
category == muzzleCategory
```

with category name comparison:

```java
Objects.equals(getCategoryName(category), getCategoryName(muzzleCategory))
```

Implement this via private helper:

```java
private static String getCategoryName(Object category)
```

It should invoke `AttachmentCategory.getName()` and return `null` on failure.

- [ ] **Step 5: Preserve failure behavior**

Do not let attachment reflection failure cancel the whole gunshot sound. If attachment lookup fails, SoundAttract should still add the base gunshot sound with no attachment reduction.

Specifically, avoid this behavior:

```java
if (attachmentsClass == null) return;
```

inside `onGunShoot` before sound creation. Reflection failure should skip reductions only.

- [ ] **Step 6: Compile-check**

Run:

```powershell
.\gradlew compileJava
```

Expected:

```text
BUILD SUCCESSFUL
```

---

## Task 3: Ensure PointBlank Mixin Config Is Loaded

**Files:**

- Modify: `src/main/templates/META-INF/neoforge.mods.toml`
- Verify: `src/main/resources/soundattract.pointblank.mixins.json`

- [ ] **Step 1: Check mixin config declarations**

Ensure the template contains both mixin declarations:

```toml
[[mixins]]
config="${mod_id}.mixins.json"

[[mixins]]
config="${mod_id}.pointblank.mixins.json"
```

- [ ] **Step 2: Verify PointBlank mixin JSON**

Confirm `src/main/resources/soundattract.pointblank.mixins.json` includes:

```json
{
  "required": false,
  "package": "com.example.soundattract.mixin",
  "compatibilityLevel": "JAVA_21",
  "plugin": "com.example.soundattract.mixin.SoundAttractMixinPlugin",
  "mixins": [
    "PointBlankGunItemMixin"
  ],
  "injectors": {
    "defaultRequire": 1
  }
}
```

- [ ] **Step 3: Verify plugin target check**

Confirm `SoundAttractMixinPlugin` checks:

```text
mod/pbj/item/GunItem.class
```

not the old `com/vicmatskiv/pointblank/...` path.

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

## Task 4: Verification

**Files:**

- No additional source files.

- [ ] **Step 1: Run full compile path**

Run:

```powershell
.\gradlew compileJava processResources
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 2: Run client smoke test**

Run the normal NeoForge client task used by this project, usually:

```powershell
.\gradlew runClient
```

Expected:

- Minecraft reaches main menu.
- No Mixin apply error for `PointBlankGunItemMixin`.
- No `ClassNotFoundException` for `mod.pbj.item.GunItem` when PointBlank is absent or disabled.

- [ ] **Step 3: In-game functional test with PointBlank installed**

Test in a world with PointBlank installed:

1. Equip a hit-scan PointBlank gun.
2. Fire it near mobs.
3. Confirm mobs react to SoundAttract sound.
4. Reload the gun.
5. Confirm reload sound attraction occurs.
6. Equip a projectile-based PointBlank weapon if available.
7. Fire it near mobs.
8. Confirm mobs react.

Expected:

- Hit-scan fire creates SoundAttract sound.
- Projectile fire creates SoundAttract sound.
- Reload creates SoundAttract sound.
- Suppressor/muzzle attachments reduce range when configured.

- [ ] **Step 4: Compatibility test without PointBlank**

Launch without PointBlank installed.

Expected:

- Game loads.
- PointBlank mixin is skipped by `SoundAttractMixinPlugin`.
- No class-loading crash from `PointBlankIntegration`.

---

## Acceptance Criteria

- `PointBlankGunItemMixin` no longer depends on stale `hitScanTarget(...)` or client-side `tryReload(...)`.
- SoundAttract hooks all authoritative PointBlank fork server actions:
  - hit-scan fire
  - projectile fire
  - reload
- PointBlank attachment reductions use recursive fork attachment lookup.
- Reflection failure does not suppress base gunshot sound creation.
- `compileJava` passes.
- `processResources` passes.
- Game launches with and without PointBlank installed.

## Implementation Notes

- Do not add diagnostic-only logs unless a descriptor fails at runtime.
- Do not change public config names.
- Do not refactor unrelated integrations.
- Do not add direct compile-time imports for `mod.pbj.*` in `PointBlankIntegration`; keep reflection there to preserve optional loading.
- It is acceptable for `PointBlankGunItemMixin` descriptors to reference `mod/pbj/item/FireModeInstance` because the mixin config is optional and gated by `SoundAttractMixinPlugin`.
