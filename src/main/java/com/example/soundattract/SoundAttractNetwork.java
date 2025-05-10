package com.example.soundattract;

import net.minecraft.util.Identifier;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.server.network.ServerPlayerEntity;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;

public class SoundAttractNetwork {
    public static final Identifier SOUND_MESSAGE_ID = new Identifier(SoundAttractMod.MOD_ID, "sound_message");
    public static final Identifier TACZ_RELOAD_ID = new Identifier(SoundAttractMod.MOD_ID, "tacz_reload");
    public static final Identifier TACZ_GUNSHOT_ID = com.example.soundattract.integration.TaczGunshotMessage.ID;
    public static void sendSoundMessageToServer(SoundMessage msg) {
        PacketByteBuf buf = new PacketByteBuf(io.netty.buffer.Unpooled.buffer());
        SoundMessage.encode(msg, buf);
        ClientPlayNetworking.send(SOUND_MESSAGE_ID, buf);
    }
    public static void sendTaczReloadToServer(com.example.soundattract.integration.TaczReloadMessage msg) {
        PacketByteBuf buf = new PacketByteBuf(io.netty.buffer.Unpooled.buffer());
        msg.write(buf);
        ClientPlayNetworking.send(TACZ_RELOAD_ID, buf);
    }
    public static void sendTaczGunshotToServer(com.example.soundattract.integration.TaczGunshotMessage msg) {
        PacketByteBuf buf = new PacketByteBuf(io.netty.buffer.Unpooled.buffer());
        msg.encode(buf);
        ClientPlayNetworking.send(TACZ_GUNSHOT_ID, buf);
    }
}