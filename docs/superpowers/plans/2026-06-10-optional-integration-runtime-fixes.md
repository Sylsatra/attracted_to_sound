# Optional Integration Runtime Fixes Plan

## Goal
Bring the NeoForge 1.21.1 optional integrations closer to full parity by fixing the runtime issues reported by testing and the current `debug.log`:

- Hot Bath splash hot water bottles must wash camouflage layers.
- Point Blank integration must target Vic's original `com.vicmatskiv.pointblank` API and jar instead of Point Blank: Jelly `mod.pbj`.
- Sound Attract must stop producing optional-mod runtime errors found in `debug.log`, especially CustomNPCs mixin and target bridge failures.

All integrations remain optional.

## Findings
- Hot Bath 1.21.1 contains `ThrownBathWater.applySplash(SplashBathWaterBottleItem)` and `ThrownCustomFluidBottle.applySplash(SplashCustomFluidBottleItem, CustomFluidDefinition)`, so the Forge splash mixin shape can be adapted directly.
- `HotBathIntegration.applySplashWash(...)` already exists, but no Hot Bath projectile mixins are registered.
- Hot Bath dirtiness and custom fluid block support can use the actual 1.21.1 API: `DirtinessHandler`, `CustomFluidBlockEntity`, and `CustomFluidDefinition`.
- Vic's Point Blank exposes the same gun request method names as the current PBJ mixin, but under `com.vicmatskiv.pointblank`.
- Vic's attachment reflection APIs exist with the same method shape under `com.vicmatskiv.pointblank.attachment`.
- `debug.log` shows CustomNPCs AI mixins failing because they inject obfuscated names (`m_8036_`, `m_8045_`) while this jar exposes `canUse` and `canContinueToUse`.
- `debug.log` also shows repeated `NpcEvent$TargetEvent.setCanceled(boolean)` reflection failures. This event is not cancellable in the installed CustomNPCs jar, so target cancellation needs an `EventHooks.onNPCTarget` mixin instead of assuming event-bus cancellation.

## Implementation Checklist

1. Hot Bath splash and API parity
   - Add `ThrownBathWaterMixin` and `ThrownCustomFluidBottleMixin`.
   - Register both in `soundattract.hotbath.mixins.json`.
   - Update `HotBathIntegration` to read custom fluid block entities directly.
   - Update `HotBathIntegration` to use `DirtinessHandler.getDirtiness(player)`.
   - Add normal Hot Bath bottle item ids to `ITEM_TO_FLUID` for fallback fluid resolution.
   - Match Forge bath camo wash semantics by applying full camo wash while bathing.

2. Vic's Point Blank migration
   - Replace the compile-only Point Blank jar with `pointblank-neoforge-1.21-1.11.1.jar`.
   - Change `PointBlankGunItemMixin` target and parameter types to `com.vicmatskiv.pointblank`.
   - Change `PointBlankIntegration` reflection packages to `com.vicmatskiv.pointblank.attachment`.
   - Add a Point Blank mixin plugin so the mixin config only applies when optional mod id `pointblank` is loaded.

3. CustomNPCs runtime fixes
   - Change AI mixin injections from obfuscated method names to `canUse` and `canContinueToUse`, with `require = 0`.
   - Add a CustomNPCs `EventHooks.onNPCTarget` mixin that returns `true` when stealth blocks targeting.
   - Make the reflective CustomNPCs target bridge no-op safely when the API event is not cancellable.

4. Verification
   - Search for stale `mod.pbj` and old Point Blank jar references.
   - Run `gradle compileJava --console=plain --warning-mode=summary`.
   - Run `gradle processResources --console=plain --warning-mode=summary`.
   - Re-scan the source for obvious empty or missing mixin config entries.

## Risk Notes
- Runtime verification still requires launching Minecraft with the chosen optional mod set.
- Point Blank runtime behavior depends on the installed mod jar matching the Vic jar compiled against.
- CustomNPCs target cancellation is adapted through its `EventHooks` return path because its public target event does not implement `ICancellableEvent`.
