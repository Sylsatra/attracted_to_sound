# SoundMessage Wire-Protocol Trust Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove client trust from `range` and `weight` in `SoundMessage` by making them server-derived from validated client inputs (`action`, `intensity01`, `whispering`).

**Architecture:** Change wire format so the client can only transmit *what it knows* (its own action / its own audio intensity / whether it's whispering). The server resolves `range` and `weight` from its own SERVER-spec config, bounded by a global `maxClientSoundRange` cap. Server-local callers (TACZ/PB/vanilla) drop the `SoundMessage.handle` loopback and call `SoundTracker.addSound` directly.

**Tech Stack:** Minecraft Forge 1.20.1, `SimpleChannel` networking, `ForgeConfigSpec`.

**Spec:** `@c:/Users/haihb/Desktop/modding/attract_to_sound/1.20.1-4.1.4/docs/superpowers/specs/2026-04-23-soundmessage-wire-trust-design.md`

**Testing note:** This mod has no Java test infrastructure. Verification is the Gradle compile/build plus manual in-game scenarios from spec §6. Steps below embed compile checks as the automated safety net and defer behavioral verification to Task 7.

---

## File Map

| File | Change |
|------|--------|
| `src/main/java/com/example/soundattract/config/SoundAttractConfig.java` | Add `maxClientSoundRange` + 4 voice-chat keys to `Server`; extend migration routine; add `migrateInt`, `intensity01ToRangeForVoiceChat` helper. |
| `src/main/java/com/example/soundattract/config/separate/VoiceConfig.java` | Add deprecation comments on 4 migrated keys. |
| `src/main/java/com/example/soundattract/integration/tacz/TaczIntegration.java` | Replace `SoundMessage.handle(msg, () -> null)` with direct `SoundTracker.addSound`. |
| `src/main/java/com/example/soundattract/integration/pointblank/PointBlankIntegration.java` | Same refactor as TACZ. |
| `src/main/java/com/example/soundattract/integration/vanilla/VanillaIntegrationEvents.java` | Remove dead `sendToServer` branch + local `SoundMessage` construction. |
| `src/main/java/com/example/soundattract/network/SoundMessage.java` | New wire format (`action`, `intensity01`, `whispering`), new constructors, rewritten `handle`. |
| `src/main/java/com/example/soundattract/client/AttractionClientEvents.java` | Send `action`, no range/weight. |
| `src/main/java/com/example/soundattract/event/client/SoundAttractClientEvents.java` | Send `action` for step sounds, null otherwise; no hardcoded numbers. |
| `src/main/java/com/example/soundattract/integration/voicechat/VoiceChatIntegrationClient.java` | Send `intensity01` + `whispering`; drop threshold walk locally. |
| `src/main/java/com/example/soundattract/network/SoundAttractNetwork.java` | Bump `PROTOCOL_VERSION` from `"1"` to `"2"`. |

---

## Chunk 1: Config additions

### Task 1: Add SERVER keys for voice chat + safety cap

**Files:**
- Modify: `src/main/java/com/example/soundattract/config/SoundAttractConfig.java`

- [ ] **Step 1: Add new fields to the `Server` inner class**

Locate the `Server` class declaration (around line 46 — `public static class Server {`) and add these fields alongside the existing ones:

```java
public final ForgeConfigSpec.DoubleValue maxClientSoundRange;

public final ForgeConfigSpec.IntValue voiceChatWhisperRange;
public final ForgeConfigSpec.IntValue voiceChatNormalRange;
public final ForgeConfigSpec.DoubleValue voiceChatWeight;
public final ForgeConfigSpec.ConfigValue<List<? extends String>> voiceChatDbThresholdMap;
```

- [ ] **Step 2: Wire their spec builders inside `Server(ForgeConfigSpec.Builder b)`**

Add a new `safety` section and a `voice_chat` section at the end of the constructor, before the closing brace:

```java
b.push("safety");
maxClientSoundRange = b
        .comment("Caps the final resolved range (blocks) for any wire-received sound.",
                "Applies after server-side resolution; server-local gun/vanilla sounds are unaffected.")
        .defineInRange("maxClientSoundRange", 256.0, 1.0, 256.0);
b.pop();

b.push("voice_chat");
voiceChatWhisperRange = b
        .comment("Base range used when the speaker is whispering.")
        .defineInRange("voiceChatWhisperRange", 16, 1, 64);
voiceChatNormalRange = b
        .comment("Base range used for normal speaking.")
        .defineInRange("voiceChatNormalRange", 32, 1, 128);
voiceChatWeight = b
        .comment("Weight assigned to the generated voice chat sound event.")
        .defineInRange("voiceChatWeight", 9.0, 0.0, 10.0);
voiceChatDbThresholdMap = b
        .comment("Normalized dB thresholds to range multipliers. Format: 'threshold:multiplier'.")
        .defineList("voiceChatDbThresholdMap", Arrays.asList(
                "50:1.0", "30:0.7", "10:0.3", "0:0.0"
        ), obj -> obj instanceof String && ((String) obj).contains(":"));
b.pop();
```

- [ ] **Step 3: Compile**

Run: `./gradlew compileJava --no-daemon`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/example/soundattract/config/SoundAttractConfig.java
git commit -m "config(server): add maxClientSoundRange + voice chat keys to SERVER spec"
```

---

### Task 2: Extend migration routine with voice chat keys

**Files:**
- Modify: `src/main/java/com/example/soundattract/config/SoundAttractConfig.java`

- [ ] **Step 1: Add an `migrateInt` helper**

Just below the existing `migrateDouble` method, insert:

```java
private static int migrateInt(String name,
                              ForgeConfigSpec.IntValue src,
                              ForgeConfigSpec.IntValue dst,
                              int dstDefault) {
    if (dst.get() != dstDefault) return 0;
    int comCurr = src.get();
    if (comCurr == dstDefault) return 0;
    dst.set(comCurr);
    SoundAttractMod.LOGGER.info("[SoundAttract] migrate {}: {} -> SERVER", name, comCurr);
    return 1;
}
```

- [ ] **Step 2: Add 4 voice chat migration calls inside `migrateCommonToServerIfNeeded`**

Add these lines just before the `if (migrated > 0)` block:

```java
migrated += migrateInt("voiceChatWhisperRange",
        COMMON.voiceChatWhisperRange, SERVER.voiceChatWhisperRange, 16);
migrated += migrateInt("voiceChatNormalRange",
        COMMON.voiceChatNormalRange, SERVER.voiceChatNormalRange, 32);
migrated += migrateDouble("voiceChatWeight",
        COMMON.voiceChatWeight, SERVER.voiceChatWeight, 9.0);
migrated += migrateStringList("voiceChatDbThresholdMap",
        COMMON.voiceChatDbThresholdMap, SERVER.voiceChatDbThresholdMap,
        Arrays.asList("50:1.0", "30:0.7", "10:0.3", "0:0.0"));
```

- [ ] **Step 3: Compile**

Run: `./gradlew compileJava --no-daemon`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/example/soundattract/config/SoundAttractConfig.java
git commit -m "config(migration): migrate voice chat keys COMMON -> SERVER"
```

---

### Task 3: Deprecate voice chat keys in `VoiceConfig`

**Files:**
- Modify: `src/main/java/com/example/soundattract/config/separate/VoiceConfig.java`

- [ ] **Step 1: Replace the comments on the four migrated keys**

For each of `VOICE_CHAT_WHISPER_RANGE`, `VOICE_CHAT_NORMAL_RANGE`, `VOICE_CHAT_WEIGHT`, `VOICE_CHAT_DB_THRESHOLD_MAP`, replace their existing `.comment(...)` argument(s) with:

```java
.comment(
        "[DEPRECATED as of 6.3.4] Moved to soundattract/server-rules.toml (SERVER config).",
        "The value here is no longer read at runtime; kept only so existing config files do not error.")
```

Keep the `.defineInRange(...)` / `.defineList(...)` calls exactly as they are.

- [ ] **Step 2: Compile**

Run: `./gradlew compileJava --no-daemon`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/example/soundattract/config/separate/VoiceConfig.java
git commit -m "config(deprecation): mark voice chat keys deprecated in COMMON"
```

---

## Chunk 2: Server-local callers bypass SoundMessage loopback

### Task 4: TACZ integration direct `addSound`

**Files:**
- Modify: `src/main/java/com/example/soundattract/integration/tacz/TaczIntegration.java`

- [ ] **Step 1: Replace `SoundMessage` construction + `handle` in `onGunShoot`**

Locate the existing block (around line 62–74 — `SoundMessage msg = new SoundMessage( TACZ_SOUND_ID, ... ); SoundMessage.handle(msg, () -> null);`). Replace with:

```java
int lifetime = SoundAttractConfig.COMMON.soundLifetimeTicks.get();
net.minecraft.core.BlockPos pos = player.blockPosition();
String dimString = player.level().dimension().location().toString();
String meta = player.getUUID() + "/" + soundType;
String soundIdToUse = SoundTracker.buildIntegrationSoundId(TACZ_SOUND_ID, meta);
SoundTracker.addSound(null, pos, dimString, (int) range, weight, lifetime, soundIdToUse);
```

Add import if missing: `import com.example.soundattract.tracking.SoundTracker;`
Add import if missing: `import com.example.soundattract.config.SoundAttractConfig;`

- [ ] **Step 2: Do the same in `onGunReload`**

Replace the corresponding `SoundMessage`/`handle` block (around line 94–106) with the same pattern but using `soundType = "reload"` which is already defined locally.

- [ ] **Step 3: Remove the now-unused `SoundMessage` import**

If no other references remain in this file, delete `import com.example.soundattract.network.SoundMessage;`.

- [ ] **Step 4: Compile**

Run: `./gradlew compileJava --no-daemon`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/example/soundattract/integration/tacz/TaczIntegration.java
git commit -m "tacz: call SoundTracker.addSound directly, remove SoundMessage loopback"
```

---

### Task 5: Point Blank integration direct `addSound`

**Files:**
- Modify: `src/main/java/com/example/soundattract/integration/pointblank/PointBlankIntegration.java`

- [ ] **Step 1: Replace `onGunShoot` SoundMessage block**

Locate the block (around line 39–51). Replace with:

```java
int lifetime = SoundAttractConfig.COMMON.soundLifetimeTicks.get();
net.minecraft.core.BlockPos pos = player.blockPosition();
String dimString = player.level().dimension().location().toString();
String meta = player.getUUID() + "/shoot";
String soundIdToUse = SoundTracker.buildIntegrationSoundId(SoundMessage.POINT_BLANK_SOUND_ID, meta);
SoundTracker.addSound(null, pos, dimString, (int) rangeAndWeight[0], rangeAndWeight[1], lifetime, soundIdToUse);
```

Add imports if missing: `com.example.soundattract.tracking.SoundTracker`.
`SoundMessage` import is still needed for `POINT_BLANK_SOUND_ID`.

- [ ] **Step 2: Replace `onGunReload` SoundMessage block**

Same pattern with `meta = player.getUUID() + "/reload"`.

- [ ] **Step 3: Compile**

Run: `./gradlew compileJava --no-daemon`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/example/soundattract/integration/pointblank/PointBlankIntegration.java
git commit -m "pointblank: call SoundTracker.addSound directly, remove SoundMessage loopback"
```

---

### Task 6: Clean up `VanillaIntegrationEvents`

**Files:**
- Modify: `src/main/java/com/example/soundattract/integration/vanilla/VanillaIntegrationEvents.java`

- [ ] **Step 1: Delete the `SoundMessage` construction + dead `sendToServer` branch**

In `sendVanillaSound`, delete:

```java
SoundMessage msg = new SoundMessage(
    ResourceLocation.parse(soundId),
    x, y, z,
    dim,
    uuid,
    range,
    weight,
    animatorClass
);
boolean isServer = true;
try {
    Class.forName("net.minecraft.server.level.ServerPlayer");
} catch (Throwable t) {
    isServer = false;
}
if (isServer) {
    ...keep the inner block that ends with SoundTracker.addSound(...)...
    return;
} else {
    com.example.soundattract.network.SoundAttractNetwork.INSTANCE.sendToServer(msg);
}
```

Leave only the body that was inside `if (isServer)`, without the `return;` at the end. The outer if/else wrapper and the `SoundMessage msg = ...` construction are removed entirely.

- [ ] **Step 2: Remove now-unused import**

Delete `import com.example.soundattract.network.SoundMessage;` if present.

- [ ] **Step 3: Compile**

Run: `./gradlew compileJava --no-daemon`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/example/soundattract/integration/vanilla/VanillaIntegrationEvents.java
git commit -m "vanilla: drop dead SoundMessage loopback, use SoundTracker.addSound directly"
```

---

## Chunk 3: Rewrite `SoundMessage`

### Task 7: Swap wire fields on `SoundMessage`

**Files:**
- Modify: `src/main/java/com/example/soundattract/network/SoundMessage.java`

- [ ] **Step 1: Update the class fields**

Replace the field block (lines 19–27) with:

```java
private final ResourceLocation soundId;
private final double x, y, z;
private final ResourceLocation dimension;
private final Optional<UUID> sourcePlayerUUID;
private final String action;
private final float intensity01;
private final boolean whispering;
private final String animatorClass;
private final String taczType;
private final String pointBlankType;

private static final java.util.Set<String> KNOWN_ACTIONS = java.util.Set.of(
        "CRAWLING", "SNEAKING", "WALKING", "SPRINTING", "SPRINT_JUMPING");
```

- [ ] **Step 2: Replace constructors**

Delete all current `public SoundMessage(...)` constructors. Add exactly these:

```java
public SoundMessage(ResourceLocation soundId, double x, double y, double z,
                    ResourceLocation dimension, Optional<UUID> sourcePlayerUUID,
                    String action, float intensity01, boolean whispering,
                    String animatorClass, String taczType, String pointBlankType) {
    this.soundId = soundId;
    this.x = x;
    this.y = y;
    this.z = z;
    this.dimension = dimension;
    this.sourcePlayerUUID = sourcePlayerUUID;
    this.action = action;
    this.intensity01 = Float.isFinite(intensity01) ? Math.max(0f, Math.min(1f, intensity01)) : 0f;
    this.whispering = whispering;
    this.animatorClass = animatorClass;
    this.taczType = taczType;
    this.pointBlankType = pointBlankType;
}

public SoundMessage(ResourceLocation soundId, double x, double y, double z,
                    ResourceLocation dimension, Optional<UUID> sourcePlayerUUID) {
    this(soundId, x, y, z, dimension, sourcePlayerUUID, null, Float.NaN, false, null, null, null);
}

public SoundMessage(ResourceLocation soundId, double x, double y, double z,
                    ResourceLocation dimension, Optional<UUID> sourcePlayerUUID,
                    String action) {
    this(soundId, x, y, z, dimension, sourcePlayerUUID, action, Float.NaN, false, null, null, null);
}

public SoundMessage(ResourceLocation soundId, double x, double y, double z,
                    ResourceLocation dimension, Optional<UUID> sourcePlayerUUID,
                    float intensity01, boolean whispering) {
    this(soundId, x, y, z, dimension, sourcePlayerUUID, null, intensity01, whispering, null, null, null);
}
```

- [ ] **Step 3: Rewrite `encode`**

```java
public static void encode(SoundMessage msg, FriendlyByteBuf buf) {
    buf.writeResourceLocation(msg.soundId);
    buf.writeDouble(msg.x);
    buf.writeDouble(msg.y);
    buf.writeDouble(msg.z);
    buf.writeResourceLocation(msg.dimension);
    buf.writeBoolean(msg.sourcePlayerUUID.isPresent());
    msg.sourcePlayerUUID.ifPresent(buf::writeUUID);
    buf.writeBoolean(msg.action != null);
    if (msg.action != null) buf.writeUtf(msg.action, 32);
    buf.writeFloat(msg.intensity01);
    buf.writeBoolean(msg.whispering);
    buf.writeBoolean(msg.animatorClass != null);
    if (msg.animatorClass != null) buf.writeUtf(msg.animatorClass);
    buf.writeBoolean(msg.taczType != null);
    if (msg.taczType != null) buf.writeUtf(msg.taczType);
    buf.writeBoolean(msg.pointBlankType != null);
    if (msg.pointBlankType != null) buf.writeUtf(msg.pointBlankType);
}
```

- [ ] **Step 4: Rewrite `decode`**

```java
public static SoundMessage decode(FriendlyByteBuf buf) {
    ResourceLocation soundId = buf.readResourceLocation();
    double x = buf.readDouble();
    double y = buf.readDouble();
    double z = buf.readDouble();
    ResourceLocation dimension = buf.readResourceLocation();
    Optional<UUID> sourcePlayerUUID = buf.readBoolean() ? Optional.of(buf.readUUID()) : Optional.empty();
    String rawAction = buf.readBoolean() ? buf.readUtf(32) : null;
    String action = (rawAction != null && KNOWN_ACTIONS.contains(rawAction)) ? rawAction : null;
    float intensity01 = buf.readFloat();
    boolean whispering = buf.readBoolean();
    String animatorClass = buf.readBoolean() ? buf.readUtf() : null;
    String taczType = buf.readBoolean() ? buf.readUtf() : null;
    String pointBlankType = buf.readBoolean() ? buf.readUtf() : null;
    return new SoundMessage(soundId, x, y, z, dimension, sourcePlayerUUID,
            action, intensity01, whispering, animatorClass, taczType, pointBlankType);
}
```

- [ ] **Step 5: Compile (expect errors in `handle`)**

Run: `./gradlew compileJava --no-daemon`
Expected: errors inside `handle` because `msg.range` / `msg.weight` no longer exist. That's fine — Task 8 rewrites `handle`.

---

### Task 8: Rewrite `SoundMessage.handle`

**Files:**
- Modify: `src/main/java/com/example/soundattract/network/SoundMessage.java`

- [ ] **Step 1: Replace the body of `handle` with the server-authoritative resolution**

Replace the entire `public static void handle(SoundMessage msg, Supplier<NetworkEvent.Context> ctx)` method:

```java
public static void handle(SoundMessage msg, Supplier<NetworkEvent.Context> ctx) {
    try {
        ResourceLocation loc = msg.soundId;
        boolean isIntegration = (msg.taczType != null) || (msg.pointBlankType != null);
        if (!SoundAttractConfig.SOUND_ID_WHITELIST_CACHE.isEmpty()
                && (loc == null || !SoundAttractConfig.SOUND_ID_WHITELIST_CACHE.contains(loc))
                && !msg.soundId.equals(VOICE_CHAT_SOUND_ID)
                && !msg.soundId.equals(POINT_BLANK_SOUND_ID)
                && !isIntegration) {
            if (SoundAttractConfig.COMMON.debugLogging.get()) {
                SoundAttractMod.LOGGER.info("[SoundMessage] Dropping sound {} because it is not in whitelist (dim={})", loc, msg.dimension);
            }
            if (ctx != null && ctx.get() != null) ctx.get().setPacketHandled(true);
            return;
        }

        Runnable logic = () -> {
            if (!SoundAttractConfig.serverReady()) return;
            ServerPlayer sender = (ctx != null && ctx.get() != null)
                              ? ctx.get().getSender() : null;
            ServerLevel serverLevel = sender != null
                    ? sender.serverLevel()
                    : net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer()
                          .getLevel(net.minecraft.resources.ResourceKey.create(
                                    net.minecraft.core.registries.Registries.DIMENSION,
                                    msg.dimension));

            if (serverLevel == null) {
                SoundAttractMod.LOGGER.warn("[SoundMessage] serverLevel is null for {}", msg.dimension);
                return;
            }
            if (!serverLevel.dimension().location().equals(msg.dimension)) {
                SoundAttractMod.LOGGER.warn("[SoundMessage] dimension mismatch ({} != {})",
                        serverLevel.dimension().location(), msg.dimension);
                return;
            }

            BlockPos pos = BlockPos.containing(msg.x, msg.y, msg.z);
            if (pos.equals(BlockPos.ZERO) && sender != null) pos = sender.blockPosition();
            String dimString = msg.dimension.toString();
            int lifetime = SoundAttractConfig.COMMON.soundLifetimeTicks.get();

            double range;
            double weight;
            String resolvedIdForAdd = null;

            if (msg.action != null) {
                Integer r = SoundAttractConfig.PLAYER_ACTION_RANGES_CACHE.get(msg.action);
                Double w = SoundAttractConfig.PLAYER_ACTION_WEIGHTS_CACHE.get(msg.action);
                if (r == null || w == null) {
                    if (SoundAttractConfig.COMMON.debugLogging.get()) {
                        SoundAttractMod.LOGGER.info("[SoundMessage] Unknown action {}, dropping", msg.action);
                    }
                    return;
                }
                range = r;
                weight = w;
            } else if (msg.soundId.equals(VOICE_CHAT_SOUND_ID)) {
                int baseRange = msg.whispering
                        ? SoundAttractConfig.SERVER.voiceChatWhisperRange.get()
                        : SoundAttractConfig.SERVER.voiceChatNormalRange.get();
                double normDb = Math.max(0f, Math.min(1f, msg.intensity01)) * 127.0;
                double factor = lookupVoiceChatFactor(normDb);
                range = Math.round(baseRange * factor);
                if (range <= 0) return;
                weight = SoundAttractConfig.SERVER.voiceChatWeight.get();
                resolvedIdForAdd = VOICE_CHAT_SOUND_ID.toString();
            } else {
                SoundAttractConfig.SoundDefaultEntry def =
                        SoundAttractConfig.SOUND_DEFAULT_ENTRIES_CACHE.get(msg.soundId);
                if (def == null) {
                    if (SoundAttractConfig.COMMON.debugLogging.get()) {
                        SoundAttractMod.LOGGER.info("[SoundMessage] No default entry for {}, dropping", msg.soundId);
                    }
                    return;
                }
                range = def.range();
                weight = def.weight();
            }

            range = Math.min(range, SoundAttractConfig.SERVER.maxClientSoundRange.get());

            if (SoundAttractConfig.COMMON.debugLogging.get()) {
                SoundAttractMod.LOGGER.info(
                        "[SoundMessage] resolved soundId={} action={} whispering={} intensity01={} -> range={} weight={}",
                        msg.soundId, msg.action, msg.whispering, msg.intensity01, range, weight);
            }

            if (resolvedIdForAdd != null) {
                SoundTracker.addSound(null, pos, dimString, (int) range, weight, lifetime, resolvedIdForAdd);
            } else {
                SoundEvent se = ForgeRegistries.SOUND_EVENTS.getValue(msg.soundId);
                if (se != null) {
                    SoundTracker.addSound(se, pos, dimString, (int) range, weight, lifetime);
                } else {
                    SoundTracker.addSound(null, pos, dimString, (int) range, weight, lifetime, msg.soundId.toString());
                }
            }
        };

        if (ctx != null && ctx.get() != null) {
            ctx.get().enqueueWork(logic);
            ctx.get().setPacketHandled(true);
        } else {
            logic.run();
        }
    } catch (Exception e) {
        SoundAttractMod.LOGGER.error("[SoundMessage] Exception for soundId={}", msg.soundId, e);
        if (ctx != null && ctx.get() != null) ctx.get().setPacketHandled(true);
    }
}

private static double lookupVoiceChatFactor(double normDb) {
    java.util.List<? extends String> raw = SoundAttractConfig.SERVER.voiceChatDbThresholdMap.get();
    if (raw == null || raw.isEmpty()) {
        if (normDb >= 50.0) return 1.0;
        if (normDb >= 30.0) return 0.7;
        if (normDb >= 10.0) return 0.3;
        return 0.0;
    }
    for (String entry : raw) {
        String[] parts = entry.split(":", 2);
        if (parts.length != 2) continue;
        try {
            double thr = Double.parseDouble(parts[0].trim());
            double mult = Double.parseDouble(parts[1].trim());
            if (normDb >= thr) return mult;
        } catch (NumberFormatException ignored) {}
    }
    return 0.0;
}
```

- [ ] **Step 2: Delete the now-unused `getPointBlankType` method if not referenced**

Run: `git grep "getPointBlankType"` — if no results outside `SoundMessage.java`, delete it.

- [ ] **Step 3: Compile**

Run: `./gradlew compileJava --no-daemon`
Expected: compile errors **only** in the client emit sites (Task 9–11 fix those). If there are errors anywhere else, pause and investigate.

- [ ] **Step 4: Commit (intermediate — build still broken; note it in the message)**

```bash
git add src/main/java/com/example/soundattract/network/SoundMessage.java
git commit -m "net(wire): new SoundMessage wire format + server-authoritative handle (WIP, emit sites pending)"
```

---

## Chunk 4: Update client emit sites

### Task 9: `AttractionClientEvents` — send action only

**Files:**
- Modify: `src/main/java/com/example/soundattract/client/AttractionClientEvents.java`

- [ ] **Step 1: Replace the `new SoundMessage(...)` call (lines 87–93)**

Locate `SoundAttractNetwork.INSTANCE.sendToServer(new SoundMessage( virtualSoundId, soundX, soundY, soundZ, ... ));`. Replace with:

```java
SoundAttractNetwork.INSTANCE.sendToServer(new SoundMessage(
        virtualSoundId,
        soundX, soundY, soundZ,
        player.level().dimension().location(),
        java.util.Optional.of(player.getUUID()),
        action
));
```

Note: `action` is already an uppercased enum-like `String` (e.g. `"WALKING"`) — the spec requires it in that exact set. Verify the local variable is already one of `{CRAWLING, SNEAKING, WALKING, SPRINTING, SPRINT_JUMPING}`.

- [ ] **Step 2: Remove dead reads**

Delete any now-unused lookups into `SoundAttractConfig.PLAYER_ACTION_RANGES_CACHE` / `PLAYER_ACTION_WEIGHTS_CACHE` inside this method (if present).

- [ ] **Step 3: Compile**

Run: `./gradlew compileJava --no-daemon`
Expected: still errors in the other two emit sites only.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/example/soundattract/client/AttractionClientEvents.java
git commit -m "client(attract): send action only, no wire range/weight"
```

---

### Task 10: `SoundAttractClientEvents` — send action for steps, null otherwise

**Files:**
- Modify: `src/main/java/com/example/soundattract/event/client/SoundAttractClientEvents.java`

- [ ] **Step 1: Replace the `switch (currentAction)` block and `new SoundMessage(...)` call**

Delete the hardcoded `switch` (lines 93–117) that sets `calculatedRange` / `calculatedWeight`.

Replace the final `SoundMessage msg = new SoundMessage(soundRL, x, y, z, dim, sourcePlayerUUID, calculatedRange, calculatedWeight);` with:

```java
String actionName = (currentAction != null && currentAction != PlayerAction.IDLE)
        ? currentAction.name()
        : null;
if (currentAction == PlayerAction.IDLE) return;
SoundMessage msg = new SoundMessage(soundRL, x, y, z, dim, sourcePlayerUUID, actionName);
```

Note: for non-step sounds (where the step branch above is skipped), `currentAction` stays `PlayerAction.IDLE`. Adjust the condition so non-step sounds still forward (action null) but step sounds with `IDLE` are dropped:

```java
boolean isStep = se != null && se.getLocation().getPath().contains("step")
        && clientPlayer.position().distanceToSqr(x, y, z) < 1.5 * 1.5;
if (isStep && currentAction == PlayerAction.IDLE) return;

String actionName = (isStep && currentAction != PlayerAction.IDLE)
        ? currentAction.name()
        : null;
SoundMessage msg = new SoundMessage(soundRL, x, y, z, dim, sourcePlayerUUID, actionName);
```

(Restructure the existing `if (se != null && ... step ...)` block so `currentAction` is only classified when the step condition is true, and the forwarding always happens at the end.)

- [ ] **Step 2: Delete now-unused `calculatedRange` / `calculatedWeight` locals**

Remove their declarations (lines 63–64) and any remaining references.

- [ ] **Step 3: Compile**

Run: `./gradlew compileJava --no-daemon`
Expected: one remaining error in `VoiceChatIntegrationClient`.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/example/soundattract/event/client/SoundAttractClientEvents.java
git commit -m "client(play-sound): send action for steps, null otherwise; no wire range/weight"
```

---

### Task 11: `VoiceChatIntegrationClient` — send intensity01 + whispering

**Files:**
- Modify: `src/main/java/com/example/soundattract/integration/voicechat/VoiceChatIntegrationClient.java`

- [ ] **Step 1: Replace the dB→factor→range logic with a single `intensity01` computation**

Delete:
- The `factor`/threshold walk block (lines 42–62).
- The `baseRange`/`effectiveRange` computation (lines 66–73).
- The `weight` read (line 88).
- The `SoundMessage(... effectiveRange, weight)` call (lines 90–98).

Replace with:

```java
double normDb = db - (-127.0);
float intensity01 = (float) Math.max(0.0, Math.min(1.0, normDb / 127.0));

if (SoundAttractConfig.COMMON.debugLogging.get()) {
    SoundAttractMod.LOGGER.info(
        "[SVC Client] dbFS={} normDb={} intensity01={} whispering={}",
        db, normDb, intensity01, event.isWhispering()
    );
}

double x = clientPlayer.getX();
double y = clientPlayer.getY();
double z = clientPlayer.getZ();
ResourceLocation dim = clientWorld.dimension().location();
Optional<UUID> sourcePlayerUUID = Optional.of(clientPlayer.getUUID());

SoundMessage msg = new SoundMessage(
        SoundMessage.VOICE_CHAT_SOUND_ID,
        x, y, z,
        dim,
        sourcePlayerUUID,
        intensity01,
        event.isWhispering()
);
SoundAttractNetwork.INSTANCE.sendToServer(msg);
```

- [ ] **Step 2: Remove the now-unused `VoiceChatThresholds` import if no other reference exists in this file**

Run: `git grep -n "VoiceChatThresholds" src/main/java/com/example/soundattract/integration/voicechat/VoiceChatIntegrationClient.java`
If no results, delete the import.

- [ ] **Step 3: Compile**

Run: `./gradlew compileJava --no-daemon`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/example/soundattract/integration/voicechat/VoiceChatIntegrationClient.java
git commit -m "client(svc): send intensity01 + whispering; drop client-side threshold walk"
```

---

## Chunk 5: Finalize & verify

### Task 12: Bump network protocol version

**Files:**
- Modify: `src/main/java/com/example/soundattract/network/SoundAttractNetwork.java`

- [ ] **Step 1: Change `PROTOCOL_VERSION`**

Replace:
```java
private static final String PROTOCOL_VERSION = "1";
```
with:
```java
private static final String PROTOCOL_VERSION = "2";
```

- [ ] **Step 2: Compile**

Run: `./gradlew compileJava --no-daemon`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/example/soundattract/network/SoundAttractNetwork.java
git commit -m "net: bump PROTOCOL_VERSION to 2 (wire format change)"
```

---

### Task 13: Full build verification

- [ ] **Step 1: Run full build**

Run: `./gradlew build -x test --no-daemon`
Expected: `BUILD SUCCESSFUL`. Warnings about pre-existing deprecations are OK; no new errors.

- [ ] **Step 2: Grep for leftover `msg.range` / `msg.weight` references**

Run: `git grep -n "msg\.range\|msg\.weight" src/main/java`
Expected: no results.

- [ ] **Step 3: Grep for any remaining `SoundMessage.handle(msg, () -> null)` calls**

Run: `git grep -n "SoundMessage.handle(msg, () -> null)" src/main/java`
Expected: no results (all server-local callers now use `SoundTracker.addSound` directly).

- [ ] **Step 4: Grep for stale 7-arg / 8-arg `new SoundMessage(...)` constructors**

Run: `git grep -nE "new SoundMessage\([^)]*,[^)]*,[^)]*,[^)]*,[^)]*,[^)]*,[^)]*int[^)]*\)" src/main/java`
A manual review of matches is enough — verify none still pass `range`/`weight`.

- [ ] **Step 5: Commit nothing; this is a review gate**

If any check fails, fix before proceeding.

---

### Task 14: Manual in-game verification (from spec §6)

> This task has no commits; it is an acceptance gate run by the developer.

- [ ] **Scenario 1 — Footstep integrity.** Start a dev server (`./gradlew runServer`) + client (`./gradlew runClient`). Walk / sneak / sprint near an attracted mob. Confirm detection ranges match `SERVER.playerActionRanges` entries.

- [ ] **Scenario 2 — Footstep cheat attempt.** (Optional; requires a hacked client or a debug packet-injection cmd.) Send a footstep packet with a fabricated `action` such as `"FLY"`. Confirm server drops / uses default-entry.

- [ ] **Scenario 3 — Voice chat.** With Simple Voice Chat installed and `SERVER.enableVoiceChatIntegration=true`, speak at normal volume. Confirm detection at ~`voiceChatNormalRange`. Whisper → detection at `voiceChatWhisperRange`. Silence → no detection packet registered (check debug log).

- [ ] **Scenario 4 — Voice cheat attempt.** Same as scenario 2 but with voice chat packet carrying `intensity01 > 1.0` or NaN. Server clamps to 1.0; range never exceeds `voiceChatNormalRange`.

- [ ] **Scenario 5 — Unknown soundId forward.** Trigger a non-whitelisted, non-default-entry sound (e.g. a rare vanilla sound not in the defaults map). Confirm server drops silently with debug log entry.

- [ ] **Scenario 6 — Gun/vanilla flows unchanged.** Shoot a TACZ gun, shoot a Point Blank gun, walk/sprint/jump/sneak as a vanilla player. Confirm detection radii match pre-refactor values.

- [ ] **Scenario 7 — Safety cap.** Set `SERVER.maxClientSoundRange = 10` in `config/soundattract/server-rules.toml` and restart. Confirm no wire-derived detection exceeds 10 blocks. Revert cap when done.

- [ ] **Scenario 8 — Config migration.** On a pre-existing world, edit `config/soundattract/voice.toml` and set `voiceChatNormalRange = 40` before first launch on 6.3.4. Launch once. Verify:
  - `config/soundattract/server-rules.toml` now contains `voiceChatNormalRange = 40`.
  - Log line `[SoundAttract] migrate voiceChatNormalRange: 40 -> SERVER` appears.
  - Subsequent launches do not re-migrate.
  - Further edits to the deprecated `voice.toml` key are ignored (no log migration on second run).

---

## Done criteria

- All 14 tasks checked.
- `./gradlew build -x test --no-daemon` green.
- No new `msg.range` / `msg.weight` / `SoundMessage.handle(msg, () -> null)` references in the tree.
- All 8 manual scenarios pass.
