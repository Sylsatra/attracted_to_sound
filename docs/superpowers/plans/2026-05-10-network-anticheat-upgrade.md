# Network Anticheat Upgrade Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restore Forge 1.20.1 networking features and implement server-side anticheat to prevent client manipulation of sound detection ranges and weights.

**Architecture:** Update SoundMessage to include all fields from Forge version, move range/weight calculation to server-side based on sound type/action/intensity, and update all integration senders to send raw data instead of pre-calculated values.

**Tech Stack:** NeoForge 1.21.1, StreamCodec, CustomPacketPayload, PayloadRegistrar

---

## File Structure

**Files to modify:**
- `src/main/java/com/example/soundattract/SoundMessage.java` - Add missing fields (action, intensity01, whispering, pointBlankType), update StreamCodec, move range/weight calculation to handle()
- `src/main/java/com/example/soundattract/integration/voicechat/VoiceChatIntegrationClient.java` - Update to send intensity01 and whispering, add computePeakDb method
- `src/main/java/com/example/soundattract/integration/pointblank/PointBlankIntegration.java` - Update to send pointBlankType instead of pre-calculated range
- `src/main/java/com/example/soundattract/integration/tacz/TaczIntegration.java` - Update to send raw data instead of pre-calculated range
- `src/main/java/com/example/soundattract/event/client/SoundAttractClientEvents.java` - Update SoundMessage constructor call for new signature

**Files to reference:**
- `Forge-1.20.1/src/main/java/com/example/soundattract/network/SoundMessage.java` - Reference for field additions and logic
- `1.21.1-4.1.2-neo/src/main/java/com/example/soundattract/SoundMessage.java` - Current implementation to upgrade

---

## Chunk 1: SoundMessage Field Additions and StreamCodec Update

### Task 1: Add missing fields to SoundMessage record

**Files:**
- Modify: `src/main/java/com/example/soundattract/SoundMessage.java:25-34`

- [ ] **Step 1: Add missing fields to SoundMessage record definition**

```java
public record SoundMessage(
    ResourceLocation soundId,
    Vec3 position,
    ResourceLocation dimension,
    Optional<UUID> sourcePlayerUUID,
    int range,           // DEPRECATED: Server will recalculate, kept for backward compatibility
    double weight,       // DEPRECATED: Server will recalculate, kept for backward compatibility
    Optional<String> animatorClass,
    Optional<String> taczType,
    String action,       // NEW: Player action (CRAWLING, SNEAKING, WALKING, SPRINTING, SPRINT_JUMPING)
    float intensity01,   // NEW: Voice chat intensity (0.0-1.0)
    boolean whispering,  // NEW: Voice chat whisper flag
    String pointBlankType // NEW: PointBlank gun type
) implements CustomPacketPayload {
```

- [ ] **Step 2: Add VOICE_CHAT_SOUND_ID and POINT_BLANK_SOUND_ID constants**

```java
public static final ResourceLocation VOICE_CHAT_SOUND_ID = ResourceLocation.fromNamespaceAndPath(SoundAttractMod.MOD_ID, "voice_chat");
public static final ResourceLocation POINT_BLANK_SOUND_ID = ResourceLocation.fromNamespaceAndPath("pointblank", "gun_action");
```

- [ ] **Step 3: Add KNOWN_ACTIONS constant set**

