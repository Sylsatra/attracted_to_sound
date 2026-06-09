# SoundMessage Wire-Protocol Trust — Design Spec

**Status:** Approved (2026-04-23)
**Mod version target:** 6.3.4 (follows 6.3.3 server-authoritative migration)
**Scope:** Eliminate client trust for `range` and `weight` in `SoundMessage` without removing any current functionality.

---

## 1. Problem

Today `SoundMessage` is both a wire DTO (client → server) and a local helper used by three server-side callers (TACZ, Point Blank, vanilla footsteps). The wire format includes `range: int` and `weight: double`. These two fields are fully trusted by the server, so a modified client can:

- Send `range = 0` / `weight = 0` to suppress their own sound signature.
- Send `range = Integer.MAX_VALUE` / huge weight to flood attraction on unrelated sounds.
- Send arbitrary values for voice chat or custom sounds with no bound whatsoever.

The server-side callers (TACZ, PB, vanilla) are not the attack surface — they construct `SoundMessage` on the server and call `SoundMessage.handle(msg, () -> null)` as a local loopback. Only three client-originated sites travel the wire:

| Client emit site | Data under client control |
|------------------|----------------------------|
| `AttractionClientEvents.onPlaySoundEvent` | Action (stance), soundId, pos, range, weight |
| `SoundAttractClientEvents.onPlaySoundEvent` | Any soundId detected client-side, pos, range, weight (hardcoded per action for footsteps, `-1` otherwise) |
| `VoiceChatIntegrationClient.handleClientSound` | Whispering flag, peak dB → range, weight |

## 2. Goals / Non-goals

**Goals**
- Server authoritative resolution of `range` and `weight` for all wire-received sounds.
- Preserve every currently supported behavior: footstep attraction, step-sound forwarding, voice-chat detection, gun/vanilla sound detection.
- Keep latency and allocation profile unchanged.

**Non-goals**
- No change to `SoundTracker`, `AttractionGoal`, `LeaderAttractionGoal`, `FollowerEdgeRelayGoal`, stealth, or scent logic.
- No change to the SOUND_ID whitelist semantics or default-entries map.
- No new sound IDs or new integrations.
- No server→client sound sync changes.

## 3. Design

### 3.1 New wire format

`SoundMessage` keeps its identity as the network DTO, but its encoded/decoded fields change:

| Field | Removed | Kept | Added |
|-------|---------|------|-------|
| `soundId: ResourceLocation` |  | ✅ |  |
| `x`, `y`, `z: double` |  | ✅ |  |
| `dimension: ResourceLocation` |  | ✅ |  |
| `sourcePlayerUUID: Optional<UUID>` |  | ✅ |  |
| `range: int` | ✅ |  |  |
| `weight: double` | ✅ |  |  |
| `animatorClass: String?` |  | ✅ |  |
| `taczType: String?` |  | ✅ |  |
| `pointBlankType: String?` |  | ✅ |  |
| `action: String?` |  |  | ✅ |
| `intensity01: float` |  |  | ✅ |
| `whispering: boolean` |  |  | ✅ |

Decoder validation:
- `action`: if non-null, must match one of the known player action names: `CRAWLING`, `SNEAKING`, `WALKING`, `SPRINTING`, `SPRINT_JUMPING`. Max length 32 chars. Unknown values are reset to `null`.
- `intensity01`: finite float clamped to `[0.0, 1.0]`. `NaN` is replaced with `0.0`.
- `whispering`: plain boolean.

### 3.2 Server-side resolution (new `SoundMessage.handle`)

