# TACZ / PointBlank / VoiceChat Attraction Regression — Investigation Plan

> **STATUS 2026-04-23 22:20 — ROOT CAUSE CONFIRMED from runtime log.** See "Phase 2 outcome" below.

> **For agentic workers:** Use `superpowers:systematic-debugging`. Evidence before claims.

**Scope:** Identify why mobs stopped reacting to TACZ gunshots (and by extension Point Blank + Voice Chat) after the Part 5–6 refactor, while vanilla-movement attraction (walking/running) still works. Also investigate secondary symptom: mobs gathering at a block with no audible source.

## Phase 2 outcome (the log speaks)

Log file: `C:/Users/haihb/curseforge/minecraft/Instances/Sound 1.20.1 (1)/logs/latest.log` (session 22:16–22:19).

Findings:

- **Zero `[TaczIntegration]` lines in the entire log.** `onGunShoot` never fires. `onGunReload` never fires.
- **Zero `[SoundTracker] Successfully added sound: tacz:gun...` lines.** Confirms no direct-addSound path ever runs for TACZ.
- User *did* fire the gun: log at 22:19:22 shows four `[SoundMessage] No default entry for tacz:gun, dropping` entries. These come from the **client-side raw sound forwarder** (`SoundAttractClientEvents`) hearing the TACZ gunshot sound event and sending it as a generic `SoundMessage`; the server correctly rejects because `tacz:gun` has no default entry, and the server-side integration that would normally add it is not listening.
- **Symptom C identified:** mobs pile at `BlockPos{x=-10, y=69, z=78}` because of sound `soundattract:virtual#unknown/arrow_investigation/-2748778749883` with range≈5.6 and weight≈13. That is the `ArrowInvestigationEvents` dispatch with a **failed origin resolution** (shooter id = `unknown`). The sound persists and is repeatedly selected by `findNearest`. Independent of the TACZ regression.

## Confirmed root cause (H2 variant)

Integration event-bus registration is gated on a cache value that is computed too early:

`@c:/Users/haihb/Desktop/modding/attract_to_sound/1.20.1-4.1.4/src/main/java/com/example/soundattract/config/SoundAttractConfig.java:1038`:

```java
TACZ_ENABLED_CACHE = ModList.get().isLoaded("tacz") && serverReady() && SERVER.enableTaczIntegration.get();
```

And the identical pattern at line 1126 for `POINT_BLANK_ENABLED_CACHE`.

`bakeConfig()` runs at **COMMON config load** (during mod loading / `FMLCommonSetupEvent`). At that moment **`serverReady()` returns false** because the `SERVER` spec is only loaded on `ServerAboutToStartEvent`. Therefore `TACZ_ENABLED_CACHE = false` always.

`@c:/Users/haihb/Desktop/modding/attract_to_sound/1.20.1-4.1.4/src/main/java/com/example/soundattract/SoundAttractMod.java:133-143` then does:

```java
event.enqueueWork(this::handleTaczIntegration);   // runs at FMLCommonSetupEvent
...
private void handleTaczIntegration() {
    if (ModList.get().isLoaded("tacz") && SoundAttractConfig.TACZ_ENABLED_CACHE) {
        TaczIntegrationHandler.register();           // NEVER REACHED
    }
}
```

→ `TaczIntegrationHandler.register()` is **never called**, so `MinecraftForge.EVENT_BUS.register(TaczIntegration.class)` never happens, so `@SubscribeEvent onGunShoot` never receives events.

Same regression affects `POINT_BLANK_ENABLED_CACHE` and, by the same pattern, voice-chat bootstrap if it reads any SERVER-only key at FMLCommonSetupEvent.

This regression was introduced in **Task 1 (SERVER keys)** when `enableTaczIntegration` / `enablePointBlankIntegration` / `enableVoiceChat*` were migrated from COMMON → SERVER. The `serverReady() &&` guard was added to be safe, but COMMON-era consumers like `handleTaczIntegration` still run at FMLCommonSetupEvent and see the pre-serverReady value.

**Ground rule:** Every hypothesis below was to be disproved or confirmed by log or config-cache dump. That has been done — H2 confirmed, H1/H3/H4 disproved or moot.

---

## Known facts (from code inspection)

