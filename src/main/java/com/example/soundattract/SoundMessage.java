package com.example.soundattract;

import com.example.soundattract.config.SoundAttractConfig;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.registries.ForgeRegistries;

public class SoundMessage {
    private final ResourceLocation soundId;
    private final double x, y, z;
    private final ResourceLocation dimension;
    private final Optional<UUID> sourcePlayerUUID;
    private final int range;
    private final double weight;
    private final String animatorClass;
    private final String taczType;

    public static final ResourceLocation VOICE_CHAT_SOUND_ID = ResourceLocation.fromNamespaceAndPath(SoundAttractMod.MOD_ID, "voice_chat");

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
            ResourceLocation loc = msg.soundId;
            if (!SoundAttractConfig.SOUND_ID_WHITELIST_CACHE.isEmpty()
                    && (loc == null || !SoundAttractConfig.SOUND_ID_WHITELIST_CACHE.contains(loc))
                    && !msg.soundId.equals(VOICE_CHAT_SOUND_ID)) {
                if (ctx != null && ctx.get() != null) ctx.get().setPacketHandled(true);
                return;
            }

            Runnable logic = () -> {
                ServerPlayer sender = (ctx != null && ctx.get() != null)
                                  ? ctx.get().getSender() : null;
                ServerLevel  serverLevel = sender != null
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
                    SoundAttractMod.LOGGER.warn("[SoundMessage] dimension mismatch ({} ≠ {})",
                                                serverLevel.dimension().location(), msg.dimension);
                    return;
                }

                BlockPos pos = BlockPos.containing(msg.x, msg.y, msg.z);
                if (pos.equals(BlockPos.ZERO) && sender != null) pos = sender.blockPosition();
                String dimString = msg.dimension.toString();
                int    lifetime  = SoundAttractConfig.COMMON.soundLifetimeTicks.get();

                if (msg.soundId.equals(VOICE_CHAT_SOUND_ID)) {
                    if (msg.range > 0) {
                        SoundTracker.addSound(null, pos, dimString,
                                              msg.range, msg.weight, lifetime,
                                              VOICE_CHAT_SOUND_ID.toString());
                    }
                } else {
                    SoundEvent se = ForgeRegistries.SOUND_EVENTS.getValue(msg.soundId);
                    double range  = msg.range;
                    double weight = msg.weight;

                    if (range < 0) {
                        SoundAttractConfig.SoundDefaultEntry def =
                            SoundAttractConfig.SOUND_DEFAULT_ENTRIES_CACHE.get(msg.soundId);
                        if (def != null) {
                            range  = def.range();
                            weight = def.weight();
                        }
                    }
                    if (range < 0) {
                        if ("shoot".equals(msg.taczType)) {
                            if (SoundAttractConfig.COMMON.debugLogging.get()) {
                                SoundAttractMod.LOGGER.info(
                                    "[SoundMessage] Skipping fallback for gun-shot {}", msg.soundId);
                            }
                            return;
                        }
                        range  = 10;
                        weight = 1.0;
                        if (SoundAttractConfig.COMMON.debugLogging.get()) {
                            SoundAttractMod.LOGGER.info(
                                "[SoundMessage] Using fallback range/weight for {}: range={}, weight={}",
                                msg.soundId, range, weight);
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
            SoundAttractMod.LOGGER.error("[SoundMessage] Exception for soundId={}", msg.soundId, e);
            if (ctx != null && ctx.get() != null) ctx.get().setPacketHandled(true);
        }
    }
}