```
preconditions (unchanged):
  - packet-handled / enqueueWork wiring
  - dimension lookup, dimension mismatch guard

resolution order (per packet):
  1. Whitelist check (existing). Drop if soundId not whitelisted
     and not VOICE_CHAT_SOUND_ID / POINT_BLANK_SOUND_ID / integration-marked.

  2. If msg.action != null and matches a known player action:
       range  = SERVER.playerActionRanges[action]
       weight = SERVER.playerActionWeights[action]

  3. else if msg.soundId == VOICE_CHAT_SOUND_ID:
       baseRange = msg.whispering
                     ? SERVER.voiceChatWhisperRange
                     : SERVER.voiceChatNormalRange
       factor    = thresholdLookup(
                     SERVER.voiceChatDbThresholdMap,
                     intensity01ToNormDb(msg.intensity01))
       range  = max(0, round(baseRange * factor))
       weight = SERVER.voiceChatWeight
       if range <= 0 -> drop

  4. else (arbitrary whitelisted soundId):
       def = SOUND_DEFAULT_ENTRIES_CACHE.get(msg.soundId)
       if def == null -> drop silently
       range  = def.range()
       weight = def.weight()

  5. Safety cap (new):
       range = min(range, SERVER.maxClientSoundRange)

  6. SoundTracker.addSound(...) -- behavior unchanged from here on.
```

`intensity01ToNormDb`: linear map `intensity01 * 127.0` reproduces the existing client normalization (`normDb = db - (-127.0)`, so `normDb ∈ [0, 127]`). The threshold map is walked exactly as today, just on a server-derived value.

### 3.3 Client changes

`AttractionClientEvents.onPlaySoundEvent`
- Drop reads of `PLAYER_ACTION_RANGES_CACHE` / `PLAYER_ACTION_WEIGHTS_CACHE`.
- Construct `SoundMessage` with `action = currentAction.name()`, no range/weight, `intensity01 = NaN`, `whispering = false`.

`SoundAttractClientEvents.onPlaySoundEvent`
- Remove the hardcoded per-action `switch` (range 2/3/8/12/16).
- When a step is classified, set `action = classifiedAction.name()`.
- When a non-step is forwarded, `action = null` (server will use `SOUND_DEFAULT_ENTRIES_CACHE`).
- No range/weight carried on the wire.

