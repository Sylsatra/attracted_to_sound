package com.example.soundattract;

import com.example.soundattract.config.SoundAttractConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import java.util.function.Supplier;
import java.util.UUID;
import java.util.Optional;
import net.minecraft.world.phys.Vec3;

public class SoundMessage {
    private final ResourceLocation soundId;
    private final double x, y, z;
    private final ResourceLocation dimension;
    private final Optional<UUID> sourcePlayerUUID;
    private final int range;
    private final double weight;
    private final String animatorClass;
    private final String taczType;

    public static final ResourceLocation VOICE_CHAT_SOUND_ID = new ResourceLocation(SoundAttractMod.MOD_ID, "voice_chat");

    public SoundMessage(ResourceLocation soundId, double x, double y, double z, ResourceLocation dimension, Optional<UUID> sourcePlayerUUID, int range, double weight, String animatorClass, String taczType) {
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

    public SoundMessage(ResourceLocation soundId, double x, double y, double z, ResourceLocation dimension, Optional<UUID> sourcePlayerUUID, int range, double weight, String animatorClass) {
        this(soundId, x, y, z, dimension, sourcePlayerUUID, range, weight, animatorClass, null);
    }

    public SoundMessage(ResourceLocation soundId, double x, double y, double z, ResourceLocation dimension, Optional<UUID> sourcePlayerUUID) {
        this(soundId, x, y, z, dimension, sourcePlayerUUID, -1, 1.0, null, null);
    }

    public SoundMessage(ResourceLocation soundId, double x, double y, double z, ResourceLocation dimension, Optional<UUID> sourcePlayerUUID, int range) {
        this(soundId, x, y, z, dimension, sourcePlayerUUID, range, 1.0, null, null);
    }

    public SoundMessage(ResourceLocation soundId, double x, double y, double z, ResourceLocation dimension, Optional<UUID> sourcePlayerUUID, int range, double weight) {
        this(soundId, x, y, z, dimension, sourcePlayerUUID, range, weight, null, null);
    }

    public static void encode(SoundMessage msg, FriendlyByteBuf buf) {
        buf.writeResourceLocation(msg.soundId);
        buf.writeDouble(msg.x);
        buf.writeDouble(msg.y);
        buf.writeDouble(msg.z);
        buf.writeResourceLocation(msg.dimension);
        buf.writeBoolean(msg.sourcePlayerUUID.isPresent());
        msg.sourcePlayerUUID.ifPresent(buf::writeUUID);
        buf.writeInt(msg.range);
        buf.writeDouble(msg.weight);
        buf.writeBoolean(msg.animatorClass != null);
        if (msg.animatorClass != null) buf.writeUtf(msg.animatorClass);
        buf.writeBoolean(msg.taczType != null);
        if (msg.taczType != null) buf.writeUtf(msg.taczType);
    }

    public static SoundMessage decode(FriendlyByteBuf buf) {
        ResourceLocation soundId = buf.readResourceLocation();
        double x = buf.readDouble();
        double y = buf.readDouble();
        double z = buf.readDouble();
        ResourceLocation dimension = buf.readResourceLocation();
        Optional<UUID> sourcePlayerUUID = buf.readBoolean() ? Optional.of(buf.readUUID()) : Optional.empty();
        int range = buf.readInt();
        double weight = buf.readDouble();
        String animatorClass = buf.readBoolean() ? buf.readUtf() : null;
        String taczType = buf.readBoolean() ? buf.readUtf() : null;
        return new SoundMessage(soundId, x, y, z, dimension, sourcePlayerUUID, range, weight, animatorClass, taczType);
    }

    public static void handle(SoundMessage msg, Supplier<NetworkEvent.Context> ctx) {
        try {
            String soundIdStr = msg.soundId != null ? msg.soundId.toString() : null;
            if (!SoundAttractConfig.SOUND_ID_WHITELIST_CACHE.isEmpty()
                && (soundIdStr == null || !SoundAttractConfig.SOUND_ID_WHITELIST_CACHE.contains(soundIdStr))
                && (msg.soundId == null || !msg.soundId.equals(VOICE_CHAT_SOUND_ID))) {
                if (ctx != null && ctx.get() != null) ctx.get().setPacketHandled(true);
                return;
            }
            Runnable logic = () -> {
                ServerPlayer sender = null;
                ServerLevel serverLevel = null;
                if (ctx != null && ctx.get() != null && ctx.get().getSender() != null && ctx.get().getSender() instanceof ServerPlayer) {
                    sender = (ServerPlayer) ctx.get().getSender();
                    serverLevel = sender.serverLevel();
                    if (com.example.soundattract.config.SoundAttractConfig.debugLogging.get()) {
                        com.example.soundattract.SoundAttractMod.LOGGER.info("[SoundMessage] Using sender's world: {}", serverLevel);
                    }
                } else if (ctx != null && ctx.get() != null && ctx.get().getDirection().getReceptionSide().isServer()) {
                    net.minecraft.server.MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
                    if (server != null) {
                        serverLevel = server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, msg.dimension));
                        if (com.example.soundattract.config.SoundAttractConfig.debugLogging.get()) {
                            com.example.soundattract.SoundAttractMod.LOGGER.info("[SoundMessage] Using context dimension: {}", msg.dimension);
                        }
                    }
                } else {
                    net.minecraft.server.MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
                    if (server != null) {
                        serverLevel = server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, msg.dimension));
                        if (com.example.soundattract.config.SoundAttractConfig.debugLogging.get()) {
                            com.example.soundattract.SoundAttractMod.LOGGER.info("[SoundMessage] Fallback: Using server world for dimension {}: {}", msg.dimension, serverLevel);
                        }
                    }
                }

