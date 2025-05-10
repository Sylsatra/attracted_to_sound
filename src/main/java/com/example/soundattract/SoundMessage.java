package com.example.soundattract;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.Identifier;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.world.World;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;

import com.example.soundattract.SoundAttractMod;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import net.minecraft.sound.SoundEvent;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World;
import java.util.function.Supplier;
import java.util.UUID;
import java.util.Optional;
import net.minecraft.util.math.Vec3d;

public class SoundMessage {
    private final Identifier soundId;
    private final double x, y, z;
    private final Identifier dimension;
    private final Optional<UUID> sourcePlayerUUID;
    private final int range;
    private final double weight;
    private final String animatorClass;
    private final String taczType;

    public static final Identifier VOICE_CHAT_SOUND_ID = new Identifier(SoundAttractMod.MOD_ID, "voice_chat");

    public SoundMessage(Identifier soundId, double x, double y, double z, Identifier dimension, Optional<UUID> sourcePlayerUUID, int range, double weight, String animatorClass, String taczType) {
        this.soundId = soundId;
        this.x = x;
        this.y = y;
        this.z = z;
        this.dimension = dimension;
        this.sourcePlayerUUID = sourcePlayerUUID;
        this.range = range;
        this.weight = weight;
        this.animatorClass = animatorClass;
        this.taczType = taczType;
    }

    public SoundMessage(Identifier soundId, double x, double y, double z, Identifier dimension, Optional<UUID> sourcePlayerUUID, int range, double weight, String animatorClass) {
        this(soundId, x, y, z, dimension, sourcePlayerUUID, range, weight, animatorClass, null);
    }

    public SoundMessage(Identifier soundId, double x, double y, double z, Identifier dimension, Optional<UUID> sourcePlayerUUID) {
        this(soundId, x, y, z, dimension, sourcePlayerUUID, -1, 1.0, null, null);
    }

    public SoundMessage(Identifier soundId, double x, double y, double z, Identifier dimension, Optional<UUID> sourcePlayerUUID, int range) {
        this(soundId, x, y, z, dimension, sourcePlayerUUID, range, 1.0, null, null);
    }

    public SoundMessage(Identifier soundId, double x, double y, double z, Identifier dimension, Optional<UUID> sourcePlayerUUID, int range, double weight) {
        this(soundId, x, y, z, dimension, sourcePlayerUUID, range, weight, null, null);
    }

    public static void encode(SoundMessage msg, PacketByteBuf buf) {
        buf.writeIdentifier(msg.soundId);
        buf.writeDouble(msg.x);
        buf.writeDouble(msg.y);
        buf.writeDouble(msg.z);
        buf.writeIdentifier(msg.dimension);
        buf.writeBoolean(msg.sourcePlayerUUID.isPresent());
        msg.sourcePlayerUUID.ifPresent(buf::writeUuid);
        buf.writeInt(msg.range);
        buf.writeDouble(msg.weight);
        buf.writeBoolean(msg.animatorClass != null);
        if (msg.animatorClass != null) buf.writeString(msg.animatorClass);
        buf.writeBoolean(msg.taczType != null);
        if (msg.taczType != null) buf.writeString(msg.taczType);
    }

    public static SoundMessage decode(PacketByteBuf buf) {
        Identifier soundId = buf.readIdentifier();
        double x = buf.readDouble();
        double y = buf.readDouble();
        double z = buf.readDouble();
        Identifier dimension = buf.readIdentifier();
        Optional<UUID> sourcePlayerUUID = buf.readBoolean() ? Optional.of(buf.readUuid()) : Optional.empty();
        int range = buf.readInt();
        double weight = buf.readDouble();
        String animatorClass = buf.readBoolean() ? buf.readString() : null;
        String taczType = buf.readBoolean() ? buf.readString() : null;
        return new SoundMessage(soundId, x, y, z, dimension, sourcePlayerUUID, range, weight, animatorClass, taczType);
    }

