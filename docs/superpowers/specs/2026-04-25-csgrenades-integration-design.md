# CS Grenades Integration — Design Spec

**Date:** 2026-04-25
**Author:** Cascade (brainstorming session with @Sylsatra)
**Status:** Draft — awaiting user review
**Mod target:** `com.example.soundattract` (Forge 1.20.1-4.1.4)
**Integration target:** `csgrenades` (Counter-Strike Grenades, Kotlin, prerelease 1.5.0)

---

## 1. Goal

Add an **optional** integration with the CS Grenades mod so that its grenades interact with the Sound Attract mod's existing sound, LOS, and AI-goal subsystems in thematically appropriate ways:

| Grenade | Mechanic |
|---|---|
| HE (bomb) | Loud explosion attracts mobs through the existing sound pipeline |
| Flashbang | Blinds mobs within range/LOS/FOV, suppressing their targeting |
| Smoke | Persistent cloud blocks line-of-sight between mob and target |
| Incendiary / Molotov | Eligible non-fire-immune mobs flee from active fire grenades |
| Decoy | **Out of scope for v1** — upstream prerelease has not implemented mimic-gunshot sounds yet |

Integration is gated behind a master flag (`enableCsgrenadesIntegration`, default `true`) and per-mechanic sub-flags, and loads only when `csgrenades` is present.

## 2. Non-Goals