- `@c:/Users/haihb/Desktop/modding/attract_to_sound/1.20.1-4.1.4/src/main/java/com/example/soundattract/ai/AttractionGoal.java:115` — `findInterestingSoundRecord()` reads from `SoundTracker` without filtering by sound-ID. So the mob goal does NOT gate on sound ID; any record in the tracker is eligible.
- `@c:/Users/haihb/Desktop/modding/attract_to_sound/1.20.1-4.1.4/src/main/java/com/example/soundattract/tracking/SoundTracker.java:287-296` — `SoundTracker.addSound` applies `SOUND_ID_WHITELIST_CACHE` using `extractBaseSoundLocation(soundIdToUse)` which strips the `#...` metadata suffix. No bypass for integration sound IDs.
- `@c:/Users/haihb/Desktop/modding/attract_to_sound/1.20.1-4.1.4/src/main/java/com/example/soundattract/tracking/SoundTracker.java:79-101` — `buildIntegrationSoundId("tacz:gun", "<uuid>/shoot")` returns `"tacz:gun#<uuid>/shoot"`. The base `tacz:gun` is recovered via `extractBaseSoundLocation`.
- **Pre-Part-6 path** (for reference, now gone): `SoundMessage.handle` had an *earlier* whitelist gate that bypassed when `taczType` / `pointBlankType` / `VOICE_CHAT_SOUND_ID` was present. Direct callers via Part 6 no longer benefit from that early bypass — they are filtered only by the tracker-level whitelist on the base ID.
- **Working path (symptom B):** `AttractionClientEvents` → client packet → `SoundMessage.handle` action branch → resolves range/weight → calls `SoundTracker.addSound` with explicit id `"soundattract:player_action.walking"` (and friends).

---

## Invariant / differential

What (B) does that (A)/(D)/(E) don't:

- (B) registers base IDs like `soundattract:player_action.walking` (no `#` metadata).
- (A)/(D)/(E) register base IDs `tacz:gun`, `pointblank:gun_action`, `soundattract:voice_chat`, possibly with `#metadata`.

Any filter or config path that treats these base IDs differently is the prime suspect.

---

## Ranked hypotheses

### H1 — ~~`SOUND_ID_WHITELIST_CACHE` excludes integrations~~ **disproved**

Log confirms `685 whitelist` entries and irrelevant sounds like `minecraft:entity.zombie.ambient` are dropped by whitelist, proving the filter works. But the TACZ failure happens *before* whitelist is even consulted — the server never receives a proper integration call, so whitelist is not the cause. Leaving the writeup below as original-reasoning record.

Pre-Part-6 path had a *second* bypass at the `SoundMessage.handle` layer for `isIntegration`, `VOICE_CHAT_SOUND_ID`, `POINT_BLANK_SOUND_ID` that effectively fed those IDs into `SoundTracker.addSound` anyway. But `addSound` has its own whitelist with no integration bypass. **Before Part 6 it worked** probably because the user's whitelist was empty, or because the server-local caller path (now changed) had its own bypass. Post-Part 6, if *anything* has populated `SOUND_ID_WHITELIST_CACHE` with only a subset (e.g., via datapack `DP_SOUND_WHITELIST_CACHE` merging, or config migration side effect), integrations break while player action still passes because the user's player_action IDs are in that subset.

Confirmation log line:
```
[SoundTracker] Sound tacz:gun#<uuid>/shoot not in whitelist, ignoring.
```
(Note: same file line 292 suppresses this log for `pointblank:` prefixed entries. For PB, infer from *absence* of a `[SoundTracker] Successfully added sound: pointblank:...` line.)

### H2 — event-bus registration gated on pre-serverReady cache ★ **CONFIRMED**

See "Confirmed root cause" above. Evidence: absence of `[TaczIntegration] Gunshot Flash: ...` during 22:19:22 shots. Triggered by Task 1's COMMON→SERVER migration combined with the `serverReady() &&` guard on line 1038 / 1126 of `SoundAttractConfig.bakeConfig()`.

### H3 — ~~`TACZ_SHOOT_RANGE_CACHE` zeroed~~ **moot**

Irrelevant because `onGunShoot` never runs; range never read. Skip.

### H4 — ~~AttractionGoal scan misses~~ **disproved**