```java
private static final java.util.Set<String> KNOWN_ACTIONS = java.util.Set.of(
    "CRAWLING", "SNEAKING", "WALKING", SPRINTING", "SPRINT_JUMPING"
);
```

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/example/soundattract/SoundMessage.java
git commit -m "feat: add missing fields to SoundMessage for anticheat"
```

### Task 2: Update StreamCodec to encode/decode new fields

**Files:**
- Modify: `src/main/java/com/example/soundattract/SoundMessage.java:59-80`

- [ ] **Step 1: Update Part1 record to include new fields**

```java
private record Part1(
    ResourceLocation soundId,
    Vec3 position,
    ResourceLocation dimension,
    Optional<UUID> sourcePlayerUUID,
    String action,
    float intensity01,
    boolean whispering,
    String pointBlankType
) {}
```

- [ ] **Step 2: Update PART1_CODEC to encode/decode new fields**

```java
private static final StreamCodec<FriendlyByteBuf, Part1> PART1_CODEC = StreamCodec.composite(
    ResourceLocation.STREAM_CODEC, Part1::soundId,
    StreamCodec.composite(
        ByteBufCodecs.DOUBLE, Vec3::x,
        ByteBufCodecs.DOUBLE, Vec3::y,
        ByteBufCodecs.DOUBLE, Vec3::z,
        Vec3::new
    ), Part1::position,
    ResourceLocation.STREAM_CODEC, Part1::dimension,
    net.minecraft.core.UUIDUtil.STREAM_CODEC.apply(ByteBufCodecs::optional), Part1::sourcePlayerUUID,
    ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs::optional), Part1::action,
    ByteBufCodecs.FLOAT, Part1::intensity01,
    ByteBufCodecs.BOOL, Part1::whispering,
    ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs::optional), Part1::pointBlankType,
    Part1::new
);
```

- [ ] **Step 3: Update STREAM_CODEC to use new Part1**

```java
public static final StreamCodec<FriendlyByteBuf, SoundMessage> STREAM_CODEC = StreamCodec.composite(
    PART1_CODEC,
    sm -> new Part1(sm.soundId(), sm.position(), sm.dimension(), sm.sourcePlayerUUID(), sm.action(), sm.intensity01(), sm.whispering(), sm.pointBlankType()),
    ByteBufCodecs.VAR_INT,
    SoundMessage::range,
    ByteBufCodecs.DOUBLE,
    SoundMessage::weight,
    ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs::optional),
    SoundMessage::animatorClass,
    ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs::optional),
    SoundMessage::taczType,
    (part1, range, weight, animClass, taczType) -> new SoundMessage(
        part1.soundId(),
        part1.position(),
        part1.dimension(),
        part1.sourcePlayerUUID(),
        range,
        weight,
        animClass,
        taczType,
        part1.action(),
        part1.intensity01(),
        part1.whispering(),
        part1.pointBlankType()
    )
);
```

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/example/soundattract/SoundMessage.java
git commit -m "feat: update StreamCodec to encode/decode new SoundMessage fields"
```

---

## Chunk 2: Server-Side Validation Logic

### Task 3: Add lookupVoiceChatFactor method

**Files:**
- Modify: `src/main/java/com/example/soundattract/SoundMessage.java:127-133` (add before handle method)

- [ ] **Step 1: Add lookupVoiceChatFactor method**

```java
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

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/example/soundattract/SoundMessage.java
git commit -m "feat: add voice chat DB threshold lookup method"
```

### Task 4: Update SoundMessage.handle to calculate range/weight server-side

**Files:**
- Modify: `src/main/java/com/example/soundattract/SoundMessage.java:82-127`

- [ ] **Step 1: Replace handle method with server-side validation logic**

