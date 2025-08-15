package com.example.soundattract.network;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;
import net.minecraft.server.network.ServerPlayerEntity;
import org.slf4j.Logger;

public class FabricSimpleNbtSync {
    public static final Identifier SIMPLE_NBT_SYNC_ID = new Identifier("soundattract", "simple_nbt_sync");

    public static void registerServerReceiver(Logger logger) {
        ServerPlayNetworking.registerGlobalReceiver(SIMPLE_NBT_SYNC_ID, (server, player, handler, buf, responseSender) -> {
            try {
                NbtCompound nbt = buf.readNbt();
                server.execute(() -> {
                    if (com.example.soundattract.SoundAttractMod.CONFIG != null && com.example.soundattract.SoundAttractMod.CONFIG.debugLogging) {
                        logger.info("[FabricSimpleNbtSync] Server received NBT from {}: {}", player.getName().getString(), nbt);
                    }
                });
            } catch (Exception e) {
                if (com.example.soundattract.SoundAttractMod.CONFIG != null && com.example.soundattract.SoundAttractMod.CONFIG.debugLogging) {
                    e.printStackTrace();
                }
            }
        });
    }

    public static void sendNbtToServer(NbtCompound nbt, Logger logger) {
        if (com.example.soundattract.SoundAttractMod.CONFIG != null && com.example.soundattract.SoundAttractMod.CONFIG.debugLogging) {
            PacketByteBuf buf = new PacketByteBuf(io.netty.buffer.Unpooled.buffer());
            buf.writeNbt(nbt != null ? nbt : new NbtCompound());
            logger.info("[FabricSimpleNbtSync] Client sending NBT: {}", nbt);
            ClientPlayNetworking.send(SIMPLE_NBT_SYNC_ID, buf);
        }
    }
}