`VoiceChatIntegrationClient.handleClientSound`
- Keep peak-dB computation locally.
- Compute `intensity01 = clamp((peakDb - (-127.0)) / 127.0, 0, 1)` — same domain as today, normalized.
- Send `whispering = event.isWhispering()`.
- Drop the client-side threshold lookup and range multiplication; server does it.
- If `peakDb == -127.0` (silence), skip entirely (same behavior as today's `effectiveRange <= 0` early return).

### 3.4 Server-side caller refactor (TACZ / Point Blank / Vanilla)

These no longer use `SoundMessage.handle` as a loopback. Each calls `SoundTracker.addSound` directly with server-trusted range/weight. The `SoundMessage` DTO stops being built for server-local emits.

`TaczIntegration.onGunShoot` / `onGunReload`
- Build the integration soundId (`SoundTracker.buildIntegrationSoundId(TACZ_SOUND_ID, meta)`).
- Call `SoundTracker.addSound(null, pos, dim, range, weight, lifetime, soundId)` directly.

`PointBlankIntegration.onGunShoot` / `onGunReload`
- Same pattern with `POINT_BLANK_SOUND_ID`.

`VanillaIntegrationEvents.sendVanillaSound`
- Already calls `SoundTracker.addSound` on the server branch. The dead `sendToServer` else-branch and the unused `SoundMessage` construction in the server path are deleted.

### 3.5 Config changes (SERVER spec)

New `[safety]` section:

| Key | Type | Default | Range | Purpose |
|-----|------|---------|-------|---------|
| `maxClientSoundRange` | double | 256.0 | [1.0, 256.0] | Caps final resolved range for any wire-received sound. |

Migrated from COMMON to SERVER (with `[DEPRECATED as of 6.3.4]` comments on the COMMON side, value no longer read at runtime):

- `voiceChatWhisperRange`
- `voiceChatNormalRange`
- `voiceChatWeight`
- `voiceChatDbThresholdMap`

The migration routine in `SoundAttractConfig.migrateCommonToServerIfNeeded` gains four entries for these keys, using the same helpers (`migrateDouble`, `migrateStringList`, etc. — one new `migrateInt` helper for the two range ints).

### 3.6 Constructors

`SoundMessage` public constructors collapse to:

1. Full wire constructor (new field list).
2. Convenience client-side constructors: `(soundId, pos, dim, uuid)`, `(..., action)`, `(..., intensity01, whispering)` for voice chat.

Server-local constructors that took `(range, weight)` are removed because server-local callers no longer go through `SoundMessage`.

## 4. Data flow diagram

```
[Client A]                                           [Server]
AttractionClientEvents ─────► SoundMessage(action) ──┐
SoundAttractClientEvents ───► SoundMessage(action?) ─┼──► handle() ─► resolve from SERVER config
VoiceChatIntegrationClient ─► SoundMessage(intens.) ─┘                              │
                                                                                    ▼
                                                                         SoundTracker.addSound

[Server] (loopback eliminated)
TaczIntegration     ─────────────────────────────────────────────────► SoundTracker.addSound
PointBlankIntegration ───────────────────────────────────────────────► SoundTracker.addSound
VanillaIntegrationEvents ────────────────────────────────────────────► SoundTracker.addSound
```

## 5. Failure handling

| Scenario | Result |
|----------|--------|
| Malicious client sends `action="FLY"` | Decode resets to null; falls through to default-entry lookup; drops if not whitelisted. |
| Malicious client sends `intensity01=Float.MAX_VALUE` | Clamp to 1.0. Server range still bounded by `normalRange * factor`. |
| Client spams packets | No change from today — rate limiting is orthogonal. (Out of scope.) |
| `SOUND_DEFAULT_ENTRIES_CACHE` miss on legitimate unwhitelisted sound | Drops silently (matches today's whitelist behavior). |
| SERVER config not yet loaded (pre-server-start packet) | Decode succeeds, `handle` early-returns if `serverReady() == false`. Packet discarded. |

## 6. Testing

Manual verification scenarios (added to the plan's §Verification section):

1. **Footstep integrity.** Vanilla client walking/sneaking/sprinting produces correct detection ranges exactly matching `SERVER.playerActionRanges` values.
2. **Footstep cheat attempt.** A hand-crafted packet with `range=9999` on a footstep soundId → server still computes range from its own config; no amplification.
3. **Voice chat.** Normal speech triggers detection at server-configured `voiceChatNormalRange`; whispering triggers at `voiceChatWhisperRange`; silence produces no detection.
4. **Voice cheat attempt.** Packet with `intensity01=1.0` while actual audio is silent → server still applies threshold map; if threshold of 1.0 is configured to full factor, detection range hits `normalRange` (which is the intended cap anyway).
5. **Arbitrary sound with no default.** Client forwards a soundId absent from `SOUND_DEFAULT_ENTRIES_CACHE` → server drops silently.
6. **Gun/vanilla flows unchanged.** TACZ shoot, PB shoot, vanilla walk/sprint/jump/sneak still register with identical ranges and positions as before.
7. **Safety cap.** Set `maxClientSoundRange = 10`; confirm no wire-derived detection exceeds 10 blocks.
8. **Config migration.** Pre-existing COMMON `voiceChat*` values transfer to SERVER on first server start; SERVER-configured values persist across restarts; subsequent COMMON edits are ignored.

## 7. Risks

- **Wire breakage against older mod versions.** Same-mod on both sides, Forge channel version bump catches mismatches.
- **Behavior drift in dB→factor.** The threshold walk is unchanged; only its input now comes from a normalized float on the wire instead of a local dB computation on the server. For any given real peak-dB, the same multiplier is chosen.
- **Server-local refactor (TACZ/PB/vanilla) introduces new bugs.** Mitigated by preserving exact existing semantics: same positions, same range/weight values, same lifetime, same soundId string built via the existing `SoundTracker.buildIntegrationSoundId` helper.

## 8. Rollback

If the refactor regresses gun/vanilla detection, revert `TaczIntegration`, `PointBlankIntegration`, `VanillaIntegrationEvents` to the `SoundMessage.handle(msg, () -> null)` loopback. The new wire format is independent of those callers and remains safe.

## 9. Follow-ups (explicit non-goals of this spec)

- Server-side rate limiting per player per tick for client-emitted sound packets.
- Signed `sourcePlayerUUID` binding — currently client-asserted, could be replaced with `ctx.getSender().getUUID()` as a separate hardening.
- `SoundMessage` whitelist check moving before dimension lookup for a minor perf win.
