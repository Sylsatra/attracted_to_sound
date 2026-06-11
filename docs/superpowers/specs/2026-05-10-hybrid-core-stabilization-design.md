# Hybrid Core Stabilization Design

## Goal

Reach a clean `compileJava` result for the NeoForge 1.21.1 Sound Attract core port while preserving gameplay-critical behavior and explicitly deferring non-critical cosmetic, optional integration, loot, and enchantment systems.

## Context

The project is a NeoForge 1.21.1 port of Sound Attract 6.3.3 from Forge 1.20.1. Core source has already been copied and partially migrated. Networking, config registration, event bus registration, tick events, integration dependency removal, and several no-op integration shims have already been addressed.

The current compile state is concentrated in a small set of remaining migration buckets:

- NeoForge 1.21.1 loot modifier codec API changes.
- Removed or redesigned 1.21 enchantment APIs affecting the custom `conceal` enchantment.
- Client rendering API changes for player/camo layers.
- Old `ItemStack` NBT access replaced by data components.
- Event API changes for damage, visibility, and entity tracking.
- Optional integration references such as `CsGrenadesTracker`.
- Entity predicate profile codecs using removed JSON helper methods.

## Scope

### Preserve now

- Sound attraction AI and sound tracking.
- Stealth detection runtime logic, except the deferred custom conceal enchantment path.
- Scent creation, querying, and cache cleanup.
- Core mob behavior, pathfinding, raids, and goals.
- Config registration and config cache loading.
- Core networking packet registration and payload handling.

### Defer now

- Custom loot modifier behavior.
- Custom `conceal` enchantment registration and enchantment-specific stealth bonus.
- Cosmetic player and mob camo render layers.
- Optional integration-specific tracking such as CS Grenades smoke LOS.
- Advanced `EntityPredicate` profile serialization if not required for core startup.

## Design

Use a hybrid stabilization approach. Keep code paths that affect gameplay-critical runtime systems active, and replace non-critical compile blockers with explicit dormant shims or no-op registration surfaces. These shims should be easy to identify and replace in later port stages.

The implementation should not attempt broad refactors. It should make the smallest necessary changes to reach core compile success while avoiding half-ported APIs that may fail at runtime. Every deferred feature should either become a no-op class/method or be unregistered from the active mod entrypoint.

## Architecture Boundaries

- `SoundAttractMod` remains the authoritative mod entrypoint.
- Gameplay event handlers remain registered on the NeoForge event bus.
- Deferred systems should either keep their public classes but not register behavior, or keep minimal no-op methods used by core callers.
- Camo state/degradation remains active, but client visual render layer registration can be disabled until the render APIs are fully ported.
- Loot/enchantment classes can remain present as inert compatibility shells, but the mod should not register broken serializers or invalid enchantment types.

## Verification

Primary verification is compile-driven:

```powershell
.\gradlew.bat compileJava --console=plain
```

Success means `compileJava` passes for the core-only NeoForge project with integrations excluded. Warnings are acceptable during this stabilization stage. Runtime validation comes after compile success.

## Follow-up Work

After core compile passes, later specs should restore deferred systems one at a time:

1. Full NeoForge 1.21.1 loot modifier port.
2. Full 1.21 enchantment/data component implementation for `conceal`.
3. Full camo client render layer port.
4. Optional integration ports.