- Implementing fire patch block-spread logic (upstream's `FireGrenadeEntity` is a stub; fire damage is out of our scope)
- Modifying `csgrenades` itself
- Adding Decoy support (deferred until upstream ships the gunshot-mimic feature)
- Persisting effect state across server restarts (all state is transient in-memory)

## 3. Architecture Overview

Follows the existing `integration/<mod>/` pattern (mirrors `integration/pointblank/`).

```
src/main/java/com/example/soundattract/integration/csgrenades/
    CsGrenadesCompat.java          - isLoaded() guard, reflective-free access point
    CsGrenadesIntegration.java     - lifecycle bootstrap (called from SoundAttractMod)
    CsGrenadesEventHandler.java    - subscribes to GrenadeActivateEvent on FORGE bus
    CsGrenadesTracker.java         - periodic scan of active smoke/fire grenades
    SmokeLosSuppression.java       - pure ray-sphere intersection math
    FlashbangEffect.java           - per-mob blind-until-tick map, apply + query
    FleeFromFireGoal.java          - Goal attached to eligible mobs
```

**Event flow:**

1. Server load: `SoundAttractMod.handleCsgrenadesIntegration()` checks `CsGrenadesCompat.isLoaded()`. If true, registers `CsGrenadesEventHandler` and `CsGrenadesTracker` on the Forge bus.
2. Grenade activates upstream → `GrenadeActivateEvent` fires → `CsGrenadesEventHandler` dispatches on `grenadeType`:
   - `FLASH_BANG` → `FlashbangEffect.apply(level, pos)` — one-shot range/LOS/FOV scan
   - `SMOKE_GRENADE`, `INCENDIARY`, `MOLOTOV` → no immediate action (tracker picks them up)
   - `HE_GRENADE` → no custom action (whitelist-seeded sound handles attraction)
   - `DECOY` → no action (v1 out of scope)
3. `CsGrenadesTracker` runs every `csgrenadesTrackerScanIntervalTicks` (default 5) on server tick END, rebuilding cached `smokeActive` and `fireActive` entity lists by iterating `CSGrenadeServerAPI.entity.grenades` and filtering.
4. Existing subsystems query the tracker:
   - `FovEvents.hasSmartLineOfSight` consults `CsGrenadesTracker.smokeBlocksRay`
   - `StealthDetectionEvents.shouldSuppressTargeting` consults `FlashbangEffect.isMobBlind`
   - `FleeFromFireGoal.canUse` consults `CsGrenadesTracker.nearestActiveFire`
5. `FleeFromFireGoal` is attached at `EntityJoinLevelEvent` to mobs whose ID is in `fleeFromFireEligibleMobs` and for which `mob.fireImmune() == false`.

## 4. Config Schema

All new keys live in a new `csgrenades_integration` push-block inside the existing `IntegrationConfig.java`, mirrored into `SoundAttractConfig.Common`.

```
# Master
enableCsgrenadesIntegration : bool = true

# Flashbang
enableFlashbangBlinding       : bool   = true
flashbangBlindRadius          : double = 16.0    (2.0, 64.0)
flashbangBlindDurationTicks   : int    = 100     (1, 6000)    # base; scaled by (1 - dist/radius)
flashbangRequireLineOfSight   : bool   = true
flashbangRequireFov           : bool   = true
flashbangMobScanBudget        : int    = 64      (8, 512)

# Smoke
enableSmokeLosBlocking        : bool   = true
smokeCloudRadius              : double = 4.0     (1.0, 16.0)
smokeLifetimeMaxTicks         : int    = 400     (20, 6000)   # safety cap for tracker

# Incendiary / Molotov flee
enableFireFlee                : bool   = true
fireFleeDangerRadius          : double = 6.0     (1.0, 32.0)
fireFleeAwayDistance          : double = 16.0    (4.0, 64.0)
fireFleeSpeedModifier         : double = 1.4     (0.5, 3.0)
fireFleeCooldownTicks         : int    = 40      (1, 600)
fleeFromFireEligibleMobs      : list<string> = [
    "minecraft:zombie","minecraft:husk","minecraft:drowned","minecraft:skeleton",
    "minecraft:stray","minecraft:creeper","minecraft:spider","minecraft:cave_spider",
    "minecraft:wolf","minecraft:villager","minecraft:iron_golem","minecraft:piglin",
    "minecraft:piglin_brute","minecraft:zoglin","minecraft:hoglin","minecraft:witch",
    "minecraft:pillager","minecraft:vindicator","minecraft:evoker","minecraft:ravager"
]

# Tracker performance
csgrenadesTrackerScanIntervalTicks : int = 5  (1, 40)
```

### Whitelist seeding (schema migration)

`CURRENT_SCHEMA_VERSION` is bumped. Migration appends (append-only, de-duplicated, preserving existing user values) the following to `soundIdWhitelist` and `soundDefaults`, matching the pattern used for PointBlank's `pointblank:gun_action`:

| Sound ID | Range | Weight |
|---|---|---|
| `csgrenades:hegrenade.explode` | 64 | 20 |
| `csgrenades:hegrenade.detonate` | 64 | 20 |
| `csgrenades:flashbang.explode` | 32 | 8 |
| `csgrenades:incendiary.detonate` | 24 | 6 |
| `csgrenades:molotov.detonate` | 24 | 6 |
| `csgrenades:smokegrenade.emit` | 12 | 3 |

Seeded sound IDs remain inert if `csgrenades` is uninstalled later — no cleanup required.

### Runtime data (in-memory only)

```java
// FlashbangEffect
ConcurrentHashMap<UUID, Long> blindUntilTick;   // mobId -> absolute server tick
// capped at flashbangMobScanBudget * 16 entries (1024 default); oldest evicted on overflow

// CsGrenadesTracker (per-server singleton, but scoped per ServerLevel inside)
volatile List<CounterStrikeGrenadeEntity> smokeActive;
volatile List<CounterStrikeGrenadeEntity> fireActive;
long lastRebuildTick;
```

## 5. Algorithms

### 5.1 Flashbang Blinding

Trigger: `GrenadeActivateEvent` with `grenadeType == FLASH_BANG`, server side only.

```
onActivate(grenade):
  if !enableCsgrenadesIntegration || !enableFlashbangBlinding: return
  level   = grenade.level(); if not ServerLevel: return
  center  = grenade.position()
  radius  = flashbangBlindRadius
  baseDur = flashbangBlindDurationTicks
  now     = level.getGameTime()

  aabb   = AABB(center).inflate(radius)
  mobs   = level.getEntitiesOfClass(Mob.class, aabb, m -> m.isAlive())
  if mobs.size > flashbangMobScanBudget: mobs = mobs.subList(0, budget)

  for mob in mobs:
    d = distance(mob.getEyePosition(), center); if d > radius: continue
    if flashbangRequireLineOfSight
        && !OptimizedLOS.hasLineOfSight(mob.getEyePosition(), center, level): continue
    if flashbangRequireFov
        && !FovEvents.isTargetInFov(mob, grenade, /*checkObstructions=*/false): continue
    dur = max(1, (int) round(baseDur * (1 - d / radius)))
    FlashbangEffect.blind(mob.getUUID(), now + dur)

  FlashbangEffect.enforceCap()
```

**Hook into targeting:** prepend a check in `StealthDetectionEvents.shouldSuppressTargeting(Mob mob)`:

```java
if (FlashbangEffect.isMobBlind(mob.getUUID(), mob.level().getGameTime())) return true;
```

This reuses the existing target-suppression machinery (LivingChangeTargetEvent is already intercepted).

### 5.2 Smoke LOS Blocking

**Tracker rebuild** every `csgrenadesTrackerScanIntervalTicks`:

```
rebuildSmoke(level, now):
  snapshot = new ArrayList(CSGrenadeServerAPI.entity.grenades.values())   // defensive copy
  list = []
  for g in snapshot:
    if g == null || g.level() != level || !g.isAlive(): continue
    if g.grenadeType != SMOKE_GRENADE: continue
    if !g.isActivated(): continue
    if now - g.getActivationTick() > smokeLifetimeMaxTicks: continue
    list.add(g)
  smokeActive = list   // volatile swap
```

**Ray-sphere intersection** (branch-free, standard analytic form):

```
blocksRay(start, end, center, radius):
  d = end - start
  f = start - center
  a = d.lengthSqr(); if a < 1e-9: return false
  b = 2 * f.dot(d)
  c = f.lengthSqr() - radius*radius
  disc = b*b - 4*a*c; if disc < 0: return false
  sq = sqrt(disc)
  t1 = (-b - sq) / (2*a)
  t2 = (-b + sq) / (2*a)
  return (t1 in [0,1]) || (t2 in [0,1])
```

**Hook** inside `FovEvents.hasSmartLineOfSight(looker, target)`, after existing LOS cache / raycast:

```java
if (SoundAttractConfig.COMMON.enableCsgrenadesIntegration.get()
    && SoundAttractConfig.COMMON.enableSmokeLosBlocking.get()
    && CsGrenadesTracker.smokeBlocksRay(
        looker.level(), looker.getEyePosition(), target.getEyePosition())) {
    return false;
}
```

Cached LOS results short-circuit before this check — smoke only runs on fresh evaluations. Typical tracker list is 0–3 entities; fast-exit when empty.

### 5.3 Incendiary / Molotov Flee

**Eligibility at entity spawn:**

```java
@SubscribeEvent onEntityJoin(EntityJoinLevelEvent e) {
    if (e.getLevel().isClientSide) return;
    if (!CsGrenadesCompat.isLoaded()) return;
    if (!SoundAttractConfig.COMMON.enableCsgrenadesIntegration.get()) return;
    if (!SoundAttractConfig.COMMON.enableFireFlee.get()) return;
    if (!(e.getEntity() instanceof PathfinderMob mob)) return;
    if (mob.fireImmune()) return;
    String id = ForgeRegistries.ENTITY_TYPES.getKey(mob.getType()).toString();
    if (!SoundAttractConfig.FLEE_FROM_FIRE_ELIGIBLE_SET.contains(id)) return;
    mob.goalSelector.addGoal(1, new FleeFromFireGoal(mob));
}
```

**Goal logic** (new file, patterned after `FleeFromUnseenAttackerGoal`):

```java
class FleeFromFireGoal extends Goal {
    // flags: MOVE. priority 1.
    private long lastFleeTick = Long.MIN_VALUE;
    private Vec3 wanted;

    boolean canUse() {
        if (!enableCsgrenadesIntegration || !enableFireFlee) return false;
        long now = mob.level().getGameTime();
        if (now - lastFleeTick < fireFleeCooldownTicks) return false;
        Vec3 fireCenter = CsGrenadesTracker.nearestActiveFire(
            mob.level(), mob.position(), fireFleeDangerRadius);
        if (fireCenter == null) return false;
        Vec3 away = DefaultRandomPos.getPosAway(
            (PathfinderMob) mob, (int) fireFleeAwayDistance, 7, fireCenter);
        if (away == null) return false;
        this.wanted = away;
        return true;
    }
    void start() {
        mob.getNavigation().moveTo(wanted.x, wanted.y, wanted.z, fireFleeSpeedModifier);
        lastFleeTick = mob.level().getGameTime();
    }
    boolean canContinueToUse() { return !mob.getNavigation().isDone(); }
    void stop() { wanted = null; }
}
```

**Tracker query:** `nearestActiveFire(level, pos, maxDist)` iterates `fireActive` (INCENDIARY + MOLOTOV combined), returns nearest grenade position within `maxDist` or `null`.

Interaction with `AttractionGoal`: `FleeFromFireGoal` uses priority 1 + `MOVE` flag → vanilla goal selector naturally pre-empts `AttractionGoal` at equal/lower priority. Exact priority of existing goals verified at implementation time.

## 6. Safety & Error Handling

### Class-loading isolation

- `CsGrenadesCompat.isLoaded()` = `ModList.get().isLoaded("csgrenades")`, cached
- Only `CsGrenadesIntegration`, `CsGrenadesEventHandler`, `CsGrenadesTracker`, `FlashbangEffect`, `FleeFromFireGoal`, `SmokeLosSuppression` reference CS classes — these are never class-loaded unless the mod is present
- `SoundAttractMod.handleCsgrenadesIntegration()` is the single entry point, mirrors existing `handleTaczIntegration` / `handlePointBlankIntegration`
- Hooks in `FovEvents` / `StealthDetectionEvents` call into **static method signatures that take only vanilla types**; the implementations short-circuit on `!isLoaded()` before touching any CS class

### Concurrency / NPE / memory

| Risk | Mitigation |
|---|---|
| `CSGrenadeServerAPI.entity.grenades` mutated during iteration (Kotlin non-thread-safe map) | Defensive `new ArrayList<>(api.grenades.values())` snapshot inside try/catch; server thread only |
| `FlashbangEffect.blindUntilTick` unbounded growth | Cap = `flashbangMobScanBudget * 16`; evict expired-first, then oldest; periodic 200-tick sweep |
| Mob unloads while blind | `EntityLeaveLevelEvent` invalidates `blindUntilTick` entry (same pattern as `ScentQueryCache` cleanup committed earlier today) |
| Tracker holds references to dead/wrong-level entities | `isAlive()` + `level() == current` filter every rebuild; `volatile` swap of list reference |
| Server stop leaks state | `ServerStoppedEvent` calls `FlashbangEffect.clear()` + `CsGrenadesTracker.clear()` |
| Event handler throws | Each `@SubscribeEvent` body wrapped in `try { ... } catch (Throwable t) { LOGGER.warn(...) }` |
| `grenade.level()` null | Instance-of `ServerLevel` check before use |

### Performance

| Path | Cost | Frequency |
|---|---|---|
| Flashbang activation scan | O(mobs in AABB) ≤ 64 | Rare (player action) |
| Smoke / fire tracker rebuild | O(active grenades), typically < 10 | Every 5 ticks |
| Smoke LOS hook | O(active smoke), 0–3 typical | Per fresh LOS query |
| Fire-flee `canUse` | O(active fire) per eligible mob | Natural goal cadence, cooldown-gated |
| Whitelist-seeded sounds | Zero added cost (existing pipeline) | — |

### Backward compatibility

- Master flag + sub-flags default `true`; flip one boolean to disable
- Schema bump is append-only, de-dup, preserves existing user values
- No changes to existing config keys, event handlers, or save data
- Seeded sound IDs are inert if `csgrenades` is absent

## 7. Testing

### Unit-testable (pure logic)

- `SmokeLosSuppression.blocksRay`: ray through sphere, tangent, outside, start-inside, end-inside, zero-length ray, degenerate sphere
- `FlashbangEffect.linearDuration(dist, radius, base)`: `dist=0` → full, `dist=radius` → ≥1, `dist>radius` → caller skips

### Manual / in-game verification

| Scenario | Expected |
|---|---|
| HE into zombie horde | Zombies converge (whitelist sound, weight 20) |
| Flashbang 8 blocks in front of zombie facing it | Zombie suppression ~50 ticks |
| Flashbang with zombie facing away | No suppression (FOV fail) |
| Flashbang with zombie behind wall | No suppression (LOS fail) |
| Smoke between zombie and player | Zombie cannot target player; clears when smoke dies |
| Smoke not on line | Targeting proceeds normally |
| Fire-immune Blaze near incendiary | Ignores it |
| Eligible zombie within 6b of incendiary | Flees ~16 blocks; re-evaluates after cooldown |
| `enableCsgrenadesIntegration=false` | All inert, no log spam |
| `csgrenades` absent | Server starts cleanly, no classloader errors |

### Regression

- Migration is idempotent (run server twice → no duplicate entries)
- Existing PointBlank / Tacz / EnhancedAI integrations unaffected when CS integration disabled

### Verification commands (for user, since Cascade cannot run MC)

```powershell
./gradlew classes --no-daemon
./gradlew build --no-daemon -x test
```

In-game:
```
/summon zombie ~ ~ ~5
/give @p csgrenades:hegrenade 4
/give @p csgrenades:flashbang 4
/give @p csgrenades:smokegrenade 4
/give @p csgrenades:incendiary 4
```

## 8. Open Questions / Deferred

- **Decoy integration**: deferred until upstream ships gunshot-mimic sound event
- **Fire patch granularity**: currently uses grenade entity position. If upstream implements fire spread (currently commented-out in `FireGrenadeEntity.kt`), the tracker can be extended to scan individual fire blocks in a later iteration
- **Particle/visual feedback** for blinded mobs: not in v1 (server-side-only behavior change)

## 9. References

- Existing pattern files studied: `integration/pointblank/PointBlankIntegration.java`, `ai/FleeFromUnseenAttackerGoal.java`, `event/FovEvents.java`, `event/StealthDetectionEvents.java`, `event/ScentEvents.java`, `util/ScentQueryCache.java`
- Upstream API: `CSGrenadeServerAPI.entity.grenades`, `club.pisquad.minecraft.csgrenades.event.GrenadeActivateEvent`, `club.pisquad.minecraft.csgrenades.GrenadeType`