                if (serverLevel == null) {
                    com.example.soundattract.SoundAttractMod.LOGGER.warn("[SoundMessage] serverLevel is null for dimension: {}", msg.dimension);
                    return;
                }

                if (!serverLevel.dimension().location().equals(msg.dimension)) {
                    com.example.soundattract.SoundAttractMod.LOGGER.warn("[SoundMessage] serverLevel dimension mismatch: {} != {}", serverLevel.dimension().location(), msg.dimension);
                    return;
                }

                BlockPos pos = BlockPos.containing(msg.x, msg.y, msg.z);
                if (pos.getX() == 0 && pos.getY() == 0 && pos.getZ() == 0 && sender != null) {
                    pos = sender.blockPosition();
                    if (com.example.soundattract.config.SoundAttractConfig.debugLogging.get()) {
                        com.example.soundattract.SoundAttractMod.LOGGER.info("[SoundMessage] Fallback to sender position {} for sound {}", pos, msg.soundId);
                    }
                }
                String dimString = msg.dimension.toString();
                int lifetime = SoundAttractConfig.soundLifetimeTicks.get();

                if (msg.soundId.equals(VOICE_CHAT_SOUND_ID)) {
                    if (msg.range > 0) {
                        SoundTracker.addSound(null, pos, dimString, msg.range, msg.weight, lifetime, VOICE_CHAT_SOUND_ID.toString());
                    }
                } else {
                    SoundEvent se = ForgeRegistries.SOUND_EVENTS.getValue(msg.soundId);
                    double range = msg.range;
                    double weight = msg.weight;
                    String id = msg.soundId != null ? msg.soundId.toString() : "";

                    if (range < 0 && msg.soundId != null) {
                        SoundAttractConfig.SoundConfig config = SoundAttractConfig.getSoundConfigForId(msg.soundId.toString());
                        if (config != null) {
                            range = config.range;
                            weight = config.weight;
                            if (com.example.soundattract.config.SoundAttractConfig.debugLogging.get()) {
                                com.example.soundattract.SoundAttractMod.LOGGER.info("[SoundMessage] Overriding range/weight for {} from nonPlayerSoundIdList: range={}, weight={}", msg.soundId, range, weight);
                            }
                        }
                    }

                    SoundTracker.addSound(se, pos, dimString, range, weight, lifetime);
                }
            };
            if (ctx != null && ctx.get() != null) {
                ctx.get().enqueueWork(logic);
                ctx.get().setPacketHandled(true);
            } else {
                logic.run();
            }
        } catch (Exception e) {
            com.example.soundattract.SoundAttractMod.LOGGER.error("[SoundMessage] Exception in handle for soundId={}", msg.soundId, e);
            if (ctx != null && ctx.get() != null) ctx.get().setPacketHandled(true);
        }
    }
}