```java
public static void handle(SoundMessage msg, IPayloadContext context) {
    context.enqueueWork(() -> {
        try {
            ResourceLocation loc = msg.soundId();
            boolean isIntegration = (msg.taczType() != null) || (msg.pointBlankType() != null);
            if (!SoundAttractConfig.SOUND_ID_WHITELIST_CACHE.isEmpty()
                    && (loc == null || !SoundAttractConfig.SOUND_ID_WHITELIST_CACHE.contains(loc))
                    && !loc.equals(VOICE_CHAT_SOUND_ID)
                    && !loc.equals(ResourceLocation.fromNamespaceAndPath("pointblank", "gun_action"))
                    && !isIntegration) {
                if (SoundAttractConfig.COMMON.debugLogging.get()) {
                    SoundAttractMod.LOGGER.info("[SoundMessage] Dropping sound {} because it is not in whitelist (dim={})", loc, msg.dimension());
                }
                return;
            }

            if (!SoundAttractConfig.serverReady()) return;
            ServerPlayer sender = context.player() instanceof ServerPlayer sp ? sp : null;
            ResourceKey<Level> levelKey = ResourceKey.create(Registries.DIMENSION, msg.dimension());
            ServerLevel serverLevel = ServerLifecycleHooks.getCurrentServer().getLevel(levelKey);
            if (serverLevel == null) {
                SoundAttractMod.LOGGER.warn("[SoundMessage] serverLevel is null for {}", msg.dimension());
                return;
            }
            if (!serverLevel.dimension().location().equals(msg.dimension())) {
                SoundAttractMod.LOGGER.warn("[SoundMessage] dimension mismatch ({} != {})",
                        serverLevel.dimension().location(), msg.dimension());
                return;
            }

            final Vec3 soundLocation = msg.position().equals(Vec3.ZERO) && sender != null ? sender.position() : msg.position();
            final BlockPos pos = BlockPos.containing(soundLocation);
            final String dimString = msg.dimension().toString();
            final int lifetime = SoundAttractConfig.COMMON.soundLifetimeTicks.get();

            double range;
            double weight;
            String resolvedIdForAdd = null;

            // Server-side range/weight calculation (anticheat)
            if (msg.action() != null) {
                Integer r = SoundAttractConfig.PLAYER_ACTION_RANGES_CACHE.get(msg.action());
                Double w = SoundAttractConfig.PLAYER_ACTION_WEIGHTS_CACHE.get(msg.action());
                if (r == null || w == null) {
                    if (SoundAttractConfig.COMMON.debugLogging.get()) {
                        SoundAttractMod.LOGGER.info("[SoundMessage] Unknown action {}, dropping", msg.action());
                    }
                    return;
                }
                range = r;
                weight = w;
            } else if (loc.equals(VOICE_CHAT_SOUND_ID)) {
                int baseRange = msg.whispering()
                        ? SoundAttractConfig.SERVER.voiceChatWhisperRange.get()
                        : SoundAttractConfig.SERVER.voiceChatNormalRange.get();
                double normDb = Math.max(0f, Math.min(1f, msg.intensity01())) * 127.0;
                double factor = lookupVoiceChatFactor(normDb);
                range = Math.round(baseRange * factor);
                if (range <= 0) return;
                weight = SoundAttractConfig.SERVER.voiceChatWeight.get();
                resolvedIdForAdd = VOICE_CHAT_SOUND_ID.toString();
            } else {
                SoundAttractConfig.SoundDefaultEntry def = SoundAttractConfig.SOUND_DEFAULT_ENTRIES_CACHE.get(loc);
                if (def == null) {
                    if (SoundAttractConfig.COMMON.debugLogging.get()) {
                        SoundAttractMod.LOGGER.info("[SoundMessage] No default entry for {}, dropping", loc);
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
                        loc, msg.action(), msg.whispering(), msg.intensity01(), range, weight);
            }

            if (resolvedIdForAdd != null) {
                SoundTracker.addSound(null, pos, dimString, (int) range, weight, lifetime, resolvedIdForAdd);
            } else {
                Optional<SoundEvent> se = BuiltInRegistries.SOUND_EVENT.getOptional(loc);
                se.ifPresent(soundEvent -> SoundTracker.addSound(soundEvent, pos, dimString, (int) range, weight, lifetime, null));
            }
        } catch (Exception e) {
            SoundAttractMod.LOGGER.error("[SoundMessage] Exception for soundId={}", msg.soundId(), e);
        }
    });
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/example/soundattract/SoundMessage.java
git commit -m "feat: implement server-side range/weight calculation for anticheat"
```

---

## Chunk 3: VoiceChat Integration Update

### Task 5: Update VoiceChatIntegrationClient to send intensity01 and whispering

**Files:**
- Modify: `src/main/java/com/example/soundattract/integration/voicechat/VoiceChatIntegrationClient.java:58-66`

- [ ] **Step 1: Update SoundMessage construction to include intensity01 and whispering**

```java
SoundMessage msg = new SoundMessage(
    SoundMessage.VOICE_CHAT_SOUND_ID,
    new Vec3(x, y, z),
    dim,
    sourcePlayerUUID,
    0,  // range (server will recalculate)
    0.0,  // weight (server will recalculate)
    Optional.empty(),  // animatorClass
    Optional.empty(),  // taczType
    null,  // action
    intensity01,  // NEW
    isWhispering,  // NEW
    null  // pointBlankType
);
```

- [ ] **Step 2: Revert stub to full implementation with computePeakDb method**

Replace entire file content with Forge version adapted for NeoForge network API, including:

```java
private static double computePeakDb(short[] samples) {
    int highest = 0;
    for (short s : samples) {
        int a = s == Short.MIN_VALUE ? 32768 : Math.abs(s);
        if (a > highest) highest = a;
    }
    if (highest == 0) return -127.0;
    double norm = highest / 32768.0;
    double db = 20.0 * Math.log10(norm);
    if (!Double.isFinite(db)) return -127.0;
    if (db > 0.0) db = 0.0;
    if (db < -127.0) db = -127.0;
    return db;
}
```

- [ ] **Step 3: Add VoiceChat dependency back to build.gradle**