Log shows `[AttractionGoal] Mob Zombie found sound: pos=BlockPos{x=-10, y=69, z=78}, range=16.0, weight=10.0` repeatedly — consumer loop is healthy. Mobs DO react to sounds when sounds actually enter the tracker. Confirms the regression is upstream at the producer (integration registration).

### H5 — `serverReady()` gate drops voice-chat packets

`@c:/.../SoundMessage.java:125` has `if (!SoundAttractConfig.serverReady()) return;`. If voice chat packets arrive before SERVER config loads, they're silently dropped. Secondary — if Plasmo bootstrap is also gated on a pre-serverReady cache like `VOICE_CHAT_ENABLED_CACHE` computed in bakeConfig, H2 applies to voice chat too. Check for `VOICE_CHAT_ENABLED_CACHE` usage in `PlasmoVoiceBootstrap`.

### Symptom C — mobs gathering at a block, no audible source ★ **CONFIRMED = arrow investigation**

Log `[findNearest] final pick soundattract:virtual#unknown/arrow_investigation/-2748778749883 at BlockPos{x=-10, y=69, z=78} with range=5.62 weight=13.0` (22:17:23) and dozens of subsequent `found sound: pos=BlockPos{x=-10, y=69, z=78}` entries prove:

- `ArrowInvestigationEvents.dispatchInvestigation` registered a virtual sound at that block.
- Shooter UUID resolved to `unknown`, meaning `resolveOrigin()` could not map back to a player (likely a dispenser-fired, stray, or mob-fired arrow, or a pre-existing arrow that was already in flight when we began tracking).
- Weight (13) + long lifetime cause mobs to keep re-selecting it even after many ticks.

This is a **separate bug** from the TACZ regression. Track as its own issue — do not couple the fix with Task 14.

Mini-plan for Symptom C (defer to separate ticket):
- Gate `dispatchInvestigation` on origin resolved (`shooter != null`); skip when shooter is unknown (or use a much lower weight).
- Or: reduce weight of `arrow_investigation` sounds to << normal sounds so they don't monopolize `findNearest`.
- Or: shorten `lifetimeTicks` for investigation virtual sounds.

---

## Fix plan (Task 14 follow-up, *minimal* upstream repair)

### Step F1 — Repair integration registration timing

Two equally valid options:

**Option A (preferred, least diff):** Drop the `serverReady() && ` guard from `TACZ_ENABLED_CACHE` / `POINT_BLANK_ENABLED_CACHE` / `VOICE_CHAT_ENABLED_CACHE` computation in `bakeConfig()`, and instead read `SERVER.enableXxx.get()` lazily at event-handler entry. Concretely:

1. Change `@c:/.../SoundAttractConfig.java:1038` from
   ```java
   TACZ_ENABLED_CACHE = ModList.get().isLoaded("tacz") && serverReady() && SERVER.enableTaczIntegration.get();
   ```
   to
   ```java
   TACZ_ENABLED_CACHE = ModList.get().isLoaded("tacz") && (!serverReady() || SERVER.enableTaczIntegration.get());
   ```
   i.e. assume enabled when SERVER isn't loaded yet; re-evaluate on subsequent reloads.
2. Same transform for `POINT_BLANK_ENABLED_CACHE` at line 1126 and any VoiceChat equivalent.
3. Add early-return guards inside each `@SubscribeEvent` handler:
   ```java
   if (!SoundAttractConfig.serverReady()) return;
   if (!SoundAttractConfig.SERVER.enableTaczIntegration.get()) return;
   ```

**Option B (cleaner semantics, more diff):** Defer `handleTaczIntegration` and the PB / voice-chat equivalents from `FMLCommonSetupEvent` to `ServerAboutToStartEvent` (we already subscribe to that event for migration). At that point `serverReady()` is true and config is definitive. Downside: any client-side behavior of TACZ integration (there is none today) would be deferred too.

Recommend **Option A**. Smallest footprint; keeps the existing event-bus-at-common-setup discipline intact; handler-level guard gives a live enable/disable response without server restart.

### Step F2 — Verify via log harvest

After implementing F1, rerun the same test. Expected new log lines:

- `Tacz mod found and integration is enabled. Registering event listeners.` (already present — confirm still there)
- `[TaczIntegration] Gunshot Flash: BaseRange=…, FinalRange=…` on each shot
- `[TaczIntegration] Shot: range=…, weight=…`
- `[SoundTracker] Successfully added sound: tacz:gun#<uuid>/shoot at BlockPos{…} (range=…)`
- `[AttractionGoal] Mob Zombie found sound: pos=BlockPos{<player pos>}, range=…, weight=…`

Absence of `[SoundMessage] No default entry for tacz:gun, dropping` during shots is also expected — once server-side integration registers, server-side addSound beats the client echo.

### Step F3 — Symptom C (separate ticket, don't bundle)

Defer. File as `arrow-investigation-unknown-shooter-phantom-sound.md`.

---

## Investigation steps (run in order)

> Phase 1–5 kept below as reference; Phase 2 is **already complete** — see top.

### Phase 1 — Observability baseline (≤ 5 min)

- [ ] **Step 1.1:** Ensure `soundattract-common.toml` has `debugLogging = true`. If not, set it and reload with `/reload` or restart the dev server.
- [ ] **Step 1.2:** Start `./gradlew runServer` + `./gradlew runClient`. Join single-player or dedicated server.
- [ ] **Step 1.3:** Spawn a zombie ~5 blocks away. Confirm it reacts when you walk (sanity check for symptom B working path).

### Phase 2 — TACZ isolated test (≤ 5 min)

- [ ] **Step 2.1:** Clear server log file. Stand still. Shoot the TACZ gun **once**.
- [ ] **Step 2.2:** Grep the log:
  ```
  grep "TaczIntegration\|SoundTracker\|SoundMessage\|AttractionGoal" logs/latest.log
  ```
- [ ] **Step 2.3:** Classify the outcome:

| Log pattern seen | Diagnosis | Next phase |
|---|---|---|
| No `[TaczIntegration]` line at all | **H2** — event not firing | Phase 3A |
| `[TaczIntegration] Shot: range=0.0` | **H3** — config cache empty/bad | Phase 3B |
| `[SoundTracker] Sound tacz:gun#... not in whitelist, ignoring.` | **H1** — whitelist drop | Phase 3C |
| `[SoundTracker] Successfully added sound: tacz:gun#...` but no `[AttractionGoal] Mob ... found sound: pos=<that pos>` for any mob within ~16 blocks | **H4** — consumer can't see it | Phase 3D |
| Both add + AttractionGoal find logs present, but mob still doesn't path | **H4b** — goal suppressed by target / stealth / other gate | Phase 3E |

### Phase 3A — H2 verification (event bus)

- [ ] Grep the codebase for TACZ registration site:
  ```
  rg -n "TaczIntegration|addListener.*Tacz|TACZ.*register" src/main/java --type java
  ```
- [ ] Read the registration callsite. Check whether subscription is gated on a SERVER config key that might read `false` at startup time (common mistake: reading config in a static initializer before SERVER spec is loaded).
- [ ] Temporarily hard-code registration unconditionally, restart, shoot, confirm `[TaczIntegration] Gunshot Flash` appears. That proves the gate is wrong.

### Phase 3B — H3 verification (config cache)

- [ ] Add a debug dump at server start:
  ```java
  SoundAttractMod.LOGGER.info("[DEBUG] TACZ_SHOOT_RANGE_CACHE={} TACZ_SHOOT_WEIGHT_CACHE={} POINT_BLANK_SHOOT_RANGE_CACHE={}",
      SoundAttractConfig.TACZ_SHOOT_RANGE_CACHE,
      SoundAttractConfig.TACZ_SHOOT_WEIGHT_CACHE,
      SoundAttractConfig.POINT_BLANK_SHOOT_RANGE_CACHE);
  ```
  inside `SoundAttractConfig.bakeConfig()` last line (or server-start event).
- [ ] Expected healthy: `140.0 / 15.0 / 140.0`. If zero → trace which migration/bake path zeroes them.

### Phase 3C — H1 verification (whitelist)

