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

    public static final ResourceLocation VOICE_CHAT_SOUND_ID = new ResourceLocation(SoundAttractMod.MOD_ID, "voice_chat");

    public SoundMessage(ResourceLocation soundId, double x, double y, double z, ResourceLocation dimension, Optional<UUID> sourcePlayerUUID) {
        this(soundId, x, y, z, dimension, sourcePlayerUUID, -1, 1.0); 
    }

    public SoundMessage(ResourceLocation soundId, double x, double y, double z, ResourceLocation dimension, Optional<UUID> sourcePlayerUUID, int range) {
         this(soundId, x, y, z, dimension, sourcePlayerUUID, range, 1.0); 
    }

    public SoundMessage(ResourceLocation soundId, double x, double y, double z, ResourceLocation dimension, Optional<UUID> sourcePlayerUUID, int range, double weight) {
        this.soundId = soundId;
        this.x = x;
        this.y = y;
        this.z = z;
        this.dimension = dimension;
        this.sourcePlayerUUID = sourcePlayerUUID;
        this.range = range;
        this.weight = weight;
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

        return new SoundMessage(soundId, x, y, z, dimension, sourcePlayerUUID, range, weight);
    }

    public static void handle(SoundMessage msg, Supplier<NetworkEvent.Context> ctx) {
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
            int lifetime = SoundAttractConfig.COMMON.soundLifetimeTicks.get();

            if (msg.soundId.equals(VOICE_CHAT_SOUND_ID)) {
                if (msg.range > 0) {
                     SoundTracker.addSound(null, pos, dimString, msg.range, msg.weight, lifetime);
                } else {
                }
            } else {
                 SoundEvent snd = ForgeRegistries.SOUND_EVENTS.getValue(msg.soundId);
                 if (snd == null) {
                    return;
                 }

                 if (msg.range >= 0) {
                     SoundTracker.addSound(snd, pos, dimString, msg.range, msg.weight, lifetime);
                 } else {
                     SoundAttractConfig.SoundConfig config = SoundAttractConfig.SOUND_CONFIGS_CACHE.get(snd);
                     if (config != null) {
                         SoundTracker.addSound(snd, pos, dimString, config.range, config.weight, lifetime);
                     } else {
                         if (!SoundAttractConfig.PLAYER_STEP_SOUNDS_CACHE.contains(snd)) {
                         } else {
                         }
                     }
                 }
            }


        });
        ctx.get().setPacketHandled(true);
    }
}