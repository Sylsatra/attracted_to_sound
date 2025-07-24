package com.example.soundattract;

import net.minecraft.entity.LivingEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.Optional;
import java.util.UUID;

public class SoundMessage {
    private final Identifier soundId;
    private final double x, y, z;
    private final Identifier dimension;
    private final Optional<UUID> sourcePlayerUUID;
    private final int range;
    private final double weight;
    private final String animatorClass;
    private final String gunData;

    public static final Identifier VOICE_CHAT_SOUND_ID = new Identifier(SoundAttractMod.MOD_ID, "voice_chat");

    public SoundMessage(Identifier soundId, double x, double y, double z, Identifier dimension, Optional<UUID> sourcePlayerUUID, int range, double weight, String animatorClass, String gunData) {
        this.soundId = soundId;
        this.x = x;
        this.y = y;
        this.z = z;
        this.dimension = dimension;
        this.sourcePlayerUUID = sourcePlayerUUID;
        this.range = range;
        this.weight = weight;
        this.animatorClass = animatorClass;
        this.gunData = gunData;
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
        buf.writeBoolean(msg.gunData != null);
        if (msg.gunData != null) buf.writeString(msg.gunData);
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
        String gunData = buf.readBoolean() ? buf.readString() : null;
        return new SoundMessage(soundId, x, y, z, dimension, sourcePlayerUUID, range, weight, animatorClass, gunData);
    }

    public static void handle(SoundMessage msg, net.minecraft.server.network.ServerPlayerEntity sender) {
        if (SoundAttractMod.CONFIG != null && SoundAttractMod.CONFIG.debugLogging) {
            SoundAttractMod.LOGGER.info("[DEBUG] SoundMessage.handle called for soundId: {} | range: {} | weight: {} | pos: ({}, {}, {}) | dim: {} | sender: {}", msg.soundId, msg.range, msg.weight, msg.x, msg.y, msg.z, msg.dimension, sender != null ? sender.getName().getString() : "null");
        }
        try {
            String soundIdStr = msg.soundId != null ? msg.soundId.toString() : null;
            if (!SoundAttractMod.CONFIG.soundIdWhitelist.isEmpty()
                    && (soundIdStr == null || !SoundAttractMod.CONFIG.soundIdWhitelist.contains(soundIdStr))
                    && (msg.soundId == null || !msg.soundId.equals(VOICE_CHAT_SOUND_ID))) {
                return;
            }
            if (sender == null) {
                SoundAttractMod.LOGGER.warn("[SoundMessage] sender is null for sound message with soundId: {}", msg.soundId);
                return;
            }
            RegistryKey<World> worldKey = RegistryKey.of(RegistryKeys.WORLD, msg.dimension);
            ServerWorld serverWorld = sender.getServer().getWorld(worldKey);
            if (serverWorld == null) {
                SoundAttractMod.LOGGER.warn("[SoundMessage] serverWorld is null for dimension: {}", msg.dimension);
                return;
            }
            if (!serverWorld.getRegistryKey().getValue().equals(msg.dimension)) {
                SoundAttractMod.LOGGER.warn("[SoundMessage] serverWorld dimension mismatch: {} != {}", serverWorld.getRegistryKey().getValue(), msg.dimension);
                return;
            }

            BlockPos pos = new BlockPos((int) msg.x, (int) msg.y, (int) msg.z);
            if (pos.getX() == 0 && pos.getY() == 0 && pos.getZ() == 0 && sender != null) {
                pos = sender.getBlockPos();
                SoundAttractMod.LOGGER.info("[SoundMessage] Fallback to sender position {} for sound {}", pos, msg.soundId);
            }
            String dimString = msg.dimension.toString();
            int lifetime = SoundAttractMod.CONFIG.soundLifetimeTicks;



            if (msg.gunData != null) {
                String actionType = "unknown";
                String gunId = "unknown";
                try {

                    String[] parts = msg.gunData.split(";", 2);
                    if (parts.length > 0) {
                        actionType = parts[0];
                    }
                    if (parts.length > 1) {
                        gunId = parts[1];
                    }
                } catch (Exception e) {
                    SoundAttractMod.LOGGER.warn("[SoundMessage] Failed to parse gunData string: {}", msg.gunData, e);
                }

                if (SoundAttractMod.CONFIG != null && SoundAttractMod.CONFIG.debugLogging) {
                    SoundAttractMod.LOGGER.info("[DEBUG_SERVER] Received gun action SoundMessage: action={}, gunId={}, raw='{}'", actionType, gunId, msg.gunData);
                }
            }



            if (msg.soundId.equals(VOICE_CHAT_SOUND_ID)) {
                if (SoundAttractMod.CONFIG != null && SoundAttractMod.CONFIG.debugLogging) {
                    SoundAttractMod.LOGGER.info("[DEBUG] About to call SoundTracker.addSound for VOICE_CHAT_SOUND_ID at pos=({}, {}, {}), dim={}, range={}, weight={}", pos.getX(), pos.getY(), pos.getZ(), dimString, msg.range, msg.weight);
                }
                if (msg.range > 0) {
                    SoundTracker.addSound(null, pos, dimString, msg.range, msg.weight, lifetime, VOICE_CHAT_SOUND_ID.toString());
                }
            } else {
                double range = msg.range;
                double weight = msg.weight;
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

                SoundTracker.addSound(null, pos, dimString, range, weight, lifetime, msg.soundId.toString());

            }
        } catch (Exception e) {
            SoundAttractMod.LOGGER.error("[SoundMessage] Exception in handle for soundId={}", msg.soundId, e);
        }
    }
}