    public static void handle(SoundMessage msg, net.minecraft.server.network.ServerPlayerEntity sender) {
    if (com.example.soundattract.SoundAttractMod.CONFIG != null && com.example.soundattract.SoundAttractMod.CONFIG.debugLogging) {
    com.example.soundattract.SoundAttractMod.LOGGER.info("[DEBUG] SoundMessage.handle called for soundId: {} | range: {} | weight: {} | pos: ({}, {}, {}) | dim: {} | sender: {}", msg.soundId, msg.range, msg.weight, msg.x, msg.y, msg.z, msg.dimension, sender != null ? sender.getName().getString() : "null");
}
        try {
            String soundIdStr = msg.soundId != null ? msg.soundId.toString() : null;
            if (!SoundAttractMod.CONFIG.soundIdWhitelist.isEmpty()
                && (soundIdStr == null || !SoundAttractMod.CONFIG.soundIdWhitelist.contains(soundIdStr))
                && (msg.soundId == null || !msg.soundId.equals(VOICE_CHAT_SOUND_ID))) {
                return;
            }
            if (sender == null) {
                com.example.soundattract.SoundAttractMod.LOGGER.warn("[SoundMessage] sender is null for sound message with soundId: {}", msg.soundId);
                return;
            }
            RegistryKey<World> worldKey = RegistryKey.of(RegistryKeys.WORLD, msg.dimension);
            ServerWorld serverWorld = sender.getServer().getWorld(worldKey);
            if (serverWorld == null) {
                com.example.soundattract.SoundAttractMod.LOGGER.warn("[SoundMessage] serverWorld is null for dimension: {}", msg.dimension);
                return;
            }
            if (!serverWorld.getRegistryKey().getValue().equals(msg.dimension)) {
                com.example.soundattract.SoundAttractMod.LOGGER.warn("[SoundMessage] serverWorld dimension mismatch: {} != {}", serverWorld.getRegistryKey().getValue(), msg.dimension);
                return;
            }

            BlockPos pos = new BlockPos((int)msg.x, (int)msg.y, (int)msg.z);
            if (pos.getX() == 0 && pos.getY() == 0 && pos.getZ() == 0 && sender != null) {
                pos = sender.getBlockPos();
                com.example.soundattract.SoundAttractMod.LOGGER.info("[SoundMessage] Fallback to sender position {} for sound {}", pos, msg.soundId);
            }
            String dimString = msg.dimension.toString();
            int lifetime = SoundAttractMod.CONFIG.soundLifetimeTicks;

            if (msg.soundId.toString().equals("soundattract:gunshot") && msg.taczType != null) {
    String[] parts = msg.taczType.split(";");
    String gunId = null, fireMode = null;
    int ammo = -1;
    for (String part : parts) {
        if (part.startsWith("GunId=")) gunId = part.substring(6);
        else if (part.startsWith("FireMode=")) fireMode = part.substring(9);
        else if (part.startsWith("Ammo=")) {
            try { ammo = Integer.parseInt(part.substring(5)); } catch (Exception ignored) {}
        }
    }
    if (com.example.soundattract.SoundAttractMod.CONFIG != null && com.example.soundattract.SoundAttractMod.CONFIG.debugLogging) {
    SoundAttractMod.LOGGER.info("[DEBUG_SERVER] Received gunshot SoundMessage: gunId={}, fireMode={}, ammo={}, raw='{}'", gunId, fireMode, ammo, msg.taczType);
}
}

        if (msg.soundId.equals(VOICE_CHAT_SOUND_ID)) {
            if (com.example.soundattract.SoundAttractMod.CONFIG != null && com.example.soundattract.SoundAttractMod.CONFIG.debugLogging) {
    com.example.soundattract.SoundAttractMod.LOGGER.info("[DEBUG] About to call SoundTracker.addSound for VOICE_CHAT_SOUND_ID at pos=({}, {}, {}), dim={}, range={}, weight={}", pos.getX(), pos.getY(), pos.getZ(), dimString, msg.range, msg.weight);
}
                if (msg.range > 0) {
                    SoundTracker.addSound(null, pos, dimString, msg.range, msg.weight, lifetime, VOICE_CHAT_SOUND_ID.toString());
                }
            } else {
                SoundEvent se = net.minecraft.registry.Registries.SOUND_EVENT.get(msg.soundId);
                double range = msg.range;
                double weight = msg.weight;
                String id = msg.soundId != null ? msg.soundId.toString() : "";

                if (range < 0 && msg.soundId != null) {
    com.example.soundattract.config.SoundAttractConfigData.SoundConfig config = SoundAttractMod.CONFIG.getSoundConfigForId(msg.soundId.toString());
    if (config != null) {
        range = config.range;
        weight = config.weight;
        if (SoundAttractMod.CONFIG.debugLogging) {
            SoundAttractMod.LOGGER.info("[SoundMessage] Overriding range/weight for {} from nonPlayerSoundIdList: range={}, weight={}", msg.soundId, range, weight);
        }
    }
}

SoundTracker.addSound(se, pos, dimString, range, weight, lifetime);
            }
        } catch (Exception e) {
            com.example.soundattract.SoundAttractMod.LOGGER.error("[SoundMessage] Exception in handle for soundId={}", msg.soundId, e);
        }
    }
}