```gradle
dependencies {
    compileOnly 'net.tslat.smartbrainlib:SmartBrainLib-neoforge-1.21.1:1.16.11'
    compileOnly 'de.maxhenkel.voicechat:voicechat-neoforge-1.21.1:2.6.17'
}
```

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/example/soundattract/integration/voicechat/VoiceChatIntegrationClient.java build.gradle
git commit -m "feat: update VoiceChat integration to send intensity/whispering"
```

---

## Chunk 4: PointBlank Integration Update

### Task 6: Update PointBlankIntegration to send pointBlankType

**Files:**
- Modify: `src/main/java/com/example/soundattract/integration/pointblank/PointBlankIntegration.java` (find SoundMessage construction)

- [ ] **Step 1: Find SoundMessage construction in PointBlankIntegration**

Use grep to locate where SoundMessage is created.

- [ ] **Step 2: Update SoundMessage to send pointBlankType instead of pre-calculated range**

```java
SoundMessage msg = new SoundMessage(
    ResourceLocation.fromNamespaceAndPath("pointblank", "gun_action"),
    new Vec3(x, y, z),
    dim,
    Optional.empty(),  // sourcePlayerUUID
    0,  // range (server will recalculate)
    0.0,  // weight (server will recalculate)
    Optional.empty(),  // animatorClass
    Optional.empty(),  // taczType
    null,  // action
    Float.NaN,  // intensity01
    false,  // whispering
    gunType  // pointBlankType
);
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/example/soundattract/integration/pointblank/PointBlankIntegration.java
git commit -m "feat: update PointBlank integration to send gun type for server-side calculation"
```

---

## Chunk 5: TACZ Integration Update

### Task 7: Update TaczIntegration to send raw data

**Files:**
- Modify: `src/main/java/com/example/soundattract/integration/tacz/TaczIntegration.java` (find SoundMessage construction)

- [ ] **Step 1: Find SoundMessage construction in TaczIntegration**

Use grep to locate where SoundMessage is created.

- [ ] **Step 2: Update SoundMessage to send raw data instead of pre-calculated range**

```java
SoundMessage msg = new SoundMessage(
    ResourceLocation.fromNamespaceAndPath(SoundAttractMod.MOD_ID, "tacz_sound"),
    new Vec3(x, y, z),
    dim,
    Optional.empty(),  // sourcePlayerUUID
    0,  // range (server will recalculate)
    0.0,  // weight (server will recalculate)
    Optional.of(animatorClass),  // animatorClass
    Optional.of(taczType),  // taczType
    null,  // action
    Float.NaN,  // intensity01
    false,  // whispering
    null  // pointBlankType
);
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/example/soundattract/integration/tacz/TaczIntegration.java
git commit -m "feat: update TACZ integration to send raw data for server-side calculation"
```

---

## Chunk 6: Update SoundAttractClientEvents Constructor Call

### Task 8: Update SoundMessage constructor in SoundAttractClientEvents

**Files:**
- Modify: `src/main/java/com/example/soundattract/event/client/SoundAttractClientEvents.java:99`

- [ ] **Step 1: Update SoundMessage construction to use new signature**

```java
SoundMessage msg = new SoundMessage(
    soundRL,
    new Vec3(x, y, z),
    dim,
    sourcePlayerUUID,
    0,  // range (server will recalculate)
    0.0,  // weight (server will recalculate)
    Optional.empty(),  // animatorClass
    Optional.empty(),  // taczType
    actionName,  // action
    Float.NaN,  // intensity01
    false,  // whispering
    null  // pointBlankType
);
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/example/soundattract/event/client/SoundAttractClientEvents.java
git commit -m "feat: update SoundMessage constructor call for new signature"
```

---

## Chunk 7: Verification and Testing

### Task 9: Compile verification

**Files:**
- Test: `build/compile-cmd.log`

- [ ] **Step 1: Run compile**

```bash
./gradlew.bat compileJava --console=plain > build\compile-cmd.log 2>&1
```

- [ ] **Step 2: Check for errors**

```bash
Select-String -Path .\build\compile-cmd.log -Pattern 'error:'
```

Expected: 0 errors

- [ ] **Step 3: Commit**

```bash
git add docs/superpowers/plans/2026-05-10-network-anticheat-upgrade.md
git commit -m "docs: add network anticheat upgrade plan"
```

### Task 10: Runtime testing (manual)

- [ ] **Step 1: Test voice chat integration**
- [ ] **Step 2: Test PointBlank integration**
- [ ] **Step 3: Test TACZ integration**
- [ ] **Step 4: Verify server-side range calculation works**
- [ ] **Step 5: Verify client cannot manipulate range/weight**

---

## Summary

This plan upgrades the networking system to match Forge 1.20.1's feature set with server-side anticheat:

1. **SoundMessage field additions:** action, intensity01, whispering, pointBlankType
2. **Server-side validation:** Range and weight calculated server-side based on sound type/action/intensity
3. **Voice chat integration:** Sends intensity and whispering for server-side range calculation
4. **PointBlank integration:** Sends gun type for server-side calculation
5. **TACZ integration:** Sends raw data instead of pre-calculated values
6. **Optional player action tracking:** For movement-based sound detection

**Security improvement:** Client can no longer manipulate sound detection ranges and weights by sending arbitrary values. All calculations happen server-side based on validated input.