- [ ] Add a debug dump at server start:
  ```java
  SoundAttractMod.LOGGER.info("[DEBUG] SOUND_ID_WHITELIST_CACHE size={} contents={}",
      SoundAttractConfig.SOUND_ID_WHITELIST_CACHE.size(),
      SoundAttractConfig.SOUND_ID_WHITELIST_CACHE);
  SoundAttractMod.LOGGER.info("[DEBUG] DP_SOUND_WHITELIST_CACHE size={} contents={}",
      SoundAttractConfig.DP_SOUND_WHITELIST_CACHE.size(),
      SoundAttractConfig.DP_SOUND_WHITELIST_CACHE);
  ```
- [ ] If non-empty and missing `tacz:gun` / `pointblank:gun_action` / `soundattract:voice_chat`, **H1 confirmed**.
- [ ] **Fix direction** (Phase 4): restore the integration bypass at the `SoundTracker.addSound` layer, parallel to what `SoundMessage.handle` used to have:
  ```java
  // in SoundTracker.addSound, before the whitelist check
  boolean isIntegrationId = soundIdToUse != null && (
          soundIdToUse.startsWith("tacz:gun")
       || soundIdToUse.startsWith("pointblank:")
       || soundIdToUse.startsWith("soundattract:voice_chat"));
  if (!isIntegrationId
          && !SoundAttractConfig.SOUND_ID_WHITELIST_CACHE.isEmpty()
          && (loc == null || !SoundAttractConfig.SOUND_ID_WHITELIST_CACHE.contains(loc))) {
      // drop
  }
  ```
  Only apply after Phase 2+3C confirms H1.

### Phase 3D — H4 verification (consumer scan)

- [ ] Read the logged `(range=<R>, pos=<P>)` from addSound. Check:
  - If `R > 64`: should go into `LARGE_RANGE_SOUNDS` and be found regardless of distance. Grep for `[AttractionGoal] Mob ... found sound: ...range=<R>...`.
  - If `R ≤ 64`: only visible within ±16 blocks 3D grid cell neighbours of the mob. Place mob within 8 blocks of shoot position and retry.
- [ ] Cross-check `SoundAttractConfig.COMMON.maxSoundsTracked.get()` — if capacity is exceeded the new record may be rejected (`addSound` line 336-341).

### Phase 3E — H4b (goal gates)

- [ ] Read `@c:/.../AttractionGoal.java:85-146 canUse()`. Check which of these guards fail:
  - `mob.isVehicle()` / `mob.isSleeping()`
  - `shouldSuppressTargeting()` via `StealthDetectionEvents`
  - `skipSoundScanWhenHasTarget` + mob has target
  - `raidLeaderOnlySoundScan`
  - `isMobEligible()` — entity type in attracted set, has profile, or is CustomNpcs mob
  - `scanCooldownCounter > 0` — throttled
- [ ] Add temporary logging inside each guard to see which one returns early. Revert logging after.

### Phase 4 — Mirror PB and VoiceChat

Run Phase 2 for Point Blank and for Voice Chat (speak at normal volume). Expected: same root cause as TACZ. If different, split into separate hypotheses.

**Voice chat specifics:** if the client packet path is used (not Plasmo), check for the Part-6-new `if (!SoundAttractConfig.serverReady()) return;` dropping early packets. Trigger voice chat *after* the "Done" server-start log line.

### Phase 5 — Symptom C (phantom gathering)

- [ ] With debug logging still on, approach the block the mobs are staring at. Wait 30 s.
- [ ] Grep log for `addSound.*<block-coords>`. Identify the soundId and its source callsite.
- [ ] Cross-check `SoundAttractConfig.COMMON.soundLifetimeTicks.get()` (default typically 200 = 10 s; if misconfigured to a huge value, all sounds persist).

---

## Fix policy

- **No fixes until Phase 2 produces a log-line-based diagnosis.**
- When fixing: one minimal change per confirmed hypothesis. Add a regression log line so the same symptom produces a distinct signature next time.
- If H1 confirmed: prefer fixing at `SoundTracker.addSound` level (central) rather than patching each caller.

## Out of scope

- Redesigning the whitelist model.
- Changing integration sound-ID formats.
- Pre-Part-6 git bisect (we know the regression window; a bisect would only reconfirm).

---

## Done criteria

- Mobs react to TACZ gunshots from within the configured detection range.
- Same for Point Blank and Voice Chat.
- Phantom-sound gathering (symptom C) explained or reclassified as a separate pre-existing issue with its own ticket.
- Debug logging turned back off.
