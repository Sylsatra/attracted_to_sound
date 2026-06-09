# CS Grenades Integration Debug Plan

## Problem Statement
Flashbang blinding and smoke LOS blocking are not working. Mobs continue targeting despite being flashbanged or smoke being present.

## Hypotheses

### H1: Event Not Firing or Handler Failing
The `GrenadeActivateEvent` may not be reaching our handler, or reflection-based entity extraction fails.

### H2: Wrong Method/Property Access
Kotlin data class properties might require `getEntity()` not `component1()`, or enum name matching fails.

### H3: Tracker Not Finding Grenades
`CsGrenadesTracker.scanAndRebuild` might fail to find smoke/fire grenades via reflection.

### H4: Stealth System Overrides
Stealth detection scan interval or grace period might override/supersede our blind/smoke checks.

## Investigation Steps

### Phase 1: Verify Event Reception (Flashbang)

**1.1 Confirm Event Registration**
Location: `CsGrenadesEventHandler.registerGrenadeEvent()`
Add log after successful registration:
```java
SoundAttractMod.LOGGER.info("[CSGrenades] Successfully registered GrenadeActivateEvent listener");
```

**1.2 Confirm Event Fires**
Location: `CsGrenadesEventHandler.onGrenadeActivate()` (first line)
```java
SoundAttractMod.LOGGER.info("[CSGrenades] GrenadeActivateEvent received");
```

**1.3 Verify Entity Extraction**
```java
Method getEntityMethod = event.getClass().getMethod("component1");
Entity grenade = (Entity) getEntityMethod.invoke(event);
SoundAttractMod.LOGGER.info("[CSGrenades] Extracted entity: {} (class: {})", grenade, grenade != null ? grenade.getClass().getName() : "null");
```

**1.4 Verify Enum Extraction and Name**
```java
Object grenadeType = getGrenadeTypeMethod.invoke(grenade);
String typeName = grenadeType.toString();
SoundAttractMod.LOGGER.info("[CSGrenades] Grenade type: {} (class: {})", typeName, grenadeType.getClass().getName());
```

**Expected:** Should see `typeName` = "FLASH_BANG" or "FLASHBANG"

### Phase 2: Verify Blind State Application

**2.1 Log When Mobs Are Blinded**
Location: `FlashbangEffect.blind()`
```java
SoundAttractMod.LOGGER.info("[CSGrenades] Blinded mob {} for {} ticks (expire at {})", mob.getUUID(), durationTicks, currentTick + durationTicks);
```

**2.2 Log Blind State Queries**
Location: `FlashbangEffect.isMobBlind()`
```java
boolean blind = entry != null && currentTick <= entry.expireTick;
if (blind) {
    SoundAttractMod.LOGGER.debug("[CSGrenades] Mob {} is blind (expires at {})", mob.getUUID(), entry.expireTick);
}
return blind;
```

**2.3 Log Stealth Suppression Check**
Location: `StealthDetectionEvents.shouldSuppressTargeting()`
```java
if (CsGrenadesCompat.isLoaded() && ...FlashbangEffect.isMobBlind(...)) {
    SoundAttractMod.LOGGER.info("[CSGrenades] Suppressing targeting for blind mob {}", mob.getUUID());
    return true;
}
```

### Phase 3: Verify Smoke Tracking

**3.1 Log Tracker Initialization**
Location: `CsGrenadesTracker.ensureReflection()`
```java
SoundAttractMod.LOGGER.info("[CSGrenades] Reflection initialized. ServerAPI: {}, GrenadesMethod: {}", 
    SERVER_API_INSTANCE != null, GET_GRENADES_METHOD != null);
```

**3.2 Log Scan Results**
Location: `CsGrenadesTracker.scanAndRebuild()`
```java
Map<UUID, ?> grenades = (Map<UUID, ?>) GET_GRENADES_METHOD.invoke(entityApi);
SoundAttractMod.LOGGER.info("[CSGrenades] Found {} grenades", grenades != null ? grenades.size() : 0);
```

**3.3 Log Per-Grenade Type**
```java
for (Object grenade : grenades.values()) {
    Object grenadeType = GET_GRENADE_TYPE_METHOD.invoke(grenade);
    String typeName = grenadeType != null ? grenadeType.toString() : "null";
    SoundAttractMod.LOGGER.info("[CSGrenades] Grenade type: {}", typeName);
}
```

**3.4 Log Active Smoke/Fire Counts**
```java
SMOKE_SNAPSHOT = new CopyOnWriteArrayList<>(ACTIVE_SMOKE.values());
FIRE_SNAPSHOT = new CopyOnWriteArrayList<>(ACTIVE_FIRE.values());
SoundAttractMod.LOGGER.info("[CSGrenades] Active smoke: {}, fire: {}", SMOKE_SNAPSHOT.size(), FIRE_SNAPSHOT.size());
```

**3.5 Log Smoke Block Checks**
Location: `CsGrenadesTracker.smokeBlocksRay()`
```java
if (SmokeLosSuppression.blocksRay(from, to, smoke.center, smokeRadius)) {
    SoundAttractMod.LOGGER.info("[CSGrenades] Smoke blocked ray from {} to {}", from, to);
    return true;
}
```

**3.6 Log FovEvents Smoke Check**
Location: `FovEvents.hasSmartLineOfSight()`
```java
if (CsGrenadesTracker.smokeBlocksRay(eye, targetCenter, maxAge, currentTick)) {
    SoundAttractMod.LOGGER.info("[CSGrenades] Smoke blocking LOS for mob {}", looker.getUUID());
    return false;
}
```

### Phase 4: Verify Stealth Integration

**4.1 Identify Callers of shouldSuppressTargeting**
Search codebase for all call sites of `shouldSuppressTargeting()` to understand when/where it's invoked.

**4.2 Check Stealth Scan Configuration**
Verify `SoundAttractConfig.COMMON.enableStealthMechanics.get()` is true.

**4.3 Check Targeting Grace Period**
Search for "gracePeriod" or similar timing fields that might keep mobs targeting after LOS loss.

### Phase 5: Fix Identified Issues

**5.1 Fix Property Access (if needed)**
If `component1()` fails, try:
```java
Method getEntityMethod = event.getClass().getMethod("getEntity");
```

**5.2 Fix Enum Name Matching**
If enum names differ, update switch/case statements to match actual Kotlin enum names.

**5.3 Fix Reflection Caching**
Ensure methods are properly cached and reused.

**5.4 Adjust Check Ordering (if needed)**
If stealth system overrides our checks, consider moving blind/smoke checks to different lifecycle point.

## Expected Outcomes

After adding logging, we should see:
- `[CSGrenades] GrenadeActivateEvent received` when flashbang explodes
- `[CSGrenades] Blinded mob` when flashbang affects mobs
- `[CSGrenades] Suppressing targeting for blind mob` when stealth queries blind state
- `[CSGrenades] Found X grenades` during tracker scan
- `[CSGrenades] Active smoke: X` showing tracked smoke clouds
- `[CSGrenades] Smoke blocking LOS` when smoke blocks targeting

If any of these logs are missing, that identifies the failure point.
