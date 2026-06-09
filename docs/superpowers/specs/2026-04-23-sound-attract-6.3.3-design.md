# Sound Attract 6.3.3 — Design Spec

**Date:** 2026-04-23
**Baseline:** Sound Attract 6.3.2 (Forge 1.20.1-47.4.0)
**Target version:** 6.3.3

## Scope

One bug fix, two new features.

## 1. Bug fix: Turkish locale crash

`String.toLowerCase()` / `toUpperCase()` without a `Locale` argument uses the JVM default. Under `tr_TR`, `"I".toLowerCase()` = `"ı"` (dotless), which breaks `ResourceLocation` paths (which require `[a-z0-9_./-]`) and string-keyed map lookups — producing IAE crashes at load or on player actions.

**Fix:** sweep the codebase; replace all bare `toLowerCase()` / `toUpperCase()` with the `Locale.ROOT` variant.

**Known sites:**
- `client/AttractionClientEvents.java:85` — `action.toLowerCase()` folded into a `ResourceLocation` path (primary crash site).
- `config/PlayerStance.java:36` — map-key lookup.
- `camo/CamoTextureGenerator.java:351, 438` — `ResourceLocation` paths.
- `config/SoundAttractConfig.java:182` — cosmetic log line.

## 2. Feature: Wooden floor creek

### 2.1 Assets
Four `.ogg` files in `floor_creek_sounds/` moved to `assets/soundattract/sounds/floor/wooden_floor_creek_1..4.ogg`. Registered as `soundattract:wooden_floor_creek` (`SoundSource.BLOCKS`).

### 2.2 Block scope — tag-driven
Block tag `#soundattract:floor_creek_blocks`; default tag JSON pulls in `#minecraft:planks`, `#minecraft:wooden_slabs`, `#minecraft:wooden_stairs`.

### 2.3 Detection model — server-side, cumulative distance
Per-player state (`WeakHashMap<UUID, FloorCreekState>`):
- `distanceAccumulator` — horizontal distance on wood-tag blocks.
- `lastPos`, `wasOnGround`.

Each `PlayerTickEvent.Post` (server phase) for each player:
1. If `!enableFloorCreek` → skip.
2. Compute horizontal `dx` from `lastPos`.
3. **Jump takeoff** (`wasOnGround && !onGround`): if previous surface was on-wood, roll 80%.
4. **Jump landing** (`!wasOnGround && onGround`): if current floor is wood, roll 80%.
5. **On-ground movement**: if on wood, add `dx` to accumulator; while `accumulator >= 2.0`, subtract 2.0 and roll at pose-derived probability.
6. If not on wood, reset accumulator to 0.

