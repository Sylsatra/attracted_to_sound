package com.example.soundattract;

import com.example.soundattract.config.SoundAttractConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
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

    public static final ResourceLocation VOICE_CHAT_SOUND_ID = new ResourceLocation(SoundAttractMod.MOD_ID, "voice_chat");

    public SoundMessage(ResourceLocation soundId, double x, double y, double z, ResourceLocation dimension, Optional<UUID> sourcePlayerUUID, int range, double weight, String animatorClass) {
        this.soundId = soundId;
        this.x = x;
        this.y = y;
        this.z = z;
        this.dimension = dimension;
        this.sourcePlayerUUID = sourcePlayerUUID;
        this.range = range;
        this.weight = weight;
        this.animatorClass = animatorClass;
    }

    public SoundMessage(ResourceLocation soundId, double x, double y, double z, ResourceLocation dimension, Optional<UUID> sourcePlayerUUID) {
        this(soundId, x, y, z, dimension, sourcePlayerUUID, -1, 1.0, null);
    }

    public SoundMessage(ResourceLocation soundId, double x, double y, double z, ResourceLocation dimension, Optional<UUID> sourcePlayerUUID, int range) {
        this(soundId, x, y, z, dimension, sourcePlayerUUID, range, 1.0, null);
    }

    public SoundMessage(ResourceLocation soundId, double x, double y, double z, ResourceLocation dimension, Optional<UUID> sourcePlayerUUID, int range, double weight) {
        this(soundId, x, y, z, dimension, sourcePlayerUUID, range, weight, null);
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
        return new SoundMessage(soundId, x, y, z, dimension, sourcePlayerUUID, range, weight, animatorClass);
    }

    public static void handle(SoundMessage msg, Supplier<NetworkEvent.Context> ctx) {
        String soundIdStr = msg.soundId != null ? msg.soundId.toString() : null;
        if (!SoundAttractConfig.SOUND_ID_WHITELIST_CACHE.isEmpty()
            && (soundIdStr == null || !SoundAttractConfig.SOUND_ID_WHITELIST_CACHE.contains(soundIdStr))
            && (msg.soundId == null || !msg.soundId.equals(VOICE_CHAT_SOUND_ID))) {
            ctx.get().setPacketHandled(true);
            return;
        }
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender(); 
            Level serverLevel = null;

            if (sender != null) {
                 serverLevel = sender.server.getLevel(sender.level().dimension());
            } else if (ctx.get().getDirection().getReceptionSide().isServer()) {

                 net.minecraft.server.MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
                 if (server != null) {
                     serverLevel = server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, msg.dimension));
                 }
            }

            if (serverLevel == null) {
                 return;
            }

            if (!serverLevel.dimension().location().equals(msg.dimension)) {
                 return;
            }

            BlockPos pos = BlockPos.containing(msg.x, msg.y, msg.z);
            String dimString = msg.dimension.toString();
            int lifetime = SoundAttractConfig.soundLifetimeTicks.get();

            if (msg.soundId.equals(VOICE_CHAT_SOUND_ID)) {
                if (msg.range > 0) {
                    SoundTracker.addSound(null, pos, dimString, msg.range, msg.weight, lifetime, VOICE_CHAT_SOUND_ID.toString());
                } else {
                }
            } else {
                 SoundEvent se = ForgeRegistries.SOUND_EVENTS.getValue(msg.soundId);
                 double range = msg.range;
                 double weight = msg.weight;
                 String id = msg.soundId != null ? msg.soundId.toString() : "";
                 if (range <= 0 || weight <= 0) {
                     if (id.contains("tacz:gun")) {
                         range = SoundAttractConfig.taczShootRange.get();
                         weight = SoundAttractConfig.taczShootWeight.get();
                     } else if (id.contains("tacz:reload")) {
                         range = SoundAttractConfig.taczReloadRange.get();
                         weight = SoundAttractConfig.taczReloadWeight.get();
                     }
                 }
                 SoundTracker.addSound(se, pos, dimString, range, weight, lifetime);
            }

        });
        ctx.get().setPacketHandled(true);
    }
}