package com.example.soundattract;

import com.example.soundattract.config.SoundAttractConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.function.Supplier;

public class SoundMessage {
    private final ResourceLocation soundId;
    private final double x, y, z;
    private final ResourceLocation dimension;

    public SoundMessage(ResourceLocation soundId, double x, double y, double z, ResourceLocation dimension) {
        this.soundId = soundId;
        this.x = x;
        this.y = y;
        this.z = z;
        this.dimension = dimension;
    }

    public static void encode(SoundMessage msg, FriendlyByteBuf buf) {
        buf.writeResourceLocation(msg.soundId);
        buf.writeDouble(msg.x);
        buf.writeDouble(msg.y);
        buf.writeDouble(msg.z);
        buf.writeResourceLocation(msg.dimension);
    }

    public static SoundMessage decode(FriendlyByteBuf buf) {
        return new SoundMessage(
            buf.readResourceLocation(),
            buf.readDouble(),
            buf.readDouble(),
            buf.readDouble(),
            buf.readResourceLocation()
        );
    }

    public static void handle(SoundMessage msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            var player = ctx.get().getSender();
            if (player == null) return;

            if (!player.level.dimension().location().equals(msg.dimension)) return;

            var snd = ForgeRegistries.SOUND_EVENTS.getValue(msg.soundId);
            if (snd == null) return;

            BlockPos pos = new BlockPos(msg.x, msg.y, msg.z);
            String dimString = msg.dimension.toString();

            SoundTracker.addSound(snd, pos, dimString);
        });
        ctx.get().setPacketHandled(true);
    }
}