**Pose mapping** (mutually exclusive, evaluated in order):
- `player.isVisuallySwimming() && !player.isInWater()` → 40% (crawling through 1-block gap on wood).
- `player.isVisuallySwimming() && player.isInWater()` → skip (swim in water, don't creak).
- `player.isCrouching()` → 30%.
- `player.isSprinting()` → 80%.
- else → 20% (standing walk).

### 2.4 Sound emission
Server calls `level.playSound(null, x, y, z, ModSounds.WOODEN_FLOOR_CREEK.get(), SoundSource.BLOCKS, 1.0f, 0.9f + rand*0.2f)`. Every nearby client hears it; the existing `SoundAttractionEvents.onPlaySound` pipeline picks it up and registers it in `SoundTracker` using the defaults below.

### 2.5 Config
Common category `floor_creek`:
- `enableFloorCreek` (bool, default `true`).
- `floorCreekProbSwimmingCrawling` (double, default `0.40`).
- `floorCreekProbSneaking` (double, default `0.30`).
- `floorCreekProbWalking` (double, default `0.20`).
- `floorCreekProbSprinting` (double, default `0.80`).
- `floorCreekProbJump` (double, default `0.80`).
- `floorCreekDistanceStep` (double, default `2.0`).

`soundIdWhitelist` gets `soundattract:wooden_floor_creek` appended.
`soundDefaults` gets `soundattract:wooden_floor_creek;20;20` appended.

Migration: schema bumps from 15 → 16; `bakeConfig()` migration block appends missing entries without overwriting user edits.

## 3. Feature: Arrow investigation

### 3.1 Projectile scope — tag + config list
Two allowlists, unioned at runtime (either matches = project is tracked):

1. **Entity-type tag** `#soundattract:investigate_projectiles` — datapack-overridable. Default tag JSON ships with `minecraft:arrow`, `minecraft:spectral_arrow`.
2. **Config list** `investigateProjectileIds` (new config key) — in case the pack ships no datapack. Default contains all vanilla combat projectiles:
   `minecraft:arrow`, `minecraft:spectral_arrow`, `minecraft:trident`, `minecraft:snowball`, `minecraft:egg`, `minecraft:ender_pearl`, `minecraft:experience_bottle`, `minecraft:potion`, `minecraft:fireball`, `minecraft:small_fireball`, `minecraft:dragon_fireball`, `minecraft:wither_skull`, `minecraft:shulker_bullet`, `minecraft:llama_spit`.
   Parsed into `INVESTIGATE_PROJECTILE_TYPES_CACHE : Set<EntityType<?>>` during `bakeConfig`.

Runtime check: `entity instanceof Projectile && (entityType.is(TAG) || INVESTIGATE_PROJECTILE_TYPES_CACHE.contains(entityType))`. Generalizing to `Projectile` (not just `AbstractArrow`) so thrown potions / fireballs / snowballs also count. `AbstractArrow.inGround` is still consulted for landing gating when the projectile is an arrow subclass.

### 3.2 Near-miss detection (segment-based, per-tick)
Subscribe to `EntityTickEvent.Post`. Track a `Map<Integer arrowId, ArrowFlightState>` where state holds `prevPos`, `spawnPos`, `notifiedMobIds`. On each tick:
- Skip if `arrow.inGround` or `!arrow.isAlive()`.
- Build segment `prevPos → currentPos`.
- Query `level.getEntitiesOfClass(Mob.class, aabbInflate(segmentAABB, 2.0 + buffer), predicate)` where predicate = AttractionGoal-enabled mob + not already notified.
- For each hit, compute segment-to-AABB squared distance. If ≤ `(arrowNearMissRadius)²`, trigger investigation and add UUID to `notifiedMobIds`.
- Update `prevPos`.

### 3.3 Landing detection
Subscribe to `ProjectileImpactEvent`. On matching arrow impact, scan mobs in `AABB.ofSize(impactPos, 2*r, 2*r, 2*r)` where `r = arrowImpactRadius`. For each AttractionGoal mob, trigger investigation.

### 3.4 Origin resolution (hybrid)
```
Vec3 resolveOrigin(AbstractArrow arrow, @Nullable Vec3 cachedSpawnPos):
    Entity owner = arrow.getOwner()
    if owner != null: return owner.position()
    if cachedSpawnPos != null: return cachedSpawnPos
    Vec3 dir = arrow.getDeltaMovement().normalize()
    if dir.lengthSqr() < 1e-6: return arrow.position()
    Vec3 start = arrow.position()
    Vec3 end = start.subtract(dir.scale(arrowReverseExtrapolateMaxDistance))
    BlockHitResult hit = level.clip(new ClipContext(start, end, BLOCK, NONE, arrow))
    return hit.getType() == MISS ? end : hit.getLocation()
```
Then `target = origin + (rand*2-1)*offset, surfaceY(x,z), origin.z + (rand*2-1)*offset` where `offset = arrowInvestigationOffset` (default 10). Y snapped via `level.getHeight(Heightmap.MOTION_BLOCKING_NO_LEAVES, x, z)`.

### 3.5 Investigation dispatch
For each matched mob:
- If `arrowInvestigationRespectCurrentTarget` and (`mob.getTarget() != null` or mob's `AttractionGoal.getTargetSoundPos() != null`), skip.
- If per-mob cooldown not expired, skip.
- Otherwise call `SoundTracker.addVirtualSound(targetPos, dimKey, range, weight, lifetime, sourcePlayerUuid /*may be null*/, "arrow_investigation")` so the record is visible to `SoundTracker.getNearbySounds`, and AttractionGoal will pick it up naturally.
- Set cooldown `now + arrowInvestigationCooldownTicks`.

Virtual sound ids used: `soundattract:arrow_whiz` (near-miss), `soundattract:arrow_impact` (landing).

### 3.6 Config
Common category `arrow_investigation`:
- `enableArrowInvestigation` (bool, default `true`).
- `arrowNearMissRadius` (double, default `2.0`).
- `arrowImpactRadius` (double, default `4.0`).
- `arrowInvestigationOffset` (int, default `10`).
- `arrowInvestigationCooldownTicks` (int, default `40`).
- `arrowReverseExtrapolateMaxDistance` (double, default `32.0`).
- `arrowInvestigationRespectCurrentTarget` (bool, default `true`).
- `investigateProjectileIds` (string list, default: 14 vanilla combat projectile entity ids listed in §3.1).

`soundIdWhitelist` gets `soundattract:arrow_whiz`, `soundattract:arrow_impact`.
`soundDefaults` gets `soundattract:arrow_whiz;16;10`, `soundattract:arrow_impact;24;15`.

Migration: same schema bump (15 → 16).

## 4. Testing

- **Turkish locale:** Launch with `-Duser.language=tr -Duser.country=TR` and walk/sneak/sprint on wood → no crash at `ResourceLocation` construction. Config load with default strings → no crash.
- **Floor creek:** Place a plank bridge. Walk 100 blocks per pose; verify observed fire rate ≈ configured probability × (distance/stepSize). Step onto stone → accumulator resets. Crawl under slab gap on wood → fires at 40%; crawl in water → no fire. Sprint jumps generate two rolls.
- **Arrow:** Skeleton fires past idle villager → investigates. Dispenser fires past villager → reverse-extrapolation gives a plausible target in cone-shaped origin area. Arrow volley of 10 → single investigation per mob within 2s cooldown. Arrow passes while mob has combat target → skipped.

## 4.1 Hardening — leaks, NPE, races

All new state lives on the server thread, but we program defensively so future threading changes or modded events don't break us.

**`FloorCreekEvents`:**
- `STATES` uses `ConcurrentHashMap<UUID, State>` (not `WeakHashMap` — UUID keys are not reachable elsewhere, which would make entries prematurely collectable AND leak them when logout happens off the server thread).
- Subscribe to `PlayerEvent.PlayerLoggedOutEvent` → `STATES.remove(uuid)`.
- Subscribe to `ServerStoppingEvent` → `STATES.clear()` (dev-environment reload safety).
- Shared `Random` replaced with `ThreadLocalRandom.current()`.
- All `SoundAttractConfig.COMMON.*.get()` calls guarded by an early-return `null` check on `SoundAttractConfig.COMMON` itself (paranoid: config can be null during very early startup if an event fires before `bakeConfig`).
- `level.getBlockState(feet)` is called only after confirming the server player is alive and not a spectator (already in spec); `feet` built with `BlockPos.containing(...)` which never returns null.
- `event.player instanceof ServerPlayer` pattern-variable already guards dimension access.

**`ArrowInvestigationEvents`:**
- `ARROWS` and `COOLDOWNS` use `ConcurrentHashMap`.
- `ARROWS` entries carry `spawnTick`; `onServerTick` prunes entries older than 1200 ticks (60s) in addition to `EntityLeaveLevelEvent` cleanup. This covers server shutdown races, dimension migration without leave event, and any future Forge event ordering changes.
- `ARROWS` has a hard-cap (e.g. 4096 entries); `onEntityJoin` drops new entries past the cap and logs at DEBUG.
- `COOLDOWNS` pruned every server tick as part of `onServerTick`.
- Null guards: `event.getRayTraceResult()` (can be null per Forge contract — skip if null), `arrow.getOwner()` (already handled), `event.getLevel()` cast to `ServerLevel` (instance-checked).
- `arrow.level()` re-checked against the tick level to avoid processing a cross-dimension arrow whose event straggled.
- `findAttractionGoal` iterates `mob.goalSelector.getAvailableGoals()`. The `Set` is owned by the mob and mutated only from the server thread during `Mob.tick()`. We read it from `LevelTickEvent` / `ProjectileImpactEvent`, both server-thread. No lock needed; a defensive `try { ... } catch (ConcurrentModificationException ignored)` is NOT added (would mask real bugs).
- `ThreadLocalRandom.current()` replaces shared `Random`.
- `Heightmap.Types.MOTION_BLOCKING_NO_LEAVES` returns an int Y even at world bottom; `BlockPos.containing(tx, ty, tz)` clamps via int-casting — safe.
- `SoundTracker.addVirtualSound` invocation guards against null dim key via `level.dimension().location().toString()` (always non-null for a `ServerLevel`).
- `SoundAttractConfig.SOUND_DEFAULT_ENTRIES_CACHE.get(loc)` may return `null` before `bakeConfig` completes — fall back to hardcoded defaults already; also guard `SoundAttractConfig.COMMON == null` early-return.

**Cross-feature:**
- Subscriber instances are registered once in `SoundAttractMod`'s constructor — no double-registration. Uninstallation on server stop clears maps via `ServerStoppingEvent`.

## 5. Out of scope
- Porting to NeoForge / 1.21.
- Refactoring unrelated `toLowerCase` call sites that aren't locale-sensitive in practice (we still fix them per Q9 sweep option, just not part of test coverage).
